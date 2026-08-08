package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.view.Surface
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

enum class VideoAspectRatio(val width: Int, val height: Int, val label: String) {
    SQUARE_1_1(1080, 1080, "Square Post (1:1)"),
    STORY_9_16(1080, 1920, "Reels / Status (9:16)"),
    LANDSCAPE_16_9(1280, 720, "Landscape (16:9)")
}

object Mp4VideoEncoder {

    suspend fun encodeBitmapsToMp4(
        context: Context,
        bitmaps: List<Bitmap>,
        aspectRatio: VideoAspectRatio = VideoAspectRatio.SQUARE_1_1,
        durationPerSlideSeconds: Int = 4,
        musicOption: AudioTrackOption = AudioTrackOption.UPBEAT_RETAIL,
        customAudioUri: Uri? = null,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        if (bitmaps.isEmpty()) return@withContext null

        val outputFile = File(context.cacheDir, "promo_video_${System.currentTimeMillis()}.mp4")
        if (outputFile.exists()) outputFile.delete()

        val width = aspectRatio.width
        val height = aspectRatio.height
        val fps = 30
        val frameDurationUs = 1000000L / fps
        val framesPerSlide = fps * durationPerSlideSeconds
        val totalFrames = framesPerSlide * bitmaps.size

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: Surface? = null

        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val bgPaint = Paint().apply { color = Color.parseColor("#0B0F19") }

            var currentFrame = 0

            for (slideIndex in bitmaps.indices) {
                val currentBitmap = bitmaps[slideIndex]
                val nextBitmap = bitmaps.getOrNull(slideIndex + 1) ?: bitmaps.first()

                for (f in 0 until framesPerSlide) {
                    val ptsUs = currentFrame * frameDurationUs
                    val slideProgress = f.toFloat() / framesPerSlide.toFloat()

                    val canvas = inputSurface.lockCanvas(null)
                    try {
                        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                        // Smooth transition & subtle Ken Burns scale/pan effect
                        val scale = 1.0f + (slideProgress * 0.05f) // Subtle 5% zoom
                        val dx = (slideProgress * 15f)

                        // If transitioning out in last 10 frames, crossfade
                        val isTransitioning = f >= (framesPerSlide - 10)
                        if (isTransitioning && bitmaps.size > 1) {
                            val alphaTransition = (f - (framesPerSlide - 10)) / 10f
                            
                            // Draw current slide
                            paint.alpha = ((1f - alphaTransition) * 255).toInt().coerceIn(0, 255)
                            drawScaledBitmap(canvas, currentBitmap, width, height, scale, dx, paint)

                            // Draw next slide overlay
                            paint.alpha = (alphaTransition * 255).toInt().coerceIn(0, 255)
                            drawScaledBitmap(canvas, nextBitmap, width, height, 1.0f, 0f, paint)
                            paint.alpha = 255
                        } else {
                            paint.alpha = 255
                            drawScaledBitmap(canvas, currentBitmap, width, height, scale, dx, paint)
                        }
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }

                    // Drain encoder output
                    var encoderDone = false
                    while (!encoderDone) {
                        val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                        if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            encoderDone = true
                        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            if (muxerStarted) {
                                throw RuntimeException("Format changed twice")
                            }
                            videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        } else if (outputBufferIndex >= 0) {
                            val encodedData = encoder.getOutputBuffer(outputBufferIndex)
                            if (encodedData != null) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    bufferInfo.size = 0
                                }
                                if (bufferInfo.size != 0) {
                                    if (!muxerStarted) {
                                        throw RuntimeException("Muxer not started")
                                    }
                                    bufferInfo.presentationTimeUs = ptsUs
                                    encodedData.position(bufferInfo.offset)
                                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                                }
                                encoder.releaseOutputBuffer(outputBufferIndex, false)
                            }
                        }
                    }

                    currentFrame++
                    val overallProgress = currentFrame.toFloat() / totalFrames.toFloat()
                    onProgress(overallProgress.coerceIn(0.0f, 0.95f))
                }
            }

            // Signal end of stream to encoder
            encoder.signalEndOfInputStream()

            var endOfStream = false
            while (!endOfStream) {
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // Waiting
                } else if (outputBufferIndex >= 0) {
                    val encodedData = encoder.getOutputBuffer(outputBufferIndex)
                    if (encodedData != null) {
                        if (bufferInfo.size != 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                    }
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        endOfStream = true
                    }
                }
            }

            onProgress(0.95f)

            // Release video encoder and muxer before audio pass
            try { encoder.stop(); encoder.release(); encoder = null } catch (e: Exception) { e.printStackTrace() }
            try { muxer.stop(); muxer.release(); muxer = null } catch (e: Exception) { e.printStackTrace() }

            // Handle audio track muxing if requested
            if (musicOption != AudioTrackOption.NONE) {
                var audioSourceFile: File? = null
                if (musicOption == AudioTrackOption.CUSTOM_FILE && customAudioUri != null) {
                    audioSourceFile = AudioEngine.copyUriToCacheFile(context, customAudioUri)
                } else if (musicOption != AudioTrackOption.CUSTOM_FILE) {
                    val durationSecs = (totalFrames / fps).coerceAtLeast(5)
                    audioSourceFile = AudioEngine.getOrGenerateAacAudioFile(context, musicOption, durationSecs)
                }

                if (audioSourceFile != null && audioSourceFile.exists()) {
                    val finalWithAudioFile = File(context.cacheDir, "promo_video_audio_${System.currentTimeMillis()}.mp4")
                    val muxSuccess = muxVideoAndAudio(outputFile, audioSourceFile, finalWithAudioFile)
                    if (muxSuccess && finalWithAudioFile.exists() && finalWithAudioFile.length() > 0) {
                        try { outputFile.delete() } catch (e: Exception) {}
                        onProgress(1.0f)
                        return@withContext finalWithAudioFile
                    }
                }
            }

            onProgress(1.0f)
            return@withContext outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            try {
                encoder?.stop()
                encoder?.release()
            } catch (e: Exception) { e.printStackTrace() }

            try {
                if (muxer != null) {
                    muxer.stop()
                    muxer.release()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun muxVideoAndAudio(
        videoFile: File,
        audioFile: File,
        finalOutputFile: File
    ): Boolean {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            var videoTrackIdx = -1
            var audioTrackIdx = -1

            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIdx = i
                    break
                }
            }

            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    break
                }
            }

            if (videoTrackIdx < 0) return false

            muxer = MediaMuxer(finalOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            videoExtractor.selectTrack(videoTrackIdx)
            val videoFormat = videoExtractor.getTrackFormat(videoTrackIdx)
            val muxerVideoTrack = muxer.addTrack(videoFormat)

            var muxerAudioTrack = -1
            if (audioTrackIdx >= 0) {
                audioExtractor.selectTrack(audioTrackIdx)
                val audioFormat = audioExtractor.getTrackFormat(audioTrackIdx)
                muxerAudioTrack = muxer.addTrack(audioFormat)
            }

            muxer.start()

            val bufferSize = 1024 * 1024
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // Copy video frames
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // Copy audio frames
            if (audioTrackIdx >= 0 && muxerAudioTrack >= 0) {
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break
                    bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    bufferInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                    audioExtractor.advance()
                }
            }

            muxer.stop()
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            try { muxer?.stop(); muxer?.release() } catch (ex: Exception) {}
            try { videoExtractor?.release() } catch (ex: Exception) {}
            try { audioExtractor?.release() } catch (ex: Exception) {}
            return false
        }
    }

    private fun drawScaledBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        scale: Float,
        dx: Float,
        paint: Paint
    ) {
        val srcWidth = bitmap.width
        val srcHeight = bitmap.height

        val srcRect = Rect(0, 0, srcWidth, srcHeight)

        // Calculate aspect fit inside target dimensions
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()

        var destWidth = targetWidth.toFloat()
        var destHeight = targetHeight.toFloat()

        if (srcRatio > targetRatio) {
            destHeight = targetWidth.toFloat() / srcRatio
        } else {
            destWidth = targetHeight.toFloat() * srcRatio
        }

        val left = ((targetWidth - destWidth) / 2f) + dx
        val top = (targetHeight - destHeight) / 2f
        val right = left + destWidth
        val bottom = top + destHeight

        canvas.save()
        canvas.scale(scale, scale, targetWidth / 2f, targetHeight / 2f)
        canvas.drawBitmap(bitmap, srcRect, RectF(left, top, right, bottom), paint)
        canvas.restore()
    }

    fun getFileProviderUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
