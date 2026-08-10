package de.kewl.boatspeedy.trip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import de.kewl.boatspeedy.MainActivity
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.data.SettingsRepository
import de.kewl.boatspeedy.data.NotifField
import de.kewl.boatspeedy.data.SpeedUnit
import de.kewl.boatspeedy.location.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Vordergrunddienst, der während einer Fahrt GPS-Updates sammelt (auch bei
 * ausgeschaltetem Bildschirm / App im Hintergrund) und die Kennzahlen über
 * [TripRepository] hochrechnet. Zeigt eine dauerhafte Benachrichtigung.
 */
class LocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                TripRepository.endTrip()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> start()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        TripRepository.beginTrip()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(getString(R.string.trip_starting) to ""),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

        if (job?.isActive == true) return
        val provider = LocationProvider(applicationContext)
        val settings = SettingsRepository(applicationContext).settings
            .stateIn(scope, SharingStarted.Eagerly, de.kewl.boatspeedy.data.Settings())

        job = scope.launch {
            combine(
                provider.state,
                settings,
                de.kewl.boatspeedy.battery.BatteryRepository.state,
            ) { gps, s, hub -> Triple(gps, s, hub) }.collect { (gps, s, hub) ->
                TripRepository.onLocation(gps)
                updateNotification(notificationLines(gps, s, hub))
                maybeCheckWeather(gps.latitude, gps.longitude, s)
            }
        }
    }

    private var lastWeatherMs = 0L

    /** Während der Fahrt (auch bei ausgeschaltetem Bildschirm) DWD-Warnungen prüfen. */
    private fun maybeCheckWeather(lat: Double?, lon: Double?, s: de.kewl.boatspeedy.data.Settings) {
        if (!s.weatherWarnEnabled || lat == null || lon == null) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastWeatherMs < WEATHER_INTERVAL_MS) return
        lastWeatherMs = now
        scope.launch {
            de.kewl.boatspeedy.weather.WeatherRepository.check(
                applicationContext, lat, lon, s.weatherAlarmOn, s.weatherSound,
            )
        }
    }

    /**
     * Baut die (bis zu) zwei Zeilen der Fahrt-Benachrichtigung aus den in den Einstellungen
     * gewählten Werten. Zeile 1 ist eingeklappt sichtbar, Zeile 2 nur aufgeklappt.
     */
    private fun notificationLines(
        gps: de.kewl.boatspeedy.location.GpsState,
        s: de.kewl.boatspeedy.data.Settings,
        hub: de.kewl.boatspeedy.battery.BatteryHub,
    ): Pair<String, String> {
        val stats = TripRepository.stats.value
        val bank = de.kewl.boatspeedy.battery.activeBatteryData(s, hub).takeIf { it.isNotEmpty() }
            ?.let { de.kewl.boatspeedy.battery.combineBatteries(it, s.bankMode) }
        val range = de.kewl.boatspeedy.battery.estimateRange(bank, gps.speedMs)

        fun value(f: NotifField): String? = when (f) {
            NotifField.SPEED -> gps.speedMs?.let {
                String.format(Locale.getDefault(), "%.1f %s", it * s.unit.factorFromMs, s.unit.label)
            } ?: "--"
            NotifField.DISTANCE -> formatDistance(stats.distanceM)
            NotifField.TIME -> formatDuration(stats.elapsedMs)
            NotifField.CHARGE_AH -> String.format(Locale.getDefault(), "%.1f Ah", stats.chargeAh)
            NotifField.ENERGY_WH -> String.format(Locale.getDefault(), "%.0f Wh", stats.energyWh)
            NotifField.SOC -> bank?.takeIf { it.voltage > 0f }?.let { "${getString(R.string.soc_short)} ${it.soc} %" }
            NotifField.RANGE -> range?.let { formatDistance(it.km * 1000.0) }
            NotifField.TIME_LEFT -> range?.let { formatDuration((it.hours * 3600_000).toLong()) }
        }

        fun line(n: Int) = NotifField.entries
            .filter { it.line == n && it in s.notifFields }
            .mapNotNull { value(it) }
            .joinToString(" · ")

        return line(1) to line(2)
    }

    private fun formatDuration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val sec = total % 60
        return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.getDefault(), "%d:%02d", m, sec)
    }

    private fun formatDistance(m: Double): String =
        if (m < 1000) "${m.roundToInt()} m"
        else String.format(Locale.getDefault(), "%.2f km", m / 1000.0)

    private fun buildNotification(lines: Pair<String, String>): Notification {
        val (line1, line2) = lines
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val big = if (line2.isBlank()) line1 else "$line1\n$line2"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.trip_running))
            .setContentText(line1)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(lines: Pair<String, String>) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(lines))
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.trip_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "trip"
        private const val NOTIF_ID = 1
        private const val WEATHER_INTERVAL_MS = 10 * 60_000L
        const val ACTION_START = "de.kewl.boatspeedy.action.START"
        const val ACTION_STOP = "de.kewl.boatspeedy.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, LocationService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
