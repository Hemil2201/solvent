package com.expensesplitter.app.ui.screens.expense

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensesplitter.app.data.remote.dto.ExpenseCreateDto
import com.expensesplitter.app.data.remote.dto.ExpenseUpdateDto
import com.expensesplitter.app.data.remote.dto.SplitInputDto
import com.expensesplitter.app.data.repository.AuthRepository
import com.expensesplitter.app.data.repository.Category
import com.expensesplitter.app.data.repository.CurrentUser
import com.expensesplitter.app.data.repository.ExpenseRepository
import com.expensesplitter.app.data.repository.PendingReceiptDraftHolder
import com.expensesplitter.app.ui.util.sanitizeMoneyInput
import java.time.LocalDate
import kotlinx.coroutines.launch
import retrofit2.HttpException

val SPLIT_TYPES = listOf("equal", "exact", "percentage", "shares")
val CURRENCIES = listOf("USD", "EUR", "GBP", "CAD", "INR")

data class AddExpenseFormState(
    val amount: String = "",
    val currency: String = "USD",
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val users: List<CurrentUser> = emptyList(),
    val paidBy: String? = null,
    val isShared: Boolean = false,
    val splitType: String = "equal",
    val splitValues: Map<String, String> = emptyMap(),
    val receiptPhotoUrl: String? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

class AddExpenseViewModel(
    private val expenseRepository: ExpenseRepository,
    private val authRepository: AuthRepository,
    private val pendingReceiptDraftHolder: PendingReceiptDraftHolder? = null,
    private val expenseId: String? = null,
) : ViewModel() {
    val isEditMode: Boolean = expenseId != null

    var state by mutableStateOf(AddExpenseFormState())
        private set

    init {
        viewModelScope.launch {
            try {
                val categories = expenseRepository.getCategories()
                val users = authRepository.getLoginUsers()
                val sessionUserId = authRepository.getSessionUser()?.id

                if (expenseId != null) {
                    // Editing an existing expense — load its current values
                    // instead of the blank-form / receipt-draft prefill path.
                    val existing = expenseRepository.getExpense(expenseId)
                    state = state.copy(
                        categories = categories,
                        selectedCategoryId = existing.categoryId ?: categories.firstOrNull()?.id,
                        users = users,
                        amount = existing.amount,
                        currency = existing.currency,
                        description = existing.description.orEmpty(),
                        date = runCatching { LocalDate.parse(existing.date) }.getOrDefault(LocalDate.now()),
                        paidBy = existing.paidBy,
                        isShared = existing.isShared,
                        splitType = existing.splits.firstOrNull()?.splitType ?: "equal",
                        // The original % / shares input isn't stored server-side,
                        // only the resulting owed amount — that's the best
                        // available prefill for any split type.
                        splitValues = existing.splits.associate { it.userId to it.amountOwed },
                        receiptPhotoUrl = existing.receiptPhotoUrl,
                        isLoading = false,
                    )
                    return@launch
                }

                // A receipt scan (see ReceiptScanScreen) leaves its parsed
                // fields here for this form to pick up and prefill, once.
                val draft = pendingReceiptDraftHolder?.consume()

                state = state.copy(
                    categories = categories,
                    selectedCategoryId = draft?.categoryId ?: categories.firstOrNull()?.id,
                    users = users,
                    paidBy = sessionUserId ?: users.firstOrNull()?.id,
                    amount = draft?.amount ?: state.amount,
                    description = draft?.description ?: state.description,
                    date = draft?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: state.date,
                    receiptPhotoUrl = draft?.receiptPhotoUrl,
                    isLoading = false,
                )
            } catch (e: Exception) {
                state = state.copy(isLoading = false, error = e.message ?: "Failed to load form data")
            }
        }
    }

    // The field only hints a numeric keyboard (KeyboardType.Decimal) — that
    // doesn't actually block other input (paste, IME auto-suggest, adb/test
    // input, some third-party keyboards all bypass it), so letters could
    // slip into an amount meant to be currency. Filter here instead: only
    // digits and a single decimal point, at most 2 digits after it.
    fun updateAmount(value: String) { state = state.copy(amount = sanitizeMoneyInput(value)) }
    fun selectCurrency(value: String) { state = state.copy(currency = value) }
    fun updateDescription(value: String) { state = state.copy(description = value) }
    fun updateDate(value: LocalDate) { state = state.copy(date = value) }
    fun selectCategory(id: String) { state = state.copy(selectedCategoryId = id) }
    fun selectPaidBy(userId: String) { state = state.copy(paidBy = userId) }
    fun toggleShared(shared: Boolean) { state = state.copy(isShared = shared) }
    fun selectSplitType(type: String) { state = state.copy(splitType = type, splitValues = emptyMap()) }
    fun updateSplitValue(userId: String, value: String) {
        state = state.copy(splitValues = state.splitValues + (userId to sanitizeMoneyInput(value)))
    }

    fun submit(onSuccess: () -> Unit) {
        val amount = state.amount.toBigDecimalOrNull()
        if (amount == null) {
            state = state.copy(error = "Enter a valid amount")
            return
        }
        val paidBy = state.paidBy ?: return

        val splits = if (state.isShared && state.splitType != "equal") {
            state.users.map { user ->
                SplitInputDto(user_id = user.id, value = state.splitValues[user.id] ?: "0")
            }
        } else null

        state = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            try {
                if (expenseId != null) {
                    expenseRepository.updateExpense(
                        expenseId,
                        ExpenseUpdateDto(
                            amount = amount.toPlainString(),
                            currency = state.currency,
                            date = state.date.toString(),
                            description = state.description.ifBlank { null },
                            category_id = state.selectedCategoryId,
                            paid_by = paidBy,
                            is_shared = state.isShared,
                            split_type = if (state.isShared) state.splitType else null,
                            splits = splits,
                        ),
                    )
                } else {
                    expenseRepository.createExpense(
                        ExpenseCreateDto(
                            amount = amount.toPlainString(),
                            currency = state.currency,
                            date = state.date.toString(),
                            description = state.description.ifBlank { null },
                            category_id = state.selectedCategoryId,
                            paid_by = paidBy,
                            is_shared = state.isShared,
                            split_type = if (state.isShared) state.splitType else null,
                            splits = splits,
                            receipt_photo_url = state.receiptPhotoUrl,
                        ),
                    )
                }
                state = state.copy(isSubmitting = false, success = true)
                onSuccess()
            } catch (e: HttpException) {
                val detail = e.response()?.errorBody()?.string()
                state = state.copy(isSubmitting = false, error = detail ?: e.message())
            } catch (e: Exception) {
                state = state.copy(isSubmitting = false, error = e.message ?: "Failed to create expense")
            }
        }
    }
}
