package de.kewl.boatspeedy.anchor

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
import de.kewl.boatspeedy.alarm.AlarmPlayer
import de.kewl.boatspeedy.data.AlarmSound
import de.kewl.boatspeedy.location.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Vordergrunddienst des Ankeralarms: überwacht die Distanz zum gesetzten Ankerpunkt
 * (auch bei ausgeschaltetem Display) und spielt bei Überschreiten des Radius den
 * gewählten Alarmton in Schleife. Steuerung über [AnchorRepository].
 */
class AnchorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var alarmSound: AlarmSound = AlarmSound.PIEP

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AlarmPlayer.stop()
                AnchorRepository.clear()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SILENCE -> {
                AnchorRepository.silence()
                AlarmPlayer.stop()
                updateNotification()
                return START_STICKY
            }
            else -> {
                intent?.getStringExtra(EXTRA_SOUND)?.let {
                    alarmSound = runCatching { AlarmSound.valueOf(it) }.getOrDefault(AlarmSound.PIEP)
                }
                start()
            }
        }
        return START_STICKY
    }

    private fun start() {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        if (job?.isActive == true) return
        val provider = LocationProvider(applicationContext)
        job = scope.launch {
            provider.state.collect { gps ->
                val lat = gps.latitude
                val lon = gps.longitude
                if (lat != null && lon != null) {
                    AnchorRepository.onLocation(lat, lon)
                }
                val s = AnchorRepository.state.value
                if (s.alarming && !AlarmPlayer.isPlaying) {
                    AlarmPlayer.play(applicationContext, alarmSound, loop = true)
                } else if (!s.alarming && AlarmPlayer.isPlaying) {
                    AlarmPlayer.stop()
                }
                updateNotification()
            }
        }
    }

    private fun buildNotification(): Notification {
        val s = AnchorRepository.state.value
        val text = if (s.alarming) {
            getString(R.string.anchor_alarm_dragging, s.distanceM.roundToInt(), s.radiusM)
        } else {
            getString(R.string.anchor_watch_text, s.distanceM.roundToInt(), s.radiusM)
        }
        val tapIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.anchor_watch_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .setPriority(if (s.alarming) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.anchor_raise), action(ACTION_STOP))
        if (s.alarming) {
            builder.addAction(0, getString(R.string.anchor_silence), action(ACTION_SILENCE))
        }
        return builder.build()
    }

    private fun action(a: String): PendingIntent {
        val intent = Intent(this, AnchorService::class.java).setAction(a)
        return PendingIntent.getService(
            this, a.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.anchor_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        AlarmPlayer.stop()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "anchor"
        private const val NOTIF_ID = 2
        private const val EXTRA_SOUND = "sound"
        const val ACTION_START = "de.kewl.boatspeedy.action.ANCHOR_START"
        const val ACTION_STOP = "de.kewl.boatspeedy.action.ANCHOR_STOP"
        const val ACTION_SILENCE = "de.kewl.boatspeedy.action.ANCHOR_SILENCE"

        fun start(context: Context, sound: AlarmSound) {
            val intent = Intent(context, AnchorService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SOUND, sound.name)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AnchorService::class.java).setAction(ACTION_STOP))
        }

        fun silence(context: Context) {
            context.startService(Intent(context, AnchorService::class.java).setAction(ACTION_SILENCE))
        }
    }
}
