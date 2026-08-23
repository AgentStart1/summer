package com.example.summerapp

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.summerapp.ui.addbalance.AddBalanceChangeScreen
import com.example.summerapp.ui.feed.FeedScreen
import com.example.summerapp.ui.fundsources.FundSourcesScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                FeedScreen(
                    onAddBalanceChange = { backStack.add(AddBalanceChange) },
                    onManageFundSources = { backStack.add(FundSources) },
                    modifier = Modifier.safeDrawingPadding().padding(16.dp),
                )
            }
            entry<FundSources> {
                FundSourcesScreen(
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding().padding(16.dp),
                )
            }
            entry<AddBalanceChange> {
                AddBalanceChangeScreen(
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding().padding(16.dp),
                )
            }
        },
    )
}
