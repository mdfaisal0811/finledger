package com.example.data.firestore

import android.content.Context
import com.example.R
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.AppNotification
import com.example.domain.model.BorrowingRecord
import com.example.domain.model.Budget
import com.example.domain.model.Category
import com.example.domain.model.FinancialEngine
import com.example.domain.model.LendingRecord
import com.example.domain.model.LoanStatus
import com.example.domain.model.NotificationKind
import com.example.domain.model.RepaymentRecord
import com.example.domain.model.RepaymentRecordType
import com.example.domain.model.TransactionImage
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import com.example.domain.model.UserSettings
import com.example.data.media.PreparedReceiptImage
import com.example.data.media.ReceiptImageManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FinanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun accountsCol(uid: String) = userDoc(uid).collection("accounts")
    private fun categoriesCol(uid: String) = userDoc(uid).collection("categories")
    private fun transactionsCol(uid: String) = userDoc(uid).collection("transactions")
    private fun budgetsCol(uid: String) = userDoc(uid).collection("budgets")
    private fun lendingCol(uid: String) = userDoc(uid).collection("lending_records")
    private fun borrowingCol(uid: String) = userDoc(uid).collection("borrowing_records")
    private fun repaymentsCol(uid: String) = userDoc(uid).collection("repayments")
    private fun notificationsCol(uid: String) = userDoc(uid).collection("notifications")
    private fun transactionImagesCol(uid: String) = userDoc(uid).collection("transaction_images")

    private fun <T> observeCollection(
        uid: String,
        path: String,
        query: Query,
        clazz: Class<T>
    ): Flow<List<T>> = flow {
        requireUserId()
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeUserSettings(uid: String): Flow<UserSettings?> = flow {
        requireUserId()
        val path = "users/$uid"
        emitAll(
            userDoc(uid).snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeAccounts(uid: String): Flow<List<Account>> =
        observeCollection(uid, "users/$uid/accounts", accountsCol(uid), Account::class.java)

    fun observeCategories(uid: String): Flow<List<Category>> =
        observeCollection(uid, "users/$uid/categories", categoriesCol(uid), Category::class.java)

    fun observeTransactions(uid: String): Flow<List<TransactionRecord>> =
        observeCollection(
            uid,
            "users/$uid/transactions",
            transactionsCol(uid).orderBy("occurredAtMillis", Query.Direction.DESCENDING),
            TransactionRecord::class.java
        )

    fun observeBudgets(uid: String): Flow<List<Budget>> =
        observeCollection(uid, "users/$uid/budgets", budgetsCol(uid), Budget::class.java)

    fun observeLendingRecords(uid: String): Flow<List<LendingRecord>> =
        observeCollection(uid, "users/$uid/lending_records", lendingCol(uid), LendingRecord::class.java)

    fun observeBorrowingRecords(uid: String): Flow<List<BorrowingRecord>> =
        observeCollection(uid, "users/$uid/borrowing_records", borrowingCol(uid), BorrowingRecord::class.java)

    fun observeRepayments(uid: String): Flow<List<RepaymentRecord>> =
        observeCollection(uid, "users/$uid/repayments", repaymentsCol(uid), RepaymentRecord::class.java)

    fun observeNotifications(uid: String): Flow<List<AppNotification>> =
        observeCollection(uid, "users/$uid/notifications", notificationsCol(uid), AppNotification::class.java)

    fun observeTransactionImages(uid: String): Flow<List<TransactionImage>> =
        observeCollection(
            uid,
            "users/$uid/transaction_images",
            transactionImagesCol(uid).orderBy("uploadedAtMillis", Query.Direction.DESCENDING),
            TransactionImage::class.java
        )

    /**
     * Ensures user profile, default categories, and starter accounts exist on first sign-in.
     */
    suspend fun ensureWorkspaceInitialized(firebaseUser: FirebaseUser): Result<Unit> {
        val uid = firebaseUser.uid
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid)
            val userSnap = userDoc(uid).get().await()
            val catSnap = categoriesCol(uid).limit(1).get().await()
            val accSnap = accountsCol(uid).limit(1).get().await()

            val batch = db.batch()
            var hasWrites = false

            if (!userSnap.exists()) {
                val settings = UserSettings(
                    userId = uid,
                    displayName = firebaseUser.displayName?.takeIf { it.isNotBlank() } ?: "FinLedger User",
                    email = firebaseUser.email ?: "",
                    photoUrl = firebaseUser.photoUrl?.toString(),
                    defaultAccountId = "acc_default_cash"
                )
                batch.set(userDoc(uid), settings.toCreateMap())
                hasWrites = true
            }

            if (catSnap.isEmpty) {
                DefaultSeedData.defaultCategories(uid).forEach { (id, category) ->
                    batch.set(categoriesCol(uid).document(id), category.toCreateMap())
                }
                hasWrites = true
            }

            if (accSnap.isEmpty) {
                DefaultSeedData.defaultStarterAccounts(uid).forEach { (id, account) ->
                    batch.set(accountsCol(uid).document(id), account.toCreateMap())
                }
                hasWrites = true
            }

            if (hasWrites) {
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun updateSettings(updates: Map<String, Any?>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            val payload = updates.filterValues { it != null }
                .mapValues { it.value!! }
                .toMutableMap()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            userDoc(currentUid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun saveAccount(account: Account): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(account.name.isNotBlank()) { "Account name is required." }
            val isNew = account.id.isBlank()
            val docRef = if (isNew) accountsCol(currentUid).document() else accountsCol(currentUid).document(account.id)

            if (account.defaultAccount) {
                val existingDefaults = accountsCol(currentUid).whereEqualTo("defaultAccount", true).get().await()
                val batch = db.batch()
                existingDefaults.documents.forEach { doc ->
                    if (doc.id != docRef.id) {
                        batch.update(doc.reference, mapOf("defaultAccount" to false, "updatedAt" to FieldValue.serverTimestamp()))
                    }
                }
                if (isNew) {
                    val created = account.copy(userId = currentUid, currentBalance = account.openingBalance)
                    batch.set(docRef, created.toCreateMap())
                } else {
                    val existingAcc = docRef.get().await().toObject(Account::class.java)
                    val balanceDelta = if (existingAcc != null) account.openingBalance - existingAcc.openingBalance else 0.0
                    val updatedBalance = (existingAcc?.currentBalance ?: account.currentBalance) + balanceDelta
                    batch.update(docRef, mapOf(
                        "name" to account.name.trim().take(60),
                        "type" to account.typeEnum().name,
                        "openingBalance" to account.openingBalance,
                        "currentBalance" to updatedBalance,
                        "creditLimit" to account.creditLimit.coerceAtLeast(0.0),
                        "bankName" to (account.bankName?.trim()?.take(60)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
                        "accountNumber" to (account.accountNumber?.trim()?.take(40)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
                        "billingCycleDay" to account.billingCycleDay.coerceIn(1, 31),
                        "paymentDueDay" to account.paymentDueDay.coerceIn(1, 31),
                        "minimumPayment" to account.minimumPayment.coerceAtLeast(0.0),
                        "iconKey" to account.iconKey.take(40),
                        "colorHex" to account.colorHex,
                        "notes" to (account.notes?.trim()?.take(500)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
                        "archived" to account.archived,
                        "defaultAccount" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
                batch.update(userDoc(currentUid), mapOf("defaultAccountId" to docRef.id, "updatedAt" to FieldValue.serverTimestamp()))
                batch.commit().await()
            } else {
                if (isNew) {
                    val created = account.copy(userId = currentUid, currentBalance = account.openingBalance)
                    docRef.set(created.toCreateMap()).await()
                } else {
                    val existingAcc = docRef.get().await().toObject(Account::class.java)
                    val balanceDelta = if (existingAcc != null) account.openingBalance - existingAcc.openingBalance else 0.0
                    val updatedBalance = (existingAcc?.currentBalance ?: account.currentBalance) + balanceDelta
                    docRef.update(mapOf(
                        "name" to account.name.trim().take(60),
                        "type" to account.typeEnum().name,
                        "openingBalance" to account.openingBalance,
                        "currentBalance" to updatedBalance,
                        "creditLimit" to account.creditLimit.coerceAtLeast(0.0),
                        "bankName" to (account.bankName?.trim()?.take(60)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
                        "accountNumber" to (account.accountNumber?.trim()?.take(40)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
                        "billingCycleDay" to account.billingCycleDay.coerceIn(1, 31),
                        "paymentDueDay" to account.paymentDueDay.coerceIn(1, 31),
                        "minimumPayment" to account.minimumPayment.coerceAtLeast(0.0),
                        "iconKey" to account.iconKey.take(40),
                        "colorHex" to account.colorHex,
                        "notes" to (account.notes?.trim()?.take(500)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
                        "archived" to account.archived,
                        "defaultAccount" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/accounts")
            Result.failure(e)
        }
    }

    suspend fun archiveAccount(accountId: String, archived: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(accountId.isNotBlank())
            accountsCol(currentUid).document(accountId).update(
                mapOf("archived" to archived, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/accounts/$accountId")
            Result.failure(e)
        }
    }

    /**
     * Safely deletes an account: optionally migrates all linked transactions to another account
     * and recomputes destination balance atomically before deleting the account.
     */
    suspend fun deleteAccountWithMigration(accountId: String, migrateToAccount: Account?): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(accountId.isNotBlank())
            val allTxs = transactionsCol(currentUid).get().await()
                .toObjects(TransactionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val allAccounts = accountsCol(currentUid).get().await()
                .toObjects(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val batch = db.batch()

            if (migrateToAccount != null) {
                require(migrateToAccount.id != accountId) { "Cannot migrate to the same account." }
                val updatedTxs = allTxs.map { tx ->
                    val newFrom = if (tx.accountId == accountId) migrateToAccount.id else tx.accountId
                    val newFromName = if (tx.accountId == accountId) migrateToAccount.name else tx.accountName
                    val newTo = if (tx.toAccountId == accountId) migrateToAccount.id else tx.toAccountId
                    val newToName = if (tx.toAccountId == accountId) migrateToAccount.name else tx.toAccountName
                    if (newFrom != tx.accountId || newTo != tx.toAccountId) {
                        val migrated = tx.copy(
                            accountId = newFrom,
                            accountName = newFromName,
                            toAccountId = newTo,
                            toAccountName = newToName
                        )
                        // Prevent invalid self-transfer if both sides converged onto target
                        if ((migrated.typeEnum() == TransactionType.TRANSFER || migrated.typeEnum() == TransactionType.CREDIT_CARD_PAYMENT) && migrated.accountId == migrated.toAccountId) {
                            batch.delete(transactionsCol(currentUid).document(tx.id))
                            null
                        } else {
                            batch.update(transactionsCol(currentUid).document(tx.id), migrated.toUpdateMap())
                            migrated
                        }
                    } else {
                        tx
                    }
                }.filterNotNull()

                val remainingAccounts = allAccounts.filter { it.id != accountId }
                val recomputed = FinancialEngine.recomputeAllAccountBalances(remainingAccounts, updatedTxs)
                recomputed[migrateToAccount.id]?.let { newBal ->
                    batch.update(
                        accountsCol(currentUid).document(migrateToAccount.id),
                        mapOf("currentBalance" to newBal, "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
            } else {
                // Delete associated transactions if user explicitly chooses delete without migration
                allTxs.filter { it.accountId == accountId || it.toAccountId == accountId }.forEach { tx ->
                    batch.delete(transactionsCol(currentUid).document(tx.id))
                }
            }

            batch.delete(accountsCol(currentUid).document(accountId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/accounts/$accountId")
            Result.failure(e)
        }
    }

    suspend fun saveCategory(category: Category): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(category.name.isNotBlank()) { "Category name is required." }
            if (category.id.isBlank()) {
                val docRef = categoriesCol(currentUid).document()
                docRef.set(category.copy(userId = currentUid).toCreateMap()).await()
            } else {
                categoriesCol(currentUid).document(category.id).update(
                    mapOf(
                        "name" to category.name.trim().take(50),
                        "type" to category.typeEnum().name,
                        "iconKey" to category.iconKey.take(40),
                        "colorHex" to category.colorHex,
                        "systemDefault" to category.systemDefault,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/categories")
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            categoriesCol(currentUid).document(categoryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/categories/$categoryId")
            Result.failure(e)
        }
    }

    /**
     * Atomically creates a transaction (Expense, Income, Transfer + Charge, or Credit Card Payment)
     * and updates all affected account balances in a single Firestore transaction.
     */
    suspend fun addTransaction(tx: TransactionRecord): Result<String> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            validateTransactionInput(tx)
            val newRef = transactionsCol(currentUid).document()
            val prepared = tx.copy(id = newRef.id, userId = currentUid)

            db.runTransaction { transaction ->
                val accountIds = listOfNotNull(prepared.accountId, prepared.toAccountId).distinct()
                val accountsMap = mutableMapOf<String, Account>()
                // 1. All reads first
                for (accId in accountIds) {
                    val snap = transaction.get(accountsCol(currentUid).document(accId))
                    val acc = snap.toObject(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: throw IllegalStateException("Selected account no longer exists.")
                    accountsMap[accId] = acc
                }

                // 2. Compute deltas
                val deltas = FinancialEngine.computeAccountDeltas(prepared, accountsMap)

                // 3. All writes
                transaction.set(newRef, prepared.toCreateMap())
                deltas.forEach { (accId, delta) ->
                    val currentAcc = accountsMap[accId]!!
                    val newBalance = currentAcc.currentBalance + delta
                    transaction.update(
                        accountsCol(currentUid).document(accId),
                        mapOf("currentBalance" to newBalance, "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
            }.await()

            Result.success(newRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$uid/transactions")
            Result.failure(e)
        }
    }

    /**
     * Atomically edits an existing transaction (including Transfer amount/charge/accounts)
     * by reversing the previous account effects and applying the updated values in one Firestore transaction.
     */
    suspend fun updateTransaction(oldTx: TransactionRecord, updatedTx: TransactionRecord): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(oldTx.id.isNotBlank()) { "Transaction ID missing." }
            validateTransactionInput(updatedTx)
            val txRef = transactionsCol(currentUid).document(oldTx.id)
            val prepared = updatedTx.copy(id = oldTx.id, userId = currentUid)

            db.runTransaction { transaction ->
                // 1. Read transaction and all potentially affected accounts
                val txSnap = transaction.get(txRef)
                val latestOld = txSnap.toObject(TransactionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: oldTx
                val accountIds = listOfNotNull(
                    latestOld.accountId,
                    latestOld.toAccountId,
                    prepared.accountId,
                    prepared.toAccountId
                ).distinct()

                val accountsMap = mutableMapOf<String, Account>()
                for (accId in accountIds) {
                    val snap = transaction.get(accountsCol(currentUid).document(accId))
                    val acc = snap.toObject(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    if (acc != null) {
                        accountsMap[accId] = acc
                    }
                }

                val deltas = FinancialEngine.computeEditDeltas(latestOld, prepared, accountsMap)

                // 2. Apply updates atomically
                transaction.update(txRef, prepared.toUpdateMap())
                deltas.forEach { (accId, delta) ->
                    val acc = accountsMap[accId]
                    if (acc != null) {
                        transaction.update(
                            accountsCol(currentUid).document(accId),
                            mapOf(
                                "currentBalance" to (acc.currentBalance + delta),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/transactions/${oldTx.id}")
            Result.failure(e)
        }
    }

    /**
     * Atomically deletes a transaction and reverses its balance effects.
     * If linked to a repayment, reverses the repayment on the loan record too.
     */
    suspend fun deleteTransaction(tx: TransactionRecord): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(tx.id.isNotBlank())
            val txRef = transactionsCol(currentUid).document(tx.id)

            db.runTransaction { transaction ->
                val txSnap = transaction.get(txRef)
                val existingTx = txSnap.toObject(TransactionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: return@runTransaction

                val accountIds = listOfNotNull(existingTx.accountId, existingTx.toAccountId).distinct()
                val accountsMap = mutableMapOf<String, Account>()
                for (accId in accountIds) {
                    val accSnap = transaction.get(accountsCol(currentUid).document(accId))
                    accSnap.toObject(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.let {
                        accountsMap[accId] = it
                    }
                }

                // Read linked loan/repayment if applicable
                val loanId = existingTx.loanId
                val repaymentId = existingTx.repaymentId
                val lendingSnap = if (!loanId.isNullOrBlank() && existingTx.typeEnum() == TransactionType.LENT_REPAYMENT) {
                    transaction.get(lendingCol(currentUid).document(loanId))
                } else null
                val borrowingSnap = if (!loanId.isNullOrBlank() && existingTx.typeEnum() == TransactionType.BORROWED_REPAYMENT) {
                    transaction.get(borrowingCol(currentUid).document(loanId))
                } else null

                val deltas = FinancialEngine.computeAccountDeltas(existingTx, accountsMap)
                deltas.forEach { (accId, delta) ->
                    val acc = accountsMap[accId]
                    if (acc != null) {
                        transaction.update(
                            accountsCol(currentUid).document(accId),
                            mapOf(
                                "currentBalance" to (acc.currentBalance - delta),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

                if (lendingSnap != null && lendingSnap.exists()) {
                    val loan = lendingSnap.toObject(LendingRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    if (loan != null) {
                        val newReturned = (loan.returnedAmount - existingTx.amount).coerceAtLeast(0.0)
                        val newStatus = when {
                            newReturned <= 0.0 -> LoanStatus.OPEN.name
                            newReturned + 0.001 >= loan.principalAmount -> LoanStatus.SETTLED.name
                            else -> LoanStatus.PARTIAL.name
                        }
                        transaction.update(
                            lendingCol(currentUid).document(loan.id),
                            mapOf(
                                "returnedAmount" to newReturned,
                                "status" to newStatus,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

                if (borrowingSnap != null && borrowingSnap.exists()) {
                    val loan = borrowingSnap.toObject(BorrowingRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    if (loan != null) {
                        val newRepaid = (loan.repaidAmount - existingTx.amount).coerceAtLeast(0.0)
                        val newStatus = when {
                            newRepaid <= 0.0 -> LoanStatus.OPEN.name
                            newRepaid + 0.001 >= loan.principalAmount -> LoanStatus.SETTLED.name
                            else -> LoanStatus.PARTIAL.name
                        }
                        transaction.update(
                            borrowingCol(currentUid).document(loan.id),
                            mapOf(
                                "repaidAmount" to newRepaid,
                                "status" to newStatus,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

                if (!repaymentId.isNullOrBlank()) {
                    transaction.delete(repaymentsCol(currentUid).document(repaymentId))
                }

                transaction.delete(txRef)
            }.await()

            deleteImagesForTransactionIds(currentUid, listOf(tx.id))

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/transactions/${tx.id}")
            Result.failure(e)
        }
    }

    suspend fun createLending(record: LendingRecord): Result<String> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(record.personName.isNotBlank()) { "Person name is required." }
            require(record.principalAmount > 0.0) { "Amount must be greater than zero." }
            require(record.accountId.isNotBlank()) { "Payment account is required." }

            val loanRef = lendingCol(currentUid).document()
            val txRef = transactionsCol(currentUid).document()
            val tx = TransactionRecord(
                id = txRef.id,
                userId = currentUid,
                type = TransactionType.LENDING.name,
                amount = record.principalAmount,
                chargeAmount = 0.0,
                accountId = record.accountId,
                accountName = record.accountName,
                description = record.description.ifBlank { "Lent to ${record.personName.trim()}" },
                notes = record.notes,
                loanId = loanRef.id,
                counterpartyName = record.personName.trim(),
                occurredAtMillis = record.lentDateMillis
            )
            val loan = record.copy(
                id = loanRef.id,
                userId = currentUid,
                transactionId = txRef.id,
                returnedAmount = 0.0,
                status = LoanStatus.OPEN.name
            )

            db.runTransaction { transaction ->
                val accSnap = transaction.get(accountsCol(currentUid).document(record.accountId))
                val acc = accSnap.toObject(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Selected account does not exist.")
                val delta = FinancialEngine.computeAccountDeltas(tx, mapOf(acc.id to acc))[acc.id] ?: 0.0
                transaction.set(txRef, tx.toCreateMap())
                transaction.set(loanRef, loan.toCreateMap())
                transaction.update(
                    accountsCol(currentUid).document(acc.id),
                    mapOf("currentBalance" to (acc.currentBalance + delta), "updatedAt" to FieldValue.serverTimestamp())
                )
            }.await()

            Result.success(txRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$uid/lending_records")
            Result.failure(e)
        }
    }

    suspend fun updateLending(oldRecord: LendingRecord, updated: LendingRecord): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(oldRecord.id.isNotBlank())
            require(updated.personName.isNotBlank())
            require(updated.principalAmount >= oldRecord.returnedAmount) {
                "Principal cannot be less than already returned amount."
            }
            val newStatus = when {
                oldRecord.returnedAmount <= 0.0 -> LoanStatus.OPEN.name
                oldRecord.returnedAmount + 0.001 >= updated.principalAmount -> LoanStatus.SETTLED.name
                else -> LoanStatus.PARTIAL.name
            }

            db.runTransaction { transaction ->
                val loanRef = lendingCol(currentUid).document(oldRecord.id)
                val txRef = transactionsCol(currentUid).document(oldRecord.transactionId)
                val oldAccSnap = transaction.get(accountsCol(currentUid).document(oldRecord.accountId))
                val newAccSnap = if (updated.accountId != oldRecord.accountId) {
                    transaction.get(accountsCol(currentUid).document(updated.accountId))
                } else oldAccSnap
                val txSnap = transaction.get(txRef)

                val accountsMap = mutableMapOf<String, Account>()
                oldAccSnap.toObject(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.let {
                    accountsMap[it.id] = it
                }
                newAccSnap.toObject(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.let {
                    accountsMap[it.id] = it
                }

                val existingTx = txSnap.toObject(TransactionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existingTx != null) {
                    val newTx = existingTx.copy(
                        amount = updated.principalAmount,
                        accountId = updated.accountId,
                        accountName = updated.accountName,
                        description = updated.description.ifBlank { "Lent to ${updated.personName.trim()}" },
                        notes = updated.notes,
                        counterpartyName = updated.personName.trim(),
                        occurredAtMillis = updated.lentDateMillis
                    )
                    val deltas = FinancialEngine.computeEditDeltas(existingTx, newTx, accountsMap)
                    transaction.update(txRef, newTx.toUpdateMap())
                    deltas.forEach { (accId, delta) ->
                        val acc = accountsMap[accId]
                        if (acc != null) {
                            transaction.update(
                                accountsCol(currentUid).document(accId),
                                mapOf("currentBalance" to (acc.currentBalance + delta), "updatedAt" to FieldValue.serverTimestamp())
                            )
                        }
                    }
                }

                transaction.update(
                    loanRef,
                    mapOf(
                        "personName" to updated.personName.trim().take(80),
                        "principalAmount" to updated.principalAmount,
                        "accountId" to updated.accountId,
                        "accountName" to updated.accountName.trim().take(60),
                        "lentDateMillis" to updated.lentDateMillis,
                        "expectedReturnDateMillis" to (updated.expectedReturnDateMillis ?: FieldValue.delete()),
                        "description" to updated.description.trim().take(120),
                        "notes" to (updated.notes?.trim()?.take(500)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
                        "status" to newStatus,
                        "reminderEnabled" to updated.reminderEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/lending_records/${oldRecord.id}")
            Result.failure(e)
        }
    }

    suspend fun deleteLending(record: LendingRecord): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            val linkedRepayments = repaymentsCol(currentUid).whereEqualTo("loanId", record.id).get().await()
                .toObjects(RepaymentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val txIds = (listOf(record.transactionId) + linkedRepayments.map { it.transactionId }).filter { it.isNotBlank() }.distinct()
            val allAccounts = accountsCol(currentUid).get().await()
                .toObjects(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val accountsMap = allAccounts.associateBy { it.id }

            val txsToReverse = mutableListOf<TransactionRecord>()
            for (txId in txIds) {
                transactionsCol(currentUid).document(txId).get().await()
                    .toObject(TransactionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.let { txsToReverse.add(it) }
            }

            val totalDeltas = mutableMapOf<String, Double>()
            txsToReverse.forEach { tx ->
                FinancialEngine.computeAccountDeltas(tx, accountsMap).forEach { (accId, delta) ->
                    totalDeltas[accId] = (totalDeltas[accId] ?: 0.0) - delta
                }
            }

            val batch = db.batch()
            totalDeltas.forEach { (accId, delta) ->
                val acc = accountsMap[accId]
                if (acc != null) {
                    batch.update(
                        accountsCol(currentUid).document(accId),
                        mapOf("currentBalance" to (acc.currentBalance + delta), "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
            }
            txsToReverse.forEach { batch.delete(transactionsCol(currentUid).document(it.id)) }
            linkedRepayments.forEach { batch.delete(repaymentsCol(currentUid).document(it.id)) }
            batch.delete(lendingCol(currentUid).document(record.id))
            batch.commit().await()
            deleteImagesForTransactionIds(currentUid, txIds)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/lending_records/${record.id}")
            Result.failure(e)
        }
    }

    suspend fun addLendingRepayment(
        record: LendingRecord,
        amount: Double,
        account: Account,
        repaidAtMillis: Long,
        notes: String?
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(amount > 0.0) { "Repayment amount must be positive." }
            require(amount <= record.outstandingAmount + 0.01) { "Repayment exceeds outstanding amount." }

            val repRef = repaymentsCol(currentUid).document()
            val txRef = transactionsCol(currentUid).document()

            db.runTransaction { transaction ->
                val loanSnap = transaction.get(lendingCol(currentUid).document(record.id))
                val latestLoan = loanSnap.toObject(LendingRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Lending record not found.")
                val accSnap = transaction.get(accountsCol(currentUid).document(account.id))
                val latestAcc = accSnap.toObject(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Receiving account not found.")

                val effectiveAmount = amount.coerceAtMost(latestLoan.outstandingAmount)
                val newReturned = (latestLoan.returnedAmount + effectiveAmount).coerceAtMost(latestLoan.principalAmount)
                val newStatus = if (newReturned + 0.001 >= latestLoan.principalAmount) LoanStatus.SETTLED.name else LoanStatus.PARTIAL.name

                val tx = TransactionRecord(
                    id = txRef.id,
                    userId = currentUid,
                    type = TransactionType.LENT_REPAYMENT.name,
                    amount = effectiveAmount,
                    chargeAmount = 0.0,
                    accountId = latestAcc.id,
                    accountName = latestAcc.name,
                    description = "Repayment from ${latestLoan.personName}",
                    notes = notes,
                    loanId = latestLoan.id,
                    repaymentId = repRef.id,
                    counterpartyName = latestLoan.personName,
                    occurredAtMillis = repaidAtMillis
                )
                val repayment = RepaymentRecord(
                    id = repRef.id,
                    userId = currentUid,
                    recordType = RepaymentRecordType.LENDING.name,
                    loanId = latestLoan.id,
                    personName = latestLoan.personName,
                    amount = effectiveAmount,
                    accountId = latestAcc.id,
                    accountName = latestAcc.name,
                    transactionId = txRef.id,
                    repaidAtMillis = repaidAtMillis,
                    notes = notes
                )
                val delta = FinancialEngine.computeAccountDeltas(tx, mapOf(latestAcc.id to latestAcc))[latestAcc.id] ?: 0.0

                transaction.set(txRef, tx.toCreateMap())
                transaction.set(repRef, repayment.toCreateMap())
                transaction.update(
                    lendingCol(currentUid).document(latestLoan.id),
                    mapOf(
                        "returnedAmount" to newReturned,
                        "status" to newStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.update(
                    accountsCol(currentUid).document(latestAcc.id),
                    mapOf("currentBalance" to (latestAcc.currentBalance + delta), "updatedAt" to FieldValue.serverTimestamp())
                )
            }.await()

            Result.success(txRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/repayments")
            Result.failure(e)
        }
    }

    suspend fun createBorrowing(record: BorrowingRecord): Result<String> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(record.personName.isNotBlank()) { "Person name is required." }
            require(record.principalAmount > 0.0) { "Amount must be greater than zero." }
            require(record.accountId.isNotBlank()) { "Receiving account is required." }

            val loanRef = borrowingCol(currentUid).document()
            val txRef = transactionsCol(currentUid).document()
            val tx = TransactionRecord(
                id = txRef.id,
                userId = currentUid,
                type = TransactionType.BORROWING.name,
                amount = record.principalAmount,
                chargeAmount = 0.0,
                accountId = record.accountId,
                accountName = record.accountName,
                description = record.description.ifBlank { "Borrowed from ${record.personName.trim()}" },
                notes = record.notes,
                loanId = loanRef.id,
                counterpartyName = record.personName.trim(),
                occurredAtMillis = record.borrowedDateMillis
            )
            val loan = record.copy(
                id = loanRef.id,
                userId = currentUid,
                transactionId = txRef.id,
                repaidAmount = 0.0,
                status = LoanStatus.OPEN.name
            )

            db.runTransaction { transaction ->
                val accSnap = transaction.get(accountsCol(currentUid).document(record.accountId))
                val acc = accSnap.toObject(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Selected account does not exist.")
                val delta = FinancialEngine.computeAccountDeltas(tx, mapOf(acc.id to acc))[acc.id] ?: 0.0
                transaction.set(txRef, tx.toCreateMap())
                transaction.set(loanRef, loan.toCreateMap())
                transaction.update(
                    accountsCol(currentUid).document(acc.id),
                    mapOf("currentBalance" to (acc.currentBalance + delta), "updatedAt" to FieldValue.serverTimestamp())
                )
            }.await()

            Result.success(txRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$uid/borrowing_records")
            Result.failure(e)
        }
    }

    suspend fun updateBorrowing(oldRecord: BorrowingRecord, updated: BorrowingRecord): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(oldRecord.id.isNotBlank())
            require(updated.personName.isNotBlank())
            require(updated.principalAmount >= oldRecord.repaidAmount) {
                "Principal cannot be less than already repaid amount."
            }
            val newStatus = when {
                oldRecord.repaidAmount <= 0.0 -> LoanStatus.OPEN.name
                oldRecord.repaidAmount + 0.001 >= updated.principalAmount -> LoanStatus.SETTLED.name
                else -> LoanStatus.PARTIAL.name
            }

            db.runTransaction { transaction ->
                val loanRef = borrowingCol(currentUid).document(oldRecord.id)
                val txRef = transactionsCol(currentUid).document(oldRecord.transactionId)
                val oldAccSnap = transaction.get(accountsCol(currentUid).document(oldRecord.accountId))
                val newAccSnap = if (updated.accountId != oldRecord.accountId) {
                    transaction.get(accountsCol(currentUid).document(updated.accountId))
                } else oldAccSnap
                val txSnap = transaction.get(txRef)

                val accountsMap = mutableMapOf<String, Account>()
                oldAccSnap.toObject(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.let {
                    accountsMap[it.id] = it
                }
                newAccSnap.toObject(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.let {
                    accountsMap[it.id] = it
                }

                val existingTx = txSnap.toObject(TransactionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existingTx != null) {
                    val newTx = existingTx.copy(
                        amount = updated.principalAmount,
                        accountId = updated.accountId,
                        accountName = updated.accountName,
                        description = updated.description.ifBlank { "Borrowed from ${updated.personName.trim()}" },
                        notes = updated.notes,
                        counterpartyName = updated.personName.trim(),
                        occurredAtMillis = updated.borrowedDateMillis
                    )
                    val deltas = FinancialEngine.computeEditDeltas(existingTx, newTx, accountsMap)
                    transaction.update(txRef, newTx.toUpdateMap())
                    deltas.forEach { (accId, delta) ->
                        val acc = accountsMap[accId]
                        if (acc != null) {
                            transaction.update(
                                accountsCol(currentUid).document(accId),
                                mapOf("currentBalance" to (acc.currentBalance + delta), "updatedAt" to FieldValue.serverTimestamp())
                            )
                        }
                    }
                }

                transaction.update(
                    loanRef,
                    mapOf(
                        "personName" to updated.personName.trim().take(80),
                        "principalAmount" to updated.principalAmount,
                        "accountId" to updated.accountId,
                        "accountName" to updated.accountName.trim().take(60),
                        "borrowedDateMillis" to updated.borrowedDateMillis,
                        "expectedRepaymentDateMillis" to (updated.expectedRepaymentDateMillis ?: FieldValue.delete()),
                        "description" to updated.description.trim().take(120),
                        "notes" to (updated.notes?.trim()?.take(500)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
                        "status" to newStatus,
                        "reminderEnabled" to updated.reminderEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/borrowing_records/${oldRecord.id}")
            Result.failure(e)
        }
    }

    suspend fun deleteBorrowing(record: BorrowingRecord): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            val linkedRepayments = repaymentsCol(currentUid).whereEqualTo("loanId", record.id).get().await()
                .toObjects(RepaymentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val txIds = (listOf(record.transactionId) + linkedRepayments.map { it.transactionId }).filter { it.isNotBlank() }.distinct()
            val allAccounts = accountsCol(currentUid).get().await()
                .toObjects(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val accountsMap = allAccounts.associateBy { it.id }

            val txsToReverse = mutableListOf<TransactionRecord>()
            for (txId in txIds) {
                transactionsCol(currentUid).document(txId).get().await()
                    .toObject(TransactionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.let { txsToReverse.add(it) }
            }

            val totalDeltas = mutableMapOf<String, Double>()
            txsToReverse.forEach { tx ->
                FinancialEngine.computeAccountDeltas(tx, accountsMap).forEach { (accId, delta) ->
                    totalDeltas[accId] = (totalDeltas[accId] ?: 0.0) - delta
                }
            }

            val batch = db.batch()
            totalDeltas.forEach { (accId, delta) ->
                val acc = accountsMap[accId]
                if (acc != null) {
                    batch.update(
                        accountsCol(currentUid).document(accId),
                        mapOf("currentBalance" to (acc.currentBalance + delta), "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
            }
            txsToReverse.forEach { batch.delete(transactionsCol(currentUid).document(it.id)) }
            linkedRepayments.forEach { batch.delete(repaymentsCol(currentUid).document(it.id)) }
            batch.delete(borrowingCol(currentUid).document(record.id))
            batch.commit().await()
            deleteImagesForTransactionIds(currentUid, txIds)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/borrowing_records/${record.id}")
            Result.failure(e)
        }
    }

    suspend fun addBorrowingRepayment(
        record: BorrowingRecord,
        amount: Double,
        account: Account,
        repaidAtMillis: Long,
        notes: String?
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(amount > 0.0) { "Repayment amount must be positive." }
            require(amount <= record.outstandingDebt + 0.01) { "Repayment exceeds outstanding debt." }

            val repRef = repaymentsCol(currentUid).document()
            val txRef = transactionsCol(currentUid).document()

            db.runTransaction { transaction ->
                val loanSnap = transaction.get(borrowingCol(currentUid).document(record.id))
                val latestLoan = loanSnap.toObject(BorrowingRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Borrowing record not found.")
                val accSnap = transaction.get(accountsCol(currentUid).document(account.id))
                val latestAcc = accSnap.toObject(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Payment account not found.")

                val effectiveAmount = amount.coerceAtMost(latestLoan.outstandingDebt)
                val newRepaid = (latestLoan.repaidAmount + effectiveAmount).coerceAtMost(latestLoan.principalAmount)
                val newStatus = if (newRepaid + 0.001 >= latestLoan.principalAmount) LoanStatus.SETTLED.name else LoanStatus.PARTIAL.name

                val tx = TransactionRecord(
                    id = txRef.id,
                    userId = currentUid,
                    type = TransactionType.BORROWED_REPAYMENT.name,
                    amount = effectiveAmount,
                    chargeAmount = 0.0,
                    accountId = latestAcc.id,
                    accountName = latestAcc.name,
                    description = "Repaid to ${latestLoan.personName}",
                    notes = notes,
                    loanId = latestLoan.id,
                    repaymentId = repRef.id,
                    counterpartyName = latestLoan.personName,
                    occurredAtMillis = repaidAtMillis
                )
                val repayment = RepaymentRecord(
                    id = repRef.id,
                    userId = currentUid,
                    recordType = RepaymentRecordType.BORROWING.name,
                    loanId = latestLoan.id,
                    personName = latestLoan.personName,
                    amount = effectiveAmount,
                    accountId = latestAcc.id,
                    accountName = latestAcc.name,
                    transactionId = txRef.id,
                    repaidAtMillis = repaidAtMillis,
                    notes = notes
                )
                val delta = FinancialEngine.computeAccountDeltas(tx, mapOf(latestAcc.id to latestAcc))[latestAcc.id] ?: 0.0

                transaction.set(txRef, tx.toCreateMap())
                transaction.set(repRef, repayment.toCreateMap())
                transaction.update(
                    borrowingCol(currentUid).document(latestLoan.id),
                    mapOf(
                        "repaidAmount" to newRepaid,
                        "status" to newStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.update(
                    accountsCol(currentUid).document(latestAcc.id),
                    mapOf("currentBalance" to (latestAcc.currentBalance + delta), "updatedAt" to FieldValue.serverTimestamp())
                )
            }.await()

            Result.success(txRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/repayments")
            Result.failure(e)
        }
    }

    suspend fun deleteRepayment(repayment: RepaymentRecord): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            val txSnap = transactionsCol(currentUid).document(repayment.transactionId).get().await()
            val tx = txSnap.toObject(TransactionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (tx != null) {
                return deleteTransaction(tx)
            }
            repaymentsCol(currentUid).document(repayment.id).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/repayments/${repayment.id}")
            Result.failure(e)
        }
    }

    suspend fun saveBudget(budget: Budget): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(budget.name.isNotBlank()) { "Budget name is required." }
            require(budget.limitAmount > 0.0) { "Budget limit must be greater than zero." }
            if (budget.id.isBlank()) {
                budgetsCol(currentUid).document().set(budget.copy(userId = currentUid).toCreateMap()).await()
            } else {
                budgetsCol(currentUid).document(budget.id).update(
                    mapOf(
                        "name" to budget.name.trim().take(60),
                        "periodType" to budget.periodTypeEnum().name,
                        "categoryId" to (budget.categoryId?.takeIf { it.isNotBlank() } ?: FieldValue.delete()),
                        "categoryName" to (budget.categoryName?.trim()?.take(50)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
                        "limitAmount" to budget.limitAmount,
                        "startDateMillis" to budget.startDateMillis,
                        "endDateMillis" to budget.endDateMillis.coerceAtLeast(budget.startDateMillis),
                        "alertThresholds" to budget.alertThresholds.take(6),
                        "lastNotifiedThreshold" to budget.lastNotifiedThreshold.coerceIn(0, 100),
                        "active" to budget.active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/budgets")
            Result.failure(e)
        }
    }

    suspend fun deleteBudget(budgetId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            budgetsCol(currentUid).document(budgetId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/budgets/$budgetId")
            Result.failure(e)
        }
    }

    suspend fun createNotificationAndUpdateBudgetThreshold(
        budgetId: String?,
        newThreshold: Int?,
        notification: AppNotification
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            val batch = db.batch()
            batch.set(
                notificationsCol(currentUid).document(),
                notification.copy(userId = currentUid).toCreateMap()
            )
            if (!budgetId.isNullOrBlank() && newThreshold != null) {
                batch.update(
                    budgetsCol(currentUid).document(budgetId),
                    mapOf("lastNotifiedThreshold" to newThreshold, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/notifications")
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            notificationsCol(currentUid).document(notificationId).update(
                mapOf("read" to true, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/notifications/$notificationId")
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(notifications: List<AppNotification>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            val unread = notifications.filter { !it.read && it.id.isNotBlank() }
            if (unread.isEmpty()) return Result.success(Unit)
            val batch = db.batch()
            unread.forEach { n ->
                batch.update(
                    notificationsCol(currentUid).document(n.id),
                    mapOf("read" to true, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/notifications")
            Result.failure(e)
        }
    }

    suspend fun restoreWorkspaceBackup(
        accounts: List<Account>,
        categories: List<Category>,
        transactions: List<TransactionRecord>,
        budgets: List<Budget>,
        lendingRecords: List<LendingRecord>,
        borrowingRecords: List<BorrowingRecord>,
        repayments: List<RepaymentRecord>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            val ops = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

            accounts.forEach { acc ->
                val ref = if (acc.id.isNotBlank()) accountsCol(currentUid).document(acc.id) else accountsCol(currentUid).document()
                ops.add(ref to acc.copy(userId = currentUid).toCreateMap())
            }
            categories.forEach { cat ->
                val ref = if (cat.id.isNotBlank()) categoriesCol(currentUid).document(cat.id) else categoriesCol(currentUid).document()
                ops.add(ref to cat.copy(userId = currentUid).toCreateMap())
            }
            transactions.forEach { tx ->
                val ref = if (tx.id.isNotBlank()) transactionsCol(currentUid).document(tx.id) else transactionsCol(currentUid).document()
                ops.add(ref to tx.copy(userId = currentUid).toCreateMap())
            }
            budgets.forEach { bud ->
                val ref = if (bud.id.isNotBlank()) budgetsCol(currentUid).document(bud.id) else budgetsCol(currentUid).document()
                ops.add(ref to bud.copy(userId = currentUid).toCreateMap())
            }
            lendingRecords.forEach { lend ->
                val ref = if (lend.id.isNotBlank()) lendingCol(currentUid).document(lend.id) else lendingCol(currentUid).document()
                ops.add(ref to lend.copy(userId = currentUid).toCreateMap())
            }
            borrowingRecords.forEach { bor ->
                val ref = if (bor.id.isNotBlank()) borrowingCol(currentUid).document(bor.id) else borrowingCol(currentUid).document()
                ops.add(ref to bor.copy(userId = currentUid).toCreateMap())
            }
            repayments.forEach { rep ->
                val ref = if (rep.id.isNotBlank()) repaymentsCol(currentUid).document(rep.id) else repaymentsCol(currentUid).document()
                ops.add(ref to rep.copy(userId = currentUid).toCreateMap())
            }

            // Firestore batch limit is 500 writes per commit
            ops.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { (ref, payload) -> batch.set(ref, payload) }
                batch.commit().await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun addTransactionImage(
        transactionId: String,
        prepared: PreparedReceiptImage
    ): Result<TransactionImage> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(transactionId.isNotBlank()) { "Transaction ID is required for attachment." }
            val docRef = transactionImagesCol(currentUid).document()
            val storageRefPath = "users/$currentUid/transactions/$transactionId/${docRef.id}_${prepared.fileName}"
            val downloadUrl = ReceiptImageManager.uploadToFirebaseStorage(
                storageRefPath = storageRefPath,
                localFile = prepared.localFile,
                mimeType = prepared.fileType
            )
            val imageRecord = TransactionImage(
                id = docRef.id,
                userId = currentUid,
                transactionId = transactionId,
                fileName = prepared.fileName,
                fileType = prepared.fileType,
                storageRefPath = storageRefPath,
                downloadUrl = downloadUrl,
                localFilePath = prepared.localFile.absolutePath,
                fileSizeBytes = prepared.fileSizeBytes,
                uploadedAtMillis = System.currentTimeMillis()
            )
            docRef.set(imageRecord.toCreateMap()).await()
            Result.success(imageRecord)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$uid/transaction_images")
            Result.failure(e)
        }
    }

    suspend fun replaceTransactionImage(
        existing: TransactionImage,
        prepared: PreparedReceiptImage
    ): Result<TransactionImage> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(existing.id.isNotBlank()) { "Image ID is required." }
            if (existing.storageRefPath.isNotBlank()) {
                ReceiptImageManager.deleteFromFirebaseStorage(existing.storageRefPath)
            }
            existing.localFilePath?.let { path ->
                runCatching { java.io.File(path).takeIf { it.exists() }?.delete() }
            }
            val newStorageRefPath = "users/$currentUid/transactions/${existing.transactionId}/${existing.id}_${prepared.fileName}"
            val downloadUrl = ReceiptImageManager.uploadToFirebaseStorage(
                storageRefPath = newStorageRefPath,
                localFile = prepared.localFile,
                mimeType = prepared.fileType
            )
            val updated = existing.copy(
                fileName = prepared.fileName,
                fileType = prepared.fileType,
                storageRefPath = newStorageRefPath,
                downloadUrl = downloadUrl,
                localFilePath = prepared.localFile.absolutePath,
                fileSizeBytes = prepared.fileSizeBytes,
                uploadedAtMillis = System.currentTimeMillis()
            )
            transactionImagesCol(currentUid).document(existing.id).update(updated.toUpdateMap()).await()
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/transaction_images/${existing.id}")
            Result.failure(e)
        }
    }

    suspend fun deleteTransactionImage(image: TransactionImage): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return try {
            val currentUid = requireUserId()
            require(image.id.isNotBlank())
            if (image.storageRefPath.isNotBlank()) {
                ReceiptImageManager.deleteFromFirebaseStorage(image.storageRefPath)
            }
            image.localFilePath?.let { path ->
                runCatching { java.io.File(path).takeIf { it.exists() }?.delete() }
            }
            transactionImagesCol(currentUid).document(image.id).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/transaction_images/${image.id}")
            Result.failure(e)
        }
    }

    private suspend fun deleteImagesForTransactionIds(uid: String, txIds: List<String>) {
        if (txIds.isEmpty()) return
        try {
            val toDelete = mutableListOf<TransactionImage>()
            for (chunk in txIds.distinct().chunked(10)) {
                val snap = transactionImagesCol(uid).whereIn("transactionId", chunk).get().await()
                toDelete.addAll(
                    snap.toObjects(TransactionImage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                )
            }
            if (toDelete.isEmpty()) return
            val batch = db.batch()
            toDelete.forEach { img ->
                if (img.id.isNotBlank()) {
                    batch.delete(transactionImagesCol(uid).document(img.id))
                }
            }
            batch.commit().await()
            toDelete.forEach { img ->
                if (img.storageRefPath.isNotBlank()) {
                    ReceiptImageManager.deleteFromFirebaseStorage(img.storageRefPath)
                }
                img.localFilePath?.let { path ->
                    runCatching { java.io.File(path).takeIf { it.exists() }?.delete() }
                }
            }
        } catch (_: Exception) {
            // Best-effort cleanup; primary financial deletion already committed
        }
    }

    private fun validateTransactionInput(tx: TransactionRecord) {
        require(tx.amount > 0.0) { "Amount must be greater than zero." }
        require(tx.chargeAmount >= 0.0) { "Charge cannot be negative." }
        require(tx.accountId.isNotBlank()) { "Please select an account." }
        val type = tx.typeEnum()
        if (type == TransactionType.INCOME && tx.chargeAmount >= tx.amount) {
            throw IllegalArgumentException("Additional fee cannot exceed or equal the income amount.")
        }
        if (type == TransactionType.TRANSFER || type == TransactionType.CREDIT_CARD_PAYMENT) {
            require(!tx.toAccountId.isNullOrBlank()) { "Please select a destination account." }
            require(tx.toAccountId != tx.accountId) { "Source and destination accounts cannot be the same." }
        }
    }
}
