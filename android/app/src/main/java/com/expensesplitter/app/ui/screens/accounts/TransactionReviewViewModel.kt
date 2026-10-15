package com.expensesplitter.app.ui.screens.accounts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensesplitter.app.data.repository.Category
import com.expensesplitter.app.data.repository.CurrentUser
import com.expensesplitter.app.data.repository.AuthRepository
import com.expensesplitter.app.data.repository.ExpenseRepository
import com.expensesplitter.app.data.repository.PlaidRepository
import com.expensesplitter.app.data.repository.PlaidSyncSummary
import com.expensesplitter.app.data.repository.PlaidTransaction
import kotlinx.coroutines.launch

data class ReviewUiState(
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val transactions: List<PlaidTransaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val users: List<CurrentUser> = emptyList(),
    val error: String? = null,
    val lastSync: PlaidSyncSummary? = null,
) {
    // "Caught up" only once a load has actually completed with an empty
    // queue — isLoading guards the brief empty-list flash before the first
    // fetch returns.
    val isCaughtUp: Boolean get() = !isLoading && transactions.isEmpty() && error == null
}

class TransactionReviewViewModel(
    private val plaidRepository: PlaidRepository,
    private val expenseRepository: ExpenseRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    var state by mutableStateOf(ReviewUiState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = state.copy(isLoading = true, error = null)
            try {
                val transactions = plaidRepository.getTransactions()
                val categories = expenseRepository.getCategories()
                val users = authRepository.getLoginUsers()
                state = state.copy(isLoading = false, transactions = transactions, categories = categories, users = users)
            } catch (e: Exception) {
                state = state.copy(isLoading = false, error = e.message ?: "Failed to load transactions")
            }
        }
    }

    fun sync() {
        viewModelScope.launch {
            state = state.copy(isSyncing = true, error = null)
            try {
                val summary = plaidRepository.sync()
                val transactions = plaidRepository.getTransactions()
                state = state.copy(isSyncing = false, transactions = transactions, lastSync = summary)
            } catch (e: Exception) {
                state = state.copy(isSyncing = false, error = e.message ?: "Sync failed")
            }
        }
    }

    fun resolve(
        transactionId: String,
        categoryId: String?,
        isShared: Boolean,
        splitType: String?,
        splitValues: Map<String, String>?,
        confirmDuplicate: Boolean,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                plaidRepository.resolveTransaction(transactionId, categoryId, isShared, splitType, splitValues, confirmDuplicate)
                state = state.copy(transactions = state.transactions.filter { it.id != transactionId })
            } catch (e: Exception) {
                onError(e.message ?: "Failed to add expense")
            }
        }
    }

    fun dismiss(transactionId: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                plaidRepository.dismissTransaction(transactionId)
                state = state.copy(transactions = state.transactions.filter { it.id != transactionId })
            } catch (e: Exception) {
                onError(e.message ?: "Failed to skip transaction")
            }
        }
    }
}
