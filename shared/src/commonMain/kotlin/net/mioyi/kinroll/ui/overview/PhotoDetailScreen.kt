package net.mioyi.kinroll.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.data.PhotoEntity
import net.mioyi.kinroll.resources.Res
import net.mioyi.kinroll.resources.*
import net.mioyi.kinroll.ui.components.PhotoThumbnail
import net.mioyi.kinroll.ui.components.categoryOf
import net.mioyi.kinroll.ui.components.formatBytes
import net.mioyi.kinroll.ui.components.formatDateTime
import net.mioyi.kinroll.ui.components.formatExposure
import net.mioyi.kinroll.ui.components.kindOf
import net.mioyi.kinroll.ui.components.label
import net.mioyi.kinroll.ui.components.trimDecimal
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private sealed interface DetailState {
    data object Loading : DetailState
    data object Missing : DetailState
    data class Loaded(val photo: PhotoEntity) : DetailState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(graph: AppGraph, id: String, onBack: () -> Unit) {
    val state by produceState<DetailState>(DetailState.Loading, id) {
        value = graph.photoDao.getById(id)?.let { DetailState.Loaded(it) } ?: DetailState.Missing
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when (val s = state) {
                DetailState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                DetailState.Missing -> Text(
                    stringResource(Res.string.detail_not_found),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                is DetailState.Loaded -> Detail(s.photo, padding.calculateBottomPadding().value)
            }
        }
    }
}

@Composable
private fun Detail(photo: PhotoEntity, bottomInset: Float) {
    // MediaStore reports stored (unrotated) dimensions; displayed pixels are swapped at 90°/270°.
    val rotated = photo.orientation % 180 != 0
    val w = if (rotated) photo.height else photo.width
    val h = if (rotated) photo.width else photo.height
    val ratio = if (w > 0 && h > 0) w.toFloat() / h else 1f
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PhotoThumbnail(
            id = photo.id,
            maxSide = 1600,
            contentScale = ContentScale.Fit,
            // Portraits get a 3:4 frame so the metadata stays reachable; Fit letterboxes inside it.
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio.coerceAtLeast(0.75f)),
        )
        Spacer(Modifier.height(16.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            photo.takenAt?.let { Field(Res.string.detail_taken, formatDateTime(it)) }
            if (w > 0 && h > 0) Field(Res.string.detail_dimensions, "$w × $h")
            if (photo.sizeBytes > 0) Field(Res.string.detail_size, formatBytes(photo.sizeBytes))
            listOfNotNull(photo.cameraMake, photo.cameraModel)
                .joinToString(" ").takeIf { it.isNotBlank() }
                ?.let { Field(Res.string.detail_camera, it) }
            photo.lensModel?.takeIf { it.isNotBlank() }?.let { Field(Res.string.detail_lens, it) }
            photo.iso?.let { Field(Res.string.detail_iso, it.toString()) }
            photo.exposureTime?.let { Field(Res.string.detail_shutter, formatExposure(it)) }
            photo.fNumber?.let { Field(Res.string.detail_aperture, "f/" + trimDecimal(it, 1)) }
            photo.focalLength?.let { Field(Res.string.detail_focal, trimDecimal(it, 1) + " mm") }
            if (photo.latitude != null && photo.longitude != null) {
                Field(Res.string.detail_location, trimDecimal(photo.latitude!!, 5) + ", " + trimDecimal(photo.longitude!!, 5))
            }
            if (photo.indexVersion > 0) {
                Field(Res.string.detail_kind, stringResource(kindOf(photo.kind).label()))
                categoryOf(photo.category)?.let { Field(Res.string.detail_category, stringResource(it.label())) }
            }
            photo.ocrText?.takeIf { it.isNotBlank() }?.let { text ->
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.detail_ocr),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Spacer(Modifier.height((bottomInset + 32).dp))
    }
}

@Composable
private fun Field(label: StringResource, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
