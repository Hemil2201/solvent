package com.expensesplitter.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LinkTokenResponseDto(val link_token: String)

@Serializable
data class ExchangePublicTokenRequestDto(
    val public_token: String,
    val institution_id: String? = null,
    val institution_name: String? = null,
)

@Serializable
data class PlaidAccountDto(
    val id: String,
    val item_id: String,
    val name: String,
    val official_name: String? = null,
    val mask: String? = null,
    val type: String? = null,
    val subtype: String? = null,
    val current_balance: String? = null,
    val available_balance: String? = null,
    val iso_currency_code: String? = null,
    val institution_name: String? = null,
)
