package net.mioyi.kinroll.ui.credits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.mioyi.kinroll.resources.Res
import net.mioyi.kinroll.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(onBack: () -> Unit, onOpen: (name: String, licenseFile: String) -> Unit) {
    Scaffold(topBar = { BackBar(stringResource(Res.string.credits_title), onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item { SectionHeader(stringResource(Res.string.credits_software)) }
            items(softwareCredits) { CreditRow(it) { onOpen(it.name, it.licenseFile) } }
            item {
                SectionHeader(stringResource(Res.string.credits_models))
                Text(
                    stringResource(Res.string.credits_models_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
                )
            }
            items(modelCredits) { CreditRow(it) { onOpen(it.name, it.licenseFile) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(name: String, licenseFile: String, onBack: () -> Unit) {
    val text by produceState<String?>(null, licenseFile) {
        value = reflow(Res.readBytes(licenseFile).decodeToString())
    }
    Scaffold(topBar = { BackBar(name, onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when (val t = text) {
                null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> SelectionContainer {
                    Text(
                        t,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                            .padding(bottom = padding.calculateBottomPadding()),
                    )
                }
            }
        }
    }
}

private val listItemStart = Regex("""^\s*(\*|\d+\.|\([a-z0-9]+\))\s""")

/** License files are hard-wrapped at ~80 columns; unwrap paragraphs so they fit a phone screen. */
private fun reflow(text: String): String {
    val out = StringBuilder()
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        when {
            line.isEmpty() -> if (out.isNotEmpty() && !out.endsWith("\n\n")) out.append("\n\n")
            out.isEmpty() || out.endsWith('\n') -> out.append(line)
            listItemStart.containsMatchIn(raw) -> out.append('\n').append(line)
            else -> out.append(' ').append(line)
        }
    }
    return out.trimEnd().toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
            }
        },
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun CreditRow(credit: Credit, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(credit.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(
            "${credit.holder} · ${credit.license}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        credit.notice?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
