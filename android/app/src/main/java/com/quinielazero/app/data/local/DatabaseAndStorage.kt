package com.quinielazero.app.data.local

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.quinielazero.app.core.model.BoletaJugada
import com.quinielazero.app.core.model.ConfiguracionApp
import com.quinielazero.app.core.model.EstadoVerificacion
import com.quinielazero.app.core.model.FuenteSaludInfo
import com.quinielazero.app.core.model.Jurisdiccion
import com.quinielazero.app.core.model.SorteoDomain
import com.quinielazero.app.core.model.ThemeMode
import com.quinielazero.app.core.model.TurnoSorteo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID

@Entity(
    tableName = "sorteos",
    indices = [
        Index(value = ["loteria", "fechaIso", "turno"], unique = true),
        Index(value = ["loteria", "fechaIso"])
    ]
)
data class SorteoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loteria: String,
    val fechaIso: String,
    val turno: String,
    val datosCsv: String,
    val estadoVerificacion: String,
    val fuentePrimaria: String,
    val fuenteSecundaria: String,
    val nroSorteo: String
) {
    fun toDomain(): SorteoDomain? {
        val t = TurnoSorteo.normalizar(turno) ?: return null
        val nums = datosCsv.split(",").map { it.trim().padStart(4, '0') }
        if (nums.size != 20) return null
        return SorteoDomain(
            id = id,
            loteria = Jurisdiccion.fromString(loteria),
            fechaIso = fechaIso,
            turno = t,
            numeros = nums,
            estado = EstadoVerificacion.fromString(estadoVerificacion),
            fuentePrimaria = fuentePrimaria,
            fuenteSecundaria = fuenteSecundaria,
            nroSorteo = nroSorteo
        )
    }
}

@Entity(tableName = "fuentes_salud")
data class FuenteSaludEntity(
    @PrimaryKey val nombre: String,
    val jurisdiccion: String,
    val estado: String,
    val httpStatus: Int,
    val latenciaMs: Int,
    val detalle: String,
    val checkedAt: String
) {
    fun toDomain() = FuenteSaludInfo(
        nombre = nombre,
        jurisdiccion = jurisdiccion,
        estado = estado,
        httpStatus = httpStatus,
        latenciaMs = latenciaMs,
        detalle = detalle,
        checkedAt = checkedAt
    )
}

@Dao
interface SorteoDao {
    @Query("SELECT * FROM sorteos ORDER BY fechaIso DESC, id DESC")
    fun observeAll(): Flow<List<SorteoEntity>>

    @Query("SELECT * FROM sorteos ORDER BY fechaIso DESC, id DESC")
    suspend fun getAllOnce(): List<SorteoEntity>

    @Query("SELECT COUNT(*) FROM sorteos")
    suspend fun countAll(): Int

    @Query("SELECT * FROM sorteos WHERE loteria = :loteria AND fechaIso = :fecha AND turno = :turno LIMIT 1")
    suspend fun findOne(loteria: String, fecha: String, turno: String): SorteoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SorteoEntity>)
}

@Dao
interface FuenteSaludDao {
    @Query("SELECT * FROM fuentes_salud ORDER BY nombre ASC")
    fun observeAll(): Flow<List<FuenteSaludEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FuenteSaludEntity>)
}

@Database(
    entities = [SorteoEntity::class, FuenteSaludEntity::class],
    version = 1,
    exportSchema = false
)
abstract class QuinielaDatabase : RoomDatabase() {
    abstract fun sorteoDao(): SorteoDao
    abstract fun fuenteSaludDao(): FuenteSaludDao

    companion object {
        @Volatile
        private var INSTANCE: QuinielaDatabase? = null

        fun getInstance(context: Context): QuinielaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuinielaDatabase::class.java,
                    "quiniela_zero_pro.db"
                ).build().also { INSTANCE = it }
            }
    }
}

class HistorialBootstrap(
    private val context: Context,
    private val db: QuinielaDatabase
) {
    suspend fun ensureSeededFromAssets() = withContext(Dispatchers.IO) {
        val dao = db.sorteoDao()
        if (dao.countAll() > 0) return@withContext

        val batch = ArrayList<SorteoEntity>(1000)
        for ((assetName, jurDefault) in listOf(
            "provincia_completo.csv" to "PROVINCIA",
            "ciudad_completo.csv" to "CIUDAD"
        )) {
            runCatching {
                context.assets.open(assetName).use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                        reader.readLine() // header
                        var line: String? = reader.readLine()
                        while (line != null) {
                            val parts = line.split(",")
                            if (parts.size >= 23) {
                                val fecha = parts[0].trim()
                                val lot = parts[1].trim().ifEmpty { jurDefault }.uppercase()
                                val turno = TurnoSorteo.normalizar(parts[2])?.dbValue
                                val nums = parts.subList(3, 23).map { it.trim().padStart(4, '0') }
                                if (fecha.length == 10 && turno != null && nums.all { it.length == 4 && it.all(Char::isDigit) }) {
                                    batch.add(
                                        SorteoEntity(
                                            loteria = lot,
                                            fechaIso = fecha,
                                            turno = turno,
                                            datosCsv = nums.joinToString(","),
                                            estadoVerificacion = "HISTORIAL",
                                            fuentePrimaria = assetName,
                                            fuenteSecundaria = "",
                                            nroSorteo = ""
                                        )
                                    )
                                    if (batch.size >= 800) {
                                        dao.upsertAll(batch.toList())
                                        batch.clear()
                                    }
                                }
                            }
                            line = reader.readLine()
                        }
                    }
                }
            }
        }
        if (batch.isNotEmpty()) {
            dao.upsertAll(batch)
        }
    }
}

class EncryptedTicketStore(context: Context) {
    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "quiniela_zero_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        context.getSharedPreferences("quiniela_zero_fallback_prefs", Context.MODE_PRIVATE)
    }

    fun loadConfig(): ConfiguracionApp {
        val raw = prefs.getString("config_json", null) ?: return ConfiguracionApp()
        return runCatching {
            val obj = JSONObject(raw)
            val mutedArr = obj.optJSONArray("mutedDays") ?: JSONArray()
            val holidayArr = obj.optJSONArray("holidayDates") ?: JSONArray()
            ConfiguracionApp(
                themeMode = ThemeMode.entries.getOrElse(obj.optInt("themeMode", 0)) { ThemeMode.AUTO },
                holidayMode = obj.optBoolean("holidayMode", false),
                holidayDates = (0 until holidayArr.length()).map { holidayArr.getString(it) }.toSet(),
                morningHour = obj.optInt("morningHour", 9),
                morningMinute = obj.optInt("morningMinute", 0),
                nightHour = obj.optInt("nightHour", 23),
                nightMinute = obj.optInt("nightMinute", 0),
                mutedDays = (0 until mutedArr.length()).map { mutedArr.getInt(it) }.toSet(),
                dailyLimitArs = obj.optDouble("dailyLimitArs", 5000.0),
                weeklyLimitArs = obj.optDouble("weeklyLimitArs", 20000.0),
                avoidImmediateRepeat = obj.optBoolean("avoidImmediateRepeat", true),
                topCount = obj.optInt("topCount", 5),
                includeCrossJurisdiction = obj.optBoolean("includeCrossJurisdiction", true)
            )
        }.getOrElse { ConfiguracionApp() }
    }

    fun saveConfig(cfg: ConfiguracionApp) {
        val obj = JSONObject().apply {
            put("themeMode", cfg.themeMode.ordinal)
            put("holidayMode", cfg.holidayMode)
            put("holidayDates", JSONArray(cfg.holidayDates.toList()))
            put("morningHour", cfg.morningHour)
            put("morningMinute", cfg.morningMinute)
            put("nightHour", cfg.nightHour)
            put("nightMinute", cfg.nightMinute)
            put("mutedDays", JSONArray(cfg.mutedDays.toList()))
            put("dailyLimitArs", cfg.dailyLimitArs)
            put("weeklyLimitArs", cfg.weeklyLimitArs)
            put("avoidImmediateRepeat", cfg.avoidImmediateRepeat)
            put("topCount", cfg.topCount)
            put("includeCrossJurisdiction", cfg.includeCrossJurisdiction)
        }
        prefs.edit().putString("config_json", obj.toString()).apply()
    }

    fun loadTickets(): List<BoletaJugada> {
        val raw = prefs.getString("tickets_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val nums = o.getJSONArray("numeros")
                val hits = o.optJSONArray("aciertos") ?: JSONArray()
                BoletaJugada(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    fechaIso = o.getString("fechaIso"),
                    jurisdiccion = Jurisdiccion.fromString(o.getString("jurisdiccion")),
                    turno = TurnoSorteo.normalizar(o.getString("turno")) ?: return@mapNotNull null,
                    numeros = (0 until nums.length()).map { nums.getString(it) },
                    montoArs = o.optDouble("montoArs", 0.0),
                    aciertos = (0 until hits.length()).map { hits.getString(it) },
                    controlado = o.optBoolean("controlado", false)
                )
            }
        }.getOrElse { emptyList() }
    }

    fun saveTickets(tickets: List<BoletaJugada>) {
        val arr = JSONArray()
        tickets.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("fechaIso", t.fechaIso)
                    put("jurisdiccion", t.jurisdiccion.dbValue)
                    put("turno", t.turno.dbValue)
                    put("numeros", JSONArray(t.numeros))
                    put("montoArs", t.montoArs)
                    put("aciertos", JSONArray(t.aciertos))
                    put("controlado", t.controlado)
                }
            )
        }
        prefs.edit().putString("tickets_json", arr.toString()).apply()
    }
}

class ExportManager(private val context: Context) {
    suspend fun exportCsvAndShare(draws: List<SorteoDomain>): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "quiniela_zero_historial.csv")
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("fecha,loteria,turno,estado," + (1..20).joinToString(",") { "n$it" } + "\n")
            draws.forEach { d ->
                w.write("${d.fechaIso},${d.loteria.dbValue},${d.turno.dbValue},${d.estado.dbValue},${d.numeros.joinToString(",")}\n")
            }
        }
        file
    }

    suspend fun exportPdfAndShare(draws: List<SorteoDomain>): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "quiniela_zero_resumen.pdf")
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 10f }

        canvas.drawText("Quiniela-Zero Pro Max · Respaldo Oficial de Historial", 36f, 44f, titlePaint)
        canvas.drawText("Total registros exportados: ${draws.size} (mostrando últimos 40)", 36f, 64f, bodyPaint)

        var y = 92f
        draws.take(40).forEach { d ->
            val top5 = d.numeros.take(5).joinToString(" · ")
            canvas.drawText("${d.fechaIso} | ${d.loteria.label} | ${d.turno.label} | Cab: ${d.cabeza} | [$top5] (${d.estado.label})", 36f, y, bodyPaint)
            y += 17f
        }
        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        file
    }

    fun shareFile(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Exportar historial Quiniela-Zero").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
