package com.example.summerapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.summerapp.data.db.SummerDatabase
import com.example.summerapp.data.db.entity.BalanceChange
import com.example.summerapp.data.db.entity.FundSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FinanceJourneyTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val database by lazy {
        SummerDatabase.getInstance(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Before
    fun setup() {
        clearDatabase()
    }

    @After
    fun teardown() {
        clearDatabase()
    }

    @Test
    fun addFundSourceAndNegativeBalance_appearsInFeed() {
        composeTestRule.onNodeWithText("Summer Finance").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithContentDescription("Add Fund Source").performClick()
        composeTestRule.onNodeWithText("Fund Source Name").performTextInput(TEST_FUND_SOURCE)
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.onNodeWithText(TEST_FUND_SOURCE).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithContentDescription("Add Balance Change").performClick()
        composeTestRule.onNodeWithText(TEST_FUND_SOURCE).performClick()
        composeTestRule.onNodeWithText("New Balance").performTextInput("-245.70")
        composeTestRule.onNodeWithText("Save Balance Change").performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Summer Finance").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Summer Finance").assertIsDisplayed()
        val storedBalance = runBlocking {
            withContext(Dispatchers.IO) {
                database.balanceChangeDao().getAll().first().single().newBalance
            }
        }
        assertEquals(-245.70, storedBalance, 0.0)
    }

    @Test
    fun balanceTimeline_showsSnapshotsAndImpactRecords() {
        val now = System.currentTimeMillis()
        runBlocking {
            withContext(Dispatchers.IO) {
                val bankId = database.fundSourceDao().insert(
                    FundSource(name = "Timeline Bank", createdAt = now, updatedAt = now)
                )
                val walletId = database.fundSourceDao().insert(
                    FundSource(name = "Timeline Wallet", createdAt = now + 1, updatedAt = now + 1)
                )
                database.balanceChangeDao().insert(
                    BalanceChange(
                        fundSourceId = walletId,
                        newBalance = 500.0,
                        note = "Opening cash",
                        timestamp = now + 1_000,
                    )
                )
                database.balanceChangeDao().insert(
                    BalanceChange(
                        fundSourceId = bankId,
                        newBalance = 8_000.0,
                        note = "Salary income",
                        timestamp = now + 2_000,
                    )
                )
                database.balanceChangeDao().insert(
                    BalanceChange(
                        fundSourceId = walletId,
                        newBalance = 380.0,
                        previousBalance = 500.0,
                        note = "Bought groceries",
                        timestamp = now + 3_000,
                    )
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("¥8,380.00").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("¥8,380.00").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Bought groceries").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("−¥120.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Salary income").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("+¥8,000.00").assertIsDisplayed()
    }

    private fun clearDatabase() = runBlocking {
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }

    companion object {
        private const val TEST_FUND_SOURCE = "Instrumentation Wallet"
    }
}
