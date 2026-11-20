package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.export.Formatters
import com.example.domain.model.Account
import com.example.domain.model.FinancialStatement
import com.example.domain.model.MonthlySelection
import com.example.domain.model.StatementEngine
import com.example.domain.model.StatementFilter
import com.example.domain.model.StatementPeriodMode
import com.example.domain.model.StatementRow
import com.example.domain.model.WeeklySelection
import com.example.domain.model.YearlySelection
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AmountTextStyle
import com.example.ui.theme.LocalFinLedgerColors
import com.example.ui.viewmodel.FinanceWorkspaceData
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatementsScreen(
    data: FinanceWorkspaceData,
    onBack: () -> Unit,
    onDownloadPdf: (Uri, FinancialStatement) -> Unit,
    onExportCsv: (Uri, FinancialStatement) -> Unit,
    onShareStatement: (FinancialStatement) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    val settings = data.settings

    var filter by remember { mutableStateOf(StatementFilter()) }
    var generatedStatement by remember {
        mutableStateOf(
            StatementEngine.generateStatement(
                filter = StatementFilter(),
                settings = settings,
                accounts = data.accounts,
                transactions = data.transactions,
                lendingRecords = data.lendingRecords,
                borrowingRecords = data.borrowingRecords
            )
        )
    }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showWeekDatePicker by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { onDownloadPdf(it, generatedStatement) } }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { onExportCsv(it, generatedStatement) } }

    fun regenerate(nextFilter: StatementFilter = filter) {
        filter = nextFilter
        generatedStatement = StatementEngine.generateStatement(
            filter = nextFilter,
            settings = settings,
            accounts = data.accounts,
            transactions = data.transactions,
            lendingRecords = data.lendingRecords,
            borrowingRecords = data.borrowingRecords
        )
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1040.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("statements_back_button")) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                        Column {
                            Text("Financial Statements", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Custom, Weekly, Monthly, Yearly & Account-wise",
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.textSecondary
                            )
                        }
                    }
                }
            }

            // Statement Filter Controls Card
            item {
                FinCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Statement Period", style = MaterialTheme.typography.titleSmall)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatementPeriodMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = filter.periodMode == mode,
                                    onClick = { filter = filter.copy(periodMode = mode) },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.testTag("stmt_mode_${mode.name.lowercase()}")
                                )
                            }
                        }

                        // Sub-options per selected mode
                        when (filter.periodMode) {
                            StatementPeriodMode.CUSTOM -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showStartDatePicker = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("From: ${Formatters.formatDate(filter.customStartMillis, settings.dateFormat)}", maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = { showEndDatePicker = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("To: ${Formatters.formatDate(filter.customEndMillis, settings.dateFormat)}", maxLines = 1)
                                    }
                                }
                            }

                            StatementPeriodMode.WEEKLY -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    WeeklySelection.entries.forEach { sel ->
                                        FilterChip(
                                            selected = filter.weeklySelection == sel,
                                            onClick = {
                                                filter = filter.copy(weeklySelection = sel)
                                                if (sel == WeeklySelection.SPECIFIC_WEEK) showWeekDatePicker = true
                                            },
                                            label = { Text(sel.label) }
                                        )
                                    }
                                }
                                if (filter.weeklySelection == WeeklySelection.SPECIFIC_WEEK) {
                                    val (ws, we) = StatementEngine.weekBoundsMillis(filter.specificWeekAnchorMillis)
                                    OutlinedButton(
                                        onClick = { showWeekDatePicker = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Week: ${Formatters.formatDate(ws, settings.dateFormat)} – ${Formatters.formatDate(we, settings.dateFormat)}"
                                        )
                                    }
                                }
                            }

                            StatementPeriodMode.MONTHLY -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MonthlySelection.entries.forEach { sel ->
                                        FilterChip(
                                            selected = filter.monthlySelection == sel,
                                            onClick = { filter = filter.copy(monthlySelection = sel) },
                                            label = { Text(sel.label) }
                                        )
                                    }
                                }
                                if (filter.monthlySelection == MonthlySelection.SELECT_MONTH_YEAR) {
                                    MonthYearSelectorRow(
                                        selectedMonth = filter.selectedMonthZeroIndexed,
                                        selectedYear = filter.selectedMonthYear,
                                        onChange = { m, y ->
                                            filter = filter.copy(selectedMonthZeroIndexed = m, selectedMonthYear = y)
                                        }
                                    )
                                }
                            }

                            StatementPeriodMode.YEARLY -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    YearlySelection.entries.forEach { sel ->
                                        FilterChip(
                                            selected = filter.yearlySelection == sel,
                                            onClick = { filter = filter.copy(yearlySelection = sel) },
                                            label = { Text(sel.label) }
                                        )
                                    }
                                }
                                if (filter.yearlySelection == YearlySelection.SELECT_YEAR) {
                                    YearSelectorDropdown(
                                        selectedYear = filter.selectedYear,
                                        onSelectYear = { y -> filter = filter.copy(selectedYear = y) }
                                    )
                                }
                            }
                        }

                        // Account-wise Selector
                        StatementAccountDropdown(
                            accounts = data.accounts,
                            selectedAccountId = filter.selectedAccountId,
                            onSelect = { accId -> filter = filter.copy(selectedAccountId = accId) }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { regenerate(filter) },
                                modifier = Modifier.weight(1f).testTag("generate_statement_btn")
                            ) {
                                Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Generate Statement")
                            }
                            OutlinedButton(
                                onClick = {
                                    regenerate(filter)
                                    showPreviewDialog = true
                                },
                                modifier = Modifier.weight(1f).testTag("preview_statement_btn")
                            ) {
                                Icon(Icons.Outlined.Preview, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Preview")
                            }
                        }
                    }
                }
            }

            // Export & Share Action Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val safeName = generatedStatement.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)
                            pdfLauncher.launch("${safeName}.pdf")
                        },
                        modifier = Modifier.weight(1f).testTag("stmt_download_pdf_btn")
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Download PDF", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            val safeName = generatedStatement.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)
                            csvLauncher.launch("${safeName}.csv")
                        },
                        modifier = Modifier.weight(1f).testTag("stmt_export_csv_btn")
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export CSV", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { onShareStatement(generatedStatement) },
                        modifier = Modifier.weight(1f).testTag("stmt_share_btn")
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Statement Header & Summary
            item {
                StatementHeaderAndSummarySection(
                    statement = generatedStatement,
                    currencySymbol = settings.currencySymbol,
                    hideBalances = settings.hideBalances,
                    dateFormat = settings.dateFormat
                )
            }

            // Detailed Ledger Table
            item {
                SectionHeader(title = "Statement Ledger (${generatedStatement.rows.size})")
                Spacer(Modifier.height(8.dp))
                if (generatedStatement.rows.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Outlined.AccountBalance,
                        title = "No transactions in period",
                        message = "Adjust the date range or account filter and tap Generate Statement."
                    )
                } else {
                    StatementTransactionTable(
                        statement = generatedStatement,
                        currencySymbol = settings.currencySymbol,
                        dateFormat = settings.dateFormat,
                        hideBalances = settings.hideBalances
                    )
                }
            }
        }
    }

    if (showStartDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = filter.customStartMillis)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { filter = filter.copy(customStartMillis = it) }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showEndDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = filter.customEndMillis)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { filter = filter.copy(customEndMillis = it) }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showWeekDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = filter.specificWeekAnchorMillis)
        DatePickerDialog(
            onDismissRequest = { showWeekDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        filter = filter.copy(weeklySelection = WeeklySelection.SPECIFIC_WEEK, specificWeekAnchorMillis = it)
                    }
                    showWeekDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showWeekDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showPreviewDialog = false },
            title = { Text("Statement Preview") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatementHeaderAndSummarySection(
                        statement = generatedStatement,
                        currencySymbol = settings.currencySymbol,
                        hideBalances = settings.hideBalances,
                        dateFormat = settings.dateFormat
                    )
                    if (generatedStatement.rows.isNotEmpty()) {
                        StatementTransactionTable(
                            statement = generatedStatement,
                            currencySymbol = settings.currencySymbol,
                            dateFormat = settings.dateFormat,
                            hideBalances = settings.hideBalances
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showPreviewDialog = false
                    val safeName = generatedStatement.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)
                    pdfLauncher.launch("${safeName}.pdf")
                }) {
                    Text("Download PDF")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onShareStatement(generatedStatement) }) { Text("Share") }
                    TextButton(onClick = { showPreviewDialog = false }) { Text("Close") }
                }
            }
        )
    }
}

@Composable
private fun StatementHeaderAndSummarySection(
    statement: FinancialStatement,
    currencySymbol: String,
    hideBalances: Boolean,
    dateFormat: String
) {
    val semantic = LocalFinLedgerColors.current
    FinCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(statement.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Holder: ${statement.userName}" + if (statement.userEmail.isNotBlank()) " (${statement.userEmail})" else "",
                style = MaterialTheme.typography.bodySmall,
                color = semantic.textSecondary
            )
            Text(
                text = "Account: ${statement.accountLabel} • Period: ${statement.dateRangeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = semantic.textSecondary
            )
            Text(
                text = "Generated: ${Formatters.formatDateTime(statement.generatedAtMillis, dateFormat)}",
                style = MaterialTheme.typography.labelSmall,
                color = semantic.textSecondary
            )

            HorizontalDivider()

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BalanceHighlightPill(
                    label = if (statement.isCreditCardStatement) "Opening Debt" else "Opening Balance",
                    value = Formatters.formatMoney(statement.openingBalance, currencySymbol, hideBalances),
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                BalanceHighlightPill(
                    label = if (statement.isCreditCardStatement) "Closing Debt" else "Closing Balance",
                    value = Formatters.formatMoney(statement.closingBalance, currencySymbol, hideBalances),
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            StatementMetricLine("Total Income", Formatters.formatMoney(statement.totalIncome, currencySymbol, hideBalances), semantic.income)
            StatementMetricLine("Total Expenses (incl. charges)", Formatters.formatMoney(statement.totalExpenses, currencySymbol, hideBalances), semantic.expense)
            StatementMetricLine("Net Cash Flow", Formatters.formatMoney(statement.netCashFlow, currencySymbol, hideBalances, showPlusSign = true), if (statement.netCashFlow >= 0) semantic.income else semantic.expense)
            StatementMetricLine("Transfer In", Formatters.formatMoney(statement.transferIn, currencySymbol, hideBalances), semantic.transfer)
            StatementMetricLine("Transfer Out", Formatters.formatMoney(statement.transferOut, currencySymbol, hideBalances), semantic.transfer)
            StatementMetricLine("Transfer Charges", Formatters.formatMoney(statement.transferCharges, currencySymbol, hideBalances), semantic.warning)
            StatementMetricLine("Money Lent", Formatters.formatMoney(statement.moneyLent, currencySymbol, hideBalances), semantic.warning)
            StatementMetricLine("Money Borrowed", Formatters.formatMoney(statement.moneyBorrowed, currencySymbol, hideBalances), MaterialTheme.colorScheme.onSurface)
            StatementMetricLine("Repayments (Period)", Formatters.formatMoney(statement.totalRepayments, currencySymbol, hideBalances), MaterialTheme.colorScheme.onSurface)
            StatementMetricLine("Outstanding Lending", Formatters.formatMoney(statement.outstandingLending, currencySymbol, hideBalances), semantic.warning)
            StatementMetricLine("Outstanding Borrowing", Formatters.formatMoney(statement.outstandingBorrowing, currencySymbol, hideBalances), semantic.expense)

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Accounting rules: Internal transfers & card repayments do not inflate income or expenses. Transfer charges are counted in expenses. Loans are excluded from income/expense.",
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.textSecondary,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun BalanceHighlightPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = LocalFinLedgerColors.current.textSecondary)
            Text(value, style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), color = accent)
        }
    }
}

@Composable
private fun StatementMetricLine(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalFinLedgerColors.current.textSecondary)
        Text(value, style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize), color = color)
    }
}

@Composable
private fun StatementTransactionTable(
    statement: FinancialStatement,
    currencySymbol: String,
    dateFormat: String,
    hideBalances: Boolean
) {
    val semantic = LocalFinLedgerColors.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            // Table Header
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableHeaderCell("Date", 90.dp)
                TableHeaderCell("ID", 72.dp)
                TableHeaderCell("Description", 148.dp)
                TableHeaderCell("Category", 104.dp)
                TableHeaderCell("Type", 104.dp)
                TableHeaderCell("Account", 128.dp)
                TableHeaderCell("Income", 92.dp, TextAlign.End)
                TableHeaderCell("Expense", 92.dp, TextAlign.End)
                TableHeaderCell("Running Bal.", 108.dp, TextAlign.End)
            }
            HorizontalDivider()

            // Opening Balance Banner Row
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableCell(Formatters.formatDate(statement.startMillis, dateFormat), 90.dp)
                TableCell("—", 72.dp)
                TableCell("Opening Balance", 148.dp, bold = true)
                TableCell("—", 104.dp)
                TableCell("Balance B/F", 104.dp)
                TableCell(statement.accountLabel, 128.dp)
                TableCell("—", 92.dp, TextAlign.End)
                TableCell("—", 92.dp, TextAlign.End)
                TableCell(Formatters.formatMoney(statement.openingBalance, currencySymbol, hideBalances), 108.dp, TextAlign.End, bold = true)
            }
            HorizontalDivider()

            statement.rows.forEachIndexed { idx, row ->
                StatementTableRowView(
                    row = row,
                    currencySymbol = currencySymbol,
                    dateFormat = dateFormat,
                    hideBalances = hideBalances,
                    zebra = idx % 2 == 1
                )
                HorizontalDivider(color = semantic.cardBorder.copy(alpha = 0.5f))
            }

            // Closing Balance Footer Row
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableCell(Formatters.formatDate(statement.endMillis, dateFormat), 90.dp)
                TableCell("—", 72.dp)
                TableCell("Closing Balance", 148.dp, bold = true)
                TableCell("—", 104.dp)
                TableCell("Balance C/F", 104.dp)
                TableCell(statement.accountLabel, 128.dp)
                TableCell(Formatters.formatMoney(statement.totalIncome, currencySymbol, hideBalances), 92.dp, TextAlign.End, color = semantic.income, bold = true)
                TableCell(Formatters.formatMoney(statement.totalExpenses, currencySymbol, hideBalances), 92.dp, TextAlign.End, color = semantic.expense, bold = true)
                TableCell(Formatters.formatMoney(statement.closingBalance, currencySymbol, hideBalances), 108.dp, TextAlign.End, color = MaterialTheme.colorScheme.primary, bold = true)
            }
        }
    }
}

@Composable
private fun StatementTableRowView(
    row: StatementRow,
    currencySymbol: String,
    dateFormat: String,
    hideBalances: Boolean,
    zebra: Boolean
) {
    val semantic = LocalFinLedgerColors.current
    Row(
        modifier = Modifier
            .background(if (zebra) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableCell(Formatters.formatDate(row.occurredAtMillis, dateFormat), 90.dp)
        TableCell(row.transactionId.take(8), 72.dp, color = semantic.textSecondary)
        TableCell(row.description, 148.dp)
        TableCell(row.categoryName, 104.dp)
        TableCell(row.transactionTypeLabel, 104.dp)
        TableCell(row.accountDisplay, 128.dp)
        TableCell(
            text = if (row.incomeAmount > 0.0) Formatters.formatMoney(row.incomeAmount, currencySymbol, hideBalances) else "—",
            width = 92.dp,
            align = TextAlign.End,
            color = if (row.incomeAmount > 0.0) semantic.income else semantic.textSecondary
        )
        TableCell(
            text = if (row.expenseAmount > 0.0) Formatters.formatMoney(row.expenseAmount, currencySymbol, hideBalances) else "—",
            width = 92.dp,
            align = TextAlign.End,
            color = if (row.expenseAmount > 0.0) semantic.expense else semantic.textSecondary
        )
        TableCell(
            text = Formatters.formatMoney(row.runningBalance, currencySymbol, hideBalances),
            width = 108.dp,
            align = TextAlign.End,
            bold = true
        )
    }
}

@Composable
private fun TableHeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    align: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = 4.dp)
    )
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    align: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = color,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatementAccountDropdown(
    accounts: List<Account>,
    selectedAccountId: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedAcc = accounts.firstOrNull { it.id == selectedAccountId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedAcc?.let { "${it.name} (${it.typeEnum().label})" } ?: "All Accounts (Consolidated)",
            onValueChange = {},
            readOnly = true,
            label = { Text("Account Scope") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All Accounts (Consolidated)") },
                onClick = { onSelect(null); expanded = false }
            )
            accounts.forEach { acc ->
                val tag = if (acc.archived) " • Archived" else ""
                DropdownMenuItem(
                    text = { Text("${acc.name} (${acc.typeEnum().label}$tag)") },
                    onClick = { onSelect(acc.id); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthYearSelectorRow(
    selectedMonth: Int,
    selectedYear: Int,
    onChange: (Int, Int) -> Unit
) {
    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    var monthExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }
    val curYear = Calendar.getInstance().get(Calendar.YEAR)
    val years = ((curYear - 5)..(curYear + 1)).toList().reversed()

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = monthExpanded,
            onExpandedChange = { monthExpanded = !monthExpanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = monthNames[selectedMonth.coerceIn(0, 11)],
                onValueChange = {},
                readOnly = true,
                label = { Text("Month") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                monthNames.forEachIndexed { idx, name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { onChange(idx, selectedYear); monthExpanded = false }
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = yearExpanded,
            onExpandedChange = { yearExpanded = !yearExpanded },
            modifier = Modifier.weight(0.75f)
        ) {
            OutlinedTextField(
                value = selectedYear.toString(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Year") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                years.forEach { y ->
                    DropdownMenuItem(
                        text = { Text(y.toString()) },
                        onClick = { onChange(selectedMonth, y); yearExpanded = false }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearSelectorDropdown(
    selectedYear: Int,
    onSelectYear: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val curYear = Calendar.getInstance().get(Calendar.YEAR)
    val years = ((curYear - 10)..(curYear + 1)).toList().reversed()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedYear.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Select Year") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            years.forEach { y ->
                DropdownMenuItem(
                    text = { Text(y.toString()) },
                    onClick = { onSelectYear(y); expanded = false }
                )
            }
        }
    }
}
