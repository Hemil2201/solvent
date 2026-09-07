package com.expensesplitter.app.data.repository

import com.expensesplitter.app.data.remote.ApiService
import java.io.File

data class CategoryBreakdown(val categoryId: String?, val categoryName: String, val total: String)

data class MonthlyReport(
    val month: Int,
    val year: Int,
    val totalSpend: String,
    val personalSpend: String,
    val sharedSpend: String,
    val byCategory: List<CategoryBreakdown>,
)

class ReportRepository(private val apiService: ApiService) {
    suspend fun getMonthlyReport(month: Int, year: Int): MonthlyReport {
        val dto = apiService.getMonthlyReport(month, year)
        return MonthlyReport(
            month = dto.month ?: month,
            year = dto.year ?: year,
            totalSpend = dto.total_spend,
            personalSpend = dto.personal_spend,
            sharedSpend = dto.shared_spend,
            byCategory = dto.by_category.map { CategoryBreakdown(it.category_id, it.category_name, it.total) },
        )
    }

    // month/year are meaningless for a custom range — callers that need the
    // range back already hold the LocalDates they passed in.
    suspend fun getReportForRange(startDate: String, endDate: String): MonthlyReport {
        val dto = apiService.getReportForRange(startDate, endDate)
        return MonthlyReport(
            month = dto.month ?: 0,
            year = dto.year ?: 0,
            totalSpend = dto.total_spend,
            personalSpend = dto.personal_spend,
            sharedSpend = dto.shared_spend,
            byCategory = dto.by_category.map { CategoryBreakdown(it.category_id, it.category_name, it.total) },
        )
    }

    suspend fun downloadCsv(month: Int, year: Int, cacheDir: File): File {
        val body = apiService.exportCsv(month, year)
        val file = File(cacheDir, "expenses_${year}_${"%02d".format(month)}.csv")
        body.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        return file
    }

    suspend fun downloadCsvForRange(startDate: String, endDate: String, cacheDir: File): File {
        val body = apiService.exportCsvForRange(startDate, endDate)
        val file = File(cacheDir, "expenses_${startDate}_to_${endDate}.csv")
        body.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        return file
    }
}
