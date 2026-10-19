package com.expensesplitter.app.ui.screens.budgets

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.expensesplitter.app.data.repository.BudgetRepository
import com.expensesplitter.app.data.repository.CategoryBudgetSummary
import com.expensesplitter.app.ui.components.AccentCard
import com.expensesplitter.app.ui.components.CategoryIcon
import com.expensesplitter.app.ui.components.EmptyState
import com.expensesplitter.app.ui.components.MonthPager
import com.expensesplitter.app.ui.components.SkeletonList
import com.expensesplitter.app.ui.theme.BalanceColors
import com.expensesplitter.app.ui.theme.Spacing
import com.expensesplitter.app.ui.theme.categoryColorFor
import com.expensesplitter.app.ui.util.sanitizeMoneyInput

private data class EditingTarget(
    val userId: String?,
    val categoryId: String,
    val label: String,
    val currentValue: String,
)

@Composable
fun BudgetsScreen(budgetRepository: BudgetRepository) {
    val viewModel: BudgetsViewModel = viewModel(
        factory = viewModelFactory { initializer { BudgetsViewModel(budgetRepository) } },
    )
    val state = viewModel.state
    var editing by remember { mutableStateOf<EditingTarget?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    // Only categories/lines with a target actually set are shown — this
    // screen is a tracker for budgets you've configured, not a checklist of
    // every category. Setting a new one goes through the "+" instead.
    val categoriesWithTargets = state.categories.mapNotNull { category ->
        val personalWithTarget = category.personal.filter { it.targetAmount != null }
        val groupHasTarget = category.group.targetAmount != null
        if (personalWithTarget.isEmpty() && !groupHasTarget) {
            null
        } else {
            category to personalWithTarget
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add budget target")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Budgets",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = Spacing.lg, top = Spacing.lg, end = Spacing.lg),
            )
            MonthPager(
                month = state.month,
                year = state.year,
                onChange = viewModel::changeMonth,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )

            Crossfade(targetState = state.isLoading, animationSpec = tween(250), label = "budgets-loading") { loading ->
                when {
                    loading -> SkeletonList(rows = 4)
                    state.error != null -> Box(Modifier.fillMaxSize()) {
                        Text("Couldn't load budgets: ${state.error}", modifier = Modifier.padding(24.dp))
                    }
                    categoriesWithTargets.isEmpty() -> EmptyState(
                        icon = Icons.Filled.Savings,
                        title = "No budget targets yet",
                        message = "Tap the + button to set a monthly target for a category.",
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        items(categoriesWithTargets) { (category, personalWithTarget) ->
                            AccentCard(accent = categoryColorFor(category.categoryName)) {
                                CategoryBudgetSection(category, personalWithTarget) { userId, label, current ->
                                    editing = EditingTarget(userId, category.categoryId, label, current)
                                }
                            }
                        }
                        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 80.dp)) }
                    }
                }
            }
        }
    }

    editing?.let { target ->
        EditTargetDialog(
            target = target,
            onDismiss = { editing = null },
            onSave = { amount ->
                viewModel.setBudget(target.userId, target.categoryId, amount) { editing = null }
            },
        )
    }

    if (showAdd) {
        AddTargetDialog(
            categories = state.categories,
            onDismiss = { showAdd = false },
            onPick = { categoryId, userId, label ->
                showAdd = false
                editing = EditingTarget(userId, categoryId, label, "")
            },
        )
    }
}

@Composable
private fun CategoryBudgetSection(
    category: CategoryBudgetSummary,
    personalWithTarget: List<com.expensesplitter.app.data.repository.PersonalBudgetLine>,
    onEditTarget: (userId: String?, label: String, currentValue: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(categoryName = category.categoryName, icon = null, size = 32.dp)
            Text(
                category.categoryName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }

        personalWithTarget.forEach { line ->
            BudgetProgressRow(
                label = line.name,
                target = line.targetAmount,
                actual = line.actualSpend,
                onClick = { onEditTarget(line.userId, line.name, line.targetAmount ?: "") },
            )
        }
        if (category.group.targetAmount != null) {
            BudgetProgressRow(
                label = "Group",
                target = category.group.targetAmount,
                actual = category.group.actualSpend,
                onClick = { onEditTarget(null, "Group", category.group.targetAmount ?: "") },
            )
        }
    }
}

// 80% is the usual "heads up, you're getting close" threshold — early
// enough to actually adjust spending for the rest of the month, not just a
// moment before going over.
private const val NEAR_LIMIT_THRESHOLD = 0.8

@Composable
private fun BudgetProgressRow(label: String, target: String?, actual: String, onClick: () -> Unit) {
    val targetValue = target?.toDoubleOrNull()
    val actualValue = actual.toDoubleOrNull() ?: 0.0
    val isOverBudget = targetValue != null && targetValue > 0 && actualValue > targetValue
    val fraction = if (targetValue != null && targetValue > 0) (actualValue / targetValue).toFloat() else 0f
    val isNearLimit = !isOverBudget && fraction >= NEAR_LIMIT_THRESHOLD
    val stateColor = when {
        isOverBudget -> BalanceColors.negativeLight
        isNearLimit -> BalanceColors.warningLight
        else -> BalanceColors.positiveLight
    }

    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$$actual / $$target",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isOverBudget || isNearLimit) stateColor else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (targetValue != null && targetValue > 0) {
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs).height(8.dp),
                color = stateColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            if (isOverBudget) {
                Text(
                    "Over by $${"%.2f".format(actualValue - targetValue)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = stateColor,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            } else if (isNearLimit) {
                Text(
                    "Getting close",
                    style = MaterialTheme.typography.labelSmall,
                    color = stateColor,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun EditTargetDialog(target: EditingTarget, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(target.currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${target.label} target") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = sanitizeMoneyInput(it) },
                label = { Text("Monthly target") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, autoCorrectEnabled = false),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTargetDialog(
    categories: List<CategoryBudgetSummary>,
    onDismiss: () -> Unit,
    onPick: (categoryId: String, userId: String?, label: String) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Pair<String?, String>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add budget target") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                    OutlinedTextField(
                        value = selectedCategory?.categoryName ?: "Select category",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.categoryName) },
                                onClick = { selectedCategory = category; target = null; categoryMenuExpanded = false },
                            )
                        }
                    }
                }

                selectedCategory?.let { category ->
                    Text("For", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        category.personal.forEach { line ->
                            FilterChip(
                                selected = target?.first == line.userId,
                                onClick = { target = line.userId to line.name },
                                label = { Text(line.name) },
                            )
                        }
                        FilterChip(
                            selected = target?.first == null && target != null,
                            onClick = { target = null to "Group" },
                            label = { Text("Group") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val category = selectedCategory ?: return@TextButton
                    val (userId, label) = target ?: return@TextButton
                    onPick(category.categoryId, userId, label)
                },
                enabled = selectedCategory != null && target != null,
            ) { Text("Next") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
