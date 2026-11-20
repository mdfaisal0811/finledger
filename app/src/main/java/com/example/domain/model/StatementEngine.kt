package com.example.domain.model

import com.example.data.export.Formatters
import java.util.Calendar

enum class StatementPeriodMode(val label: String) {
    CUSTOM("Custom Date"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

enum class WeeklySelection(val label: String) {
    CURRENT_WEEK("Current Week"),
    PREVIOUS_WEEK("Previous Week"),
    SPECIFIC_WEEK("Select Week")
}

enum class MonthlySelection(val label: String) {
    CURRENT_MONTH("Current Month"),
    PREVIOUS_MONTH("Previous Month"),
    SELECT_MONTH_YEAR("Month & Year")
}

enum class YearlySelection(val label: String) {
    CURRENT_YEAR("Current Year"),
    PREVIOUS_YEAR("Previous Year"),
    SELECT_YEAR("Select Year")
}

data class StatementFilter(
    val periodMode: StatementPeriodMode = StatementPeriodMode.MONTHLY,
    val weeklySelection: WeeklySelection = WeeklySelection.CURRENT_WEEK,
    val specificWeekAnchorMillis: Long = System.currentTimeMillis(),
    val monthlySelection: MonthlySelection = MonthlySelection.CURRENT_MONTH,
    val selectedMonthZeroIndexed: Int = Calendar.getInstance().get(Calendar.MONTH),
    val selectedMonthYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val yearlySelection: YearlySelection = YearlySelection.CURRENT_YEAR,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val customStartMillis: Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis,
    val customEndMillis: Long = System.currentTimeMillis(),
    val selectedAccountId: String? = null // null = All Accounts
)

data class StatementRow(
    val transactionId: String,
    val occurredAtMillis: Long,
    val description: String,
    val categoryName: String,
    val transactionTypeLabel: String,
    val transactionType: TransactionType,
    val accountDisplay: String,
    val incomeAmount: Double,
    val expenseAmount: Double,
    val transferInAmount: Double,
    val transferOutAmount: Double,
    val chargeAmount: Double,
    val netBalanceDelta: Double,
    val runningBalance: Double
)

data class FinancialStatement(
    val title: String,
    val userName: String,
    val userEmail: String,
    val accountLabel: String,
    val accountId: String?,
    val isCreditCardStatement: Boolean,
    val startMillis: Long,
    val endMillis: Long,
    val dateRangeLabel: String,
    val generatedAtMillis: Long,
    val openingBalance: Double,
    val closingBalance: Double,
    val totalIncome: Double,
    val totalExpenses: Double,
    val netCashFlow: Double,
    val transferIn: Double,
    val transferOut: Double,
    val transferCharges: Double,
    val moneyLent: Double,
    val moneyBorrowed: Double,
    val repaymentsReceived: Double,
    val repaymentsPaid: Double,
    val totalRepayments: Double,
    val outstandingLending: Double,
    val outstandingBorrowing: Double,
    val rows: List<StatementRow>
)

object StatementEngine {

    fun resolvePeriodBounds(
        filter: StatementFilter,
        nowMillis: Long = System.currentTimeMillis()
    ): Triple<Long, Long, String> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (filter.periodMode) {
            StatementPeriodMode.CUSTOM -> {
                val s = startOfDayMillis(minOf(filter.customStartMillis, filter.customEndMillis))
                val e = endOfDayMillis(maxOf(filter.customStartMillis, filter.customEndMillis))
                Triple(s, e, "Custom Statement")
            }

            StatementPeriodMode.WEEKLY -> {
                val anchorCal = Calendar.getInstance().apply {
                    timeInMillis = when (filter.weeklySelection) {
                        WeeklySelection.CURRENT_WEEK -> nowMillis
                        WeeklySelection.PREVIOUS_WEEK -> nowMillis - 7L * 86_400_000L
                        WeeklySelection.SPECIFIC_WEEK -> filter.specificWeekAnchorMillis
                    }
                }
                val (s, e) = weekBoundsMillis(anchorCal.timeInMillis)
                val label = when (filter.weeklySelection) {
                    WeeklySelection.CURRENT_WEEK -> "Weekly Statement (Current Week)"
                    WeeklySelection.PREVIOUS_WEEK -> "Weekly Statement (Previous Week)"
                    WeeklySelection.SPECIFIC_WEEK -> "Weekly Statement"
                }
                Triple(s, e, label)
            }

            StatementPeriodMode.MONTHLY -> {
                val (year, month) = when (filter.monthlySelection) {
                    MonthlySelection.CURRENT_MONTH -> cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
                    MonthlySelection.PREVIOUS_MONTH -> {
                        val prev = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                        prev.get(Calendar.YEAR) to prev.get(Calendar.MONTH)
                    }
                    MonthlySelection.SELECT_MONTH_YEAR -> filter.selectedMonthYear to filter.selectedMonthZeroIndexed.coerceIn(0, 11)
                }
                val s = startOfMonthMillis(year, month)
                val e = endOfMonthMillis(year, month)
                val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                Triple(s, e, "Monthly Statement — ${monthNames[month]} $year")
            }

            StatementPeriodMode.YEARLY -> {
                val year = when (filter.yearlySelection) {
                    YearlySelection.CURRENT_YEAR -> cal.get(Calendar.YEAR)
                    YearlySelection.PREVIOUS_YEAR -> cal.get(Calendar.YEAR) - 1
                    YearlySelection.SELECT_YEAR -> filter.selectedYear
                }
                val s = Calendar.getInstance().apply {
                    set(year, Calendar.JANUARY, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val e = Calendar.getInstance().apply {
                    set(year, Calendar.DECEMBER, 31, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                Triple(s, e, "Yearly Statement — $year")
            }
        }
    }

    fun generateStatement(
        filter: StatementFilter,
        settings: UserSettings,
        accounts: List<Account>,
        transactions: List<TransactionRecord>,
        lendingRecords: List<LendingRecord>,
        borrowingRecords: List<BorrowingRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): FinancialStatement {
        val (startMillis, endMillis, baseTitle) = resolvePeriodBounds(filter, nowMillis)
        val accountsById = accounts.associateBy { it.id }
        val targetAccount = filter.selectedAccountId?.let { accountsById[it] }
        val isSingleAccount = targetAccount != null
        val isCreditCard = targetAccount?.typeEnum() == AccountType.CREDIT_CARD

        val dateRangeLabel = "${Formatters.formatDate(startMillis, settings.dateFormat)} – ${Formatters.formatDate(endMillis, settings.dateFormat)}"
        val accountLabel = if (targetAccount != null) {
            "${targetAccount.name} (${targetAccount.typeEnum().label})"
        } else {
            "All Accounts"
        }
        val title = if (targetAccount != null) "$baseTitle (${targetAccount.name})" else baseTitle

        // Compute deterministic balances before startMillis
        val beforeStartTxs = transactions.filter { it.occurredAtMillis < startMillis }
        val preBalances = FinancialEngine.recomputeAllAccountBalances(accounts, beforeStartTxs)

        val openingBalance = if (targetAccount != null) {
            preBalances[targetAccount.id] ?: targetAccount.openingBalance
        } else {
            val assets = accounts
                .filter { it.typeEnum() != AccountType.CREDIT_CARD }
                .sumOf { preBalances[it.id] ?: it.openingBalance }
            val debt = accounts
                .filter { it.typeEnum() == AccountType.CREDIT_CARD }
                .sumOf { preBalances[it.id] ?: it.openingBalance }
            assets - debt
        }

        // Chronological transactions inside [startMillis, endMillis]
        val periodTxs = transactions
            .filter { tx ->
                tx.occurredAtMillis in startMillis..endMillis &&
                    (!isSingleAccount || tx.accountId == targetAccount!!.id || tx.toAccountId == targetAccount.id)
            }
            .sortedWith(compareBy<TransactionRecord> { it.occurredAtMillis }.thenBy { it.id })

        var runningBalance = openingBalance
        var totalIncome = 0.0
        var totalExpenses = 0.0
        var transferIn = 0.0
        var transferOut = 0.0
        var transferCharges = 0.0
        var moneyLent = 0.0
        var moneyBorrowed = 0.0
        var repaymentsReceived = 0.0
        var repaymentsPaid = 0.0

        val rows = periodTxs.map { tx ->
            val txType = tx.typeEnum()
            val deltas = FinancialEngine.computeAccountDeltas(tx, accountsById)

            val rowDelta = if (targetAccount != null) {
                deltas[targetAccount.id] ?: 0.0
            } else {
                // Net position delta across all accounts: +Asset delta - Credit Card Debt delta
                var netDelta = 0.0
                deltas.forEach { (accId, d) ->
                    val acc = accountsById[accId]
                    if (acc?.typeEnum() == AccountType.CREDIT_CARD) {
                        netDelta -= d
                    } else if (acc != null) {
                        netDelta += d
                    }
                }
                netDelta
            }
            runningBalance += rowDelta

            var rowIncome = 0.0
            var rowExpense = 0.0
            var rowTransferIn = 0.0
            var rowTransferOut = 0.0
            var rowCharge = 0.0

            when (txType) {
                TransactionType.INCOME -> {
                    rowIncome = tx.amount
                    rowExpense = tx.chargeAmount
                    rowCharge = tx.chargeAmount
                    totalIncome += tx.amount
                    totalExpenses += tx.chargeAmount
                }

                TransactionType.EXPENSE -> {
                    val exp = tx.amount + tx.chargeAmount
                    rowExpense = exp
                    rowCharge = tx.chargeAmount
                    totalExpenses += exp
                }

                TransactionType.TRANSFER, TransactionType.CREDIT_CARD_PAYMENT -> {
                    if (targetAccount != null) {
                        if (tx.accountId == targetAccount.id) {
                            rowTransferOut = tx.amount
                            rowCharge = tx.chargeAmount
                            rowExpense = tx.chargeAmount
                            transferOut += tx.amount
                            transferCharges += tx.chargeAmount
                            totalExpenses += tx.chargeAmount
                        }
                        if (tx.toAccountId == targetAccount.id) {
                            rowTransferIn = tx.amount
                            transferIn += tx.amount
                        }
                    } else {
                        // All Accounts statement:
                        // Internal transfers do NOT count as income or expenses and do NOT double-count net balance.
                        // Transfer charges ARE included in expenses.
                        rowTransferIn = tx.amount
                        rowTransferOut = tx.amount
                        rowCharge = tx.chargeAmount
                        rowExpense = tx.chargeAmount
                        transferIn += tx.amount
                        transferOut += tx.amount
                        transferCharges += tx.chargeAmount
                        totalExpenses += tx.chargeAmount
                    }
                }

                TransactionType.LENDING -> {
                    // Lent money is NOT counted as an ordinary expense
                    moneyLent += tx.amount
                }

                TransactionType.BORROWING -> {
                    // Borrowed money is NOT counted as income
                    moneyBorrowed += tx.amount
                }

                TransactionType.LENT_REPAYMENT -> {
                    repaymentsReceived += tx.amount
                }

                TransactionType.BORROWED_REPAYMENT -> {
                    repaymentsPaid += tx.amount
                }
            }

            val accountDisplay = when (txType) {
                TransactionType.TRANSFER, TransactionType.CREDIT_CARD_PAYMENT ->
                    "${tx.accountName} → ${tx.toAccountName ?: "Account"}"
                else -> tx.accountName
            }

            val categoryLabel = when {
                !tx.categoryName.isNullOrBlank() -> tx.categoryName
                txType == TransactionType.TRANSFER -> "Internal Transfer"
                txType == TransactionType.CREDIT_CARD_PAYMENT -> "Card Bill Payment"
                txType == TransactionType.LENDING -> "Money Lent"
                txType == TransactionType.BORROWING -> "Money Borrowed"
                txType == TransactionType.LENT_REPAYMENT -> "Lent Return"
                txType == TransactionType.BORROWED_REPAYMENT -> "Debt Repaid"
                else -> "Uncategorized"
            }

            val desc = tx.description.ifBlank {
                tx.counterpartyName?.let { "${txType.label}: $it" } ?: categoryLabel
            }

            StatementRow(
                transactionId = tx.id,
                occurredAtMillis = tx.occurredAtMillis,
                description = desc,
                categoryName = categoryLabel,
                transactionTypeLabel = txType.label,
                transactionType = txType,
                accountDisplay = accountDisplay,
                incomeAmount = rowIncome,
                expenseAmount = rowExpense,
                transferInAmount = rowTransferIn,
                transferOutAmount = rowTransferOut,
                chargeAmount = rowCharge,
                netBalanceDelta = rowDelta,
                runningBalance = runningBalance
            )
        }

        val totalRepayments = repaymentsReceived + repaymentsPaid
        val outstandingLending = lendingRecords
            .filter { targetAccount == null || it.accountId == targetAccount.id }
            .sumOf { it.outstandingAmount }
        val outstandingBorrowing = borrowingRecords
            .filter { targetAccount == null || it.accountId == targetAccount.id }
            .sumOf { it.outstandingDebt }

        return FinancialStatement(
            title = title,
            userName = settings.displayName.ifBlank { "FinLedger User" },
            userEmail = settings.email,
            accountLabel = accountLabel,
            accountId = targetAccount?.id,
            isCreditCardStatement = isCreditCard,
            startMillis = startMillis,
            endMillis = endMillis,
            dateRangeLabel = dateRangeLabel,
            generatedAtMillis = nowMillis,
            openingBalance = openingBalance,
            closingBalance = runningBalance,
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            netCashFlow = totalIncome - totalExpenses,
            transferIn = transferIn,
            transferOut = transferOut,
            transferCharges = transferCharges,
            moneyLent = moneyLent,
            moneyBorrowed = moneyBorrowed,
            repaymentsReceived = repaymentsReceived,
            repaymentsPaid = repaymentsPaid,
            totalRepayments = totalRepayments,
            outstandingLending = outstandingLending,
            outstandingBorrowing = outstandingBorrowing,
            rows = rows
        )
    }

    fun weekBoundsMillis(anchorMillis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = anchorMillis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis > anchorMillis) {
            cal.add(Calendar.DAY_OF_YEAR, -7)
        }
        val endCal = (cal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 6)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis to endCal.timeInMillis
    }

    private fun startOfDayMillis(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun endOfDayMillis(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    private fun startOfMonthMillis(year: Int, monthZeroIndexed: Int): Long =
        Calendar.getInstance().apply {
            set(year, monthZeroIndexed, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun endOfMonthMillis(year: Int, monthZeroIndexed: Int): Long =
        Calendar.getInstance().apply {
            set(year, monthZeroIndexed, 1, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }.timeInMillis
}
