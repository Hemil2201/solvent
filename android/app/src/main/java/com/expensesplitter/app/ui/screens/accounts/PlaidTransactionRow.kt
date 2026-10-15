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
import com.expensesplitter.app.data.repository.PlaidTransaction
import com.expensesplitter.app.ui.components.CategoryIcon
import com.expensesplitter.app.ui.screens.expense.SPLIT_TYPES
import com.expensesplitter.app.ui.theme.BalanceColors
import com.expensesplitter.app.ui.theme.Spacing
import com.expensesplitter.app.ui.util.sanitizeMoneyInput

// Business-card-style compact row: the common case is a pre-filled category
// + "Personal" default and one tap on Add — not a form to fill in per
// transaction. Editing only expands into a split-values form when the user
// actually turns Shared + a non-equal split on, which is the rare path.
@Composable
fun PlaidTransactionRow(
    txn: PlaidTransaction,
    categories: List<Category>,
    users: List<CurrentUser>,
    onResolve: (
        categoryId: String?,
        isShared: Boolean,
        splitType: String?,
        splitValues: Map<String, String>?,
        confirmDuplicate: Boolean,
        onError: (String) -> Unit,
    ) -> Unit,
    onDismiss: (onError: (String) -> Unit) -> Unit,
) {
    var selectedCategoryId by remember(txn.id) { mutableStateOf(txn.matchedCategoryId) }
    var isShared by remember(txn.id) { mutableStateOf(false) }
    var splitType by remember(txn.id) { mutableStateOf("equal") }
    var splitValues by remember(txn.id) { mutableStateOf(mapOf<String, String>()) }
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
                Column(modifier = Modifier.weight(1f)) {
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
                Text(
                    txn.rawAmount ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }

            if (showDuplicateBanner) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BalanceColors.negativeLight.copy(alpha = 0.12f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Possible duplicate", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            TextButton(
                                enabled = !isSubmitting,
                                onClick = {
                                    isSubmitting = true
                                    onResolve(null, false, null, null, true) { error = it; isSubmitting = false }
                                },
                            ) { Text("Skip, it's a duplicate") }
                            TextButton(onClick = { showDuplicateBanner = false }) { Text("Not a duplicate") }
                        }
                    }
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
                FilterChip(
                    selected = isShared,
                    onClick = { isShared = !isShared },
                    label = { Text(if (isShared) "Shared" else "Personal") },
                )
            }

            if (isShared) {
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
                            onResolve(
                                selectedCategoryId,
                                isShared,
                                if (isShared) splitType else null,
                                if (isShared && splitType != "equal") splitValues else null,
                                false,
                            ) { error = it; isSubmitting = false }
                        },
                    ) { Text("Add") }
                }
            }
        }
    }
}
