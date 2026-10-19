package com.expensesplitter.app.data.repository

import com.expensesplitter.app.data.remote.ApiService
import com.expensesplitter.app.data.remote.dto.ExchangePublicTokenRequestDto
import com.expensesplitter.app.data.remote.dto.PlaidAccountDto
import com.expensesplitter.app.data.remote.dto.PlaidTransactionDto
import com.expensesplitter.app.data.remote.dto.ResolvePlaidTransactionRequestDto
import com.expensesplitter.app.data.remote.dto.SplitInputDto

data class PlaidAccount(
    val id: String,
    val itemId: String,
    val name: String,
    val officialName: String?,
    val mask: String?,
    val type: String?,
    val subtype: String?,
    val currentBalance: String?,
    val availableBalance: String?,
    val currencyCode: String?,
    val institutionName: String?,
    val institutionColor: String?,
    val institutionLogo: String?,
)

data class PlaidSyncSummary(val added: Int, val modified: Int, val removed: Int)

data class PlaidTransaction(
    val id: String,
    val accountName: String,
    val institutionName: String?,
    val rawDate: String?,
    val rawDescription: String?,
    val rawMerchantName: String?,
    val rawAmount: String?,
    val pending: Boolean,
    val logoUrl: String?,
    val matchedCategoryId: String?,
    val needsClarification: Boolean,
    val isDuplicateOf: String?,
    val resolvedExpenseId: String?,
    val suggestedIsShared: Boolean?,
    val suggestedSplitType: String?,
    val suggestedOwedByOther: Boolean,
    val suggestedSplitValues: Map<String, String>?,
    val suggestionSampleCount: Int?,
) {
    // Falls back through merchant name -> raw description -> a placeholder,
    // same precedence used server-side when an expense is created from this.
    val displayName: String get() = rawMerchantName ?: rawDescription ?: "(no description)"

    // True whenever there's history to show a "you've done this before" hint
    // for — including a prior Personal choice, not just a Shared one.
    val hasSuggestion: Boolean get() = suggestionSampleCount != null
}

class PlaidRepository(private val apiService: ApiService) {
    suspend fun createLinkToken(): String = apiService.createPlaidLinkToken().link_token

    suspend fun exchangePublicToken(publicToken: String, institutionId: String?, institutionName: String?): List<PlaidAccount> =
        apiService
            .exchangePlaidPublicToken(ExchangePublicTokenRequestDto(publicToken, institutionId, institutionName))
            .map { it.toDomain() }

    suspend fun getAccounts(): List<PlaidAccount> = apiService.getPlaidAccounts().map { it.toDomain() }

    suspend fun unlinkItem(itemId: String) = apiService.unlinkPlaidItem(itemId)

    suspend fun sync(): PlaidSyncSummary {
        val dto = apiService.syncPlaidTransactions()
        return PlaidSyncSummary(dto.added, dto.modified, dto.removed)
    }

    suspend fun getTransactions(unresolvedOnly: Boolean = true): List<PlaidTransaction> =
        apiService.getPlaidTransactions(unresolvedOnly).map { it.toDomain() }

    suspend fun resolveTransaction(
        transactionId: String,
        categoryId: String?,
        isShared: Boolean,
        splitType: String?,
        splitValues: Map<String, String>?,
        confirmDuplicate: Boolean,
        owedByOther: Boolean = false,
    ): PlaidTransaction {
        val splits = splitValues?.map { (userId, value) -> SplitInputDto(userId, value) }
        val body = ResolvePlaidTransactionRequestDto(
            category_id = categoryId,
            is_shared = isShared,
            split_type = splitType,
            splits = splits,
            confirm_duplicate = confirmDuplicate,
            owed_by_other = owedByOther,
        )
        return apiService.resolvePlaidTransaction(transactionId, body).toDomain()
    }

    suspend fun dismissTransaction(transactionId: String): PlaidTransaction =
        apiService.dismissPlaidTransaction(transactionId).toDomain()
}

private fun PlaidAccountDto.toDomain() = PlaidAccount(
    id = id,
    itemId = item_id,
    name = name,
    officialName = official_name,
    mask = mask,
    type = type,
    subtype = subtype,
    currentBalance = current_balance,
    availableBalance = available_balance,
    currencyCode = iso_currency_code,
    institutionName = institution_name,
    institutionColor = institution_color,
    institutionLogo = institution_logo,
)

private fun PlaidTransactionDto.toDomain() = PlaidTransaction(
    id = id,
    accountName = account_name,
    institutionName = institution_name,
    logoUrl = logo_url,
    rawDate = raw_date,
    rawDescription = raw_description,
    rawMerchantName = raw_merchant_name,
    rawAmount = raw_amount,
    pending = pending,
    matchedCategoryId = matched_category_id,
    needsClarification = needs_clarification,
    isDuplicateOf = is_duplicate_of,
    resolvedExpenseId = resolved_expense_id,
    suggestedIsShared = suggested_is_shared,
    suggestedSplitType = suggested_split_type,
    suggestedOwedByOther = suggested_owed_by_other,
    suggestedSplitValues = suggested_split_values,
    suggestionSampleCount = suggestion_sample_count,
)
