package com.expensesplitter.app.ui.screens.accounts

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.expensesplitter.app.data.repository.AuthRepository
import com.expensesplitter.app.data.repository.ExpenseRepository
import com.expensesplitter.app.data.repository.PlaidRepository
import com.expensesplitter.app.ui.components.SkeletonList
import com.expensesplitter.app.ui.theme.BalanceColors
import com.expensesplitter.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionReviewScreen(
    plaidRepository: PlaidRepository,
    expenseRepository: ExpenseRepository,
    authRepository: AuthRepository,
    onOpenExpense: (String) -> Unit,
) {
    val viewModel: TransactionReviewViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TransactionReviewViewModel(plaidRepository, expenseRepository, authRepository) }
        },
    )
    val state = viewModel.state
    val currentUserId = authRepository.getSessionUser()?.id

    // A fresh pull every time this screen is reached — it's a recurring
    // inbox, not a one-shot wizard, so "whatever's new since last time"
    // should just show up without a manual tap.
    LaunchedEffect(Unit) { viewModel.sync() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Review Transactions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (!state.isLoading) {
                    Text(
                        if (state.transactions.isEmpty()) "Nothing new" else "${state.transactions.size} to review",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = viewModel::sync, enabled = !state.isSyncing) {
                if (state.isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = Spacing.xs).size(16.dp))
                } else {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs).size(18.dp))
                }
                Text(if (state.isSyncing) "Syncing…" else "Sync")
            }
        }

        state.error?.let {
            Text(
                "Couldn't load transactions: $it",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }

        Crossfade(targetState = state.isLoading, animationSpec = tween(250), label = "review-loading") { loading ->
            when {
                loading -> SkeletonList(rows = 4, modifier = Modifier.padding(Spacing.lg))
                state.isCaughtUp -> AllCaughtUpContent()
                else -> {
                    val grouped = state.transactions.groupBy { it.rawDate }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        grouped.forEach { (rawDate, txns) ->
                            stickyHeader(key = "header-$rawDate") {
                                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        formatReviewDateHeader(rawDate),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = Spacing.xs),
                                    )
                                }
                            }
                            items(txns, key = { it.id }) { txn ->
                                PlaidTransactionRow(
                                    txn = txn,
                                    categories = state.categories,
                                    users = state.users,
                                    currentUserId = currentUserId,
                                    expenseRepository = expenseRepository,
                                    onResolve = { categoryId, isShared, splitType, splitValues, confirmDuplicate, owedByOther, refundOfExpenseId, notes, onError ->
                                        viewModel.resolve(
                                            txn.id, categoryId, isShared, splitType, splitValues, confirmDuplicate, owedByOther,
                                            refundOfExpenseId, notes, onError,
                                        )
                                    },
                                    onDismiss = { onError -> viewModel.dismiss(txn.id, onError) },
                                    onOpenExpense = onOpenExpense,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllCaughtUpContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = BalanceColors.positiveLight,
                modifier = Modifier.size(56.dp),
            )
        }
        Text("You're all caught up", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "New transactions will show up here next time you sync.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val headerFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

private fun formatReviewDateHeader(rawDate: String?): String {
    if (rawDate == null) return "Unknown date"
    return try {
        val date = LocalDate.parse(rawDate)
        val today = LocalDate.now()
        when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(headerFormatter)
        }
    } catch (e: Exception) {
        rawDate
    }
}
