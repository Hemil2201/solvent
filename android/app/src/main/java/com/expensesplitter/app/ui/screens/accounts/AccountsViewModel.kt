package com.expensesplitter.app.ui.screens.accounts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensesplitter.app.data.repository.PlaidAccount
import com.expensesplitter.app.data.repository.PlaidRepository
import kotlinx.coroutines.launch

sealed class AccountsFlowState {
    data object Loading : AccountsFlowState()
    data class Loaded(val accounts: List<PlaidAccount>) : AccountsFlowState()
    data class Error(val message: String) : AccountsFlowState()
}

class AccountsViewModel(private val plaidRepository: PlaidRepository) : ViewModel() {
    var state by mutableStateOf<AccountsFlowState>(AccountsFlowState.Loading)
        private set

    // Set once a link token is fetched; the screen observes this to launch
    // Plaid Link, then calls onLinkTokenConsumed so it doesn't relaunch on
    // recomposition.
    var pendingLinkToken by mutableStateOf<String?>(null)
        private set

    var isLinking by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = AccountsFlowState.Loading
            try {
                state = AccountsFlowState.Loaded(plaidRepository.getAccounts())
            } catch (e: Exception) {
                state = AccountsFlowState.Error(e.message ?: "Failed to load accounts")
            }
        }
    }

    fun startLink() {
        viewModelScope.launch {
            isLinking = true
            try {
                pendingLinkToken = plaidRepository.createLinkToken()
            } catch (e: Exception) {
                isLinking = false
                state = AccountsFlowState.Error(e.message ?: "Failed to start linking")
            }
        }
    }

    fun onLinkTokenConsumed() {
        pendingLinkToken = null
    }

    fun onLinkSuccess(publicToken: String, institutionId: String?, institutionName: String?) {
        viewModelScope.launch {
            try {
                plaidRepository.exchangePublicToken(publicToken, institutionId, institutionName)
                load()
            } catch (e: Exception) {
                state = AccountsFlowState.Error(e.message ?: "Failed to finish linking")
            } finally {
                isLinking = false
            }
        }
    }

    fun onLinkExit() {
        isLinking = false
    }

    fun unlink(itemId: String) {
        viewModelScope.launch {
            try {
                plaidRepository.unlinkItem(itemId)
                load()
            } catch (e: Exception) {
                state = AccountsFlowState.Error(e.message ?: "Failed to unlink")
            }
        }
    }
}
