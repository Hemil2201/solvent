package com.expensesplitter.app.ui.screens.expense

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.expensesplitter.app.data.repository.ExpenseRef
import com.expensesplitter.app.data.repository.ExpenseRepository
import com.expensesplitter.app.ui.components.AccentCard
import com.expensesplitter.app.ui.components.EmptyState
import com.expensesplitter.app.ui.theme.BalanceColors
import com.expensesplitter.app.ui.theme.MoneyType
import com.expensesplitter.app.ui.theme.Spacing

// On-demand cleanup scan, distinct from the Review Transactions warning —
// that one only catches new transactions going forward, this one finds
// groups among expenses that are already sitting in the data.
@Composable
fun DuplicateExpensesScreen(expenseRepository: ExpenseRepository, onOpenExpense: (String) -> Unit) {
    val viewModel: DuplicateExpensesViewModel = viewModel(
        factory = viewModelFactory { initializer { DuplicateExpensesViewModel(expenseRepository) } },
    )
    val state = viewModel.state

    when {
        state.isLoading -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.padding(24.dp)) }
        state.error != null -> Box(Modifier.fillMaxSize()) {
            Text("Couldn't load: ${state.error}", modifier = Modifier.padding(24.dp))
        }
        state.groups.isEmpty() -> EmptyState(
            icon = Icons.Filled.Warning,
            title = "No duplicates found",
            message = "Nothing in your expenses looks like a same-day, same-merchant duplicate.",
            modifier = Modifier.fillMaxSize(),
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(state.groups, key = { group -> group.joinToString { it.id } }) { group ->
                DuplicateGroupCard(group, onOpenExpense)
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(group: List<ExpenseRef>, onOpenExpense: (String) -> Unit) {
    AccentCard(accent = BalanceColors.warningLight) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                group.first().description ?: "Expense",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            group.forEach { ref ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenExpense(ref.id) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ref.date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$${ref.amount}", style = MoneyType.small)
                }
            }
            Text(
                "Tap one to open it and delete if it's a mistake.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
