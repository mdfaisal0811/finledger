package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.export.Formatters
import com.example.data.export.IconRegistry
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.DashboardDateRange
import com.example.domain.model.TransactionRecord
import com.example.ui.components.BudgetProgressRow
import com.example.ui.components.CategoryDoughnutChart
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FinCard
import com.example.ui.components.IncomeExpenseBarChart
import com.example.ui.components.SectionHeader
import com.example.ui.components.TransactionRow
import com.example.ui.theme.AmountTextStyle
import com.example.ui.theme.LocalFinLedgerColors
import com.example.ui.viewmodel.FinanceWorkspaceData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    data: FinanceWorkspaceData,
    onSelectDateRange: (DashboardDateRange, Long?, Long?) -> Unit,
    onToggleHideBalances: (Boolean) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenAccountDetail: (Account) -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenTransactionDetail: (TransactionRecord) -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenLendingBorrowing: (Int) -> Unit,
    onQuickAddExpense: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinLedgerColors.current
    val settings = data.settings
    val summary = data.dashboardSummary
    val unreadCount = data.notifications.count { !it.read }
    var showCustomRangeDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top bar greeting + notification bell + hide balance toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Welcome back,",
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.textSecondary
                        )
                        Text(
                            text = settings.displayName,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onToggleHideBalances(!settings.hideBalances) },
                            modifier = Modifier.testTag("toggle_hide_balances")
                        ) {
                            Icon(
                                imageVector = if (settings.hideBalances) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (settings.hideBalances) "Show balances" else "Hide balances"
                            )
                        }
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier.testTag("open_notifications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge { Text(unreadCount.coerceAtMost(99).toString()) }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = "Notifications"
                                )
                            }
                        }
                    }
                }
            }

            // Date range selector strip
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardDateRange.entries.forEach { range ->
                        val selected = data.selectedDateRange == range
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (range == DashboardDateRange.CUSTOM) {
                                    showCustomRangeDialog = true
                                } else {
                                    onSelectDateRange(range, null, null)
                                }
                            },
                            label = {
                                val labelText = if (range == DashboardDateRange.CUSTOM && data.customStartMillis != null && data.customEndMillis != null) {
                                    "${Formatters.formatDate(data.customStartMillis, "dd MMM")} - ${Formatters.formatDate(data.customEndMillis, "dd MMM")}"
                                } else {
                                    range.label
                                }
                                Text(labelText)
                            },
                            leadingIcon = if (range == DashboardDateRange.CUSTOM) {
                                {
                                    Icon(
                                        Icons.Outlined.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("range_${range.name.lowercase()}")
                        )
                    }
                }
            }

            // Total Net Balance Hero Card (minimal, crisp, high-contrast)
            item {
                FinCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Net Balance",
                                style = MaterialTheme.typography.titleSmall,
                                color = semantic.textSecondary
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = settings.currencyCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = Formatters.formatMoney(
                                summary.totalNetBalance,
                                settings.currencySymbol,
                                settings.hideBalances
                            ),
                            style = AmountTextStyle.copy(
                                fontSize = MaterialTheme.typography.displaySmall.fontSize,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("net_balance_text")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MiniMetricPill(
                                label = "Assets",
                                value = Formatters.formatMoney(summary.totalAssets, settings.currencySymbol, settings.hideBalances),
                                color = semantic.income,
                                modifier = Modifier.weight(1f)
                            )
                            MiniMetricPill(
                                label = "Card Debt",
                                value = Formatters.formatMoney(summary.totalCreditCardDebt, settings.currencySymbol, settings.hideBalances),
                                color = semantic.expense,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Monthly / Period Financial Overview (Income, Expenses, Savings)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(title = "${data.selectedDateRange.label} Overview")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OverviewStatCard(
                            title = "Income",
                            amount = Formatters.formatMoney(summary.totalIncome, settings.currencySymbol, settings.hideBalances),
                            accentColor = semantic.income,
                            containerColor = semantic.incomeContainer,
                            modifier = Modifier.weight(1f)
                        )
                        OverviewStatCard(
                            title = "Expenses",
                            amount = Formatters.formatMoney(summary.totalExpenses, settings.currencySymbol, settings.hideBalances),
                            accentColor = semantic.expense,
                            containerColor = semantic.expenseContainer,
                            modifier = Modifier.weight(1f)
                        )
                        OverviewStatCard(
                            title = "Savings",
                            amount = Formatters.formatMoney(summary.totalSavings, settings.currencySymbol, settings.hideBalances),
                            accentColor = semantic.transfer,
                            containerColor = semantic.transferContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Account Balance Overview
            item {
                val activeAccounts = data.accounts.filter { !it.archived }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        title = "Accounts Overview",
                        actionLabel = "Manage",
                        onActionClick = onOpenAccounts
                    )
                    if (activeAccounts.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Outlined.AccountBalanceWallet,
                            title = "No active accounts",
                            message = "Add a Cash, Bank, Mobile Wallet, or Credit Card account.",
                            actionLabel = "Add Account",
                            onAction = onOpenAccounts
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(activeAccounts, key = { it.id }) { account ->
                                val isCard = account.typeEnum() == AccountType.CREDIT_CARD
                                FinCard(
                                    onClick = { onOpenAccountDetail(account) },
                                    modifier = Modifier.width(176.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(Color(account.colorHex).copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = IconRegistry.resolve(account.iconKey),
                                                contentDescription = account.name,
                                                tint = Color(account.colorHex),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Text(
                                            text = account.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = if (isCard) "Outstanding Debt" else account.typeEnum().label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = semantic.textSecondary
                                    )
                                    Text(
                                        text = Formatters.formatMoney(account.currentBalance, settings.currencySymbol, settings.hideBalances),
                                        style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                                        color = if (isCard && account.currentBalance > 0) semantic.expense else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isCard && account.creditLimit > 0) {
                                        Text(
                                            text = "Avail: ${Formatters.formatMoney(account.availableCredit, settings.currencySymbol, settings.hideBalances)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = semantic.income
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Outstanding Money Lent & Borrowed Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FinCard(
                        onClick = { onOpenLendingBorrowing(0) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.AutoMirrored.Outlined.CallMade,
                                contentDescription = "Money Lent",
                                tint = semantic.warning,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Money Lent", style = MaterialTheme.typography.labelMedium, color = semantic.textSecondary)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = Formatters.formatMoney(summary.outstandingLent, settings.currencySymbol, settings.hideBalances),
                            style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                            color = semantic.warning
                        )
                        Text("Receivable", style = MaterialTheme.typography.labelSmall, color = semantic.textSecondary)
                    }

                    FinCard(
                        onClick = { onOpenLendingBorrowing(1) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.AutoMirrored.Outlined.CallReceived,
                                contentDescription = "Money Borrowed",
                                tint = semantic.expense,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Borrowed", style = MaterialTheme.typography.labelMedium, color = semantic.textSecondary)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = Formatters.formatMoney(summary.outstandingBorrowed, settings.currencySymbol, settings.hideBalances),
                            style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                            color = semantic.expense
                        )
                        Text("Payable", style = MaterialTheme.typography.labelSmall, color = semantic.textSecondary)
                    }
                }
            }

            // Budget Progress
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        title = "Budget Progress",
                        actionLabel = "All Budgets",
                        onActionClick = onOpenBudgets
                    )
                    if (summary.budgetProgress.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Outlined.PieChart,
                            title = "No budgets configured",
                            message = "Set monthly or category budgets with 50/75/90/100% alerts.",
                            actionLabel = "Create Budget",
                            onAction = onOpenBudgets
                        )
                    } else {
                        FinCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                summary.budgetProgress.take(3).forEach { bp ->
                                    BudgetProgressRow(
                                        progress = bp,
                                        currencySymbol = settings.currencySymbol,
                                        hideBalances = settings.hideBalances
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Spending by Category Doughnut
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(title = "Spending by Category")
                    FinCard(modifier = Modifier.fillMaxWidth()) {
                        if (summary.spendingByCategory.isEmpty()) {
                            Text(
                                text = "No expenses recorded in this period.",
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.textSecondary
                            )
                        } else {
                            CategoryDoughnutChart(
                                items = summary.spendingByCategory,
                                currencySymbol = settings.currencySymbol,
                                hideBalances = settings.hideBalances
                            )
                        }
                    }
                }
            }

            // Monthly Income vs Expense Chart
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(title = "Monthly Income vs Expense")
                    FinCard(modifier = Modifier.fillMaxWidth()) {
                        IncomeExpenseBarChart(series = summary.monthlyCashflow)
                    }
                }
            }

            // Recent Transactions
            item {
                SectionHeader(
                    title = "Recent Transactions",
                    actionLabel = "See All",
                    onActionClick = onOpenTransactions
                )
            }

            if (data.transactions.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = "No transactions yet",
                        message = "Tap + to record an expense, income, transfer, or loan.",
                        actionLabel = "Add Expense",
                        onAction = onQuickAddExpense
                    )
                }
            } else {
                items(data.transactions.take(6), key = { it.id }) { tx ->
                    TransactionRow(
                        tx = tx,
                        currencySymbol = settings.currencySymbol,
                        dateFormat = settings.dateFormat,
                        hideBalances = settings.hideBalances,
                        onClick = { onOpenTransactionDetail(tx) }
                    )
                }
            }
        }
    }

    if (showCustomRangeDialog) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = data.customStartMillis,
            initialSelectedEndDateMillis = data.customEndMillis
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showCustomRangeDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = pickerState.selectedStartDateMillis
                        val e = pickerState.selectedEndDateMillis
                        if (s != null && e != null) {
                            onSelectDateRange(DashboardDateRange.CUSTOM, s, e + 86_399_999L)
                        }
                        showCustomRangeDialog = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRangeDialog = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.height(420.dp))
        }
    }
}

@Composable
private fun MiniMetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = LocalFinLedgerColors.current.textSecondary)
            Text(value, style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize), color = color)
        }
    }
}

@Composable
private fun OverviewStatCard(
    title: String,
    amount: String,
    accentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LocalFinLedgerColors.current.cardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(color = containerColor, shape = RoundedCornerShape(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(
                text = amount,
                style = AmountTextStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
