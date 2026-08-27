package com.example.summerapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
                    modifier = Modifier,
                )
            }
            entry<FundSources> {
                FundSourcesScreen(
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier,
                )
            }
            entry<AddBalanceChange> {
                AddBalanceChangeScreen(
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier,
                )
            }
        },
    )
}
