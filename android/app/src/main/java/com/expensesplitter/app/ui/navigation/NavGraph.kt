package com.expensesplitter.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.expensesplitter.app.di.AppContainer
import com.expensesplitter.app.ui.screens.activity.ActivityScreen
import com.expensesplitter.app.ui.screens.budgets.BudgetsScreen
import com.expensesplitter.app.ui.screens.dashboard.DashboardScreen
import com.expensesplitter.app.ui.screens.expense.AddExpenseScreen
import com.expensesplitter.app.ui.screens.expense.DeletedExpensesScreen
import com.expensesplitter.app.ui.screens.expense.ExpenseDetailScreen
import com.expensesplitter.app.ui.screens.expense.ExpenseListScreen
import com.expensesplitter.app.ui.screens.insights.InsightsScreen
import com.expensesplitter.app.ui.screens.login.LoginScreen
import com.expensesplitter.app.ui.screens.receipt.ReceiptScanScreen
import com.expensesplitter.app.ui.screens.recurring.RecurringExpensesScreen
import com.expensesplitter.app.ui.screens.settings.SettingsScreen
import com.expensesplitter.app.ui.screens.statement.StatementUploadScreen

// Titles for screens reached by pushing (not a bottom-tab root) — shown in
// the top bar alongside the back arrow. Tab roots render their own in-body
// heading instead and need neither a title bar nor a back arrow.
private val PUSHED_SCREEN_TITLES = mapOf(
    Screen.AddExpense.route to "Add Expense",
    Screen.EditExpense.route to "Edit Expense",
    Screen.ReceiptScan.route to "Scan Receipt",
    Screen.ExpenseDetail.route to "Expense Detail",
    Screen.DeletedExpenses.route to "Recently Deleted",
    Screen.StatementUpload.route to "Upload Statement",
    Screen.Recurring.route to "Recurring Expenses",
    Screen.Activity.route to "Activity",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseSplitterNavGraph(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    val startDestination = if (container.authRepository.hasSession()) Screen.Dashboard.route else Screen.Login.route
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    val isTabRoot = BottomNavItems.any { item -> currentRoute?.hierarchy?.any { it.route == item.route } == true }
    val isLogin = currentRoute?.hierarchy?.any { it.route == Screen.Login.route } == true
    val pushedTitle = PUSHED_SCREEN_TITLES.entries.find { (route, _) ->
        currentRoute?.hierarchy?.any { it.route == route } == true
    }?.value

    Scaffold(
        topBar = {
            if (pushedTitle != null) {
                CenterAlignedTopAppBar(
                    title = { Text(pushedTitle) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (currentRoute?.route == Screen.ExpenseDetail.route) {
                            val expenseId = backStackEntry?.arguments?.getString("expenseId")
                            if (expenseId != null) {
                                IconButton(onClick = { navController.navigate(Screen.EditExpense.buildRoute(expenseId)) }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit expense")
                                }
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!isLogin) {
                NavigationBar {
                    BottomNavItems.forEach { item ->
                        val selected = currentRoute?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                // Re-selecting a tab should pop back to that
                                // tab's own root, not just no-op while stuck
                                // on a detail screen pushed on top of it
                                // (e.g. Expenses -> ExpenseDetail -> tap
                                // Expenses again). popBackStack(route,
                                // inclusive = false) does exactly that when
                                // the route is already somewhere in the back
                                // stack; it returns false when it isn't
                                // (switching in from a different tab), so
                                // fall back to the normal
                                // save/restore-state tab-switch for that case.
                                val poppedToRoot = navController.popBackStack(item.route, false)
                                if (!poppedToRoot) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = {
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false,
                                )
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (isTabRoot && currentRoute?.hierarchy?.any { it.route == Screen.Dashboard.route } == true) {
                FloatingActionButton(onClick = { navController.navigate(Screen.AddExpense.route) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add expense")
                }
            }
        },
    ) { innerPadding ->
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.padding(innerPadding),
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                authRepository = container.authRepository,
                onLoggedIn = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                authRepository = container.authRepository,
                expenseRepository = container.expenseRepository,
                onUploadStatement = { navController.navigate(Screen.StatementUpload.route) },
                onScanReceipt = { navController.navigate(Screen.ReceiptScan.route) },
                onViewRecurring = { navController.navigate(Screen.Recurring.route) },
                onViewActivity = { navController.navigate(Screen.Activity.route) },
                onOpenCategory = { categoryId ->
                    container.pendingCategoryFilterHolder.categoryId = categoryId
                    navController.navigate(Screen.ExpenseList.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable(Screen.AddExpense.route) {
            AddExpenseScreen(
                expenseRepository = container.expenseRepository,
                authRepository = container.authRepository,
                pendingReceiptDraftHolder = container.pendingReceiptDraftHolder,
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            Screen.EditExpense.route,
            arguments = listOf(navArgument("expenseId") { type = NavType.StringType }),
        ) { backStackEntry ->
            AddExpenseScreen(
                expenseRepository = container.expenseRepository,
                authRepository = container.authRepository,
                expenseId = backStackEntry.arguments?.getString("expenseId"),
                onSaved = { navController.popBackStack() },
            )
        }
        composable(Screen.ReceiptScan.route) {
            ReceiptScanScreen(
                receiptRepository = container.receiptRepository,
                pendingReceiptDraftHolder = container.pendingReceiptDraftHolder,
                onScanned = {
                    navController.navigate(Screen.AddExpense.route) {
                        popUpTo(Screen.ReceiptScan.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.ExpenseList.route) {
            ExpenseListScreen(
                expenseRepository = container.expenseRepository,
                authRepository = container.authRepository,
                pendingCategoryFilterHolder = container.pendingCategoryFilterHolder,
                onAddExpense = { navController.navigate(Screen.AddExpense.route) },
                onOpenExpense = { id -> navController.navigate(Screen.ExpenseDetail.buildRoute(id)) },
                onViewDeleted = { navController.navigate(Screen.DeletedExpenses.route) },
            )
        }
        composable(
            Screen.ExpenseDetail.route,
            arguments = listOf(navArgument("expenseId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getString("expenseId").orEmpty()
            ExpenseDetailScreen(
                expenseId = expenseId,
                expenseRepository = container.expenseRepository,
                authRepository = container.authRepository,
                onDeleted = { navController.popBackStack() },
            )
        }
        composable(Screen.DeletedExpenses.route) {
            DeletedExpensesScreen(expenseRepository = container.expenseRepository)
        }
        composable(Screen.StatementUpload.route) {
            StatementUploadScreen(
                statementRepository = container.statementRepository,
                expenseRepository = container.expenseRepository,
                authRepository = container.authRepository,
                onDone = { navController.popBackStack() },
            )
        }
        composable(Screen.Budgets.route) { BudgetsScreen(budgetRepository = container.budgetRepository) }
        composable(Screen.Insights.route) {
            InsightsScreen(reportRepository = container.reportRepository, budgetRepository = container.budgetRepository)
        }
        composable(Screen.Activity.route) {
            ActivityScreen(expenseRepository = container.expenseRepository)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                authRepository = container.authRepository,
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Recurring.route) {
            RecurringExpensesScreen(
                recurringRepository = container.recurringRepository,
                expenseRepository = container.expenseRepository,
                authRepository = container.authRepository,
            )
        }
    }
    }
}
