package net.mioyi.kinroll.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.platform.plainTextClipEntry
import net.mioyi.kinroll.post.PostStyle
import net.mioyi.kinroll.resources.Res
import net.mioyi.kinroll.resources.*
import net.mioyi.kinroll.ui.components.PhotoGrid
import net.mioyi.kinroll.ui.components.PhotoThumbnail
import net.mioyi.kinroll.ui.components.fullWidth
import net.mioyi.kinroll.ui.components.label
import net.mioyi.kinroll.ui.components.photoItems
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(graph: AppGraph, onBack: () -> Unit) {
    val vm = viewModel { PostViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val zh = Locale.current.language.startsWith("zh")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.entry_post)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val photos = ui.photos
        if (photos == null) {
            Box(Modifier.fillMaxSize().padding(padding)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            return@Scaffold
        }
        val toggle: (String) -> Unit = { id ->
            if (!vm.toggle(id)) scope.launch { snackbar.showSnackbar(getString(Res.string.post_limit)) }
        }
        PhotoGrid(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            fullWidth("selected") { SelectedStrip(ui, onRemove = toggle) }
            fullWidth("compose") {
                Composer(
                    ui = ui,
                    onStyle = vm::setStyle,
                    onNotes = vm::setNotes,
                    onGenerate = { vm.generate(zh) },
                    onStop = vm::stop,
                    onCopy = {
                        scope.launch {
                            clipboard.setClipEntry(plainTextClipEntry(ui.output))
                            snackbar.showSnackbar(getString(Res.string.post_copied))
                        }
                    },
                )
            }
            fullWidth("all-title") {
                Text(
                    stringResource(Res.string.post_all_photos),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                )
            }
            val order = ui.selected.withIndex().associate { (i, id) -> id to i + 1 }
            photoItems(
                photos = photos,
                selected = { it.id in order },
                onClick = { toggle(it.id) },
                label = { order[it.id]?.toString() },
            )
        }
    }
}

@Composable
private fun SelectedStrip(ui: PostUi, onRemove: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            if (ui.selected.isEmpty()) stringResource(Res.string.post_pick_hint)
            else stringResource(Res.string.post_selected, ui.selected.size.toString()),
            style = MaterialTheme.typography.titleSmall,
            color = if (ui.selected.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        // Fixed-height strip with a trailing empty slot, so choosing the first photo doesn't shift the grid below.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ui.selected, key = { it }) { id ->
                PhotoThumbnail(
                    id = id,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClickLabel = stringResource(Res.string.remove)) { onRemove(id) },
                )
            }
            if (ui.selected.size < MAX_POST_PHOTOS) {
                item(key = "slot") {
                    Box(
                        Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(
    ui: PostUi,
    onStyle: (PostStyle) -> Unit,
    onNotes: (String) -> Unit,
    onGenerate: () -> Unit,
    onStop: () -> Unit,
    onCopy: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(stringResource(Res.string.post_style), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PostStyle.entries.forEachIndexed { i, style ->
                SegmentedButton(
                    selected = ui.style == style,
                    onClick = { onStyle(style) },
                    shape = SegmentedButtonDefaults.itemShape(i, PostStyle.entries.size),
                    enabled = !ui.generating,
                    icon = {},
                    label = { Text(stringResource(style.label()), maxLines = 1) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = ui.notes,
            onValueChange = onNotes,
            enabled = !ui.generating,
            placeholder = { Text(stringResource(Res.string.post_notes_hint)) },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        if (ui.generating) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(Res.string.post_stop))
            }
        } else {
            Button(
                onClick = onGenerate,
                enabled = ui.selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(if (ui.output.isEmpty()) Res.string.post_generate else Res.string.post_regenerate))
            }
        }
        if (ui.generating || ui.output.isNotEmpty() || ui.error != null) {
            Spacer(Modifier.height(16.dp))
            Output(ui, onCopy)
        }
    }
}

@Composable
private fun Output(ui: PostUi, onCopy: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (ui.generating && ui.output.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        stringResource(if (ui.modelLoading) Res.string.post_loading_model else Res.string.post_writing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (ui.output.isNotEmpty()) {
                SelectionContainer {
                    Text(ui.output, style = MaterialTheme.typography.bodyLarge)
                }
            }
            ui.error?.let {
                if (ui.output.isNotEmpty()) Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.post_failed, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!ui.generating && ui.output.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onCopy) { Text(stringResource(Res.string.post_copy)) }
            }
        }
    }
}
