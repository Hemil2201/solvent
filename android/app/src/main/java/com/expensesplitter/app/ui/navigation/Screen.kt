package com.expensesplitter.app.ui.navigation

// Routes for the screen map in project-plan/05_SCREENS.md. Only Login is
// implemented for Stage 2 (the round-trip proof); the rest are placeholders
// wired up as their vertical slices land (see project-plan/06_ROADMAP.md).
sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
    data object AddExpense : Screen("add_expense")
    data object ExpenseList : Screen("expense_list")
    data object ExpenseDetail : Screen("expense_detail/{expenseId}") {
        fun buildRoute(expenseId: String) = "expense_detail/$expenseId"
    }
    data object EditExpense : Screen("edit_expense/{expenseId}") {
        fun buildRoute(expenseId: String) = "edit_expense/$expenseId"
    }
    data object DeletedExpenses : Screen("deleted_expenses")
    data object StatementUpload : Screen("statement_upload")
    data object Budgets : Screen("budgets")
    data object Settings : Screen("settings")
    data object Recurring : Screen("recurring")
    data object ReceiptScan : Screen("receipt_scan")
    data object Activity : Screen("activity")
    // "Dashboard" is the user-facing name for this tab; the Kotlin object is
    // named Insights to avoid clashing with Screen.Dashboard (the Home tab).
    data object Insights : Screen("insights")
}
