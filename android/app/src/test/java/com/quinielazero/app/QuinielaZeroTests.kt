package com.quinielazero.app

import com.quinielazero.app.core.analytics.StatisticalEngine
import com.quinielazero.app.core.calendar.SorteoCalendarProvider
import com.quinielazero.app.core.model.BoletaJugada
import com.quinielazero.app.core.model.EstadoVerificacion
import com.quinielazero.app.core.model.Jurisdiccion
import com.quinielazero.app.core.model.QuinielaMeanings
import com.quinielazero.app.core.model.SorteoDomain
import com.quinielazero.app.core.model.TurnoSorteo
import com.quinielazero.app.core.model.VentanaAnalisis
import com.quinielazero.app.core.network.DataParsers
import com.quinielazero.app.core.network.DataSourcesManager
import com.quinielazero.app.core.network.ParsedDrawCandidate
import com.quinielazero.app.core.plus.EstadoJugada
import com.quinielazero.app.core.plus.ModalidadPlus
import com.quinielazero.app.core.plus.PlusCsvImportResult
import com.quinielazero.app.core.plus.PlusJugada
import com.quinielazero.app.core.plus.PlusPesosMotor
import com.quinielazero.app.core.plus.PlusReglasPremios
import com.quinielazero.app.core.plus.PlusSorteo
import com.quinielazero.app.core.plus.QuinielaPlusAnalyticsEngine
import com.quinielazero.app.core.plus.QuinielaPlusCsvParser
import com.quinielazero.app.core.plus.QuinielaPlusEngine
import com.quinielazero.app.core.plus.VentanaPlus
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


    @Test
    fun meanings_table_complete_and_quantitative_metrics_populated() = runBlocking {
        assertEquals(100, QuinielaMeanings.todos.size)
        assertEquals("El Muerto que habla", QuinielaMeanings.buscarPorNumero("48")?.significado)
        assertEquals("Los Huevos", QuinielaMeanings.buscarPorNumero("00")?.significado)
        assertEquals("Los Hermanos", QuinielaMeanings.buscarPorNumero("99")?.significado)

        val cal = SorteoCalendarProvider()
        val draws = (1..35).map { d ->
            val date = LocalDate.of(2026, 10, 10).plusDays(d.toLong())
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
        val res = StatisticalEngine(cal).analyze(draws, Jurisdiccion.PROVINCIA, VentanaAnalisis.DIAS_90, topCount = 5)
        assertTrue(res.entropiaShannonBits > 0.0)
        assertTrue(res.candidatos.first().puntajeCuantitativo in 1..99)
        assertTrue(res.candidatos.first().markovLift > 0.0)
        assertTrue(res.candidatos.first().significado.isNotEmpty())
        assertTrue(res.numerosCalientes.isNotEmpty())
        assertTrue(res.numerosFrios.isNotEmpty())
    }

    @Test
    fun quiniela_plus_csv_parser_auto_asset_flexible_and_mapping() {
        val assetCsv = buildString {
            appendLine("fecha,juego,turno," + (1..20).joinToString(",") { "n$it" })
            appendLine("2026-11-24,PROVINCIA,NOCTURNA," + sample20(1000).joinToString(","))
            appendLine("2026-11-24,PROVINCIA,MATUTINA," + sample20(2000).joinToString(","))
            appendLine("2026-11-23,PROVINCIA,NOCTURNA," + sample20(1100).joinToString(","))
        }
        val parsed = QuinielaPlusCsvParser.parseAssetCsv(assetCsv, "provincia_completo.csv")
        assertEquals(2, parsed.size)
        assertEquals("2026-11-24", parsed.first().fechaIso)
        assertEquals(20, parsed.first().numerosPlus.size)
        assertEquals(20, parsed.first().numerosCompletos4.size)
        assertEquals("00", parsed.first().numerosPlus.first())

        assertEquals("2026-11-24", QuinielaPlusCsvParser.normalizeDateIso("24/11/2026"))
        assertEquals("2026-11-24", QuinielaPlusCsvParser.normalizeDateIso("24-11-2026"))

        val ambiguousCsv = "colA;colB\n24/11/2026;01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20"
        val flex = QuinielaPlusCsvParser.parseFlexibleImport(ambiguousCsv, "custom.txt")
        assertTrue(flex is PlusCsvImportResult.NeedsMapping)
        val mapping = (flex as PlusCsvImportResult.NeedsMapping).suggestion
        val resolved = QuinielaPlusCsvParser.parseWithMapping(
            rawText = mapping.rawContent,
            fileName = mapping.fileName,
            delimiter = mapping.delimiter,
            hasHeader = mapping.hasHeader,
            dateCol = 0,
            numberCols = emptyList(),
            packedCol = 1
        )
        assertTrue(resolved is PlusCsvImportResult.Success)
        assertEquals(1, (resolved as PlusCsvImportResult.Success).sorteos.size)
    }

    @Test
    fun quiniela_plus_control_jugadas_evaluation_and_summary() {
        val sorteo = PlusSorteo(
            fechaIso = "2026-11-24",
            numerosPlus = (0 until 20).map { it.toString().padStart(2, '0') },
            numerosCompletos4 = sample20(1000)
        )
        val reglas = PlusReglasPremios()

        val ganadora = QuinielaPlusEngine.evaluarJugada(
            PlusJugada("1", "2026-11-24", listOf("00", "01", "02", "03", "04", "55", "66", "77"), ModalidadPlus.QUINIELA_PLUS, 1000.0),
            sorteo,
            reglas
        )
        assertEquals(EstadoJugada.GANO, ganadora.estado)
        assertEquals(5, ganadora.aciertosExactos.size)
        assertTrue(ganadora.premioGanado >= 3000.0)

        val pendiente = QuinielaPlusEngine.evaluarJugada(
            PlusJugada("2", "2026-11-25", listOf("00", "01", "02", "03", "04", "05", "06", "07"), ModalidadPlus.QUINIELA_PLUS, 1000.0),
            null,
            reglas
        )
        assertEquals(EstadoJugada.PENDIENTE, pendiente.estado)

        val perdedora = QuinielaPlusEngine.evaluarJugada(
            PlusJugada("3", "2026-11-24", listOf("40", "41", "42", "43", "44", "45", "46", "47"), ModalidadPlus.QUINIELA_PLUS, 500.0),
            sorteo,
            reglas.copy(habilitarParcialAproximacion = false, habilitarParcialTerminacion = false)
        )
        assertEquals(EstadoJugada.NO_GANO, perdedora.estado)

        val resumen = QuinielaPlusEngine.calcularResumenJugadas(listOf(ganadora, pendiente, perdedora))
        assertEquals(3, resumen.totalJugadas)
        assertEquals(2, resumen.jugadasResueltas)
        assertEquals(1, resumen.jugadasGanadoras)
        assertEquals(1, resumen.jugadasPendientes)
        assertEquals(50.0, resumen.porcentajeAcierto, 0.01)
    }

    @Test
    fun quiniela_plus_analytics_engine_deterministic_and_empty() {
        val emptyRes = QuinielaPlusAnalyticsEngine.analyze(emptyList())
        assertFalse(emptyRes.tieneDatos)
        assertTrue(emptyRes.combinacionesSugeridas.isEmpty())
        assertTrue(emptyRes.mensajeSinDatos.isNotBlank())

        val draws = (1..36).map { d ->
            val date = LocalDate.of(2026, 9, 1).plusDays(d.toLong())
            val safeDate = if (date.dayOfWeek.value == 7) date.plusDays(1) else date
            val base = (d * 3) % 80
            val nums2 = (0 until 20).map { k -> ((base + k) % 100).toString().padStart(2, '0') }
            PlusSorteo(
                fechaIso = safeDate.toString(),
                numerosPlus = nums2,
                origen = "Test"
            )
        }
        val r1 = QuinielaPlusAnalyticsEngine.analyze(draws, VentanaPlus.TOTAL, PlusPesosMotor(), topCombinaciones = 4)
        val r2 = QuinielaPlusAnalyticsEngine.analyze(draws, VentanaPlus.TOTAL, PlusPesosMotor(), topCombinaciones = 4)

        assertTrue(r1.tieneDatos)
        assertEquals(36, r1.totalSorteosAnalizados)
        assertEquals(100, r1.rankingCandidatos.size)
        assertEquals(4, r1.combinacionesSugeridas.size)
        assertEquals(8, r1.combinacionesSugeridas.first().numeros.size)
        assertTrue(r1.patrones.paresFrecuentes.isNotEmpty())
        assertTrue(r1.patrones.terminaciones.size == 10)
        assertTrue(r1.patrones.distribucionDecenas.size == 10)
        assertTrue(r1.tendenciasDiaSemana.isNotEmpty())
        assertTrue(r1.estacionalidadMensual.isNotEmpty())
        // Determinismo absoluto: dos ejecuciones producen exactamente el mismo ranking y combinaciones
        assertEquals(r1.combinacionesSugeridas, r2.combinacionesSugeridas)
        assertEquals(r1.rankingCandidatos.take(10).map { it.numero }, r2.rankingCandidatos.take(10).map { it.numero })
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
