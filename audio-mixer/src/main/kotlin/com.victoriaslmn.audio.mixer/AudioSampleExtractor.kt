package com.victoriaslmn.audio.mixer

import android.media.MediaCodec
import android.media.MediaCodec.*
import android.media.MediaExtractor
import android.media.MediaFormat
import com.victoriaslmn.audio.mixer.OperationState.*
import java.util.*

class AudioSampleExtractor(
    private val mediaExtractor: MediaExtractor,
    private val trackIndex: Int,
    val syncsPresentationTime: Boolean
) {

    private var isExtractorEos = false
    private var isDecoderEos = false

    private var decoder: MediaCodec
    private val bufferInfo = BufferInfo()
    private val buffers: Queue<AudioSample> = ArrayDeque()
    val inputFormat: MediaFormat = mediaExtractor.getTrackFormat(trackIndex)

    init {
        mediaExtractor.selectTrack(trackIndex)
        decoder = createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()
    }

    fun extractNextSample(): Boolean {
        var busy = false
        while (extract() != NONE) busy = true
        return busy
    }

    fun decodeNextSample(): Boolean {
        var busy = false
        var state: OperationState
        do {
            state = decode()
            if (state != NONE) busy = true
            // NOTE: not repeating to keep from deadlock when encoder is full.
        } while (state == SHOULD_RETRY_IMMEDIATELY)
        return busy
    }

    private fun extract(): OperationState {
        if (isExtractorEos) return NONE
        val trackIndex = mediaExtractor.sampleTrackIndex
        if (trackIndex >= 0 && trackIndex != this.trackIndex) {
            return NONE
        }
        val result = decoder.dequeueInputBuffer(0)
        if (result < 0) return NONE
        if (trackIndex < 0) {
            isExtractorEos = true
            decoder.queueInputBuffer(
                result, 0, 0, 0,
                BUFFER_FLAG_END_OF_STREAM
            )
            return NONE
        }
        val sampleSize = mediaExtractor.readSampleData(decoder.getInputBuffer(result)!!, 0)
        val isKeyFrame = mediaExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
        decoder.queueInputBuffer(
            result,
            0,
            sampleSize,
            mediaExtractor.sampleTime,
            if (isKeyFrame) BUFFER_FLAG_KEY_FRAME else 0
        )
        mediaExtractor.advance()
        return CONSUMED
    }

    private fun decode(): OperationState {
        if (isDecoderEos) return NONE
        val result = decoder.dequeueOutputBuffer(bufferInfo, 0)
        @Suppress("DEPRECATION")
        when (result) {
            INFO_TRY_AGAIN_LATER -> return NONE
            INFO_OUTPUT_FORMAT_CHANGED -> return SHOULD_RETRY_IMMEDIATELY
            INFO_OUTPUT_BUFFERS_CHANGED -> return SHOULD_RETRY_IMMEDIATELY
        }
        if (bufferInfo.isEos) {
            isDecoderEos = true
            mediaExtractor.unselectTrack(trackIndex)
        } else if (bufferInfo.size > 0) {
            val data = decoder.getOutputBuffer(result) ?: return NONE
            val buffer = AudioSample(
                bufferIndex = result,
                presentationTimeUs = bufferInfo.presentationTimeUs,
                data = data.asShortBuffer(),
                syncsPresentationTime = syncsPresentationTime
            )
            buffers.add(buffer)
        }
        return CONSUMED
    }

    fun release() {
        decoder.stop()
        decoder.release()
    }

    fun getNextSample(): GetAudioSampleResult {
        if (isDecoderEos && buffers.isEmpty()) return GetAudioSampleResult.Eos

        val audioBuffer = buffers.peek() ?: return GetAudioSampleResult.Pending

        return GetAudioSampleResult.Ready(audioBuffer)
    }

    fun releaseSample() {
        val buffer = buffers.peek() ?: return
        if (buffer.data.hasRemaining()) return

        decoder.releaseOutputBuffer(buffer.bufferIndex, false)
        buffers.remove()
    }
}


