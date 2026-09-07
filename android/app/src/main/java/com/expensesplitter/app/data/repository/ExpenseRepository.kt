package com.expensesplitter.app.data.repository

import com.expensesplitter.app.data.local.CategoryDao
import com.expensesplitter.app.data.local.CategoryEntity
import com.expensesplitter.app.data.local.ExpenseDao
import com.expensesplitter.app.data.local.ExpenseEntity
import com.expensesplitter.app.data.remote.ApiService
import com.expensesplitter.app.data.remote.dto.ExpenseCommentCreateDto
import com.expensesplitter.app.data.remote.dto.ExpenseCreateDto
import com.expensesplitter.app.data.remote.dto.ExpenseDto
import com.expensesplitter.app.data.remote.dto.ExpenseUpdateDto

data class ActivityItem(val type: String, val timestamp: String, val userName: String, val message: String)

data class Category(val id: String, val name: String, val icon: String?)

data class ExpenseSplit(val userId: String, val amountOwed: String, val splitType: String)

data class Expense(
    val id: String,
    val amount: String,
    val currency: String,
    val date: String,
    val description: String?,
    val notes: String?,
    val categoryId: String?,
    val paidBy: String,
    val isShared: Boolean,
    val deletedAt: String?,
    val splits: List<ExpenseSplit>,
    val receiptPhotoUrl: String?,
)

data class ExpenseComment(val id: String, val userId: String, val comment: String, val createdAt: String)

data class ExpenseEditHistoryEntry(
    val id: String,
    val editedBy: String,
    val fieldChanged: String,
    val oldValue: String?,
    val newValue: String?,
    val editedAt: String,
)

data class UserBalance(val userId: String, val name: String, val netBalance: String)

data class ExpenseFilter(
    val startDate: String? = null,
    val endDate: String? = null,
    val categoryId: String? = null,
    val personId: String? = null,
    val isShared: Boolean? = null,
) {
    val isEmpty: Boolean
        get() = startDate == null && endDate == null && categoryId == null && personId == null && isShared == null
}

/** Holds a category tapped on Home's category grid so the Expenses tab can
 * pick it up and pre-filter its list — same in-memory handoff pattern as
 * PendingReceiptDraftHolder, avoids threading a nav argument through the
 * bottom-tab route just for one filter value. */
class PendingCategoryFilterHolder {
    var categoryId: String? = null

    fun consume(): String? = categoryId.also { categoryId = null }
}

class ExpenseRepository(
    private val apiService: ApiService,
    private val categoryDao: CategoryDao,
    private val expenseDao: ExpenseDao,
) {
    suspend fun getCategories(): List<Category> = try {
        val categories = apiService.getCategories()
        categoryDao.replaceAll(categories.map { CategoryEntity(it.id, it.name, it.icon) })
        categories.map { Category(it.id, it.name, it.icon) }
    } catch (e: Exception) {
        categoryDao.getAll().map { Category(it.id, it.name, it.icon) }.ifEmpty { throw e }
    }

    suspend fun getExpenses(filter: ExpenseFilter = ExpenseFilter()): List<Expense> = try {
        val expenses = apiService.getExpenses(
            startDate = filter.startDate,
            endDate = filter.endDate,
            categoryId = filter.categoryId,
            personId = filter.personId,
            isShared = filter.isShared,
        )
        // Only cache the unfiltered set — a filtered result isn't "all expenses" for offline fallback.
        if (filter.isEmpty) expenseDao.replaceAll(expenses.map { it.toEntity() })
        expenses.map { it.toDomain() }
    } catch (e: Exception) {
        if (!filter.isEmpty) throw e
        expenseDao.getAll().map {
            Expense(it.id, it.amount, it.currency, it.date, it.description, null, it.categoryId, it.paidBy, it.isShared, null, emptyList(), null)
        }.ifEmpty { throw e }
    }

    suspend fun getDeletedExpenses(): List<Expense> =
        apiService.getExpenses(deletedOnly = true).map { it.toDomain() }

    suspend fun getExpense(id: String): Expense = apiService.getExpense(id).toDomain()

    suspend fun createExpense(body: ExpenseCreateDto): Expense = apiService.createExpense(body).toDomain()

    suspend fun updateExpense(id: String, body: ExpenseUpdateDto): Expense = apiService.updateExpense(id, body).toDomain()

    suspend fun deleteExpense(id: String) { apiService.deleteExpense(id) }

    suspend fun restoreExpense(id: String) { apiService.restoreExpense(id) }

    suspend fun getComments(expenseId: String): List<ExpenseComment> =
        apiService.getComments(expenseId).map { ExpenseComment(it.id, it.user_id, it.comment, it.created_at) }

    suspend fun addComment(expenseId: String, text: String): ExpenseComment {
        val dto = apiService.addComment(expenseId, ExpenseCommentCreateDto(text))
        return ExpenseComment(dto.id, dto.user_id, dto.comment, dto.created_at)
    }

    suspend fun getEditHistory(expenseId: String): List<ExpenseEditHistoryEntry> =
        apiService.getEditHistory(expenseId).map {
            ExpenseEditHistoryEntry(it.id, it.edited_by, it.field_changed, it.old_value, it.new_value, it.edited_at)
        }

    suspend fun getBalance(): List<UserBalance> =
        apiService.getBalance().balances.map { UserBalance(it.user_id, it.name, it.net_balance) }

    suspend fun getActivity(): List<ActivityItem> =
        apiService.getActivity().items.map { ActivityItem(it.type, it.timestamp, it.user_name, it.message) }

    private fun ExpenseDto.toDomain() = Expense(
        id = id,
        amount = amount,
        currency = currency,
        date = date,
        description = description,
        notes = notes,
        categoryId = category_id,
        paidBy = paid_by,
        isShared = is_shared,
        deletedAt = deleted_at,
        splits = splits.map { ExpenseSplit(it.user_id, it.amount_owed, it.split_type) },
        receiptPhotoUrl = receipt_photo_url,
    )

    private fun ExpenseDto.toEntity() =
        ExpenseEntity(id, amount, currency, date, description, category_id, paid_by, is_shared)
}
