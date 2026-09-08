package com.expensesplitter.app.ui.screens.insights

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import java.time.format.DateTimeFormatter
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
    var showRangePicker by remember { mutableStateOf(false) }

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

        // A trailing button pinned to the end via Box (not a Row sibling)
        // so it never competes with MonthPager for width — that competition
        // is what previously squeezed the next-month arrow and, on a custom
        // range, truncated the button label off the edge of the screen.
        Box(modifier = Modifier.fillMaxWidth()) {
            if (state.isCustomRange) {
                TextButton(onClick = { showRangePicker = true }, modifier = Modifier.align(Alignment.Center)) {
                    Text("${state.customStart} → ${state.customEnd}", fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = viewModel::clearCustomRange, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear custom range")
                }
            } else {
                MonthPager(
                    month = state.month,
                    year = state.year,
                    onChange = viewModel::changeMonth,
                    modifier = Modifier.align(Alignment.Center),
                )
                IconButton(onClick = { showRangePicker = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Filled.DateRange, contentDescription = "Custom date range")
                }
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

    if (showRangePicker) {
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.customStart?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = state.customEnd?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        // DateRangePicker needs a lot more vertical room than the compact
        // single-date DatePicker, so it gets a near-fullscreen Dialog rather
        // than DatePickerDialog's fixed-size chrome.
        Dialog(onDismissRequest = { showRangePicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f), shape = RoundedCornerShape(28.dp)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    DateRangePicker(
                        state = rangeState,
                        modifier = Modifier.weight(1f),
                        headline = {
                            val fmt = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
                            val start = rangeState.selectedStartDateMillis
                            val end = rangeState.selectedEndDateMillis
                            Column(modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp)) {
                                val startText = start?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(fmt) } ?: "Start date"
                                val endText = end?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(fmt) } ?: "End date"
                                Text("$startText – $endText", style = MaterialTheme.typography.titleLarge)
                                // The "how many days" readout the airline-style
                                // pickers show once both ends are picked.
                                if (start != null && end != null) {
                                    val days = ((end - start) / 86_400_000L).toInt() + 1
                                    Text(
                                        "$days day${if (days == 1) "" else "s"} selected",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showRangePicker = false }) { Text("Cancel") }
                        TextButton(
                            enabled = rangeState.selectedStartDateMillis != null && rangeState.selectedEndDateMillis != null,
                            onClick = {
                                val start = rangeState.selectedStartDateMillis
                                val end = rangeState.selectedEndDateMillis
                                if (start != null && end != null) {
                                    viewModel.setCustomRange(
                                        Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate(),
                                        Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate(),
                                    )
                                }
                                showRangePicker = false
                            },
                        ) { Text("Apply") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChartMessage(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
