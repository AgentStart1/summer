package com.example.summerapp.ui.addbalance

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.summerapp.data.DataRepository
import com.example.summerapp.data.DefaultDataRepository
import com.example.summerapp.data.db.SummerDatabase
import com.example.summerapp.data.db.entity.FundSource
import com.example.summerapp.data.llmd.LlmdServiceConnection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBalanceChangeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddBalanceChangeViewModel = viewModel(
        factory = AddBalanceChangeViewModel.Factory(
            LocalContext.current.applicationContext as android.app.Application,
            DefaultDataRepository(SummerDatabase.getInstance(LocalContext.current.applicationContext)),
            LlmdServiceConnection(LocalContext.current),
        )
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fundSources by viewModel.fundSources.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let { viewModel.extractBalanceFromImage(it) }
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Fund Source Selection
            Text(
                text = "Fund Source",
                style = MaterialTheme.typography.titleMedium,
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(fundSources) { fundSource ->
                    FilterChip(
                        selected = state.selectedFundSource?.id == fundSource.id,
                        onClick = { viewModel.selectFundSource(fundSource) },
                        label = { Text(fundSource.name) },
                    )
                }
            }

            // Balance Input
            OutlinedTextField(
                value = state.balance,
                onValueChange = { viewModel.updateBalance(it) },
                label = { Text("New Balance") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Image Import Button
            Button(
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
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import from Image")
                }
            }

            // Note Input
            OutlinedTextField(
                value = state.note,
                onValueChange = { viewModel.updateNote(it) },
                label = { Text("Note (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            // Error Message
            val errorMessage = state.errorMessage
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save Button
            Button(
                onClick = { viewModel.saveBalanceChange(onBack) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.selectedFundSource != null && state.balance.isNotBlank(),
            ) {
                Text("Save Balance Change")
            }
        }
    }
}
