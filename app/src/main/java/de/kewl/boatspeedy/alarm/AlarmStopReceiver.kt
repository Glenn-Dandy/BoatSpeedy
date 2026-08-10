package de.kewl.boatspeedy.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** „Stumm" aus der Alarm-Benachrichtigung. */
class AlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == AlarmController.ACTION_STOP) AlarmController.stop(context)
    }
}
