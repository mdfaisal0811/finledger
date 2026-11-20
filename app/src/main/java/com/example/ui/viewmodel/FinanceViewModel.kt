package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.export.ExportManager
import com.example.data.export.Formatters
import com.example.data.export.NotificationHelper
import com.example.data.export.WorkspaceBackupPayload
import com.example.data.firestore.FinanceRepository
import com.example.data.media.PreparedReceiptImage
import com.example.domain.model.Account
import com.example.domain.model.AppNotification
import com.example.domain.model.BorrowingRecord
import com.example.domain.model.Budget
import com.example.domain.model.BudgetProgress
import com.example.domain.model.Category
import com.example.domain.model.DashboardDateRange
import com.example.domain.model.DashboardSummary
import com.example.domain.model.FinancialEngine
import com.example.domain.model.FinancialStatement
import com.example.domain.model.LendingRecord
import com.example.domain.model.LoanStatus
import com.example.domain.model.NotificationKind
import com.example.domain.model.RepaymentRecord
import com.example.domain.model.TransactionImage
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionSortOrder
import com.example.domain.model.TransactionType
import com.example.domain.model.UserSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val Context.finLedgerPrefs by preferencesDataStore(name = "finledger_local_prefs")
private val KEY_PIN_HASH = stringPreferencesKey("app_pin_hash")

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class TransactionFilterState(
    val searchQuery: String = "",
    val selectedType: TransactionType? = null,
    val onlyTransferCharges: Boolean = false,
    val onlyRepayments: Boolean = false,
    val selectedAccountId: String? = null,
    val selectedCategoryId: String? = null,
    val dateStartMillis: Long? = null,
    val dateEndMillis: Long? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val sortOrder: TransactionSortOrder = TransactionSortOrder.NEWEST_FIRST
)

data class FinanceWorkspaceData(
    val settings: UserSettings = UserSettings(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<TransactionRecord> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val lendingRecords: List<LendingRecord> = emptyList(),
    val borrowingRecords: List<BorrowingRecord> = emptyList(),
    val repayments: List<RepaymentRecord> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val transactionImages: List<TransactionImage> = emptyList(),
    val dashboardSummary: DashboardSummary = FinancialEngine.computeDashboardSummary(
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0L, Long.MAX_VALUE
    ),
    val selectedDateRange: DashboardDateRange = DashboardDateRange.THIS_MONTH,
    val customStartMillis: Long? = null,
    val customEndMillis: Long? = null
)

class FinanceViewModel(
    private val repository: FinanceRepository,
    private val currentUserId: String,
    private val appContext: Context
) : ViewModel() {

    private val _selectedDateRange = MutableStateFlow(DashboardDateRange.THIS_MONTH)
    private val _customDateRange = MutableStateFlow<Pair<Long?, Long?>>(null to null)
    private val _txFilter = MutableStateFlow(TransactionFilterState())
    val txFilter: StateFlow<TransactionFilterState> = _txFilter.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _storedPin = MutableStateFlow<String?>(null)
    val storedPin: StateFlow<String?> = _storedPin.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private var dueCheckDone = false

    init {
        viewModelScope.launch {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null && currentUser.uid == currentUserId) {
                repository.ensureWorkspaceInitialized(currentUser)
            }
        }
        viewModelScope.launch {
            appContext.finLedgerPrefs.data.collect { prefs ->
                val pin = prefs[KEY_PIN_HASH]
                _storedPin.value = pin
            }
        }
    }

    private fun <T> Flow<T>.unwrapFirestoreErrors(tag: String): Flow<Result<T>> =
        this.map { Result.success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $tag", rootError)
                emit(Result.failure(rootError))
            }

    private val settingsFlow = repository.observeUserSettings(currentUserId).unwrapFirestoreErrors("settings")
    private val accountsFlow = repository.observeAccounts(currentUserId).unwrapFirestoreErrors("accounts")
    private val categoriesFlow = repository.observeCategories(currentUserId).unwrapFirestoreErrors("categories")
    private val transactionsFlow = repository.observeTransactions(currentUserId).unwrapFirestoreErrors("transactions")
    private val budgetsFlow = repository.observeBudgets(currentUserId).unwrapFirestoreErrors("budgets")
    private val lendingFlow = repository.observeLendingRecords(currentUserId).unwrapFirestoreErrors("lending")
    private val borrowingFlow = repository.observeBorrowingRecords(currentUserId).unwrapFirestoreErrors("borrowing")
    private val repaymentsFlow = repository.observeRepayments(currentUserId).unwrapFirestoreErrors("repayments")
    private val notificationsFlow = repository.observeNotifications(currentUserId).unwrapFirestoreErrors("notifications")
    private val transactionImagesFlow = repository.observeTransactionImages(currentUserId).unwrapFirestoreErrors("transaction_images")

    val workspaceState: StateFlow<UiState<FinanceWorkspaceData>> = combine(
        combine(settingsFlow, accountsFlow, categoriesFlow, transactionsFlow) { s, a, c, t ->
            listOf(s, a, c, t)
        },
        combine(
            budgetsFlow,
            lendingFlow,
            borrowingFlow,
            repaymentsFlow,
            combine(notificationsFlow, transactionImagesFlow) { n, imgs -> n to imgs }
        ) { b, l, br, r, nPair ->
            listOf(b, l, br, r, nPair.first, nPair.second)
        },
        _selectedDateRange,
        _customDateRange
    ) { part1, part2, range, customBounds ->
        @Suppress("UNCHECKED_CAST")
        val settingsRes = part1[0] as Result<UserSettings?>
        @Suppress("UNCHECKED_CAST")
        val accountsRes = part1[1] as Result<List<Account>>
        @Suppress("UNCHECKED_CAST")
        val categoriesRes = part1[2] as Result<List<Category>>
        @Suppress("UNCHECKED_CAST")
        val txsRes = part1[3] as Result<List<TransactionRecord>>
        @Suppress("UNCHECKED_CAST")
        val budgetsRes = part2[0] as Result<List<Budget>>
        @Suppress("UNCHECKED_CAST")
        val lendingRes = part2[1] as Result<List<LendingRecord>>
        @Suppress("UNCHECKED_CAST")
        val borrowingRes = part2[2] as Result<List<BorrowingRecord>>
        @Suppress("UNCHECKED_CAST")
        val repaymentsRes = part2[3] as Result<List<RepaymentRecord>>
        @Suppress("UNCHECKED_CAST")
        val notificationsRes = part2[4] as Result<List<AppNotification>>
        @Suppress("UNCHECKED_CAST")
        val txImagesRes = part2[5] as Result<List<TransactionImage>>

        val firstError = listOf(
            settingsRes, accountsRes, categoriesRes, txsRes,
            budgetsRes, lendingRes, borrowingRes, repaymentsRes, notificationsRes, txImagesRes
        ).firstOrNull { it.isFailure }?.exceptionOrNull()

        if (firstError != null) {
            UiState.Error(firstError.localizedMessage ?: "Failed to load workspace data.")
        } else {
            val settings = settingsRes.getOrNull() ?: UserSettings(userId = currentUserId)
            val accounts = accountsRes.getOrNull().orEmpty().sortedWith(
                compareBy<Account> { it.archived }.thenByDescending { it.defaultAccount }.thenBy { it.name }
            )
            val categories = categoriesRes.getOrNull().orEmpty().sortedBy { it.name }
            val transactions = txsRes.getOrNull().orEmpty().sortedByDescending { it.occurredAtMillis }
            val budgets = budgetsRes.getOrNull().orEmpty()
            val lending = lendingRes.getOrNull().orEmpty().sortedByDescending { it.lentDateMillis }
            val borrowing = borrowingRes.getOrNull().orEmpty().sortedByDescending { it.borrowedDateMillis }
            val repayments = repaymentsRes.getOrNull().orEmpty().sortedByDescending { it.repaidAtMillis }
            val notifications = notificationsRes.getOrNull().orEmpty().sortedByDescending { it.triggeredAtMillis }
            val transactionImages = txImagesRes.getOrNull().orEmpty().sortedByDescending { it.uploadedAtMillis }

            if (settings.appLockEnabled && !_storedPin.value.isNullOrBlank() && !dueCheckDone) {
                _isAppLocked.value = true
            }

            val (startMillis, endMillis) = FinancialEngine.getDateRangeBounds(
                range,
                customBounds.first,
                customBounds.second
            )

            val summary = FinancialEngine.computeDashboardSummary(
                accounts = accounts,
                allTransactions = transactions,
                categories = categories,
                budgets = budgets,
                lendingRecords = lending,
                borrowingRecords = borrowing,
                startMillis = startMillis,
                endMillis = endMillis
            )

            if (!dueCheckDone && settings.notificationsEnabled) {
                dueCheckDone = true
                checkDueReminders(settings, lending, borrowing, notifications)
            }

            UiState.Success(
                FinanceWorkspaceData(
                    settings = settings,
                    accounts = accounts,
                    categories = categories,
                    transactions = transactions,
                    budgets = budgets,
                    lendingRecords = lending,
                    borrowingRecords = borrowing,
                    repayments = repayments,
                    notifications = notifications,
                    transactionImages = transactionImages,
                    dashboardSummary = summary,
                    selectedDateRange = range,
                    customStartMillis = customBounds.first,
                    customEndMillis = customBounds.second
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading
    )

    val filteredTransactions: StateFlow<List<TransactionRecord>> = combine(
        workspaceState,
        _txFilter
    ) { state, filter ->
        val allTxs = (state as? UiState.Success)?.data?.transactions ?: return@combine emptyList()
        val query = filter.searchQuery.trim().lowercase()
        val filtered = allTxs.filter { tx ->
            val matchesQuery = query.isEmpty() ||
                tx.description.lowercase().contains(query) ||
                tx.accountName.lowercase().contains(query) ||
                (tx.toAccountName?.lowercase()?.contains(query) == true) ||
                (tx.categoryName?.lowercase()?.contains(query) == true) ||
                (tx.counterpartyName?.lowercase()?.contains(query) == true) ||
                (tx.notes?.lowercase()?.contains(query) == true)

            val matchesType = when {
                filter.onlyTransferCharges -> tx.chargeAmount > 0.0 && (tx.typeEnum() == TransactionType.TRANSFER || tx.typeEnum() == TransactionType.CREDIT_CARD_PAYMENT)
                filter.onlyRepayments -> tx.typeEnum() == TransactionType.LENT_REPAYMENT || tx.typeEnum() == TransactionType.BORROWED_REPAYMENT
                filter.selectedType != null -> tx.typeEnum() == filter.selectedType
                else -> true
            }

            val matchesAccount = filter.selectedAccountId == null ||
                tx.accountId == filter.selectedAccountId ||
                tx.toAccountId == filter.selectedAccountId

            val matchesCategory = filter.selectedCategoryId == null ||
                tx.categoryId == filter.selectedCategoryId

            val matchesDateStart = filter.dateStartMillis == null || tx.occurredAtMillis >= filter.dateStartMillis
            val matchesDateEnd = filter.dateEndMillis == null || tx.occurredAtMillis <= filter.dateEndMillis
            val matchesMin = filter.minAmount == null || tx.amount >= filter.minAmount
            val matchesMax = filter.maxAmount == null || tx.amount <= filter.maxAmount

            matchesQuery && matchesType && matchesAccount && matchesCategory &&
                matchesDateStart && matchesDateEnd && matchesMin && matchesMax
        }

        when (filter.sortOrder) {
            TransactionSortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.occurredAtMillis }
            TransactionSortOrder.OLDEST_FIRST -> filtered.sortedBy { it.occurredAtMillis }
            TransactionSortOrder.HIGHEST_AMOUNT -> filtered.sortedByDescending { it.amount }
            TransactionSortOrder.LOWEST_AMOUNT -> filtered.sortedBy { it.amount }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun setDashboardDateRange(range: DashboardDateRange, customStart: Long? = null, customEnd: Long? = null) {
        _selectedDateRange.value = range
        if (range == DashboardDateRange.CUSTOM) {
            _customDateRange.value = customStart to customEnd
        }
    }

    fun updateTransactionFilter(transform: (TransactionFilterState) -> TransactionFilterState) {
        _txFilter.value = transform(_txFilter.value)
    }

    fun resetTransactionFilter() {
        _txFilter.value = TransactionFilterState()
    }

    fun unlockWithPin(pin: String): Boolean {
        val matches = _storedPin.value == pin
        if (matches) _isAppLocked.value = false
        return matches
    }

    fun setPinAndEnableLock(pin: String, enabled: Boolean) {
        viewModelScope.launch {
            appContext.finLedgerPrefs.edit { prefs ->
                if (pin.isNotBlank()) prefs[KEY_PIN_HASH] = pin
            }
            repository.updateSettings(mapOf("appLockEnabled" to enabled))
                .onSuccess { emitMessage(if (enabled) "App lock enabled" else "App lock disabled") }
                .onFailure { emitMessage(it.localizedMessage ?: "Failed to update app lock") }
        }
    }

    fun addOrUpdateAccount(account: Account) = runAction(
        successMsg = if (account.id.isBlank()) "Account added" else "Account updated"
    ) {
        repository.saveAccount(account)
    }

    fun archiveAccount(accountId: String, archived: Boolean) = runAction(
        successMsg = if (archived) "Account archived" else "Account restored"
    ) {
        repository.archiveAccount(accountId, archived)
    }

    fun deleteAccount(accountId: String, migrateTo: Account?) = runAction(
        successMsg = if (migrateTo != null) "Account deleted and transactions migrated" else "Account deleted"
    ) {
        repository.deleteAccountWithMigration(accountId, migrateTo)
    }

    fun saveCategory(category: Category) = runAction(
        successMsg = if (category.id.isBlank()) "Category created" else "Category updated"
    ) {
        repository.saveCategory(category)
    }

    fun deleteCategory(categoryId: String) = runAction("Category deleted") {
        repository.deleteCategory(categoryId)
    }

    fun addTransaction(
        tx: TransactionRecord,
        pendingImages: List<PreparedReceiptImage> = emptyList(),
        onDone: () -> Unit = {}
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.addTransaction(tx)
            var attachmentFailures = 0
            res.onSuccess { txId ->
                for (img in pendingImages) {
                    if (repository.addTransactionImage(txId, img).isFailure) attachmentFailures++
                }
                val suffix = if (pendingImages.isNotEmpty()) " (${pendingImages.size - attachmentFailures} receipt(s) attached)" else ""
                emitMessage("${tx.typeEnum().label} recorded$suffix")
                evaluateBudgetAlertsAfterSpend(tx)
                onDone()
            }.onFailure {
                emitMessage(it.localizedMessage ?: "Failed to save transaction")
            }
            _isBusy.value = false
        }
    }

    fun updateTransaction(
        oldTx: TransactionRecord,
        updatedTx: TransactionRecord,
        pendingImages: List<PreparedReceiptImage> = emptyList(),
        onDone: () -> Unit = {}
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateTransaction(oldTx, updatedTx)
            res.onSuccess {
                for (img in pendingImages) {
                    repository.addTransactionImage(oldTx.id, img)
                }
                emitMessage("Transaction updated")
                evaluateBudgetAlertsAfterSpend(updatedTx)
                onDone()
            }.onFailure {
                emitMessage(it.localizedMessage ?: "Failed to update transaction")
            }
            _isBusy.value = false
        }
    }

    fun deleteTransaction(tx: TransactionRecord) = runAction("Transaction deleted and balances updated") {
        repository.deleteTransaction(tx)
    }

    fun createLending(
        record: LendingRecord,
        pendingImages: List<PreparedReceiptImage> = emptyList(),
        onDone: () -> Unit = {}
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createLending(record)
            res.onSuccess { txId ->
                for (img in pendingImages) {
                    repository.addTransactionImage(txId, img)
                }
                emitMessage("Lending record saved")
                onDone()
            }.onFailure {
                emitMessage(it.localizedMessage ?: "Failed to save lending record")
            }
            _isBusy.value = false
        }
    }

    fun updateLending(
        oldRecord: LendingRecord,
        updated: LendingRecord,
        pendingImages: List<PreparedReceiptImage> = emptyList(),
        onDone: () -> Unit = {}
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateLending(oldRecord, updated)
            res.onSuccess {
                if (oldRecord.transactionId.isNotBlank()) {
                    for (img in pendingImages) {
                        repository.addTransactionImage(oldRecord.transactionId, img)
                    }
                }
                emitMessage("Lending record updated")
                onDone()
            }.onFailure {
                emitMessage(it.localizedMessage ?: "Failed to update lending record")
            }
            _isBusy.value = false
        }
    }

    fun deleteLending(record: LendingRecord) = runAction("Lending record deleted") {
        repository.deleteLending(record)
    }

    fun addLendingRepayment(
        record: LendingRecord,
        amount: Double,
        account: Account,
        repaidAtMillis: Long,
        notes: String?,
        pendingImages: List<PreparedReceiptImage> = emptyList(),
        onDone: () -> Unit = {}
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.addLendingRepayment(record, amount, account, repaidAtMillis, notes)
            res.onSuccess { txId ->
                for (img in pendingImages) {
                    repository.addTransactionImage(txId, img)
                }
                emitMessage("Repayment recorded")
                onDone()
            }.onFailure {
                emitMessage(it.localizedMessage ?: "Failed to record repayment")
            }
            _isBusy.value = false
        }
    }

    fun createBorrowing(
        record: BorrowingRecord,
        pendingImages: List<PreparedReceiptImage> = emptyList(),
        onDone: () -> Unit = {}
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createBorrowing(record)
            res.onSuccess { txId ->
                for (img in pendingImages) {
                    repository.addTransactionImage(txId, img)
                }
                emitMessage("Borrowing record saved")
                onDone()
            }.onFailure {
                emitMessage(it.localizedMessage ?: "Failed to save borrowing record")
            }
            _isBusy.value = false
        }
    }

    fun updateBorrowing(
        oldRecord: BorrowingRecord,
        updated: BorrowingRecord,
        pendingImages: List<PreparedReceiptImage> = emptyList(),
        onDone: () -> Unit = {}
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateBorrowing(oldRecord, updated)
            res.onSuccess {
                if (oldRecord.transactionId.isNotBlank()) {
                    for (img in pendingImages) {
                        repository.addTransactionImage(oldRecord.transactionId, img)
                    }
                }
                emitMessage("Borrowing record updated")
                onDone()
            }.onFailure {
                emitMessage(it.localizedMessage ?: "Failed to update borrowing record")
            }
            _isBusy.value = false
        }
    }

    fun deleteBorrowing(record: BorrowingRecord) = runAction("Borrowing record deleted") {
        repository.deleteBorrowing(record)
    }

    fun addBorrowingRepayment(
        record: BorrowingRecord,
        amount: Double,
        account: Account,
        repaidAtMillis: Long,
        notes: String?,
        pendingImages: List<PreparedReceiptImage> = emptyList(),
        onDone: () -> Unit = {}
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.addBorrowingRepayment(record, amount, account, repaidAtMillis, notes)
            res.onSuccess { txId ->
                for (img in pendingImages) {
                    repository.addTransactionImage(txId, img)
                }
                emitMessage("Debt repayment recorded")
                onDone()
            }.onFailure {
                emitMessage(it.localizedMessage ?: "Failed to record repayment")
            }
            _isBusy.value = false
        }
    }

    fun deleteRepayment(repayment: RepaymentRecord) = runAction("Repayment deleted and reversed") {
        repository.deleteRepayment(repayment)
    }

    fun attachImagesToTransaction(transactionId: String, images: List<PreparedReceiptImage>) {
        if (transactionId.isBlank() || images.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            var count = 0
            for (img in images) {
                if (repository.addTransactionImage(transactionId, img).isSuccess) count++
            }
            _isBusy.value = false
            if (count > 0) emitMessage("$count receipt image(s) attached")
            else emitMessage("Failed to attach receipt image")
        }
    }

    fun replaceTransactionImage(existing: TransactionImage, replacement: PreparedReceiptImage) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.replaceTransactionImage(existing, replacement)
            _isBusy.value = false
            emitMessage(if (res.isSuccess) "Receipt image replaced" else "Failed to replace image")
        }
    }

    fun deleteTransactionImage(image: TransactionImage) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.deleteTransactionImage(image)
            _isBusy.value = false
            emitMessage(if (res.isSuccess) "Receipt image deleted" else "Failed to delete image")
        }
    }

    fun saveBudget(budget: Budget, onDone: () -> Unit = {}) = runAction(
        successMsg = if (budget.id.isBlank()) "Budget created" else "Budget updated",
        onDone = onDone
    ) {
        repository.saveBudget(budget)
    }

    fun deleteBudget(budgetId: String) = runAction("Budget deleted") {
        repository.deleteBudget(budgetId)
    }

    fun updateSettings(updates: Map<String, Any?>, message: String? = null) = runAction(message) {
        repository.updateSettings(updates)
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch { repository.markNotificationRead(id) }
    }

    fun markAllNotificationsRead() {
        val notifs = (workspaceState.value as? UiState.Success)?.data?.notifications.orEmpty()
        viewModelScope.launch { repository.markAllNotificationsRead(notifs) }
    }

    fun exportCsv(uri: Uri) {
        val data = (workspaceState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = ExportManager.writeTransactionsCsv(
                context = appContext,
                uri = uri,
                transactions = filteredTransactions.value.ifEmpty { data.transactions },
                currencySymbol = data.settings.currencySymbol,
                datePattern = data.settings.dateFormat
            )
            emitMessage(if (res.isSuccess) "CSV exported successfully" else "CSV export failed")
        }
    }

    fun exportPdf(uri: Uri) {
        val data = (workspaceState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = ExportManager.writeReportPdf(
                context = appContext,
                uri = uri,
                title = "FinLedger Financial Report",
                periodLabel = data.selectedDateRange.label,
                summary = data.dashboardSummary,
                accounts = data.accounts,
                transactions = data.transactions,
                currencySymbol = data.settings.currencySymbol,
                datePattern = data.settings.dateFormat
            )
            emitMessage(if (res.isSuccess) "PDF report exported" else "PDF export failed")
        }
    }

    fun exportStatementCsv(uri: Uri, statement: FinancialStatement) {
        val data = (workspaceState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = ExportManager.writeStatementCsv(
                context = appContext,
                uri = uri,
                statement = statement,
                currencySymbol = data.settings.currencySymbol,
                datePattern = data.settings.dateFormat
            )
            emitMessage(if (res.isSuccess) "Statement CSV exported" else "Statement CSV export failed")
        }
    }

    fun exportStatementPdf(uri: Uri, statement: FinancialStatement) {
        val data = (workspaceState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = ExportManager.writeStatementPdf(
                context = appContext,
                uri = uri,
                statement = statement,
                currencySymbol = data.settings.currencySymbol,
                datePattern = data.settings.dateFormat
            )
            emitMessage(if (res.isSuccess) "Statement PDF saved" else "Statement PDF export failed")
        }
    }

    fun shareStatementPdf(activityContext: Context, statement: FinancialStatement) {
        val data = (workspaceState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = ExportManager.createShareableStatementPdf(
                context = activityContext,
                statement = statement,
                currencySymbol = data.settings.currencySymbol,
                datePattern = data.settings.dateFormat
            )
            res.onSuccess { pdfUri ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, pdfUri)
                    putExtra(Intent.EXTRA_SUBJECT, statement.title)
                    putExtra(Intent.EXTRA_TEXT, "${statement.title} (${statement.dateRangeLabel})")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activityContext.startActivity(Intent.createChooser(shareIntent, "Share Statement PDF"))
            }.onFailure {
                emitMessage("Unable to share statement: ${it.localizedMessage}")
            }
        }
    }

    fun exportBackupJson(uri: Uri) {
        val data = (workspaceState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val payload = WorkspaceBackupPayload(
                accounts = data.accounts,
                categories = data.categories,
                transactions = data.transactions,
                budgets = data.budgets,
                lendingRecords = data.lendingRecords,
                borrowingRecords = data.borrowingRecords,
                repayments = data.repayments
            )
            val res = ExportManager.writeWorkspaceBackupJson(appContext, uri, payload)
            emitMessage(if (res.isSuccess) "Workspace backup saved" else "Backup export failed")
        }
    }

    fun restoreBackupJson(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            val parsed = ExportManager.readWorkspaceBackupJson(appContext, uri)
            val res = parsed.fold(
                onSuccess = { payload ->
                    repository.restoreWorkspaceBackup(
                        accounts = payload.accounts,
                        categories = payload.categories,
                        transactions = payload.transactions,
                        budgets = payload.budgets,
                        lendingRecords = payload.lendingRecords,
                        borrowingRecords = payload.borrowingRecords,
                        repayments = payload.repayments
                    )
                },
                onFailure = { Result.failure(it) }
            )
            _isBusy.value = false
            emitMessage(if (res.isSuccess) "Backup restored successfully" else "Restore failed: ${res.exceptionOrNull()?.localizedMessage}")
        }
    }

    private fun evaluateBudgetAlertsAfterSpend(tx: TransactionRecord) {
        val data = (workspaceState.value as? UiState.Success)?.data ?: return
        if (!data.settings.notificationsEnabled) return
        if (FinancialEngine.expenseContribution(tx) <= 0.0) return

        val updatedTxs = data.transactions + tx
        val progressList: List<BudgetProgress> = FinancialEngine.computeBudgetProgress(data.budgets, updatedTxs)
        viewModelScope.launch {
            progressList.forEach { bp ->
                val crossed = bp.highestCrossedThreshold
                if (crossed != null && crossed > bp.budget.lastNotifiedThreshold) {
                    val title = "${bp.budget.name}: $crossed% reached"
                    val spentStr = Formatters.formatMoney(bp.spentAmount, data.settings.currencySymbol)
                    val limitStr = Formatters.formatMoney(bp.budget.limitAmount, data.settings.currencySymbol)
                    val msg = "Spent $spentStr of $limitStr limit."
                    val notif = AppNotification(
                        userId = currentUserId,
                        title = title,
                        message = msg,
                        kind = NotificationKind.BUDGET_THRESHOLD.name,
                        relatedId = bp.budget.id,
                        triggeredAtMillis = System.currentTimeMillis()
                    )
                    repository.createNotificationAndUpdateBudgetThreshold(bp.budget.id, crossed, notif)
                    NotificationHelper.showAlertNotification(appContext, title, msg)
                }
            }
        }
    }

    private fun checkDueReminders(
        settings: UserSettings,
        lending: List<LendingRecord>,
        borrowing: List<BorrowingRecord>,
        existingNotifications: List<AppNotification>
    ) {
        val now = System.currentTimeMillis()
        val windowMillis = settings.dueDateReminderDays * 86_400_000L
        val existingRelatedIds = existingNotifications
            .filter { now - it.triggeredAtMillis < 86_400_000L }
            .mapNotNull { it.relatedId }
            .toSet()

        viewModelScope.launch {
            lending.filter { it.reminderEnabled && it.statusEnum() != LoanStatus.SETTLED }
                .forEach { loan ->
                    val due = loan.expectedReturnDateMillis ?: return@forEach
                    if (due - now <= windowMillis && loan.id !in existingRelatedIds) {
                        val title = "Lent Money Due: ${loan.personName}"
                        val msg = "${Formatters.formatMoney(loan.outstandingAmount, settings.currencySymbol)} expected back by ${Formatters.formatDate(due, settings.dateFormat)}."
                        repository.createNotificationAndUpdateBudgetThreshold(
                            budgetId = null,
                            newThreshold = null,
                            notification = AppNotification(
                                userId = currentUserId,
                                title = title,
                                message = msg,
                                kind = NotificationKind.LOAN_DUE.name,
                                relatedId = loan.id,
                                triggeredAtMillis = now
                            )
                        )
                    }
                }

            borrowing.filter { it.reminderEnabled && it.statusEnum() != LoanStatus.SETTLED }
                .forEach { loan ->
                    val due = loan.expectedRepaymentDateMillis ?: return@forEach
                    if (due - now <= windowMillis && loan.id !in existingRelatedIds) {
                        val title = "Debt Repayment Due: ${loan.personName}"
                        val msg = "${Formatters.formatMoney(loan.outstandingDebt, settings.currencySymbol)} due by ${Formatters.formatDate(due, settings.dateFormat)}."
                        repository.createNotificationAndUpdateBudgetThreshold(
                            budgetId = null,
                            newThreshold = null,
                            notification = AppNotification(
                                userId = currentUserId,
                                title = title,
                                message = msg,
                                kind = NotificationKind.LOAN_DUE.name,
                                relatedId = loan.id,
                                triggeredAtMillis = now
                            )
                        )
                    }
                }
        }
    }

    private fun runAction(
        successMsg: String? = null,
        onDone: () -> Unit = {},
        block: suspend () -> Result<Any?>
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = block()
            _isBusy.value = false
            res.onSuccess {
                if (!successMsg.isNullOrBlank()) emitMessage(successMsg)
                onDone()
            }.onFailure {
                emitMessage(it.localizedMessage ?: "Operation failed")
            }
        }
    }

    fun emitMessage(msg: String) {
        _snackbarEvents.tryEmit(msg)
    }

    companion object {
        private const val TAG = "FinanceVM"
    }
}
