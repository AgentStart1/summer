package com.example.summerapp.ui.addbalance

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.summerapp.data.DefaultDataRepository
import com.example.summerapp.data.db.SummerDatabase
import com.example.summerapp.data.llmd.LlmdImageAnalyzer
import com.example.summerapp.data.llmd.LlmdServiceConnection
import com.example.summerapp.data.llmd.DataStoreLlmdTargetSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBalanceChangeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddBalanceChangeViewModel = viewModel(
        factory = AddBalanceChangeViewModel.Factory(
            DefaultDataRepository(SummerDatabase.getInstance(LocalContext.current.applicationContext)),
            LlmdImageAnalyzer(
                LocalContext.current.applicationContext,
                LlmdServiceConnection(LocalContext.current.applicationContext),
            ),
            DataStoreLlmdTargetSettings(LocalContext.current.applicationContext).selectedTarget,
        )
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBack by rememberUpdatedState(onBack)

    val authorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onAuthorizationResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    AddBalanceChangeEffect.Saved -> currentOnBack()
                    is AddBalanceChangeEffect.RequestAuthorization -> {
                        authorizationLauncher.launch(
                            Intent(LlmdServiceConnection.ACTION_AUTHORIZE_CALLER)
                                .setPackage(effect.target.packageName)
                                .putExtra(
                                    LlmdServiceConnection.EXTRA_CALLER_PACKAGE,
                                    context.packageName,
                                ),
                        )
                    }
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let { viewModel.extractBalanceFromImage(it.toString()) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Add Balance Change") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Button(
                    onClick = viewModel::saveBalanceChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(52.dp),
                    enabled = state.selectedFundSource != null &&
                        state.balance.isNotBlank() &&
                        !state.isImageAnalyzing &&
                        !state.isSaving,
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (state.isSaving) "Saving..." else "Save Balance Change")
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Record a snapshot", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Choose an account and enter its latest balance.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FormSection(
                icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                title = "Fund Source",
            ) {
                if (state.fundSources.isEmpty()) {
                    Text(
                        "Add a fund source before recording a balance.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.fundSources) { fundSource ->
                            FilterChip(
                                selected = state.selectedFundSource?.id == fundSource.id,
                                onClick = { viewModel.selectFundSource(fundSource) },
                                label = { Text(fundSource.name) },
                            )
                        }
                    }
                }
            }

            FormSection(
                icon = { Icon(Icons.Default.ImageSearch, contentDescription = null) },
                title = "Balance",
            ) {
                OutlinedTextField(
                    value = state.balance,
                    onValueChange = { viewModel.updateBalance(it) },
                    label = { Text("New Balance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("¥") },
                    supportingText = { Text("Enter a negative value if the account is overdrawn.") },
                )

                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isImageAnalyzing,
                ) {
                    if (state.isImageAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyzing Image...")
                    } else {
                        Icon(Icons.Default.ImageSearch, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import from Image")
                    }
                }
            }

            FormSection(
                icon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                title = "Details",
            ) {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { viewModel.updateNote(it) },
                    label = { Text("Note (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                )
            }

            val errorMessage = state.errorMessage
            if (errorMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun FormSection(
    icon: @Composable () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = androidx.compose.ui.Alignment.Center) { icon() }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
