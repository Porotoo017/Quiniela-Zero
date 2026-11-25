package com.quinielazero.app.core.model

enum class Jurisdiccion(val label: String, val dbValue: String) {
    PROVINCIA("Provincia", "PROVINCIA"),
    CIUDAD("Ciudad", "CIUDAD");

    companion object {
        fun fromString(raw: String): Jurisdiccion =
            if (raw.uppercase().contains("CIUDAD")) CIUDAD else PROVINCIA
    }
}

enum class TurnoSorteo(val dbValue: String, val label: String, val horaAprox: String, val orden: Int) {
    LA_PREVIA("LA PREVIA", "La Previa", "10:15", 0),
    PRIMERA("PRIMERA", "Primera", "12:00", 1),
    MATUTINA("MATUTINA", "Matutina", "15:00", 2),
    VESPERTINA("VESPERTINA", "Vespertina", "18:00", 3),
    NOCTURNA("NOCTURNA", "Nocturna", "21:00", 4);

    companion object {
        fun normalizar(raw: String): TurnoSorteo? {
            val up = raw.uppercase().trim()
            return when {
                "PREVIA" in up || up == "PREV" -> LA_PREVIA
                "PRIM" in up -> PRIMERA
                "MAT" in up -> MATUTINA
                "VESP" in up -> VESPERTINA
                "NOCT" in up -> NOCTURNA
                else -> null
            }
        }
    }
}

enum class EstadoVerificacion(val dbValue: String, val label: String) {
    VERIFICADO("VERIFICADO", "Verificado"),
    NO_VERIFICADO("NO VERIFICADO", "No verificado"),
    PENDIENTE("PENDIENTE", "Pendiente"),
    HISTORIAL("HISTORIAL", "Histórico");

    companion object {
        fun fromString(raw: String): EstadoVerificacion = when (raw.uppercase().trim()) {
            "VERIFICADO" -> VERIFICADO
            "NO VERIFICADO" -> NO_VERIFICADO
            "PENDIENTE" -> PENDIENTE
            else -> HISTORIAL
        }
    }
}

enum class VentanaAnalisis(val label: String, val dias: Int?) {
    DIAS_30("30 días", 30),
    DIAS_60("60 días", 60),
    DIAS_90("90 días", 90),
    TODO("Todo", null)
}

enum class ThemeMode(val label: String) {
    AUTO("Automático"),
    DARK("Oscuro"),
    LIGHT("Claro")
}

data class SorteoDomain(
    val id: Long = 0,
    val loteria: Jurisdiccion,
    val fechaIso: String,
    val turno: TurnoSorteo,
    val numeros: List<String>,
    val estado: EstadoVerificacion,
    val fuentePrimaria: String = "",
    val fuenteSecundaria: String = "",
    val nroSorteo: String = ""
) {
    val cabeza: String get() = numeros.firstOrNull() ?: "----"
    val cabezaDosCifras: String get() = cabeza.takeLast(2)
}

data class CandidatoEstadistico(
    val numero: String,
    val score: Double,
    val frecuenciaPct: Double,
    val margenErrorPct: Double,
    val apariciones: Int,
    val atrasoSorteos: Int,
    val rachaCaliente: Boolean,
    val cruceCoincidente: Boolean,
    val confianza: String,
    val resumen: String
)

data class PuntoEvolucion(
    val etiquetaPeriodo: String,
    val frecuenciaPct: Double,
    val conteo: Int
)

data class ResultadoEstadistico(
    val jurisdiccion: Jurisdiccion,
    val ventana: VentanaAnalisis,
    val totalSorteos: Int,
    val esDiaDeSorteo: Boolean,
    val esDomingo: Boolean,
    val esFeriado: Boolean,
    val tituloModo: String,
    val subtituloCalendario: String,
    val candidatos: List<CandidatoEstadistico>,
    val evolucionTop: List<PuntoEvolucion>,
    val numerosFrios: List<CandidatoEstadistico>,
    val ultimoActualizado: String
)

data class BoletaJugada(
    val id: String,
    val fechaIso: String,
    val jurisdiccion: Jurisdiccion,
    val turno: TurnoSorteo,
    val numeros: List<String>,
    val montoArs: Double,
    val aciertos: List<String> = emptyList(),
    val controlado: Boolean = false
)

data class PlusEvaluacion(
    val numeros8: List<String>,
    val esValida: Boolean,
    val motivo: String,
    val costoArs: Double,
    val aciertosPlus: Int = 0,
    val aciertosSuperPlus: Int = 0,
    val aciertosChancePlus: Int = 0,
    val premioTier: String = "Sin controlar"
)

data class FuenteSaludInfo(
    val nombre: String,
    val jurisdiccion: String,
    val estado: String,
    val httpStatus: Int,
    val latenciaMs: Int,
    val detalle: String,
    val checkedAt: String
)

data class ConfiguracionApp(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val holidayMode: Boolean = false,
    val holidayDates: Set<String> = emptySet(),
    val morningHour: Int = 9,
    val morningMinute: Int = 0,
    val nightHour: Int = 23,
    val nightMinute: Int = 0,
    val mutedDays: Set<Int> = emptySet(), // 1=Mon .. 7=Sun (java.time DayOfWeek.value)
    val dailyLimitArs: Double = 5000.0,
    val weeklyLimitArs: Double = 20000.0,
    val avoidImmediateRepeat: Boolean = true,
    val topCount: Int = 5,
    val includeCrossJurisdiction: Boolean = true
)
