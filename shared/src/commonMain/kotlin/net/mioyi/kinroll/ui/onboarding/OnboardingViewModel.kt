package net.mioyi.kinroll.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.model.DownloadProgress
import net.mioyi.kinroll.model.DownloadSource
import net.mioyi.kinroll.model.ModelGroup
import net.mioyi.kinroll.model.ModelManifest
import net.mioyi.kinroll.platform.PermissionState

enum class OnboardingStep { PERMISSION, MODELS }

sealed interface DownloadUi {
    data object Idle : DownloadUi
    data class Running(val progress: DownloadProgress?) : DownloadUi
    data class Failed(val message: String) : DownloadUi
}

data class ModelGroupUi(val group: ModelGroup, val bytes: Long, val ready: Boolean)

data class OnboardingUi(
    val step: OnboardingStep,
    val permission: PermissionState,
    val asked: Boolean = false,
    val source: DownloadSource? = null,
    val groups: List<ModelGroupUi> = emptyList(),
    val download: DownloadUi = DownloadUi.Idle,
) {
    val totalBytes: Long get() = groups.sumOf { it.bytes }
    val modelsReady: Boolean get() = groups.isNotEmpty() && groups.all { it.ready }
    val granted: Boolean get() = permission == PermissionState.FULL || permission == PermissionState.LIMITED
}

class OnboardingViewModel(private val graph: AppGraph) : ViewModel() {
    private val photos = graph.platform.photoLibrary
    private val models = graph.modelManager

    private val _ui = MutableStateFlow(
        photos.permissionState().let { p ->
            // Full access is already satisfied; limited access stays on the step so the hint is seen once.
            OnboardingUi(
                step = if (p == PermissionState.FULL) OnboardingStep.MODELS else OnboardingStep.PERMISSION,
                permission = p,
                groups = groupStates(),
            )
        },
    )
    val ui: StateFlow<OnboardingUi> = _ui

    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            val source = graph.settings.downloadSource()
            _ui.update { it.copy(source = source) }
        }
    }

    private fun groupStates(): List<ModelGroupUi> = ModelGroup.entries.map { group ->
        val files = ModelManifest.files.filter { it.group == group }
        ModelGroupUi(group, files.sumOf { it.sizeBytes }, files.all { models.isReady(it) })
    }

    fun onPermissionResult(state: PermissionState) {
        _ui.update {
            it.copy(
                permission = state,
                asked = true,
                step = if (state == PermissionState.FULL) OnboardingStep.MODELS else it.step,
            )
        }
    }

    /** Re-read on resume: the user may have changed access in system Settings. */
    fun refreshPermission() {
        val state = photos.permissionState()
        _ui.update {
            it.copy(
                permission = state,
                step = if (state == PermissionState.FULL) OnboardingStep.MODELS else it.step,
            )
        }
    }

    fun continueWithLimited() {
        _ui.update { if (it.granted) it.copy(step = OnboardingStep.MODELS) else it }
    }

    fun selectSource(source: DownloadSource) {
        if (_ui.value.download is DownloadUi.Running) return
        _ui.update { it.copy(source = source) }
        viewModelScope.launch { graph.settings.setDownloadSource(source) }
    }

    fun startDownload() {
        val source = _ui.value.source ?: return
        if (downloadJob?.isActive == true) return
        _ui.update { it.copy(download = DownloadUi.Running(null)) }
        downloadJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                models.downloadAll(source) { p ->
                    _ui.update {
                        val previous = (it.download as? DownloadUi.Running)?.progress?.fileId
                        // A new file started: the previous one is on disk, so group checkmarks may change.
                        val groups = if (previous != p.fileId) groupStates() else it.groups
                        it.copy(download = DownloadUi.Running(p), groups = groups)
                    }
                }
                _ui.update { it.copy(download = DownloadUi.Idle, groups = groupStates()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.update {
                    it.copy(
                        download = DownloadUi.Failed(e.message ?: e::class.simpleName.orEmpty()),
                        groups = groupStates(),
                    )
                }
            }
        }
    }
}
