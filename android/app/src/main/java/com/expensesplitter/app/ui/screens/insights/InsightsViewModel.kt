package com.expensesplitter.app.ui.screens.insights

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensesplitter.app.data.repository.CategoryBreakdown
import com.expensesplitter.app.data.repository.MonthlyReport
import com.expensesplitter.app.data.repository.ReportRepository
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class MonthSpend(val month: Int, val year: Int, val total: String)

data class InsightsUiState(
    val month: Int = LocalDate.now().monthValue,
    val year: Int = LocalDate.now().year,
    // Both non-null <=> a custom range is active, overriding month/year for
    // everything except the Spend Over Time trend (that one stays anchored
    // to real calendar months regardless — see loadTrend).
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val isLoading: Boolean = true,
    val report: MonthlyReport? = null,
    val trend: List<MonthSpend> = emptyList(),
    val error: String? = null,
) {
    val byCategory: List<CategoryBreakdown> get() = report?.byCategory.orEmpty()
    val isCustomRange: Boolean get() = customStart != null && customEnd != null
}

private const val TREND_MONTHS = 6

class InsightsViewModel(private val reportRepository: ReportRepository) : ViewModel() {
    var state by mutableStateOf(InsightsUiState())
        private set

    init {
        load()
    }

    fun load() {
        state = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val start = state.customStart
                val end = state.customEnd
                val report = if (start != null && end != null) {
                    reportRepository.getReportForRange(start.toString(), end.toString())
                } else {
                    reportRepository.getMonthlyReport(state.month, state.year)
                }
                // Trend is always the trailing 6 real calendar months ending
                // this month, independent of whatever range is selected —
                // a custom "3mo10d" window doesn't map onto month buckets.
                val now = LocalDate.now()
                val trend = loadTrend(now.monthValue, now.year)
                state = state.copy(isLoading = false, report = report, trend = trend)
            } catch (e: Exception) {
                state = state.copy(isLoading = false, error = e.message ?: "Failed to load insights")
            }
        }
    }

    private suspend fun loadTrend(month: Int, year: Int): List<MonthSpend> = coroutineScope {
        val months = (0 until TREND_MONTHS).map { offset ->
            var m = month - (TREND_MONTHS - 1 - offset)
            var y = year
            while (m < 1) { m += 12; y-- }
            m to y
        }
        months.map { (m, y) ->
            async { MonthSpend(m, y, reportRepository.getMonthlyReport(m, y).totalSpend) }
        }.awaitAll()
    }

    fun changeMonth(delta: Int) {
        var month = state.month + delta
        var year = state.year
        if (month > 12) { month = 1; year++ }
        if (month < 1) { month = 12; year-- }
        state = state.copy(month = month, year = year)
        load()
    }

    // DateRangePicker hands back both ends at once (unlike the old two-step
    // single-date flow), so this can just set both fields atomically.
    fun setCustomRange(start: LocalDate, end: LocalDate) {
        state = if (end.isBefore(start)) {
            state.copy(customStart = end, customEnd = start)
        } else {
            state.copy(customStart = start, customEnd = end)
        }
        load()
    }

    fun clearCustomRange() {
        state = state.copy(customStart = null, customEnd = null)
        load()
    }
}
