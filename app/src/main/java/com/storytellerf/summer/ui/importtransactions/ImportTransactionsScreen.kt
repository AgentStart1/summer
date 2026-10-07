package com.storytellerf.summer.ui.importtransactions

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storytellerf.summer.data.DefaultDataRepository
import com.storytellerf.summer.data.db.SummerDatabase
import com.storytellerf.summer.data.llmd.DataStoreLlmdTargetSettings
import com.storytellerf.summer.data.llmd.LlmdServiceConnection
import com.storytellerf.summer.data.recognition.configuredImageAnalyzer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportTransactionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportTransactionsViewModel = viewModel(factory = ImportTransactionsViewModel.Factory(
        DefaultDataRepository(SummerDatabase.getInstance(LocalContext.current.applicationContext)),
        configuredImageAnalyzer(LocalContext.current.applicationContext),
        DataStoreLlmdTargetSettings(LocalContext.current.applicationContext).selectedTarget,
    )),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentOnBack by rememberUpdatedState(onBack)
    val authorization = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onAuthorizationResult(it.resultCode == Activity.RESULT_OK)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.recognize(it.toString()) }
    }
    LaunchedEffect(viewModel, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect -> withContext(Dispatchers.Main) { when (effect) {
                is ImportTransactionsEffect.Saved -> {
                    Toast.makeText(context, "Imported ${effect.count} transactions", Toast.LENGTH_SHORT).show()
                    currentOnBack()
                }
                is ImportTransactionsEffect.Authorize -> authorization.launch(
                    Intent(LlmdServiceConnection.ACTION_AUTHORIZE_CALLER).setPackage(effect.target.packageName)
                        .putExtra(LlmdServiceConnection.EXTRA_CALLER_PACKAGE, context.packageName)
                )
            } } }
        }
    }
    val editable = !state.isSaving && !state.isAnalyzing
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text("Import Transactions") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }, bottomBar = {
        Surface {
            Button(onClick = viewModel::save, enabled = editable && state.fundSourceId != null && state.rows.any { it.selected },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Text(if (state.isSaving) "Importing..." else "Import selected transactions")
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Import a transaction screenshot", style = MaterialTheme.typography.titleLarge)
                Text("Choose an account, then review dates, signed CNY amounts and descriptions before importing. Expenses are negative, income is positive.")
            }
            item {
                if (state.fundSources.isEmpty()) Text("Add a fund source in Settings first.")
                state.fundSources.forEach { source ->
                    FilterChip(selected = state.fundSourceId == source.id, enabled = editable,
                        onClick = { viewModel.selectFundSource(source) }, label = { Text(source.name) })
                }
                OutlinedButton(onClick = { picker.launch("image/*") }, enabled = editable) { Text("Choose Screenshot") }
                if (state.isAnalyzing) CircularProgressIndicator()
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            itemsIndexed(state.rows, key = { _, row -> row.imageRow }) { index, row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Checkbox(checked = row.selected, enabled = editable, onCheckedChange = { viewModel.updateRow(index, row.copy(selected = it)) })
                            Text("Transaction ${index + 1}", modifier = Modifier.padding(top = 12.dp))
                        }
                        OutlinedTextField(value = row.date, enabled = editable, onValueChange = { viewModel.updateRow(index, row.copy(date = it)) },
                            label = { Text("Local date and time") }, supportingText = { Text(if (row.date.isBlank()) "Enter the verified local date and time: yyyy-MM-ddTHH:mm:ss" else "yyyy-MM-ddTHH:mm:ss") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = row.amount, enabled = editable, onValueChange = { viewModel.updateRow(index, row.copy(amount = it)) },
                            label = { Text("Signed amount (CNY)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = row.transactionId, enabled = editable, onValueChange = { viewModel.updateRow(index, row.copy(transactionId = it)) },
                            label = { Text("Original transaction ID") }, supportingText = {
                                Text(if (row.transactionId.isBlank()) "ID not visible: only repeat imports of this screenshot can be detected." else "Used to detect duplicates across screenshots for this account.")
                            }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = row.note, enabled = editable, onValueChange = { viewModel.updateRow(index, row.copy(note = it)) },
                            label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
