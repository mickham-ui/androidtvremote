package com.out.androidtvremote.ui.frags.remote

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.out.androidtvremote.androidLib.AndroidRemoteTv
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streams microphone audio to the TV using the Android TV Remote voice protocol.
 *
 * Protocol (per tronikos/androidtvremote2):
 *   App → TV : KEYCODE_SEARCH  (triggerVoiceSearch)
 *   TV  → App: RemoteVoiceBegin { session_id }          ← handled in RemoteSession
 *   App → TV : RemoteVoiceBegin { session_id }  (ACK)   ← sent automatically in RemoteSession
 *   App → TV : RemoteVoicePayload { session_id, samples } × N
 *   App → TV : RemoteVoiceEnd   { session_id }
 *
 * Audio format: 16-bit PCM, 8 kHz, mono.
 * Chunk size:   320 bytes = 20 ms per packet.
 */
class TvVoiceController(
    private val tv: AndroidRemoteTv,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "TvVoiceController"

        // 16-bit PCM, 8 kHz mono — same as the reference Python implementation
        private const val SAMPLE_RATE     = 8000
        private const val CHANNEL_CONFIG  = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT    = AudioFormat.ENCODING_PCM_16BIT

        // 320 bytes = 160 samples = 20 ms at 8 kHz 16-bit mono
        private const val CHUNK_SIZE      = 320

        // Silence / VAD tuning
        private const val SILENCE_THRESHOLD_RMS = 300       // below this = silence
        private const val SILENCE_STOP_MS        = 2000L    // stop after 2 s of silence
        private const val MAX_RECORD_MS          = 10_000L  // hard cap 10 s

        // Software gain — boosts quiet microphones to match TV's ASR threshold
        // Increase to 2.5f if ASR still fails; decrease to 1.5f if audio clips
        private const val GAIN_FACTOR     = 2.0f
    }

    // Callbacks for UI feedback
    var onRecordingStarted:  (() -> Unit)? = null
    var onRecordingStopped:  (() -> Unit)? = null
    var onError:             ((String) -> Unit)? = null

    private val isRecording = AtomicBoolean(false)
    private val voiceEndSent = AtomicBoolean(false)
    private var recordingJob: Job? = null

    /** Call this when the TV fires onVoiceSessionStarted(sessionId). */
    fun startStreaming(sessionId: Int, context: android.content.Context) {
        if (isRecording.getAndSet(true)) {
            Log.w(TAG, "startStreaming called while already recording — ignored")
            return
        }

        // Check mic permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            isRecording.set(false)
            onError?.invoke("Microphone permission not granted")
            return
        }

        voiceEndSent.set(false)

        recordingJob = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufSize = maxOf(minBuf, CHUNK_SIZE * 4)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufSize
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                isRecording.set(false)
                withContext(Dispatchers.Main) { onError?.invoke("AudioRecord init failed") }
                return@launch
            }

            recorder.startRecording()
            withContext(Dispatchers.Main) { onRecordingStarted?.invoke() }
            Log.i(TAG, "Recording started — sessionId=$sessionId sampleRate=$SAMPLE_RATE gain=$GAIN_FACTOR")

            val buffer = ByteArray(CHUNK_SIZE)
            var silenceDurationMs = 0L
            val startTime = System.currentTimeMillis()

            try {
                while (isActive && isRecording.get()) {
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed >= MAX_RECORD_MS) {
                        Log.i(TAG, "Max record time reached")
                        break
                    }

                    val bytesRead = recorder.read(buffer, 0, CHUNK_SIZE)
                    if (bytesRead <= 0) continue

                    val rms = computeRms(buffer, bytesRead)

                    // VAD: silence detection on pre-gain signal
                    if (rms < SILENCE_THRESHOLD_RMS) {
                        silenceDurationMs += (bytesRead.toLong() * 1000L) / (SAMPLE_RATE * 2)
                        if (silenceDurationMs >= SILENCE_STOP_MS) {
                            Log.i(TAG, "Silence detected after ${silenceDurationMs}ms — stopping")
                            break
                        }
                    } else {
                        silenceDurationMs = 0L
                    }

                    // Apply software gain and send to TV
                    val amplified = amplify(buffer, bytesRead, GAIN_FACTOR)
//                    tv.sendVoiceChunk(sessionId, amplified)
                }
            } finally {
                recorder.stop()
                recorder.release()
                sendVoiceEndOnce(sessionId)
                isRecording.set(false)
                withContext(Dispatchers.Main) { onRecordingStopped?.invoke() }
                Log.i(TAG, "Recording stopped — sessionId=$sessionId")
            }
        }
    }

    /** Call this if the user manually stops recording (e.g. taps button again). */
    fun stopStreaming(sessionId: Int) {
        if (!isRecording.get()) return
        isRecording.set(false)
        recordingJob?.cancel()
        sendVoiceEndOnce(sessionId)
        onRecordingStopped?.invoke()
    }

    private fun sendVoiceEndOnce(sessionId: Int) {
        if (voiceEndSent.compareAndSet(false, true)) {
//            tv.endVoiceSession(sessionId)
            Log.i(TAG, "VoiceEnd sent — sessionId=$sessionId")
        }
    }

    /**
     * Amplifies raw 16-bit little-endian PCM by [gain], clipping at ±32767.
     */
    private fun amplify(src: ByteArray, length: Int, gain: Float): ByteArray {
        val dst = src.copyOf(length)
        var i = 0
        while (i + 1 < length) {
            val sample = (dst[i].toInt() and 0xFF) or (dst[i + 1].toInt() shl 8)
            val amplified = (sample * gain).toInt().coerceIn(-32768, 32767)
            dst[i]     = (amplified and 0xFF).toByte()
            dst[i + 1] = ((amplified shr 8) and 0xFF).toByte()
            i += 2
        }
        return dst
    }

    /**
     * RMS of 16-bit little-endian PCM samples.
     */
    private fun computeRms(buffer: ByteArray, length: Int): Double {
        var sumSq = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            sumSq += sample.toDouble() * sample.toDouble()
            count++
            i += 2
        }
        return if (count == 0) 0.0 else Math.sqrt(sumSq / count)
    }
}