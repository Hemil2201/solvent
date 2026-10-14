package com.expensesplitter.app.di

import android.content.Context
import com.expensesplitter.app.data.local.AppDatabase
import com.expensesplitter.app.data.local.TokenStore
import com.expensesplitter.app.data.remote.ApiService
import com.expensesplitter.app.data.remote.NetworkModule
import com.expensesplitter.app.data.repository.AuthRepository
import com.expensesplitter.app.data.repository.BudgetRepository
import com.expensesplitter.app.data.repository.ExpenseRepository
import com.expensesplitter.app.data.repository.PendingCategoryFilterHolder
import com.expensesplitter.app.data.repository.PendingReceiptDraftHolder
import com.expensesplitter.app.data.repository.PlaidRepository
import com.expensesplitter.app.data.repository.ReceiptRepository
import com.expensesplitter.app.data.repository.RecurringRepository
import com.expensesplitter.app.data.repository.ReportRepository
import com.expensesplitter.app.data.repository.StatementRepository

// Manual DI container — a Hilt/Koin graph would be overkill for a 2-user app
// with this few dependencies. Reconsider only if this grows unwieldy.
class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val tokenStore = TokenStore(context)
    private val apiService: ApiService = NetworkModule.createApiService(tokenStore)

    val authRepository = AuthRepository(
        apiService = apiService,
        userDao = database.userDao(),
        tokenStore = tokenStore,
    )

    val expenseRepository = ExpenseRepository(
        apiService = apiService,
        categoryDao = database.categoryDao(),
        expenseDao = database.expenseDao(),
    )

    val budgetRepository = BudgetRepository(apiService = apiService)

    val statementRepository = StatementRepository(apiService = apiService)

    val reportRepository = ReportRepository(apiService = apiService)

    val recurringRepository = RecurringRepository(apiService = apiService)

    val receiptRepository = ReceiptRepository(apiService = apiService)

    val plaidRepository = PlaidRepository(apiService = apiService)

    val pendingReceiptDraftHolder = PendingReceiptDraftHolder()

    val pendingCategoryFilterHolder = PendingCategoryFilterHolder()
}
