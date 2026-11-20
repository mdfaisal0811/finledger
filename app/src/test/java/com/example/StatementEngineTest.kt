package com.example

import com.example.data.export.ExportManager
import com.example.data.media.ReceiptImageManager
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.BorrowingRecord
import com.example.domain.model.LendingRecord
import com.example.domain.model.LoanStatus
import com.example.domain.model.MonthlySelection
import com.example.domain.model.StatementEngine
import com.example.domain.model.StatementFilter
import com.example.domain.model.StatementPeriodMode
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import com.example.domain.model.UserSettings
import com.example.domain.model.WeeklySelection
import com.example.domain.model.YearlySelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StatementEngineTest {

    private val bank = Account(
        id = "acc_bank",
        name = "DBBL Bank",
        type = AccountType.BANK.name,
        openingBalance = 10_000.0,
        currentBalance = 10_000.0
    )

    private val cash = Account(
        id = "acc_cash",
        name = "Cash Wallet",
        type = AccountType.CASH.name,
        openingBalance = 2_000.0,
        currentBalance = 2_000.0
    )

    private val wallet = Account(
        id = "acc_bkash",
        name = "bKash Wallet",
        type = AccountType.MOBILE_WALLET.name,
        openingBalance = 1_500.0,
        currentBalance = 1_500.0
    )

    private val card = Account(
        id = "acc_card",
        name = "Visa Card",
        type = AccountType.CREDIT_CARD.name,
        openingBalance = 500.0,
        currentBalance = 500.0,
        creditLimit = 50_000.0
    )

    private val accounts = listOf(bank, cash, wallet, card)

    private val settings = UserSettings(
        userId = "user_1",
        displayName = "Amina Rahman",
        email = "amina@example.com",
        currencySymbol = "$",
        dateFormat = "dd MMM yyyy"
    )

    private fun dateMillis(year: Int, monthZeroIndexed: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            set(year, monthZeroIndexed, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `all accounts monthly statement applies strict accounting rules and running balance`() {
        // Prior month transaction: +5000 salary into bank before April
        val priorSalary = TransactionRecord(
            id = "tx_prior",
            type = TransactionType.INCOME.name,
            amount = 5_000.0,
            accountId = bank.id,
            accountName = bank.name,
            categoryName = "Salary",
            description = "March Bonus",
            occurredAtMillis = dateMillis(2025, Calendar.MARCH, 28)
        )

        // April period transactions:
        val salary = TransactionRecord(
            id = "tx_1",
            type = TransactionType.INCOME.name,
            amount = 4_000.0,
            accountId = bank.id,
            accountName = bank.name,
            categoryName = "Salary",
            description = "April Salary",
            occurredAtMillis = dateMillis(2025, Calendar.APRIL, 2)
        )
        val transfer = TransactionRecord(
            id = "tx_2",
            type = TransactionType.TRANSFER.name,
            amount = 1_000.0,
            chargeAmount = 15.0,
            accountId = bank.id,
            accountName = bank.name,
            toAccountId = wallet.id,
            toAccountName = wallet.name,
            description = "Top up bKash",
            occurredAtMillis = dateMillis(2025, Calendar.APRIL, 5)
        )
        val cardPurchase = TransactionRecord(
            id = "tx_3",
            type = TransactionType.EXPENSE.name,
            amount = 600.0,
            accountId = card.id,
            accountName = card.name,
            categoryName = "Dining",
            description = "Dinner out",
            occurredAtMillis = dateMillis(2025, Calendar.APRIL, 8)
        )
        val cardPayment = TransactionRecord(
            id = "tx_4",
            type = TransactionType.CREDIT_CARD_PAYMENT.name,
            amount = 700.0,
            chargeAmount = 5.0,
            accountId = bank.id,
            accountName = bank.name,
            toAccountId = card.id,
            toAccountName = card.name,
            description = "Visa Bill Payment",
            occurredAtMillis = dateMillis(2025, Calendar.APRIL, 10)
        )
        val moneyLent = TransactionRecord(
            id = "tx_5",
            type = TransactionType.LENDING.name,
            amount = 800.0,
            accountId = cash.id,
            accountName = cash.name,
            counterpartyName = "Rafi",
            description = "Lent to Rafi",
            occurredAtMillis = dateMillis(2025, Calendar.APRIL, 12)
        )
        val moneyBorrowed = TransactionRecord(
            id = "tx_6",
            type = TransactionType.BORROWING.name,
            amount = 1_200.0,
            accountId = bank.id,
            accountName = bank.name,
            counterpartyName = "Tariq",
            description = "Borrowed from Tariq",
            occurredAtMillis = dateMillis(2025, Calendar.APRIL, 14)
        )
        val lentRepayment = TransactionRecord(
            id = "tx_7",
            type = TransactionType.LENT_REPAYMENT.name,
            amount = 300.0,
            accountId = cash.id,
            accountName = cash.name,
            counterpartyName = "Rafi",
            description = "Partial return from Rafi",
            occurredAtMillis = dateMillis(2025, Calendar.APRIL, 18)
        )
        val borrowedRepayment = TransactionRecord(
            id = "tx_8",
            type = TransactionType.BORROWED_REPAYMENT.name,
            amount = 500.0,
            accountId = bank.id,
            accountName = bank.name,
            counterpartyName = "Tariq",
            description = "Partial payback to Tariq",
            occurredAtMillis = dateMillis(2025, Calendar.APRIL, 20)
        )

        val txs = listOf(
            priorSalary, salary, transfer, cardPurchase, cardPayment,
            moneyLent, moneyBorrowed, lentRepayment, borrowedRepayment
        )

        val lendingRecords = listOf(
            LendingRecord(
                id = "l_1",
                personName = "Rafi",
                principalAmount = 800.0,
                returnedAmount = 300.0,
                accountId = cash.id,
                accountName = cash.name,
                status = LoanStatus.PARTIAL.name
            )
        )
        val borrowingRecords = listOf(
            BorrowingRecord(
                id = "b_1",
                personName = "Tariq",
                principalAmount = 1_200.0,
                repaidAmount = 500.0,
                accountId = bank.id,
                accountName = bank.name,
                status = LoanStatus.PARTIAL.name
            )
        )

        val filter = StatementFilter(
            periodMode = StatementPeriodMode.MONTHLY,
            monthlySelection = MonthlySelection.SELECT_MONTH_YEAR,
            selectedMonthZeroIndexed = Calendar.APRIL,
            selectedMonthYear = 2025,
            selectedAccountId = null
        )

        val stmt = StatementEngine.generateStatement(
            filter = filter,
            settings = settings,
            accounts = accounts,
            transactions = txs,
            lendingRecords = lendingRecords,
            borrowingRecords = borrowingRecords,
            nowMillis = dateMillis(2025, Calendar.APRIL, 25)
        )

        // Opening Balance before April:
        // Assets: Bank(10000+5000) + Cash(2000) + bKash(1500) = 18500; Card debt = 500 => Net = 18000
        assertEquals(18_000.0, stmt.openingBalance, 0.001)
        assertEquals("Amina Rahman", stmt.userName)
        assertEquals("All Accounts", stmt.accountLabel)
        assertEquals(8, stmt.rows.size)

        // Accounting checks:
        // Income = 4000 only (borrowed 1200 & repayment 300 NOT income, transfers NOT income)
        assertEquals(4_000.0, stmt.totalIncome, 0.001)
        // Expenses = 600 (card purchase) + 15 (transfer fee) + 5 (cc payment fee) = 620
        // (transfer 1000, cc payment 700, lent 800, borrowed repayment 500 NOT expenses)
        assertEquals(620.0, stmt.totalExpenses, 0.001)
        assertEquals(3_380.0, stmt.netCashFlow, 0.001)
        assertEquals(20.0, stmt.transferCharges, 0.001)
        assertEquals(1_700.0, stmt.transferIn, 0.001)
        assertEquals(1_700.0, stmt.transferOut, 0.001)
        assertEquals(800.0, stmt.moneyLent, 0.001)
        assertEquals(1_200.0, stmt.moneyBorrowed, 0.001)
        assertEquals(800.0, stmt.totalRepayments, 0.001)
        assertEquals(500.0, stmt.outstandingLending, 0.001)
        assertEquals(700.0, stmt.outstandingBorrowing, 0.001)

        // Closing Net Position check:
        // Bank: 15000 + 4000 - 1015 - 705 + 1200 - 500 = 17980
        // Cash: 2000 - 800 + 300 = 1500
        // bKash: 1500 + 1000 = 2500
        // Total Assets = 21980
        // Card debt: 500 + 600 - 700 = 400
        // Net Closing = 21980 - 400 = 21580
        assertEquals(21_580.0, stmt.closingBalance, 0.001)
        assertEquals(21_580.0, stmt.rows.last().runningBalance, 0.001)
    }

    @Test
    fun `account wise statement for bank and credit card compute individual balances properly`() {
        val txs = listOf(
            TransactionRecord(
                id = "tx_bank_in",
                type = TransactionType.INCOME.name,
                amount = 3_000.0,
                accountId = bank.id,
                accountName = bank.name,
                occurredAtMillis = dateMillis(2025, Calendar.MAY, 3)
            ),
            TransactionRecord(
                id = "tx_to_cash",
                type = TransactionType.TRANSFER.name,
                amount = 1_000.0,
                chargeAmount = 10.0,
                accountId = bank.id,
                accountName = bank.name,
                toAccountId = cash.id,
                toAccountName = cash.name,
                occurredAtMillis = dateMillis(2025, Calendar.MAY, 6)
            ),
            TransactionRecord(
                id = "tx_card_spend",
                type = TransactionType.EXPENSE.name,
                amount = 2_500.0,
                accountId = card.id,
                accountName = card.name,
                categoryName = "Shopping",
                occurredAtMillis = dateMillis(2025, Calendar.MAY, 8)
            ),
            TransactionRecord(
                id = "tx_card_pay",
                type = TransactionType.CREDIT_CARD_PAYMENT.name,
                amount = 1_200.0,
                chargeAmount = 0.0,
                accountId = bank.id,
                accountName = bank.name,
                toAccountId = card.id,
                toAccountName = card.name,
                occurredAtMillis = dateMillis(2025, Calendar.MAY, 15)
            )
        )

        val bankFilter = StatementFilter(
            periodMode = StatementPeriodMode.CUSTOM,
            customStartMillis = dateMillis(2025, Calendar.MAY, 1, 0),
            customEndMillis = dateMillis(2025, Calendar.MAY, 31, 23),
            selectedAccountId = bank.id
        )
        val bankStmt = StatementEngine.generateStatement(
            filter = bankFilter,
            settings = settings,
            accounts = accounts,
            transactions = txs,
            lendingRecords = emptyList(),
            borrowingRecords = emptyList(),
            nowMillis = dateMillis(2025, Calendar.MAY, 20)
        )
        assertEquals(10_000.0, bankStmt.openingBalance, 0.001)
        assertEquals(3_000.0, bankStmt.totalIncome, 0.001)
        assertEquals(10.0, bankStmt.transferCharges, 0.001)
        assertEquals(10.0, bankStmt.totalExpenses, 0.001)
        assertEquals(2_200.0, bankStmt.transferOut, 0.001)
        assertEquals(0.0, bankStmt.transferIn, 0.001)
        // Bank Closing: 10000 + 3000 - 1010 - 1200 = 10790
        assertEquals(10_790.0, bankStmt.closingBalance, 0.001)

        // Credit card statement
        val cardStmt = StatementEngine.generateStatement(
            filter = bankFilter.copy(selectedAccountId = card.id),
            settings = settings,
            accounts = accounts,
            transactions = txs,
            lendingRecords = emptyList(),
            borrowingRecords = emptyList(),
            nowMillis = dateMillis(2025, Calendar.MAY, 20)
        )
        assertTrue(cardStmt.isCreditCardStatement)
        assertEquals(500.0, cardStmt.openingBalance, 0.001)
        assertEquals(2_500.0, cardStmt.totalExpenses, 0.001)
        assertEquals(1_200.0, cardStmt.transferIn, 0.001)
        // Card debt closing: 500 + 2500 - 1200 = 1800
        assertEquals(1_800.0, cardStmt.closingBalance, 0.001)
    }

    @Test
    fun `weekly monthly and yearly filter modes resolve expected bounds`() {
        val now = dateMillis(2025, Calendar.JUNE, 18) // Wednesday, June 18, 2025
        val (curWStart, curWEnd) = StatementEngine.resolvePeriodBounds(
            StatementFilter(periodMode = StatementPeriodMode.WEEKLY, weeklySelection = WeeklySelection.CURRENT_WEEK),
            nowMillis = now
        ).let { it.first to it.second }
        val (prevWStart, prevWEnd) = StatementEngine.resolvePeriodBounds(
            StatementFilter(periodMode = StatementPeriodMode.WEEKLY, weeklySelection = WeeklySelection.PREVIOUS_WEEK),
            nowMillis = now
        ).let { it.first to it.second }
        assertTrue(curWEnd - curWStart in 6L * 86_400_000L..7L * 86_400_000L)
        assertEquals(curWStart - 7L * 86_400_000L, prevWStart)
        assertEquals(curWEnd - 7L * 86_400_000L, prevWEnd)

        val (prevMonthStart, prevMonthEnd, prevMonthTitle) = StatementEngine.resolvePeriodBounds(
            StatementFilter(periodMode = StatementPeriodMode.MONTHLY, monthlySelection = MonthlySelection.PREVIOUS_MONTH),
            nowMillis = now
        )
        assertTrue(prevMonthTitle.contains("May 2025"))
        assertTrue(prevMonthEnd > prevMonthStart)

        val (_, _, prevYearTitle) = StatementEngine.resolvePeriodBounds(
            StatementFilter(periodMode = StatementPeriodMode.YEARLY, yearlySelection = YearlySelection.PREVIOUS_YEAR),
            nowMillis = now
        )
        assertTrue(prevYearTitle.contains("2024"))

        val (_, _, specificYearTitle) = StatementEngine.resolvePeriodBounds(
            StatementFilter(
                periodMode = StatementPeriodMode.YEARLY,
                yearlySelection = YearlySelection.SELECT_YEAR,
                selectedYear = 2023
            ),
            nowMillis = now
        )
        assertTrue(specificYearTitle.contains("2023"))
    }

    @Test
    fun `statement csv builder includes all required statement columns and summary values`() {
        val tx = TransactionRecord(
            id = "tx_csv_1",
            type = TransactionType.EXPENSE.name,
            amount = 250.0,
            accountId = cash.id,
            accountName = cash.name,
            categoryName = "Groceries",
            description = "Supermarket",
            occurredAtMillis = dateMillis(2025, Calendar.JULY, 10)
        )
        val stmt = StatementEngine.generateStatement(
            filter = StatementFilter(
                periodMode = StatementPeriodMode.MONTHLY,
                monthlySelection = MonthlySelection.SELECT_MONTH_YEAR,
                selectedMonthZeroIndexed = Calendar.JULY,
                selectedMonthYear = 2025,
                selectedAccountId = cash.id
            ),
            settings = settings,
            accounts = accounts,
            transactions = listOf(tx),
            lendingRecords = emptyList(),
            borrowingRecords = emptyList(),
            nowMillis = dateMillis(2025, Calendar.JULY, 12)
        )

        val csv = ExportManager.buildStatementCsvString(stmt, settings.currencySymbol, settings.dateFormat)
        assertTrue(csv.contains("Amina Rahman"))
        assertTrue(csv.contains("Cash Wallet (Cash)"))
        assertTrue(csv.contains("Opening Balance,2000.00"))
        assertTrue(csv.contains("Closing Balance,1750.00"))
        assertTrue(csv.contains("Date,Transaction ID,Description,Category,Transaction Type,Account,Income Amount ($),Expense Amount ($),Running Balance ($)"))
        assertTrue(csv.contains("tx_csv_1,Supermarket,Groceries,Expense,Cash Wallet,,250.00,1750.00"))
    }

    @Test
    fun `receipt allowed mime types and extensions cover jpg jpeg png webp only`() {
        assertTrue("image/jpeg" in ReceiptImageManager.ALLOWED_MIME_TYPES)
        assertTrue("image/jpg" in ReceiptImageManager.ALLOWED_MIME_TYPES)
        assertTrue("image/png" in ReceiptImageManager.ALLOWED_MIME_TYPES)
        assertTrue("image/webp" in ReceiptImageManager.ALLOWED_MIME_TYPES)
        assertFalse("application/pdf" in ReceiptImageManager.ALLOWED_MIME_TYPES)
        assertFalse("image/gif" in ReceiptImageManager.ALLOWED_MIME_TYPES)
        assertEquals(setOf("jpg", "jpeg", "png", "webp"), ReceiptImageManager.ALLOWED_EXTENSIONS)
    }
}
