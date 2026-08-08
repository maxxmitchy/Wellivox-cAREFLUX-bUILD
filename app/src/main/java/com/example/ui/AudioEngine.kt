package com.example.ui

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

enum class AudioTrackOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val tempoBpm: Int,
    val genre: String
) {
    UPBEAT_RETAIL("UPBEAT_RETAIL", "Upbeat Retail Promo", "Catchy, energetic rhythmic beat & synth chords", 120, "Promo Pulse"),
    SMOOTH_WELLNESS("SMOOTH_WELLNESS", "Smooth Wellness & Calm", "Relaxing ambient tone pads & soft harmony", 90, "Chill Wellness"),
    MODERN_MEDICAL("MODERN_MEDICAL", "Modern Medical Beats", "Professional corporate electronic groove", 110, "Corporate"),
    HIGH_ENERGY("HIGH_ENERGY", "High Energy Sale Blast", "Dynamic countdown rhythmic electronic beats", 128, "EDM Sale"),
    CUSTOM_FILE("CUSTOM_FILE", "Custom Music File", "User uploaded custom MP3 or WAV audio track", 0, "Custom"),
    NONE("NONE", "No Music (Silent MP4)", "Video encoded without background music", 0, "Silent")
}

object AudioEngine {
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingOption: AudioTrackOption? = null

    fun playPreview(context: Context, option: AudioTrackOption, customUri: Uri? = null, onCompletion: () -> Unit = {}) {
        stopPreview()
        if (option == AudioTrackOption.NONE) return

        try {
            val mp = MediaPlayer()
            if (option == AudioTrackOption.CUSTOM_FILE && customUri != null) {
                mp.setDataSource(context, customUri)
            } else {
                val audioFile = getOrGenerateAudioFile(context, option)
                mp.setDataSource(audioFile.absolutePath)
            }
            mp.isLooping = true
            mp.prepare()
            mp.start()
            mediaPlayer = mp
            currentPlayingOption = option
        } catch (e: Exception) {
            e.printStackTrace()
            stopPreview()
        }
    }

    fun stopPreview() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            currentPlayingOption = null
        }
    }

    fun isPlaying(option: AudioTrackOption): Boolean {
        return currentPlayingOption == option && mediaPlayer?.isPlaying == true
    }

    /**
     * Generates or retrieves a WAV audio file for the specified built-in preset option.
     * Uses PCM synthesis to create pleasant background audio tracks offline!
     */
    fun getOrGenerateAudioFile(context: Context, option: AudioTrackOption, durationSeconds: Int = 15): File {
        val file = File(context.cacheDir, "preset_audio_${option.id}.wav")
        if (file.exists() && file.length() > 1000) {
            return file
        }

        val sampleRate = 44100
        val numSamples = durationSeconds * sampleRate
        val pcmData = ByteArray(numSamples * 2) // 16-bit PCM mono

        val bpm = option.tempoBpm
        val bps = bpm / 60.0
        val secondsPerBeat = 1.0 / bps

        // Chord frequencies (Hz) for chords
        val chordFreqs = when (option) {
            AudioTrackOption.UPBEAT_RETAIL -> listOf(
                doubleArrayOf(261.63, 329.63, 392.00), // C Major
                doubleArrayOf(293.66, 349.23, 440.00), // D minor
                doubleArrayOf(329.63, 392.00, 493.88), // E minor
                doubleArrayOf(349.23, 440.00, 523.25)  // F Major
            )
            AudioTrackOption.SMOOTH_WELLNESS -> listOf(
                doubleArrayOf(220.00, 277.18, 329.63), // A Major soft
                doubleArrayOf(246.94, 293.66, 369.99), // B minor soft
                doubleArrayOf(261.63, 329.63, 392.00)  // C Major soft
            )
            AudioTrackOption.MODERN_MEDICAL -> listOf(
                doubleArrayOf(196.00, 246.94, 293.66), // G Major
                doubleArrayOf(220.00, 261.63, 329.63), // A minor
                doubleArrayOf(174.61, 220.00, 261.63)  // F Major
            )
            AudioTrackOption.HIGH_ENERGY -> listOf(
                doubleArrayOf(146.83, 174.61, 220.00), // D minor bass
                doubleArrayOf(174.61, 220.00, 261.63), // F Major
                doubleArrayOf(196.00, 246.94, 293.66)  // G Major
            )
            else -> listOf(doubleArrayOf(261.63, 329.63, 392.00))
        }

        var idx = 0
        for (i in 0 until numSamples) {
            val time = i.toDouble() / sampleRate
            val currentBeat = (time / secondsPerBeat).toInt()
            val chordIndex = (currentBeat / 2) % chordFreqs.size
            val freqs = chordFreqs[chordIndex]

            // Synth tone calculation
            var sampleVal = 0.0
            for (f in freqs) {
                sampleVal += sin(2.0 * Math.PI * f * time) * 0.25
            }

            // Add rhythmic drum/beat pulse on every beat
            val beatPhase = (time % secondsPerBeat) / secondsPerBeat
            val drumEnvelope = Math.exp(-beatPhase * 12.0)
            val snareNoise = ((Math.random() * 2.0) - 1.0) * drumEnvelope * 0.15
            val kickFrequency = 120.0 * Math.exp(-beatPhase * 20.0)
            val kickTone = sin(2.0 * Math.PI * kickFrequency * time) * drumEnvelope * 0.35

            if (option == AudioTrackOption.UPBEAT_RETAIL || option == AudioTrackOption.HIGH_ENERGY) {
                sampleVal = sampleVal * 0.6 + kickTone + snareNoise
            } else {
                sampleVal = sampleVal * 0.8 + kickTone * 0.2
            }

            // Normalize & write 16-bit PCM little-endian
            val shortVal = (sampleVal.coerceIn(-0.95, 0.95) * 32767).toInt().toShort()
            pcmData[idx++] = (shortVal.toInt() and 0x00FF).toByte()
            pcmData[idx++] = ((shortVal.toInt() shr 8) and 0x00FF).toByte()
        }

        writeWavHeader(file, sampleRate, 1, 16, numSamples)
        FileOutputStream(file, true).use { fos ->
            fos.write(pcmData)
        }

        return file
    }

    /**
     * Generates or retrieves an AAC audio file for the specified built-in preset option.
     * Uses PCM synthesis + AAC encoding to create seamless background audio tracks for MP4 export.
     */
    fun getOrGenerateAacAudioFile(context: Context, option: AudioTrackOption, durationSeconds: Int = 15): File {
        val aacFile = File(context.cacheDir, "preset_audio_${option.id}.m4a")
        if (aacFile.exists() && aacFile.length() > 1000) {
            return aacFile
        }

        val wavFile = getOrGenerateAudioFile(context, option, durationSeconds)
        try {
            convertPcmToAac(wavFile, aacFile)
            if (aacFile.exists() && aacFile.length() > 500) {
                return aacFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return wavFile
    }

    /**
     * Converts a PCM WAV file into an MP4/AAC audio file using MediaCodec.
     */
    fun convertPcmToAac(pcmFile: File, outputFile: File, sampleRate: Int = 44100, channels: Int = 1) {
        if (!pcmFile.exists()) return
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var audioTrackIdx = -1
        var muxerStarted = false

        val fis = java.io.FileInputStream(pcmFile)
        if (pcmFile.length() > 44) fis.skip(44L)

        val bufferInfo = MediaCodec.BufferInfo()
        val inputBuffer = ByteArray(4096)
        var isPcmEof = false
        var isEncoderEof = false

        while (!isEncoderEof) {
            if (!isPcmEof) {
                val inputIdx = encoder.dequeueInputBuffer(10000)
                if (inputIdx >= 0) {
                    val byteBuf = encoder.getInputBuffer(inputIdx)
                    byteBuf?.clear()
                    val readBytes = fis.read(inputBuffer)
                    if (readBytes > 0) {
                        byteBuf?.put(inputBuffer, 0, readBytes)
                        encoder.queueInputBuffer(inputIdx, 0, readBytes, System.nanoTime() / 1000, 0)
                    } else {
                        isPcmEof = true
                        encoder.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                }
            }

            val outputIdx = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                audioTrackIdx = muxer.addTrack(encoder.outputFormat)
                muxer.start()
                muxerStarted = true
            } else if (outputIdx >= 0) {
                if (muxerStarted && bufferInfo.size > 0) {
                    val outBuf = encoder.getOutputBuffer(outputIdx)
                    if (outBuf != null) {
                        (outBuf as java.nio.Buffer).position(bufferInfo.offset)
                        (outBuf as java.nio.Buffer).limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(audioTrackIdx, outBuf, bufferInfo)
                    }
                }
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isEncoderEof = true
                }
                encoder.releaseOutputBuffer(outputIdx, false)
            }
        }

        try { fis.close() } catch (e: Exception) { e.printStackTrace() }
        try { encoder.stop(); encoder.release() } catch (e: Exception) { e.printStackTrace() }
        try { if (muxerStarted) { muxer.stop(); muxer.release() } } catch (e: Exception) { e.printStackTrace() }
    }

    fun copyUriToCacheFile(context: Context, uri: Uri): File? {
        return try {
            val destFile = File(context.cacheDir, "user_custom_audio_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0) destFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun writeWavHeader(file: File, sampleRate: Int, channels: Int, bitsPerSample: Int, numSamples: Int) {
        val dataSize = numSamples * channels * (bitsPerSample / 8)
        val totalSize = 36 + dataSize
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()

        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put("RIFF".toByteArray())
            putInt(totalSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // Subchunk1Size for PCM
            putShort(1) // AudioFormat 1 = PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(bitsPerSample.toShort())
            put("data".toByteArray())
            putInt(dataSize)
        }.array()

        FileOutputStream(file).use { fos ->
            fos.write(header)
        }
    }
}
