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
import com.quinielazero.app.core.model.NumeroConStats
import com.quinielazero.app.core.model.PlusEvaluacion
import com.quinielazero.app.core.model.QuinielaMeanings
import com.quinielazero.app.core.model.ResultadoEstadistico
import com.quinielazero.app.core.model.SorteoDomain
import com.quinielazero.app.core.model.TurnoSorteo
import com.quinielazero.app.core.model.VentanaAnalisis
import com.quinielazero.app.core.network.DataSourcesManager
import com.quinielazero.app.core.network.SyncReport
import com.quinielazero.app.core.plus.ModalidadPlus
import com.quinielazero.app.core.plus.PlusCsvImportResult
import com.quinielazero.app.core.plus.PlusCsvMappingSuggestion
import com.quinielazero.app.core.plus.PlusJugada
import com.quinielazero.app.core.plus.PlusPesosMotor
import com.quinielazero.app.core.plus.PlusReglasPremios
import com.quinielazero.app.core.plus.PlusResultadoAnalisis
import com.quinielazero.app.core.plus.PlusResumenJugadas
import com.quinielazero.app.core.plus.PlusSorteo
import com.quinielazero.app.core.plus.QuinielaPlusAnalyticsEngine
import com.quinielazero.app.core.plus.QuinielaPlusCsvParser
import com.quinielazero.app.core.plus.QuinielaPlusEngine
import com.quinielazero.app.core.plus.VentanaPlus
import com.quinielazero.app.data.local.EncryptedTicketStore
import com.quinielazero.app.data.local.ExportManager
import com.quinielazero.app.data.local.HistorialBootstrap
import com.quinielazero.app.data.local.PlusSorteoEntity
import com.quinielazero.app.data.local.QuinielaDatabase
import com.quinielazero.app.data.local.QuinielaPlusBootstrap
import com.quinielazero.app.data.local.QuinielaPlusPrefsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class QuinielaPlusUiState(
    val isReady: Boolean = false,
    val historial: List<PlusSorteo> = emptyList(),
    val jugadasEvaluadas: List<PlusJugada> = emptyList(),
    val resumenJugadas: PlusResumenJugadas = PlusResumenJugadas(0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0),
    val reglasPremios: PlusReglasPremios = PlusReglasPremios(),
    val ventanaAnalisis: VentanaPlus = VentanaPlus.TOTAL,
    val pesosMotor: PlusPesosMotor = PlusPesosMotor(),
    val topCombinaciones: Int = 4,
    val analisis: PlusResultadoAnalisis? = null,
    val pendingCsvMapping: PlusCsvMappingSuggestion? = null,
    val editingJugada: PlusJugada? = null,
    val defaultFechaIso: String = ""
)

data class MainUiState(
    val isBootstrapping: Boolean = true,
    val isSyncing: Boolean = false,
    val selectedJurisdiccion: Jurisdiccion = Jurisdiccion.PROVINCIA,
    val selectedVentana: VentanaAnalisis = VentanaAnalisis.DIAS_90,
    val calendarStatus: CalendarDayStatus? = null,
    val estadistica: ResultadoEstadistico? = null,
    val estadisticaOpuesta: ResultadoEstadistico? = null,
    val allDraws: List<SorteoDomain> = emptyList(),
    val significadosConStats: List<NumeroConStats> = emptyList(),
    val tickets: List<BoletaJugada> = emptyList(),
    val plusNumbers: List<String> = listOf("07", "14", "22", "33", "48", "59", "71", "88"),
    val plusEvaluacion: PlusEvaluacion = QuinielaPlusEngine.validarCombinacion(listOf("07", "14", "22", "33", "48", "59", "71", "88")),
    val plusModule: QuinielaPlusUiState = QuinielaPlusUiState(),
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

    // Almacenamiento exclusivo del módulo Quiniela Plus
    private val plusPrefsStore = QuinielaPlusPrefsStore(application)
    private val plusBootstrap = QuinielaPlusBootstrap(application, db.plusSorteoDao(), plusPrefsStore)

    private val selectedJurisdiccion = MutableStateFlow(Jurisdiccion.PROVINCIA)
    private val selectedVentana = MutableStateFlow(VentanaAnalisis.DIAS_90)
    private val configState = MutableStateFlow(ticketStore.loadConfig())
    private val ticketsState = MutableStateFlow(ticketStore.loadTickets())
    private val plusNumbersState = MutableStateFlow(listOf("07", "14", "22", "33", "48", "59", "71", "88"))
    private val plusBoletasCount = MutableStateFlow(1)
    private val isBootstrapping = MutableStateFlow(true)
    private val isSyncing = MutableStateFlow(false)
    private val syncBanner = MutableStateFlow("Provincia & Ciudad")
    private val offlineMode = MutableStateFlow(false)
    private val toastMessage = MutableStateFlow<String?>(null)
    private val statResult = MutableStateFlow<ResultadoEstadistico?>(null)
    private val oppositeResult = MutableStateFlow<ResultadoEstadistico?>(null)
    private var lastAutoSyncMillis: Long = 0L

    // Flujos exclusivos del módulo Quiniela Plus
    private val plusReadyState = MutableStateFlow(false)
    private var plusInitStarted = false
    private val plusJugadasRawState = MutableStateFlow(plusPrefsStore.loadJugadas())
    private val plusReglasState = MutableStateFlow(plusPrefsStore.loadReglas())
    private val plusVentanaState = MutableStateFlow(VentanaPlus.TOTAL)
    private val plusPesosState = MutableStateFlow(plusPrefsStore.loadPesos())
    private val plusTopCombosState = MutableStateFlow(4)
    private val plusAnalisisState = MutableStateFlow<PlusResultadoAnalisis?>(null)
    private val plusPendingMappingState = MutableStateFlow<PlusCsvMappingSuggestion?>(null)
    private val plusEditingJugadaState = MutableStateFlow<PlusJugada?>(null)

    private val drawsFlow = db.sorteoDao().observeAll().map { list ->
        list.mapNotNull { it.toDomain() }
    }
    private val healthFlow = db.fuenteSaludDao().observeAll().map { list ->
        list.map { it.toDomain() }
    }
    private val plusHistoryFlow = db.plusSorteoDao().observeAll().map { list ->
        list.map { it.toDomain() }
    }

    private val plusModuleFlow: StateFlow<QuinielaPlusUiState> = combine(
        plusReadyState,
        plusHistoryFlow,
        plusJugadasRawState,
        plusReglasState,
        plusVentanaState,
        plusPesosState,
        plusTopCombosState,
        plusAnalisisState,
        plusPendingMappingState,
        plusEditingJugadaState
    ) { values ->
        val ready = values[0] as Boolean
        @Suppress("UNCHECKED_CAST")
        val historial = values[1] as List<PlusSorteo>
        @Suppress("UNCHECKED_CAST")
        val rawJugadas = values[2] as List<PlusJugada>
        val reglas = values[3] as PlusReglasPremios
        val ventana = values[4] as VentanaPlus
        val pesos = values[5] as PlusPesosMotor
        val topCombos = values[6] as Int
        val analisis = values[7] as PlusResultadoAnalisis?
        val pendingMapping = values[8] as PlusCsvMappingSuggestion?
        val editing = values[9] as PlusJugada?

        val porFecha = historial.associateBy { it.fechaIso }
        val evaluadas = QuinielaPlusEngine.evaluarTodasJugadas(rawJugadas, porFecha, reglas)
        val resumen = QuinielaPlusEngine.calcularResumenJugadas(evaluadas)

        QuinielaPlusUiState(
            isReady = ready,
            historial = historial,
            jugadasEvaluadas = evaluadas,
            resumenJugadas = resumen,
            reglasPremios = reglas,
            ventanaAnalisis = ventana,
            pesosMotor = pesos,
            topCombinaciones = topCombos,
            analisis = analisis,
            pendingCsvMapping = pendingMapping,
            editingJugada = editing,
            defaultFechaIso = calendarProvider.todayArgentina().toString()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuinielaPlusUiState(defaultFechaIso = calendarProvider.todayArgentina().toString()))

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
        oppositeResult,
        plusModuleFlow
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
        val plusModule = values[15] as QuinielaPlusUiState

        val latestNoctProv = draws.firstOrNull { it.loteria == Jurisdiccion.PROVINCIA && it.turno == TurnoSorteo.NOCTURNA }
        val plusEval = if (plusNums.size == 8 && latestNoctProv != null) {
            QuinielaPlusEngine.controlarContraNocturnaProvincia(plusNums, latestNoctProv, plusCnt * QuinielaPlusEngine.DEFAULT_TICKET_COST_ARS)
        } else {
            QuinielaPlusEngine.validarCombinacion(plusNums, plusCnt)
        }

        val meaningsStats = buildMeaningsStats(draws, jur, stat)

        MainUiState(
            isBootstrapping = booting,
            isSyncing = syncing,
            selectedJurisdiccion = jur,
            selectedVentana = win,
            calendarStatus = calendarProvider.getDayStatus(),
            estadistica = stat,
            estadisticaOpuesta = oppStat,
            allDraws = draws,
            significadosConStats = meaningsStats,
            tickets = evaluateTicketsAgainstDraws(tickets, draws),
            plusNumbers = plusNums,
            plusEvaluacion = plusEval,
            plusModule = plusModule,
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
            com.quinielazero.app.notifications.NotificationScheduler.scheduleAll(getApplication(), configState.value)

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
                plusBootstrap.syncFromNocturnaProvincia(draws)
            }
        }

        // Recálculo automático del motor predictivo Plus cada vez que cambia el historial o parámetros
        viewModelScope.launch {
            combine(plusHistoryFlow, plusVentanaState, plusPesosState, plusTopCombosState) { hist, win, pesos, topN ->
                Quad(hist, win, pesos, topN)
            }.collect { (hist, win, pesos, topN) ->
                val computed = withContext(Dispatchers.Default) {
                    QuinielaPlusAnalyticsEngine.analyze(
                        allDrawsDesc = hist,
                        ventana = win,
                        pesos = pesos,
                        topCombinaciones = topN
                    )
                }
                plusAnalisisState.value = computed
            }
        }

        refreshFromSources(includeDirectScrape = true)
    }

    private fun buildMeaningsStats(
        draws: List<SorteoDomain>,
        jur: Jurisdiccion,
        stat: ResultadoEstadistico?
    ): List<NumeroConStats> {
        val jurDraws = draws.filter { it.loteria == jur }
        val counts = IntArray(100)
        val lastIdx = IntArray(100) { -1 }
        val lastDate = Array(100) { "--" }

        jurDraws.forEachIndexed { idx, d ->
            val seen = BooleanArray(100)
            d.numeros.forEach { num4 ->
                val n2 = num4.takeLast(2).toIntOrNull() ?: return@forEach
                if (n2 in 0..99) {
                    counts[n2]++
                    if (!seen[n2]) {
                        seen[n2] = true
                        if (lastIdx[n2] == -1) {
                            lastIdx[n2] = idx
                            lastDate[n2] = d.fechaIso
                        }
                    }
                }
            }
        }
        val topSet = stat?.candidatos?.map { it.numero }?.toSet().orEmpty()
        return QuinielaMeanings.todos.map { sig ->
            val idxNum = sig.numero.toIntOrNull() ?: 0
            val gap = if (lastIdx[idxNum] >= 0) lastIdx[idxNum] else jurDraws.size
            NumeroConStats(
                significado = sig,
                apariciones = counts[idxNum],
                atrasoSorteos = gap,
                ultimaFecha = lastDate[idxNum],
                enTopMotor = sig.numero in topSet
            )
        }
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

    fun onAppResumed() {
        val now = System.currentTimeMillis()
        if (now - lastAutoSyncMillis > 120_000L) {
            refreshFromSources(includeDirectScrape = true)
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
        lastAutoSyncMillis = System.currentTimeMillis()
        viewModelScope.launch {
            isSyncing.value = true
            val report: SyncReport = runCatching {
                dataSourcesManager.syncAllSources(includeDirectScrape)
            }.getOrElse {
                SyncReport(0, 0, true, "Modo offline · Usando historial local guardado.")
            }
            offlineMode.value = report.offlineFallback
            syncBanner.value = if (report.nuevosGuardados > 0) {
                "Provincia & Ciudad · +${report.nuevosGuardados} nuevos"
            } else {
                "Provincia & Ciudad"
            }
            isSyncing.value = false
        }
    }

    fun updateConfig(transform: (ConfiguracionApp) -> ConfiguracionApp) {
        val updated = transform(configState.value)
        configState.value = updated
        ticketStore.saveConfig(updated)
        calendarProvider.updateHolidayConfig(updated.holidayMode, updated.holidayDates)
        com.quinielazero.app.notifications.NotificationScheduler.scheduleAll(getApplication(), updated)
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
        val clean = num2.trim().takeLast(2).padStart(2, '0')
        val current = plusNumbersState.value.toMutableList()
        if (clean in current) {
            current.remove(clean)
        } else if (current.size < 8) {
            current.add(clean)
        } else {
            toastMessage.value = "Quiniela Plus permite 8 números. Tocá uno arriba para liberarlo."
            return
        }
        plusNumbersState.value = current.sorted()
    }

    fun autocompletarPlusDesdeMotor(seed: Int = 0) {
        val analisis = plusAnalisisState.value
        if (analisis != null && analisis.tieneDatos && analisis.combinacionesSugeridas.isNotEmpty()) {
            val combos = analisis.combinacionesSugeridas
            plusNumbersState.value = combos[seed.mod(combos.size)].numeros
            return
        }
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

    // -------------------------------------------------------------------------
    // ACCIONES DEL MÓDULO QUINIELA PLUS (Control, Historial, Análisis)
    // -------------------------------------------------------------------------
    fun onQuinielaPlusOpened() {
        if (plusInitStarted) return
        plusInitStarted = true
        viewModelScope.launch {
            val imported = plusBootstrap.ensureInitialized()
            val currentDraws = runCatching { drawsFlow.first() }.getOrElse { emptyList() }
            plusBootstrap.syncFromNocturnaProvincia(currentDraws)
            plusReadyState.value = true
            if (imported > 0) {
                toastMessage.value = "Historial Plus inicializado automáticamente ($imported sorteos desde CSV)."
            }
        }
    }

    fun saveOrUpdatePlusJugada(
        fechaRaw: String,
        numerosInput: List<String>,
        modalidad: ModalidadPlus,
        montoApostado: Double,
        notas: String
    ): Boolean {
        val fechaIso = QuinielaPlusCsvParser.normalizeDateIso(fechaRaw)
        if (fechaIso == null) {
            toastMessage.value = "Fecha inválida. Usá formato AAAA-MM-DD o DD/MM/AAAA."
            return false
        }
        val cleanNums = numerosInput
            .mapNotNull { tok ->
                val d = tok.trim().filter(Char::isDigit)
                if (d.isEmpty()) null else d.takeLast(2).padStart(2, '0')
            }
            .filter { it.toIntOrNull() in 0..99 }
            .distinct()

        val minRequired = if (modalidad == ModalidadPlus.CABEZA_EXACTA) 1 else 8
        if (cleanNums.size < minRequired || cleanNums.size > 8) {
            toastMessage.value = if (modalidad == ModalidadPlus.CABEZA_EXACTA) {
                "Ingresá entre 1 y 8 números de 2 cifras (00–99)."
            } else {
                "${modalidad.label} requiere exactamente 8 números distintos (tenés ${cleanNums.size}/8)."
            }
            return false
        }

        val editing = plusEditingJugadaState.value
        val item = PlusJugada(
            id = editing?.id ?: UUID.randomUUID().toString(),
            fechaIso = fechaIso,
            numeros = cleanNums.sorted(),
            modalidad = modalidad,
            montoApostado = montoApostado.coerceAtLeast(0.0),
            notas = notas.trim()
        )
        val updated = if (editing != null) {
            plusJugadasRawState.value.map { if (it.id == editing.id) item else it }
        } else {
            listOf(item) + plusJugadasRawState.value
        }.sortedByDescending { it.fechaIso }

        plusJugadasRawState.value = updated
        plusPrefsStore.saveJugadas(updated)
        plusEditingJugadaState.value = null
        toastMessage.value = if (editing != null) "Jugada Plus actualizada." else "Jugada Plus guardada y evaluada."
        return true
    }

    fun startEditPlusJugada(jugada: PlusJugada) {
        plusEditingJugadaState.value = jugada
        if (jugada.numeros.isNotEmpty()) {
            plusNumbersState.value = jugada.numeros
        }
    }

    fun cancelEditPlusJugada() {
        plusEditingJugadaState.value = null
    }

    fun deletePlusJugada(id: String) {
        val updated = plusJugadasRawState.value.filterNot { it.id == id }
        plusJugadasRawState.value = updated
        plusPrefsStore.saveJugadas(updated)
        if (plusEditingJugadaState.value?.id == id) {
            plusEditingJugadaState.value = null
        }
        toastMessage.value = "Jugada eliminada."
    }

    fun registerOfficialPlusResult(
        fechaRaw: String,
        rawNumbersText: String,
        notas: String = ""
    ): Boolean {
        val fechaIso = QuinielaPlusCsvParser.normalizeDateIso(fechaRaw)
        if (fechaIso == null) {
            toastMessage.value = "Fecha del sorteo inválida. Usá AAAA-MM-DD o DD/MM/AAAA."
            return false
        }
        val parsed = QuinielaPlusCsvParser.parseManualDrawNumbers(rawNumbersText)
        if (parsed == null) {
            toastMessage.value = "Ingresá entre 8 y 20 números válidos (2 o 4 cifras separados por espacio o coma)."
            return false
        }
        val (nums2, nums4) = parsed
        val sorteo = PlusSorteo(
            fechaIso = fechaIso,
            numerosPlus = nums2,
            numerosCompletos4 = nums4,
            origen = "Registro Oficial Manual",
            notas = notas.trim().ifEmpty { "${nums2.size} extracciones" }
        )
        viewModelScope.launch(Dispatchers.IO) {
            db.plusSorteoDao().upsertOne(PlusSorteoEntity.fromDomain(sorteo))
        }
        toastMessage.value = "Resultado $fechaIso guardado en Historial y jugadas recalculadas."
        return true
    }

    fun updatePlusReglasPremios(transform: (PlusReglasPremios) -> PlusReglasPremios) {
        val updated = transform(plusReglasState.value)
        plusReglasState.value = updated
        plusPrefsStore.saveReglas(updated)
    }

    fun cargarCombinacionEnControl(numeros: List<String>) {
        plusNumbersState.value = numeros.map { it.takeLast(2).padStart(2, '0') }.distinct().take(8).sorted()
        toastMessage.value = "Combinación cargada en la boleta de Control de Jugadas."
    }

    fun importPlusFileContent(rawText: String, fileName: String) {
        viewModelScope.launch(Dispatchers.Default) {
            when (val res = QuinielaPlusCsvParser.parseFlexibleImport(rawText, fileName)) {
                is PlusCsvImportResult.Success -> applyPlusImportSuccess(res.sorteos, res.sourceLabel, res.skippedRows)
                is PlusCsvImportResult.NeedsMapping -> plusPendingMappingState.value = res.suggestion
                is PlusCsvImportResult.Error -> toastMessage.value = res.message
            }
        }
    }

    fun confirmPlusCsvMapping(dateCol: Int, numberCols: List<Int>, packedCol: Int?) {
        val pending = plusPendingMappingState.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            when (
                val res = QuinielaPlusCsvParser.parseWithMapping(
                    rawText = pending.rawContent,
                    fileName = pending.fileName,
                    delimiter = pending.delimiter,
                    hasHeader = pending.hasHeader,
                    dateCol = dateCol,
                    numberCols = numberCols,
                    packedCol = packedCol
                )
            ) {
                is PlusCsvImportResult.Success -> {
                    plusPendingMappingState.value = null
                    applyPlusImportSuccess(res.sorteos, res.sourceLabel, res.skippedRows)
                }
                is PlusCsvImportResult.Error -> {
                    toastMessage.value = res.message
                }
                is PlusCsvImportResult.NeedsMapping -> Unit
            }
        }
    }

    fun dismissPlusCsvMapping() {
        plusPendingMappingState.value = null
    }

    private suspend fun applyPlusImportSuccess(sorteos: List<PlusSorteo>, sourceLabel: String, skippedRows: Int) {
        val existingDates = db.plusSorteoDao().getAllOnce().map { it.fechaIso }.toSet()
        val nuevos = sorteos.count { it.fechaIso !in existingDates }
        val actualizados = sorteos.size - nuevos
        val entities = sorteos.map { PlusSorteoEntity.fromDomain(it) }
        entities.chunked(400).forEach { chunk -> db.plusSorteoDao().upsertAll(chunk) }
        val skipNote = if (skippedRows > 0) " ($skippedRows filas omitidas)" else ""
        toastMessage.value = "Importación $sourceLabel: +$nuevos nuevos, $actualizados actualizados por fecha$skipNote."
    }

    fun deletePlusSorteo(fechaIso: String) {
        viewModelScope.launch(Dispatchers.IO) {
            plusPrefsStore.markManuallyDeletedDate(fechaIso)
            db.plusSorteoDao().deleteByFecha(fechaIso)
        }
        toastMessage.value = "Sorteo $fechaIso eliminado del historial Plus."
    }

    fun clearAllPlusHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            db.plusSorteoDao().deleteAll()
            plusPrefsStore.setBootstrapDone(true)
        }
        toastMessage.value = "Historial de Quiniela Plus vaciado."
    }

    fun reimportBaseCsvToPlus() {
        viewModelScope.launch {
            val count = plusBootstrap.reimportarCsvBase()
            toastMessage.value = if (count > 0) {
                "CSV histórico importado automáticamente ($count sorteos)."
            } else {
                "No se encontraron registros en el CSV base."
            }
        }
    }

    fun exportPlusHistoryCsv() {
        viewModelScope.launch {
            val draws = uiState.value.plusModule.historial
            if (draws.isEmpty()) {
                toastMessage.value = "No hay sorteos en Quiniela Plus para exportar."
                return@launch
            }
            val file = exportManager.exportPlusCsvAndShare(draws)
            exportManager.shareFile(file, "text/csv")
        }
    }

    fun selectPlusVentana(ventana: VentanaPlus) {
        plusVentanaState.value = ventana
    }

    fun updatePlusPesos(pesos: PlusPesosMotor) {
        plusPesosState.value = pesos
        plusPrefsStore.savePesos(pesos)
    }

    fun setPlusTopCombinaciones(count: Int) {
        plusTopCombosState.value = count.coerceIn(1, 8)
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
