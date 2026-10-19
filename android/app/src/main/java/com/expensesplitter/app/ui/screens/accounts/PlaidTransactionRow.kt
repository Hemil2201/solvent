package com.expensesplitter.app.ui.screens.accounts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expensesplitter.app.data.repository.Category
import com.expensesplitter.app.data.repository.CurrentUser
import com.expensesplitter.app.data.repository.Expense
import com.expensesplitter.app.data.repository.ExpenseRepository
import com.expensesplitter.app.data.repository.PlaidTransaction
import com.expensesplitter.app.ui.components.CategoryIcon
import com.expensesplitter.app.ui.components.MerchantIcon
import com.expensesplitter.app.ui.screens.expense.SPLIT_TYPES
import com.expensesplitter.app.ui.theme.BalanceColors
import com.expensesplitter.app.ui.theme.Spacing
import com.expensesplitter.app.ui.util.sanitizeMoneyInput

// Who the expense belongs to. A single chip whose label just swapped between
// "Personal"/"Shared" didn't show there was a choice at all — three chips
// shown together (matching the SPLIT_TYPES row pattern below) make every
// option visible up front instead of something you only discover by tapping.
private enum class ShareMode { PERSONAL, SHARED, OWED_BY_OTHER }

// Business-card-style compact row: the common case is a pre-filled category
// + "Personal" default and one tap on Add — not a form to fill in per
// transaction. Editing only expands into a split-values form when the user
// actually turns Shared + a non-equal split on, which is the rare path.
@Composable
fun PlaidTransactionRow(
    txn: PlaidTransaction,
    categories: List<Category>,
    users: List<CurrentUser>,
    currentUserId: String?,
    expenseRepository: ExpenseRepository,
    onResolve: (
        categoryId: String?,
        isShared: Boolean,
        splitType: String?,
        splitValues: Map<String, String>?,
        confirmDuplicate: Boolean,
        owedByOther: Boolean,
        refundOfExpenseId: String?,
        notes: String?,
        onError: (String) -> Unit,
    ) -> Unit,
    onDismiss: (onError: (String) -> Unit) -> Unit,
    onOpenExpense: (String) -> Unit,
) {
    val otherUser = users.firstOrNull { it.id != currentUserId }
    // Plaid's sign convention: negative = money in, i.e. a refund/return
    // rather than a purchase.
    val isRefund = (txn.rawAmount?.toDoubleOrNull() ?: 0.0) < 0
    var linkedExpense by remember(txn.id) { mutableStateOf<Expense?>(null) }
    var showRefundPicker by remember { mutableStateOf(false) }
    var showNoteField by remember(txn.id) { mutableStateOf(false) }
    var noteText by remember(txn.id) { mutableStateOf("") }

    // Pre-fill from this merchant/category's history (same user's own past
    // choices, nothing guessed) instead of always defaulting to Personal —
    // the common case becomes zero taps instead of one.
    val initialShareMode = when {
        txn.suggestedOwedByOther -> ShareMode.OWED_BY_OTHER
        txn.suggestedIsShared == true -> ShareMode.SHARED
        else -> ShareMode.PERSONAL
    }

    var selectedCategoryId by remember(txn.id) { mutableStateOf(txn.matchedCategoryId) }
    var shareMode by remember(txn.id) { mutableStateOf(initialShareMode) }
    var splitType by remember(txn.id) { mutableStateOf(txn.suggestedSplitType ?: "equal") }
    var splitValues by remember(txn.id) { mutableStateOf(txn.suggestedSplitValues ?: mapOf()) }
    // Starts true for a flagged duplicate so the banner shows immediately;
    // "Not a duplicate" just dismisses the banner locally and falls through
    // to the normal Add flow below — it doesn't change what Add sends.
    var showDuplicateBanner by remember(txn.id) { mutableStateOf(txn.isDuplicateOf != null) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val selectedCategory = categories.find { it.id == selectedCategoryId }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MerchantIcon(
                        logoUrl = txn.logoUrl,
                        categoryName = selectedCategory?.name ?: "?",
                        categoryIcon = selectedCategory?.icon,
                        size = 36.dp,
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(
                                txn.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (txn.pending) {
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                    Text(
                                        "Pending",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            "${txn.accountName}${txn.institutionName?.let { " · $it" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    txn.rawAmount ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isRefund) BalanceColors.positiveLight else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }

            if (showDuplicateBanner) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BalanceColors.negativeLight.copy(alpha = 0.12f),
                ) {
                    // Label gets its own line rather than sharing a row with
                    // the buttons — squeezed next to "Skip, it's a
                    // duplicate" + "Not a duplicate" on a phone-width screen,
                    // the buttons' Text had so little width left that Compose
                    // wrapped it one character per line.
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text("Possible duplicate", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            TextButton(
                                enabled = !isSubmitting,
                                onClick = {
                                    isSubmitting = true
                                    onResolve(null, false, null, null, true, false, null, null) { error = it; isSubmitting = false }
                                },
                            ) { Text("Skip, it's a duplicate") }
                            TextButton(onClick = { showDuplicateBanner = false }) { Text("Not a duplicate") }
                        }
                    }
                }
            }

            // Broader, informational cousin of the exact-amount banner above
            // (same merchant within a day, any amount) — a heads-up, not a
            // forced merge, since the two amounts differing might mean two
            // genuinely separate purchases. The backend never sends both at
            // once, but the null-check here is the real guard either way.
            txn.possibleDuplicate?.let { dup ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BalanceColors.warningLight.copy(alpha = 0.14f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "⚠ You already have ${dup.description ?: "an expense"} · $${dup.amount} on ${dup.date}",
                            style = MaterialTheme.typography.labelMedium,
                            color = BalanceColors.warningLight,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onOpenExpense(dup.id) }) { Text("View it") }
                    }
                }
            }

            if (txn.hasSuggestion) {
                Text(
                    "You've usually handled this one this way" +
                        (txn.suggestionSampleCount?.takeIf { it > 1 }?.let { " ($it times)" } ?: "") + " — pre-filled below.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (isRefund) {
                val linked = linkedExpense
                if (linked == null) {
                    AssistChip(
                        onClick = { showRefundPicker = true },
                        label = { Text("Link to original purchase") },
                    )
                } else {
                    AssistChip(
                        onClick = { linkedExpense = null },
                        label = { Text("Returning: ${linked.description ?: "expense"} · $${linked.amount}") },
                        trailingIcon = { Text("✕", style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Box {
                    AssistChip(
                        onClick = { categoryMenuExpanded = true },
                        leadingIcon = { CategoryIcon(categoryName = selectedCategory?.name ?: "?", icon = selectedCategory?.icon, size = 20.dp) },
                        label = { Text(selectedCategory?.name ?: "Category") },
                    )
                    DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                leadingIcon = { CategoryIcon(categoryName = category.name, icon = category.icon, size = 24.dp) },
                                text = { Text(category.name) },
                                onClick = { selectedCategoryId = category.id; categoryMenuExpanded = false },
                            )
                        }
                    }
                }
            }

            // Not every transaction needs one, so it starts collapsed behind
            // a chip — but a quick way to tell apart "which of 5 Amazon
            // orders was this" is worth one tap to reach.
            if (showNoteField || noteText.isNotBlank()) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note") },
                    placeholder = { Text("e.g. keyboard order") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                AssistChip(onClick = { showNoteField = true }, label = { Text("Add a note") })
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FilterChip(
                    selected = shareMode == ShareMode.PERSONAL,
                    onClick = { shareMode = ShareMode.PERSONAL },
                    label = { Text("Personal") },
                )
                FilterChip(
                    selected = shareMode == ShareMode.SHARED,
                    onClick = { shareMode = ShareMode.SHARED },
                    label = { Text("Shared") },
                )
                if (otherUser != null) {
                    FilterChip(
                        selected = shareMode == ShareMode.OWED_BY_OTHER,
                        onClick = { shareMode = ShareMode.OWED_BY_OTHER },
                        label = { Text("Owed by ${otherUser.name}") },
                    )
                }
            }

            if (shareMode == ShareMode.OWED_BY_OTHER && otherUser != null) {
                Text(
                    "${otherUser.name} owes the full ${txn.rawAmount ?: "amount"} — you paid, nothing split your way.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (shareMode == ShareMode.SHARED) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    SPLIT_TYPES.forEach { type ->
                        FilterChip(
                            selected = splitType == type,
                            onClick = { splitType = type; splitValues = emptyMap() },
                            label = { Text(type.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                if (splitType != "equal") {
                    users.forEach { user ->
                        OutlinedTextField(
                            value = splitValues[user.id] ?: "",
                            onValueChange = { splitValues = splitValues + (user.id to sanitizeMoneyInput(it)) },
                            label = { Text(user.name) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = !isSubmitting,
                    onClick = { isSubmitting = true; onDismiss { error = it; isSubmitting = false } },
                ) { Text("Skip") }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Button(
                        enabled = !isSubmitting,
                        onClick = {
                            if (selectedCategoryId == null) {
                                error = "Pick a category first"
                                return@Button
                            }
                            isSubmitting = true
                            val isShared = shareMode != ShareMode.PERSONAL
                            val resolvedSplitType = when (shareMode) {
                                ShareMode.PERSONAL -> null
                                ShareMode.SHARED -> splitType
                                ShareMode.OWED_BY_OTHER -> "exact"
                            }
                            val resolvedSplitValues = when {
                                shareMode == ShareMode.SHARED && splitType != "equal" -> splitValues
                                shareMode == ShareMode.OWED_BY_OTHER && otherUser != null && currentUserId != null ->
                                    mapOf(otherUser.id to (txn.rawAmount ?: "0"), currentUserId to "0")
                                else -> null
                            }
                            onResolve(
                                selectedCategoryId,
                                isShared,
                                resolvedSplitType,
                                resolvedSplitValues,
                                false,
                                shareMode == ShareMode.OWED_BY_OTHER,
                                linkedExpense?.id,
                                noteText.trim().ifBlank { null },
                            ) { error = it; isSubmitting = false }
                        },
                    ) { Text("Add") }
                }
            }
        }
    }

    if (showRefundPicker) {
        RefundPickerDialog(
            merchantName = txn.displayName,
            expenseRepository = expenseRepository,
            onDismiss = { showRefundPicker = false },
            onPick = { expense -> linkedExpense = expense; showRefundPicker = false },
        )
    }
}
