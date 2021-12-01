package com.victoriaslmn.audio.mixer

import android.media.MediaCodec

val MediaCodec.BufferInfo.isEos: Boolean
    get() = flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

fun Float.clamp(min: Float, max: Float): Float = maxOf(min, minOf(this, max))