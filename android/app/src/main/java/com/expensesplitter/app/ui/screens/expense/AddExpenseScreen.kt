package com.expensesplitter.app.ui.screens.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.text.style.TextAlign
import com.expensesplitter.app.data.repository.AuthRepository
import com.expensesplitter.app.data.repository.ExpenseRepository
import com.expensesplitter.app.data.repository.PendingReceiptDraftHolder
import com.expensesplitter.app.ui.components.CategoryIcon
import com.expensesplitter.app.ui.components.MoneyLoadingIndicator
import com.expensesplitter.app.ui.components.SuccessCheckOverlay
import com.expensesplitter.app.ui.theme.MoneyType
import com.expensesplitter.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    expenseRepository: ExpenseRepository,
    authRepository: AuthRepository,
    pendingReceiptDraftHolder: PendingReceiptDraftHolder? = null,
    expenseId: String? = null,
    onSaved: () -> Unit,
) {
    val viewModel: AddExpenseViewModel = viewModel(
        key = expenseId,
        factory = viewModelFactory {
            initializer { AddExpenseViewModel(expenseRepository, authRepository, pendingReceiptDraftHolder, expenseId) }
        },
    )
    val state = viewModel.state

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MoneyLoadingIndicator() }
        return
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            delay(650)
            onSaved()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state.receiptPhotoUrl != null) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "Receipt attached — review the fields below before saving",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                state.currency,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextField(
                value = state.amount,
                onValueChange = viewModel::updateAmount,
                placeholder = {
                    Text(
                        "0.00",
                        style = MoneyType.hero.copy(textAlign = TextAlign.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                textStyle = MoneyType.hero.copy(textAlign = TextAlign.Center),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.primary,
                    unfocusedTextColor = MaterialTheme.colorScheme.primary,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CURRENCIES.forEach { currency ->
                FilterChip(
                    selected = state.currency == currency,
                    onClick = { viewModel.selectCurrency(currency) },
                    label = { Text(currency) },
                )
            }
        }

        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::updateDescription,
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
        )

        androidx.compose.material3.AssistChip(
            onClick = { showDatePicker = true },
            label = { Text(state.date.toString()) },
            leadingIcon = {
                Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(16.dp))
            },
        )

        CategoryDropdown(
            categories = state.categories,
            selectedId = state.selectedCategoryId,
            onSelect = viewModel::selectCategory,
        )

        Text("Paid by", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            state.users.forEach { user ->
                FilterChip(
                    selected = state.paidBy == user.id,
                    onClick = { viewModel.selectPaidBy(user.id) },
                    label = { Text(user.name) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Shared expense", style = MaterialTheme.typography.labelLarge)
            Switch(checked = state.isShared, onCheckedChange = viewModel::toggleShared)
        }

        if (state.isShared) {
            Text("Split type", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SPLIT_TYPES.forEach { type ->
                    FilterChip(
                        selected = state.splitType == type,
                        onClick = { viewModel.selectSplitType(type) },
                        label = { Text(type.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            if (state.splitType != "equal") {
                val hint = when (state.splitType) {
                    "exact" -> "Dollar amount each person owes (must sum to the total)"
                    "percentage" -> "Percentage each person owes (must sum to 100)"
                    else -> "Share count for each person (e.g. 2 and 1)"
                }
                Text(hint, style = MaterialTheme.typography.bodySmall)
                state.users.forEach { user ->
                    OutlinedTextField(
                        value = state.splitValues[user.id] ?: "",
                        onValueChange = { viewModel.updateSplitValue(user.id, it) },
                        label = { Text(user.name) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = { viewModel.submit { showSuccess = true } },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.isSubmitting) "Saving…" else if (viewModel.isEditMode) "Save Changes" else "Save Expense") }
    }

        SuccessCheckOverlay(visible = showSuccess, modifier = Modifier.align(Alignment.Center))
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        viewModel.updateDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = datePickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    categories: List<com.expensesplitter.app.data.repository.Category>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.find { it.id == selectedId }
    val selectedName = selected?.name ?: "Select category"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            leadingIcon = { CategoryIcon(categoryName = selected?.name ?: "?", icon = selected?.icon, size = 28.dp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    leadingIcon = { CategoryIcon(categoryName = category.name, icon = category.icon, size = 28.dp) },
                    text = { Text(category.name) },
                    onClick = {
                        onSelect(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
