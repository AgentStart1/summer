package com.storytellerf.summer.ui.addbalance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storytellerf.summer.data.recognition.parseLocalDateTime

@Composable
internal fun BalanceImportPreview(state: AddBalanceChangeUiState, viewModel: AddBalanceChangeViewModel, modifier: Modifier) {
    val editable = !state.isSaving && !state.isImageAnalyzing
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Review balances before saving", style = MaterialTheme.typography.titleLarge)
            Text("Assign each balance to a selected account and check its amount and image creation time.")
            if (state.isImageAnalyzing) CircularProgressIndicator()
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = viewModel::clearBalancePreview, enabled = editable) { Text("Back to manual entry") }
        }
        itemsIndexed(state.balanceRows, key = { _, row -> row.key }) { index, row ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Checkbox(checked = row.selected, enabled = editable,
                            onCheckedChange = { viewModel.updateBalanceRow(row.key, row.copy(selected = it)) })
                        Text("Balance preview ${index + 1} · Image ${row.imageIndex + 1}")
                    }
                    if (row.label.isNotBlank()) Text(row.label)
                    if (row.fundSourceId == null) Text("Assign an account before saving", color = MaterialTheme.colorScheme.error)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.imageTargets, key = { it.fundSourceId }) { target ->
                            FilterChip(selected = row.fundSourceId == target.fundSourceId, enabled = editable,
                                onClick = { viewModel.updateBalanceRow(row.key, row.copy(fundSourceId = target.fundSourceId)) },
                                label = { Text("Use ${target.name}") })
                        }
                    }
                    OutlinedTextField(value = row.balance, onValueChange = { viewModel.updateBalanceRow(row.key, row.copy(balance = it)) },
                        label = { Text("Balance (CNY)") }, enabled = editable, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = row.dateTime, onValueChange = {
                        viewModel.updateBalanceRow(row.key, row.copy(dateTime = it, timestamp = parseLocalDateTime(it.trim())))
                    }, label = { Text("Local date and time") }, supportingText = { Text("yyyy-MM-ddTHH:mm:ss") },
                        enabled = editable, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = row.note, onValueChange = { viewModel.updateBalanceRow(row.key, row.copy(note = it)) },
                        label = { Text("Note (Optional)") }, enabled = editable, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
