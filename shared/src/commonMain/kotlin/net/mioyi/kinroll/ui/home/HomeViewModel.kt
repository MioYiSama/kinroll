package net.mioyi.kinroll.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.data.PhotoSummary
import net.mioyi.kinroll.index.INDEX_VERSION
import net.mioyi.kinroll.index.IndexerState
import net.mioyi.kinroll.platform.PhotoKind

data class HomeUi(
    val total: Int = 0,
    val indexed: Int = 0,
    val indexer: IndexerState = IndexerState.Idle,
    val screenshots: Int = 0,
    val badShots: Int = 0,
    val recent: List<PhotoSummary> = emptyList(),
)

class HomeViewModel(graph: AppGraph) : ViewModel() {
    private val dao = graph.photoDao

    private val counts = combine(
        dao.observeCount(),
        dao.observeIndexedCount(INDEX_VERSION),
        dao.observeKindCount(PhotoKind.SCREENSHOT.name),
        dao.observeBadShots().map { it.size },
    ) { total, indexed, screenshots, bad -> HomeUi(total, indexed, IndexerState.Idle, screenshots, bad) }

    val ui: StateFlow<HomeUi> = combine(
        counts,
        graph.indexer.state,
        dao.observeSummaries().map { it.take(RECENT) },
    ) { c, state, recent -> c.copy(indexer = state, recent = recent) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    private companion object {
        const val RECENT = 4
    }
}
