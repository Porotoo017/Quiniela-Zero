package com.quinielazero.app.core.plus

/**
 * Modalidades de juego soportadas dentro del submódulo Control de Jugadas de Quiniela Plus.
 */
enum class ModalidadPlus(val label: String, val descripcion: String) {
    QUINIELA_PLUS("Quiniela Plus (Últ. 2)", "Compara tus números contra las últimas 2 cifras de las 20 extracciones."),
    SUPER_PLUS("Súper Plus (Prim. 2)", "Compara contra las primeras 2 cifras de las 20 extracciones (si hay 4 dígitos)."),
    CHANCE_PLUS("Chance Plus (Centrales)", "Compara contra las 2 cifras centrales de las 20 extracciones (si hay 4 dígitos)."),
    CABEZA_EXACTA("A la Cabeza (1° premio)", "Evalúa acierto directo contra el primer número sorteado (posición 1).")
}

/**
 * Estado de resolución de una jugada frente al historial oficial de sorteos Plus.
 */
enum class EstadoJugada(val label: String) {
    GANO("Ganó"),
    NO_GANO("No ganó"),
    PENDIENTE("Pendiente")
}

/**
 * Ventana de sorteos configurable para el motor Análisis Predictivo Plus.
 */
enum class VentanaPlus(val label: String, val cantidadSorteos: Int?) {
    ULTIMOS_10("Últ. 10", 10),
    ULTIMOS_30("Últ. 30", 30),
    ULTIMOS_50("Últ. 50", 50),
    ULTIMOS_100("Últ. 100", 100),
    TOTAL("Histórico", null)
}

/**
 * Registro oficial de un sorteo de Quiniela Plus en el historial persistente.
 * [numerosPlus] contiene los 20 números de 2 cifras (00..99).
 * [numerosCompletos4] opcionalmente guarda los 20 números de 4 cifras (cuando provienen de Nocturna Provincia)
 * para habilitar evaluación de Súper Plus y Chance Plus.
 */
data class PlusSorteo(
    val fechaIso: String,
    val numerosPlus: List<String>,
    val numerosCompletos4: List<String> = emptyList(),
    val origen: String = "Manual",
    val notas: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * Jugada registrada por el usuario en Control de Jugadas.
 */
data class PlusJugada(
    val id: String,
    val fechaIso: String,
    val numeros: List<String>,
    val modalidad: ModalidadPlus = ModalidadPlus.QUINIELA_PLUS,
    val montoApostado: Double = 1000.0,
    val notas: String = "",
    val estado: EstadoJugada = EstadoJugada.PENDIENTE,
    val aciertosExactos: List<String> = emptyList(),
    val aciertosParciales: List<String> = emptyList(),
    val detalleComparacion: String = "Esperando resultado oficial de esa fecha",
    val premioGanado: Double = 0.0
)

/**
 * Reglas configurables para calcular premios y aciertos parciales en Control de Jugadas.
 */
data class PlusReglasPremios(
    val mult8Aciertos: Double = 500.0,
    val mult7Aciertos: Double = 80.0,
    val mult6Aciertos: Double = 15.0,
    val mult5Aciertos: Double = 3.0,
    val multCabezaExacta: Double = 70.0,
    val habilitarParcialAproximacion: Boolean = true,
    val habilitarParcialTerminacion: Boolean = false,
    val multAciertoParcial: Double = 0.5
)

/**
 * Resumen estadístico de Control de Jugadas.
 */
data class PlusResumenJugadas(
    val totalJugadas: Int,
    val jugadasResueltas: Int,
    val jugadasGanadoras: Int,
    val jugadasPendientes: Int,
    val totalApostado: Double,
    val totalGanado: Double,
    val balanceNeto: Double,
    val porcentajeAcierto: Double
)

/**
 * Pesos configurables por el usuario para la ponderación del motor Análisis Predictivo Plus.
 */
data class PlusPesosMotor(
    val pesoFrecuencia: Double = 0.30,
    val pesoAtraso: Double = 0.25,
    val pesoPatrones: Double = 0.25,
    val pesoTemporalMarkov: Double = 0.20
)

data class PlusNumeroStats(
    val numero: String,
    val frecuenciaAbsoluta: Int,
    val frecuenciaRelativaPct: Double,
    val zScore: Double,
    val esOutlier: Boolean,
    val atrasoSorteos: Int,
    val gapMedio: Double,
    val regularidadCiclo: Double,
    val probabilidadMarkov: Double,
    val markovLift: Double,
    val scoreFrecuencia: Double,
    val scoreAtraso: Double,
    val scorePatrones: Double,
    val scoreTemporal: Double,
    val scoreFinal: Double,
    val puntaje0a100: Int,
    val explicacion: String
)

data class PlusCoocurrencia(
    val numeros: List<String>,
    val apariciones: Int,
    val porcentajeSorteos: Double
)

data class PlusTerminacionStats(
    val digito: Int,
    val apariciones: Int,
    val porcentaje: Double,
    val atrasoSorteos: Int
)

data class PlusDecenaStats(
    val etiqueta: String,
    val apariciones: Int,
    val porcentaje: Double
)

data class PlusDiaSemanaStats(
    val diaNombre: String,
    val diaOrden: Int,
    val cantidadSorteos: Int,
    val numerosTop: List<String>
)

data class PlusCicloEstacional(
    val etiquetaMes: String,
    val cantidadSorteos: Int,
    val numerosTop: List<String>,
    val promedioSumaDigitos: Double
)

data class PlusPatronesResumen(
    val paresFrecuentes: List<PlusCoocurrencia>,
    val triosFrecuentes: List<PlusCoocurrencia>,
    val terminaciones: List<PlusTerminacionStats>,
    val distribucionDecenas: List<PlusDecenaStats>,
    val rangoSumaDigitosModal: String,
    val promedioSumaDigitosPorNumero: Double,
    val rangoSuma8SugeridaMin: Int,
    val rangoSuma8SugeridaMax: Int,
    val pctPares: Double,
    val pctImpares: Double,
    val pctAltos: Double,
    val pctBajos: Double,
    val promedioRepeticionesAnterior: Double,
    val ultimoSorteoRepetidos: Int
)

data class PlusCombinacionSugerida(
    val ranking: Int,
    val numeros: List<String>,
    val puntajePromedio: Double,
    val sumaDigitos: Int,
    val paresCount: Int,
    val altosCount: Int,
    val decenasCubiertas: Int,
    val justificacion: String
)

data class PlusResultadoAnalisis(
    val tieneDatos: Boolean,
    val mensajeSinDatos: String = "",
    val ventana: VentanaPlus,
    val totalSorteosAnalizados: Int,
    val totalSorteosHistorico: Int,
    val fechaUltimoSorteo: String,
    val fechaPrimerSorteo: String,
    val mediaApariciones: Double,
    val desvioEstandarApariciones: Double,
    val calientes: List<PlusNumeroStats>,
    val frios: List<PlusNumeroStats>,
    val mayorAtraso: List<PlusNumeroStats>,
    val outliers: List<PlusNumeroStats>,
    val rankingCandidatos: List<PlusNumeroStats>,
    val patrones: PlusPatronesResumen,
    val tendenciasDiaSemana: List<PlusDiaSemanaStats>,
    val estacionalidadMensual: List<PlusCicloEstacional>,
    val combinacionesSugeridas: List<PlusCombinacionSugerida>,
    val pesosAplicados: PlusPesosMotor
)

/**
 * Sugerencia interactiva cuando un archivo CSV/TXT externo tiene encabezados ambiguos
 * y requiere confirmación manual del mapeo una sola vez.
 */
data class PlusCsvMappingSuggestion(
    val fileName: String,
    val rawContent: String,
    val delimiter: Char,
    val hasHeader: Boolean,
    val headers: List<String>,
    val sampleRows: List<List<String>>,
    val suggestedDateCol: Int,
    val suggestedNumberCols: List<Int>,
    val suggestedPackedCol: Int?
)

sealed interface PlusCsvImportResult {
    data class Success(
        val sorteos: List<PlusSorteo>,
        val skippedRows: Int,
        val sourceLabel: String
    ) : PlusCsvImportResult

    data class NeedsMapping(
        val suggestion: PlusCsvMappingSuggestion
    ) : PlusCsvImportResult

    data class Error(val message: String) : PlusCsvImportResult
}
