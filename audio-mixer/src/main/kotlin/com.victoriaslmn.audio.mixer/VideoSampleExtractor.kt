package com.victoriaslmn.audio.mixer

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat.KEY_MAX_INPUT_SIZE
import com.victoriaslmn.audio.mixer.TrackType.VIDEO
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VideoSampleExtractor(
    private val mediaExtractor: MediaExtractor,
    private val trackIndex: Int,
    private val muxer: MP4Muxer
) : MediaSampleWorker {
    private val buffer: ByteBuffer
    private val bufferInfo = MediaCodec.BufferInfo()
    override var finished = false
        private set

    init {
        mediaExtractor.selectTrack(trackIndex)
        val format = mediaExtractor.getTrackFormat(trackIndex)

        muxer.setOutputFormat(VIDEO, format)
        buffer = ByteBuffer.allocate(format.getInteger(KEY_MAX_INPUT_SIZE))
            .order(ByteOrder.nativeOrder())
    }

    override fun processNextSample(): Boolean {
        val currentTrackIndex = mediaExtractor.sampleTrackIndex

        if (currentTrackIndex < 0) {
            mediaExtractor.unselectTrack(trackIndex)
            finished = true
            return true
        }

        if (currentTrackIndex != trackIndex) {
            return false
        }

        buffer.clear()
        val sampleSize = mediaExtractor.readSampleData(buffer, 0)
        val isKeyFrame = mediaExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
        val flags = if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        bufferInfo.set(0, sampleSize, mediaExtractor.sampleTime, flags)
        muxer.writeSampleData(VIDEO, buffer, bufferInfo)

        mediaExtractor.advance()
        return true
    }

    override fun close() {}
}