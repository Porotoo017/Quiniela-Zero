package com.quinielazero.app

import com.quinielazero.app.core.analytics.StatisticalEngine
import com.quinielazero.app.core.calendar.SorteoCalendarProvider
import com.quinielazero.app.core.model.BoletaJugada
import com.quinielazero.app.core.model.EstadoVerificacion
import com.quinielazero.app.core.model.Jurisdiccion
import com.quinielazero.app.core.model.SorteoDomain
import com.quinielazero.app.core.model.TurnoSorteo
import com.quinielazero.app.core.model.VentanaAnalisis
import com.quinielazero.app.core.network.DataParsers
import com.quinielazero.app.core.network.DataSourcesManager
import com.quinielazero.app.core.network.ParsedDrawCandidate
import com.quinielazero.app.core.plus.QuinielaPlusEngine
import com.quinielazero.app.data.local.FuenteSaludDao
import com.quinielazero.app.data.local.FuenteSaludEntity
import com.quinielazero.app.data.local.SorteoDao
import com.quinielazero.app.data.local.SorteoEntity
import com.quinielazero.app.notifications.NotificationContentBuilder
import com.quinielazero.app.notifications.NotificationScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class QuinielaZeroTests {

    private fun sample20(base: Int = 1000): List<String> =
        (0 until 20).map { (base + it).toString().padStart(4, '0') }

    @Test
    fun parsers_gba_ciudad_vivitusuerte_and_malformed() {
        val gbaHtml = buildString {
            append("""<div class="sorteo"><h3>La Previa</h3><span class="fechaSorteo">24/11/2026</span><span class="nSorteo">41200</span><div class="bolillas">""")
            sample20(300).forEachIndexed { idx, num ->
                append("""<div><span>${(idx + 1).toString().padStart(2, '0')}</span><span>$num</span></div>""")
            }
            append("""</div></div>""")
        }
        val parsedGba = DataParsers.parseGbaHtml(gbaHtml)
        assertEquals(1, parsedGba.size)
        assertEquals("2026-11-24", parsedGba[0].fechaIso)
        assertEquals(TurnoSorteo.LA_PREVIA, parsedGba[0].turno)
        assertEquals(20, parsedGba[0].numeros.size)

        val ciudadJs = """window.RESULTADOS_DATA = [{"sorteo":53211,"fecha":"24\/11\/2026","modalidad":"NOCT","jurisdicciones":{"51":{"numeros":[${
            (1..20).joinToString(",") { """{"pos":"${it.toString().padStart(2, '0')}","val":"${(5000 + it)}"}""" }
        }]},"53":{"numeros":[${
            (1..20).joinToString(",") { """{"pos":"${it.toString().padStart(2, '0')}","val":"${(6000 + it)}"}""" }
        }]}}}];window.POZO_ULTIMO_SORTEO = 0;"""
        val parsedCiudad = DataParsers.parseCiudadResultadosJs(ciudadJs)
        assertEquals(2, parsedCiudad.size)
        assertEquals(setOf(Jurisdiccion.CIUDAD, Jurisdiccion.PROVINCIA), parsedCiudad.map { it.loteria }.toSet())

        val viviHtml = buildString {
            append("""<div id="seccionPizarra" data-fecha-default="2026-11-24"><div class="card"><div class="card-header">Vespertina</div><table data-pizarra-momento="4">""")
            sample20(7000).forEachIndexed { i, n ->
                append("""<tr><td class="caja-slot">${i + 1}.</td><td class="caja-resultado">$n</td></tr>""")
            }
            append("""</table></div></div>""")
        }
        val parsedVivi = DataParsers.parseVivitusuerteHtml(viviHtml, Jurisdiccion.CIUDAD)
        assertEquals(1, parsedVivi.size)
        assertEquals("2026-11-24", parsedVivi[0].fechaIso)
        assertEquals(TurnoSorteo.VESPERTINA, parsedVivi[0].turno)

        assertTrue(DataParsers.parseGbaHtml("<html><div>corrupto</div></html>").isEmpty())
    }

    @Test
    fun calendar_deduces_saturday_from_history_and_blocks_sunday_and_holidays() {
        val cal = SorteoCalendarProvider()
        val nums = sample20(1100)
        // Historia donde los sábados sólo hubo La Previa y Matutina
        val history = listOf(
            SorteoDomain(1, Jurisdiccion.PROVINCIA, "2026-11-20", TurnoSorteo.LA_PREVIA, nums, EstadoVerificacion.HISTORIAL),
            SorteoDomain(2, Jurisdiccion.PROVINCIA, "2026-11-20", TurnoSorteo.NOCTURNA, nums, EstadoVerificacion.HISTORIAL),
            SorteoDomain(3, Jurisdiccion.PROVINCIA, "2026-11-21", TurnoSorteo.LA_PREVIA, nums, EstadoVerificacion.HISTORIAL),
            SorteoDomain(4, Jurisdiccion.PROVINCIA, "2026-11-21", TurnoSorteo.MATUTINA, nums, EstadoVerificacion.HISTORIAL)
        )
        cal.calibrateFromHistory(history)

        // Domingo 2026-11-22 -> 0 sorteos
        val sunday = LocalDate.of(2026, 11, 22)
        assertFalse(cal.getDayStatus(sunday).hasDraws)
        assertTrue(cal.getTurnsForDate(Jurisdiccion.PROVINCIA, sunday).isEmpty())

        // Sábado 2026-11-21 -> dedujo exactamente 2 turnos
        val saturday = LocalDate.of(2026, 11, 21)
        assertEquals(listOf(TurnoSorteo.LA_PREVIA, TurnoSorteo.MATUTINA), cal.getTurnsForDate(Jurisdiccion.PROVINCIA, saturday))

        // Modo feriado activo
        cal.updateHolidayConfig(true, emptySet())
        assertFalse(cal.getDayStatus(LocalDate.of(2026, 11, 23)).hasDraws)
    }

    @Test
    fun statistical_engine_computes_confidence_margin_and_sunday_mode() = runBlocking {
        val cal = SorteoCalendarProvider()
        val draws = (1..25).map { d ->
            val date = LocalDate.of(2026, 10, 25).plusDays(d.toLong())
            val safeDate = if (date.dayOfWeek.value == 7) date.plusDays(1) else date
            SorteoDomain(
                id = d.toLong(),
                loteria = Jurisdiccion.PROVINCIA,
                fechaIso = safeDate.toString(),
                turno = TurnoSorteo.NOCTURNA,
                numeros = sample20(1000 + d),
                estado = EstadoVerificacion.VERIFICADO
            )
        }
        val engine = StatisticalEngine(cal)
        val sundayRes = engine.analyze(
            allDraws = draws,
            jurisdiccion = Jurisdiccion.PROVINCIA,
            ventana = VentanaAnalisis.DIAS_90,
            topCount = 5,
            referenceDate = LocalDate.of(2026, 11, 22)
        )
        assertTrue(sundayRes.esDomingo)
        assertFalse(sundayRes.esDiaDeSorteo)
        assertEquals("Sugerencia de jugadas para la semana", sundayRes.tituloModo)
        assertEquals(5, sundayRes.candidatos.size)
        assertTrue(sundayRes.candidatos.first().resumen.contains("Basado en"))
        assertTrue(sundayRes.candidatos.first().resumen.contains("±"))
    }

    @Test
    fun quiniela_plus_validation_and_hits() {
        val invalid = QuinielaPlusEngine.validarCombinacion(listOf("01", "01", "02"))
        assertFalse(invalid.esValida)

        val picked = listOf("00", "01", "02", "03", "04", "05", "06", "07")
        val draw = SorteoDomain(1, Jurisdiccion.PROVINCIA, "2026-11-24", TurnoSorteo.NOCTURNA, sample20(1000), EstadoVerificacion.VERIFICADO)
        val eval = QuinielaPlusEngine.controlarContraNocturnaProvincia(picked, draw, 1000.0)
        assertTrue(eval.esValida)
        assertEquals(8, eval.aciertosPlus)
        assertEquals("Pozo Mayor (8 aciertos)", eval.premioTier)
    }

    @Test
    fun cross_validation_and_notifications_integration() {
        val cal = SorteoCalendarProvider()
        val mgr = DataSourcesManager(FakeSorteoDao(), FakeHealthDao(), cal)
        val a = sample20(2000)
        val b = sample20(4000)

        val res = mgr.crossValidate(
            listOf(
                ParsedDrawCandidate(Jurisdiccion.PROVINCIA, "2026-11-24", TurnoSorteo.MATUTINA, a, "loteria.gba.gov.ar"),
                ParsedDrawCandidate(Jurisdiccion.PROVINCIA, "2026-11-24", TurnoSorteo.MATUTINA, a, "vivitusuerte.com"),
                ParsedDrawCandidate(Jurisdiccion.CIUDAD, "2026-11-24", TurnoSorteo.MATUTINA, a, "loteriadelaciudad"),
                ParsedDrawCandidate(Jurisdiccion.CIUDAD, "2026-11-24", TurnoSorteo.VESPERTINA, a, "loteriadelaciudad"),
                ParsedDrawCandidate(Jurisdiccion.CIUDAD, "2026-11-24", TurnoSorteo.VESPERTINA, b, "vivitusuerte.com"),
                // Domingo rechazado
                ParsedDrawCandidate(Jurisdiccion.PROVINCIA, "2026-11-22", TurnoSorteo.NOCTURNA, a, "loteria.gba.gov.ar")
            )
        )
        val map = res.associateBy { it.loteria to it.turno }
        assertEquals(EstadoVerificacion.VERIFICADO, map[Jurisdiccion.PROVINCIA to TurnoSorteo.MATUTINA]?.estado)
        assertEquals(EstadoVerificacion.NO_VERIFICADO, map[Jurisdiccion.CIUDAD to TurnoSorteo.MATUTINA]?.estado)
        assertEquals(EstadoVerificacion.PENDIENTE, map[Jurisdiccion.CIUDAD to TurnoSorteo.VESPERTINA]?.estado)
        assertFalse(map.containsKey(Jurisdiccion.PROVINCIA to TurnoSorteo.NOCTURNA))

        // Notificaciones ART 09:00 y 23:00
        val art = ZoneId.of("America/Argentina/Buenos_Aires")
        val now = ZonedDateTime.of(2026, 11, 24, 10, 0, 0, 0, art)
        val nextMorning = NotificationScheduler.computeNextTriggerMillis(9, 0, now)
        assertTrue(nextMorning > now.toInstant().toEpochMilli())

        val (sunTitle, sunBody) = NotificationContentBuilder.buildMorningContent(
            LocalDate.of(2026, 11, 22), cal, listOf("14", "32"), listOf("07", "88")
        )
        assertTrue(sunTitle.contains("Domingo"))
        assertTrue(sunBody.contains("NO hay sorteos"))

        val (_, nightSunBody) = NotificationContentBuilder.buildNightContent(
            LocalDate.of(2026, 11, 22),
            emptyList(),
            listOf(BoletaJugada("1", "2026-11-20", Jurisdiccion.PROVINCIA, TurnoSorteo.NOCTURNA, listOf("14"), 500.0)),
            listOf("14")
        )
        assertTrue(nightSunBody.contains("Hoy domingo no hubo sorteos"))
    }
}

private class FakeSorteoDao : SorteoDao {
    override fun observeAll(): Flow<List<SorteoEntity>> = flowOf(emptyList())
    override suspend fun getAllOnce(): List<SorteoEntity> = emptyList()
    override suspend fun countAll(): Int = 0
    override suspend fun findOne(loteria: String, fecha: String, turno: String): SorteoEntity? = null
    override suspend fun upsertAll(items: List<SorteoEntity>) = Unit
}

private class FakeHealthDao : FuenteSaludDao {
    override fun observeAll(): Flow<List<FuenteSaludEntity>> = flowOf(emptyList())
    override suspend fun upsertAll(items: List<FuenteSaludEntity>) = Unit
}
