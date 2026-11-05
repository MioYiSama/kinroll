package net.mioyi.kinroll.ui.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.cleanup.DuplicateGroup
import net.mioyi.kinroll.data.PhotoSummary
import net.mioyi.kinroll.index.INDEX_VERSION

enum class CleanupTab { SCREENSHOTS, SAVED, BAD, DUPLICATES }

data class CleanupUi(
    val tab: CleanupTab = CleanupTab.SCREENSHOTS,
    val screenshots: List<PhotoSummary>? = null,
    val saved: List<PhotoSummary>? = null,
    val bad: List<PhotoSummary>? = null,
    /** null while comparing. */
    val duplicates: List<DuplicateGroup>? = null,
    val selection: Map<CleanupTab, Set<String>> = emptyMap(),
    val indexed: Int = 0,
    val total: Int = 0,
) {
    /** Photos of the current tab; null while loading. */
    val photos: List<PhotoSummary>?
        get() = when (tab) {
            CleanupTab.SCREENSHOTS -> screenshots
            CleanupTab.SAVED -> saved
            CleanupTab.BAD -> bad
            CleanupTab.DUPLICATES -> duplicates?.flatMap { it.photos }
        }

    val selected: Set<String> get() = selection[tab].orEmpty()

    val selectedPhotos: List<PhotoSummary>
        get() = photos.orEmpty().filter { it.id in selected }

    val selectedBytes: Long get() = selectedPhotos.sumOf { it.sizeBytes }
}

private data class Lists(
    val screenshots: List<PhotoSummary>,
    val saved: List<PhotoSummary>,
    val bad: List<PhotoSummary>,
    val indexed: Int,
    val total: Int,
)

class CleanupViewModel(graph: AppGraph) : ViewModel() {
    private val cleanup = graph.cleanup
    private val local = MutableStateFlow(CleanupUi())
    private var duplicatesJob: Job? = null

    private val lists = combine(
        cleanup.screenshots(),
        cleanup.savedImages(),
        cleanup.badShots(),
        graph.photoDao.observeIndexedCount(INDEX_VERSION),
        graph.photoDao.observeCount(),
    ) { s, v, b, indexed, total -> Lists(s, v, b, indexed, total) }

    val ui: StateFlow<CleanupUi> = combine(local, lists) { l, lists ->
        l.copy(
            screenshots = lists.screenshots,
            saved = lists.saved,
            bad = lists.bad,
            indexed = lists.indexed,
            total = lists.total,
            // Drop selections of photos that vanished (trashed here or deleted elsewhere).
            selection = l.selection.mapValues { (tab, ids) ->
                val present = when (tab) {
                    CleanupTab.SCREENSHOTS -> lists.screenshots
                    CleanupTab.SAVED -> lists.saved
                    CleanupTab.BAD -> lists.bad
                    CleanupTab.DUPLICATES -> l.duplicates?.flatMap { it.photos }.orEmpty()
                }.mapTo(HashSet()) { it.id }
                ids.filterTo(HashSet()) { it in present }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CleanupUi())

    fun selectTab(tab: CleanupTab) {
        local.update { it.copy(tab = tab) }
        if (tab == CleanupTab.DUPLICATES && local.value.duplicates == null) loadDuplicates()
    }

    fun toggle(id: String) = local.update {
        val current = it.selection[it.tab].orEmpty()
        val next = if (id in current) current - id else current + id
        it.copy(selection = it.selection + (it.tab to next))
    }

    fun selectAll(all: Boolean) {
        val photos = ui.value.photos.orEmpty()
        local.update {
            it.copy(selection = it.selection + (it.tab to if (all) photos.mapTo(HashSet()) { p -> p.id } else emptySet()))
        }
    }

    fun onTrashed(ids: List<String>) {
        if (ids.isEmpty()) return
        val removed = ids.toHashSet()
        local.update { s ->
            s.copy(
                selection = s.selection.mapValues { (_, sel) -> sel - removed },
                duplicates = s.duplicates?.let { groups -> regroup(groups, removed) },
            )
        }
        viewModelScope.launch { cleanup.onTrashed(ids) }
    }

    private fun loadDuplicates() {
        if (duplicatesJob?.isActive == true) return
        duplicatesJob = viewModelScope.launch {
            val groups = try {
                withContext(Dispatchers.Default) { cleanup.duplicates() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                println("kinroll-ui duplicates failed: ${e.message}")
                emptyList()
            }
            local.update { s ->
                // Everything except each group's keeper starts selected.
                val preselected = groups.flatMap { g -> g.photos.filter { it.id != g.keepId }.map { it.id } }.toSet()
                s.copy(duplicates = groups, selection = s.selection + (CleanupTab.DUPLICATES to preselected))
            }
        }
    }

    /** Remove trashed photos from groups locally; a group left with one photo is no longer a duplicate. */
    private fun regroup(groups: List<DuplicateGroup>, removed: Set<String>): List<DuplicateGroup> =
        groups.mapNotNull { g ->
            val rest = g.photos.filter { it.id !in removed }
            when {
                rest.size < 2 -> null
                g.keepId in removed -> DuplicateGroup(rest, rest.first().id)
                else -> g.copy(photos = rest)
            }
        }
}
