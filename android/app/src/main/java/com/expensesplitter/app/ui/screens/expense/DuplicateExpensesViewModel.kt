package com.expensesplitter.app.ui.screens.expense

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensesplitter.app.data.repository.ExpenseRef
import com.expensesplitter.app.data.repository.ExpenseRepository
import kotlinx.coroutines.launch

data class DuplicateExpensesUiState(
    val isLoading: Boolean = true,
    val groups: List<List<ExpenseRef>> = emptyList(),
    val error: String? = null,
)

class DuplicateExpensesViewModel(private val expenseRepository: ExpenseRepository) : ViewModel() {
    var state by mutableStateOf(DuplicateExpensesUiState())
        private set

    init {
        load()
    }

    fun load() {
        state = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            state = try {
                state.copy(isLoading = false, groups = expenseRepository.getDuplicateGroups())
            } catch (e: Exception) {
                state.copy(isLoading = false, error = e.message ?: "Failed to load")
            }
        }
    }
}
