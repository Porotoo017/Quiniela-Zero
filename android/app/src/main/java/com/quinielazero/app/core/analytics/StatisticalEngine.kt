package com.quinielazero.app.core.analytics

import com.quinielazero.app.core.calendar.SorteoCalendarProvider
import com.quinielazero.app.core.model.CandidatoEstadistico
import com.quinielazero.app.core.model.Jurisdiccion
import com.quinielazero.app.core.model.PuntoEvolucion
import com.quinielazero.app.core.model.QuinielaMeanings
import com.quinielazero.app.core.model.ResultadoEstadistico
import com.quinielazero.app.core.model.SorteoDomain
import com.quinielazero.app.core.model.VentanaAnalisis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Motor Cuantitativo Multi-Variable Pro Max (silencioso en segundo plano):
 *  - Z-Score Binomial / Poisson frente a la esperanza matemática
 *  - EWMA Multi-Horizonte (corto λ=0.08 vs largo λ=0.012)
 *  - Supervivencia / Hazard-Rate de atraso (Z_atraso)
 *  - Markov Lift condicionado a la última cabeza
 *  - Cruce Provincia <-> Ciudad + Balance Chi² de terminaciones
 */
class StatisticalEngine(
    private val calendarProvider: SorteoCalendarProvider
) {
    private data class ScoredSet(
        val candidates: List<CandidatoEstadistico>,
        val hotNumbers: List<CandidatoEstadistico>,
        val coldNumbers: List<CandidatoEstadistico>,
        val entropyBits: Double,
        val chiSqTerminations: Double
    )

    suspend fun analyze(
        allDraws: List<SorteoDomain>,
        jurisdiccion: Jurisdiccion,
        ventana: VentanaAnalisis,
        topCount: Int = 5,
        avoidImmediateRepeat: Boolean = true,
        includeCrossJurisdiction: Boolean = true,
        referenceDate: LocalDate = calendarProvider.todayArgentina()
    ): ResultadoEstadistico = withContext(Dispatchers.Default) {
        val dayStatus = calendarProvider.getDayStatus(referenceDate)
        val cutoffDate = ventana.dias?.let { referenceDate.minusDays(it.toLong()).toString() }

        val targetDraws = allDraws
            .asSequence()
            .filter { it.loteria == jurisdiccion }
            .filter { cutoffDate == null || it.fechaIso >= cutoffDate }
            .sortedWith(compareBy<SorteoDomain> { it.fechaIso }.thenBy { it.turno.orden })
            .toList()

        val oppositeJur = if (jurisdiccion == Jurisdiccion.PROVINCIA) Jurisdiccion.CIUDAD else Jurisdiccion.PROVINCIA
        val oppositeDraws = if (includeCrossJurisdiction) {
            allDraws
                .asSequence()
                .filter { it.loteria == oppositeJur }
                .filter { cutoffDate == null || it.fechaIso >= cutoffDate }
                .sortedWith(compareBy<SorteoDomain> { it.fechaIso }.thenBy { it.turno.orden })
                .toList()
        } else {
            emptyList()
        }

        val totalSorteos = targetDraws.size
        if (totalSorteos == 0) {
            return@withContext ResultadoEstadistico(
                jurisdiccion = jurisdiccion,
                ventana = ventana,
                totalSorteos = 0,
                esDiaDeSorteo = dayStatus.hasDraws,
                esDomingo = dayStatus.isSunday,
                esFeriado = dayStatus.isHoliday,
                tituloModo = if (dayStatus.isSunday) "Sugerencia de jugadas para la semana" else "Sin datos suficientes",
                subtituloCalendario = dayStatus.summaryLabel,
                candidatos = emptyList(),
                evolucionTop = emptyList(),
                numerosCalientes = emptyList(),
                numerosFrios = emptyList(),
                efectividadWalkForwardPct = 0.0,
                entropiaShannonBits = 0.0,
                chiCuadradoTerminaciones = 0.0,
                ultimoActualizado = calendarProvider.nowArgentina().toLocalTime().toString().take(5)
            )
        }

        val scored = computeMultiFactorScores(
            targetDraws = targetDraws,
            oppositeDraws = oppositeDraws,
            topCount = topCount.coerceIn(3, 5),
            avoidImmediateRepeat = avoidImmediateRepeat,
            includeCrossJurisdiction = includeCrossJurisdiction
        )

        val evalWindow = min(30, (totalSorteos - 20).coerceAtLeast(0))
        val walkForwardPct = if (evalWindow >= 5) {
            var hits = 0
            val startIdx = totalSorteos - evalWindow
            for (k in startIdx until totalSorteos) {
                val sub = targetDraws.subList(0, k)
                val stepRes = computeMultiFactorScores(
                    targetDraws = sub,
                    oppositeDraws = oppositeDraws.take((oppositeDraws.size - (totalSorteos - k)).coerceAtLeast(0)),
                    topCount = 5,
                    avoidImmediateRepeat = avoidImmediateRepeat,
                    includeCrossJurisdiction = includeCrossJurisdiction
                )
                val nextNums = targetDraws[k].numeros.mapNotNull { it.takeLast(2) }.toSet()
                if (stepRes.candidates.any { it.numero in nextNums }) {
                    hits++
                }
            }
            (hits.toDouble() / evalWindow.toDouble()) * 100.0
        } else {
            0.0
        }

        val leadNum = scored.candidates.firstOrNull()?.numero
        val evolution = if (leadNum != null) {
            val buckets = 8
            val chunkSize = (totalSorteos / buckets).coerceAtLeast(1)
            targetDraws.chunked(chunkSize).take(buckets).map { chunk ->
                val count = chunk.sumOf { d -> d.numeros.count { it.endsWith(leadNum) } }
                val pct = (count.toDouble() / (chunk.size * 20).coerceAtLeast(1)) * 100.0
                PuntoEvolucion(
                    etiquetaPeriodo = chunk.last().fechaIso.takeLast(5),
                    frecuenciaPct = pct,
                    conteo = count
                )
            }
        } else {
            emptyList()
        }

        val tituloModo = when {
            dayStatus.isSunday -> "Sugerencia de jugadas para la semana"
            dayStatus.isHoliday -> "Modo Feriado · Planificación"
            else -> "Números recomendados"
        }

        ResultadoEstadistico(
            jurisdiccion = jurisdiccion,
            ventana = ventana,
            totalSorteos = totalSorteos,
            esDiaDeSorteo = dayStatus.hasDraws,
            esDomingo = dayStatus.isSunday,
            esFeriado = dayStatus.isHoliday,
            tituloModo = tituloModo,
            subtituloCalendario = dayStatus.summaryLabel,
            candidatos = scored.candidates,
            evolucionTop = evolution,
            numerosCalientes = scored.hotNumbers,
            numerosFrios = scored.coldNumbers,
            efectividadWalkForwardPct = walkForwardPct,
            entropiaShannonBits = scored.entropyBits,
            chiCuadradoTerminaciones = scored.chiSqTerminations,
            ultimoActualizado = calendarProvider.nowArgentina().toLocalTime().toString().take(5)
        )
    }

    private fun computeMultiFactorScores(
        targetDraws: List<SorteoDomain>,
        oppositeDraws: List<SorteoDomain>,
        topCount: Int,
        avoidImmediateRepeat: Boolean,
        includeCrossJurisdiction: Boolean
    ): ScoredSet {
        val n = targetDraws.size
        val freqSlots = IntArray(100)
        val freqDraws = IntArray(100)
        val ewmaShort = DoubleArray(100)
        val ewmaLong = DoubleArray(100)
        val headWeighted = DoubleArray(100)
        val seenIndices = Array(100) { ArrayList<Int>(32) }
        val recentStreakCount = IntArray(100)
        val terminationRecent = IntArray(10)

        val markovFromHead = Array(100) { IntArray(100) }
        val headTotals = IntArray(100)

        val lambdaShort = 0.08
        val lambdaLong = 0.012
        var prevHead = -1

        targetDraws.forEachIndexed { idx, draw ->
            val age = (n - 1 - idx).coerceAtLeast(0)
            val wShort = exp(-lambdaShort * age)
            val wLong = exp(-lambdaLong * age)
            val isRecent = age < 14
            val seenInDraw = BooleanArray(100)

            var currentHead = -1
            draw.numeros.forEachIndexed { pos, num4 ->
                val n2 = num4.takeLast(2).toIntOrNull() ?: return@forEachIndexed
                if (n2 !in 0..99) return@forEachIndexed
                if (pos == 0) currentHead = n2

                freqSlots[n2]++
                val posWeight = when {
                    pos == 0 -> 1.80
                    pos < 5 -> 1.25
                    else -> 1.0
                }
                headWeighted[n2] += wLong * posWeight
                ewmaShort[n2] += wShort * posWeight
                ewmaLong[n2] += wLong * posWeight

                if (!seenInDraw[n2]) {
                    seenInDraw[n2] = true
                    freqDraws[n2]++
                    seenIndices[n2].add(idx)
                    if (isRecent) {
                        recentStreakCount[n2]++
                        terminationRecent[n2 % 10]++
                    }
                    if (prevHead in 0..99) {
                        markovFromHead[prevHead][n2]++
                    }
                }
            }
            if (prevHead in 0..99) headTotals[prevHead]++
            if (currentHead in 0..99) prevHead = currentHead
        }

        val oppositeEcho = DoubleArray(100)
        if (includeCrossJurisdiction && oppositeDraws.isNotEmpty()) {
            val recentOpp = oppositeDraws.takeLast(25)
            recentOpp.forEachIndexed { idx, d ->
                val recency = exp(-0.07 * (recentOpp.size - 1 - idx))
                d.numeros.forEachIndexed { pos, num4 ->
                    val n2 = num4.takeLast(2).toIntOrNull() ?: return@forEachIndexed
                    if (n2 in 0..99) {
                        val mult = if (pos == 0) 2.0 else if (pos < 5) 0.8 else 0.25
                        oppositeEcho[n2] += recency * mult
                    }
                }
            }
        }

        val totalSlotObs = (n * 20).coerceAtLeast(1)
        var entropy = 0.0
        for (i in 0..99) {
            val p = freqSlots[i].toDouble() / totalSlotObs.toDouble()
            if (p > 0.0) entropy -= p * (ln(p) / ln(2.0))
        }

        val sumTerm = terminationRecent.sum().coerceAtLeast(1).toDouble()
        val expTerm = sumTerm / 10.0
        var chiSqTerm = 0.0
        for (d in 0..9) {
            chiSqTerm += (terminationRecent[d] - expTerm).pow(2) / max(expTerm, 1.0)
        }

        val lastDraw = targetDraws.last()
        val lastHead = lastDraw.numeros.firstOrNull()?.takeLast(2)?.toIntOrNull() ?: -1
        val lastDrawSet = lastDraw.numeros.mapNotNull { it.takeLast(2).toIntOrNull() }.toSet()

        val expectedSlots = n * 0.20
        val stdSlots = sqrt(max(1.0, n * 20.0 * 0.01 * 0.99))

        val maxHeadWeighted = headWeighted.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val maxEwmaShort = ewmaShort.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val maxEwmaLong = ewmaLong.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val maxOpp = oppositeEcho.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0

        val allAnalyzed = (0..99).map { num ->
            val appearances = freqSlots[num]
            val drawCount = freqDraws[num]
            val freqRel = appearances.toDouble() / totalSlotObs.toDouble()
            val zFreq = (appearances - expectedSlots) / stdSlots

            val ewmaNormShort = ewmaShort[num] / maxEwmaShort
            val ewmaNormLong = ewmaLong[num] / maxEwmaLong
            val ewmaMomentum = ewmaNormShort - ewmaNormLong

            val historyIdx = seenIndices[num]
            val lastSeen = historyIdx.lastOrNull() ?: -1
            val currentGap = (n - 1) - lastSeen
            val gaps = if (historyIdx.size >= 2) {
                (1 until historyIdx.size).map { historyIdx[it] - historyIdx[it - 1] }
            } else {
                listOf(5)
            }
            val meanGap = gaps.average().coerceAtLeast(1.0)
            val varGap = gaps.map { (it - meanGap).pow(2) }.average()
            val stdGap = sqrt(varGap).coerceAtLeast(1.5)
            val zGap = (currentGap - meanGap) / stdGap
            val survivalDue = (gaps.count { it <= currentGap }.toDouble() / gaps.size.toDouble()) * 100.0

            val pBaseDraw = (drawCount.toDouble() + 1.0) / (n.toDouble() + 5.0)
            val markovLift = if (lastHead in 0..99 && headTotals[lastHead] >= 5) {
                val pCond = (markovFromHead[lastHead][num].toDouble() + 0.5) / (headTotals[lastHead].toDouble() + 2.5)
                (pCond / pBaseDraw).coerceIn(0.4, 3.0)
            } else {
                1.0
            }

            val termDeficit = ((expTerm - terminationRecent[num % 10]) / max(expTerm, 1.0)).coerceIn(-1.0, 1.0)

            val fFreq = (headWeighted[num] / maxHeadWeighted)
            val fMomentum = ((ewmaMomentum + 0.5) / 1.0).coerceIn(0.0, 1.0)
            val fSurvival = (survivalDue / 100.0) * 0.6 + ((zGap + 1.5) / 4.0).coerceIn(0.0, 1.0) * 0.4
            val fMarkov = ((markovLift - 0.5) / 2.0).coerceIn(0.0, 1.0)
            val fCross = if (includeCrossJurisdiction) (oppositeEcho[num] / maxOpp) else 0.0
            val fBalance = ((termDeficit + 1.0) / 2.0)

            val composite = (0.28 * fFreq) +
                (0.16 * fMomentum) +
                (0.22 * fSurvival) +
                (0.16 * fMarkov) +
                (0.12 * fCross) +
                (0.06 * fBalance)

            val margin = 1.96 * sqrt((freqRel * (1.0 - freqRel)).coerceAtLeast(0.0) / totalSlotObs.toDouble())
            val confianza = when {
                n >= 120 -> "Alta"
                n >= 40 -> "Media"
                else -> "Baja"
            }
            val numStr = String.format(Locale.US, "%02d", num)
            val meaning = QuinielaMeanings.buscarPorNumero(numStr)?.significado ?: ""
            val freqPct = freqRel * 100.0
            val errPct = margin * 100.0
            val score100 = (composite * 100.0).toInt().coerceIn(1, 99)

            CandidatoEstadistico(
                numero = numStr,
                score = composite,
                puntajeCuantitativo = score100,
                frecuenciaPct = freqPct,
                margenErrorPct = errPct,
                zScoreFrecuencia = zFreq,
                zScoreAtraso = zGap,
                markovLift = markovLift,
                ewmaMomentum = ewmaMomentum,
                tasaSupervivenciaPct = survivalDue,
                apariciones = appearances,
                atrasoSorteos = currentGap,
                rachaCaliente = recentStreakCount[num] >= 3 || ewmaMomentum > 0.12,
                cruceCoincidente = oppositeEcho[num] >= 1.4,
                confianza = confianza,
                significado = meaning,
                resumen = String.format(
                    Locale.US,
                    "Basado en %d sorteos · Frecuencia %.1f%% (±%.2f%%)",
                    n,
                    freqPct,
                    errPct
                )
            )
        }

        val candidatesPool = if (avoidImmediateRepeat) {
            allAnalyzed.filterNot { (it.numero.toIntOrNull() ?: -1) in lastDrawSet }
        } else {
            allAnalyzed
        }

        val hotNumbers = allAnalyzed
            .sortedWith(compareByDescending<CandidatoEstadistico> { it.ewmaMomentum }.thenByDescending { it.frecuenciaPct })
            .take(5)

        val coldNumbers = allAnalyzed
            .sortedWith(compareByDescending<CandidatoEstadistico> { it.atrasoSorteos }.thenByDescending { it.zScoreAtraso })
            .take(5)

        return ScoredSet(
            candidates = candidatesPool.sortedByDescending { it.score }.take(topCount),
            hotNumbers = hotNumbers,
            coldNumbers = coldNumbers,
            entropyBits = entropy,
            chiSqTerminations = chiSqTerm
        )
    }
}
