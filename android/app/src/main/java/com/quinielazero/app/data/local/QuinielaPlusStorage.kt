package com.quinielazero.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.quinielazero.app.core.model.Jurisdiccion
import com.quinielazero.app.core.model.SorteoDomain
import com.quinielazero.app.core.model.TurnoSorteo
import com.quinielazero.app.core.plus.EstadoJugada
import com.quinielazero.app.core.plus.ModalidadPlus
import com.quinielazero.app.core.plus.PlusJugada
import com.quinielazero.app.core.plus.PlusPesosMotor
import com.quinielazero.app.core.plus.PlusReglasPremios
import com.quinielazero.app.core.plus.PlusSorteo
import com.quinielazero.app.core.plus.QuinielaPlusCsvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "plus_sorteos")
data class PlusSorteoEntity(
    @PrimaryKey val fechaIso: String,
    val numerosPlusCsv: String,
    val numeros4Csv: String,
    val origen: String,
    val notas: String,
    val createdAtMs: Long
) {
    fun toDomain(): PlusSorteo {
        val nums2 = numerosPlusCsv.split(",").map { it.trim() }.filter { it.length == 2 }
        val nums4 = numeros4Csv.split(",").map { it.trim() }.filter { it.length == 4 }
        return PlusSorteo(
            fechaIso = fechaIso,
            numerosPlus = nums2,
            numerosCompletos4 = nums4,
            origen = origen,
            notas = notas,
            createdAtMs = createdAtMs
        )
    }

    companion object {
        fun fromDomain(s: PlusSorteo): PlusSorteoEntity = PlusSorteoEntity(
            fechaIso = s.fechaIso,
            numerosPlusCsv = s.numerosPlus.joinToString(","),
            numeros4Csv = s.numerosCompletos4.joinToString(","),
            origen = s.origen,
            notas = s.notas,
            createdAtMs = s.createdAtMs
        )
    }
}

@Dao
interface PlusSorteoDao {
    @Query("SELECT * FROM plus_sorteos ORDER BY fechaIso DESC")
    fun observeAll(): Flow<List<PlusSorteoEntity>>

    @Query("SELECT * FROM plus_sorteos ORDER BY fechaIso DESC")
    suspend fun getAllOnce(): List<PlusSorteoEntity>

    @Query("SELECT COUNT(*) FROM plus_sorteos")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PlusSorteoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(item: PlusSorteoEntity)

    @Query("DELETE FROM plus_sorteos WHERE fechaIso = :fechaIso")
    suspend fun deleteByFecha(fechaIso: String)

    @Query("DELETE FROM plus_sorteos")
    suspend fun deleteAll()
}

/**
 * Almacenamiento cifrado exclusivo del módulo Quiniela Plus (jugadas, reglas de premios,
 * pesos configurables del motor predictivo y estado de inicialización CSV).
 */
class QuinielaPlusPrefsStore(context: Context) {
    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "quiniela_zero_plus_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        context.getSharedPreferences("quiniela_zero_plus_fallback_prefs", Context.MODE_PRIVATE)
    }

    fun isBootstrapDone(): Boolean = prefs.getBoolean("plus_bootstrap_done", false)

    fun setBootstrapDone(done: Boolean) {
        prefs.edit().putBoolean("plus_bootstrap_done", done).apply()
    }

    fun getManuallyDeletedDates(): Set<String> =
        prefs.getStringSet("plus_deleted_dates", emptySet()) ?: emptySet()

    fun markManuallyDeletedDate(fechaIso: String) {
        val next = getManuallyDeletedDates().toMutableSet().apply { add(fechaIso) }
        prefs.edit().putStringSet("plus_deleted_dates", next).apply()
    }

    fun clearManuallyDeletedDates() {
        prefs.edit().remove("plus_deleted_dates").apply()
    }

    fun loadJugadas(): List<PlusJugada> {
        val raw = prefs.getString("plus_jugadas_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val o = arr.optJSONObject(idx) ?: return@mapNotNull null
                val numsArr = o.optJSONArray("numeros") ?: JSONArray()
                val modName = o.optString("modalidad", ModalidadPlus.QUINIELA_PLUS.name)
                val modalidad = ModalidadPlus.entries.firstOrNull { it.name == modName } ?: ModalidadPlus.QUINIELA_PLUS
                val estName = o.optString("estado", EstadoJugada.PENDIENTE.name)
                val estado = EstadoJugada.entries.firstOrNull { it.name == estName } ?: EstadoJugada.PENDIENTE
                PlusJugada(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    fechaIso = o.optString("fechaIso", ""),
                    numeros = (0 until numsArr.length()).map { numsArr.getString(it) },
                    modalidad = modalidad,
                    montoApostado = o.optDouble("montoApostado", 1000.0),
                    notas = o.optString("notas", ""),
                    estado = estado
                )
            }.filter { it.fechaIso.isNotBlank() && it.numeros.isNotEmpty() }
        }.getOrElse { emptyList() }
    }

    fun saveJugadas(jugadas: List<PlusJugada>) {
        val arr = JSONArray()
        jugadas.forEach { j ->
            arr.put(
                JSONObject().apply {
                    put("id", j.id)
                    put("fechaIso", j.fechaIso)
                    put("numeros", JSONArray(j.numeros))
                    put("modalidad", j.modalidad.name)
                    put("montoApostado", j.montoApostado)
                    put("notas", j.notas)
                    put("estado", j.estado.name)
                }
            )
        }
        prefs.edit().putString("plus_jugadas_json", arr.toString()).apply()
    }

    fun loadReglas(): PlusReglasPremios {
        val raw = prefs.getString("plus_reglas_json", null) ?: return PlusReglasPremios()
        return runCatching {
            val o = JSONObject(raw)
            PlusReglasPremios(
                mult8Aciertos = o.optDouble("mult8", 500.0),
                mult7Aciertos = o.optDouble("mult7", 80.0),
                mult6Aciertos = o.optDouble("mult6", 15.0),
                mult5Aciertos = o.optDouble("mult5", 3.0),
                multCabezaExacta = o.optDouble("multCab", 70.0),
                habilitarParcialAproximacion = o.optBoolean("parcialApprox", true),
                habilitarParcialTerminacion = o.optBoolean("parcialTerm", false),
                multAciertoParcial = o.optDouble("multParcial", 0.5)
            )
        }.getOrElse { PlusReglasPremios() }
    }

    fun saveReglas(r: PlusReglasPremios) {
        val o = JSONObject().apply {
            put("mult8", r.mult8Aciertos)
            put("mult7", r.mult7Aciertos)
            put("mult6", r.mult6Aciertos)
            put("mult5", r.mult5Aciertos)
            put("multCab", r.multCabezaExacta)
            put("parcialApprox", r.habilitarParcialAproximacion)
            put("parcialTerm", r.habilitarParcialTerminacion)
            put("multParcial", r.multAciertoParcial)
        }
        prefs.edit().putString("plus_reglas_json", o.toString()).apply()
    }

    fun loadPesos(): PlusPesosMotor {
        val raw = prefs.getString("plus_pesos_json", null) ?: return PlusPesosMotor()
        return runCatching {
            val o = JSONObject(raw)
            PlusPesosMotor(
                pesoFrecuencia = o.optDouble("freq", 0.30),
                pesoAtraso = o.optDouble("gap", 0.25),
                pesoPatrones = o.optDouble("pat", 0.25),
                pesoTemporalMarkov = o.optDouble("temp", 0.20)
            )
        }.getOrElse { PlusPesosMotor() }
    }

    fun savePesos(p: PlusPesosMotor) {
        val o = JSONObject().apply {
            put("freq", p.pesoFrecuencia)
            put("gap", p.pesoAtraso)
            put("pat", p.pesoPatrones)
            put("temp", p.pesoTemporalMarkov)
        }
        prefs.edit().putString("plus_pesos_json", o.toString()).apply()
    }
}

/**
 * Detecta automáticamente los CSV históricos existentes en `assets/` (tanto cualquier `*plus*.csv`
 * como `provincia_completo.csv` filtrado a PROVINCIA + NOCTURNA) y los importa al historial interno
 * de Quiniela Plus sin requerir acción del usuario.
 */
class QuinielaPlusBootstrap(
    private val context: Context,
    private val dao: PlusSorteoDao,
    private val prefsStore: QuinielaPlusPrefsStore
) {
    suspend fun ensureInitialized(): Int = withContext(Dispatchers.IO) {
        if (prefsStore.isBootstrapDone()) return@withContext 0
        val imported = importFromAssetsInternal()
        prefsStore.setBootstrapDone(true)
        imported
    }

    suspend fun reimportarCsvBase(): Int = withContext(Dispatchers.IO) {
        prefsStore.clearManuallyDeletedDates()
        val imported = importFromAssetsInternal()
        prefsStore.setBootstrapDone(true)
        imported
    }

    private suspend fun importFromAssetsInternal(): Int {
        val assetFiles = runCatching { context.assets.list("")?.toList().orEmpty() }.getOrElse { emptyList() }
        val dedicatedPlusAssets = assetFiles.filter {
            it.endsWith(".csv", ignoreCase = true) && it.contains("plus", ignoreCase = true)
        }
        val orderedAssets = buildList {
            if ("provincia_completo.csv" in assetFiles) add("provincia_completo.csv")
            addAll(dedicatedPlusAssets.filter { it != "provincia_completo.csv" })
        }
        if (orderedAssets.isEmpty()) return 0

        val mergedByDate = LinkedHashMap<String, PlusSorteo>()
        orderedAssets.forEach { assetName ->
            val raw = runCatching {
                context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrNull() ?: return@forEach
            val parsed = QuinielaPlusCsvParser.parseAssetCsv(raw, assetName)
            parsed.forEach { s -> mergedByDate[s.fechaIso] = s }
        }

        if (mergedByDate.isEmpty()) return 0
        val entities = mergedByDate.values.map { PlusSorteoEntity.fromDomain(it) }
        entities.chunked(400).forEach { chunk -> dao.upsertAll(chunk) }
        return entities.size
    }

    /**
     * Sincroniza automáticamente nuevos sorteos de Nocturna Provincia recibidos por la app
     * hacia el historial persistente de Quiniela Plus, respetando borrados manuales del usuario.
     */
    suspend fun syncFromNocturnaProvincia(draws: List<SorteoDomain>) = withContext(Dispatchers.IO) {
        if (!prefsStore.isBootstrapDone()) return@withContext
        val deleted = prefsStore.getManuallyDeletedDates()
        val existingDates = dao.getAllOnce().map { it.fechaIso }.toSet()
        val nuevas = draws
            .filter { it.loteria == Jurisdiccion.PROVINCIA && it.turno == TurnoSorteo.NOCTURNA && it.numeros.size == 20 }
            .filter { it.fechaIso !in existingDates && it.fechaIso !in deleted }
            .distinctBy { it.fechaIso }
            .map { d ->
                PlusSorteoEntity(
                    fechaIso = d.fechaIso,
                    numerosPlusCsv = d.numeros.map { it.takeLast(2).padStart(2, '0') }.joinToString(","),
                    numeros4Csv = d.numeros.map { it.padStart(4, '0').takeLast(4) }.joinToString(","),
                    origen = "Auto Nocturna Provincia (${d.estado.label})",
                    notas = "Sincronizado automáticamente",
                    createdAtMs = System.currentTimeMillis()
                )
            }
        if (nuevas.isNotEmpty()) {
            dao.upsertAll(nuevas)
        }
    }
}
