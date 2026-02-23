package org.openauto.companion.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Plays inaudible silence through Android's audio system to keep the
 * AA media audio channel open. Some media sources don't properly trigger
 * the head unit to stream audio — this forces the channel to stay active
 * so background audio bleeds through.
 */
class SilentAudioPlayer {
    private var track: AudioTrack? = null

    fun start() {
        if (track != null) return

        try {
            val sampleRate = 16000
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            // Write a buffer of silence and set it to loop forever
            val silence = ByteArray(bufferSize)
            audioTrack.write(silence, 0, silence.size)
            audioTrack.setLoopPoints(0, bufferSize / 2, -1) // frames = bytes / 2 (16-bit mono)
            audioTrack.play()

            track = audioTrack
            Log.i(TAG, "Silent audio player started (${bufferSize} bytes, looping)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start silent audio player", e)
        }
    }

    fun stop() {
        track?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping silent audio player", e)
            }
        }
        track = null
        Log.i(TAG, "Silent audio player stopped")
    }

    val isActive: Boolean get() = track?.playState == AudioTrack.PLAYSTATE_PLAYING

    companion object {
        private const val TAG = "SilentAudioPlayer"
    }
}
