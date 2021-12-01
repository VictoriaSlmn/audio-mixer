package com.victoriaslmn.audio.mixer

import java.io.Closeable

interface MediaSampleWorker: Closeable {
    val finished: Boolean
    fun processNextSample(): Boolean
}