package com.quinielazero.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.quinielazero.app.MainActivity
import com.quinielazero.app.R
import com.quinielazero.app.core.analytics.StatisticalEngine
import com.quinielazero.app.core.calendar.SorteoCalendarProvider
import com.quinielazero.app.core.model.BoletaJugada
import com.quinielazero.app.core.model.ConfiguracionApp
import com.quinielazero.app.core.model.Jurisdiccion
import com.quinielazero.app.core.model.SorteoDomain
import com.quinielazero.app.core.model.VentanaAnalisis
import com.quinielazero.app.core.network.DataSourcesManager
import com.quinielazero.app.data.local.EncryptedTicketStore
import com.quinielazero.app.data.local.HistorialBootstrap
import com.quinielazero.app.data.local.QuinielaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object NotificationChannels {
    const val CHANNEL_PICKS = "quiniela_zero_daily_picks"
    const val CHANNEL_SUMMARY = "quiniela_zero_daily_summary"
    const val CHANNEL_SOURCES = "quiniela_zero_source_alerts"

    fun createAll(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_PICKS,
                    "Recomendaciones Diarias (09:00)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Números estadísticos diarios y planificación semanal dominical."
                },
                NotificationChannel(
                    CHANNEL_SUMMARY,
                    "Resumen Nocturno y Semanal (23:00)",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Cierre de sorteos, control automático de Mis Jugadas y balance semanal."
                },
                NotificationChannel(
                    CHANNEL_SOURCES,
                    "Salud de Fuentes Oficiales",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Alertas técnicas si cambia la estructura de las webs oficiales."
                }
            )
        )
    }
}

object NotificationContentBuilder {
    fun buildMorningContent(
        date: LocalDate,
        calendar: SorteoCalendarProvider,
        picksProvincia: List<String>,
        picksCiudad: List<String>
    ): Pair<String, String> {
        val provTxt = picksProvincia.joinToString(" · ").ifEmpty { "--" }
        val ciuTxt = picksCiudad.joinToString(" · ").ifEmpty { "--" }
        return when {
            date.dayOfWeek == DayOfWeek.SUNDAY -> {
                "Domingo · Planificá tu semana" to
                    "Hoy es domingo y NO hay sorteos oficiales en Provincia ni Ciudad. ¡Arrancá la semana con foco! Sugeridos semanales — Prov: $provTxt | Ciu: $ciuTxt."
            }
            calendar.isHoliday(date) -> {
                "Modo Feriado · Planificación" to
                    "Hoy tenés el Modo Feriado activo (sin sorteos programados). Referencia estadística — Prov: $provTxt | Ciu: $ciuTxt."
            }
            else -> {
                "Quiniela-Zero · Picks del Día" to
                    "Provincia: $provTxt | Ciudad: $ciuTxt. Recordá que son estimaciones estadísticas. Jugá responsablemente."
            }
        }
    }

    fun buildNightContent(
        date: LocalDate,
        draws: List<SorteoDomain>,
        tickets: List<BoletaJugada>,
        picksProvincia: List<String>
    ): Pair<String, String> {
        if (date.dayOfWeek == DayOfWeek.SUNDAY) {
            val weekStart = date.minusDays(6).toString()
            val weekDraws = draws.filter { it.fechaIso >= weekStart }
            val weekTickets = tickets.filter { it.fechaIso >= weekStart }
            val totalHits = weekTickets.sumOf { it.aciertos.size }
            val topHead = weekDraws
                .groupingBy { it.cabezaDosCifras }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: "--"
            val msg = if (weekDraws.isEmpty()) {
                "Hoy domingo no hubo sorteos. Aún sin sorteos semanales cargados."
            } else {
                "Hoy domingo no hubo sorteos. Resumen semanal: ${weekDraws.size} sorteos procesados, terminación más frecuente '$topHead', aciertos en Mis Jugadas: $totalHits. Sugerencia para el lunes: ${picksProvincia.take(3).joinToString(" · ")}."
            }
            return "Resumen Semanal · Quiniela-Zero" to msg
        }

        val todayIso = date.toString()
        val todayDraws = draws.filter { it.fechaIso == todayIso }
        if (todayDraws.isEmpty()) {
            return "Cierre del Día · Sin extractos" to
                "No se registraron sorteos nuevos hoy ($todayIso) o están pendientes de verificación."
        }
        val cabezasTxt = todayDraws
            .sortedBy { it.turno.orden }
            .take(5)
            .joinToString(" | ") { "${it.loteria.label.take(4)} ${it.turno.label.take(4)}: ${it.cabeza}" }

        val todayTickets = tickets.filter { it.fechaIso == todayIso }
        val hitsToday = todayTickets.sumOf { t ->
            val matchDraw = todayDraws.find { it.loteria == t.jurisdiccion && it.turno == t.turno }
            if (matchDraw == null) 0 else t.numeros.count { n -> matchDraw.numeros.any { it.endsWith(n.takeLast(2)) } }
        }
        val hitsSuggested = todayDraws.count { d ->
            picksProvincia.any { p -> d.numeros.any { it.endsWith(p) } }
        }
        return "Resumen Nocturno · $todayIso" to
            "$cabezasTxt. Coincidencias c/estimación: $hitsSuggested · Aciertos en Mis Jugadas: $hitsToday."
    }
}

object NotificationScheduler {
    private const val REQ_MORNING = 9001
    private const val REQ_NIGHT = 23001
    const val EXTRA_KIND = "notif_kind"
    const val KIND_MORNING = "MORNING"
    const val KIND_NIGHT = "NIGHT"

    private val ZONE_AR = ZoneId.of("America/Argentina/Buenos_Aires")

    fun computeNextTriggerMillis(
        hour: Int,
        minute: Int,
        now: ZonedDateTime = ZonedDateTime.now(ZONE_AR)
    ): Long {
        var target = now.withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59)).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return target.toInstant().toEpochMilli()
    }

    fun scheduleAll(context: Context, config: ConfiguracionApp) {
        NotificationChannels.createAll(context)
        scheduleSingle(context, KIND_MORNING, REQ_MORNING, config.morningHour, config.morningMinute)
        scheduleSingle(context, KIND_NIGHT, REQ_NIGHT, config.nightHour, config.nightMinute)

        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val healthWork = PeriodicWorkRequestBuilder<SourceHealthWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "quiniela_source_health_monitor",
            ExistingPeriodicWorkPolicy.KEEP,
            healthWork
        )
    }

    private fun scheduleSingle(context: Context, kind: String, requestCode: Int, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            putExtra(EXTRA_KIND, kind)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = computeNextTriggerMillis(hour, minute)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            runCatching { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
                .onFailure { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }
}

class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val kind = intent.getStringExtra(NotificationScheduler.EXTRA_KIND) ?: NotificationScheduler.KIND_MORNING

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = EncryptedTicketStore(context)
                val config = store.loadConfig()
                val db = QuinielaDatabase.getInstance(context)
                HistorialBootstrap(context, db).ensureSeededFromAssets()

                val calendar = SorteoCalendarProvider()
                calendar.updateHolidayConfig(config.holidayMode, config.holidayDates)
                val today = calendar.todayArgentina()

                // Reprogramar siguiente día
                NotificationScheduler.scheduleAll(context, config)

                if (today.dayOfWeek.value in config.mutedDays) {
                    return@launch
                }

                val syncMgr = DataSourcesManager(db.sorteoDao(), db.fuenteSaludDao(), calendar)
                runCatching { syncMgr.syncAllSources(includeDirectScrape = true) }

                val draws = db.sorteoDao().getAllOnce().mapNotNull { it.toDomain() }
                calendar.calibrateFromHistory(draws)
                val engine = StatisticalEngine(calendar)

                val provRes = engine.analyze(
                    allDraws = draws,
                    jurisdiccion = Jurisdiccion.PROVINCIA,
                    ventana = VentanaAnalisis.DIAS_90,
                    topCount = config.topCount,
                    avoidImmediateRepeat = config.avoidImmediateRepeat,
                    includeCrossJurisdiction = config.includeCrossJurisdiction,
                    referenceDate = today
                )
                val ciuRes = engine.analyze(
                    allDraws = draws,
                    jurisdiccion = Jurisdiccion.CIUDAD,
                    ventana = VentanaAnalisis.DIAS_90,
                    topCount = config.topCount,
                    avoidImmediateRepeat = config.avoidImmediateRepeat,
                    includeCrossJurisdiction = config.includeCrossJurisdiction,
                    referenceDate = today
                )

                val (title, body) = if (kind == NotificationScheduler.KIND_MORNING) {
                    NotificationContentBuilder.buildMorningContent(
                        today,
                        calendar,
                        provRes.candidatos.map { it.numero },
                        ciuRes.candidatos.map { it.numero }
                    )
                } else {
                    NotificationContentBuilder.buildNightContent(
                        today,
                        draws,
                        store.loadTickets(),
                        provRes.candidatos.map { it.numero }
                    )
                }

                if (Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                ) {
                    val tapIntent = Intent(context, MainActivity::class.java)
                    val tapPi = PendingIntent.getActivity(
                        context,
                        0,
                        tapIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val channelId = if (kind == NotificationScheduler.KIND_MORNING) {
                        NotificationChannels.CHANNEL_PICKS
                    } else {
                        NotificationChannels.CHANNEL_SUMMARY
                    }
                    val notif = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.ic_launcher_monochrome_img)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                        .setContentIntent(tapPi)
                        .setAutoCancel(true)
                        .build()

                    NotificationManagerCompat.from(context).notify(
                        if (kind == NotificationScheduler.KIND_MORNING) 901 else 2301,
                        notif
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val cfg = EncryptedTicketStore(context).loadConfig()
            NotificationScheduler.scheduleAll(context, cfg)
        }
    }
}

class SourceHealthWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = QuinielaDatabase.getInstance(applicationContext)
        val calendar = SorteoCalendarProvider()
        val mgr = DataSourcesManager(db.sorteoDao(), db.fuenteSaludDao(), calendar)
        val report = runCatching { mgr.syncAllSources(includeDirectScrape = true) }.getOrNull()
        return if (report != null) Result.success() else Result.retry()
    }
}
