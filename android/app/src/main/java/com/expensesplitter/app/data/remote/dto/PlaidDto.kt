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
    val institution_color: String? = null,
    val institution_logo: String? = null,
)

@Serializable
data class SyncSummaryDto(val added: Int, val modified: Int, val removed: Int)

@Serializable
data class PlaidTransactionDto(
    val id: String,
    val account_name: String,
    val institution_name: String? = null,
    val raw_date: String? = null,
    val raw_description: String? = null,
    val raw_merchant_name: String? = null,
    val raw_amount: String? = null,
    val pending: Boolean,
    val logo_url: String? = null,
    val matched_category_id: String? = null,
    val needs_clarification: Boolean,
    val is_duplicate_of: String? = null,
    val resolved_expense_id: String? = null,
    val dismissed_at: String? = null,
    val suggested_is_shared: Boolean? = null,
    val suggested_split_type: String? = null,
    val suggested_owed_by_other: Boolean = false,
    val suggested_split_values: Map<String, String>? = null,
    val suggestion_sample_count: Int? = null,
)

@Serializable
data class ResolvePlaidTransactionRequestDto(
    val category_id: String? = null,
    val paid_by: String? = null,
    val is_shared: Boolean = false,
    val split_type: String? = null,
    val splits: List<SplitInputDto>? = null,
    val confirm_duplicate: Boolean = false,
    val owed_by_other: Boolean = false,
    val refund_of_expense_id: String? = null,
    val notes: String? = null,
)
