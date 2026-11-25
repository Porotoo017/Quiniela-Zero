package com.quinielazero.app.core.network

import com.quinielazero.app.core.calendar.SorteoCalendarProvider
import com.quinielazero.app.core.model.EstadoVerificacion
import com.quinielazero.app.core.model.Jurisdiccion
import com.quinielazero.app.core.model.SorteoDomain
import com.quinielazero.app.core.model.TurnoSorteo
import com.quinielazero.app.data.local.FuenteSaludDao
import com.quinielazero.app.data.local.FuenteSaludEntity
import com.quinielazero.app.data.local.SorteoDao
import com.quinielazero.app.data.local.SorteoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class ParsedDrawCandidate(
    val loteria: Jurisdiccion,
    val fechaIso: String,
    val turno: TurnoSorteo,
    val numeros: List<String>,
    val fuente: String,
    val nroSorteo: String = ""
)

data class SyncReport(
    val nuevosGuardados: Int,
    val pendientes: Int,
    val offlineFallback: Boolean,
    val mensajeEstado: String
)

object DataParsers {
    private val DATE_DMY = Regex("""(\d{2})/(\d{2})/(\d{4})""")
    private val DATE_ISO = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private val FOUR_DIGITS = Regex("""\d{4}""")

    fun normalizarFechaIso(raw: String): String? {
        val clean = raw.trim().replace("\\/", "/")
        DATE_ISO.find(clean)?.let { return it.value }
        DATE_DMY.find(clean)?.let { m ->
            val (d, mo, y) = m.destructured
            return "$y-$mo-$d"
        }
        val dmy2 = Regex("""(\d{2})/(\d{2})/(\d{2})""").find(clean)
        if (dmy2 != null) {
            val (d, mo, y) = dmy2.destructured
            return "20$y-$mo-$d"
        }
        return null
    }

    fun isValidExtracto(numeros: List<String>): Boolean =
        numeros.size == 20 && numeros.all { FOUR_DIGITS.matches(it) }

    fun parseGbaHtml(html: String): List<ParsedDrawCandidate> {
        val doc = Jsoup.parse(html)
        val out = mutableListOf<ParsedDrawCandidate>()
        for (bloque in doc.select("div.sorteo")) {
            val titulo = bloque.selectFirst("h3")?.text().orEmpty()
            val turno = TurnoSorteo.normalizar(titulo) ?: continue
            val fechaIso = normalizarFechaIso(bloque.selectFirst(".fechaSorteo")?.text().orEmpty()) ?: continue
            val nro = bloque.selectFirst(".nSorteo")?.text().orEmpty().filter(Char::isDigit)
            val box = bloque.selectFirst("div.bolillas") ?: continue

            val pares = mutableMapOf<Int, String>()
            for (div in box.select("div")) {
                val spans = div.children().filter { it.tagName() == "span" }
                if (spans.size >= 2) {
                    val pos = spans[0].text().trim().toIntOrNull()
                    val valTxt = spans[1].text().trim()
                    if (pos != null && pos in 1..20 && FOUR_DIGITS.matches(valTxt)) {
                        pares[pos] = valTxt
                    }
                }
            }
            if (pares.size != 20) {
                val allSpans = box.select("span").map { it.text().trim() }
                if (allSpans.size >= 40) {
                    pares.clear()
                    for (i in 0 until 40 step 2) {
                        val pos = allSpans[i].toIntOrNull()
                        val valTxt = allSpans[i + 1]
                        if (pos != null && pos in 1..20 && FOUR_DIGITS.matches(valTxt)) {
                            pares[pos] = valTxt
                        }
                    }
                }
            }
            if (pares.size == 20) {
                val nums = (1..20).mapNotNull { pares[it] }
                if (isValidExtracto(nums)) {
                    out.add(ParsedDrawCandidate(Jurisdiccion.PROVINCIA, fechaIso, turno, nums, "loteria.gba.gov.ar", nro))
                }
            }
        }
        return out
    }

    fun parseCiudadIndexIds(html: String, maxCount: Int = 10): List<String> {
        val doc = Jsoup.parse(html)
        return doc.select("li.custom-option")
            .mapNotNull { it.attr("data-value").trim().takeIf { v -> v.isNotEmpty() && v.all(Char::isDigit) } }
            .distinct()
            .take(maxCount)
    }

    fun parseCiudadResultadosJs(raw: String): List<ParsedDrawCandidate> {
        val marker = raw.indexOf("window.RESULTADOS_DATA")
        val start = raw.indexOf('[', if (marker >= 0) marker else 0)
        if (start < 0) return emptyList()

        // Extraer el array JSON balanceado respetando llaves/corchetes
        var depth = 0
        var inStr = false
        var esc = false
        var end = -1
        for (i in start until raw.length) {
            val ch = raw[i]
            if (esc) { esc = false; continue }
            if (ch == '\\') { esc = true; continue }
            if (ch == '"') { inStr = !inStr; continue }
            if (inStr) continue
            if (ch == '[') depth++
            else if (ch == ']') {
                depth--
                if (depth == 0) { end = i + 1; break }
            }
        }
        if (end <= start) return emptyList()

        val out = mutableListOf<ParsedDrawCandidate>()
        runCatching {
            val arr = JSONArray(raw.substring(start, end))
            val jurMap = mapOf("51" to Jurisdiccion.CIUDAD, "53" to Jurisdiccion.PROVINCIA)
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val fechaIso = normalizarFechaIso(item.optString("fecha")) ?: continue
                val turno = TurnoSorteo.normalizar(item.optString("modalidad")) ?: continue
                val nro = item.optString("sorteo")
                val jurs = item.optJSONObject("jurisdicciones") ?: continue

                for ((code, jur) in jurMap) {
                    val jObj = jurs.optJSONObject(code) ?: continue
                    val numsArr = jObj.optJSONArray("numeros") ?: continue
                    val pares = mutableMapOf<Int, String>()
                    for (k in 0 until numsArr.length()) {
                        val nObj = numsArr.getJSONObject(k)
                        val pos = nObj.optString("pos").toIntOrNull()
                        val dig = nObj.optString("val").filter(Char::isDigit).padStart(4, '0')
                        if (pos != null && pos in 1..20 && FOUR_DIGITS.matches(dig)) {
                            pares[pos] = dig
                        }
                    }
                    if (pares.size == 20) {
                        val nums = (1..20).mapNotNull { pares[it] }
                        if (isValidExtracto(nums)) {
                            out.add(ParsedDrawCandidate(jur, fechaIso, turno, nums, "quiniela.loteriadelaciudad.gob.ar", nro))
                        }
                    }
                }
            }
        }
        return out
    }

    fun parseVivitusuerteHtml(html: String, loteria: Jurisdiccion): List<ParsedDrawCandidate> {
        val doc = Jsoup.parse(html)
        val attrDate = doc.selectFirst("#seccionPizarra[data-fecha-default]")?.attr("data-fecha-default").orEmpty()
        val fechaIso = normalizarFechaIso(attrDate) ?: normalizarFechaIso(doc.text()) ?: return emptyList()
        val out = mutableListOf<ParsedDrawCandidate>()
        for (card in doc.select("div.card")) {
            val header = card.selectFirst(".card-header")?.text().orEmpty()
            val turno = TurnoSorteo.normalizar(header) ?: continue
            val table = card.selectFirst("table[data-pizarra-momento]") ?: continue
            val slots = table.select(".caja-slot").map { it.text().trim().trimEnd('.') }
            val vals = table.select(".caja-resultado").map { it.text().trim() }
            val pares = mutableMapOf<Int, String>()
            slots.zip(vals).forEach { (s, v) ->
                val pos = s.toIntOrNull()
                val dig = v.filter(Char::isDigit).padStart(4, '0')
                if (pos != null && pos in 1..20 && FOUR_DIGITS.matches(dig)) {
                    pares[pos] = dig
                }
            }
            if (pares.size == 20) {
                val nums = (1..20).mapNotNull { pares[it] }
                if (isValidExtracto(nums)) {
                    out.add(ParsedDrawCandidate(loteria, fechaIso, turno, nums, "vivitusuerte.com"))
                }
            }
        }
        return out
    }

    fun parseSnapshotJson(rawJson: String): List<ParsedDrawCandidate> {
        val out = mutableListOf<ParsedDrawCandidate>()
        runCatching {
            val root = JSONObject(rawJson)
            val arr = root.optJSONArray("sorteos_recientes") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val jur = Jurisdiccion.fromString(o.optString("loteria"))
                val fechaIso = normalizarFechaIso(o.optString("fecha")) ?: continue
                val turno = TurnoSorteo.normalizar(o.optString("turno")) ?: continue
                val numsArr = o.optJSONArray("numeros") ?: continue
                val nums = (0 until numsArr.length()).map { numsArr.getString(it).padStart(4, '0') }
                if (isValidExtracto(nums)) {
                    out.add(
                        ParsedDrawCandidate(
                            loteria = jur,
                            fechaIso = fechaIso,
                            turno = turno,
                            numeros = nums,
                            fuente = o.optString("fuente_primaria", "github-snapshot"),
                            nroSorteo = o.optString("nro_sorteo")
                        )
                    )
                }
            }
        }
        return out
    }
}

class DataSourcesManager(
    private val sorteoDao: SorteoDao,
    private val healthDao: FuenteSaludDao,
    private val calendarProvider: SorteoCalendarProvider
) {
    companion object {
        const val URL_GBA = "https://www.loteria.gba.gov.ar/index.php/resultados"
        const val URL_CIUDAD_HOME = "https://quiniela.loteriadelaciudad.gob.ar/"
        const val URL_CIUDAD_DATA = "https://quiniela.loteriadelaciudad.gob.ar/includes/resultados-data.php?sorteo="
        const val URL_VIVI_PROV = "https://www.vivitusuerte.com/pizarra/provincia"
        const val URL_VIVI_CIUDAD = "https://www.vivitusuerte.com/pizarra/ciudad"
        const val URL_GITHUB_SNAPSHOT = "https://raw.githubusercontent.com/Porotoo017/Quiniela-Zero/main/sync_snapshot.json"
        private val RETRY_DELAYS = longArrayOf(2_000L, 8_000L, 30_000L)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private suspend fun fetchWithBackoff(url: String, maxRetries: Int = 2): Pair<Int, String?> {
        var lastCode = 0
        for (attempt in 0..maxRetries) {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "QuinielaZero-Android/2.0")
                .build()
            val result = runCatching {
                client.newCall(req).execute().use { resp ->
                    lastCode = resp.code
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            }.getOrNull()
            if (result != null) return lastCode to result
            if (attempt < maxRetries) delay(RETRY_DELAYS.getOrElse(attempt) { 2_000L })
        }
        return lastCode to null
    }

    suspend fun syncAllSources(includeDirectScrape: Boolean = true): SyncReport = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<ParsedDrawCandidate>()
        val healthLogs = mutableListOf<FuenteSaludEntity>()
        val nowTxt = calendarProvider.nowArgentina().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))

        // 0. Snapshot continuo de GitHub Actions (Provincia + Ciudad unificados y auto-actualizados)
        run {
            val t0 = System.currentTimeMillis()
            val (code, body) = fetchWithBackoff(URL_GITHUB_SNAPSHOT, maxRetries = 1)
            val lat = (System.currentTimeMillis() - t0).toInt()
            if (body != null) {
                val parsed = DataParsers.parseSnapshotJson(body)
                candidates.addAll(parsed)
                healthLogs.add(FuenteSaludEntity("Snapshot Auto-Sync Repo", "AMBAS", if (parsed.isNotEmpty()) "OK" else "VACIO", code, lat, "${parsed.size} sorteos recientes", nowTxt))
            } else {
                healthLogs.add(FuenteSaludEntity("Snapshot Auto-Sync Repo", "AMBAS", "ERROR", code, lat, "Sin respuesta HTTP $code", nowTxt))
            }
        }

        if (includeDirectScrape) {
            // 1. Prioridad 1: Oficial Provincia (Lotería GBA)
            run {
                val t0 = System.currentTimeMillis()
                val (code, body) = fetchWithBackoff(URL_GBA, maxRetries = 2)
                val lat = (System.currentTimeMillis() - t0).toInt()
                if (body != null) {
                    val parsed = DataParsers.parseGbaHtml(body)
                    val estado = when {
                        parsed.isNotEmpty() -> "OK"
                        body.contains("div.bolillas") || body.contains("bolillas") -> "ESPERA"
                        else -> "CAMBIO_ESTRUCTURA"
                    }
                    candidates.addAll(parsed)
                    healthLogs.add(FuenteSaludEntity("Lotería GBA (Oficial)", "PROVINCIA", estado, code, lat, "${parsed.size} turnos", nowTxt))
                } else {
                    healthLogs.add(FuenteSaludEntity("Lotería GBA (Oficial)", "PROVINCIA", "ERROR", code, lat, "Falla red HTTP $code", nowTxt))
                }
            }

            // 2. Prioridad 2: Oficial Ciudad (LOTBA JSON)
            run {
                val t0 = System.currentTimeMillis()
                val (code, homeHtml) = fetchWithBackoff(URL_CIUDAD_HOME, maxRetries = 2)
                var totalCiudad = 0
                if (homeHtml != null) {
                    val ids = DataParsers.parseCiudadIndexIds(homeHtml, maxCount = 6)
                    for (id in ids) {
                        val (_, jsBody) = fetchWithBackoff(URL_CIUDAD_DATA + id, maxRetries = 1)
                        if (jsBody != null) {
                            val parsed = DataParsers.parseCiudadResultadosJs(jsBody)
                            candidates.addAll(parsed)
                            totalCiudad += parsed.size
                        }
                    }
                    val lat = (System.currentTimeMillis() - t0).toInt()
                    healthLogs.add(FuenteSaludEntity("Lotería Ciudad LOTBA", "AMBAS", if (ids.isNotEmpty()) "OK" else "CAMBIO_ESTRUCTURA", code, lat, "$totalCiudad extractos", nowTxt))
                } else {
                    val lat = (System.currentTimeMillis() - t0).toInt()
                    healthLogs.add(FuenteSaludEntity("Lotería Ciudad LOTBA", "AMBAS", "ERROR", code, lat, "Falla red HTTP $code", nowTxt))
                }
            }

            // 3. Prioridad 3: Espejo confiable ViviTuSuerte (validación cruzada y contingencia)
            for ((url, jur) in listOf(URL_VIVI_PROV to Jurisdiccion.PROVINCIA, URL_VIVI_CIUDAD to Jurisdiccion.CIUDAD)) {
                val t0 = System.currentTimeMillis()
                val (code, html) = fetchWithBackoff(url, maxRetries = 1)
                val lat = (System.currentTimeMillis() - t0).toInt()
                if (html != null) {
                    val parsed = DataParsers.parseVivitusuerteHtml(html, jur)
                    candidates.addAll(parsed)
                    healthLogs.add(FuenteSaludEntity("ViviTuSuerte (${jur.label})", jur.dbValue, if (parsed.isNotEmpty()) "OK" else "SIN_DATOS", code, lat, "${parsed.size} turnos", nowTxt))
                } else {
                    healthLogs.add(FuenteSaludEntity("ViviTuSuerte (${jur.label})", jur.dbValue, "ERROR", code, lat, "Falla HTTP $code", nowTxt))
                }
            }
        }

        if (healthLogs.isNotEmpty()) healthDao.upsertAll(healthLogs)

        if (candidates.isEmpty()) {
            return@withContext SyncReport(
                nuevosGuardados = 0,
                pendientes = 0,
                offlineFallback = true,
                mensajeEstado = "Modo offline · Usando historial local guardado."
            )
        }

        val consolidated = crossValidate(candidates)
        var saved = 0
        var pending = 0
        val toInsert = mutableListOf<SorteoEntity>()

        for (item in consolidated) {
            if (item.estado == EstadoVerificacion.PENDIENTE) pending++
            val existing = sorteoDao.findOne(item.loteria.dbValue, item.fechaIso, item.turno.dbValue)
            val newCsv = item.numeros.joinToString(",")
            if (existing == null || (existing.estadoVerificacion != "HISTORIAL" && (existing.datosCsv != newCsv || existing.estadoVerificacion != item.estado.dbValue))) {
                toInsert.add(
                    SorteoEntity(
                        id = existing?.id ?: 0,
                        loteria = item.loteria.dbValue,
                        fechaIso = item.fechaIso,
                        turno = item.turno.dbValue,
                        datosCsv = newCsv,
                        estadoVerificacion = item.estado.dbValue,
                        fuentePrimaria = item.fuentePrimaria,
                        fuenteSecundaria = item.fuenteSecundaria,
                        nroSorteo = item.nroSorteo
                    )
                )
                saved++
            }
        }

        if (toInsert.isNotEmpty()) sorteoDao.upsertAll(toInsert)

        SyncReport(
            nuevosGuardados = saved,
            pendientes = pending,
            offlineFallback = false,
            mensajeEstado = if (saved > 0) "Actualizado (+$saved sorteos)" else "Historial al día"
        )
    }

    fun crossValidate(candidates: List<ParsedDrawCandidate>): List<SorteoDomain> {
        val grouped = candidates
            .filter { c ->
                val d = runCatching { LocalDate.parse(c.fechaIso) }.getOrNull() ?: return@filter false
                DataParsers.isValidExtracto(c.numeros) && calendarProvider.isTurnAllowed(c.loteria, d, c.turno)
            }
            .groupBy { Triple(it.loteria, it.fechaIso, it.turno) }

        return grouped.map { (key, list) ->
            val bySource = list.associateBy { it.fuente }
            val byExtract = bySource.values.groupBy { it.numeros.joinToString(",") }
            val bestEntry = byExtract.maxByOrNull { it.value.size }!!
            val distinctExtracts = byExtract.size
            val distinctSources = bestEntry.value.map { it.fuente }.distinct()

            val estado = when {
                distinctExtracts > 1 && distinctSources.size < 2 -> EstadoVerificacion.PENDIENTE
                distinctSources.size >= 2 -> EstadoVerificacion.VERIFICADO
                else -> EstadoVerificacion.NO_VERIFICADO
            }

            SorteoDomain(
                loteria = key.first,
                fechaIso = key.second,
                turno = key.third,
                numeros = bestEntry.value.first().numeros,
                estado = estado,
                fuentePrimaria = distinctSources.getOrNull(0).orEmpty(),
                fuenteSecundaria = distinctSources.getOrNull(1).orEmpty(),
                nroSorteo = list.firstOrNull { it.nroSorteo.isNotEmpty() }?.nroSorteo.orEmpty()
            )
        }
    }
}
