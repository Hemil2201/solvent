package com.expensesplitter.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SplitInputDto(val user_id: String, val value: String)

@Serializable
data class ExpenseCreateDto(
    val amount: String,
    val currency: String = "USD",
    val date: String,
    val description: String? = null,
    val notes: String? = null,
    val category_id: String? = null,
    val paid_by: String,
    val is_shared: Boolean = false,
    val split_type: String? = null,
    val splits: List<SplitInputDto>? = null,
    val receipt_photo_url: String? = null,
)

@Serializable
data class ExpenseSplitDto(val user_id: String, val split_type: String, val amount_owed: String)

// Lightweight pointer to another expense — used for a refund's children
// and its parent.
@Serializable
data class ExpenseRefDto(val id: String, val description: String? = null, val amount: String, val date: String)

@Serializable
data class ExpenseDto(
    val id: String,
    val amount: String,
    val currency: String,
    val date: String,
    val description: String? = null,
    val notes: String? = null,
    val category_id: String? = null,
    val paid_by: String,
    val is_shared: Boolean,
    val receipt_photo_url: String? = null,
    val merchant_logo_url: String? = null,
    val refund_of_expense_id: String? = null,
    val has_refunds: Boolean = false,
    val source: String,
    val created_by: String,
    val created_at: String,
    val updated_at: String,
    val deleted_at: String? = null,
    val splits: List<ExpenseSplitDto> = emptyList(),
    // Only present on the single-expense GET response — absent (defaults
    // empty/null) from list/create/update responses.
    val refunds: List<ExpenseRefDto> = emptyList(),
    val refund_of: ExpenseRefDto? = null,
)

@Serializable
data class DuplicateGroupDto(val expenses: List<ExpenseRefDto>)

@Serializable
data class ExpenseUpdateDto(
    val amount: String? = null,
    val currency: String? = null,
    val date: String? = null,
    val description: String? = null,
    val notes: String? = null,
    val category_id: String? = null,
    val paid_by: String? = null,
    val is_shared: Boolean? = null,
    val split_type: String? = null,
    val splits: List<SplitInputDto>? = null,
)

@Serializable
data class ExpenseCommentCreateDto(val comment: String)

@Serializable
data class ExpenseCommentDto(val id: String, val expense_id: String, val user_id: String, val comment: String, val created_at: String)

@Serializable
data class ExpenseEditHistoryDto(
    val id: String,
    val expense_id: String,
    val edited_by: String,
    val field_changed: String,
    val old_value: String? = null,
    val new_value: String? = null,
    val edited_at: String,
)
