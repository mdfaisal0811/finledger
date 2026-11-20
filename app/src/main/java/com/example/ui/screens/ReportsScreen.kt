package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.export.Formatters
import com.example.domain.model.AccountType
import com.example.domain.model.DashboardDateRange
import com.example.domain.model.FinancialEngine
import com.example.ui.components.CashflowLineChart
import com.example.ui.components.CategoryDoughnutChart
import com.example.ui.components.FinCard
import com.example.ui.components.IncomeExpenseBarChart
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AmountTextStyle
import com.example.ui.theme.LocalFinLedgerColors
import com.example.ui.viewmodel.FinanceWorkspaceData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    data: FinanceWorkspaceData,
    onSelectDateRange: (DashboardDateRange, Long?, Long?) -> Unit,
    onExportCsv: (android.net.Uri) -> Unit,
    onExportPdf: (android.net.Uri) -> Unit,
    onOpenStatements: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    val settings = data.settings
    val summary = data.dashboardSummary
    var showCustomRange by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(onExportCsv) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let(onExportPdf) }

    val accountSummaries = remember(data.accounts, data.transactions) {
        data.accounts.filter { !it.archived }.map { acc ->
            FinancialEngine.computeAccountSummary(acc, data.transactions)
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reports & Analytics", style = MaterialTheme.typography.headlineMedium)
                }
            }

            // Statements CTA + Export buttons
            item {
                FinCard(
                    onClick = onOpenStatements,
                    modifier = Modifier.fillMaxWidth().testTag("reports_open_statements_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = "Statements",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Generate Financial Statements", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Custom Date, Weekly, Monthly, Yearly & Account-wise PDF / CSV",
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.textSecondary
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = semantic.textSecondary
                        )
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { csvLauncher.launch("finledger_report.csv") },
                        modifier = Modifier.weight(1f).testTag("export_csv_button")
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export CSV")
                    }
                    OutlinedButton(
                        onClick = { pdfLauncher.launch("finledger_report.pdf") },
                        modifier = Modifier.weight(1f).testTag("export_pdf_button")
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export PDF")
                    }
                }
            }

            // Period selector
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardDateRange.entries.forEach { range ->
                        FilterChip(
                            selected = data.selectedDateRange == range,
                            onClick = {
                                if (range == DashboardDateRange.CUSTOM) showCustomRange = true
                                else onSelectDateRange(range, null, null)
                            },
                            label = {
                                Text(
                                    when (range) {
                                        DashboardDateRange.TODAY -> "Daily"
                                        DashboardDateRange.THIS_WEEK -> "Weekly"
                                        DashboardDateRange.THIS_MONTH -> "Monthly"
                                        DashboardDateRange.THIS_YEAR -> "Yearly"
                                        DashboardDateRange.CUSTOM -> "Custom"
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // Income vs Expense & Savings Analysis
            item {
                SectionHeader(title = "Income vs Expense & Savings")
                Spacer(Modifier.height(8.dp))
                FinCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReportMetricRow("Total Income", Formatters.formatMoney(summary.totalIncome, settings.currencySymbol, settings.hideBalances), semantic.income)
                        ReportMetricRow("Total Expenses (incl. charges)", Formatters.formatMoney(summary.totalExpenses, settings.currencySymbol, settings.hideBalances), semantic.expense)
                        ReportMetricRow("Net Savings (Income - Expense)", Formatters.formatMoney(summary.totalSavings, settings.currencySymbol, settings.hideBalances), semantic.transfer)
                        val savingsRate = if (summary.totalIncome > 0) ((summary.totalSavings / summary.totalIncome) * 100).toInt() else 0
                        ReportMetricRow("Savings Rate", "$savingsRate%", MaterialTheme.colorScheme.onSurface)
                        ReportMetricRow("Transfer Charges", Formatters.formatMoney(summary.totalTransferCharges, settings.currencySymbol, settings.hideBalances), semantic.warning)
                    }
                }
            }

            // Bar chart
            item {
                SectionHeader(title = "6-Month Income vs Expense")
                Spacer(Modifier.height(8.dp))
                FinCard(modifier = Modifier.fillMaxWidth()) {
                    IncomeExpenseBarChart(series = summary.monthlyCashflow)
                }
            }

            // Line chart
            item {
                SectionHeader(title = "Cashflow Trend Line")
                Spacer(Modifier.height(8.dp))
                FinCard(modifier = Modifier.fillMaxWidth()) {
                    CashflowLineChart(series = summary.monthlyCashflow)
                }
            }

            // Category Distribution Doughnut
            item {
                SectionHeader(title = "Category Distribution")
                Spacer(Modifier.height(8.dp))
                FinCard(modifier = Modifier.fillMaxWidth()) {
                    if (summary.spendingByCategory.isEmpty()) {
                        Text("No expense data in selected period.", style = MaterialTheme.typography.bodySmall, color = semantic.textSecondary)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CategoryDoughnutChart(
                                items = summary.spendingByCategory,
                                currencySymbol = settings.currencySymbol,
                                hideBalances = settings.hideBalances
                            )
                            summary.spendingByCategory.forEach { cat ->
                                ReportMetricRow(
                                    label = "${cat.categoryName} (${(cat.percentage * 100).toInt()}%)",
                                    value = Formatters.formatMoney(cat.amount, settings.currencySymbol, settings.hideBalances),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Account-wise Spending
            item {
                SectionHeader(title = "Account-wise Activity")
                Spacer(Modifier.height(8.dp))
                FinCard(modifier = Modifier.fillMaxWidth()) {
                    if (accountSummaries.isEmpty()) {
                        Text("No accounts available.", style = MaterialTheme.typography.bodySmall, color = semantic.textSecondary)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            accountSummaries.forEach { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(item.account.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "In: ${Formatters.formatMoney(item.totalIncome, settings.currencySymbol, settings.hideBalances)}  |  Out: ${Formatters.formatMoney(item.totalExpense, settings.currencySymbol, settings.hideBalances)}  |  Bal: ${Formatters.formatMoney(item.account.currentBalance, settings.currencySymbol, settings.hideBalances)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = semantic.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Lending & Borrowing + Credit Card Summary
            item {
                SectionHeader(title = "Credit Cards & Loan Summary")
                Spacer(Modifier.height(8.dp))
                FinCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val cards = data.accounts.filter { it.typeEnum() == AccountType.CREDIT_CARD && !it.archived }
                        ReportMetricRow("Total Credit Card Outstanding", Formatters.formatMoney(summary.totalCreditCardDebt, settings.currencySymbol, settings.hideBalances), semantic.expense)
                        ReportMetricRow("Active Credit Cards", "${cards.size}", MaterialTheme.colorScheme.onSurface)
                        ReportMetricRow("Outstanding Money Lent", Formatters.formatMoney(summary.outstandingLent, settings.currencySymbol, settings.hideBalances), semantic.warning)
                        ReportMetricRow("Outstanding Money Borrowed", Formatters.formatMoney(summary.outstandingBorrowed, settings.currencySymbol, settings.hideBalances), semantic.expense)
                    }
                }
            }
        }
    }

    if (showCustomRange) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = data.customStartMillis,
            initialSelectedEndDateMillis = data.customEndMillis
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showCustomRange = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = pickerState.selectedStartDateMillis
                        val e = pickerState.selectedEndDateMillis
                        if (s != null && e != null) {
                            onSelectDateRange(DashboardDateRange.CUSTOM, s, e + 86_399_999L)
                        }
                        showCustomRange = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRange = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.height(420.dp))
        }
    }
}

@Composable
private fun ReportMetricRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalFinLedgerColors.current.textSecondary)
        Text(value, style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize), color = color)
    }
}
