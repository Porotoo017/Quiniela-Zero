package com.quinielazero.app.core.calendar

import com.quinielazero.app.core.model.Jurisdiccion
import com.quinielazero.app.core.model.SorteoDomain
import com.quinielazero.app.core.model.TurnoSorteo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class CalendarDayStatus(
    val date: LocalDate,
    val isSunday: Boolean,
    val isHoliday: Boolean,
    val hasDraws: Boolean,
    val turnsProvincia: List<TurnoSorteo>,
    val turnsCiudad: List<TurnoSorteo>,
    val summaryLabel: String
)

class SorteoCalendarProvider(
    private val zoneId: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
) {
    private val weekdayTurns: MutableMap<Jurisdiccion, List<TurnoSorteo>> = mutableMapOf(
        Jurisdiccion.PROVINCIA to TurnoSorteo.entries.toList(),
        Jurisdiccion.CIUDAD to TurnoSorteo.entries.toList()
    )
    private val saturdayTurns: MutableMap<Jurisdiccion, List<TurnoSorteo>> = mutableMapOf(
        Jurisdiccion.PROVINCIA to TurnoSorteo.entries.toList(),
        Jurisdiccion.CIUDAD to TurnoSorteo.entries.toList()
    )

    @Volatile
    var holidayModeEnabled: Boolean = false
        private set

    @Volatile
    private var customHolidays: Set<LocalDate> = emptySet()

    fun nowArgentina(): ZonedDateTime = ZonedDateTime.now(zoneId)
    fun todayArgentina(): LocalDate = nowArgentina().toLocalDate()

    fun updateHolidayConfig(enabled: Boolean, holidayDatesIso: Set<String>) {
        holidayModeEnabled = enabled
        customHolidays = holidayDatesIso.mapNotNull {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }.toSet()
    }

    /**
     * Deduce dinámicamente desde el historial real cargado qué turnos se sortean
     * de lunes a viernes y cuáles los sábados, sin inventar horarios ni datos.
     */
    fun calibrateFromHistory(history: List<SorteoDomain>) {
        if (history.isEmpty()) return
        for (jur in Jurisdiccion.entries) {
            val subset = history.filter { it.loteria == jur }
            if (subset.isEmpty()) continue

            val weekdayFound = mutableSetOf<TurnoSorteo>()
            val saturdayFound = mutableSetOf<TurnoSorteo>()

            for (draw in subset) {
                val date = runCatching { LocalDate.parse(draw.fechaIso) }.getOrNull() ?: continue
                when (date.dayOfWeek) {
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY -> weekdayFound.add(draw.turno)
                    DayOfWeek.SATURDAY -> saturdayFound.add(draw.turno)
                    DayOfWeek.SUNDAY -> Unit // Nunca hay sorteos los domingos
                }
            }

            if (weekdayFound.isNotEmpty()) {
                weekdayTurns[jur] = TurnoSorteo.entries.filter { it in weekdayFound }
            }
            if (saturdayFound.isNotEmpty()) {
                saturdayTurns[jur] = TurnoSorteo.entries.filter { it in saturdayFound }
            }
        }
    }

    fun isHoliday(date: LocalDate): Boolean = holidayModeEnabled || (date in customHolidays)

    fun getTurnsForDate(jurisdiccion: Jurisdiccion, date: LocalDate): List<TurnoSorteo> {
        if (date.dayOfWeek == DayOfWeek.SUNDAY || isHoliday(date)) return emptyList()
        return when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> saturdayTurns[jurisdiccion].orEmpty()
            else -> weekdayTurns[jurisdiccion].orEmpty()
        }
    }

    fun isTurnAllowed(jurisdiccion: Jurisdiccion, date: LocalDate, turno: TurnoSorteo): Boolean {
        if (date.dayOfWeek == DayOfWeek.SUNDAY) return false
        val allowed = when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> saturdayTurns[jurisdiccion].orEmpty()
            else -> weekdayTurns[jurisdiccion].orEmpty()
        }
        return turno in allowed
    }

    fun getDayStatus(date: LocalDate = todayArgentina()): CalendarDayStatus {
        val isSun = date.dayOfWeek == DayOfWeek.SUNDAY
        val isHol = isHoliday(date)
        val provTurns = getTurnsForDate(Jurisdiccion.PROVINCIA, date)
        val ciuTurns = getTurnsForDate(Jurisdiccion.CIUDAD, date)
        val hasDraws = !isSun && !isHol && (provTurns.isNotEmpty() || ciuTurns.isNotEmpty())

        val label = when {
            isSun -> "Domingo sin sorteos oficiales · Modo planificación semanal"
            isHol -> "Modo Feriado activo · Sin sorteos programados hoy"
            date.dayOfWeek == DayOfWeek.SATURDAY ->
                "Sábado (${provTurns.size} turnos Provincia / ${ciuTurns.size} Ciudad deducidos del historial)"
            else -> "Día hábil (${provTurns.size} sorteos diarios)"
        }

        return CalendarDayStatus(
            date = date,
            isSunday = isSun,
            isHoliday = isHol,
            hasDraws = hasDraws,
            turnsProvincia = provTurns,
            turnsCiudad = ciuTurns,
            summaryLabel = label
        )
    }
}
