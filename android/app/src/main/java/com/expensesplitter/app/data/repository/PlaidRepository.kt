package com.expensesplitter.app.data.repository

import com.expensesplitter.app.data.remote.ApiService
import com.expensesplitter.app.data.remote.dto.ExchangePublicTokenRequestDto

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
)

class PlaidRepository(private val apiService: ApiService) {
    suspend fun createLinkToken(): String = apiService.createPlaidLinkToken().link_token

    suspend fun exchangePublicToken(publicToken: String, institutionId: String?, institutionName: String?): List<PlaidAccount> =
        apiService
            .exchangePlaidPublicToken(ExchangePublicTokenRequestDto(publicToken, institutionId, institutionName))
            .map { it.toDomain() }

    suspend fun getAccounts(): List<PlaidAccount> = apiService.getPlaidAccounts().map { it.toDomain() }

    suspend fun unlinkItem(itemId: String) = apiService.unlinkPlaidItem(itemId)
}

private fun com.expensesplitter.app.data.remote.dto.PlaidAccountDto.toDomain() = PlaidAccount(
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
)
