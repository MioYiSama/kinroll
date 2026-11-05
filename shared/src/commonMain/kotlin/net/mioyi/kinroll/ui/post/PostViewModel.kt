package net.mioyi.kinroll.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.data.PhotoSummary
import net.mioyi.kinroll.post.PostStyle

const val MAX_POST_PHOTOS = 9

data class PostUi(
    val photos: List<PhotoSummary>? = null,
    val selected: List<String> = emptyList(),
    val style: PostStyle = PostStyle.XIAOHONGSHU,
    val notes: String = "",
    val output: String = "",
    val generating: Boolean = false,
    val modelLoading: Boolean = false,
    val error: String? = null,
)

class PostViewModel(graph: AppGraph) : ViewModel() {
    private val posts = graph.posts
    private val local = MutableStateFlow(PostUi())
    private var job: Job? = null

    val ui: StateFlow<PostUi> = combine(local, graph.photoDao.observeSummaries(), graph.llm.loading) { l, photos, loading ->
        val present = photos.mapTo(HashSet()) { it.id }
        l.copy(photos = photos, selected = l.selected.filter { it in present }, modelLoading = loading)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PostUi())

    /** @return false when the photo could not be added because the limit is reached. */
    fun toggle(id: String): Boolean {
        val current = local.value.selected
        if (id in current) {
            local.update { it.copy(selected = it.selected - id) }
            return true
        }
        if (current.size >= MAX_POST_PHOTOS) return false
        local.update { it.copy(selected = it.selected + id) }
        return true
    }

    fun setStyle(style: PostStyle) = local.update { it.copy(style = style) }

    fun setNotes(notes: String) = local.update { it.copy(notes = notes) }

    fun generate(zh: Boolean) {
        val s = ui.value
        if (s.selected.isEmpty() || s.generating) return
        job?.cancel()
        local.update { it.copy(output = "", error = null, generating = true) }
        job = viewModelScope.launch {
            try {
                posts.generate(s.selected, s.style, s.notes.trim(), zh).collect { delta ->
                    local.update { it.copy(output = it.output + delta) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                local.update { it.copy(error = e.message ?: e::class.simpleName.orEmpty()) }
            } finally {
                local.update { it.copy(generating = false, output = it.output.trim()) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        local.update { it.copy(generating = false, output = it.output.trim()) }
    }
}
