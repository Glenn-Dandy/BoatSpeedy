package de.kewl.boatspeedy.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import de.kewl.boatspeedy.MainActivity
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.data.AlarmSound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dauer-Alarme (Ladestand erreicht / niedriger Ladestand): der Ton läuft in Schleife,
 * bis der Nutzer ihn **quittiert** – per Lautstärketaste, Antippen des Banners in der App
 * oder „Stumm" in der Benachrichtigung.
 */
object AlarmController {

    /** Text des aktiven Alarms (null = kein Alarm). */
    private val _active = MutableStateFlow<String?>(null)
    val active: StateFlow<String?> = _active.asStateFlow()

    private const val CHANNEL = "alarm_ack"
    private const val NOTIF_ID = 8
    const val ACTION_STOP = "de.kewl.boatspeedy.action.STOP_ALARM"

    /** Startet einen quittierpflichtigen Alarm. [withSound]=false → nur Banner/Meldung. */
    fun trigger(context: Context, title: String, text: String, sound: AlarmSound, withSound: Boolean) {
        _active.value = text
        if (withSound) AlarmPlayer.play(context, sound, loop = true)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.alarm_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val stop = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, AlarmStopReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, context.getString(R.string.anchor_silence), stop)
            .build()
        nm.notify(NOTIF_ID, n)
    }

    /** Quittieren: Ton aus, Benachrichtigung weg, Banner verschwindet. */
    fun stop(context: Context) {
        AlarmPlayer.stop()
        _active.value = null
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
    }

    val isActive: Boolean get() = _active.value != null
}
