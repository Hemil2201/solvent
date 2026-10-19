package com.expensesplitter.app.ui.screens.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensesplitter.app.data.repository.ActivityItem
import com.expensesplitter.app.data.repository.ExpenseRepository
import com.expensesplitter.app.data.repository.PlaidRepository
import com.expensesplitter.app.data.repository.UserBalance
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val balances: List<UserBalance> = emptyList(),
    val activity: List<ActivityItem> = emptyList(),
    val reviewCount: Int = 0,
)

class DashboardViewModel(
    private val expenseRepository: ExpenseRepository,
    private val plaidRepository: PlaidRepository,
) : ViewModel() {
    var state by mutableStateOf(DashboardUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val balances = try {
                expenseRepository.getBalance()
            } catch (e: Exception) {
                emptyList()
            }
            val activity = try {
                expenseRepository.getActivity()
            } catch (e: Exception) {
                emptyList()
            }
            // No accounts linked yet (or a transient error) both just mean
            // "nothing to review" — the banner simply doesn't show, same as
            // the empty-queue case once accounts are linked.
            val reviewCount = try {
                plaidRepository.getTransactions().size
            } catch (e: Exception) {
                0
            }
            state = DashboardUiState(isLoading = false, balances = balances, activity = activity, reviewCount = reviewCount)
        }
    }
}
