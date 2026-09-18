package com.example.utils

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Compresor nativo de audio AAC usando MediaCodec.
 *
 * Pipeline: MediaExtractor → MediaCodec decoder (AAC→PCM) → MediaCodec encoder (PCM→AAC) → MediaMuxer (MP4)
 *
 * Se usa para reducir el tamaño de grabaciones largas antes de subirlas a la API, ya que
 * algunas (Groq) tienen un límite de 25 MB por archivo.
 *
 * Punto dulce de calidad para voz: 64-96 kbps mono.
 * Por debajo de 48 kbps, la voz empieza a sonar metálica y pierde inteligibilidad.
 */
object AudioCompressor {

    private const val TAG = "AudioCompressor"

    /** Bitrate mínimo aceptable para voz. Por debajo de esto, preferimos avisar al usuario. */
    const val MIN_ACCEPTABLE_BITRATE = 48_000

    /** Bitrate ideal para voz: buena calidad, tamaño contenido. */
    const val IDEAL_VOICE_BITRATE = 96_000

    /** Bitrate máximo: no tiene sentido subir más para voz. */
    const val MAX_BITRATE = 128_000

    /** Tamaño objetivo por defecto (un poco por debajo de 25 MB para dar margen al protocolo HTTP). */
    const val DEFAULT_TARGET_SIZE_MB = 24.5

    sealed class Result {
        data class Success(
            val outputFile: File,
            val originalSize: Long,
            val newSize: Long
        ) : Result()

        data class Failure(val reason: String) : Result()

        /** El archivo es tan largo que comprimirlo por debajo de [MIN_ACCEPTABLE_BITRATE] lo arruinaría. */
        data class QualityTooLow(
            val requiredBitrate: Int,
            val minimumBitrate: Int
        ) : Result()
    }

    /**
     * Calcula el bitrate necesario para que el archivo entre en [targetSizeMB].
     * Retorna null si el bitrate requerido sería menor al mínimo aceptable.
     */
    fun calculateTargetBitrate(
        durationMs: Long,
        targetSizeMB: Double = DEFAULT_TARGET_SIZE_MB
    ): Int? {
        if (durationMs <= 0) return IDEAL_VOICE_BITRATE

        val durationSec = durationMs / 1000.0
        val targetBytes = targetSizeMB * 1024 * 1024
        val bitsPerSecond = (targetBytes * 8) / durationSec
        val bitrate = bitsPerSecond.toInt()

        return if (bitrate < MIN_ACCEPTABLE_BITRATE) null
        else bitrate.coerceAtMost(MAX_BITRATE)
    }

    /**
     * Comprime [inputFile] a AAC con el bitrate [targetBitrate].
     * Escribe el resultado en [outputFile].
     */
    suspend fun compress(
        inputFile: File,
        outputFile: File,
        targetBitrate: Int
    ): Result = withContext(Dispatchers.IO) {
        if (!inputFile.exists()) {
            return@withContext Result.Failure("Input file does not exist")
        }

        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        try {
            extractor = MediaExtractor().apply {
                setDataSource(inputFile.absolutePath)
            }

            // Buscar la pista de audio
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || inputFormat == null) {
                return@withContext Result.Failure("No audio track found in input")
            }

            extractor.selectTrack(audioTrackIndex)

            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME)!!

            // Decoder: AAC → PCM
            decoder = MediaCodec.createDecoderByType(inputMime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            // Encoder: PCM → AAC al bitrate objetivo
            val outputFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            ).apply {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            // FIX: antes decoder y encoder compartían un mismo BufferInfo y, sobre todo,
            // el bucle que alimentaba PCM al encoder giraba en vacío sin drenar la salida
            // del encoder. En audios largos eso se traba para siempre (de ahí que la
            // compresión "tardara" y nunca devolviera nada). Ahora hay un BufferInfo por
            // etapa y el drenaje del encoder se ejecuta también mientras se espera un
            // buffer de entrada libre.
            val decoderInfo = MediaCodec.BufferInfo()
            val encoderInfo = MediaCodec.BufferInfo()
            var muxerStarted = false
            var outputTrackIndex = -1
            var inputDone = false
            var decoderDone = false
            var encoderDone = false

            /** Drena UNA salida del encoder hacia el muxer. Devuelve true si consumió algo. */
            fun drainEncoderOnce(timeoutUs: Long): Boolean {
                val encIndex = encoder.dequeueOutputBuffer(encoderInfo, timeoutUs)
                when {
                    encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            outputTrackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        return true
                    }
                    encIndex >= 0 -> {
                        val encodedData = encoder.getOutputBuffer(encIndex)
                        if ((encoderInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            encoderInfo.size = 0
                        }
                        if (encodedData != null && encoderInfo.size > 0 && muxerStarted) {
                            encodedData.position(encoderInfo.offset)
                            encodedData.limit(encoderInfo.offset + encoderInfo.size)
                            muxer.writeSampleData(outputTrackIndex, encodedData, encoderInfo)
                        }
                        encoder.releaseOutputBuffer(encIndex, false)
                        if ((encoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                        }
                        return true
                    }
                }
                return false
            }

            val deadline = System.currentTimeMillis() + 10 * 60 * 1000L // tope de seguridad

            while (!encoderDone) {
                if (System.currentTimeMillis() > deadline) {
                    return@withContext Result.Failure("Compression timed out")
                }

                // 1. Extractor -> decoder
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // 2. Decoder -> encoder
                if (!decoderDone) {
                    val decIndex = decoder.dequeueOutputBuffer(decoderInfo, 10_000)
                    if (decIndex >= 0) {
                        val pcmBuffer = decoder.getOutputBuffer(decIndex)
                        val isEos = (decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                        if (pcmBuffer != null && decoderInfo.size > 0) {
                            var pcmFed = false
                            while (!pcmFed) {
                                if (System.currentTimeMillis() > deadline) {
                                    return@withContext Result.Failure("Compression timed out")
                                }
                                val encInIndex = encoder.dequeueInputBuffer(10_000)
                                if (encInIndex >= 0) {
                                    val encBuffer = encoder.getInputBuffer(encInIndex)!!
                                    encBuffer.clear()
                                    pcmBuffer.position(decoderInfo.offset)
                                    pcmBuffer.limit(decoderInfo.offset + decoderInfo.size)
                                    encBuffer.put(pcmBuffer)
                                    encoder.queueInputBuffer(
                                        encInIndex, 0, decoderInfo.size,
                                        decoderInfo.presentationTimeUs,
                                        if (isEos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                                    )
                                    pcmFed = true
                                } else {
                                    // Clave: si no hay input libre es porque la salida
                                    // está llena. Drenarla desatasca el pipeline.
                                    drainEncoderOnce(0)
                                }
                            }
                        } else if (isEos) {
                            // EOS sin datos: señalizar fin al encoder igualmente.
                            var signalled = false
                            while (!signalled) {
                                val encInIndex = encoder.dequeueInputBuffer(10_000)
                                if (encInIndex >= 0) {
                                    encoder.queueInputBuffer(
                                        encInIndex, 0, 0,
                                        decoderInfo.presentationTimeUs,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    signalled = true
                                } else {
                                    drainEncoderOnce(0)
                                }
                            }
                        }

                        decoder.releaseOutputBuffer(decIndex, false)
                        if (isEos) decoderDone = true
                    }
                }

                // 3. Encoder -> muxer
                drainEncoderOnce(10_000)
            }

            Result.Success(
                outputFile = outputFile,
                originalSize = inputFile.length(),
                newSize = outputFile.length()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed", e)
            if (outputFile.exists()) outputFile.delete()
            Result.Failure(e.message ?: "Unknown error during compression")
        } finally {
            try { extractor?.release() } catch (e: Exception) { Log.w(TAG, "extractor release", e) }
            try { decoder?.stop() } catch (e: Exception) { Log.w(TAG, "decoder stop", e) }
            try { decoder?.release() } catch (e: Exception) { Log.w(TAG, "decoder release", e) }
            try { encoder?.stop() } catch (e: Exception) { Log.w(TAG, "encoder stop", e) }
            try { encoder?.release() } catch (e: Exception) { Log.w(TAG, "encoder release", e) }
            try { if (muxerStartedSafe(muxer)) muxer?.stop() } catch (e: Exception) { Log.w(TAG, "muxer stop", e) }
            try { muxer?.release() } catch (e: Exception) { Log.w(TAG, "muxer release", e) }
        }
    }

    private fun muxerStartedSafe(muxer: MediaMuxer?): Boolean {
        // MediaMuxer.stop() tira si nunca se llamó start(). Chequeo indirecto.
        return try {
            muxer?.let { true } ?: false
        } catch (e: Exception) {
            false
        }
    }
}