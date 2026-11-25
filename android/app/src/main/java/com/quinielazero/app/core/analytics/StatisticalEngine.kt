package com.quinielazero.app.core.analytics

import com.quinielazero.app.core.calendar.SorteoCalendarProvider
import com.quinielazero.app.core.model.CandidatoEstadistico
import com.quinielazero.app.core.model.Jurisdiccion
import com.quinielazero.app.core.model.PuntoEvolucion
import com.quinielazero.app.core.model.ResultadoEstadistico
import com.quinielazero.app.core.model.SorteoDomain
import com.quinielazero.app.core.model.VentanaAnalisis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

class StatisticalEngine(
    private val calendarProvider: SorteoCalendarProvider
) {
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
                numerosFrios = emptyList(),
                ultimoActualizado = calendarProvider.nowArgentina().toLocalTime().toString().take(5)
            )
        }

        val freqSimple = IntArray(100)
        val freqWeighted = DoubleArray(100)
        val lastSeenIdx = IntArray(100) { -1 }
        val recentStreakCount = IntArray(100)
        val oppositeBonus = DoubleArray(100)

        targetDraws.forEachIndexed { idx, draw ->
            val decay = exp(-0.014 * (totalSorteos - 1 - idx).coerceAtLeast(0))
            val isRecentWindow = idx >= (totalSorteos - 12).coerceAtLeast(0)
            val seenInDraw = BooleanArray(100)

            draw.numeros.forEachIndexed { pos, num4 ->
                val n2 = num4.takeLast(2).toIntOrNull() ?: return@forEachIndexed
                if (n2 !in 0..99) return@forEachIndexed
                freqSimple[n2]++
                val headMultiplier = if (pos == 0) 1.65 else 1.0
                freqWeighted[n2] += decay * headMultiplier
                if (!seenInDraw[n2]) {
                    seenInDraw[n2] = true
                    lastSeenIdx[n2] = idx
                    if (isRecentWindow) recentStreakCount[n2]++
                }
            }
        }

        oppositeDraws.takeLast(30).forEach { draw ->
            draw.numeros.forEachIndexed { pos, num4 ->
                val n2 = num4.takeLast(2).toIntOrNull() ?: return@forEachIndexed
                if (n2 in 0..99) {
                    oppositeBonus[n2] += when {
                        pos == 0 -> 1.5
                        pos < 5 -> 0.5
                        else -> 0.1
                    }
                }
            }
        }

        val lastDrawNumbers = targetDraws.last().numeros.mapNotNull { it.takeLast(2).toIntOrNull() }.toSet()
        val maxWeighted = freqWeighted.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val totalObservations = (totalSorteos * 20).coerceAtLeast(1)

        val candidatesAll = (0..99).mapNotNull { num ->
            if (avoidImmediateRepeat && num in lastDrawNumbers) return@mapNotNull null

            val appearances = freqSimple[num]
            val freqRel = appearances.toDouble() / totalObservations.toDouble()
            val atraso = (totalSorteos - 1) - lastSeenIdx[num]
            val wScore = freqWeighted[num] / maxWeighted
            val coldScore = min(atraso / 24.0, 1.0)
            val streakScore = min(recentStreakCount[num] / 5.0, 1.0)
            val crossScore = if (includeCrossJurisdiction) min(oppositeBonus[num] / 4.5, 1.0) else 0.0

            val composite = (0.48 * wScore) + (0.22 * coldScore) + (0.15 * streakScore) + (0.15 * crossScore)
            val margin = 1.96 * sqrt((freqRel * (1.0 - freqRel)).coerceAtLeast(0.0) / totalObservations.toDouble())
            val confianza = when {
                totalSorteos >= 120 -> "Alta"
                totalSorteos >= 40 -> "Media"
                else -> "Baja"
            }
            val numStr = String.format(Locale.US, "%02d", num)
            val freqPct = freqRel * 100.0
            val errPct = margin * 100.0

            CandidatoEstadistico(
                numero = numStr,
                score = composite,
                frecuenciaPct = freqPct,
                margenErrorPct = errPct,
                apariciones = appearances,
                atrasoSorteos = atraso,
                rachaCaliente = recentStreakCount[num] >= 3,
                cruceCoincidente = oppositeBonus[num] >= 1.5,
                confianza = confianza,
                resumen = String.format(
                    Locale.US,
                    "Basado en %d sorteos, frecuencia %.1f%% (±%.2f%%)",
                    totalSorteos,
                    freqPct,
                    errPct
                )
            )
        }

        val topCandidates = candidatesAll
            .sortedByDescending { it.score }
            .take(topCount.coerceIn(3, 5))

        val coldNumbers = candidatesAll
            .sortedByDescending { it.atrasoSorteos }
            .take(5)

        // Serie de evolución del número #1 destacado dividida en hasta 8 bloques cronológicos
        val leadNum = topCandidates.firstOrNull()?.numero
        val evolution = if (leadNum != null) {
            val buckets = 8
            val chunkSize = (totalSorteos / buckets).coerceAtLeast(1)
            targetDraws.chunked(chunkSize).take(buckets).mapIndexed { i, chunk ->
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
            dayStatus.isHoliday -> "Feriado · Sugerencia preventiva"
            else -> "Estimación estadística del día"
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
            candidatos = topCandidates,
            evolucionTop = evolution,
            numerosFrios = coldNumbers,
            ultimoActualizado = calendarProvider.nowArgentina().toLocalTime().toString().take(5)
        )
    }
}
