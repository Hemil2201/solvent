package com.expensesplitter.app.ui.screens.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expensesplitter.app.data.repository.Expense
import com.expensesplitter.app.data.repository.ExpenseRepository
import com.expensesplitter.app.ui.components.MerchantIcon
import com.expensesplitter.app.ui.theme.MoneyType
import com.expensesplitter.app.ui.theme.Spacing

// Lets the user optionally connect a refund to the specific earlier expense
// it's returning — purely for display/traceability, the split math doesn't
// depend on it. No backend search endpoint exists (or is needed): this
// fetches the full expense list once and filters client-side, which is
// plenty for a 2-person app's expense volume.
@Composable
fun RefundPickerDialog(
    merchantName: String,
    expenseRepository: ExpenseRepository,
    onDismiss: () -> Unit,
    onPick: (Expense) -> Unit,
) {
    // Bank transaction descriptions often carry a store-number suffix
    // ("PRIMARK GRAPEVINE 092") that a resolved expense's own description
    // ("Primark") won't contain — seeding the search with just the first
    // word keeps the default results non-empty for the common case.
    var query by remember { mutableStateOf(merchantName.trim().substringBefore(' ')) }
    var allExpenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            allExpenses = expenseRepository.getExpenses()
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load expenses"
        }
        isLoading = false
    }

    val matches = remember(query, allExpenses) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            emptyList()
        } else {
            allExpenses
                .filter { it.description?.contains(needle, ignoreCase = true) == true }
                .sortedByDescending { it.date }
                .take(20)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link to original purchase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    isLoading -> CircularProgressIndicator(modifier = Modifier.padding(Spacing.md))
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    matches.isEmpty() -> Text(
                        "No matching expenses found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(matches, key = { it.id }) { expense ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onPick(expense) }.padding(vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                MerchantIcon(
                                    logoUrl = expense.merchantLogoUrl,
                                    categoryName = expense.description ?: "?",
                                    categoryIcon = null,
                                    size = 32.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        expense.description ?: "(no description)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        expense.date,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("$${expense.amount}", style = MoneyType.small)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
