package com.expensesplitter.app.ui.screens.insights

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.expensesplitter.app.data.repository.ReportRepository
import com.expensesplitter.app.ui.components.AreaChart
import com.expensesplitter.app.ui.components.BarSegment
import com.expensesplitter.app.ui.components.MonthPager
import com.expensesplitter.app.ui.components.SectionCard
import com.expensesplitter.app.ui.components.SegmentedBar
import com.expensesplitter.app.ui.components.SkeletonBlock
import com.expensesplitter.app.ui.components.TrendPoint
import com.expensesplitter.app.ui.components.donutSlicesFor
import com.expensesplitter.app.ui.components.DonutChart
import com.expensesplitter.app.ui.theme.BalanceColors
import com.expensesplitter.app.ui.theme.IndigoTertiaryContainerLight
import com.expensesplitter.app.ui.theme.Spacing
import java.time.Instant
import java.time.Month
import java.time.ZoneOffset
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(reportRepository: ReportRepository) {
    val viewModel: InsightsViewModel = viewModel(
        factory = viewModelFactory { initializer { InsightsViewModel(reportRepository) } },
    )
    val state = viewModel.state
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // "start" | "end" | null — which end of a custom range the date picker
    // below is currently editing. Tapping "Custom range" starts at "start"
    // and, once confirmed, chains straight into "end" for a quick two-tap flow.
    var datePickerTarget by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = {
                coroutineScope.launch {
                    val start = state.customStart
                    val end = state.customEnd
                    val file = if (start != null && end != null) {
                        reportRepository.downloadCsvForRange(start.toString(), end.toString(), context.cacheDir)
                    } else {
                        reportRepository.downloadCsv(state.month, state.year, context.cacheDir)
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share expenses CSV"))
                }
            }) { Text("Export CSV") }
        }

        if (state.isCustomRange) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AssistChip(
                    onClick = { datePickerTarget = "start" },
                    label = { Text("${state.customStart} → ${state.customEnd}") },
                )
                IconButton(onClick = viewModel::clearCustomRange) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear custom range")
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // MonthPager fills whatever width it's given, so it needs a
                // weight here or it swallows the whole row and leaves no
                // room for the button beside it.
                MonthPager(
                    month = state.month,
                    year = state.year,
                    onChange = viewModel::changeMonth,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { datePickerTarget = "start" }) { Text("Custom range") }
            }
        }

        Crossfade(targetState = state.isLoading, animationSpec = tween(250), label = "insights-loading") { loading ->
            if (loading) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    repeat(3) {
                        SkeletonBlock(Modifier.fillMaxWidth().height(180.dp), shape = RoundedCornerShape(32.dp))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    state.error?.let { Text("Couldn't load insights: $it", color = MaterialTheme.colorScheme.error) }

                    SectionCard("Category Breakdown", tint = MaterialTheme.colorScheme.surfaceContainerLow) {
                        if (state.byCategory.isEmpty()) {
                            EmptyChartMessage("No expenses this month")
                        } else {
                            DonutChart(
                                slices = donutSlicesFor(state.byCategory.map { it.categoryName to (it.total.toDoubleOrNull() ?: 0.0) }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    SectionCard("Spend Over Time", tint = IndigoTertiaryContainerLight.copy(alpha = 0.35f)) {
                        if (state.trend.size < 2) {
                            EmptyChartMessage("Not enough data yet")
                        } else {
                            AreaChart(
                                points = state.trend.map { m ->
                                    TrendPoint(
                                        Month.of(m.month).name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                                        m.total.toDoubleOrNull() ?: 0.0,
                                    )
                                },
                                lineColor = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    SectionCard("Personal vs Shared", tint = MaterialTheme.colorScheme.surfaceContainerLow) {
                        state.report?.let { report ->
                            SegmentedBar(
                                segments = listOf(
                                    BarSegment("Personal", report.personalSpend.toDoubleOrNull() ?: 0.0, BalanceColors.positiveLight),
                                    BarSegment("Shared", report.sharedSpend.toDoubleOrNull() ?: 0.0, BalanceColors.negativeLight),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Spacer(Modifier.height(Spacing.xl))
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
                    if (date != null) {
                        if (target == "start") {
                            viewModel.setCustomStart(date)
                            datePickerTarget = "end"
                        } else {
                            viewModel.setCustomEnd(date)
                            datePickerTarget = null
                        }
                    } else {
                        datePickerTarget = null
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { datePickerTarget = null }) { Text("Cancel") } },
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
private fun EmptyChartMessage(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
