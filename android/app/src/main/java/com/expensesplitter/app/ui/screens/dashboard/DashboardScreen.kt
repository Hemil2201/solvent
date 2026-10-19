package com.expensesplitter.app.ui.screens.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.expensesplitter.app.ui.components.ActionRow
import com.expensesplitter.app.ui.components.BalanceHeroCard
import com.expensesplitter.app.ui.components.SkeletonBalanceCard
import com.expensesplitter.app.ui.components.SkeletonList
import com.expensesplitter.app.ui.theme.HeroShapes
import com.expensesplitter.app.ui.theme.Spacing

@Composable
fun DashboardScreen(
    authRepository: AuthRepository,
    expenseRepository: ExpenseRepository,
    plaidRepository: PlaidRepository,
    onUploadStatement: () -> Unit,
    onScanReceipt: () -> Unit,
    onViewRecurring: () -> Unit,
    onViewActivity: () -> Unit,
    onViewAccounts: () -> Unit,
    onReviewTransactions: () -> Unit,
) {
    val viewModel: DashboardViewModel = viewModel(
        factory = viewModelFactory { initializer { DashboardViewModel(expenseRepository, plaidRepository) } },
    )
    val state = viewModel.state
    val sessionUser = authRepository.getSessionUser()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg)
            .padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = "Hi, ${sessionUser?.name ?: "there"}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Crossfade(targetState = state.isLoading, animationSpec = tween(250), label = "dashboard-loading") { loading ->
            if (loading) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    SkeletonBalanceCard()
                    SkeletonList(rows = 4)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    BalanceHeroCard(sessionUserName = sessionUser?.name, balances = state.balances)

                    if (state.reviewCount > 0) {
                        ReviewBanner(count = state.reviewCount, onClick = onReviewTransactions)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            "More",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
                                ActionRow(label = "Upload Statement", icon = Icons.Filled.UploadFile, onClick = onUploadStatement)
                                Divider()
                                ActionRow(label = "Scan a Receipt", icon = Icons.Filled.CameraAlt, onClick = onScanReceipt)
                                Divider()
                                ActionRow(label = "Recurring Expenses", icon = Icons.Filled.Autorenew, onClick = onViewRecurring)
                                Divider()
                                ActionRow(label = "View Activity", icon = Icons.Filled.History, onClick = onViewActivity)
                                Divider()
                                ActionRow(label = "Linked Accounts", icon = Icons.Filled.AccountBalance, onClick = onViewAccounts)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Prominent, not a buried list row — this is the app's most-used daily
// task once a bank is linked, so it gets its own highlighted banner right
// under the balance instead of competing for attention with one-off
// utilities like "Upload Statement".
@Composable
private fun ReviewBanner(count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = HeroShapes.heroCard,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(Icons.Filled.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Column {
                    Text(
                        "$count transaction${if (count == 1) "" else "s"} to review",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "From your linked accounts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
