package com.quinielazero.app.core.plus

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parser flexible para importar historial de Quiniela Plus desde CSV, TXT o JSON.
 * Soporta:
 *  - El CSV histórico existente en assets (`provincia_completo.csv`: extrae PROVINCIA + NOCTURNA).
 *  - CSVs dedicados de Quiniela Plus (`fecha,n1..n20` o `fecha,numeros`).
 *  - Archivos externos con encabezados variables (fecha/date, numero/resultado/extracciones)
 *    o confirmación manual de columnas cuando el formato es ambiguo.
 */
object QuinielaPlusCsvParser {

    private val DATE_HEADERS = setOf("fecha", "date", "dia", "fecha_sorteo", "fechasorteo", "sorteo_fecha")
    private val LOTERIA_HEADERS = setOf("loteria", "juego", "jurisdiccion", "lottery", "provincia")
    private val TURNO_HEADERS = setOf("turno", "draw", "sorteo", "horario", "turno_sorteo")
    private val PACKED_NUM_HEADERS = setOf("numeros", "numero", "resultado", "resultados", "extracciones", "bolillas", "plus", "numeros_plus")

    private val D_M_Y_SLASH = DateTimeFormatter.ofPattern("d/M/yyyy")
    private val D_M_Y_DASH = DateTimeFormatter.ofPattern("d-M-yyyy")
    private val Y_M_D_SLASH = DateTimeFormatter.ofPattern("yyyy/M/d")

    fun normalizeDateIso(raw: String): String? {
        val clean = raw.trim().replace("﻿", "").substringBefore("T").substringBefore(" ")
        if (clean.isBlank()) return null

        // yyyy-MM-dd
        runCatching { return LocalDate.parse(clean).toString() }
        // dd/MM/yyyy
        runCatching { return LocalDate.parse(clean, D_M_Y_SLASH).toString() }
        // dd-MM-yyyy
        runCatching { return LocalDate.parse(clean, D_M_Y_DASH).toString() }
        // yyyy/MM/dd
        runCatching { return LocalDate.parse(clean, Y_M_D_SLASH).toString() }

        // Regex fallback dd.MM.yyyy or yyyyMMdd
        val digitsOnly = clean.filter(Char::isDigit)
        if (digitsOnly.length == 8) {
            val y = digitsOnly.substring(0, 4).toIntOrNull()
            val m = digitsOnly.substring(4, 6).toIntOrNull()
            val d = digitsOnly.substring(6, 8).toIntOrNull()
            if (y != null && y in 1990..2099 && m != null && d != null) {
                runCatching { return LocalDate.of(y, m, d).toString() }
            }
            val d2 = digitsOnly.substring(0, 2).toIntOrNull()
            val m2 = digitsOnly.substring(2, 4).toIntOrNull()
            val y2 = digitsOnly.substring(4, 8).toIntOrNull()
            if (y2 != null && y2 in 1990..2099 && m2 != null && d2 != null) {
                runCatching { return LocalDate.of(y2, m2, d2).toString() }
            }
        }
        return null
    }

    /**
     * Convierte una lista de tokens numéricos crudos (de 1 a 4 dígitos) en:
     *  - Lista de 2 cifras `00..99` para Quiniela Plus.
     *  - Lista de 4 cifras (si todos venían en formato 4 dígitos) para Súper/Chance Plus.
     */
    fun normalizeDrawNumbers(rawTokens: List<String>): Pair<List<String>, List<String>>? {
        val cleaned = rawTokens
            .map { it.trim().filter(Char::isDigit) }
            .filter { it.isNotEmpty() && it.length <= 4 }
        if (cleaned.size < 8) return null

        val allFour = cleaned.all { it.length >= 3 }
        val nums4 = if (allFour) cleaned.take(20).map { it.padStart(4, '0').takeLast(4) } else emptyList()
        val nums2 = cleaned.take(20).map { it.takeLast(2).padStart(2, '0') }
        if (nums2.any { it.toIntOrNull() !in 0..99 }) return null
        return nums2 to nums4
    }

    /**
     * Parsea un archivo proveniente de assets (ej. `provincia_completo.csv` o un CSV dedicado de Plus).
     * En `provincia_completo.csv` filtra automáticamente `PROVINCIA` + `NOCTURNA`, que es el sorteo oficial
     * que alimenta Quiniela Plus. Si no existen columnas de juego/turno, importa todas las filas válidas.
     */
    fun parseAssetCsv(rawText: String, assetName: String): List<PlusSorteo> {
        val lines = rawText.replace("﻿", "").lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val delimiter = detectDelimiter(lines.take(5))
        val firstCells = splitLine(lines.first(), delimiter)
        val hasHeader = firstCells.any { cell ->
            val low = cell.lowercase().trim()
            low in DATE_HEADERS || low in LOTERIA_HEADERS || low in TURNO_HEADERS || low in PACKED_NUM_HEADERS ||
                Regex("^n\\d{1,2}$").matches(low)
        }

        val headers = if (hasHeader) firstCells.map { it.lowercase().trim() } else emptyList()
        val dataLines = if (hasHeader) lines.drop(1) else lines

        val dateIdx = headers.indexOfFirst { it in DATE_HEADERS }.takeIf { it >= 0 } ?: 0
        val lotIdx = headers.indexOfFirst { it in LOTERIA_HEADERS }.takeIf { it >= 0 }
        val turnoIdx = headers.indexOfFirst { it in TURNO_HEADERS }.takeIf { it >= 0 }
        val nCols = headers.mapIndexedNotNull { i, h -> if (Regex("^n\\d{1,2}$").matches(h)) i else null }
        val packedIdx = headers.indexOfFirst { it in PACKED_NUM_HEADERS }.takeIf { it >= 0 }

        val isMultiTurnAsset = assetName.contains("provincia", ignoreCase = true) || (lotIdx != null && turnoIdx != null)
        val out = LinkedHashMap<String, PlusSorteo>()

        for (line in dataLines) {
            val cells = splitLine(line, delimiter)
            if (cells.size <= dateIdx) continue
            val fechaIso = normalizeDateIso(cells[dateIdx]) ?: continue

            if (isMultiTurnAsset) {
                val lotVal = lotIdx?.let { cells.getOrNull(it)?.uppercase()?.trim() }.orEmpty()
                val turnoVal = turnoIdx?.let { cells.getOrNull(it)?.uppercase()?.trim() }.orEmpty()
                val okLot = lotVal.isEmpty() || "PROVINCIA" in lotVal
                val okTurno = turnoVal.isEmpty() || "NOCT" in turnoVal || "PLUS" in turnoVal
                if (!okLot || !okTurno) continue
            }

            val rawNumTokens = when {
                nCols.isNotEmpty() -> nCols.mapNotNull { cells.getOrNull(it) }
                packedIdx != null -> extractTokensFromPacked(cells.getOrNull(packedIdx).orEmpty())
                else -> cells.drop(dateIdx + 1)
                    .filter { c -> val d = c.filter(Char::isDigit); d.isNotEmpty() && d.length <= 4 }
                    .let { if (it.size >= 8) it else extractTokensFromPacked(line.substringAfter(delimiter)) }
            }

            val (nums2, nums4) = normalizeDrawNumbers(rawNumTokens) ?: continue
            out[fechaIso] = PlusSorteo(
                fechaIso = fechaIso,
                numerosPlus = nums2,
                numerosCompletos4 = nums4,
                origen = "CSV inicial ($assetName)",
                notas = if (nums4.isNotEmpty()) "Nocturna Provincia · 20 extracciones" else "20 extracciones Plus"
            )
        }
        return out.values.sortedByDescending { it.fechaIso }
    }

    /**
     * Parser flexible para archivos externos (CSV, JSON o TXT) importados por el usuario.
     * Si detecta encabezados/columnas estándar, devuelve [PlusCsvImportResult.Success].
     * Si el formato es ambiguo, devuelve [PlusCsvImportResult.NeedsMapping] para confirmación única.
     */
    fun parseFlexibleImport(rawText: String, fileName: String): PlusCsvImportResult {
        val cleaned = rawText.replace("﻿", "").trim()
        if (cleaned.isEmpty()) return PlusCsvImportResult.Error("El archivo está vacío.")

        if (cleaned.startsWith("{") || cleaned.startsWith("[")) {
            val jsonDraws = parseJsonImport(cleaned, fileName)
            if (jsonDraws.isNotEmpty()) {
                return PlusCsvImportResult.Success(jsonDraws, skippedRows = 0, sourceLabel = fileName)
            }
        }

        val lines = cleaned.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return PlusCsvImportResult.Error("No se encontraron líneas válidas.")

        val delimiter = detectDelimiter(lines.take(8))
        val firstCells = splitLine(lines.first(), delimiter)
        val hasHeader = firstCells.any { cell ->
            val low = cell.lowercase().trim()
            low in DATE_HEADERS || low in LOTERIA_HEADERS || low in TURNO_HEADERS || low in PACKED_NUM_HEADERS ||
                Regex("^n\\d{1,2}$").matches(low) || cell.any { it.isLetter() } && normalizeDateIso(cell) == null
        }

        val headers = if (hasHeader) firstCells else List(firstCells.size) { "Columna ${it + 1}" }
        val headerNorms = headers.map { it.lowercase().trim() }
        val dataLines = if (hasHeader) lines.drop(1) else lines
        if (dataLines.isEmpty()) return PlusCsvImportResult.Error("El archivo no contiene filas de datos.")

        val dateCol = headerNorms.indexOfFirst { it in DATE_HEADERS }.takeIf { it >= 0 }
        val nCols = headerNorms.mapIndexedNotNull { idx, h ->
            if (Regex("^(n|num|nro|bolilla|pos|puesto)?\\d{1,2}$").matches(h) && idx != dateCol) idx else null
        }
        val packedCol = headerNorms.indexOfFirst { it in PACKED_NUM_HEADERS }.takeIf { it >= 0 }

        // Confident auto-detection if header explicitly matched OR rows follow unambiguous fecha + >=8 numbers
        val confidentHeader = dateCol != null && (nCols.size >= 8 || packedCol != null)
        if (confidentHeader) {
            return parseWithMapping(
                rawText = cleaned,
                fileName = fileName,
                delimiter = delimiter,
                hasHeader = hasHeader,
                dateCol = dateCol!!,
                numberCols = nCols,
                packedCol = packedCol
            )
        }

        // Heuristic detection for headerless or non-standard headers
        val sampleRows = dataLines.take(5).map { splitLine(it, delimiter) }
        val guessedDateCol = (0 until (sampleRows.maxOfOrNull { it.size } ?: 0)).firstOrNull { colIdx ->
            sampleRows.count { row -> normalizeDateIso(row.getOrNull(colIdx).orEmpty()) != null } >=
                (sampleRows.size / 2 + 1).coerceAtMost(sampleRows.size)
        } ?: 0

        val maxCols = sampleRows.maxOfOrNull { it.size } ?: 0
        val guessedPacked = (0 until maxCols).firstOrNull { colIdx ->
            colIdx != guessedDateCol && sampleRows.any { row ->
                extractTokensFromPacked(row.getOrNull(colIdx).orEmpty()).size >= 8
            }
        }
        val guessedNumCols = if (guessedPacked == null) {
            (0 until maxCols).filter { colIdx ->
                colIdx != guessedDateCol && sampleRows.count { row ->
                    val tok = row.getOrNull(colIdx).orEmpty().trim().filter(Char::isDigit)
                    tok.length in 1..4
                } >= (sampleRows.size / 2 + 1).coerceAtMost(sampleRows.size)
            }
        } else emptyList()

        // If headerless standard CSV (e.g. date + 20 numbers directly), import automatically
        if (!hasHeader && (guessedNumCols.size >= 20 || guessedPacked != null)) {
            return parseWithMapping(
                rawText = cleaned,
                fileName = fileName,
                delimiter = delimiter,
                hasHeader = false,
                dateCol = guessedDateCol,
                numberCols = guessedNumCols,
                packedCol = guessedPacked
            )
        }

        // Otherwise ask the user to confirm the column mapping once
        return PlusCsvImportResult.NeedsMapping(
            PlusCsvMappingSuggestion(
                fileName = fileName,
                rawContent = cleaned,
                delimiter = delimiter,
                hasHeader = hasHeader,
                headers = headers,
                sampleRows = sampleRows,
                suggestedDateCol = guessedDateCol,
                suggestedNumberCols = guessedNumCols,
                suggestedPackedCol = guessedPacked
            )
        )
    }

    fun parseWithMapping(
        rawText: String,
        fileName: String,
        delimiter: Char,
        hasHeader: Boolean,
        dateCol: Int,
        numberCols: List<Int>,
        packedCol: Int?
    ): PlusCsvImportResult {
        val lines = rawText.replace("﻿", "").lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val dataLines = if (hasHeader) lines.drop(1) else lines
        val firstCells = splitLine(lines.firstOrNull().orEmpty(), delimiter).map { it.lowercase().trim() }
        val lotIdx = if (hasHeader) firstCells.indexOfFirst { it in LOTERIA_HEADERS }.takeIf { it >= 0 } else null
        val turnoIdx = if (hasHeader) firstCells.indexOfFirst { it in TURNO_HEADERS }.takeIf { it >= 0 } else null

        val out = LinkedHashMap<String, PlusSorteo>()
        var skipped = 0

        for (line in dataLines) {
            val cells = splitLine(line, delimiter)
            val fechaIso = normalizeDateIso(cells.getOrNull(dateCol).orEmpty())
            if (fechaIso == null) {
                skipped++
                continue
            }
            if (lotIdx != null && turnoIdx != null) {
                val lot = cells.getOrNull(lotIdx).orEmpty().uppercase()
                val turno = cells.getOrNull(turnoIdx).orEmpty().uppercase()
                if ((lot.isNotBlank() && "PROVINCIA" !in lot && "PLUS" !in lot) ||
                    (turno.isNotBlank() && "NOCT" !in turno && "PLUS" !in turno)
                ) {
                    continue
                }
            }
            val rawTokens = when {
                packedCol != null -> extractTokensFromPacked(cells.getOrNull(packedCol).orEmpty())
                numberCols.isNotEmpty() -> numberCols.mapNotNull { cells.getOrNull(it) }
                else -> cells.filterIndexed { i, _ -> i != dateCol }
            }
            val (nums2, nums4) = normalizeDrawNumbers(rawTokens) ?: run {
                skipped++
                continue
            }
            out[fechaIso] = PlusSorteo(
                fechaIso = fechaIso,
                numerosPlus = nums2,
                numerosCompletos4 = nums4,
                origen = "Importado ($fileName)"
            )
        }

        if (out.isEmpty()) {
            return PlusCsvImportResult.Error("No se pudieron extraer sorteos válidos con las columnas seleccionadas.")
        }
        return PlusCsvImportResult.Success(
            sorteos = out.values.sortedByDescending { it.fechaIso },
            skippedRows = skipped,
            sourceLabel = fileName
        )
    }

    private fun parseJsonImport(rawJson: String, fileName: String): List<PlusSorteo> {
        val arr = runCatching {
            if (rawJson.startsWith("[")) JSONArray(rawJson)
            else {
                val obj = JSONObject(rawJson)
                obj.optJSONArray("sorteos")
                    ?: obj.optJSONArray("historial")
                    ?: obj.optJSONArray("data")
                    ?: obj.optJSONArray("resultados")
                    ?: JSONArray()
            }
        }.getOrNull() ?: return emptyList()

        val out = LinkedHashMap<String, PlusSorteo>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val rawDate = item.optString("fechaIso", item.optString("fecha", item.optString("date", "")))
            val fechaIso = normalizeDateIso(rawDate) ?: continue

            val rawNums = mutableListOf<String>()
            val arrNums = item.optJSONArray("numerosPlus")
                ?: item.optJSONArray("numeros")
                ?: item.optJSONArray("resultados")
            if (arrNums != null) {
                for (j in 0 until arrNums.length()) {
                    rawNums.add(arrNums.optString(j))
                }
            } else {
                val packed = item.optString("numeros", item.optString("resultado", ""))
                if (packed.isNotBlank()) {
                    rawNums.addAll(extractTokensFromPacked(packed))
                } else {
                    for (k in 1..20) {
                        val v = item.optString("n$k", "")
                        if (v.isNotBlank()) rawNums.add(v)
                    }
                }
            }
            val (nums2, nums4) = normalizeDrawNumbers(rawNums) ?: continue
            out[fechaIso] = PlusSorteo(
                fechaIso = fechaIso,
                numerosPlus = nums2,
                numerosCompletos4 = nums4,
                origen = "Importado JSON ($fileName)"
            )
        }
        return out.values.sortedByDescending { it.fechaIso }
    }

    fun parseManualDrawNumbers(raw: String): Pair<List<String>, List<String>>? {
        val tokens = extractTokensFromPacked(raw)
        return normalizeDrawNumbers(tokens)
    }

    private fun extractTokensFromPacked(raw: String): List<String> {
        return Regex("\\d{1,4}").findAll(raw).map { it.value }.toList()
    }

    private fun detectDelimiter(sampleLines: List<String>): Char {
        val candidates = listOf(',', ';', '\t', '|')
        return candidates.maxByOrNull { delim ->
            val counts = sampleLines.map { splitLine(it, delim).size }
            if (counts.isEmpty()) 0 else counts.min()
        } ?: ','
    }

    private fun splitLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(cur.toString().trim())
                    cur.clear()
                }
                else -> cur.append(ch)
            }
        }
        result.add(cur.toString().trim())
        return result
    }
}
