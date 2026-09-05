package com.myra.assistant.ai

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Native PCM Audio Engine powering MYRA.
 * Input: 16kHz Mono 16-bit PCM AudioRecord.
 * Output: 24kHz Mono 16-bit PCM AudioTrack.
 */
class AudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "AudioEngine"
        const val MIC_SAMPLE_RATE = 16000
        const val SPEAKER_SAMPLE_RATE = 24000
        const val CHUNK_SIZE = 1024
    }

    private val engineScope = CoroutineScope(Dispatchers.IO + Job())

    // Recording components
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    @Volatile var isRecording: Boolean = false
        private set
    @Volatile var isMuted: Boolean = false
    @Volatile var isSpeaking: Boolean = false
        private set

    // Playback components
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val audioPlaybackQueue = LinkedBlockingQueue<ByteArray>()

    // Callbacks
    var onAudioCaptured: ((ByteArray) -> Unit)? = null
    var onAmplitudeChanged: ((Float) -> Unit)? = null
    var onSpeakingStarted: (() -> Unit)? = null
    var onSpeakingStopped: (() -> Unit)? = null

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SPEAKER_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize * 2, 8192)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SPEAKER_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) return

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                MIC_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize * 2, CHUNK_SIZE * 4)

            // Prefer VOICE_RECOGNITION, fallback to MIC
            var record: AudioRecord? = null
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MIC_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            } catch (e: Exception) {
                Log.w(TAG, "VOICE_RECOGNITION source unavailable, falling back to MIC")
            }

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    MIC_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord could not be initialized")
                return
            }

            audioRecord = record
            record.startRecording()
            isRecording = true

            recordingJob = engineScope.launch {
                val buffer = ByteArray(CHUNK_SIZE)
                while (isActive && isRecording) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        val rms = calculateRms(buffer, readBytes)
                        onAmplitudeChanged?.invoke(rms)

                        // Suppress mic echo when MYRA is speaking or when muted
                        if (!isMuted && !isSpeaking) {
                            val chunk = buffer.copyOf(readBytes)
                            onAudioCaptured?.invoke(chunk)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}")
            isRecording = false
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null
    }

    fun startPlayback() {
        if (playbackJob != null && playbackJob?.isActive == true) return

        try {
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                initAudioTrack()
            }
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioTrack playback: ${e.message}")
        }

        playbackJob = engineScope.launch {
            var speakingState = false
            while (isActive) {
                val pcmChunk = audioPlaybackQueue.poll(80, TimeUnit.MILLISECONDS)
                if (pcmChunk != null && pcmChunk.isNotEmpty()) {
                    if (!speakingState) {
                        speakingState = true
                        isSpeaking = true
                        onSpeakingStarted?.invoke()
                    }
                    audioTrack?.write(pcmChunk, 0, pcmChunk.size)
                } else {
                    if (speakingState && audioPlaybackQueue.isEmpty()) {
                        speakingState = false
                        isSpeaking = false
                        onSpeakingStopped?.invoke()
                    }
                }
            }
        }
    }

    fun queueAudio(pcmData: ByteArray) {
        if (pcmData.isNotEmpty()) {
            audioPlaybackQueue.offer(pcmData)
        }
    }

    fun clearPlaybackQueue() {
        audioPlaybackQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing AudioTrack: ${e.message}")
        }
        if (isSpeaking) {
            isSpeaking = false
            onSpeakingStopped?.invoke()
        }
    }

    private fun calculateRms(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        val sampleCount = length / 2
        if (sampleCount == 0) return 0f

        for (i in 0 until length - 1 step 2) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            sum += sample * sample
        }
        val rms = sqrt(sum / sampleCount)
        // Normalize 0..32767 to 0..1 range with scaling
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    fun release() {
        stopRecording()
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack: ${e.message}")
        }
        audioTrack = null
        audioPlaybackQueue.clear()
    }
}
