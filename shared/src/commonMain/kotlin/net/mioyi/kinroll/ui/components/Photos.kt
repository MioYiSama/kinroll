package net.mioyi.kinroll.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import net.mioyi.kinroll.data.PhotoSummary
import net.mioyi.kinroll.ui.ThumbnailCache

val LocalThumbnailCache: ProvidableCompositionLocal<ThumbnailCache> =
    staticCompositionLocalOf { error("ThumbnailCache not provided") }

/** Thumbnail with a flat placeholder tone while decoding; nothing is drawn for unreadable photos. */
@Composable
fun PhotoThumbnail(
    id: String,
    modifier: Modifier = Modifier,
    maxSide: Int = 256,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val cache = LocalThumbnailCache.current
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, id, maxSide) {
        value = cache.peek(id, maxSide) ?: cache.load(id, maxSide)
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        bitmap?.let {
            Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = contentScale)
        }
    }
}

private val CellGap = 2.dp

@Composable
fun PhotoGrid(
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(CellGap),
        verticalArrangement = Arrangement.spacedBy(CellGap),
        content = content,
    )
}

fun LazyGridScope.fullWidth(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = "header") { content() }
}

/**
 * @param selected null = no selection mode; otherwise whether this photo is selected.
 */
fun LazyGridScope.photoItems(
    photos: List<PhotoSummary>,
    keyPrefix: String = "",
    selected: ((PhotoSummary) -> Boolean)? = null,
    onClick: (PhotoSummary) -> Unit,
    onLongClick: ((PhotoSummary) -> Unit)? = null,
    label: @Composable (PhotoSummary) -> String? = { null },
) {
    items(photos, key = { keyPrefix + it.id }, contentType = { "photo" }) { photo ->
        PhotoCell(
            photo = photo,
            selected = selected?.invoke(photo),
            onClick = { onClick(photo) },
            onLongClick = onLongClick?.let { { it(photo) } },
            label = label(photo),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoCell(
    photo: PhotoSummary,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    val inset by animateDpAsState(if (selected == true) 10.dp else 0.dp)
    val corner by animateDpAsState(if (selected == true) 10.dp else 0.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .background(if (selected == true) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        PhotoThumbnail(
            id = photo.id,
            modifier = Modifier.fillMaxSize().padding(inset).clip(RoundedCornerShape(corner)),
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(inset + 4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (selected != null) {
            SelectionMark(selected, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    if (selected) {
        Box(
            modifier.size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    } else {
        Box(
            modifier
                .size(22.dp)
                .background(Color.Black.copy(alpha = 0.25f), CircleShape)
                .border(1.5.dp, Color.White, CircleShape),
        )
    }
}
