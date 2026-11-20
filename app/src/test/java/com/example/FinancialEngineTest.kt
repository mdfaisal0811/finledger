package com.example

import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.BorrowingRecord
import com.example.domain.model.Budget
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.model.DashboardDateRange
import com.example.domain.model.FinancialEngine
import com.example.domain.model.LendingRecord
import com.example.domain.model.LoanStatus
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialEngineTest {

    private val bank = Account(
        id = "acc_bank",
        name = "DBBL Bank",
        type = AccountType.BANK.name,
        openingBalance = 50_000.0,
        currentBalance = 50_000.0
    )

    private val cash = Account(
        id = "acc_cash",
        name = "Cash Wallet",
        type = AccountType.CASH.name,
        openingBalance = 2_000.0,
        currentBalance = 2_000.0
    )

    private val savings = Account(
        id = "acc_sav",
        name = "DPS Savings",
        type = AccountType.SAVINGS.name,
        openingBalance = 15_000.0,
        currentBalance = 15_000.0
    )

    private val card = Account(
        id = "acc_card",
        name = "Visa Platinum",
        type = AccountType.CREDIT_CARD.name,
        openingBalance = 1_000.0,
        currentBalance = 1_000.0,
        creditLimit = 100_000.0
    )

    private val accounts = listOf(bank, cash, savings, card)

    @Test
    fun `transfer with charge updates source and destination accurately without distorting income or expense`() {
        val transfer = TransactionRecord(
            id = "tx_1",
            type = TransactionType.TRANSFER.name,
            amount = 10_000.0,
            chargeAmount = 20.0,
            accountId = bank.id,
            accountName = bank.name,
            toAccountId = cash.id,
            toAccountName = cash.name,
            description = "Bank to Cash withdrawal",
            occurredAtMillis = System.currentTimeMillis()
        )

        val balances = FinancialEngine.recomputeAllAccountBalances(accounts, listOf(transfer))
        assertEquals(39_980.0, balances[bank.id]!!, 0.001)
        assertEquals(12_000.0, balances[cash.id]!!, 0.001)
        assertEquals(0.0, FinancialEngine.incomeContribution(transfer), 0.001)
        assertEquals(20.0, FinancialEngine.expenseContribution(transfer), 0.001)
    }

    @Test
    fun `atomic transfer edit reverses old accounts and applies new amount charge and destination`() {
        val accountsById = accounts.associateBy { it.id }
        val oldTransfer = TransactionRecord(
            id = "tx_1",
            type = TransactionType.TRANSFER.name,
            amount = 10_000.0,
            chargeAmount = 20.0,
            accountId = bank.id,
            accountName = bank.name,
            toAccountId = cash.id,
            toAccountName = cash.name
        )
        // After old transfer: bank=39980, cash=12000, savings=15000
        val currentMap = accountsById.mapValues { (id, acc) ->
            when (id) {
                bank.id -> acc.copy(currentBalance = 39_980.0)
                cash.id -> acc.copy(currentBalance = 12_000.0)
                else -> acc
            }
        }

        // Edit: change amount to 5,000, charge to 15, destination to savings
        val updatedTransfer = oldTransfer.copy(
            amount = 5_000.0,
            chargeAmount = 15.0,
            toAccountId = savings.id,
            toAccountName = savings.name
        )

        val deltas = FinancialEngine.computeEditDeltas(oldTransfer, updatedTransfer, currentMap)
        assertEquals(5_005.0, deltas[bank.id]!!, 0.001) // 39980 + 5005 = 44985
        assertEquals(-10_000.0, deltas[cash.id]!!, 0.001) // 12000 - 10000 = 2000
        assertEquals(5_000.0, deltas[savings.id]!!, 0.001) // 15000 + 5000 = 20000

        // Must match full recomputation from opening balances
        val recomputed = FinancialEngine.recomputeAllAccountBalances(accounts, listOf(updatedTransfer))
        assertEquals(44_985.0, recomputed[bank.id]!!, 0.001)
        assertEquals(2_000.0, recomputed[cash.id]!!, 0.001)
        assertEquals(20_000.0, recomputed[savings.id]!!, 0.001)
    }

    @Test
    fun `credit card purchase increases debt and bill payment reduces bank and debt without double counting expense`() {
        val now = System.currentTimeMillis()
        val purchase = TransactionRecord(
            id = "tx_purchase",
            type = TransactionType.EXPENSE.name,
            amount = 4_500.0,
            chargeAmount = 0.0,
            accountId = card.id,
            accountName = card.name,
            categoryId = "cat_food",
            categoryName = "Food & Dining",
            occurredAtMillis = now
        )
        val cardPayment = TransactionRecord(
            id = "tx_pay",
            type = TransactionType.CREDIT_CARD_PAYMENT.name,
            amount = 3_000.0,
            chargeAmount = 10.0,
            accountId = bank.id,
            accountName = bank.name,
            toAccountId = card.id,
            toAccountName = card.name,
            occurredAtMillis = now + 1000
        )

        val balances = FinancialEngine.recomputeAllAccountBalances(accounts, listOf(purchase, cardPayment))
        // Card opened at 1,000 debt + 4,500 purchase - 3,000 payment = 2,500 debt
        assertEquals(2_500.0, balances[card.id]!!, 0.001)
        // Bank opened at 50,000 - 3,000 payment - 10 charge = 46,990
        assertEquals(46_990.0, balances[bank.id]!!, 0.001)

        val updatedCard = card.copy(currentBalance = balances[card.id]!!)
        assertEquals(97_500.0, updatedCard.availableCredit, 0.001)

        // Expenses count 4,500 purchase + 10 payment charge = 4,510 (NOT 4500 + 3000)
        val summary = FinancialEngine.computeDashboardSummary(
            accounts = listOf(bank.copy(currentBalance = balances[bank.id]!!), updatedCard),
            allTransactions = listOf(purchase, cardPayment),
            categories = listOf(Category(id = "cat_food", name = "Food & Dining", type = CategoryType.EXPENSE.name)),
            budgets = emptyList(),
            lendingRecords = emptyList(),
            borrowingRecords = emptyList(),
            startMillis = now - 60_000,
            endMillis = now + 60_000
        )
        assertEquals(4_510.0, summary.totalExpenses, 0.001)
        assertEquals(46_990.0 - 2_500.0, summary.totalNetBalance, 0.001)
    }

    @Test
    fun `lending borrowing and repayments update account balances while excluding from income and expense`() {
        val now = System.currentTimeMillis()
        val lentTx = TransactionRecord(
            id = "tx_lend",
            type = TransactionType.LENDING.name,
            amount = 5_000.0,
            accountId = cash.id,
            occurredAtMillis = now
        )
        val lentRepayTx = TransactionRecord(
            id = "tx_lend_rep",
            type = TransactionType.LENT_REPAYMENT.name,
            amount = 2_000.0,
            accountId = bank.id,
            occurredAtMillis = now + 100
        )
        val borrowTx = TransactionRecord(
            id = "tx_borrow",
            type = TransactionType.BORROWING.name,
            amount = 8_000.0,
            accountId = bank.id,
            occurredAtMillis = now + 200
        )
        val borrowRepayTx = TransactionRecord(
            id = "tx_borrow_rep",
            type = TransactionType.BORROWED_REPAYMENT.name,
            amount = 3_000.0,
            accountId = bank.id,
            occurredAtMillis = now + 300
        )

        val txs = listOf(lentTx, lentRepayTx, borrowTx, borrowRepayTx)
        val balances = FinancialEngine.recomputeAllAccountBalances(accounts, txs)
        // Cash: 2000 - 5000 = -3000 (or allowed negative if overdraft, engine accurately tracks delta)
        assertEquals(-3_000.0, balances[cash.id]!!, 0.001)
        // Bank: 50000 + 2000 + 8000 - 3000 = 57000
        assertEquals(57_000.0, balances[bank.id]!!, 0.001)

        txs.forEach { tx ->
            assertEquals(0.0, FinancialEngine.incomeContribution(tx), 0.001)
            assertEquals(0.0, FinancialEngine.expenseContribution(tx), 0.001)
        }

        val lending = LendingRecord(id = "l1", personName = "Rahim", principalAmount = 5_000.0, returnedAmount = 2_000.0, status = LoanStatus.PARTIAL.name)
        val borrowing = BorrowingRecord(id = "b1", personName = "Karim", principalAmount = 8_000.0, repaidAmount = 3_000.0, status = LoanStatus.PARTIAL.name)
        assertEquals(3_000.0, lending.outstandingAmount, 0.001)
        assertEquals(5_000.0, borrowing.outstandingDebt, 0.001)
    }

    @Test
    fun `budget progress detects category spending and threshold crossings`() {
        val (monthStart, monthEnd) = FinancialEngine.getDateRangeBounds(DashboardDateRange.THIS_MONTH)
        val budget = Budget(
            id = "b_food",
            name = "Food Budget",
            periodType = BudgetPeriodType.MONTHLY.name,
            categoryId = "cat_food",
            categoryName = "Food",
            limitAmount = 1_000.0,
            startDateMillis = monthStart,
            endDateMillis = monthEnd,
            alertThresholds = listOf(50, 75, 90, 100),
            active = true
        )
        val txs = listOf(
            TransactionRecord(
                id = "tx1",
                type = TransactionType.EXPENSE.name,
                amount = 760.0,
                chargeAmount = 40.0,
                accountId = cash.id,
                categoryId = "cat_food",
                occurredAtMillis = monthStart + 10_000
            ),
            TransactionRecord(
                id = "tx2",
                type = TransactionType.EXPENSE.name,
                amount = 500.0,
                accountId = cash.id,
                categoryId = "cat_other",
                occurredAtMillis = monthStart + 10_000
            )
        )

        val progress = FinancialEngine.computeBudgetProgress(listOf(budget), txs).single()
        assertEquals(800.0, progress.spentAmount, 0.001)
        assertEquals(200.0, progress.remainingAmount, 0.001)
        assertEquals(0.8f, progress.progressRatio, 0.001f)
        assertEquals(75, progress.highestCrossedThreshold)
        assertTrue(progress.spentAmount < budget.limitAmount)
    }
}
