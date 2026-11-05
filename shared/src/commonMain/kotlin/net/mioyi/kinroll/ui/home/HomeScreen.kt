package net.mioyi.kinroll.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.data.PhotoSummary
import net.mioyi.kinroll.index.IndexerState
import net.mioyi.kinroll.resources.Res
import net.mioyi.kinroll.resources.*
import net.mioyi.kinroll.ui.components.PhotoThumbnail
import net.mioyi.kinroll.ui.components.formatCount
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    graph: AppGraph,
    onCleanup: () -> Unit,
    onOverview: () -> Unit,
    onPost: () -> Unit,
    onCredits: () -> Unit,
) {
    val vm = viewModel { HomeViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Fires once when Home first appears (lifecycle catch-up) and on every return to the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { graph.indexScheduler.request() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.app_name), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onCredits) {
                        Icon(Icons.Outlined.Info, stringResource(Res.string.credits_title))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { IndexPanel(ui) }
            item { Spacer(Modifier.height(4.dp)) }
            item {
                Entry(
                    title = stringResource(Res.string.entry_cleanup),
                    description = stringResource(
                        Res.string.entry_cleanup_desc,
                        formatCount(ui.screenshots),
                        formatCount(ui.badShots),
                    ),
                    onClick = onCleanup,
                )
            }
            item {
                Entry(
                    title = stringResource(Res.string.entry_overview),
                    description = stringResource(Res.string.entry_overview_desc),
                    onClick = onOverview,
                    photos = ui.recent,
                )
            }
            item {
                Entry(
                    title = stringResource(Res.string.entry_post),
                    description = stringResource(Res.string.entry_post_desc),
                    onClick = onPost,
                )
            }
        }
    }
}

@Composable
private fun IndexPanel(ui: HomeUi) {
    val state = ui.indexer
    val status = when {
        state is IndexerState.Syncing -> stringResource(Res.string.index_syncing)
        state is IndexerState.Failed -> stringResource(Res.string.index_failed, state.message)
        ui.total == 0 -> stringResource(Res.string.index_empty)
        state is IndexerState.Indexing ->
            stringResource(Res.string.index_progress, formatCount(ui.indexed), formatCount(ui.total))
        ui.indexed >= ui.total -> pluralStringResource(Res.plurals.index_done, ui.total, formatCount(ui.total))
        else -> stringResource(Res.string.index_waiting, formatCount(ui.indexed), formatCount(ui.total))
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatCount(ui.total),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    pluralStringResource(Res.plurals.home_photos_label, ui.total),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            if (state is IndexerState.Syncing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp))
            } else {
                LinearProgressIndicator(
                    progress = { if (ui.total > 0) ui.indexed.toFloat() / ui.total else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state is IndexerState.Failed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Entry(
    title: String,
    description: String,
    onClick: () -> Unit,
    photos: List<PhotoSummary> = emptyList(),
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (photos.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) { i ->
                        val photo = photos.getOrNull(i)
                        val cell = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                        if (photo != null) PhotoThumbnail(photo.id, cell) else Spacer(cell)
                    }
                }
            }
        }
    }
}
