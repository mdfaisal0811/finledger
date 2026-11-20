package com.example.domain.model

import java.util.Calendar
import kotlin.math.abs

data class CategorySpending(
    val categoryId: String,
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val colorHex: Long
)

data class MonthlyCashflow(
    val label: String,
    val year: Int,
    val monthZeroIndexed: Int,
    val income: Double,
    val expense: Double
)

data class BudgetProgress(
    val budget: Budget,
    val spentAmount: Double,
    val remainingAmount: Double,
    val progressRatio: Float,
    val highestCrossedThreshold: Int?
)

data class AccountSummaryItem(
    val account: Account,
    val totalIncome: Double,
    val totalExpense: Double,
    val totalTransfersIn: Double,
    val totalTransfersOut: Double
)

data class DashboardSummary(
    val totalNetBalance: Double,
    val totalAssets: Double,
    val totalCreditCardDebt: Double,
    val totalIncome: Double,
    val totalExpenses: Double,
    val totalSavings: Double,
    val savingsAccountsBalance: Double,
    val totalTransferCharges: Double,
    val outstandingLent: Double,
    val outstandingBorrowed: Double,
    val spendingByCategory: List<CategorySpending>,
    val monthlyCashflow: List<MonthlyCashflow>,
    val budgetProgress: List<BudgetProgress>
)

object FinancialEngine {

    /**
     * Computes the signed delta on an account's `currentBalance` when applying a transaction.
     * Note:
     * - For non-credit-card accounts, `currentBalance` is cash/asset balance (+ increases balance, - decreases balance).
     * - For CREDIT_CARD accounts, `currentBalance` is outstanding debt (+ increases debt, - decreases debt).
     */
    fun computeAccountDeltas(
        tx: TransactionRecord,
        accountsById: Map<String, Account>
    ): Map<String, Double> {
        val deltas = mutableMapOf<String, Double>()
        val sourceAccount = accountsById[tx.accountId]
        val isSourceCard = sourceAccount?.typeEnum() == AccountType.CREDIT_CARD
        val destAccount = tx.toAccountId?.let { accountsById[it] }
        val isDestCard = destAccount?.typeEnum() == AccountType.CREDIT_CARD

        fun addDelta(accountId: String, delta: Double) {
            if (accountId.isBlank() || abs(delta) < 1e-9) return
            deltas[accountId] = (deltas[accountId] ?: 0.0) + delta
        }

        when (tx.typeEnum()) {
            TransactionType.INCOME -> {
                val netAmount = (tx.amount - tx.chargeAmount).coerceAtLeast(0.0)
                if (isSourceCard) {
                    // Refund/income into a credit card reduces outstanding debt
                    addDelta(tx.accountId, -netAmount)
                } else {
                    addDelta(tx.accountId, netAmount)
                }
            }

            TransactionType.EXPENSE -> {
                val totalExpense = tx.amount + tx.chargeAmount
                if (isSourceCard) {
                    // Credit card purchase increases outstanding debt
                    addDelta(tx.accountId, totalExpense)
                } else {
                    addDelta(tx.accountId, -totalExpense)
                }
            }

            TransactionType.TRANSFER -> {
                val totalDeducted = tx.amount + tx.chargeAmount
                if (isSourceCard) {
                    // Cash advance / transfer from credit card increases debt
                    addDelta(tx.accountId, totalDeducted)
                } else {
                    addDelta(tx.accountId, -totalDeducted)
                }
                val toId = tx.toAccountId
                if (!toId.isNullOrBlank()) {
                    if (isDestCard) {
                        // Transferring into a credit card reduces its debt
                        addDelta(toId, -tx.amount)
                    } else {
                        addDelta(toId, tx.amount)
                    }
                }
            }

            TransactionType.CREDIT_CARD_PAYMENT -> {
                val totalPaid = tx.amount + tx.chargeAmount
                if (isSourceCard) {
                    addDelta(tx.accountId, totalPaid)
                } else {
                    addDelta(tx.accountId, -totalPaid)
                }
                val cardId = tx.toAccountId
                if (!cardId.isNullOrBlank()) {
                    // Paying credit card bill reduces card's outstanding debt
                    addDelta(cardId, -tx.amount)
                }
            }

            TransactionType.LENDING -> {
                if (isSourceCard) {
                    addDelta(tx.accountId, tx.amount)
                } else {
                    addDelta(tx.accountId, -tx.amount)
                }
            }

            TransactionType.BORROWING -> {
                if (isSourceCard) {
                    addDelta(tx.accountId, -tx.amount)
                } else {
                    addDelta(tx.accountId, tx.amount)
                }
            }

            TransactionType.LENT_REPAYMENT -> {
                if (isSourceCard) {
                    addDelta(tx.accountId, -tx.amount)
                } else {
                    addDelta(tx.accountId, tx.amount)
                }
            }

            TransactionType.BORROWED_REPAYMENT -> {
                if (isSourceCard) {
                    addDelta(tx.accountId, tx.amount)
                } else {
                    addDelta(tx.accountId, -tx.amount)
                }
            }
        }
        return deltas
    }

    /**
     * Combines reversing `oldTx` and applying `newTx` into net balance deltas per account ID.
     */
    fun computeEditDeltas(
        oldTx: TransactionRecord,
        newTx: TransactionRecord,
        accountsById: Map<String, Account>
    ): Map<String, Double> {
        val combined = mutableMapOf<String, Double>()
        computeAccountDeltas(oldTx, accountsById).forEach { (id, delta) ->
            combined[id] = (combined[id] ?: 0.0) - delta
        }
        computeAccountDeltas(newTx, accountsById).forEach { (id, delta) ->
            combined[id] = (combined[id] ?: 0.0) + delta
        }
        return combined.filterValues { abs(it) >= 1e-9 }
    }

    /**
     * Recomputes every account's balance deterministically from openingBalance + all transactions.
     */
    fun recomputeAllAccountBalances(
        accounts: List<Account>,
        transactions: List<TransactionRecord>
    ): Map<String, Double> {
        val accountsById = accounts.associateBy { it.id }
        val balances = accounts.associate { it.id to it.openingBalance }.toMutableMap()
        transactions.sortedBy { it.occurredAtMillis }.forEach { tx ->
            computeAccountDeltas(tx, accountsById).forEach { (accountId, delta) ->
                if (balances.containsKey(accountId)) {
                    balances[accountId] = (balances[accountId] ?: 0.0) + delta
                }
            }
        }
        return balances
    }

    fun isIncomeTransaction(tx: TransactionRecord): Boolean =
        tx.typeEnum() == TransactionType.INCOME

    /**
     * Expense amount contributed by a transaction:
     * - EXPENSE: amount + chargeAmount
     * - TRANSFER: chargeAmount ONLY (transfer principal is NEVER an expense)
     * - CREDIT_CARD_PAYMENT: chargeAmount ONLY (card bill payment is NEVER a second expense)
     * - INCOME: chargeAmount ONLY (optional deduction/fee)
     * - LENDING / BORROWING / REPAYMENTS: 0.0 (not ordinary income/expense)
     */
    fun expenseContribution(tx: TransactionRecord): Double = when (tx.typeEnum()) {
        TransactionType.EXPENSE -> tx.amount + tx.chargeAmount
        TransactionType.TRANSFER,
        TransactionType.CREDIT_CARD_PAYMENT,
        TransactionType.INCOME -> tx.chargeAmount
        TransactionType.LENDING,
        TransactionType.BORROWING,
        TransactionType.LENT_REPAYMENT,
        TransactionType.BORROWED_REPAYMENT -> 0.0
    }

    fun incomeContribution(tx: TransactionRecord): Double = when (tx.typeEnum()) {
        TransactionType.INCOME -> tx.amount
        else -> 0.0
    }

    fun computeBudgetSpent(
        budget: Budget,
        transactions: List<TransactionRecord>
    ): Double {
        val (periodStart, periodEnd) = resolveBudgetWindow(budget)
        return transactions.asSequence()
            .filter { it.occurredAtMillis in periodStart..periodEnd }
            .sumOf { tx ->
                if (budget.categoryId.isNullOrBlank()) {
                    expenseContribution(tx)
                } else {
                    if (tx.typeEnum() == TransactionType.EXPENSE && tx.categoryId == budget.categoryId) {
                        tx.amount + tx.chargeAmount
                    } else {
                        0.0
                    }
                }
            }
    }

    fun resolveBudgetWindow(budget: Budget, nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
        return when (budget.periodTypeEnum()) {
            BudgetPeriodType.WEEKLY -> getDateRangeBounds(DashboardDateRange.THIS_WEEK, nowMillis = nowMillis)
            BudgetPeriodType.MONTHLY -> getDateRangeBounds(DashboardDateRange.THIS_MONTH, nowMillis = nowMillis)
            BudgetPeriodType.CUSTOM -> budget.startDateMillis to budget.endDateMillis
        }
    }

    fun computeBudgetProgress(
        budgets: List<Budget>,
        transactions: List<TransactionRecord>
    ): List<BudgetProgress> {
        return budgets.filter { it.active }.map { budget ->
            val spent = computeBudgetSpent(budget, transactions)
            val remaining = (budget.limitAmount - spent)
            val ratio = if (budget.limitAmount > 0.0) (spent / budget.limitAmount).toFloat() else 0f
            val pct = (ratio * 100f).toInt()
            val crossed = budget.alertThresholds.sorted().lastOrNull { pct >= it }
            BudgetProgress(
                budget = budget,
                spentAmount = spent,
                remainingAmount = remaining,
                progressRatio = ratio.coerceAtLeast(0f),
                highestCrossedThreshold = crossed
            )
        }
    }

    fun computeDashboardSummary(
        accounts: List<Account>,
        allTransactions: List<TransactionRecord>,
        categories: List<Category>,
        budgets: List<Budget>,
        lendingRecords: List<LendingRecord>,
        borrowingRecords: List<BorrowingRecord>,
        startMillis: Long,
        endMillis: Long
    ): DashboardSummary {
        val activeAccounts = accounts.filter { !it.archived }
        val totalAssets = activeAccounts
            .filter { it.typeEnum() != AccountType.CREDIT_CARD }
            .sumOf { it.currentBalance }
        val totalCardDebt = activeAccounts
            .filter { it.typeEnum() == AccountType.CREDIT_CARD }
            .sumOf { it.currentBalance }
        val totalNetBalance = totalAssets - totalCardDebt
        val savingsAccountsBalance = activeAccounts
            .filter { it.typeEnum() == AccountType.SAVINGS }
            .sumOf { it.currentBalance }

        val periodTransactions = allTransactions.filter {
            it.occurredAtMillis in startMillis..endMillis
        }

        val totalIncome = periodTransactions.sumOf { incomeContribution(it) }
        val totalExpenses = periodTransactions.sumOf { expenseContribution(it) }
        val totalSavings = totalIncome - totalExpenses
        val totalTransferCharges = periodTransactions
            .filter { it.typeEnum() == TransactionType.TRANSFER || it.typeEnum() == TransactionType.CREDIT_CARD_PAYMENT }
            .sumOf { it.chargeAmount }

        val categoriesById = categories.associateBy { it.id }
        val categoryTotals = mutableMapOf<String, Pair<String, Double>>()
        periodTransactions.forEach { tx ->
            val exp = expenseContribution(tx)
            if (exp > 0.0) {
                when (tx.typeEnum()) {
                    TransactionType.EXPENSE -> {
                        val catId = tx.categoryId ?: "uncategorized"
                        val catName = tx.categoryName ?: categoriesById[catId]?.name ?: "Other"
                        val prev = categoryTotals[catId]?.second ?: 0.0
                        categoryTotals[catId] = catName to (prev + exp)
                    }
                    TransactionType.TRANSFER, TransactionType.CREDIT_CARD_PAYMENT, TransactionType.INCOME -> {
                        val key = "transfer_charges"
                        val prev = categoryTotals[key]?.second ?: 0.0
                        categoryTotals[key] = "Transfer & Fees" to (prev + exp)
                    }
                    else -> Unit
                }
            }
        }

        val spendingList = categoryTotals.entries
            .sortedByDescending { it.value.second }
            .map { (catId, pair) ->
                val color = categoriesById[catId]?.colorHex
                    ?: if (catId == "transfer_charges") 0xFF2563EB else 0xFF64748B
                val pct = if (totalExpenses > 0.0) (pair.second / totalExpenses).toFloat() else 0f
                CategorySpending(
                    categoryId = catId,
                    categoryName = pair.first,
                    amount = pair.second,
                    percentage = pct,
                    colorHex = color
                )
            }

        val monthlyCashflow = computeMonthlySeries(allTransactions, monthsCount = 6)
        val budgetProgress = computeBudgetProgress(budgets, allTransactions)
        val outstandingLent = lendingRecords.sumOf { it.outstandingAmount }
        val outstandingBorrowed = borrowingRecords.sumOf { it.outstandingDebt }

        return DashboardSummary(
            totalNetBalance = totalNetBalance,
            totalAssets = totalAssets,
            totalCreditCardDebt = totalCardDebt,
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            totalSavings = totalSavings,
            savingsAccountsBalance = savingsAccountsBalance,
            totalTransferCharges = totalTransferCharges,
            outstandingLent = outstandingLent,
            outstandingBorrowed = outstandingBorrowed,
            spendingByCategory = spendingList,
            monthlyCashflow = monthlyCashflow,
            budgetProgress = budgetProgress
        )
    }

    fun computeMonthlySeries(
        transactions: List<TransactionRecord>,
        monthsCount: Int = 6,
        nowMillis: Long = System.currentTimeMillis()
    ): List<MonthlyCashflow> {
        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val result = mutableListOf<MonthlyCashflow>()
        for (i in (monthsCount - 1) downTo 0) {
            val c = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -i)
            }
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val start = startOfMonthMillis(year, month)
            val end = endOfMonthMillis(year, month)
            val monthTxs = transactions.filter { it.occurredAtMillis in start..end }
            result.add(
                MonthlyCashflow(
                    label = monthNames[month],
                    year = year,
                    monthZeroIndexed = month,
                    income = monthTxs.sumOf { incomeContribution(it) },
                    expense = monthTxs.sumOf { expenseContribution(it) }
                )
            )
        }
        return result
    }

    fun computeAccountSummary(
        account: Account,
        transactions: List<TransactionRecord>
    ): AccountSummaryItem {
        var income = 0.0
        var expense = 0.0
        var transfersIn = 0.0
        var transfersOut = 0.0
        transactions.forEach { tx ->
            if (tx.accountId == account.id) {
                when (tx.typeEnum()) {
                    TransactionType.INCOME -> {
                        income += tx.amount
                        expense += tx.chargeAmount
                    }
                    TransactionType.EXPENSE -> expense += tx.amount + tx.chargeAmount
                    TransactionType.TRANSFER -> {
                        transfersOut += tx.amount
                        expense += tx.chargeAmount
                    }
                    TransactionType.CREDIT_CARD_PAYMENT -> {
                        transfersOut += tx.amount
                        expense += tx.chargeAmount
                    }
                    else -> Unit
                }
            }
            if (tx.toAccountId == account.id) {
                if (tx.typeEnum() == TransactionType.TRANSFER || tx.typeEnum() == TransactionType.CREDIT_CARD_PAYMENT) {
                    transfersIn += tx.amount
                }
            }
        }
        return AccountSummaryItem(
            account = account,
            totalIncome = income,
            totalExpense = expense,
            totalTransfersIn = transfersIn,
            totalTransfersOut = transfersOut
        )
    }

    fun getDateRangeBounds(
        range: DashboardDateRange,
        customStart: Long? = null,
        customEnd: Long? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (range) {
            DashboardDateRange.TODAY -> {
                val start = (cal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val end = (cal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                start to end
            }
            DashboardDateRange.THIS_WEEK -> {
                val startCal = (cal.clone() as Calendar).apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (startCal.timeInMillis > nowMillis) {
                    startCal.add(Calendar.DAY_OF_YEAR, -7)
                }
                val endCal = (startCal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, 6)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                startCal.timeInMillis to endCal.timeInMillis
            }
            DashboardDateRange.THIS_MONTH -> {
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH)
                startOfMonthMillis(year, month) to endOfMonthMillis(year, month)
            }
            DashboardDateRange.THIS_YEAR -> {
                val start = (cal.clone() as Calendar).apply {
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val end = (cal.clone() as Calendar).apply {
                    set(Calendar.MONTH, Calendar.DECEMBER)
                    set(Calendar.DAY_OF_MONTH, 31)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                start to end
            }
            DashboardDateRange.CUSTOM -> {
                val start = customStart ?: startOfMonthMillis(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                val end = customEnd ?: nowMillis
                minOf(start, end) to maxOf(start, end)
            }
        }
    }

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
