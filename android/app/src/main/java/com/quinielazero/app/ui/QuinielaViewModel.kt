package com.quinielazero.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quinielazero.app.core.analytics.StatisticalEngine
import com.quinielazero.app.core.calendar.CalendarDayStatus
import com.quinielazero.app.core.calendar.SorteoCalendarProvider
import com.quinielazero.app.core.model.BoletaJugada
import com.quinielazero.app.core.model.ConfiguracionApp
import com.quinielazero.app.core.model.FuenteSaludInfo
import com.quinielazero.app.core.model.Jurisdiccion
import com.quinielazero.app.core.model.PlusEvaluacion
import com.quinielazero.app.core.model.ResultadoEstadistico
import com.quinielazero.app.core.model.SorteoDomain
import com.quinielazero.app.core.model.TurnoSorteo
import com.quinielazero.app.core.model.VentanaAnalisis
import com.quinielazero.app.core.network.DataSourcesManager
import com.quinielazero.app.core.network.SyncReport
import com.quinielazero.app.core.plus.QuinielaPlusEngine
import com.quinielazero.app.data.local.EncryptedTicketStore
import com.quinielazero.app.data.local.ExportManager
import com.quinielazero.app.data.local.HistorialBootstrap
import com.quinielazero.app.data.local.QuinielaDatabase
import com.quinielazero.app.notifications.NotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class MainUiState(
    val isBootstrapping: Boolean = true,
    val isSyncing: Boolean = false,
    val selectedJurisdiccion: Jurisdiccion = Jurisdiccion.PROVINCIA,
    val selectedVentana: VentanaAnalisis = VentanaAnalisis.DIAS_90,
    val calendarStatus: CalendarDayStatus? = null,
    val estadistica: ResultadoEstadistico? = null,
    val estadisticaOpuesta: ResultadoEstadistico? = null,
    val allDraws: List<SorteoDomain> = emptyList(),
    val tickets: List<BoletaJugada> = emptyList(),
    val plusNumbers: List<String> = listOf("07", "14", "22", "33", "48", "59", "71", "88"),
    val plusEvaluacion: PlusEvaluacion = QuinielaPlusEngine.validarCombinacion(listOf("07", "14", "22", "33", "48", "59", "71", "88")),
    val fuentesSalud: List<FuenteSaludInfo> = emptyList(),
    val config: ConfiguracionApp = ConfiguracionApp(),
    val syncStatusBanner: String = "",
    val offlineMode: Boolean = false,
    val toastMessage: String? = null
)

class QuinielaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = QuinielaDatabase.getInstance(application)
    private val ticketStore = EncryptedTicketStore(application)
    private val exportManager = ExportManager(application)
    private val calendarProvider = SorteoCalendarProvider()
    private val statisticalEngine = StatisticalEngine(calendarProvider)
    private val dataSourcesManager = DataSourcesManager(db.sorteoDao(), db.fuenteSaludDao(), calendarProvider)

    private val selectedJurisdiccion = MutableStateFlow(Jurisdiccion.PROVINCIA)
    private val selectedVentana = MutableStateFlow(VentanaAnalisis.DIAS_90)
    private val configState = MutableStateFlow(ticketStore.loadConfig())
    private val ticketsState = MutableStateFlow(ticketStore.loadTickets())
    private val plusNumbersState = MutableStateFlow(listOf("07", "14", "22", "33", "48", "59", "71", "88"))
    private val plusBoletasCount = MutableStateFlow(1)
    private val isBootstrapping = MutableStateFlow(true)
    private val isSyncing = MutableStateFlow(false)
    private val syncBanner = MutableStateFlow("")
    private val offlineMode = MutableStateFlow(false)
    private val toastMessage = MutableStateFlow<String?>(null)
    private val statResult = MutableStateFlow<ResultadoEstadistico?>(null)
    private val oppositeResult = MutableStateFlow<ResultadoEstadistico?>(null)

    private val drawsFlow = db.sorteoDao().observeAll().map { list ->
        list.mapNotNull { it.toDomain() }
    }
    private val healthFlow = db.fuenteSaludDao().observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val uiState: StateFlow<MainUiState> = combine(
        isBootstrapping,
        isSyncing,
        selectedJurisdiccion,
        selectedVentana,
        drawsFlow,
        ticketsState,
        plusNumbersState,
        plusBoletasCount,
        healthFlow,
        configState,
        syncBanner,
        offlineMode,
        toastMessage,
        statResult,
        oppositeResult
    ) { values ->
        val booting = values[0] as Boolean
        val syncing = values[1] as Boolean
        val jur = values[2] as Jurisdiccion
        val win = values[3] as VentanaAnalisis
        @Suppress("UNCHECKED_CAST")
        val draws = values[4] as List<SorteoDomain>
        @Suppress("UNCHECKED_CAST")
        val tickets = values[5] as List<BoletaJugada>
        @Suppress("UNCHECKED_CAST")
        val plusNums = values[6] as List<String>
        val plusCnt = values[7] as Int
        @Suppress("UNCHECKED_CAST")
        val health = values[8] as List<FuenteSaludInfo>
        val cfg = values[9] as ConfiguracionApp
        val banner = values[10] as String
        val offline = values[11] as Boolean
        val toast = values[12] as String?
        val stat = values[13] as ResultadoEstadistico?
        val oppStat = values[14] as ResultadoEstadistico?

        val latestNoctProv = draws.firstOrNull { it.loteria == Jurisdiccion.PROVINCIA && it.turno == TurnoSorteo.NOCTURNA }
        val plusEval = if (plusNums.size == 8 && latestNoctProv != null) {
            QuinielaPlusEngine.controlarContraNocturnaProvincia(plusNums, latestNoctProv, plusCnt * QuinielaPlusEngine.DEFAULT_TICKET_COST_ARS)
        } else {
            QuinielaPlusEngine.validarCombinacion(plusNums, plusCnt)
        }

        MainUiState(
            isBootstrapping = booting,
            isSyncing = syncing,
            selectedJurisdiccion = jur,
            selectedVentana = win,
            calendarStatus = calendarProvider.getDayStatus(),
            estadistica = stat,
            estadisticaOpuesta = oppStat,
            allDraws = draws,
            tickets = evaluateTicketsAgainstDraws(tickets, draws),
            plusNumbers = plusNums,
            plusEvaluacion = plusEval,
            fuentesSalud = health,
            config = cfg,
            syncStatusBanner = banner,
            offlineMode = offline,
            toastMessage = toast
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            HistorialBootstrap(getApplication(), db).ensureSeededFromAssets()
            isBootstrapping.value = false
            NotificationScheduler.scheduleAll(getApplication(), configState.value)

            // Recálculo automático ante cada ingreso de nuevos sorteos o cambio de filtro (invalida caché)
            combine(drawsFlow, selectedJurisdiccion, selectedVentana, configState) { draws, jur, win, cfg ->
                Quad(draws, jur, win, cfg)
            }.collect { (draws, jur, win, cfg) ->
                calendarProvider.updateHolidayConfig(cfg.holidayMode, cfg.holidayDates)
                calendarProvider.calibrateFromHistory(draws)
                statResult.value = statisticalEngine.analyze(
                    allDraws = draws,
                    jurisdiccion = jur,
                    ventana = win,
                    topCount = cfg.topCount,
                    avoidImmediateRepeat = cfg.avoidImmediateRepeat,
                    includeCrossJurisdiction = cfg.includeCrossJurisdiction
                )
                val opp = if (jur == Jurisdiccion.PROVINCIA) Jurisdiccion.CIUDAD else Jurisdiccion.PROVINCIA
                oppositeResult.value = statisticalEngine.analyze(
                    allDraws = draws,
                    jurisdiccion = opp,
                    ventana = win,
                    topCount = cfg.topCount,
                    avoidImmediateRepeat = cfg.avoidImmediateRepeat,
                    includeCrossJurisdiction = cfg.includeCrossJurisdiction
                )
            }
        }
        refreshFromSources(includeDirectScrape = true)
    }

    private fun evaluateTicketsAgainstDraws(tickets: List<BoletaJugada>, draws: List<SorteoDomain>): List<BoletaJugada> {
        return tickets.map { t ->
            val match = draws.find { it.loteria == t.jurisdiccion && it.fechaIso == t.fechaIso && it.turno == t.turno }
            if (match == null) {
                t
            } else {
                val aciertos = t.numeros.filter { n ->
                    val clean = n.trim()
                    match.numeros.any { it.endsWith(clean) || it == clean }
                }
                t.copy(aciertos = aciertos, controlado = true)
            }
        }
    }

    fun selectJurisdiccion(jur: Jurisdiccion) {
        selectedJurisdiccion.value = jur
    }

    fun selectVentana(win: VentanaAnalisis) {
        selectedVentana.value = win
    }

    fun refreshFromSources(includeDirectScrape: Boolean = true) {
        if (isSyncing.value) return
        viewModelScope.launch {
            isSyncing.value = true
            val report: SyncReport = runCatching {
                dataSourcesManager.syncAllSources(includeDirectScrape)
            }.getOrElse {
                SyncReport(0, 0, true, "Modo offline · Usando historial local guardado.")
            }
            offlineMode.value = report.offlineFallback
            syncBanner.value = report.mensajeEstado
            isSyncing.value = false
        }
    }

    fun updateConfig(transform: (ConfiguracionApp) -> ConfiguracionApp) {
        val updated = transform(configState.value)
        configState.value = updated
        ticketStore.saveConfig(updated)
        calendarProvider.updateHolidayConfig(updated.holidayMode, updated.holidayDates)
        NotificationScheduler.scheduleAll(getApplication(), updated)
    }

    fun addTicket(jurisdiccion: Jurisdiccion, turno: TurnoSorteo, rawNumbers: String, montoArs: Double) {
        val cleanNums = rawNumbers.split(",", " ", ";")
            .map { it.trim().filter(Char::isDigit) }
            .filter { it.length in 2..4 }
            .distinct()
        if (cleanNums.isEmpty()) {
            toastMessage.value = "Ingresá al menos un número válido de 2 a 4 cifras."
            return
        }
        val hoy = calendarProvider.todayArgentina().toString()
        val boleta = BoletaJugada(
            id = UUID.randomUUID().toString(),
            fechaIso = hoy,
            jurisdiccion = jurisdiccion,
            turno = turno,
            numeros = cleanNums,
            montoArs = montoArs.coerceAtLeast(0.0)
        )
        val updated = listOf(boleta) + ticketsState.value
        ticketsState.value = updated
        ticketStore.saveTickets(updated)
        toastMessage.value = "Boleta guardada de forma cifrada."
    }

    fun deleteTicket(id: String) {
        val updated = ticketsState.value.filterNot { it.id == id }
        ticketsState.value = updated
        ticketStore.saveTickets(updated)
    }

    fun togglePlusNumber(num2: String) {
        val current = plusNumbersState.value.toMutableList()
        if (num2 in current) {
            current.remove(num2)
        } else if (current.size < 8) {
            current.add(num2)
        } else {
            toastMessage.value = "Quiniela Plus permite hasta 8 números. Tocá uno para reemplazarlo."
            return
        }
        plusNumbersState.value = current.sorted()
    }

    fun autocompletarPlusDesdeMotor(seed: Int = 0) {
        val stat = statResult.value ?: return
        plusNumbersState.value = QuinielaPlusEngine.autocompletarDesdeMotor(
            stat.candidatos,
            stat.numerosFrios,
            semillaExtra = seed
        )
    }

    fun setPlusBoletasCount(count: Int) {
        plusBoletasCount.value = count.coerceIn(1, 20)
    }

    fun exportCsv() {
        viewModelScope.launch {
            val draws = uiState.value.allDraws.take(2500)
            val file = exportManager.exportCsvAndShare(draws)
            exportManager.shareFile(file, "text/csv")
        }
    }

    fun exportPdf() {
        viewModelScope.launch {
            val draws = uiState.value.allDraws
            val file = exportManager.exportPdfAndShare(draws)
            exportManager.shareFile(file, "application/pdf")
        }
    }

    fun consumeToast() {
        toastMessage.value = null
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
