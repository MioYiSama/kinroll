package net.mioyi.kinroll.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.data.PhotoSummary
import net.mioyi.kinroll.ml.clip.PhotoCategory
import net.mioyi.kinroll.platform.PhotoKind

sealed interface OverviewFilter {
    data object All : OverviewFilter
    data object Screenshots : OverviewFilter
    data class Category(val category: PhotoCategory) : OverviewFilter
}

data class FilterChipUi(val filter: OverviewFilter, val count: Int)

sealed interface SearchUi {
    data object None : SearchUi
    data class Running(val query: String) : SearchUi
    data class Done(val query: String, val photos: List<PhotoSummary>) : SearchUi
}

data class OverviewUi(
    val filter: OverviewFilter = OverviewFilter.All,
    val chips: List<FilterChipUi> = emptyList(),
    /** null while the first query result is loading. */
    val photos: List<PhotoSummary>? = null,
    val search: SearchUi = SearchUi.None,
)

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModel(graph: AppGraph) : ViewModel() {
    private val dao = graph.photoDao
    private val searchService = graph.search
    private val filter = MutableStateFlow<OverviewFilter>(OverviewFilter.All)
    private val search = MutableStateFlow<SearchUi>(SearchUi.None)
    private var searchJob: Job? = null

    private val chips: Flow<List<FilterChipUi>> = combine(
        dao.observeCount(),
        dao.observeKindCount(PhotoKind.SCREENSHOT.name),
        dao.observeCategoryCounts(),
    ) { total, screenshots, categories ->
        val byName = categories.associate { it.category to it.count }
        buildList {
            add(FilterChipUi(OverviewFilter.All, total))
            if (screenshots > 0) add(FilterChipUi(OverviewFilter.Screenshots, screenshots))
            PhotoCategory.entries
                .mapNotNull { c -> byName[c.name]?.takeIf { it > 0 }?.let { FilterChipUi(OverviewFilter.Category(c), it) } }
                .sortedByDescending { it.count }
                .let(::addAll)
        }
    }

    private val photos: Flow<List<PhotoSummary>> = filter.flatMapLatest { f ->
        when (f) {
            OverviewFilter.All -> dao.observeSummaries()
            OverviewFilter.Screenshots -> dao.observeByKind(PhotoKind.SCREENSHOT.name)
            is OverviewFilter.Category -> dao.observeByCategory(f.category.name)
        }
    }

    val ui: StateFlow<OverviewUi> = combine(filter, chips, photos.map<List<PhotoSummary>, List<PhotoSummary>?> { it }, search) { f, c, p, s ->
        OverviewUi(filter = f, chips = c, photos = p, search = s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUi())

    fun selectFilter(f: OverviewFilter) {
        filter.value = f
    }

    fun search(raw: String) {
        val query = raw.trim()
        searchJob?.cancel()
        if (query.isEmpty()) {
            search.value = SearchUi.None
            return
        }
        search.value = SearchUi.Running(query)
        searchJob = viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.Default) {
                    val ids = searchService.search(query)
                    val byId = dao.summariesByIds(ids).associateBy { it.id }
                    ids.mapNotNull { byId[it] }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                println("kinroll-ui search failed: ${e.message}")
                emptyList()
            }
            search.update { if (it is SearchUi.Running && it.query == query) SearchUi.Done(query, result) else it }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        search.value = SearchUi.None
    }
}
