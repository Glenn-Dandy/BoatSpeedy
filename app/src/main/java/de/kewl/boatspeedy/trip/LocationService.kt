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
            buildNotification(getString(R.string.trip_starting)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

        if (job?.isActive == true) return
        val provider = LocationProvider(applicationContext)
        val settings = SettingsRepository(applicationContext).settings
            .stateIn(scope, SharingStarted.Eagerly, de.kewl.boatspeedy.data.Settings())

        job = scope.launch {
            combine(provider.state, settings) { gps, s -> gps to s }.collect { (gps, s) ->
                TripRepository.onLocation(gps)
                updateNotification(notificationText(gps.speedMs, s.unit))
            }
        }
    }

    private fun notificationText(speedMs: Float?, unit: SpeedUnit): String {
        val stats = TripRepository.stats.value
        val speedStr = if (speedMs == null) "--" else
            String.format(Locale.getDefault(), "%.1f %s", speedMs * unit.factorFromMs, unit.label)
        val distStr = formatDistance(stats.distanceM)
        return "$speedStr · $distStr"
    }

    private fun formatDistance(m: Double): String =
        if (m < 1000) "${m.roundToInt()} m"
        else String.format(Locale.getDefault(), "%.2f km", m / 1000.0)

    private fun buildNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.trip_running))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
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
