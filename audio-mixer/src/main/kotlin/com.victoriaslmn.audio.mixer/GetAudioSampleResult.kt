package com.victoriaslmn.audio.mixer

sealed class GetAudioSampleResult {
    object Pending : GetAudioSampleResult()
    object Eos : GetAudioSampleResult()
    data class Ready(val sample: AudioSample) : GetAudioSampleResult()
}
