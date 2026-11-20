package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.firestore.FinanceRepository
import com.example.domain.model.Account
import com.example.domain.model.ThemeMode
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.components.FinCard
import com.example.ui.components.QuickActionSheet
import com.example.ui.components.QuickActionType
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.BudgetsScreen
import com.example.ui.screens.CategoriesManagerScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LendingBorrowingScreen
import com.example.ui.screens.LoanFormDialog
import com.example.ui.screens.MoreAndSettingsScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.StatementsScreen
import com.example.ui.screens.TransactionFormDialog
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.FinLedgerTheme
import com.example.ui.theme.LocalFinLedgerColors
import com.example.ui.viewmodel.FinanceViewModel
import com.example.ui.viewmodel.FinanceWorkspaceData
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    TRANSACTIONS("Transactions", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong, "nav_transactions"),
    ACCOUNTS("Accounts", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "nav_accounts"),
    REPORTS("Reports", Icons.Filled.BarChart, Icons.Outlined.BarChart, "nav_reports"),
    MORE("More", Icons.Filled.Menu, Icons.Outlined.Menu, "nav_more")
}

sealed interface SecondaryDestination {
    data class LendingBorrowing(val initialTab: Int = 0) : SecondaryDestination
    data object Budgets : SecondaryDestination
    data object Categories : SecondaryDestination
    data object Notifications : SecondaryDestination
    data object Statements : SecondaryDestination
}

@Composable
fun FinLedgerAppRoot(auth: FirebaseAuth = Firebase.auth) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        FinLedgerTheme(themeMode = ThemeMode.SYSTEM) {
            AuthScreen(onAuthSuccess = { currentUser = auth.currentUser })
        }
    } else {
        key(user.uid) {
            AuthenticatedFinLedgerShell(currentUserId = user.uid)
        }
    }
}

@Composable
private fun AuthenticatedFinLedgerShell(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: FinanceViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                FinanceViewModel(
                    repository = FinanceRepository(db),
                    currentUserId = currentUserId,
                    appContext = app.applicationContext
                )
            }
        }
    )

    val uiState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val filteredTransactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val filterState by viewModel.txFilter.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val storedPin by viewModel.storedPin.collectAsStateWithLifecycle()

    val themeMode = (uiState as? UiState.Success)?.data?.settings?.themeModeEnum() ?: ThemeMode.SYSTEM

    FinLedgerTheme(themeMode = themeMode) {
        if (isAppLocked) {
            PinLockScreen(
                onUnlock = { pin -> viewModel.unlockWithPin(pin) }
            )
            return@FinLedgerTheme
        }

        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(viewModel) {
            viewModel.snackbarEvents.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }

        var activeTab by remember { mutableStateOf(MainTab.HOME) }
        var secondaryDest by remember { mutableStateOf<SecondaryDestination?>(null) }
        var showQuickActionSheet by remember { mutableStateOf(false) }

        var txFormType by remember { mutableStateOf<TransactionType?>(null) }
        var editingTransaction by remember { mutableStateOf<TransactionRecord?>(null) }
        var preselectedCardForPayment by remember { mutableStateOf<Account?>(null) }
        var showQuickLoanDialog by remember { mutableStateOf<Boolean?>(null) } // true=Lending, false=Borrowing
        var accountDetailShortcut by remember { mutableStateOf<Account?>(null) }

        if (secondaryDest != null) {
            BackHandler { secondaryDest = null }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                floatingActionButton = {
                    if (uiState is UiState.Success && secondaryDest == null) {
                        FloatingActionButton(
                            onClick = { showQuickActionSheet = true },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("main_fab_add")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Quick Actions")
                        }
                    }
                },
                bottomBar = {
                    if (!useNavRail && secondaryDest == null) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            MainTab.entries.forEach { tab ->
                                val selected = activeTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { activeTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail && secondaryDest == null) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Spacer(Modifier.height(12.dp))
                            MainTab.entries.forEach { tab ->
                                val selected = activeTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { activeTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        if (isBusy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                        }

                        when (val state = uiState) {
                            is UiState.Loading -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        CircularProgressIndicator()
                                        Text("Loading your financial workspace...", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }

                            is UiState.Error -> {
                                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                    FinCard(modifier = Modifier.widthIn(max = 420.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = LocalFinLedgerColors.current.expense)
                                            Text("Unable to sync workspace", style = MaterialTheme.typography.titleMedium)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(state.message, style = MaterialTheme.typography.bodySmall)
                                        Spacer(Modifier.height(12.dp))
                                        Button(onClick = {
                                            signOut(credentialManager, scope)
                                        }) {
                                            Text("Sign Out & Retry")
                                        }
                                    }
                                }
                            }

                            is UiState.Success -> {
                                val data: FinanceWorkspaceData = state.data
                                when (val sub = secondaryDest) {
                                    is SecondaryDestination.LendingBorrowing -> {
                                        LendingBorrowingScreen(
                                            initialTab = sub.initialTab,
                                            lendingRecords = data.lendingRecords,
                                            borrowingRecords = data.borrowingRecords,
                                            repayments = data.repayments,
                                            accounts = data.accounts,
                                            transactionImages = data.transactionImages,
                                            settings = data.settings,
                                            onBack = { secondaryDest = null },
                                            onCreateLending = { rec, pending -> viewModel.createLending(rec, pending) },
                                            onUpdateLending = { old, upd, pending -> viewModel.updateLending(old, upd, pending) },
                                            onDeleteLending = { viewModel.deleteLending(it) },
                                            onAddLendingRepayment = { rec, amt, acc, millis, notes, pending ->
                                                viewModel.addLendingRepayment(rec, amt, acc, millis, notes, pending)
                                            },
                                            onCreateBorrowing = { rec, pending -> viewModel.createBorrowing(rec, pending) },
                                            onUpdateBorrowing = { old, upd, pending -> viewModel.updateBorrowing(old, upd, pending) },
                                            onDeleteBorrowing = { viewModel.deleteBorrowing(it) },
                                            onAddBorrowingRepayment = { rec, amt, acc, millis, notes, pending ->
                                                viewModel.addBorrowingRepayment(rec, amt, acc, millis, notes, pending)
                                            },
                                            onDeleteRepayment = { viewModel.deleteRepayment(it) },
                                            onAttachImagesToTx = { txId, pending ->
                                                viewModel.attachImagesToTransaction(txId, pending)
                                            },
                                            onReplaceImage = { existing, rep ->
                                                viewModel.replaceTransactionImage(existing, rep)
                                            },
                                            onDeleteImage = { img -> viewModel.deleteTransactionImage(img) }
                                        )
                                    }

                                    SecondaryDestination.Budgets -> {
                                        BudgetsScreen(
                                            budgetProgress = data.dashboardSummary.budgetProgress,
                                            categories = data.categories,
                                            settings = data.settings,
                                            onBack = { secondaryDest = null },
                                            onSaveBudget = { viewModel.saveBudget(it) },
                                            onDeleteBudget = { viewModel.deleteBudget(it) }
                                        )
                                    }

                                    SecondaryDestination.Categories -> {
                                        CategoriesManagerScreen(
                                            categories = data.categories,
                                            onBack = { secondaryDest = null },
                                            onSaveCategory = { viewModel.saveCategory(it) },
                                            onDeleteCategory = { viewModel.deleteCategory(it) }
                                        )
                                    }

                                    SecondaryDestination.Notifications -> {
                                        NotificationsScreen(
                                            notifications = data.notifications,
                                            dateFormat = data.settings.dateFormat,
                                            onBack = { secondaryDest = null },
                                            onMarkRead = { viewModel.markNotificationRead(it) },
                                            onMarkAllRead = { viewModel.markAllNotificationsRead() }
                                        )
                                    }

                                    SecondaryDestination.Statements -> {
                                        StatementsScreen(
                                            data = data,
                                            onBack = { secondaryDest = null },
                                            onDownloadPdf = { uri, stmt -> viewModel.exportStatementPdf(uri, stmt) },
                                            onExportCsv = { uri, stmt -> viewModel.exportStatementCsv(uri, stmt) },
                                            onShareStatement = { stmt -> viewModel.shareStatementPdf(context, stmt) }
                                        )
                                    }

                                    null -> {
                                        when (activeTab) {
                                            MainTab.HOME -> DashboardScreen(
                                                data = data,
                                                onSelectDateRange = { r, s, e -> viewModel.setDashboardDateRange(r, s, e) },
                                                onToggleHideBalances = { hide ->
                                                    viewModel.updateSettings(mapOf("hideBalances" to hide))
                                                },
                                                onOpenNotifications = { secondaryDest = SecondaryDestination.Notifications },
                                                onOpenAccounts = { activeTab = MainTab.ACCOUNTS },
                                                onOpenAccountDetail = { acc ->
                                                    accountDetailShortcut = acc
                                                    activeTab = MainTab.ACCOUNTS
                                                },
                                                onOpenTransactions = { activeTab = MainTab.TRANSACTIONS },
                                                onOpenTransactionDetail = { tx ->
                                                    val editable = tx.typeEnum() in listOf(
                                                        TransactionType.INCOME,
                                                        TransactionType.EXPENSE,
                                                        TransactionType.TRANSFER,
                                                        TransactionType.CREDIT_CARD_PAYMENT
                                                    )
                                                    if (editable) {
                                                        editingTransaction = tx
                                                        txFormType = tx.typeEnum()
                                                    } else {
                                                        activeTab = MainTab.TRANSACTIONS
                                                    }
                                                },
                                                onOpenBudgets = { secondaryDest = SecondaryDestination.Budgets },
                                                onOpenLendingBorrowing = { tabIdx ->
                                                    secondaryDest = SecondaryDestination.LendingBorrowing(tabIdx)
                                                },
                                                onQuickAddExpense = {
                                                    editingTransaction = null
                                                    txFormType = TransactionType.EXPENSE
                                                }
                                            )

                                            MainTab.TRANSACTIONS -> TransactionsScreen(
                                                transactions = filteredTransactions,
                                                filterState = filterState,
                                                accounts = data.accounts,
                                                categories = data.categories,
                                                transactionImages = data.transactionImages,
                                                settings = data.settings,
                                                onUpdateFilter = { viewModel.updateTransactionFilter(it) },
                                                onResetFilter = { viewModel.resetTransactionFilter() },
                                                onEditTransaction = { tx ->
                                                    editingTransaction = tx
                                                    txFormType = tx.typeEnum()
                                                },
                                                onDeleteTransaction = { tx -> viewModel.deleteTransaction(tx) },
                                                onAttachImages = { txId, pending ->
                                                    viewModel.attachImagesToTransaction(txId, pending)
                                                },
                                                onReplaceImage = { existing, rep ->
                                                    viewModel.replaceTransactionImage(existing, rep)
                                                },
                                                onDeleteImage = { img -> viewModel.deleteTransactionImage(img) }
                                            )

                                            MainTab.ACCOUNTS -> AccountsScreen(
                                                accounts = data.accounts,
                                                transactions = data.transactions,
                                                settings = data.settings,
                                                initialSelectedAccount = accountDetailShortcut,
                                                onClearInitialSelected = { accountDetailShortcut = null },
                                                onSaveAccount = { acc -> viewModel.addOrUpdateAccount(acc) },
                                                onArchiveAccount = { id, arch -> viewModel.archiveAccount(id, arch) },
                                                onDeleteAccount = { id, migrate -> viewModel.deleteAccount(id, migrate) },
                                                onPayCreditCard = { card ->
                                                    editingTransaction = null
                                                    preselectedCardForPayment = card
                                                    txFormType = TransactionType.CREDIT_CARD_PAYMENT
                                                },
                                                onEditTransaction = { tx ->
                                                    editingTransaction = tx
                                                    txFormType = tx.typeEnum()
                                                }
                                            )

                                            MainTab.REPORTS -> ReportsScreen(
                                                data = data,
                                                onSelectDateRange = { r, s, e -> viewModel.setDashboardDateRange(r, s, e) },
                                                onExportCsv = { uri -> viewModel.exportCsv(uri) },
                                                onExportPdf = { uri -> viewModel.exportPdf(uri) },
                                                onOpenStatements = { secondaryDest = SecondaryDestination.Statements }
                                            )

                                            MainTab.MORE -> MoreAndSettingsScreen(
                                                data = data,
                                                storedPin = storedPin,
                                                onOpenStatements = { secondaryDest = SecondaryDestination.Statements },
                                                onOpenLendingBorrowing = { secondaryDest = SecondaryDestination.LendingBorrowing(0) },
                                                onOpenBudgets = { secondaryDest = SecondaryDestination.Budgets },
                                                onOpenCategories = { secondaryDest = SecondaryDestination.Categories },
                                                onOpenNotifications = { secondaryDest = SecondaryDestination.Notifications },
                                                onUpdateSettings = { updates, msg -> viewModel.updateSettings(updates, msg) },
                                                onSetPinAndLock = { pin, enabled -> viewModel.setPinAndEnableLock(pin, enabled) },
                                                onExportCsv = { uri -> viewModel.exportCsv(uri) },
                                                onExportBackupJson = { uri -> viewModel.exportBackupJson(uri) },
                                                onRestoreBackupJson = { uri -> viewModel.restoreBackupJson(uri) },
                                                onSignOut = {
                                                    signOut(credentialManager, scope)
                                                }
                                            )
                                        }
                                    }
                                }

                                // Quick Action Sheet
                                if (showQuickActionSheet) {
                                    QuickActionSheet(
                                        onSelect = { qa ->
                                            showQuickActionSheet = false
                                            editingTransaction = null
                                            preselectedCardForPayment = null
                                            when (qa) {
                                                QuickActionType.ADD_EXPENSE -> txFormType = TransactionType.EXPENSE
                                                QuickActionType.ADD_INCOME -> txFormType = TransactionType.INCOME
                                                QuickActionType.TRANSFER -> txFormType = TransactionType.TRANSFER
                                                QuickActionType.MONEY_LENT -> showQuickLoanDialog = true
                                                QuickActionType.MONEY_BORROWED -> showQuickLoanDialog = false
                                            }
                                        },
                                        onDismiss = { showQuickActionSheet = false }
                                    )
                                }

                                // Add/Edit Transaction or Transfer Dialog
                                txFormType?.let { type ->
                                    val savedTxImages = remember(editingTransaction, data.transactionImages) {
                                        editingTransaction?.let { tx ->
                                            data.transactionImages.filter { it.transactionId == tx.id }
                                        }.orEmpty()
                                    }
                                    TransactionFormDialog(
                                        initialType = type,
                                        editingTransaction = editingTransaction,
                                        preselectedToCard = preselectedCardForPayment,
                                        accounts = data.accounts,
                                        categories = data.categories,
                                        savedImages = savedTxImages,
                                        currencySymbol = data.settings.currencySymbol,
                                        dateFormat = data.settings.dateFormat,
                                        defaultAccountId = data.settings.defaultAccountId,
                                        onCreateCategory = { viewModel.saveCategory(it) },
                                        onDeleteSavedImage = { img -> viewModel.deleteTransactionImage(img) },
                                        onReplaceSavedImage = { existing, rep ->
                                            viewModel.replaceTransactionImage(existing, rep)
                                        },
                                        onSave = { newTx, oldTx, pendingImages ->
                                            if (oldTx == null) {
                                                viewModel.addTransaction(newTx, pendingImages) {
                                                    txFormType = null
                                                    preselectedCardForPayment = null
                                                }
                                            } else {
                                                viewModel.updateTransaction(oldTx, newTx, pendingImages) {
                                                    txFormType = null
                                                    editingTransaction = null
                                                }
                                            }
                                        },
                                        onDismiss = {
                                            txFormType = null
                                            editingTransaction = null
                                            preselectedCardForPayment = null
                                        }
                                    )
                                }

                                // Quick Money Lent / Borrowed Dialog
                                showQuickLoanDialog?.let { isLending ->
                                    LoanFormDialog(
                                        isLending = isLending,
                                        editingLending = null,
                                        editingBorrowing = null,
                                        accounts = data.accounts.filter { !it.archived },
                                        settings = data.settings,
                                        onSaveLending = { rec, pendingImages ->
                                            viewModel.createLending(rec, pendingImages) { showQuickLoanDialog = null }
                                        },
                                        onSaveBorrowing = { rec, pendingImages ->
                                            viewModel.createBorrowing(rec, pendingImages) { showQuickLoanDialog = null }
                                        },
                                        onDismiss = { showQuickLoanDialog = null }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinLockScreen(
    onUnlock: (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp).navigationBarsPadding(), contentAlignment = Alignment.Center) {
            FinCard(modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Lock, contentDescription = "Locked", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Text("FinLedger Locked", style = MaterialTheme.typography.titleLarge)
                    Text("Enter your PIN to access your workspace.", style = MaterialTheme.typography.bodySmall, color = LocalFinLedgerColors.current.textSecondary)
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { pin = it; error = false } },
                        label = { Text("PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error) {
                        Text("Incorrect PIN. Try again.", style = MaterialTheme.typography.bodySmall, color = LocalFinLedgerColors.current.expense)
                    }
                    Button(
                        onClick = { if (!onUnlock(pin)) error = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Unlock") }
                }
            }
        }
    }
}
