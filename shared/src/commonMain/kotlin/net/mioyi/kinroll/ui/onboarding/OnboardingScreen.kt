package net.mioyi.kinroll.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.model.DownloadSource
import net.mioyi.kinroll.model.ModelManifest
import net.mioyi.kinroll.platform.PermissionState
import net.mioyi.kinroll.platform.rememberPermissionRequester
import net.mioyi.kinroll.resources.Res
import net.mioyi.kinroll.resources.*
import net.mioyi.kinroll.ui.components.description
import net.mioyi.kinroll.ui.components.formatBytes
import net.mioyi.kinroll.ui.components.label
import org.jetbrains.compose.resources.stringResource

@Composable
fun OnboardingScreen(graph: AppGraph, onDone: () -> Unit) {
    val vm = viewModel { OnboardingViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val requestPermission = rememberPermissionRequester(vm::onPermissionResult)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshPermission() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(Res.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            StepStrip(ui.step)
            AnimatedContent(ui.step, Modifier.weight(1f)) { step ->
                when (step) {
                    OnboardingStep.PERMISSION -> PermissionStep(
                        ui = ui,
                        onRequest = requestPermission,
                        onContinue = vm::continueWithLimited,
                    )
                    OnboardingStep.MODELS -> ModelsStep(
                        ui = ui,
                        onSource = vm::selectSource,
                        onDownload = vm::startDownload,
                        onEnter = onDone,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepStrip(step: OnboardingStep) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OnboardingStep.entries.forEach {
            Box(
                Modifier
                    .size(width = 28.dp, height = 4.dp)
                    .background(
                        if (it.ordinal <= step.ordinal) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun StepLayout(
    title: String,
    body: String,
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 32.dp, bottom = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(28.dp))
            content()
        }
        Column(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { actions() }
    }
}

@Composable
private fun Note(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(12.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun PermissionStep(ui: OnboardingUi, onRequest: () -> Unit, onContinue: () -> Unit) {
    StepLayout(
        title = stringResource(Res.string.onboarding_permission_title),
        body = stringResource(Res.string.onboarding_permission_body),
        actions = {
            if (ui.permission == PermissionState.LIMITED) {
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(Res.string.onboarding_continue))
                }
                OutlinedButton(onClick = onRequest, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(Res.string.onboarding_choose_again))
                }
            } else {
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(Res.string.onboarding_permission_button))
                }
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                listOf(
                    Res.string.entry_cleanup to Res.string.onboarding_feature_cleanup,
                    Res.string.entry_overview to Res.string.onboarding_feature_search,
                    Res.string.entry_post to Res.string.onboarding_feature_post,
                ).forEach { (title, body) ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        when {
            ui.permission == PermissionState.LIMITED -> Note(
                stringResource(Res.string.onboarding_permission_limited),
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
            )
            ui.asked && ui.permission == PermissionState.DENIED -> Note(
                stringResource(Res.string.onboarding_permission_denied),
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ModelsStep(
    ui: OnboardingUi,
    onSource: (DownloadSource) -> Unit,
    onDownload: () -> Unit,
    onEnter: () -> Unit,
) {
    val running = ui.download is DownloadUi.Running
    StepLayout(
        title = stringResource(Res.string.onboarding_models_title),
        body = stringResource(Res.string.onboarding_models_body, formatBytes(ui.totalBytes)),
        actions = {
            when (val d = ui.download) {
                is DownloadUi.Running -> DownloadProgressBlock(d)
                is DownloadUi.Failed -> {
                    Text(
                        stringResource(Res.string.download_failed, d.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(stringResource(Res.string.retry))
                    }
                }
                DownloadUi.Idle -> if (ui.modelsReady) {
                    Button(
                        onClick = onEnter,
                        enabled = ui.granted,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(Res.string.onboarding_enter)) }
                } else {
                    Button(
                        onClick = onDownload,
                        enabled = ui.source != null,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(Res.string.download_start)) }
                }
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(vertical = 8.dp)) {
                ui.groups.forEach { g ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(g.group.label()), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(g.group.description()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (g.ready) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = stringResource(Res.string.model_ready),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text(formatBytes(g.bytes), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        if (!ui.modelsReady) {
            Spacer(Modifier.height(28.dp))
            Text(stringResource(Res.string.download_source_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Column(Modifier.selectableGroup()) {
                DownloadSource.entries.forEach { source ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = source == ui.source,
                                enabled = !running,
                                role = Role.RadioButton,
                                onClick = { onSource(source) },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = source == ui.source, onClick = null, enabled = !running)
                        Spacer(Modifier.size(12.dp))
                        Column {
                            Text(stringResource(source.label()), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(source.description()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressBlock(d: DownloadUi.Running) {
    val p = d.progress
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (p == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            return@Column
        }
        val name = runCatching { ModelManifest.file(p.fileId).path.substringAfterLast('/') }.getOrDefault(p.fileId)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.download_file, name),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatBytes(p.fileBytes) + " / " + formatBytes(p.fileTotal),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { if (p.fileTotal > 0) (p.fileBytes.toFloat() / p.fileTotal).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.tertiary,
        )
        LinearProgressIndicator(
            progress = { if (p.overallTotal > 0) (p.overallBytes.toFloat() / p.overallTotal).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Text(
            stringResource(Res.string.download_overall, formatBytes(p.overallBytes), formatBytes(p.overallTotal)),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}
