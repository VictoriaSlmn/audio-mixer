package com.victoriaslmn.audio.mixer

import android.content.ContentResolver
import android.content.ContentValues
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.victoriaslmn.audio.mixer.TrackType.AUDIO
import com.victoriaslmn.audio.mixer.TrackType.VIDEO
import java.util.*
import java.util.concurrent.TimeUnit

class AudioMixer(
    private val contentResolver: ContentResolver
) {

    fun mix(mp4Uri: Uri, mp3Uri: Uri): Uri {
        val outputMp4Uri = createVideoUri()
        val fileDescriptor = contentResolver.openFileDescriptor(outputMp4Uri, "w")!!.fileDescriptor
        val mp4Extractor = createMediaExtractor(mp4Uri)
        val videoTrackIndex = getTrackIndex(mp4Extractor, VIDEO)
        val muxer = MP4Muxer(fileDescriptor, getVideoRotation(mp4Extractor, videoTrackIndex))
        val mp3Extractor = createMediaExtractor(mp3Uri)

        val sampleWorkers = listOf(
            createAudioSampleMuxer(mp4Extractor, mp3Extractor, muxer),
            VideoSampleExtractor(
                mediaExtractor = mp4Extractor,
                trackIndex = videoTrackIndex,
                muxer = muxer
            )
        )

        while (!sampleWorkers.all { it.finished }) {
            val processed = sampleWorkers.map { it.processNextSample() }.any { it }
            if (!processed) {
                Thread.sleep(SLEEP_TO_WAIT_TRACK_TRANSCODERS_MS)
            }
        }

        sampleWorkers.forEach { it.close() }
        mp4Extractor.release()
        mp3Extractor.release()
        muxer.release()

        markVideoAsCompletedInGallery(outputMp4Uri)

        return outputMp4Uri
    }

    private fun getVideoRotation(mp4Extractor: MediaExtractor, videoTrackIndex: Int): Int {
        var rotation = 0
        val format = mp4Extractor.getTrackFormat(videoTrackIndex)
        if (format.containsKey(MediaFormat.KEY_ROTATION)) {
            rotation = format.getInteger(MediaFormat.KEY_ROTATION)
        }
        return rotation
    }

    private fun createAudioSampleMuxer(
        mp4Extractor: MediaExtractor,
        mp3Extractor: MediaExtractor,
        muxer: MP4Muxer
    ): AudioSampleMixer {
        val mp4AudioSampleExtractor = AudioSampleExtractor(
            mediaExtractor = mp4Extractor,
            trackIndex = getTrackIndex(mp4Extractor, AUDIO),
            syncsPresentationTime = true
        )
        val mp3AudioSampleExtractor = AudioSampleExtractor(
            mediaExtractor = mp3Extractor,
            trackIndex = getTrackIndex(mp3Extractor, AUDIO),
            syncsPresentationTime = false
        )
        return AudioSampleMixer(
            extractors = listOf(mp4AudioSampleExtractor, mp3AudioSampleExtractor),
            muxer = muxer
        )
    }

    private fun createMediaExtractor(audio: Uri): MediaExtractor {
        val mediaExtractor = MediaExtractor()
        checkNotNull(contentResolver.openFileDescriptor(audio, "r")) {
            "unable to acquire file descriptor for $audio"
        }.use {
            mediaExtractor.setDataSource(it.fileDescriptor)
        }
        return mediaExtractor
    }

    private fun getTrackIndex(mediaExtractor: MediaExtractor, trackType: TrackType): Int {
        var trackIndex: Int? = null
        for (i in 0 until mediaExtractor.trackCount) {
            val trackFormat = mediaExtractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)!!
            if (mime.startsWith(trackType.prefix)) {
                trackIndex = i
                break
            }
        }
        checkNotNull(trackIndex) { "File does not have ${trackType.prefix} track" }
        return trackIndex
    }

    private fun createVideoUri(): Uri {
        val date = Date()
        val fileName = "${date.time}.mp4"
        val values = ContentValues()
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
        values.put(MediaStore.Video.Media.TITLE, fileName)
        values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        values.put(MediaStore.Video.Media.DATE_ADDED, TimeUnit.MILLISECONDS.toSeconds(date.time))
        values.put(MediaStore.Video.Media.DATE_TAKEN, date.time)
        values.put(MediaStore.Video.Media.IS_PENDING, 1)
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return contentResolver.insert(collection, values)!!
    }

    private fun markVideoAsCompletedInGallery(outputMp4Uri: Uri) {
        val values = ContentValues()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        contentResolver.update(outputMp4Uri, values, null, null)
    }

    companion object {
        private const val SLEEP_TO_WAIT_TRACK_TRANSCODERS_MS: Long = 10
    }
}
