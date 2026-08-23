package com.example.summerapp.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.summerapp.data.DataRepository
import com.example.summerapp.data.db.entity.BalanceChange
import com.example.summerapp.data.db.entity.FundSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onAddBalanceChange: () -> Unit,
    onManageFundSources: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = viewModel(
        factory = FeedViewModel.Factory(
            com.example.summerapp.data.db.SummerDatabase.getInstance(
                androidx.compose.ui.platform.LocalContext.current.applicationContext
            ).let { com.example.summerapp.data.DefaultDataRepository(it) }
        )
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Summer Finance") },
                actions = {
                    IconButton(onClick = onManageFundSources) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage Fund Sources")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddBalanceChange) {
                Icon(Icons.Default.Add, contentDescription = "Add Balance Change")
            }
        },
    ) { paddingValues ->
        when (val s = state) {
            FeedUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
            is FeedUiState.Success -> {
                FeedContent(
                    balanceChanges = s.balanceChanges,
                    fundSources = s.fundSources,
                    modifier = Modifier.padding(paddingValues),
                )
            }
            is FeedUiState.Error -> {
                Text(
                    text = "Error: ${s.message}",
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}

@Composable
private fun FeedContent(
    balanceChanges: List<BalanceChange>,
    fundSources: Map<Long, FundSource>,
    modifier: Modifier = Modifier,
) {
    if (balanceChanges.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No balance changes yet.\nTap + to add one.",
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
        items(balanceChanges, key = { it.id }) { balanceChange ->
            BalanceChangeCard(
                balanceChange = balanceChange,
                fundSource = fundSources[balanceChange.fundSourceId],
            )
        }
    }
}

@Composable
private fun BalanceChangeCard(
    balanceChange: BalanceChange,
    fundSource: FundSource?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = fundSource?.name ?: "Unknown Fund",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = formatBalance(balanceChange.newBalance),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (balanceChange.previousBalance != null) {
                Text(
                    text = "Previous: ${formatBalance(balanceChange.previousBalance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatDate(balanceChange.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!balanceChange.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = balanceChange.note,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun formatBalance(balance: Double): String {
    return "¥%.2f".format(balance)
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
