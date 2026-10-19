package com.expensesplitter.app.ui.screens.expense

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.expensesplitter.app.data.repository.AuthRepository
import com.expensesplitter.app.data.repository.Category
import com.expensesplitter.app.data.repository.CurrentUser
import com.expensesplitter.app.data.repository.Expense
import com.expensesplitter.app.data.repository.ExpenseFilter
import com.expensesplitter.app.data.repository.ExpenseRepository
import com.expensesplitter.app.data.repository.PendingCategoryFilterHolder
import com.expensesplitter.app.ui.components.CategoryIcon
import com.expensesplitter.app.ui.components.MerchantIcon
import com.expensesplitter.app.ui.components.EmptyState
import com.expensesplitter.app.ui.components.SkeletonList
import com.expensesplitter.app.ui.theme.BalanceColors
import com.expensesplitter.app.ui.theme.HeroShapes
import com.expensesplitter.app.ui.theme.MoneyType
import com.expensesplitter.app.ui.theme.Spacing
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ExpenseListScreen(
    expenseRepository: ExpenseRepository,
    authRepository: AuthRepository,
    pendingCategoryFilterHolder: PendingCategoryFilterHolder,
    onAddExpense: () -> Unit,
    onOpenExpense: (String) -> Unit,
    onViewDeleted: () -> Unit,
) {
    val viewModel: ExpenseListViewModel = viewModel(
        factory = viewModelFactory { initializer { ExpenseListViewModel(expenseRepository, authRepository) } },
    )
    val state = viewModel.state
    val sessionUserId = authRepository.getSessionUser()?.id
    var datePickerTarget by remember { mutableStateOf<String?>(null) } // "start" | "end" | null
    var searchText by remember { mutableStateOf(state.filter.search ?: "") }

    // Debounced — re-filtering on every keystroke would fire a request per
    // character typed.
    LaunchedEffect(searchText) {
        kotlinx.coroutines.delay(300)
        val newSearch = searchText.trim().ifBlank { null }
        if (newSearch != state.filter.search) {
            viewModel.updateFilter(state.filter.copy(search = newSearch))
        }
    }

    // Re-fetch whenever this screen comes back into view (e.g. after deleting
    // or editing an expense on the detail screen) — the ViewModel survives
    // across that round trip, so init{} alone won't pick up the change. A
    // category tapped on Home's grid also arrives here via the pending
    // holder rather than a nav argument.
    LaunchedEffect(Unit) {
        val pendingCategoryId = pendingCategoryFilterHolder.consume()
        if (pendingCategoryId != null) {
            viewModel.updateFilter(state.filter.copy(categoryId = pendingCategoryId))
        } else {
            viewModel.load()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExpense) {
                Icon(Icons.Filled.Add, contentDescription = "Add expense")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            androidx.compose.material3.OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search expenses") },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            FilterBar(
                filter = state.filter,
                categories = state.categories,
                users = state.users,
                onFilterChange = viewModel::updateFilter,
                onPickStartDate = { datePickerTarget = "start" },
                onPickEndDate = { datePickerTarget = "end" },
                onViewDeleted = onViewDeleted,
            )

            Crossfade(targetState = state.isLoading, animationSpec = tween(250), label = "expenses-loading") { loading ->
                when {
                    loading -> SkeletonList(rows = 6)
                    state.error != null -> Box(Modifier.fillMaxSize()) {
                        Text("Couldn't load expenses: ${state.error}", modifier = Modifier.padding(24.dp))
                    }
                    state.expenses.isEmpty() -> EmptyState(
                        icon = Icons.Filled.ReceiptLong,
                        title = "No expenses yet",
                        message = "Add your first expense with the + button, or try adjusting your filters.",
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> {
                        val grouped = state.expenses.groupBy { java.time.YearMonth.from(LocalDate.parse(it.date)) }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 96.dp),
                        ) {
                            grouped.forEach { (month, expenses) ->
                                stickyHeader(key = "header-$month") {
                                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                                        )
                                    }
                                }
                                items(expenses, key = { it.id }) { expense ->
                                    ExpenseRow(
                                        expense = expense,
                                        category = state.categories.find { it.id == expense.categoryId },
                                        sessionUserId = sessionUserId,
                                        onClick = { onOpenExpense(expense.id) },
                                    )
                                    Divider(modifier = Modifier.padding(horizontal = Spacing.lg))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    datePickerTarget?.let { target ->
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    val date = millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    val newFilter = if (target == "start") {
                        state.filter.copy(startDate = date?.toString())
                    } else {
                        state.filter.copy(endDate = date?.toString())
                    }
                    viewModel.updateFilter(newFilter)
                    datePickerTarget = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { datePickerTarget = null }) { Text("Cancel") } },
        ) { DatePicker(state = datePickerState) }
    }
}

// A single "Filters" entry point that opens a bottom sheet, instead of every
// possible filter dumped inline as a wall of chips (the first pass at this —
// wrapping ~15 chips — was still "the same old chip-wall pattern," just
// wrapped instead of clipped). This is the pattern Revolut/Monzo/Cash App
// use for transaction filtering: a compact bar, a sheet for the actual
// picking, and the category picker itself is a visual icon grid (reusing
// CategoryIcon, the same colored-circle language used everywhere else in
// the app) rather than another row of text pills. Active filters surface as
// small removable summary chips under the bar — so the state is visible at
// a glance without the sheet needing to stay open, but there are only ever
// as many of those as are actually active (usually 0-2), not fifteen.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(
    filter: ExpenseFilter,
    categories: List<Category>,
    users: List<CurrentUser>,
    onFilterChange: (ExpenseFilter) -> Unit,
    onPickStartDate: () -> Unit,
    onPickEndDate: () -> Unit,
    onViewDeleted: () -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    val activeCount = listOfNotNull(
        filter.startDate, filter.endDate, filter.isShared, filter.personId, filter.categoryId,
    ).size

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val filtersActive = activeCount > 0
            val filterContentColor = if (filtersActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(
                onClick = { showSheet = true },
                shape = RoundedCornerShape(50),
                color = if (filtersActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (filtersActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = filterContentColor, modifier = Modifier.size(18.dp))
                    Text("Filters", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = filterContentColor)
                    if (filtersActive) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("$activeCount", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onViewDeleted) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Recently Deleted")
            }
        }

        AnimatedVisibility(visible = activeCount > 0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                filter.startDate?.let { ActiveFilterChip(it) { onFilterChange(filter.copy(startDate = null)) } }
                filter.endDate?.let { ActiveFilterChip(it) { onFilterChange(filter.copy(endDate = null)) } }
                filter.isShared?.let { shared ->
                    ActiveFilterChip(if (shared) "Shared" else "Personal") { onFilterChange(filter.copy(isShared = null)) }
                }
                filter.personId?.let { id ->
                    val name = users.find { it.id == id }?.name ?: return@let
                    ActiveFilterChip(name) { onFilterChange(filter.copy(personId = null)) }
                }
                filter.categoryId?.let { id ->
                    val name = categories.find { it.id == id }?.name ?: return@let
                    ActiveFilterChip(name) { onFilterChange(filter.copy(categoryId = null)) }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = rememberModalBottomSheetState()) {
            FilterSheetContent(
                filter = filter,
                categories = categories,
                users = users,
                onFilterChange = onFilterChange,
                onPickStartDate = onPickStartDate,
                onPickEndDate = onPickEndDate,
                onClose = { showSheet = false },
            )
        }
    }
}

@Composable
private fun ActiveFilterChip(label: String, onRemove: () -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove filter",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(14.dp).clickable(onClick = onRemove),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheetContent(
    filter: ExpenseFilter,
    categories: List<Category>,
    users: List<CurrentUser>,
    onFilterChange: (ExpenseFilter) -> Unit,
    onPickStartDate: () -> Unit,
    onPickEndDate: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.lg)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Filters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (!filter.isEmpty) {
                TextButton(onClick = { onFilterChange(ExpenseFilter()) }) { Text("Clear all") }
            }
        }

        FilterSection("Date range") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(selected = filter.startDate != null, onClick = onPickStartDate, label = { Text(filter.startDate ?: "From") })
                FilterChip(selected = filter.endDate != null, onClick = onPickEndDate, label = { Text(filter.endDate ?: "To") })
            }
        }

        FilterSection("Type") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = filter.isShared == false,
                    onClick = { onFilterChange(filter.copy(isShared = if (filter.isShared == false) null else false)) },
                    label = { Text("Personal") },
                )
                FilterChip(
                    selected = filter.isShared == true,
                    onClick = { onFilterChange(filter.copy(isShared = if (filter.isShared == true) null else true)) },
                    label = { Text("Shared") },
                )
            }
        }

        if (users.isNotEmpty()) {
            FilterSection("Person") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    users.forEach { user ->
                        FilterChip(
                            selected = filter.personId == user.id,
                            onClick = { onFilterChange(filter.copy(personId = if (filter.personId == user.id) null else user.id)) },
                            label = { Text(user.name) },
                        )
                    }
                }
            }
        }

        if (categories.isNotEmpty()) {
            FilterSection("Category") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    categories.forEach { category ->
                        CategoryFilterTile(
                            category = category,
                            selected = filter.categoryId == category.id,
                            onClick = {
                                onFilterChange(filter.copy(categoryId = if (filter.categoryId == category.id) null else category.id))
                            },
                        )
                    }
                }
            }
        }

        androidx.compose.material3.Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun CategoryFilterTile(category: Category, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(68.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .padding(3.dp),
            ) {
                CategoryIcon(categoryName = category.name, icon = category.icon, size = 46.dp)
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                }
            }
        }
        Text(
            category.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 13.sp,
        )
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    category: com.expensesplitter.app.data.repository.Category?,
    sessionUserId: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            MerchantIcon(logoUrl = expense.merchantLogoUrl, categoryName = category?.name ?: "Other", categoryIcon = category?.icon)
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(
                    expense.description ?: "(no description)",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${expense.date} · ${category?.name ?: "Uncategorized"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (expense.hasRefunds) {
                        Text("↩ refunded", style = MaterialTheme.typography.bodySmall, color = BalanceColors.positiveLight)
                    }
                }
            }
        }

        val mySplit = expense.splits.find { it.userId == sessionUserId }?.amountOwed?.toBigDecimalOrNull()
        val fullAmount = expense.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = Spacing.sm)) {
            if (expense.isShared && mySplit != null) {
                val iPaid = expense.paidBy == sessionUserId
                val netToMe = if (iPaid) fullAmount - mySplit else -mySplit
                val label = if (netToMe >= BigDecimal.ZERO) "you get back" else "you owe"
                val color = if (netToMe >= BigDecimal.ZERO) BalanceColors.positiveLight else BalanceColors.negativeLight
                Text(
                    "${expense.currency} ${netToMe.abs()}",
                    style = MoneyType.small,
                    color = color,
                )
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("${expense.currency} ${expense.amount}", style = MoneyType.small, color = MaterialTheme.colorScheme.onBackground)
                Text("personal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    BigDecimal(this)
} catch (e: NumberFormatException) {
    null
}
