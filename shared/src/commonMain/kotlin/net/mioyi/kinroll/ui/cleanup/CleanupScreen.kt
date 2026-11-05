package net.mioyi.kinroll.ui.cleanup

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.platform.PhotoRef
import net.mioyi.kinroll.platform.rememberTrashRequester
import net.mioyi.kinroll.resources.Res
import net.mioyi.kinroll.resources.*
import net.mioyi.kinroll.ui.components.PhotoGrid
import net.mioyi.kinroll.ui.components.badShotLabel
import net.mioyi.kinroll.ui.components.formatBytes
import net.mioyi.kinroll.ui.components.formatCount
import net.mioyi.kinroll.ui.components.fullWidth
import net.mioyi.kinroll.ui.components.photoItems
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.stringResource

private fun CleanupTab.title(): StringResource = when (this) {
    CleanupTab.SCREENSHOTS -> Res.string.tab_screenshots
    CleanupTab.SAVED -> Res.string.tab_saved
    CleanupTab.BAD -> Res.string.tab_bad
    CleanupTab.DUPLICATES -> Res.string.tab_duplicates
}

private fun CleanupTab.empty(): StringResource = when (this) {
    CleanupTab.SCREENSHOTS -> Res.string.empty_screenshots
    CleanupTab.SAVED -> Res.string.empty_saved
    CleanupTab.BAD -> Res.string.empty_bad
    CleanupTab.DUPLICATES -> Res.string.empty_duplicates
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(graph: AppGraph, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val vm = viewModel { CleanupViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val requestTrash = rememberTrashRequester { ids ->
        vm.onTrashed(ids)
        if (ids.isNotEmpty()) scope.launch {
            snackbar.showSnackbar(getPluralString(Res.plurals.trashed_message, ids.size, formatCount(ids.size)))
        }
    }
    val photos = ui.photos
    val allSelected = !photos.isNullOrEmpty() && ui.selected.size == photos.size

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(Res.string.entry_cleanup)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                        }
                    },
                    actions = {
                        if (!photos.isNullOrEmpty()) {
                            TextButton(onClick = { vm.selectAll(!allSelected) }) {
                                Text(stringResource(if (allSelected) Res.string.select_none else Res.string.select_all))
                            }
                        }
                    },
                )
                PrimaryScrollableTabRow(selectedTabIndex = ui.tab.ordinal, edgePadding = 8.dp) {
                    CleanupTab.entries.forEach { tab ->
                        val count = when (tab) {
                            CleanupTab.SCREENSHOTS -> ui.screenshots?.size
                            CleanupTab.SAVED -> ui.saved?.size
                            CleanupTab.BAD -> ui.bad?.size
                            CleanupTab.DUPLICATES -> ui.duplicates?.sumOf { it.photos.size }
                        }
                        Tab(
                            selected = ui.tab == tab,
                            onClick = { vm.selectTab(tab) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(tab.title()))
                                    if (count != null && count > 0) {
                                        Text(
                                            "  " + formatCount(count),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (!photos.isNullOrEmpty()) {
                BottomAppBar {
                    Text(
                        stringResource(
                            Res.string.selected_summary,
                            formatCount(ui.selected.size),
                            formatBytes(ui.selectedBytes),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                    )
                    Button(
                        onClick = { requestTrash(ui.selectedPhotos.map { PhotoRef(it.id, it.uri) }) },
                        enabled = ui.selected.isNotEmpty(),
                        modifier = Modifier.padding(end = 16.dp),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.padding(start = 6.dp))
                        Text(stringResource(Res.string.move_to_trash))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                photos == null -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    if (ui.tab == CleanupTab.DUPLICATES) Text(stringResource(Res.string.duplicates_finding))
                }
                photos.isEmpty() -> EmptyState(ui, Modifier.align(Alignment.Center))
                else -> CleanupGrid(ui, onToggle = vm::toggle, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun CleanupGrid(ui: CleanupUi, onToggle: (String) -> Unit, onOpen: (String) -> Unit) {
    val keepLabel = stringResource(Res.string.keep_label)
    val selected = ui.selected
    PhotoGrid(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (ui.indexed < ui.total) {
            fullWidth("note") { IndexingNote(ui.indexed, ui.total) }
        }
        if (ui.tab == CleanupTab.DUPLICATES) {
            ui.duplicates.orEmpty().forEachIndexed { i, group ->
                fullWidth("group-$i-${group.keepId}") {
                    Text(
                        stringResource(Res.string.duplicate_group, formatCount(group.photos.size)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                photoItems(
                    photos = group.photos,
                    keyPrefix = "g$i-",
                    selected = { it.id in selected },
                    onClick = { onToggle(it.id) },
                    onLongClick = { onOpen(it.id) },
                    label = { if (it.id == group.keepId) keepLabel else null },
                )
            }
        } else {
            photoItems(
                photos = ui.photos.orEmpty(),
                selected = { it.id in selected },
                onClick = { onToggle(it.id) },
                onLongClick = { onOpen(it.id) },
                label = { p ->
                    if (ui.tab == CleanupTab.BAD) badShotLabel(p.badShotReasons)?.let { stringResource(it) } else null
                },
            )
        }
    }
}

@Composable
private fun IndexingNote(indexed: Int, total: Int) {
    Text(
        stringResource(Res.string.cleanup_indexing_note, formatCount(indexed), formatCount(total)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun EmptyState(ui: CleanupUi, modifier: Modifier) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(ui.tab.empty()), style = MaterialTheme.typography.titleMedium)
        if (ui.indexed < ui.total) {
            Text(
                stringResource(Res.string.cleanup_indexing_note, formatCount(ui.indexed), formatCount(ui.total)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(48.dp))
    }
}
