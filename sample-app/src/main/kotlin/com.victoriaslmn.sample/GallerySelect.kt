package com.victoriaslmn.sample

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.victoriaslmn.sample.GallerySelectMode.*

@ExperimentalPermissionsApi
@Composable
fun GallerySelect(
    mode: GallerySelectMode,
    onUri: (Uri) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            onUri(uri ?: Uri.EMPTY)
        }
    )

    @Composable
    fun LaunchGallery() {
        SideEffect {
            val fileType = when (mode) {
                MP4 -> "video/mp4"
                MP3 -> "audio/mpeg"
                NONE -> "*/*"
            }
            launcher.launch(fileType)
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Permission(
            permission = Manifest.permission.ACCESS_MEDIA_LOCATION,
            rationale = stringResource(R.string.permissions_label),
            permissionNotAvailableContent = {
                Column {
                    Text(stringResource(R.string.no_files_label, mode))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Button(
                            modifier = Modifier.padding(4.dp),
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                )
                            }
                        ) {
                            Text(stringResource(R.string.open_settings_button))
                        }
                        // If they don't want to grant permissions, this button will result in going back
                        Button(
                            modifier = Modifier.padding(4.dp),
                            onClick = {
                                onUri(Uri.EMPTY)
                            }
                        ) {
                            Text(stringResource(R.string.back_button))
                        }
                    }
                }
            },
        ) {
            LaunchGallery()
        }
    } else {
        LaunchGallery()
    }
}
