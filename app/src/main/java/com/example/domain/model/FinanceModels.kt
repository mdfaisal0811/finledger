package com.example.domain.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class AccountType(val label: String) {
    CASH("Cash"),
    BANK("Bank Account"),
    MOBILE_WALLET("Mobile Wallet"),
    CREDIT_CARD("Credit Card"),
    SAVINGS("Savings Account"),
    OTHER("Other Account")
}

enum class CategoryType { INCOME, EXPENSE }

enum class TransactionType(val label: String) {
    INCOME("Income"),
    EXPENSE("Expense"),
    TRANSFER("Transfer"),
    CREDIT_CARD_PAYMENT("Card Payment"),
    LENDING("Money Lent"),
    BORROWING("Money Borrowed"),
    LENT_REPAYMENT("Lent Returned"),
    BORROWED_REPAYMENT("Debt Repaid")
}

enum class BudgetPeriodType(val label: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    CUSTOM("Custom")
}

enum class LoanStatus(val label: String) {
    OPEN("Open"),
    PARTIAL("Partial"),
    SETTLED("Settled")
}

enum class RepaymentRecordType { LENDING, BORROWING }

enum class NotificationKind { BUDGET_THRESHOLD, LOAN_DUE, CARD_DUE, SYSTEM }

enum class DashboardDateRange(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    CUSTOM("Custom")
}

enum class TransactionSortOrder(val label: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    HIGHEST_AMOUNT("Highest Amount"),
    LOWEST_AMOUNT("Lowest Amount")
}

@Keep
@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "User",
    val email: String = "",
    val photoUrl: String? = null,
    val currencyCode: String = "BDT",
    val currencySymbol: String = "৳",
    val themeMode: String = ThemeMode.SYSTEM.name,
    val dateFormat: String = "dd MMM yyyy",
    val language: String = "English",
    val defaultAccountId: String? = null,
    val notificationsEnabled: Boolean = true,
    val budgetAlertThresholds: List<Int> = listOf(50, 75, 90, 100),
    val dueDateReminderDays: Int = 3,
    val appLockEnabled: Boolean = false,
    val hideBalances: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun themeModeEnum(): ThemeMode =
        enumValues<ThemeMode>().firstOrNull { it.name == themeMode } ?: ThemeMode.SYSTEM

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "displayName" to displayName.take(80).ifBlank { "User" },
        "email" to email.take(120),
        "photoUrl" to photoUrl?.take(500),
        "currencyCode" to currencyCode.take(10),
        "currencySymbol" to currencySymbol.take(6),
        "themeMode" to themeModeEnum().name,
        "dateFormat" to dateFormat.take(30),
        "language" to language.take(30),
        "defaultAccountId" to defaultAccountId?.take(128),
        "notificationsEnabled" to notificationsEnabled,
        "budgetAlertThresholds" to budgetAlertThresholds.take(6),
        "dueDateReminderDays" to dueDateReminderDays.coerceIn(0, 30),
        "appLockEnabled" to appLockEnabled,
        "hideBalances" to hideBalances,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
}

@Keep
@IgnoreExtraProperties
data class Account(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val type: String = AccountType.CASH.name,
    val openingBalance: Double = 0.0,
    val currentBalance: Double = 0.0,
    val creditLimit: Double = 0.0,
    val bankName: String? = null,
    val accountNumber: String? = null,
    val billingCycleDay: Int = 1,
    val paymentDueDay: Int = 15,
    val minimumPayment: Double = 0.0,
    val iconKey: String = "wallet",
    val colorHex: Long = 0xFF2563EB,
    val notes: String? = null,
    val archived: Boolean = false,
    val defaultAccount: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun typeEnum(): AccountType =
        enumValues<AccountType>().firstOrNull { it.name == type } ?: AccountType.OTHER

    val availableCredit: Double
        get() = if (typeEnum() == AccountType.CREDIT_CARD) (creditLimit - currentBalance).coerceAtLeast(0.0) else 0.0

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "name" to name.trim().take(60),
        "type" to typeEnum().name,
        "openingBalance" to openingBalance,
        "currentBalance" to currentBalance,
        "creditLimit" to creditLimit.coerceAtLeast(0.0),
        "bankName" to bankName?.trim()?.take(60)?.takeIf { it.isNotEmpty() },
        "accountNumber" to accountNumber?.trim()?.take(40)?.takeIf { it.isNotEmpty() },
        "billingCycleDay" to billingCycleDay.coerceIn(1, 31),
        "paymentDueDay" to paymentDueDay.coerceIn(1, 31),
        "minimumPayment" to minimumPayment.coerceAtLeast(0.0),
        "iconKey" to iconKey.take(40).ifBlank { "wallet" },
        "colorHex" to colorHex,
        "notes" to notes?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
        "archived" to archived,
        "defaultAccount" to defaultAccount,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
}

@Keep
@IgnoreExtraProperties
data class Category(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val type: String = CategoryType.EXPENSE.name,
    val iconKey: String = "category",
    val colorHex: Long = 0xFF2563EB,
    val systemDefault: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun typeEnum(): CategoryType =
        enumValues<CategoryType>().firstOrNull { it.name == type } ?: CategoryType.EXPENSE

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "name" to name.trim().take(50),
        "type" to typeEnum().name,
        "iconKey" to iconKey.take(40).ifBlank { "category" },
        "colorHex" to colorHex,
        "systemDefault" to systemDefault,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class TransactionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = TransactionType.EXPENSE.name,
    val amount: Double = 0.0,
    val chargeAmount: Double = 0.0,
    val accountId: String = "",
    val accountName: String = "",
    val toAccountId: String? = null,
    val toAccountName: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val description: String = "",
    val notes: String? = null,
    val receiptUri: String? = null,
    val loanId: String? = null,
    val repaymentId: String? = null,
    val counterpartyName: String? = null,
    val occurredAtMillis: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun typeEnum(): TransactionType =
        enumValues<TransactionType>().firstOrNull { it.name == type } ?: TransactionType.EXPENSE

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "type" to typeEnum().name,
        "amount" to amount,
        "chargeAmount" to chargeAmount.coerceAtLeast(0.0),
        "accountId" to accountId,
        "accountName" to accountName.trim().take(60).ifBlank { "Account" },
        "toAccountId" to toAccountId?.takeIf { it.isNotBlank() },
        "toAccountName" to toAccountName?.trim()?.take(60)?.takeIf { it.isNotEmpty() },
        "categoryId" to categoryId?.takeIf { it.isNotBlank() },
        "categoryName" to categoryName?.trim()?.take(50)?.takeIf { it.isNotEmpty() },
        "description" to description.trim().take(120),
        "notes" to notes?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
        "receiptUri" to receiptUri?.take(600)?.takeIf { it.isNotBlank() },
        "loanId" to loanId?.takeIf { it.isNotBlank() },
        "repaymentId" to repaymentId?.takeIf { it.isNotBlank() },
        "counterpartyName" to counterpartyName?.trim()?.take(80)?.takeIf { it.isNotEmpty() },
        "occurredAtMillis" to occurredAtMillis.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "type" to typeEnum().name,
        "amount" to amount,
        "chargeAmount" to chargeAmount.coerceAtLeast(0.0),
        "accountId" to accountId,
        "accountName" to accountName.trim().take(60).ifBlank { "Account" },
        "toAccountId" to (toAccountId?.takeIf { it.isNotBlank() } ?: FieldValue.delete()),
        "toAccountName" to (toAccountName?.trim()?.take(60)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
        "categoryId" to (categoryId?.takeIf { it.isNotBlank() } ?: FieldValue.delete()),
        "categoryName" to (categoryName?.trim()?.take(50)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
        "description" to description.trim().take(120),
        "notes" to (notes?.trim()?.take(500)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
        "receiptUri" to (receiptUri?.take(600)?.takeIf { it.isNotBlank() } ?: FieldValue.delete()),
        "loanId" to (loanId?.takeIf { it.isNotBlank() } ?: FieldValue.delete()),
        "repaymentId" to (repaymentId?.takeIf { it.isNotBlank() } ?: FieldValue.delete()),
        "counterpartyName" to (counterpartyName?.trim()?.take(80)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
        "occurredAtMillis" to occurredAtMillis.coerceAtLeast(1L),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Budget(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val periodType: String = BudgetPeriodType.MONTHLY.name,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val limitAmount: Double = 0.0,
    val startDateMillis: Long = 0L,
    val endDateMillis: Long = 0L,
    val alertThresholds: List<Int> = listOf(50, 75, 90, 100),
    val lastNotifiedThreshold: Int = 0,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun periodTypeEnum(): BudgetPeriodType =
        enumValues<BudgetPeriodType>().firstOrNull { it.name == periodType } ?: BudgetPeriodType.MONTHLY

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "name" to name.trim().take(60),
        "periodType" to periodTypeEnum().name,
        "categoryId" to categoryId?.takeIf { it.isNotBlank() },
        "categoryName" to categoryName?.trim()?.take(50)?.takeIf { it.isNotEmpty() },
        "limitAmount" to limitAmount,
        "startDateMillis" to startDateMillis,
        "endDateMillis" to endDateMillis.coerceAtLeast(startDateMillis),
        "alertThresholds" to alertThresholds.take(6),
        "lastNotifiedThreshold" to lastNotifiedThreshold.coerceIn(0, 100),
        "active" to active,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
}

@Keep
@IgnoreExtraProperties
data class LendingRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val personName: String = "",
    val principalAmount: Double = 0.0,
    val returnedAmount: Double = 0.0,
    val accountId: String = "",
    val accountName: String = "",
    val transactionId: String = "",
    val lentDateMillis: Long = System.currentTimeMillis(),
    val expectedReturnDateMillis: Long? = null,
    val description: String = "",
    val notes: String? = null,
    val status: String = LoanStatus.OPEN.name,
    val reminderEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val outstandingAmount: Double get() = (principalAmount - returnedAmount).coerceAtLeast(0.0)
    fun statusEnum(): LoanStatus =
        enumValues<LoanStatus>().firstOrNull { it.name == status } ?: LoanStatus.OPEN

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "personName" to personName.trim().take(80),
        "principalAmount" to principalAmount,
        "returnedAmount" to returnedAmount.coerceIn(0.0, principalAmount),
        "accountId" to accountId,
        "accountName" to accountName.trim().take(60),
        "transactionId" to transactionId,
        "lentDateMillis" to lentDateMillis.coerceAtLeast(1L),
        "expectedReturnDateMillis" to expectedReturnDateMillis,
        "description" to description.trim().take(120),
        "notes" to notes?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
        "status" to statusEnum().name,
        "reminderEnabled" to reminderEnabled,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
}

@Keep
@IgnoreExtraProperties
data class BorrowingRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val personName: String = "",
    val principalAmount: Double = 0.0,
    val repaidAmount: Double = 0.0,
    val accountId: String = "",
    val accountName: String = "",
    val transactionId: String = "",
    val borrowedDateMillis: Long = System.currentTimeMillis(),
    val expectedRepaymentDateMillis: Long? = null,
    val description: String = "",
    val notes: String? = null,
    val status: String = LoanStatus.OPEN.name,
    val reminderEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val outstandingDebt: Double get() = (principalAmount - repaidAmount).coerceAtLeast(0.0)
    fun statusEnum(): LoanStatus =
        enumValues<LoanStatus>().firstOrNull { it.name == status } ?: LoanStatus.OPEN

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "personName" to personName.trim().take(80),
        "principalAmount" to principalAmount,
        "repaidAmount" to repaidAmount.coerceIn(0.0, principalAmount),
        "accountId" to accountId,
        "accountName" to accountName.trim().take(60),
        "transactionId" to transactionId,
        "borrowedDateMillis" to borrowedDateMillis.coerceAtLeast(1L),
        "expectedRepaymentDateMillis" to expectedRepaymentDateMillis,
        "description" to description.trim().take(120),
        "notes" to notes?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
        "status" to statusEnum().name,
        "reminderEnabled" to reminderEnabled,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
}

@Keep
@IgnoreExtraProperties
data class RepaymentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val recordType: String = RepaymentRecordType.LENDING.name,
    val loanId: String = "",
    val personName: String = "",
    val amount: Double = 0.0,
    val accountId: String = "",
    val accountName: String = "",
    val transactionId: String = "",
    val repaidAtMillis: Long = System.currentTimeMillis(),
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun recordTypeEnum(): RepaymentRecordType =
        enumValues<RepaymentRecordType>().firstOrNull { it.name == recordType } ?: RepaymentRecordType.LENDING

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "recordType" to recordTypeEnum().name,
        "loanId" to loanId,
        "personName" to personName.trim().take(80),
        "amount" to amount,
        "accountId" to accountId,
        "accountName" to accountName.trim().take(60),
        "transactionId" to transactionId,
        "repaidAtMillis" to repaidAtMillis.coerceAtLeast(1L),
        "notes" to notes?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
}

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val kind: String = NotificationKind.SYSTEM.name,
    val relatedId: String? = null,
    val read: Boolean = false,
    val triggeredAtMillis: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun kindEnum(): NotificationKind =
        enumValues<NotificationKind>().firstOrNull { it.name == kind } ?: NotificationKind.SYSTEM

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "title" to title.trim().take(100),
        "message" to message.trim().take(300),
        "kind" to kindEnum().name,
        "relatedId" to relatedId?.takeIf { it.isNotBlank() },
        "read" to read,
        "triggeredAtMillis" to triggeredAtMillis.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
}

@Keep
@IgnoreExtraProperties
data class TransactionImage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val transactionId: String = "",
    val fileName: String = "",
    val fileType: String = "image/jpeg",
    val storageRefPath: String = "",
    val downloadUrl: String? = null,
    val localFilePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val uploadedAtMillis: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "transactionId" to transactionId,
        "fileName" to fileName.trim().take(160).ifBlank { "receipt.jpg" },
        "fileType" to fileType.lowercase().take(40),
        "storageRefPath" to storageRefPath.take(300),
        "downloadUrl" to downloadUrl?.take(1000)?.takeIf { it.isNotBlank() },
        "localFilePath" to localFilePath?.take(400)?.takeIf { it.isNotBlank() },
        "fileSizeBytes" to fileSizeBytes.coerceAtLeast(1L),
        "uploadedAtMillis" to uploadedAtMillis.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "transactionId" to transactionId,
        "fileName" to fileName.trim().take(160).ifBlank { "receipt.jpg" },
        "fileType" to fileType.lowercase().take(40),
        "storageRefPath" to storageRefPath.take(300),
        "downloadUrl" to (downloadUrl?.take(1000)?.takeIf { it.isNotBlank() } ?: FieldValue.delete()),
        "localFilePath" to (localFilePath?.take(400)?.takeIf { it.isNotBlank() } ?: FieldValue.delete()),
        "fileSizeBytes" to fileSizeBytes.coerceAtLeast(1L),
        "uploadedAtMillis" to uploadedAtMillis.coerceAtLeast(1L),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
