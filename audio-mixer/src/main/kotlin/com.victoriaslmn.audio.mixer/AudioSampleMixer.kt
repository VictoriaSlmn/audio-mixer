package com.victoriaslmn.audio.mixer

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.victoriaslmn.audio.mixer.TrackType.AUDIO
import java.nio.ByteBuffer

class AudioSampleMixer(
    private val extractors: List<AudioSampleExtractor>,
    private val muxer: MP4Muxer
): MediaSampleWorker {
    private val encoder: MediaCodec
    private val bufferInfo = MediaCodec.BufferInfo()
    private var actualOutputFormat: MediaFormat? = null
    private val syncExtractor = extractors.single { it.syncsPresentationTime }
    override var finished = false
        private set

    init {
        val outputFormat = createAudioOutputFormat(syncExtractor.inputFormat)
        encoder = MediaCodec.createEncoderByType(outputFormat.getString(MediaFormat.KEY_MIME)!!)
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
    }

    override fun processNextSample(): Boolean {
        var busy = false

        while (feedMuxer() != OperationState.NONE) busy = true

        busy = busy or extractors.map { it.decodeNextSample() }.any { it }

        while (feedEncoder()) busy = true

        busy = busy or extractors.map { it.extractNextSample() }.any { it }

        return busy
    }

    private fun feedMuxer(): OperationState {
        if (finished) return OperationState.NONE
        val result = encoder.dequeueOutputBuffer(bufferInfo, 0)
        @Suppress("DEPRECATION")
        when (result) {
            MediaCodec.INFO_TRY_AGAIN_LATER -> return OperationState.NONE
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                if (actualOutputFormat != null) {
                    throw RuntimeException("Audio output format changed twice.")
                }
                actualOutputFormat = encoder.outputFormat
                muxer.setOutputFormat(AUDIO, actualOutputFormat!!)
                return OperationState.SHOULD_RETRY_IMMEDIATELY
            }
            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> return OperationState.SHOULD_RETRY_IMMEDIATELY
        }
        if (actualOutputFormat == null) {
            throw RuntimeException("Could not determine actual output format.")
        }
        if (bufferInfo.isEos) {
            finished = true
            bufferInfo.set(0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) { // SPS or PPS, which should be passed by MediaFormat.
            encoder.releaseOutputBuffer(result, false)
            return OperationState.SHOULD_RETRY_IMMEDIATELY
        }

        muxer.writeSampleData(AUDIO, encoder.getOutputBuffer(result)!!, bufferInfo)

        encoder.releaseOutputBuffer(result, false)
        return OperationState.CONSUMED
    }

    private fun feedEncoder(): Boolean {
        val getAudioSampleResults = extractors.map { it.getNextSample() }

        val eos = syncExtractor.getNextSample() == GetAudioSampleResult.Eos
        if (!eos && getAudioSampleResults.any { it == GetAudioSampleResult.Pending }) return false

        val encoderInBuffIndex = encoder.dequeueInputBuffer(0)
        if (encoderInBuffIndex < 0) {
            return false
        }
        val outBuffer = encoder.getInputBuffer(encoderInBuffIndex)!!
        if (eos) {
            encoder.queueInputBuffer(
                encoderInBuffIndex,
                0,
                0,
                0,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
            return false
        }

        val samples =
            getAudioSampleResults.mapNotNull { (it as? GetAudioSampleResult.Ready)?.sample }
        val presentationTimeUs = mix(samples, outBuffer)

        encoder.queueInputBuffer(
            encoderInBuffIndex,
            0,
            outBuffer.position(),
            presentationTimeUs,
            0
        )
        extractors.forEach { it.releaseSample() }

        return true
    }

    private fun mix(inputs: List<AudioSample>, outBuff: ByteBuffer): Long {
        val minBuff = inputs.minByOrNull { it.data.remaining() }?.data
            ?: throw IllegalStateException("Have to be at least one not empty sample")

        while (minBuff.hasRemaining() && outBuff.hasRemaining()) {
            var mixed = inputs.map {
                val data = it.data
                if (data.hasRemaining()) (data.get() / SHORT_MAX_VALUE) else 0f
            }.sum()

            mixed = mixed.clamp(-1f, 1f)

            val processedSample = (mixed * SHORT_MAX_VALUE).toInt().toShort()

            outBuff.putShort(processedSample)
        }
        return inputs.single { it.syncsPresentationTime }.presentationTimeUs
    }

    override fun close() {
        extractors.forEach { it.release() }
        encoder.stop()
        encoder.release()
    }

    private fun createAudioOutputFormat(inputFormat: MediaFormat): MediaFormat {
        if (MediaFormat.MIMETYPE_AUDIO_AAC == inputFormat.getString(MediaFormat.KEY_MIME)) {
            return inputFormat
        }
        val outputFormat = MediaFormat()
        outputFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC)
        outputFormat.setInteger(
            MediaFormat.KEY_AAC_PROFILE,
            MediaCodecInfo.CodecProfileLevel.AACObjectELD
        )
        outputFormat.setInteger(
            MediaFormat.KEY_SAMPLE_RATE,
            inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        )
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_BIT_RATE)
        outputFormat.setInteger(
            MediaFormat.KEY_CHANNEL_COUNT,
            inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        )
        return outputFormat
    }

    companion object {
        private const val SHORT_MAX_VALUE = Short.MAX_VALUE.toFloat()
        private const val OUTPUT_BIT_RATE = 128_000
    }
}
