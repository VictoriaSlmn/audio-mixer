package com.victoriaslmn.sample

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.victoriaslmn.audio.mixer.AudioMixer
import com.victoriaslmn.sample.GallerySelectMode.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ExperimentalPermissionsApi
class MainActivity : ComponentActivity() {

    private lateinit var audioMixer: AudioMixer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioMixer = AudioMixer(contentResolver)
        setContent {
            Content()
        }
    }

    @Composable
    fun Content() {
        var mixing by remember { mutableStateOf(false) }
        var outputVideoUri by remember { mutableStateOf(Uri.EMPTY) }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(32.dp)
            ) {
                when {
                    mixing -> ProgressIndicator()
                    outputVideoUri == Uri.EMPTY -> SelectionMenu(
                        onMixingStarted = { mixing = true },
                        onMixingFinished = { outputUri ->
                            mixing = false
                            outputVideoUri = outputUri
                        }
                    )
                    else -> MixingResult(outputVideoUri) { outputVideoUri = Uri.EMPTY }
                }
            }
        }
    }

    @Composable
    private fun MixingResult(outputVideoUri: Uri, onStartNewMixing: () -> Unit) {
        Text(
            stringResource(R.string.output_video_label) + outputVideoUri.path,
            modifier = Modifier.padding(16.dp)
        )

        CreateButton(stringResource(R.string.repeat_mix_button)) {
            onStartNewMixing()
        }
    }

    @Composable
    private fun ProgressIndicator() =
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

    @Composable
    private fun SelectionMenu(
        onMixingStarted: () -> Unit,
        onMixingFinished: (Uri) -> Unit,
    ) {
        var videoUri by remember { mutableStateOf(Uri.EMPTY) }
        var audioUri by remember { mutableStateOf(Uri.EMPTY) }
        var gallerySelectMode by remember { mutableStateOf(NONE) }

        when (gallerySelectMode) {
            MP4 -> GallerySelect(
                mode = gallerySelectMode,
                onUri = { uri ->
                    gallerySelectMode = NONE
                    videoUri = uri
                }
            )
            MP3 -> GallerySelect(
                mode = gallerySelectMode,
                onUri = { uri ->
                    gallerySelectMode = NONE
                    audioUri = uri
                }
            )
            NONE -> {
                if (videoUri == Uri.EMPTY) {
                    CreateButton(stringResource(R.string.select_video_button)) {
                        gallerySelectMode = MP4
                    }
                } else {
                    Text(
                        stringResource(R.string.video_label) + videoUri.path,
                        modifier = selectionMenuPadding
                    )
                }

                if (audioUri == Uri.EMPTY) {
                    CreateButton(stringResource(R.string.select_audio_button)) {
                        gallerySelectMode = MP3
                    }
                } else {
                    Text(
                        stringResource(R.string.audio_label) + audioUri.path,
                        modifier = selectionMenuPadding
                    )
                }

                if (videoUri != Uri.EMPTY && audioUri != Uri.EMPTY) {
                    CreateButton(stringResource(R.string.mix_button)) {
                        onMixingStarted()
                        CoroutineScope(Dispatchers.Main).launch {
                            val output =
                                withContext(Dispatchers.Default) {
                                    audioMixer.mix(videoUri, audioUri)
                                }
                            onMixingFinished(output)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CreateButton(name: String, onClick: () -> Unit) =
        Button(
            modifier = selectionMenuPadding,
            onClick = {
                onClick()
            }
        ) {
            Text(name)
        }

    private val selectionMenuPadding = Modifier.padding(4.dp)
}
