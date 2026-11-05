package net.mioyi.kinroll.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.data.PhotoSummary
import net.mioyi.kinroll.resources.Res
import net.mioyi.kinroll.resources.*
import net.mioyi.kinroll.ui.components.PhotoGrid
import net.mioyi.kinroll.ui.components.formatCount
import net.mioyi.kinroll.ui.components.fullWidth
import net.mioyi.kinroll.ui.components.label
import net.mioyi.kinroll.ui.components.photoItems
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(graph: AppGraph, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val vm = viewModel { OverviewViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.entry_overview)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isBlank()) vm.clearSearch()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(Res.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            vm.clearSearch()
                        }) { Icon(Icons.Filled.Clear, stringResource(Res.string.clear_search)) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    vm.search(query)
                }),
            )
            when (val s = ui.search) {
                SearchUi.None -> {
                    FilterRow(ui, vm::selectFilter)
                    Results(ui.photos, stringResource(Res.string.empty_overview), null, onOpen)
                }
                is SearchUi.Running -> Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(Res.string.searching), style = MaterialTheme.typography.bodyMedium)
                }
                is SearchUi.Done -> Results(
                    photos = s.photos,
                    empty = stringResource(Res.string.search_no_results, s.query),
                    header = pluralStringResource(Res.plurals.search_results, s.photos.size, formatCount(s.photos.size), s.query),
                    onOpen = onOpen,
                )
            }
        }
    }
}

@Composable
private fun FilterRow(ui: OverviewUi, onSelect: (OverviewFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ui.chips, key = { it.filter.toString() }) { chip ->
            val name = when (val f = chip.filter) {
                OverviewFilter.All -> stringResource(Res.string.filter_all)
                OverviewFilter.Screenshots -> stringResource(Res.string.tab_screenshots)
                is OverviewFilter.Category -> stringResource(f.category.label())
            }
            FilterChip(
                selected = ui.filter == chip.filter,
                onClick = { onSelect(chip.filter) },
                label = { Text("$name  ${formatCount(chip.count)}") },
            )
        }
    }
}

@Composable
private fun Results(photos: List<PhotoSummary>?, empty: String, header: String?, onOpen: (String) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        when {
            photos == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            photos.isEmpty() -> Text(
                empty,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            else -> PhotoGrid(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (header != null) {
                    fullWidth("header") {
                        Text(
                            header,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                photoItems(photos, selected = null, onClick = { onOpen(it.id) })
            }
        }
    }
}
