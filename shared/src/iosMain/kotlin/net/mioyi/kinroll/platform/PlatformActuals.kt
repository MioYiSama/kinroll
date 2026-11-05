@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package net.mioyi.kinroll.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.ClipEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.mioyi.kinroll.ios.KinrollIos
import net.mioyi.kinroll.ios.permissionState

@Composable
actual fun rememberPermissionRequester(onResult: (PermissionState) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    return remember(scope) {
        { KinrollIos.photoKit.requestAuthorization { status ->
            scope.launch(Dispatchers.Main) { callback.value(permissionState(status)) }
        } }
    }
}

@Composable
actual fun rememberTrashRequester(onResult: (trashedIds: List<String>) -> Unit): (List<PhotoRef>) -> Unit {
    val callback = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    return remember(scope) {
        { photos ->
            val ids = photos.map { it.id }
            KinrollIos.photoKit.deleteAssets(ids) { success ->
                scope.launch(Dispatchers.Main) { callback.value(if (success) ids else emptyList()) }
            }
        }
    }
}

actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry.withPlainText(text)
