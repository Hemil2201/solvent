package com.expensesplitter.app.data.remote

import com.expensesplitter.app.data.remote.dto.ActivityResponseDto
import com.expensesplitter.app.data.remote.dto.BalanceResponseDto
import com.expensesplitter.app.data.remote.dto.BudgetDto
import com.expensesplitter.app.data.remote.dto.BudgetSetDto
import com.expensesplitter.app.data.remote.dto.BudgetSummaryResponseDto
import com.expensesplitter.app.data.remote.dto.CategoryDto
import com.expensesplitter.app.data.remote.dto.ExpenseCommentCreateDto
import com.expensesplitter.app.data.remote.dto.ExpenseCommentDto
import com.expensesplitter.app.data.remote.dto.ExpenseCreateDto
import com.expensesplitter.app.data.remote.dto.ExpenseDto
import com.expensesplitter.app.data.remote.dto.ExpenseEditHistoryDto
import com.expensesplitter.app.data.remote.dto.ExpenseUpdateDto
import com.expensesplitter.app.data.remote.dto.LoginRequestDto
import com.expensesplitter.app.data.remote.dto.MonthlyReportDto
import com.expensesplitter.app.data.remote.dto.RecurringExpenseCreateDto
import com.expensesplitter.app.data.remote.dto.RecurringExpenseDto
import com.expensesplitter.app.data.remote.dto.RecurringExpenseUpdateDto
import com.expensesplitter.app.data.remote.dto.ReceiptScanResponseDto
import com.expensesplitter.app.data.remote.dto.ResolveTransactionDto
import com.expensesplitter.app.data.remote.dto.StatementTransactionDto
import com.expensesplitter.app.data.remote.dto.StatementUploadDto
import com.expensesplitter.app.data.remote.dto.StatementUploadSummaryDto
import com.expensesplitter.app.data.remote.dto.TokenResponseDto
import com.expensesplitter.app.data.remote.dto.UserDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("auth/users")
    suspend fun getLoginUsers(): List<UserDto>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): TokenResponseDto

    @GET("auth/me")
    suspend fun getMe(): UserDto

    @GET("categories")
    suspend fun getCategories(): List<CategoryDto>

    @POST("expenses")
    suspend fun createExpense(@Body body: ExpenseCreateDto): ExpenseDto

    @GET("expenses")
    suspend fun getExpenses(
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("category_id") categoryId: String? = null,
        @Query("person_id") personId: String? = null,
        @Query("is_shared") isShared: Boolean? = null,
        @Query("deleted_only") deletedOnly: Boolean? = null,
    ): List<ExpenseDto>

    @GET("expenses/{id}")
    suspend fun getExpense(@Path("id") id: String): ExpenseDto

    @PUT("expenses/{id}")
    suspend fun updateExpense(@Path("id") id: String, @Body body: ExpenseUpdateDto): ExpenseDto

    @DELETE("expenses/{id}")
    suspend fun deleteExpense(@Path("id") id: String): ExpenseDto

    @POST("expenses/{id}/restore")
    suspend fun restoreExpense(@Path("id") id: String): ExpenseDto

    @GET("expenses/{id}/comments")
    suspend fun getComments(@Path("id") id: String): List<ExpenseCommentDto>

    @POST("expenses/{id}/comments")
    suspend fun addComment(@Path("id") id: String, @Body body: ExpenseCommentCreateDto): ExpenseCommentDto

    @GET("expenses/{id}/history")
    suspend fun getEditHistory(@Path("id") id: String): List<ExpenseEditHistoryDto>

    @GET("balance")
    suspend fun getBalance(): BalanceResponseDto

    @POST("budgets")
    suspend fun setBudget(@Body body: BudgetSetDto): BudgetDto

    @GET("budgets/summary")
    suspend fun getBudgetSummary(@Query("month") month: Int, @Query("year") year: Int): BudgetSummaryResponseDto

    @Multipart
    @POST("statements/upload")
    suspend fun uploadStatement(
        @Part file: MultipartBody.Part,
        @Part("bank_name") bankName: RequestBody?,
        @Part("card_last4") cardLast4: RequestBody?,
    ): StatementUploadDto

    @GET("statements/{id}")
    suspend fun getStatement(@Path("id") id: String): StatementUploadSummaryDto

    @GET("statements/{id}/transactions")
    suspend fun getStatementTransactions(@Path("id") id: String): List<StatementTransactionDto>

    @POST("statements/transactions/{id}/resolve")
    suspend fun resolveTransaction(
        @Path("id") id: String,
        @Body body: ResolveTransactionDto,
    ): StatementTransactionDto

    @GET("reports/monthly")
    suspend fun getMonthlyReport(@Query("month") month: Int, @Query("year") year: Int): MonthlyReportDto

    @GET("reports/monthly")
    suspend fun getReportForRange(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
    ): MonthlyReportDto

    @GET("reports/export")
    suspend fun exportCsv(@Query("month") month: Int, @Query("year") year: Int): ResponseBody

    @GET("reports/export")
    suspend fun exportCsvForRange(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
    ): ResponseBody

    @GET("recurring")
    suspend fun getRecurring(): List<RecurringExpenseDto>

    @POST("recurring")
    suspend fun createRecurring(@Body body: RecurringExpenseCreateDto): RecurringExpenseDto

    @PUT("recurring/{id}")
    suspend fun updateRecurring(@Path("id") id: String, @Body body: RecurringExpenseUpdateDto): RecurringExpenseDto

    @GET("activity")
    suspend fun getActivity(@Query("limit") limit: Int = 20): ActivityResponseDto

    @Multipart
    @PUT("users/me/avatar")
    suspend fun updateAvatar(@Part file: MultipartBody.Part): UserDto

    @Multipart
    @POST("receipts/scan")
    suspend fun scanReceipt(@Part file: MultipartBody.Part): ReceiptScanResponseDto
}
