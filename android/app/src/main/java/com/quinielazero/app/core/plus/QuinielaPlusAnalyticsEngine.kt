package com.quinielazero.app.core.plus

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Motor estadístico y predictivo EXCLUSIVO para Quiniela Plus.
 *
 * Garantía metodológica:
 *  - 100% determinístico sobre datos históricos reales persistidos en [PlusSorteo].
 *  - PROHIBIDO Math.random() o cualquier generador pseudoaleatorio.
 *  - Si no hay sorteos cargados, informa ausencia de datos sin inventar resultados.
 */
object QuinielaPlusAnalyticsEngine {

    fun analyze(
        allDrawsDesc: List<PlusSorteo>,
        ventana: VentanaPlus = VentanaPlus.TOTAL,
        pesos: PlusPesosMotor = PlusPesosMotor(),
        topCombinaciones: Int = 4
    ): PlusResultadoAnalisis {
        val sortedDesc = allDrawsDesc
            .filter { it.numerosPlus.isNotEmpty() }
            .distinctBy { it.fechaIso }
            .sortedByDescending { it.fechaIso }

        if (sortedDesc.isEmpty()) {
            return PlusResultadoAnalisis(
                tieneDatos = false,
                mensajeSinDatos = "No hay datos históricos de Quiniela Plus cargados. Importá un archivo CSV/JSON/TXT o registrá un sorteo oficial para activar el análisis.",
                ventana = ventana,
                totalSorteosAnalizados = 0,
                totalSorteosHistorico = 0,
                fechaUltimoSorteo = "--",
                fechaPrimerSorteo = "--",
                mediaApariciones = 0.0,
                desvioEstandarApariciones = 0.0,
                calientes = emptyList(),
                frios = emptyList(),
                mayorAtraso = emptyList(),
                outliers = emptyList(),
                rankingCandidatos = emptyList(),
                patrones = PlusPatronesResumen(
                    paresFrecuentes = emptyList(),
                    triosFrecuentes = emptyList(),
                    terminaciones = emptyList(),
                    distribucionDecenas = emptyList(),
                    rangoSumaDigitosModal = "--",
                    promedioSumaDigitosPorNumero = 0.0,
                    rangoSuma8SugeridaMin = 50,
                    rangoSuma8SugeridaMax = 90,
                    pctPares = 0.0,
                    pctImpares = 0.0,
                    pctAltos = 0.0,
                    pctBajos = 0.0,
                    promedioRepeticionesAnterior = 0.0,
                    ultimoSorteoRepetidos = 0
                ),
                tendenciasDiaSemana = emptyList(),
                estacionalidadMensual = emptyList(),
                combinacionesSugeridas = emptyList(),
                pesosAplicados = pesos
            )
        }

        val windowDesc = ventana.cantidadSorteos?.let { sortedDesc.take(it) } ?: sortedDesc
        val chronological = windowDesc.reversed()
        val totalDraws = chronological.size
        val latestDateIso = chronological.last().fechaIso
        val firstDateIso = chronological.first().fechaIso

        // A) Análisis Estadístico Básico
        val basic = computeBasicStats(chronological)

        // B) Análisis de Patrones
        val patterns = computePatterns(chronological)

        // C) Análisis Temporal, Cíclico y Markov
        val temporal = computeTemporalAndMarkov(chronological, basic)

        // D) Entrecruzamiento y Ponderación Determinística
        val normalizedWeights = normalizeWeights(pesos)
        val ranking = scoreAndRankCandidates(
            basic = basic,
            patterns = patterns,
            temporal = temporal,
            pesos = normalizedWeights
        )

        val combinations = buildCoherentCombinations(
            ranking = ranking,
            patrones = patterns.summary,
            desiredCount = topCombinaciones.coerceIn(1, 8)
        )

        val calientes = ranking.sortedWith(
            compareByDescending<PlusNumeroStats> { it.frecuenciaAbsoluta }
                .thenBy { it.atrasoSorteos }
                .thenBy { it.numero }
        ).take(12)

        val frios = ranking.sortedWith(
            compareBy<PlusNumeroStats> { it.frecuenciaAbsoluta }
                .thenByDescending { it.atrasoSorteos }
                .thenBy { it.numero }
        ).take(12)

        val mayorAtraso = ranking.sortedWith(
            compareByDescending<PlusNumeroStats> { it.atrasoSorteos }
                .thenByDescending { it.scoreFinal }
                .thenBy { it.numero }
        ).take(12)

        val outliers = ranking
            .filter { it.esOutlier }
            .sortedByDescending { abs(it.zScore) }

        return PlusResultadoAnalisis(
            tieneDatos = true,
            ventana = ventana,
            totalSorteosAnalizados = totalDraws,
            totalSorteosHistorico = sortedDesc.size,
            fechaUltimoSorteo = latestDateIso,
            fechaPrimerSorteo = firstDateIso,
            mediaApariciones = basic.meanFreq,
            desvioEstandarApariciones = basic.stdDevFreq,
            calientes = calientes,
            frios = frios,
            mayorAtraso = mayorAtraso,
            outliers = outliers,
            rankingCandidatos = ranking,
            patrones = patterns.summary,
            tendenciasDiaSemana = temporal.dayOfWeekStats,
            estacionalidadMensual = temporal.monthlyStats,
            combinacionesSugeridas = combinations,
            pesosAplicados = normalizedWeights
        )
    }

    // -------------------------------------------------------------------------
    // MÓDULO A: Estadística Básica
    // -------------------------------------------------------------------------
    private data class BasicMetrics(
        val freq: IntArray,
        val recentFreq: IntArray,
        val relPct: DoubleArray,
        val zScore: DoubleArray,
        val gap: IntArray,
        val meanGapPerNum: DoubleArray,
        val gapRegularity: DoubleArray,
        val meanFreq: Double,
        val stdDevFreq: Double
    )

    private fun computeBasicStats(chronological: List<PlusSorteo>): BasicMetrics {
        val nDraws = chronological.size
        val freq = IntArray(100)
        val recentFreq = IntArray(100)
        val lastSeenIdx = IntArray(100) { -1 }
        val gapsHistory = Array(100) { mutableListOf<Int>() }
        var totalExtractions = 0

        val recentStartIdx = max(0, nDraws - min(10, nDraws))

        chronological.forEachIndexed { idx, draw ->
            val seenInDraw = BooleanArray(100)
            draw.numerosPlus.forEach { numStr ->
                val v = numStr.toIntOrNull() ?: return@forEach
                if (v in 0..99) {
                    freq[v]++
                    totalExtractions++
                    if (idx >= recentStartIdx) recentFreq[v]++
                    if (!seenInDraw[v]) {
                        seenInDraw[v] = true
                        if (lastSeenIdx[v] >= 0) {
                            gapsHistory[v].add(idx - lastSeenIdx[v])
                        }
                        lastSeenIdx[v] = idx
                    }
                }
            }
        }

        val relPct = DoubleArray(100) { i ->
            if (totalExtractions > 0) (freq[i] * 100.0) / totalExtractions else 0.0
        }
        val gap = IntArray(100) { i ->
            if (lastSeenIdx[i] >= 0) (nDraws - 1) - lastSeenIdx[i] else nDraws
        }

        val meanFreq = freq.average()
        val variance = freq.map { (it - meanFreq) * (it - meanFreq) }.average()
        val stdDevFreq = sqrt(variance).coerceAtLeast(0.0001)
        val zScore = DoubleArray(100) { i -> (freq[i] - meanFreq) / stdDevFreq }

        val meanGap = DoubleArray(100)
        val regularity = DoubleArray(100)
        for (i in 0..99) {
            val gList = gapsHistory[i]
            if (gList.isNotEmpty()) {
                val m = gList.average()
                val sd = sqrt(gList.map { (it - m) * (it - m) }.average())
                val cv = if (m > 0.0) sd / m else 1.0
                meanGap[i] = m
                regularity[i] = 1.0 / (1.0 + cv)
            } else {
                meanGap[i] = max(5.0, nDraws.toDouble())
                regularity[i] = 0.25
            }
        }

        return BasicMetrics(
            freq = freq,
            recentFreq = recentFreq,
            relPct = relPct,
            zScore = zScore,
            gap = gap,
            meanGapPerNum = meanGap,
            gapRegularity = regularity,
            meanFreq = meanFreq,
            stdDevFreq = if (variance > 0.0) stdDevFreq else 0.0
        )
    }

    // -------------------------------------------------------------------------
    // MÓDULO B: Patrones (Pares, Tríos, Terminaciones, Decenas, Suma, Paridad)
    // -------------------------------------------------------------------------
    private data class PatternMetrics(
        val summary: PlusPatronesResumen,
        val endingScore: DoubleArray,
        val decadeScore: DoubleArray,
        val pairAffinityScore: DoubleArray
    )

    private fun computePatterns(chronological: List<PlusSorteo>): PatternMetrics {
        val nDraws = chronological.size
        val pairCounts = HashMap<Int, Int>(4096)
        val tripleCounts = HashMap<Int, Int>(16384)
        val endingCount = IntArray(10)
        val endingLastIdx = IntArray(10) { -1 }
        val decadeCount = IntArray(10)
        val sumBucketCount = HashMap<Int, Int>() // 0..18 digit sum per number -> grouped into 3-unit buckets
        var digitSumTotal = 0L
        var totalNums = 0L
        var evenNums = 0L
        var highNums = 0L
        var repeatSum = 0L
        var lastRepeats = 0

        var prevUnique: Set<Int>? = null

        chronological.forEachIndexed { idx, draw ->
            val unique = draw.numerosPlus
                .mapNotNull { it.toIntOrNull() }
                .filter { it in 0..99 }
                .distinct()
                .sorted()

            if (prevUnique != null) {
                val rep = unique.count { it in prevUnique!! }
                repeatSum += rep
                if (idx == nDraws - 1) lastRepeats = rep
            }
            prevUnique = unique.toSet()

            // Per-number distribution
            val endingsInDraw = BooleanArray(10)
            draw.numerosPlus.forEach { s ->
                val v = s.toIntOrNull() ?: return@forEach
                if (v in 0..99) {
                    totalNums++
                    val end = v % 10
                    val dec = v / 10
                    endingCount[end]++
                    endingsInDraw[end] = true
                    decadeCount[dec]++
                    if (v % 2 == 0) evenNums++
                    if (v >= 50) highNums++
                    val dSum = dec + end
                    digitSumTotal += dSum
                    val bucket = (dSum / 3) * 3
                    sumBucketCount[bucket] = (sumBucketCount[bucket] ?: 0) + 1
                }
            }
            for (d in 0..9) {
                if (endingsInDraw[d]) endingLastIdx[d] = idx
            }

            // Co-occurrence pairs and triples over distinct numbers in draw
            val m = unique.size
            for (i in 0 until m) {
                val a = unique[i]
                for (j in i + 1 until m) {
                    val b = unique[j]
                    val pairKey = (a shl 7) or b
                    pairCounts[pairKey] = (pairCounts[pairKey] ?: 0) + 1
                    for (k in j + 1 until m) {
                        val c = unique[k]
                        val tripleKey = (a shl 14) or (b shl 7) or c
                        tripleCounts[tripleKey] = (tripleCounts[tripleKey] ?: 0) + 1
                    }
                }
            }
        }

        val topPairs = pairCounts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .take(10)
            .map { (key, cnt) ->
                val a = (key ushr 7) and 0x7F
                val b = key and 0x7F
                PlusCoocurrencia(
                    numeros = listOf(fmt2(a), fmt2(b)),
                    apariciones = cnt,
                    porcentajeSorteos = (cnt * 100.0) / nDraws
                )
            }

        val topTriples = tripleCounts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .take(8)
            .map { (key, cnt) ->
                val a = (key ushr 14) and 0x7F
                val b = (key ushr 7) and 0x7F
                val c = key and 0x7F
                PlusCoocurrencia(
                    numeros = listOf(fmt2(a), fmt2(b), fmt2(c)),
                    apariciones = cnt,
                    porcentajeSorteos = (cnt * 100.0) / nDraws
                )
            }

        val terminaciones = (0..9).map { d ->
            PlusTerminacionStats(
                digito = d,
                apariciones = endingCount[d],
                porcentaje = if (totalNums > 0) (endingCount[d] * 100.0) / totalNums else 0.0,
                atrasoSorteos = if (endingLastIdx[d] >= 0) (nDraws - 1) - endingLastIdx[d] else nDraws
            )
        }.sortedByDescending { it.apariciones }

        val decenas = (0..9).map { dec ->
            val lo = fmt2(dec * 10)
            val hi = fmt2(dec * 10 + 9)
            PlusDecenaStats(
                etiqueta = "$lo–$hi",
                apariciones = decadeCount[dec],
                porcentaje = if (totalNums > 0) (decadeCount[dec] * 100.0) / totalNums else 0.0
            )
        }

        val modalBucket = sumBucketCount.maxByOrNull { it.value }?.key ?: 9
        val avgDigitSum = if (totalNums > 0) digitSumTotal.toDouble() / totalNums else 9.0
        val target8SumCenter = (avgDigitSum * 8.0).roundToInt()
        val rangoMin = (target8SumCenter - 14).coerceIn(32, 92)
        val rangoMax = (target8SumCenter + 14).coerceIn(rangoMin + 12, 118)

        val pctPares = if (totalNums > 0) (evenNums * 100.0) / totalNums else 50.0
        val pctAltos = if (totalNums > 0) (highNums * 100.0) / totalNums else 50.0
        val avgRepeats = if (nDraws > 1) repeatSum.toDouble() / (nDraws - 1) else 0.0

        // Per-number pattern scores (0..1)
        val maxEnd = endingCount.maxOrNull()?.coerceAtLeast(1) ?: 1
        val maxDec = decadeCount.maxOrNull()?.coerceAtLeast(1) ?: 1
        val endingScore = DoubleArray(100) { n ->
            val d = n % 10
            val freqNorm = endingCount[d].toDouble() / maxEnd
            val gapNorm = min(1.0, ((if (endingLastIdx[d] >= 0) (nDraws - 1) - endingLastIdx[d] else nDraws) / 4.0))
            0.70 * freqNorm + 0.30 * gapNorm
        }
        val decadeScore = DoubleArray(100) { n ->
            decadeCount[n / 10].toDouble() / maxDec
        }

        // Affinity with latest draw via historical co-occurrence pairs
        val latestSet = chronological.last().numerosPlus.mapNotNull { it.toIntOrNull() }.filter { it in 0..99 }.toSet()
        val rawAffinity = DoubleArray(100)
        for (n in 0..99) {
            var sumAff = 0.0
            for (prev in latestSet) {
                if (prev == n) continue
                val key = if (prev < n) (prev shl 7) or n else (n shl 7) or prev
                sumAff += (pairCounts[key] ?: 0).toDouble()
            }
            rawAffinity[n] = sumAff
        }
        val maxAff = rawAffinity.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val pairAffinityScore = DoubleArray(100) { n -> rawAffinity[n] / maxAff }

        return PatternMetrics(
            summary = PlusPatronesResumen(
                paresFrecuentes = topPairs,
                triosFrecuentes = topTriples,
                terminaciones = terminaciones,
                distribucionDecenas = decenas,
                rangoSumaDigitosModal = "$modalBucket–${min(18, modalBucket + 2)} por número",
                promedioSumaDigitosPorNumero = avgDigitSum,
                rangoSuma8SugeridaMin = rangoMin,
                rangoSuma8SugeridaMax = rangoMax,
                pctPares = pctPares,
                pctImpares = 100.0 - pctPares,
                pctAltos = pctAltos,
                pctBajos = 100.0 - pctAltos,
                promedioRepeticionesAnterior = avgRepeats,
                ultimoSorteoRepetidos = lastRepeats
            ),
            endingScore = endingScore,
            decadeScore = decadeScore,
            pairAffinityScore = pairAffinityScore
        )
    }

    // -------------------------------------------------------------------------
    // MÓDULO C: Análisis Temporal, Estacionalidad y Cadenas de Markov
    // -------------------------------------------------------------------------
    private data class TemporalMetrics(
        val dayOfWeekStats: List<PlusDiaSemanaStats>,
        val monthlyStats: List<PlusCicloEstacional>,
        val markovProb: DoubleArray,
        val markovLift: DoubleArray,
        val temporalScore: DoubleArray
    )

    private fun computeTemporalAndMarkov(
        chronological: List<PlusSorteo>,
        basic: BasicMetrics
    ): TemporalMetrics {
        val nDraws = chronological.size
        val latestDate = runCatching { LocalDate.parse(chronological.last().fechaIso) }.getOrNull()
        val nextExpectedDow = latestDate?.let { d ->
            if (d.dayOfWeek == DayOfWeek.SATURDAY) DayOfWeek.MONDAY else d.dayOfWeek.plus(1)
        } ?: DayOfWeek.MONDAY

        // Day of week grouping
        val dowCounts = HashMap<DayOfWeek, Int>()
        val dowFreq = HashMap<DayOfWeek, IntArray>()
        // Monthly seasonality grouping
        val monthCounts = HashMap<Month, Int>()
        val monthFreq = HashMap<Month, IntArray>()
        val monthDigitSum = HashMap<Month, Long>()
        val monthNumTotal = HashMap<Month, Long>()

        // Consecutive transition matrix (100x100)
        val transCount = Array(100) { IntArray(100) }
        val fromCount = IntArray(100)
        val targetBaseAppearances = IntArray(100)

        chronological.forEachIndexed { idx, draw ->
            val parsedDate = runCatching { LocalDate.parse(draw.fechaIso) }.getOrNull()
            val dow = parsedDate?.dayOfWeek
            val month = parsedDate?.month

            val nums = draw.numerosPlus.mapNotNull { it.toIntOrNull() }.filter { it in 0..99 }

            if (dow != null) {
                dowCounts[dow] = (dowCounts[dow] ?: 0) + 1
                val arr = dowFreq.getOrPut(dow) { IntArray(100) }
                nums.forEach { arr[it]++ }
            }
            if (month != null) {
                monthCounts[month] = (monthCounts[month] ?: 0) + 1
                val arr = monthFreq.getOrPut(month) { IntArray(100) }
                var sumD = 0L
                nums.forEach { v ->
                    arr[v]++
                    sumD += (v / 10) + (v % 10)
                }
                monthDigitSum[month] = (monthDigitSum[month] ?: 0L) + sumD
                monthNumTotal[month] = (monthNumTotal[month] ?: 0L) + nums.size
            }

            if (idx > 0) {
                val prevUnique = chronological[idx - 1].numerosPlus
                    .mapNotNull { it.toIntOrNull() }.filter { it in 0..99 }.distinct()
                val currUnique = nums.distinct()
                currUnique.forEach { targetBaseAppearances[it]++ }
                prevUnique.forEach { a ->
                    fromCount[a]++
                    currUnique.forEach { b ->
                        transCount[a][b]++
                    }
                }
            }
        }

        val dayNames = mapOf(
            DayOfWeek.MONDAY to "Lunes",
            DayOfWeek.TUESDAY to "Martes",
            DayOfWeek.WEDNESDAY to "Miércoles",
            DayOfWeek.THURSDAY to "Jueves",
            DayOfWeek.FRIDAY to "Viernes",
            DayOfWeek.SATURDAY to "Sábado",
            DayOfWeek.SUNDAY to "Domingo"
        )
        val monthNames = mapOf(
            Month.JANUARY to "Enero", Month.FEBRUARY to "Febrero", Month.MARCH to "Marzo",
            Month.APRIL to "Abril", Month.MAY to "Mayo", Month.JUNE to "Junio",
            Month.JULY to "Julio", Month.AUGUST to "Agosto", Month.SEPTEMBER to "Septiembre",
            Month.OCTOBER to "Octubre", Month.NOVEMBER to "Noviembre", Month.DECEMBER to "Diciembre"
        )

        val dowStats = DayOfWeek.entries.mapNotNull { dow ->
            val cnt = dowCounts[dow] ?: return@mapNotNull null
            val arr = dowFreq[dow] ?: IntArray(100)
            val top = (0..99).sortedWith(compareByDescending<Int> { arr[it] }.thenBy { it }).take(5).map(::fmt2)
            PlusDiaSemanaStats(
                diaNombre = dayNames[dow] ?: dow.name,
                diaOrden = dow.value,
                cantidadSorteos = cnt,
                numerosTop = top
            )
        }.sortedBy { it.diaOrden }

        val monthlyStats = Month.entries.mapNotNull { m ->
            val cnt = monthCounts[m] ?: return@mapNotNull null
            val arr = monthFreq[m] ?: IntArray(100)
            val top = (0..99).sortedWith(compareByDescending<Int> { arr[it] }.thenBy { it }).take(5).map(::fmt2)
            val tot = monthNumTotal[m] ?: 0L
            val avgS = if (tot > 0L) (monthDigitSum[m] ?: 0L).toDouble() / tot else 0.0
            PlusCicloEstacional(
                etiquetaMes = monthNames[m] ?: m.name,
                cantidadSorteos = cnt,
                numerosTop = top,
                promedioSumaDigitos = avgS
            )
        }

        // Markov transition from latest draw
        val latestUnique = chronological.last().numerosPlus
            .mapNotNull { it.toIntOrNull() }.filter { it in 0..99 }.distinct()
        val markovProb = DoubleArray(100)
        val markovLift = DoubleArray(100)
        val transitionsTotal = max(1, nDraws - 1)

        for (b in 0..99) {
            val baseRate = ((targetBaseAppearances[b] + 0.2) / (transitionsTotal + 1.0)).coerceAtLeast(0.01)
            if (latestUnique.isNotEmpty()) {
                var sumP = 0.0
                var activeSources = 0
                latestUnique.forEach { a ->
                    if (fromCount[a] > 0) {
                        // Laplace smoothing aligned to 20% draw inclusion rate
                        sumP += (transCount[a][b] + 0.20) / (fromCount[a] + 1.0)
                        activeSources++
                    }
                }
                val p = if (activeSources > 0) sumP / activeSources else baseRate
                markovProb[b] = p
                markovLift[b] = p / baseRate
            } else {
                markovProb[b] = baseRate
                markovLift[b] = 1.0
            }
        }

        // Same-day-of-week & current-month affinity
        val targetDowArr = dowFreq[nextExpectedDow] ?: IntArray(100)
        val maxDow = targetDowArr.maxOrNull()?.coerceAtLeast(1) ?: 1
        val maxLift = markovLift.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val minLift = markovLift.minOrNull() ?: 1.0
        val liftSpan = (maxLift - minLift).coerceAtLeast(0.05)

        val temporalScore = DoubleArray(100) { i ->
            val liftNorm = ((markovLift[i] - minLift) / liftSpan).coerceIn(0.0, 1.0)
            val dowNorm = targetDowArr[i].toDouble() / maxDow
            val regNorm = basic.gapRegularity[i].coerceIn(0.0, 1.0)
            0.55 * liftNorm + 0.25 * dowNorm + 0.20 * regNorm
        }

        return TemporalMetrics(
            dayOfWeekStats = dowStats,
            monthlyStats = monthlyStats,
            markovProb = markovProb,
            markovLift = markovLift,
            temporalScore = temporalScore
        )
    }

    // -------------------------------------------------------------------------
    // MÓDULO D: Ponderación Cruzada + Combinaciones Coherentes Determinísticas
    // -------------------------------------------------------------------------
    private fun normalizeWeights(p: PlusPesosMotor): PlusPesosMotor {
        val f = p.pesoFrecuencia.coerceAtLeast(0.0)
        val a = p.pesoAtraso.coerceAtLeast(0.0)
        val pat = p.pesoPatrones.coerceAtLeast(0.0)
        val t = p.pesoTemporalMarkov.coerceAtLeast(0.0)
        val sum = f + a + pat + t
        return if (sum <= 0.0001) {
            PlusPesosMotor(0.25, 0.25, 0.25, 0.25)
        } else {
            PlusPesosMotor(f / sum, a / sum, pat / sum, t / sum)
        }
    }

    private fun scoreAndRankCandidates(
        basic: BasicMetrics,
        patterns: PatternMetrics,
        temporal: TemporalMetrics,
        pesos: PlusPesosMotor
    ): List<PlusNumeroStats> {
        val maxFreq = basic.freq.maxOrNull()?.coerceAtLeast(1) ?: 1
        val minFreq = basic.freq.minOrNull() ?: 0
        val freqSpan = (maxFreq - minFreq).coerceAtLeast(1).toDouble()

        val maxRecent = basic.recentFreq.maxOrNull()?.coerceAtLeast(1) ?: 1
        val maxGap = basic.gap.maxOrNull()?.coerceAtLeast(1) ?: 1

        val rawScores = DoubleArray(100)
        val sFreqArr = DoubleArray(100)
        val sGapArr = DoubleArray(100)
        val sPatArr = DoubleArray(100)
        val sTempArr = DoubleArray(100)

        for (i in 0..99) {
            val freqNorm = (basic.freq[i] - minFreq) / freqSpan
            val recentNorm = basic.recentFreq[i].toDouble() / maxRecent
            val sFreq = (0.65 * freqNorm + 0.35 * recentNorm).coerceIn(0.0, 1.0)

            val ratioGap = (basic.gap[i].toDouble() / basic.meanGapPerNum[i].coerceAtLeast(1.0))
            val gapNorm = min(1.0, basic.gap[i].toDouble() / maxGap)
            val cyclePressure = min(1.0, ratioGap / 2.2)
            val sGap = (0.50 * gapNorm + 0.50 * cyclePressure).coerceIn(0.0, 1.0)

            val sPat = (
                0.45 * patterns.endingScore[i] +
                    0.25 * patterns.decadeScore[i] +
                    0.30 * patterns.pairAffinityScore[i]
                ).coerceIn(0.0, 1.0)

            val sTemp = temporal.temporalScore[i].coerceIn(0.0, 1.0)

            sFreqArr[i] = sFreq
            sGapArr[i] = sGap
            sPatArr[i] = sPat
            sTempArr[i] = sTemp

            rawScores[i] =
                pesos.pesoFrecuencia * sFreq +
                pesos.pesoAtraso * sGap +
                pesos.pesoPatrones * sPat +
                pesos.pesoTemporalMarkov * sTemp
        }

        val minScore = rawScores.minOrNull() ?: 0.0
        val maxScore = rawScores.maxOrNull() ?: 1.0
        val span = (maxScore - minScore).coerceAtLeast(0.0001)

        return (0..99).map { i ->
            val scaled0to100 = (35.0 + ((rawScores[i] - minScore) / span) * 64.0).roundToInt().coerceIn(1, 99)
            val isOutlier = abs(basic.zScore[i]) >= 1.8
            val explicacion = buildString {
                append("Apareció ${basic.freq[i]}x (${String.format(Locale.US, "%.2f", basic.relPct[i])}%)")
                append(" · Atraso ${basic.gap[i]} (media ${String.format(Locale.US, "%.1f", basic.meanGapPerNum[i])})")
                append(" · Term.${i % 10}")
                append(" · Markov ${String.format(Locale.US, "%.2f", temporal.markovLift[i])}x")
                if (isOutlier) {
                    append(if (basic.zScore[i] > 0) " · Atípico alto (z=+${String.format(Locale.US, "%.1f", basic.zScore[i])})" else " · Atípico bajo (z=${String.format(Locale.US, "%.1f", basic.zScore[i])})")
                }
            }
            PlusNumeroStats(
                numero = fmt2(i),
                frecuenciaAbsoluta = basic.freq[i],
                frecuenciaRelativaPct = basic.relPct[i],
                zScore = basic.zScore[i],
                esOutlier = isOutlier,
                atrasoSorteos = basic.gap[i],
                gapMedio = basic.meanGapPerNum[i],
                regularidadCiclo = basic.gapRegularity[i],
                probabilidadMarkov = temporal.markovProb[i],
                markovLift = temporal.markovLift[i],
                scoreFrecuencia = sFreqArr[i],
                scoreAtraso = sGapArr[i],
                scorePatrones = sPatArr[i],
                scoreTemporal = sTempArr[i],
                scoreFinal = rawScores[i],
                puntaje0a100 = scaled0to100,
                explicacion = explicacion
            )
        }.sortedWith(
            compareByDescending<PlusNumeroStats> { it.scoreFinal }
                .thenByDescending { it.frecuenciaAbsoluta }
                .thenBy { it.numero }
        )
    }

    /**
     * Genera hasta [desiredCount] combinaciones de 8 números usando búsqueda determinística
     * (sin aleatoriedad) filtrada por coherencia estadística histórica:
     *   - Suma de dígitos dentro del rango histórico sugerido
     *   - Balance par/impar (entre 2 y 6 pares)
     *   - Balance altos/bajos (entre 2 y 6 altos 50..99)
     *   - Cobertura de al menos 4 decenas distintas
     */
    private fun buildCoherentCombinations(
        ranking: List<PlusNumeroStats>,
        patrones: PlusPatronesResumen,
        desiredCount: Int
    ): List<PlusCombinacionSugerida> {
        if (ranking.size < 8) return emptyList()
        val statsByNum = ranking.associateBy { it.numero }
        val topPool = ranking.take(28).map { it.numero }

        val pickedCombinations = mutableListOf<List<String>>()

        // Deterministic candidate generation across shifted anchors in topPool
        for (startAnchor in 0 until min(14, topPool.size)) {
            if (pickedCombinations.size >= desiredCount) break

            val chosen = mutableListOf<String>()
            val anchor = topPool[startAnchor]
            chosen.add(anchor)

            // Greedily pick next best number respecting diversity and coherence
            val candidateOrder = topPool.drop(startAnchor + 1) + topPool.take(startAnchor)
            for (cand in candidateOrder) {
                if (chosen.size == 8) break
                if (cand in chosen) continue

                val tentative = chosen + cand
                val remaining = 8 - tentative.size
                val v = cand.toInt()
                val dec = v / 10

                // Prevent overloading single decade early (>2 per decade)
                val sameDecCount = tentative.count { (it.toInt() / 10) == dec }
                if (sameDecCount > 2 && remaining > 0) continue

                // Ensure parity and high/low can still finish in [2..6]
                val pares = tentative.count { it.toInt() % 2 == 0 }
                val impares = tentative.size - pares
                val altos = tentative.count { it.toInt() >= 50 }
                val bajos = tentative.size - altos
                if (pares > 6 || impares > 6 || altos > 6 || bajos > 6) continue

                // If finishing 8th slot, verify digit sum range and tens spread >= 4
                if (remaining == 0) {
                    val sumDig = tentative.sumOf { val x = it.toInt(); (x / 10) + (x % 10) }
                    val tensCovered = tentative.map { it.toInt() / 10 }.distinct().size
                    val inSumRange = sumDig in (patrones.rangoSuma8SugeridaMin - 8)..(patrones.rangoSuma8SugeridaMax + 8)
                    if (!inSumRange || tensCovered < 4) continue
                }

                chosen.add(cand)
            }

            if (chosen.size == 8) {
                val sortedCombo = chosen.sorted()
                val diverseEnough = pickedCombinations.all { existing ->
                    sortedCombo.count { it !in existing } >= 2
                }
                if (diverseEnough) {
                    pickedCombinations.add(sortedCombo)
                }
            }
        }

        // Deterministic fallback fill if strict filters produced fewer than desiredCount
        if (pickedCombinations.size < desiredCount) {
            var offset = 0
            while (pickedCombinations.size < desiredCount && offset + 8 <= topPool.size) {
                val slice = topPool.subList(offset, offset + 8).sorted()
                if (slice !in pickedCombinations) {
                    pickedCombinations.add(slice)
                }
                offset += 2
            }
        }

        return pickedCombinations.mapIndexed { idx, nums ->
            val avgPts = nums.mapNotNull { statsByNum[it]?.puntaje0a100 }.average()
            val sumDig = nums.sumOf { val x = it.toInt(); (x / 10) + (x % 10) }
            val pares = nums.count { it.toInt() % 2 == 0 }
            val altos = nums.count { it.toInt() >= 50 }
            val tens = nums.map { it.toInt() / 10 }.distinct().size
            val justificacion = "Score medio ${String.format(Locale.US, "%.1f", avgPts)} · Suma díg. $sumDig (rango ${patrones.rangoSuma8SugeridaMin}–${patrones.rangoSuma8SugeridaMax}) · ${pares}P/${8 - pares}I · ${altos}A/${8 - altos}B · $tens decenas"
            PlusCombinacionSugerida(
                ranking = idx + 1,
                numeros = nums,
                puntajePromedio = avgPts,
                sumaDigitos = sumDig,
                paresCount = pares,
                altosCount = altos,
                decenasCubiertas = tens,
                justificacion = justificacion
            )
        }
    }

    private fun fmt2(n: Int): String = n.coerceIn(0, 99).toString().padStart(2, '0')
}
