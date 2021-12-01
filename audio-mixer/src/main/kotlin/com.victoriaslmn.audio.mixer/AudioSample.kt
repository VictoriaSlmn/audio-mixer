package com.victoriaslmn.audio.mixer

import java.nio.ShortBuffer

/**
 * Audio data and metadata for communication between decoder and encoder
 * @param bufferIndex decoder buffer index for releasing buffer after feeding to encoder
 * @param presentationTimeUs sample presentation time
 * @param data audio data in ShortBuffer format
 * @param syncsPresentationTime true if given decoder's timing is used for syncing audio and video
 */
data class AudioSample(
    val bufferIndex: Int,
    val presentationTimeUs: Long,
    val data: ShortBuffer,
    val syncsPresentationTime: Boolean = false
)
