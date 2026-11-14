package com.swordfish.lemuroid.app.shared.achievements

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Plays a synthesized crystalline trophy chime and a double-pulse tactile celebration
 * when an achievement unlocks or a game is mastered. Uses [AudioTrack.MODE_STATIC]
 * with in-memory PCM samples so playback has zero decoding latency and zero asset bloat.
 */
class RetroAchievementsSoundPlayer(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val audioLock = Any()

    private var standardTrack: AudioTrack? = null
    private var fanfareTrack: AudioTrack? = null

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    fun playUnlockCelebration(
        fanfare: Boolean,
        playAudio: Boolean,
    ) {
        scope.launch(Dispatchers.Default) {
            triggerHaptics(fanfare)
            if (playAudio) {
                playChime(fanfare)
            }
        }
    }

    fun release() {
        synchronized(audioLock) {
            runCatching {
                standardTrack?.stop()
                standardTrack?.release()
            }
            runCatching {
                fanfareTrack?.stop()
                fanfareTrack?.release()
            }
            standardTrack = null
            fanfareTrack = null
        }
    }

    private fun playChime(fanfare: Boolean) {
        synchronized(audioLock) {
            runCatching {
                val track =
                    if (fanfare) {
                        fanfareTrack ?: buildStaticTrack(synthesizeFanfareChime()).also { fanfareTrack = it }
                    } else {
                        standardTrack ?: buildStaticTrack(synthesizeStandardChime()).also { standardTrack = it }
                    } ?: return

                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.reloadStaticData()
                track.setPlaybackHeadPosition(0)
                track.play()
            }
        }
    }

    private fun triggerHaptics(fanfare: Boolean) {
        runCatching {
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect =
                    if (vib.hasAmplitudeControl()) {
                        if (fanfare) {
                            VibrationEffect.createWaveform(
                                longArrayOf(0L, 22L, 48L, 28L, 54L, 54L),
                                intArrayOf(0, 130, 0, 180, 0, 255),
                                -1,
                            )
                        } else {
                            VibrationEffect.createWaveform(
                                longArrayOf(0L, 20L, 52L, 36L),
                                intArrayOf(0, 145, 0, 230),
                                -1,
                            )
                        }
                    } else {
                        if (fanfare) {
                            VibrationEffect.createWaveform(longArrayOf(0L, 18L, 50L, 24L, 56L, 40L), -1)
                        } else {
                            VibrationEffect.createWaveform(longArrayOf(0L, 18L, 55L, 30L), -1)
                        }
                    }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(if (fanfare) 70L else 40L)
            }
        }
    }

    private fun buildStaticTrack(samples: ShortArray): AudioTrack? {
        return runCatching {
            val byteSize = samples.size * 2
            val track =
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(byteSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

            val written = track.write(samples, 0, samples.size)
            if (written <= 0) {
                track.release()
                null
            } else {
                track
            }
        }.getOrNull()
    }

    private data class BellNote(
        val startSeconds: Double,
        val freqHz: Double,
        val gain: Double,
        val decayRate: Double,
    )

    private fun synthesizeStandardChime(): ShortArray {
        // Rising C6 -> G6 -> C7 + E6 crystalline chord sparkle (~560 ms)
        val notes =
            listOf(
                BellNote(startSeconds = 0.000, freqHz = 1046.50, gain = 0.36, decayRate = 8.5),
                BellNote(startSeconds = 0.082, freqHz = 1567.98, gain = 0.38, decayRate = 7.8),
                BellNote(startSeconds = 0.168, freqHz = 2093.00, gain = 0.44, decayRate = 5.4),
                BellNote(startSeconds = 0.168, freqHz = 1318.51, gain = 0.22, decayRate = 5.8),
            )
        return renderBellNotes(durationSeconds = 0.56, notes = notes, masterPeak = 0.44)
    }

    private fun synthesizeFanfareChime(): ShortArray {
        // Golden G5 -> B5 -> D6 -> G6/B6/D7 triumphant shimmer (~820 ms)
        val notes =
            listOf(
                BellNote(startSeconds = 0.000, freqHz = 783.99, gain = 0.34, decayRate = 8.2),
                BellNote(startSeconds = 0.078, freqHz = 987.77, gain = 0.36, decayRate = 7.8),
                BellNote(startSeconds = 0.156, freqHz = 1174.66, gain = 0.38, decayRate = 7.2),
                BellNote(startSeconds = 0.242, freqHz = 1567.98, gain = 0.45, decayRate = 4.2),
                BellNote(startSeconds = 0.242, freqHz = 1975.53, gain = 0.25, decayRate = 4.6),
                BellNote(startSeconds = 0.242, freqHz = 2349.32, gain = 0.18, decayRate = 5.2),
            )
        return renderBellNotes(durationSeconds = 0.82, notes = notes, masterPeak = 0.48)
    }

    private fun renderBellNotes(
        durationSeconds: Double,
        notes: List<BellNote>,
        masterPeak: Double,
    ): ShortArray {
        val numSamples = (durationSeconds * SAMPLE_RATE).roundToInt()
        val mix = DoubleArray(numSamples)
        var maxAbs = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var sample = 0.0
            for (note in notes) {
                val dt = t - note.startSeconds
                if (dt < 0.0) continue
                val attack =
                    if (dt < ATTACK_SECONDS) {
                        0.5 * (1.0 - cos(PI * dt / ATTACK_SECONDS))
                    } else {
                        1.0
                    }
                val env = attack * exp(-note.decayRate * dt)
                val w = 2.0 * PI * note.freqHz * dt
                val harmonic =
                    sin(w) +
                        0.26 * exp(-4.0 * dt) * sin(2.0 * w) +
                        0.09 * exp(-9.0 * dt) * sin(3.0 * w)
                sample += note.gain * env * harmonic
            }

            val remaining = durationSeconds - t
            if (remaining < TAIL_FADE_SECONDS) {
                sample *= (remaining / TAIL_FADE_SECONDS).coerceIn(0.0, 1.0)
            }

            mix[i] = sample
            val absVal = kotlin.math.abs(sample)
            if (absVal > maxAbs) {
                maxAbs = absVal
            }
        }

        val scale = if (maxAbs > 1e-6) (masterPeak / maxAbs) else 0.0
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val scaled = (mix[i] * scale * Short.MAX_VALUE).roundToInt()
            pcm[i] = scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return pcm
    }

    companion object {
        private const val SAMPLE_RATE = 44_100
        private const val ATTACK_SECONDS = 0.005
        private const val TAIL_FADE_SECONDS = 0.025
    }
}
