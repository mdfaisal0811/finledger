package com.example.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.domain.model.Account
import com.example.domain.model.BorrowingRecord
import com.example.domain.model.Budget
import com.example.domain.model.Category
import com.example.domain.model.DashboardSummary
import com.example.domain.model.FinancialStatement
import com.example.domain.model.LendingRecord
import com.example.domain.model.RepaymentRecord
import com.example.domain.model.TransactionRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

data class WorkspaceBackupPayload(
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<TransactionRecord>,
    val budgets: List<Budget>,
    val lendingRecords: List<LendingRecord>,
    val borrowingRecords: List<BorrowingRecord>,
    val repayments: List<RepaymentRecord>
)

object ExportManager {

    fun writeTransactionsCsv(
        context: Context,
        uri: Uri,
        transactions: List<TransactionRecord>,
        currencySymbol: String,
        datePattern: String
    ): Result<Unit> = runCatching {
        val sb = StringBuilder()
        sb.appendLine("Date,Time,Type,Account,ToAccount,Category,Description,Amount ($currencySymbol),Charge ($currencySymbol),Counterparty,Notes")
        transactions.forEach { tx ->
            val cols = listOf(
                Formatters.formatDate(tx.occurredAtMillis, datePattern),
                Formatters.formatTime(tx.occurredAtMillis),
                tx.typeEnum().label,
                tx.accountName,
                tx.toAccountName.orEmpty(),
                tx.categoryName.orEmpty(),
                tx.description,
                tx.amount.toString(),
                tx.chargeAmount.toString(),
                tx.counterpartyName.orEmpty(),
                tx.notes.orEmpty()
            ).joinToString(",") { escapeCsv(it) }
            sb.appendLine(cols)
        }
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        } ?: error("Unable to open output stream for CSV.")
    }

    fun writeReportPdf(
        context: Context,
        uri: Uri,
        title: String,
        periodLabel: String,
        summary: DashboardSummary,
        accounts: List<Account>,
        transactions: List<TransactionRecord>,
        currencySymbol: String,
        datePattern: String
    ): Result<Unit> = runCatching {
        val pdf = PdfDocument()
        var pageNumber = 1
        val pageWidth = 595
        val pageHeight = 842
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdf.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 20f
            isFakeBoldText = true
        }
        val headingPaint = Paint().apply {
            color = Color.parseColor("#2563EB")
            textSize = 14f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 11f
        }
        val mutedPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 10f
        }

        var y = 48f
        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - 40f) {
                pdf.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = 48f
            }
        }

        canvas.drawText(title, 40f, y, titlePaint)
        y += 20f
        canvas.drawText("Period: $periodLabel  |  Generated: ${Formatters.formatDateTime(System.currentTimeMillis(), datePattern)}", 40f, y, mutedPaint)
        y += 26f

        canvas.drawText("Financial Overview", 40f, y, headingPaint)
        y += 18f
        val overviewLines = listOf(
            "Total Net Balance: ${Formatters.formatMoney(summary.totalNetBalance, currencySymbol)}",
            "Total Income: ${Formatters.formatMoney(summary.totalIncome, currencySymbol)}",
            "Total Expenses (incl. charges): ${Formatters.formatMoney(summary.totalExpenses, currencySymbol)}",
            "Net Savings: ${Formatters.formatMoney(summary.totalSavings, currencySymbol)}",
            "Transfer Charges: ${Formatters.formatMoney(summary.totalTransferCharges, currencySymbol)}",
            "Credit Card Outstanding Debt: ${Formatters.formatMoney(summary.totalCreditCardDebt, currencySymbol)}",
            "Outstanding Money Lent: ${Formatters.formatMoney(summary.outstandingLent, currencySymbol)}",
            "Outstanding Money Borrowed: ${Formatters.formatMoney(summary.outstandingBorrowed, currencySymbol)}"
        )
        overviewLines.forEach { line ->
            ensureSpace(16f)
            canvas.drawText(line, 40f, y, bodyPaint)
            y += 16f
        }

        y += 14f
        ensureSpace(24f)
        canvas.drawText("Accounts", 40f, y, headingPaint)
        y += 18f
        accounts.forEach { acc ->
            ensureSpace(16f)
            val status = if (acc.archived) " (Archived)" else ""
            val balLabel = if (acc.typeEnum().name == "CREDIT_CARD") "Debt: ${Formatters.formatMoney(acc.currentBalance, currencySymbol)}" else Formatters.formatMoney(acc.currentBalance, currencySymbol)
            canvas.drawText("• ${acc.name} [${acc.typeEnum().label}]$status — $balLabel", 40f, y, bodyPaint)
            y += 15f
        }

        if (summary.spendingByCategory.isNotEmpty()) {
            y += 14f
            ensureSpace(24f)
            canvas.drawText("Spending by Category", 40f, y, headingPaint)
            y += 18f
            summary.spendingByCategory.forEach { cat ->
                ensureSpace(16f)
                val pct = (cat.percentage * 100).toInt()
                canvas.drawText("• ${cat.categoryName}: ${Formatters.formatMoney(cat.amount, currencySymbol)} ($pct%)", 40f, y, bodyPaint)
                y += 15f
            }
        }

        y += 14f
        ensureSpace(24f)
        canvas.drawText("Transactions (${transactions.size})", 40f, y, headingPaint)
        y += 18f
        transactions.take(80).forEach { tx ->
            ensureSpace(16f)
            val dateStr = Formatters.formatDate(tx.occurredAtMillis, datePattern)
            val desc = tx.description.ifBlank { tx.categoryName ?: tx.typeEnum().label }.take(34)
            val amt = Formatters.formatMoney(tx.amount, currencySymbol)
            val fee = if (tx.chargeAmount > 0.0) " (+${Formatters.formatMoney(tx.chargeAmount, currencySymbol)} fee)" else ""
            canvas.drawText("$dateStr | ${tx.typeEnum().label} | $desc | ${tx.accountName} | $amt$fee", 40f, y, bodyPaint)
            y += 14f
        }

        pdf.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            pdf.writeTo(out)
        } ?: error("Unable to open output stream for PDF.")
        pdf.close()
    }

    fun buildStatementCsvString(
        statement: FinancialStatement,
        currencySymbol: String,
        datePattern: String
    ): String {
        val sb = StringBuilder()
        sb.appendLine(listOf("Statement Title", statement.title).joinToString(",") { escapeCsv(it) })
        sb.appendLine(listOf("Account Holder", statement.userName).joinToString(",") { escapeCsv(it) })
        if (statement.userEmail.isNotBlank()) {
            sb.appendLine(listOf("Email", statement.userEmail).joinToString(",") { escapeCsv(it) })
        }
        sb.appendLine(listOf("Account", statement.accountLabel).joinToString(",") { escapeCsv(it) })
        sb.appendLine(listOf("Date Range", statement.dateRangeLabel).joinToString(",") { escapeCsv(it) })
        sb.appendLine(listOf("Generated Date", Formatters.formatDateTime(statement.generatedAtMillis, datePattern)).joinToString(",") { escapeCsv(it) })
        sb.appendLine()
        sb.appendLine("Summary Metric,Amount ($currencySymbol)")
        val summaryRows = listOf(
            "Opening Balance" to statement.openingBalance,
            "Total Income" to statement.totalIncome,
            "Total Expenses (incl. charges)" to statement.totalExpenses,
            "Net Cash Flow" to statement.netCashFlow,
            "Transfer In" to statement.transferIn,
            "Transfer Out" to statement.transferOut,
            "Transfer Charges" to statement.transferCharges,
            "Money Lent" to statement.moneyLent,
            "Money Borrowed" to statement.moneyBorrowed,
            "Repayments" to statement.totalRepayments,
            "Outstanding Lending" to statement.outstandingLending,
            "Outstanding Borrowing" to statement.outstandingBorrowing,
            "Closing Balance" to statement.closingBalance
        )
        summaryRows.forEach { (k, v) ->
            sb.appendLine(listOf(k, String.format(java.util.Locale.US, "%.2f", v)).joinToString(",") { escapeCsv(it) })
        }
        sb.appendLine()
        sb.appendLine("Date,Transaction ID,Description,Category,Transaction Type,Account,Income Amount ($currencySymbol),Expense Amount ($currencySymbol),Running Balance ($currencySymbol)")
        statement.rows.forEach { row ->
            val cols = listOf(
                Formatters.formatDate(row.occurredAtMillis, datePattern),
                row.transactionId,
                row.description,
                row.categoryName,
                row.transactionTypeLabel,
                row.accountDisplay,
                if (row.incomeAmount > 0.0) String.format(java.util.Locale.US, "%.2f", row.incomeAmount) else "",
                if (row.expenseAmount > 0.0) String.format(java.util.Locale.US, "%.2f", row.expenseAmount) else "",
                String.format(java.util.Locale.US, "%.2f", row.runningBalance)
            ).joinToString(",") { escapeCsv(it) }
            sb.appendLine(cols)
        }
        return sb.toString()
    }

    fun writeStatementCsv(
        context: Context,
        uri: Uri,
        statement: FinancialStatement,
        currencySymbol: String,
        datePattern: String
    ): Result<Unit> = runCatching {
        val csv = buildStatementCsvString(statement, currencySymbol, datePattern)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(csv.toByteArray(Charsets.UTF_8))
        } ?: error("Unable to open output stream for statement CSV.")
    }

    fun writeStatementPdf(
        context: Context,
        uri: Uri,
        statement: FinancialStatement,
        currencySymbol: String,
        datePattern: String
    ): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            renderStatementPdfToStream(statement, currencySymbol, datePattern, out)
        } ?: error("Unable to open output stream for statement PDF.")
    }

    fun createShareableStatementPdf(
        context: Context,
        statement: FinancialStatement,
        currencySymbol: String,
        datePattern: String
    ): Result<Uri> = runCatching {
        val dir = File(context.cacheDir, "statements").apply { mkdirs() }
        val safeTitle = statement.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(36)
        val outFile = File(dir, "${safeTitle}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outFile).use { out ->
            renderStatementPdfToStream(statement, currencySymbol, datePattern, out)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

    private fun renderStatementPdfToStream(
        statement: FinancialStatement,
        currencySymbol: String,
        datePattern: String,
        outputStream: OutputStream
    ) {
        val pageWidth = 595
        val pageHeight = 842
        val left = 32f
        val right = pageWidth - 32f
        val contentBottom = pageHeight - 44f

        // Pre-paginate transaction rows so we know exact "Page X of Y"
        val firstPageCapacity = 24
        val nextPageCapacity = 42
        val rowChunks = mutableListOf<List<com.example.domain.model.StatementRow>>()
        if (statement.rows.isEmpty()) {
            rowChunks.add(emptyList())
        } else {
            val firstBatch = statement.rows.take(firstPageCapacity)
            rowChunks.add(firstBatch)
            var idx = firstPageCapacity
            while (idx < statement.rows.size) {
                rowChunks.add(statement.rows.subList(idx, minOf(idx + nextPageCapacity, statement.rows.size)))
                idx += nextPageCapacity
            }
        }
        val totalPages = rowChunks.size.coerceAtLeast(1)

        val pdf = PdfDocument()
        val brandPaint = Paint().apply {
            color = Color.parseColor("#2563EB")
            textSize = 11f
            isFakeBoldText = true
        }
        val titlePaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 18f
            isFakeBoldText = true
        }
        val sectionPaint = Paint().apply {
            color = Color.parseColor("#2563EB")
            textSize = 12f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 9.5f
        }
        val boldBodyPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 9.5f
            isFakeBoldText = true
        }
        val mutedPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 8.5f
        }
        val incomePaint = Paint().apply {
            color = Color.parseColor("#16A34A")
            textSize = 8.5f
            textAlign = Paint.Align.RIGHT
        }
        val expensePaint = Paint().apply {
            color = Color.parseColor("#DC2626")
            textSize = 8.5f
            textAlign = Paint.Align.RIGHT
        }
        val cellPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 8.2f
        }
        val cellRightPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 8.2f
            textAlign = Paint.Align.RIGHT
        }
        val headerBgPaint = Paint().apply { color = Color.parseColor("#EFF6FF") }
        val zebraBgPaint = Paint().apply { color = Color.parseColor("#F8FAFC") }
        val summaryBgPaint = Paint().apply { color = Color.parseColor("#F8FAFC") }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.8f
        }

        val generatedStr = Formatters.formatDateTime(statement.generatedAtMillis, datePattern)

        fun drawTableHeader(canvas: Canvas, topY: Float): Float {
            canvas.drawRect(left, topY, right, topY + 18f, headerBgPaint)
            canvas.drawLine(left, topY, right, topY, borderPaint)
            canvas.drawLine(left, topY + 18f, right, topY + 18f, borderPaint)
            val ty = topY + 12.5f
            canvas.drawText("Date", left + 3f, ty, boldBodyPaint)
            canvas.drawText("ID", left + 56f, ty, boldBodyPaint)
            canvas.drawText("Description", left + 98f, ty, boldBodyPaint)
            canvas.drawText("Category", left + 204f, ty, boldBodyPaint)
            canvas.drawText("Type", left + 268f, ty, boldBodyPaint)
            canvas.drawText("Account", left + 324f, ty, boldBodyPaint)
            canvas.drawText("Income", left + 432f, ty, Paint(boldBodyPaint).apply { textAlign = Paint.Align.RIGHT })
            canvas.drawText("Expense", left + 482f, ty, Paint(boldBodyPaint).apply { textAlign = Paint.Align.RIGHT })
            canvas.drawText("Balance", right - 3f, ty, Paint(boldBodyPaint).apply { textAlign = Paint.Align.RIGHT })
            return topY + 18f
        }

        fun drawFooter(canvas: Canvas, pageIdx: Int) {
            canvas.drawLine(left, pageHeight - 32f, right, pageHeight - 32f, borderPaint)
            canvas.drawText("FinLedger Financial Statement • Generated $generatedStr", left, pageHeight - 18f, mutedPaint)
            val pageLabel = "Page $pageIdx of $totalPages"
            canvas.drawText(pageLabel, right, pageHeight - 18f, Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT })
        }

        rowChunks.forEachIndexed { index, chunk ->
            val pageNumber = index + 1
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            var y = 36f

            if (pageNumber == 1) {
                canvas.drawText("FINLEDGER PERSONAL FINANCE", left, y, brandPaint)
                y += 20f
                canvas.drawText(statement.title.take(58), left, y, titlePaint)
                y += 16f
                val userLine = buildString {
                    append("Prepared for: ${statement.userName}")
                    if (statement.userEmail.isNotBlank()) append(" (${statement.userEmail})")
                }
                canvas.drawText(userLine, left, y, mutedPaint)
                y += 13f
                canvas.drawText("Account: ${statement.accountLabel}   •   Period: ${statement.dateRangeLabel}", left, y, mutedPaint)
                y += 16f

                // Summary box
                canvas.drawRect(left, y, right, y + 124f, summaryBgPaint)
                canvas.drawLine(left, y, right, y, borderPaint)
                canvas.drawLine(left, y + 124f, right, y + 124f, borderPaint)
                val col1X = left + 10f
                val col1ValX = left + 178f
                val col2X = left + 195f
                val col2ValX = left + 358f
                val col3X = left + 372f
                val col3ValX = right - 10f

                canvas.drawText("STATEMENT SUMMARY", col1X, y + 15f, sectionPaint)
                val rowYs = listOf(y + 34f, y + 51f, y + 68f, y + 85f, y + 102f, y + 117f)

                val leftMetrics = listOf(
                    "Opening Balance" to Formatters.formatMoney(statement.openingBalance, currencySymbol),
                    "Total Income" to Formatters.formatMoney(statement.totalIncome, currencySymbol),
                    "Total Expenses" to Formatters.formatMoney(statement.totalExpenses, currencySymbol),
                    "Net Cash Flow" to Formatters.formatMoney(statement.netCashFlow, currencySymbol),
                    "Closing Balance" to Formatters.formatMoney(statement.closingBalance, currencySymbol)
                )
                val midMetrics = listOf(
                    "Transfer In" to Formatters.formatMoney(statement.transferIn, currencySymbol),
                    "Transfer Out" to Formatters.formatMoney(statement.transferOut, currencySymbol),
                    "Transfer Charges" to Formatters.formatMoney(statement.transferCharges, currencySymbol),
                    "Repayments" to Formatters.formatMoney(statement.totalRepayments, currencySymbol)
                )
                val rightMetrics = listOf(
                    "Money Lent" to Formatters.formatMoney(statement.moneyLent, currencySymbol),
                    "Money Borrowed" to Formatters.formatMoney(statement.moneyBorrowed, currencySymbol),
                    "Outst. Lending" to Formatters.formatMoney(statement.outstandingLending, currencySymbol),
                    "Outst. Borrowing" to Formatters.formatMoney(statement.outstandingBorrowing, currencySymbol)
                )

                leftMetrics.forEachIndexed { idx, (k, v) ->
                    canvas.drawText(k, col1X, rowYs[idx], bodyPaint)
                    canvas.drawText(v, col1ValX, rowYs[idx], Paint(boldBodyPaint).apply { textAlign = Paint.Align.RIGHT })
                }
                midMetrics.forEachIndexed { idx, (k, v) ->
                    canvas.drawText(k, col2X, rowYs[idx], bodyPaint)
                    canvas.drawText(v, col2ValX, rowYs[idx], Paint(boldBodyPaint).apply { textAlign = Paint.Align.RIGHT })
                }
                rightMetrics.forEachIndexed { idx, (k, v) ->
                    canvas.drawText(k, col3X, rowYs[idx], bodyPaint)
                    canvas.drawText(v, col3ValX, rowYs[idx], Paint(boldBodyPaint).apply { textAlign = Paint.Align.RIGHT })
                }

                y += 140f
                canvas.drawText("Detailed Transactions (${statement.rows.size})", left, y, sectionPaint)
                y += 10f
            } else {
                canvas.drawText("${statement.title} (Continued)", left, y, sectionPaint)
                y += 10f
            }

            y = drawTableHeader(canvas, y)
            if (chunk.isEmpty()) {
                y += 22f
                canvas.drawText("No transactions recorded within the selected period and account filter.", left + 6f, y, mutedPaint)
            } else {
                chunk.forEachIndexed { rIdx, row ->
                    if (y + 16f > contentBottom) return@forEachIndexed
                    if (rIdx % 2 == 1) {
                        canvas.drawRect(left, y, right, y + 16f, zebraBgPaint)
                    }
                    val ty = y + 11.5f
                    canvas.drawText(Formatters.formatDate(row.occurredAtMillis, "dd/MM/yy"), left + 3f, ty, cellPaint)
                    canvas.drawText(row.transactionId.take(7), left + 56f, ty, mutedPaint)
                    canvas.drawText(row.description.take(19), left + 98f, ty, cellPaint)
                    canvas.drawText(row.categoryName.take(11), left + 204f, ty, cellPaint)
                    canvas.drawText(row.transactionTypeLabel.take(10), left + 268f, ty, cellPaint)
                    canvas.drawText(row.accountDisplay.take(13), left + 324f, ty, cellPaint)
                    if (row.incomeAmount > 0.0) {
                        canvas.drawText(Formatters.formatMoney(row.incomeAmount, currencySymbol), left + 432f, ty, incomePaint)
                    }
                    if (row.expenseAmount > 0.0) {
                        canvas.drawText(Formatters.formatMoney(row.expenseAmount, currencySymbol), left + 482f, ty, expensePaint)
                    }
                    canvas.drawText(Formatters.formatMoney(row.runningBalance, currencySymbol), right - 3f, ty, cellRightPaint)
                    canvas.drawLine(left, y + 16f, right, y + 16f, Paint(borderPaint).apply { alpha = 110 })
                    y += 16f
                }
            }

            if (pageNumber == totalPages && y + 20f <= contentBottom) {
                y += 16f
                canvas.drawText(
                    "Opening: ${Formatters.formatMoney(statement.openingBalance, currencySymbol)}   •   Closing: ${Formatters.formatMoney(statement.closingBalance, currencySymbol)}",
                    right,
                    y,
                    Paint(boldBodyPaint).apply { textAlign = Paint.Align.RIGHT }
                )
            }

            drawFooter(canvas, pageNumber)
            pdf.finishPage(page)
        }

        pdf.writeTo(outputStream)
        pdf.close()
    }

    fun writeWorkspaceBackupJson(
        context: Context,
        uri: Uri,
        payload: WorkspaceBackupPayload
    ): Result<Unit> = runCatching {
        val root = JSONObject().apply {
            put("version", 1)
            put("exportedAtMillis", System.currentTimeMillis())
            put("accounts", JSONArray(payload.accounts.map { a ->
                JSONObject().apply {
                    put("id", a.id)
                    put("name", a.name)
                    put("type", a.type)
                    put("openingBalance", a.openingBalance)
                    put("currentBalance", a.currentBalance)
                    put("creditLimit", a.creditLimit)
                    put("bankName", a.bankName)
                    put("accountNumber", a.accountNumber)
                    put("billingCycleDay", a.billingCycleDay)
                    put("paymentDueDay", a.paymentDueDay)
                    put("minimumPayment", a.minimumPayment)
                    put("iconKey", a.iconKey)
                    put("colorHex", a.colorHex)
                    put("notes", a.notes)
                    put("archived", a.archived)
                    put("defaultAccount", a.defaultAccount)
                }
            }))
            put("categories", JSONArray(payload.categories.map { c ->
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("type", c.type)
                    put("iconKey", c.iconKey)
                    put("colorHex", c.colorHex)
                    put("systemDefault", c.systemDefault)
                }
            }))
            put("transactions", JSONArray(payload.transactions.map { t ->
                JSONObject().apply {
                    put("id", t.id)
                    put("type", t.type)
                    put("amount", t.amount)
                    put("chargeAmount", t.chargeAmount)
                    put("accountId", t.accountId)
                    put("accountName", t.accountName)
                    put("toAccountId", t.toAccountId)
                    put("toAccountName", t.toAccountName)
                    put("categoryId", t.categoryId)
                    put("categoryName", t.categoryName)
                    put("description", t.description)
                    put("notes", t.notes)
                    put("loanId", t.loanId)
                    put("repaymentId", t.repaymentId)
                    put("counterpartyName", t.counterpartyName)
                    put("occurredAtMillis", t.occurredAtMillis)
                }
            }))
            put("budgets", JSONArray(payload.budgets.map { b ->
                JSONObject().apply {
                    put("id", b.id)
                    put("name", b.name)
                    put("periodType", b.periodType)
                    put("categoryId", b.categoryId)
                    put("categoryName", b.categoryName)
                    put("limitAmount", b.limitAmount)
                    put("startDateMillis", b.startDateMillis)
                    put("endDateMillis", b.endDateMillis)
                    put("alertThresholds", JSONArray(b.alertThresholds))
                    put("lastNotifiedThreshold", b.lastNotifiedThreshold)
                    put("active", b.active)
                }
            }))
            put("lending_records", JSONArray(payload.lendingRecords.map { l ->
                JSONObject().apply {
                    put("id", l.id)
                    put("personName", l.personName)
                    put("principalAmount", l.principalAmount)
                    put("returnedAmount", l.returnedAmount)
                    put("accountId", l.accountId)
                    put("accountName", l.accountName)
                    put("transactionId", l.transactionId)
                    put("lentDateMillis", l.lentDateMillis)
                    put("expectedReturnDateMillis", l.expectedReturnDateMillis)
                    put("description", l.description)
                    put("notes", l.notes)
                    put("status", l.status)
                    put("reminderEnabled", l.reminderEnabled)
                }
            }))
            put("borrowing_records", JSONArray(payload.borrowingRecords.map { b ->
                JSONObject().apply {
                    put("id", b.id)
                    put("personName", b.personName)
                    put("principalAmount", b.principalAmount)
                    put("repaidAmount", b.repaidAmount)
                    put("accountId", b.accountId)
                    put("accountName", b.accountName)
                    put("transactionId", b.transactionId)
                    put("borrowedDateMillis", b.borrowedDateMillis)
                    put("expectedRepaymentDateMillis", b.expectedRepaymentDateMillis)
                    put("description", b.description)
                    put("notes", b.notes)
                    put("status", b.status)
                    put("reminderEnabled", b.reminderEnabled)
                }
            }))
            put("repayments", JSONArray(payload.repayments.map { r ->
                JSONObject().apply {
                    put("id", r.id)
                    put("recordType", r.recordType)
                    put("loanId", r.loanId)
                    put("personName", r.personName)
                    put("amount", r.amount)
                    put("accountId", r.accountId)
                    put("accountName", r.accountName)
                    put("transactionId", r.transactionId)
                    put("repaidAtMillis", r.repaidAtMillis)
                    put("notes", r.notes)
                }
            }))
        }

        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: error("Unable to open output stream for backup.")
    }

    fun readWorkspaceBackupJson(context: Context, uri: Uri): Result<WorkspaceBackupPayload> = runCatching {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Unable to read backup file.")
        val root = JSONObject(raw)

        val accountsArr = root.optJSONArray("accounts") ?: JSONArray()
        val accounts = (0 until accountsArr.length()).map { idx ->
            val o = accountsArr.getJSONObject(idx)
            Account(
                id = o.optString("id"),
                name = o.getString("name"),
                type = o.getString("type"),
                openingBalance = o.optDouble("openingBalance", 0.0),
                currentBalance = o.optDouble("currentBalance", 0.0),
                creditLimit = o.optDouble("creditLimit", 0.0),
                bankName = o.optString("bankName").takeIf { it.isNotBlank() && it != "null" },
                accountNumber = o.optString("accountNumber").takeIf { it.isNotBlank() && it != "null" },
                billingCycleDay = o.optInt("billingCycleDay", 1),
                paymentDueDay = o.optInt("paymentDueDay", 15),
                minimumPayment = o.optDouble("minimumPayment", 0.0),
                iconKey = o.optString("iconKey", "wallet"),
                colorHex = o.optLong("colorHex", 0xFF2563EB),
                notes = o.optString("notes").takeIf { it.isNotBlank() && it != "null" },
                archived = o.optBoolean("archived", false),
                defaultAccount = o.optBoolean("defaultAccount", false)
            )
        }

        val catArr = root.optJSONArray("categories") ?: JSONArray()
        val categories = (0 until catArr.length()).map { idx ->
            val o = catArr.getJSONObject(idx)
            Category(
                id = o.optString("id"),
                name = o.getString("name"),
                type = o.getString("type"),
                iconKey = o.optString("iconKey", "category"),
                colorHex = o.optLong("colorHex", 0xFF2563EB),
                systemDefault = o.optBoolean("systemDefault", false)
            )
        }

        val txArr = root.optJSONArray("transactions") ?: JSONArray()
        val transactions = (0 until txArr.length()).map { idx ->
            val o = txArr.getJSONObject(idx)
            TransactionRecord(
                id = o.optString("id"),
                type = o.getString("type"),
                amount = o.getDouble("amount"),
                chargeAmount = o.optDouble("chargeAmount", 0.0),
                accountId = o.getString("accountId"),
                accountName = o.optString("accountName", "Account"),
                toAccountId = o.optString("toAccountId").takeIf { it.isNotBlank() && it != "null" },
                toAccountName = o.optString("toAccountName").takeIf { it.isNotBlank() && it != "null" },
                categoryId = o.optString("categoryId").takeIf { it.isNotBlank() && it != "null" },
                categoryName = o.optString("categoryName").takeIf { it.isNotBlank() && it != "null" },
                description = o.optString("description", ""),
                notes = o.optString("notes").takeIf { it.isNotBlank() && it != "null" },
                loanId = o.optString("loanId").takeIf { it.isNotBlank() && it != "null" },
                repaymentId = o.optString("repaymentId").takeIf { it.isNotBlank() && it != "null" },
                counterpartyName = o.optString("counterpartyName").takeIf { it.isNotBlank() && it != "null" },
                occurredAtMillis = o.optLong("occurredAtMillis", System.currentTimeMillis())
            )
        }

        val budArr = root.optJSONArray("budgets") ?: JSONArray()
        val budgets = (0 until budArr.length()).map { idx ->
            val o = budArr.getJSONObject(idx)
            val thArr = o.optJSONArray("alertThresholds")
            val thresholds = if (thArr != null) (0 until thArr.length()).map { thArr.getInt(it) } else listOf(50, 75, 90, 100)
            Budget(
                id = o.optString("id"),
                name = o.getString("name"),
                periodType = o.optString("periodType", "MONTHLY"),
                categoryId = o.optString("categoryId").takeIf { it.isNotBlank() && it != "null" },
                categoryName = o.optString("categoryName").takeIf { it.isNotBlank() && it != "null" },
                limitAmount = o.getDouble("limitAmount"),
                startDateMillis = o.optLong("startDateMillis", 0L),
                endDateMillis = o.optLong("endDateMillis", System.currentTimeMillis()),
                alertThresholds = thresholds,
                lastNotifiedThreshold = o.optInt("lastNotifiedThreshold", 0),
                active = o.optBoolean("active", true)
            )
        }

        val lendArr = root.optJSONArray("lending_records") ?: JSONArray()
        val lendings = (0 until lendArr.length()).map { idx ->
            val o = lendArr.getJSONObject(idx)
            LendingRecord(
                id = o.optString("id"),
                personName = o.getString("personName"),
                principalAmount = o.getDouble("principalAmount"),
                returnedAmount = o.optDouble("returnedAmount", 0.0),
                accountId = o.getString("accountId"),
                accountName = o.optString("accountName", "Account"),
                transactionId = o.optString("transactionId"),
                lentDateMillis = o.optLong("lentDateMillis", System.currentTimeMillis()),
                expectedReturnDateMillis = if (o.has("expectedReturnDateMillis") && !o.isNull("expectedReturnDateMillis")) o.optLong("expectedReturnDateMillis") else null,
                description = o.optString("description", ""),
                notes = o.optString("notes").takeIf { it.isNotBlank() && it != "null" },
                status = o.optString("status", "OPEN"),
                reminderEnabled = o.optBoolean("reminderEnabled", true)
            )
        }

        val borArr = root.optJSONArray("borrowing_records") ?: JSONArray()
        val borrowings = (0 until borArr.length()).map { idx ->
            val o = borArr.getJSONObject(idx)
            BorrowingRecord(
                id = o.optString("id"),
                personName = o.getString("personName"),
                principalAmount = o.getDouble("principalAmount"),
                repaidAmount = o.optDouble("repaidAmount", 0.0),
                accountId = o.getString("accountId"),
                accountName = o.optString("accountName", "Account"),
                transactionId = o.optString("transactionId"),
                borrowedDateMillis = o.optLong("borrowedDateMillis", System.currentTimeMillis()),
                expectedRepaymentDateMillis = if (o.has("expectedRepaymentDateMillis") && !o.isNull("expectedRepaymentDateMillis")) o.optLong("expectedRepaymentDateMillis") else null,
                description = o.optString("description", ""),
                notes = o.optString("notes").takeIf { it.isNotBlank() && it != "null" },
                status = o.optString("status", "OPEN"),
                reminderEnabled = o.optBoolean("reminderEnabled", true)
            )
        }

        val repArr = root.optJSONArray("repayments") ?: JSONArray()
        val repayments = (0 until repArr.length()).map { idx ->
            val o = repArr.getJSONObject(idx)
            RepaymentRecord(
                id = o.optString("id"),
                recordType = o.getString("recordType"),
                loanId = o.getString("loanId"),
                personName = o.getString("personName"),
                amount = o.getDouble("amount"),
                accountId = o.getString("accountId"),
                accountName = o.optString("accountName", "Account"),
                transactionId = o.getString("transactionId"),
                repaidAtMillis = o.optLong("repaidAtMillis", System.currentTimeMillis()),
                notes = o.optString("notes").takeIf { it.isNotBlank() && it != "null" }
            )
        }

        WorkspaceBackupPayload(accounts, categories, transactions, budgets, lendings, borrowings, repayments)
    }

    /**
     * Copies a selected Photo Picker receipt URI into app-internal storage so it remains viewable.
     */
    fun copyReceiptToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val receiptsDir = File(context.filesDir, "receipts").apply { mkdirs() }
            val destFile = File(receiptsDir, "receipt_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(destFile).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n")
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
