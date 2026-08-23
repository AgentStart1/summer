package com.example.summerapp.ui.fundsources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.summerapp.data.DataRepository
import com.example.summerapp.data.db.SummerDatabase
import com.example.summerapp.data.db.entity.FundSource
import com.example.summerapp.data.DefaultDataRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundSourcesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FundSourcesViewModel = viewModel(
        factory = FundSourcesViewModel.Factory(
            DefaultDataRepository(SummerDatabase.getInstance(LocalContext.current.applicationContext))
        )
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var editingFundSource by remember { mutableStateOf<FundSource?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Fund Sources") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingFundSource = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Fund Source")
            }
        },
    ) { paddingValues ->
        when (val s = state) {
            FundSourcesUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
            is FundSourcesUiState.Success -> {
                FundSourcesContent(
                    fundSources = s.fundSources,
                    onEdit = { fundSource ->
                        editingFundSource = fundSource
                        showDialog = true
                    },
                    onDelete = { viewModel.deleteFundSource(it) },
                    modifier = Modifier.padding(paddingValues),
                )
            }
            is FundSourcesUiState.Error -> {
                Text(
                    text = "Error: ${s.message}",
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }

    if (showDialog) {
        FundSourceDialog(
            fundSource = editingFundSource,
            onDismiss = { showDialog = false },
            onConfirm = { name ->
                if (editingFundSource != null) {
                    viewModel.updateFundSource(editingFundSource!!.copy(name = name))
                } else {
                    viewModel.addFundSource(name)
                }
                showDialog = false
            },
        )
    }
}

@Composable
private fun FundSourcesContent(
    fundSources: List<FundSource>,
    onEdit: (FundSource) -> Unit,
    onDelete: (FundSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (fundSources.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No fund sources yet.\nTap + to add one.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(fundSources, key = { it.id }) { fundSource ->
            FundSourceItem(
                fundSource = fundSource,
                onEdit = { onEdit(fundSource) },
                onDelete = { onDelete(fundSource) },
            )
        }
    }
}

@Composable
private fun FundSourceItem(
    fundSource: FundSource,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = fundSource.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
private fun FundSourceDialog(
    fundSource: FundSource?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(fundSource?.name ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (fundSource != null) "Edit Fund Source" else "Add Fund Source") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Fund Source Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
