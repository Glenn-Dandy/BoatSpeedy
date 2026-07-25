package de.kewl.boatspeedy.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.data.AlarmSound

/**
 * Spielt die mitgelieferten Alarmtöne über den Alarm-Stream (laut, auch bei leisem
 * Klingeln). [loop]=true für den Ankeralarm (dauerhaft), false für einen kurzen
 * SoC-Alarm.
 */
object AlarmPlayer {

    private var player: MediaPlayer? = null

    fun rawRes(sound: AlarmSound): Int = when (sound) {
        AlarmSound.PIEP -> R.raw.alarm_piep
        AlarmSound.GLOCKE -> R.raw.alarm_glocke
        AlarmSound.SIRENE -> R.raw.alarm_sirene
    }

    fun play(context: Context, sound: AlarmSound, loop: Boolean) {
        stop()
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        val afd = context.resources.openRawResourceFd(rawRes(sound)) ?: return
        afd.use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        mp.isLooping = loop
        mp.setOnCompletionListener { if (!loop) stop() }
        mp.setOnPreparedListener { it.start() }
        runCatching { mp.prepareAsync() }
        player = mp
    }

    fun stop() {
        player?.let { p -> runCatching { if (p.isPlaying) p.stop(); p.release() } }
        player = null
    }

    val isPlaying: Boolean get() = runCatching { player?.isPlaying == true }.getOrDefault(false)
}
