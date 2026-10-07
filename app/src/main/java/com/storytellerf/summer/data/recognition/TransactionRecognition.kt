package com.storytellerf.summer.data.recognition

import java.security.MessageDigest
import java.util.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class RecognizedTransaction(val timestamp: Long?, val amount: Double, val note: String?, val transactionId: String? = null, val currency: String? = null)
data class RecognizedTransactions(val imageHash: String, val records: List<RecognizedTransaction>, val imagePath: String? = null)

@Serializable
private data class TransactionPayload(val transactions: List<TransactionValue>)
@Serializable
private data class TransactionValue(val timestamp: String?, val amount: Double, val note: String?, val transactionId: String?, val currency: String?)

internal fun parseTransactions(content: String, timeZone: TimeZone = TimeZone.getDefault(), currency: String? = null): List<RecognizedTransaction> {
    return try {
        val trimmed = content.trim()
        val json = Regex("\\A```(?:json)?\\s*\\n([\\s\\S]*?)\\n```\\z", RegexOption.IGNORE_CASE)
            .matchEntire(trimmed)?.groupValues?.get(1) ?: trimmed
        val values = Json.decodeFromString<TransactionPayload>(json).transactions
        require(values.size in 1..100)
        values.map { value ->
            val timestamp = value.timestamp?.let { requireNotNull(parseLocalDateTime(it, timeZone)) }
            require(value.amount.isFinite())
            val recognizedCurrency = value.currency?.uppercase(java.util.Locale.ROOT)
            require(currency == null || recognizedCurrency == null || recognizedCurrency == currency)
            RecognizedTransaction(timestamp, value.amount, value.note?.trim()?.takeIf(String::isNotEmpty), value.transactionId?.trim()?.takeIf(String::isNotEmpty), recognizedCurrency)
        }
    } catch (error: Exception) {
        throw InvalidTransactionResponseException()
    }
}

internal fun imageHash(jpeg: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(jpeg).joinToString("") { "%02x".format(it.toInt() and 0xff) }

class InvalidTransactionResponseException : Exception(
    "No valid transactions were recognized. Use a screenshot with readable amounts and income/expense direction."
)

internal const val TRANSACTION_EXTRACTION_PROMPT = """
Read completed transactions from the screenshot, not balances, credit limits or summary totals.
Return only {"transactions":[{"timestamp":null,"amount":-12.50,"note":"merchant","transactionId":null,"currency":null}]}.
amount: signed numeric value; expenses/payments/支出/付款 are negative, income/refunds/收入/退款
are positive. Use visible signs or direction labels; do not guess amounts or directions.
currency: visible ISO currency code or null when undetermined. A missing currency symbol is
allowed. Symbols such as $ and ¥ are ambiguous; never guess their ISO currency or convert amounts.
timestamp: full local yyyy-MM-ddTHH:mm:ss, or null for incomplete/relative/unclear dates.
A full date without a time uses 00:00:00. 今天/昨天/TODAY/YESTERDAY, a clock time or month/day
without a year do not establish a full date. Keep that transaction with timestamp null for review;
never invent a year or use the current date. note: visible merchant/description, otherwise null.
transactionId: complete visible original order/transaction/reference ID, otherwise null.
Masked, truncated or blurred IDs are null. Never invent IDs or use a balance/date as an ID.
Each row includes all five fields. Preserve visible order. Skip explicitly pending/failed rows
and totals. Return {"transactions":[]} only when no readable transactions qualify.
No markdown or explanations.
"""

internal const val TRANSACTION_RESPONSE_SCHEMA = """
{"type":"object","properties":{"transactions":{"type":"array","items":{
"type":"object","properties":{"timestamp":{"type":["string","null"]},"amount":{"type":"number"},"note":{"type":["string","null"]},"transactionId":{"type":["string","null"]},"currency":{"type":["string","null"]}},
"required":["timestamp","amount","note","transactionId","currency"],"additionalProperties":false}}},"required":["transactions"],"additionalProperties":false}
"""

internal fun transactionsPrompt(currency: String?): String = TRANSACTION_EXTRACTION_PROMPT +
    (currency?.let { "\nThe selected account currency is $it. Read this currency only; skip explicitly different currencies. Keep rows with undetermined currency for review." } ?: "")
