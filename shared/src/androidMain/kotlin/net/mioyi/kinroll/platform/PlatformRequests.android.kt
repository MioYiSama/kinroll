package net.mioyi.kinroll.platform

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import net.mioyi.kinroll.android.AndroidPhotoLibrary

@Composable
actual fun rememberPermissionRequester(onResult: (PermissionState) -> Unit): () -> Unit {
    val context = LocalContext.current
    val library = remember(context) { AndroidPhotoLibrary(context) }
    val currentResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        currentResult(library.permissionState())
    }
    return {
        val permissions = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            )
        }
        launcher.launch(permissions)
    }
}

/**
 * Requests system trash confirmation in batches of at most 2,000 photos.
 * Reports only confirmed IDs, including earlier batches when a later batch is cancelled.
 * Additional invocations while a confirmation is pending are ignored.
 */
@Composable
actual fun rememberTrashRequester(onResult: (trashedIds: List<String>) -> Unit): (List<PhotoRef>) -> Unit {
    val context = LocalContext.current
    val currentResult by rememberUpdatedState(onResult)
    // Flat id/uri pairs are Bundle-saveable, including while the system confirmation UI is open.
    var pending by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var confirmed by rememberSaveable { mutableIntStateOf(0) }
    var batchEnd by rememberSaveable { mutableIntStateOf(0) }
    lateinit var launcher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>

    fun finish() {
        val ids = List(confirmed) { pending[it * 2] }
        pending = arrayListOf()
        confirmed = 0
        batchEnd = 0
        currentResult(ids)
    }

    fun launchBatch() {
        batchEnd = minOf(confirmed + 2000, pending.size / 2)
        val uris = (confirmed until batchEnd).map { Uri.parse(pending[it * 2 + 1]) }
        try {
            val request = MediaStore.createTrashRequest(context.contentResolver, uris, true)
            launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } catch (error: Exception) {
            println("kinroll-trash ${error.message}")
            finish()
        }
    }

    launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (pending.isNotEmpty()) {
            if (result.resultCode == Activity.RESULT_OK) {
                confirmed = batchEnd
                if (confirmed < pending.size / 2) launchBatch() else finish()
            } else {
                finish()
            }
        }
    }

    return { photos ->
        // A second request cannot replace a batch awaiting a system result.
        if (pending.isEmpty()) {
            if (photos.isEmpty()) {
                currentResult(emptyList())
            } else {
                pending = ArrayList<String>(photos.size * 2).apply {
                    photos.forEach { photo ->
                        add(photo.id)
                        add(photo.uri)
                    }
                }
                confirmed = 0
                launchBatch()
            }
        }
    }
}

actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry(ClipData.newPlainText("kinroll", text))
