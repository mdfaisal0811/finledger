const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

const pastDate = () => new Date(Date.now() - 5000);

test("Unauthenticated access is denied across all user collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("accounts").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("budgets").get());
});

test("UserSettings: owner create, partial update, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      currencyCode: "BDT",
      currencySymbol: "৳",
      themeMode: "SYSTEM",
      dateFormat: "dd MMM yyyy",
      language: "English",
      notificationsEnabled: true,
      budgetAlertThresholds: [50, 75, 90, 100],
      dueDateReminderDays: 3,
      appLockEnabled: false,
      hideBalances: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      currencyCode: "USD",
      currencySymbol: "$",
      themeMode: "DARK",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user access denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      currencyCode: "EUR",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Accounts: owner create, balance update, @DocumentId id rejection, cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("accounts").doc("acc_bank").set({
      userId: ALICE_UID,
      name: "City Bank",
      type: "BANK",
      openingBalance: 50000.0,
      currentBalance: 50000.0,
      creditLimit: 0.0,
      billingCycleDay: 1,
      paymentDueDay: 15,
      minimumPayment: 0.0,
      iconKey: "bank",
      colorHex: 0xFF2563EB,
      archived: false,
      defaultAccount: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Balance atomic update pattern
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("accounts").doc("acc_bank").update({
      currentBalance: 39980.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject extra `id` field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("accounts").doc("acc_bad").set({
      id: "acc_bad",
      userId: ALICE_UID,
      name: "Bad",
      type: "CASH",
      openingBalance: 0,
      currentBalance: 0,
      creditLimit: 0,
      billingCycleDay: 1,
      paymentDueDay: 15,
      minimumPayment: 0,
      iconKey: "cash",
      colorHex: 0xFF16A34A,
      archived: false,
      defaultAccount: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // List subcollection works for owner, fails for Bob
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("accounts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("accounts").get());
});

test("Transactions: linked transfer + charge succeeds; same-account transfer fails; immutable createdAt protected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_transfer_1").set({
      userId: ALICE_UID,
      type: "TRANSFER",
      amount: 10000.0,
      chargeAmount: 20.0,
      accountId: "acc_bank",
      accountName: "Bank Account",
      toAccountId: "acc_cash",
      toAccountName: "Cash",
      description: "ATM withdrawal",
      occurredAtMillis: Date.now() - 2000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Edit transfer in place
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_transfer_1").update({
      amount: 12000.0,
      chargeAmount: 25.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_transfer_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject transfer to same account
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_transfer_same").set({
      userId: ALICE_UID,
      type: "TRANSFER",
      amount: 500.0,
      chargeAmount: 0.0,
      accountId: "acc_bank",
      accountName: "Bank Account",
      toAccountId: "acc_bank",
      toAccountName: "Bank Account",
      description: "Self transfer",
      occurredAtMillis: Date.now() - 2000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Categories, Budgets, Lending, Borrowing, Repayments, and Notifications rules succeed for owner and deny others", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("categories").doc("cat_food").set({
      userId: ALICE_UID,
      name: "Food & Dining",
      type: "EXPENSE",
      iconKey: "restaurant",
      colorHex: 0xFFEF4444,
      systemDefault: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("budgets").doc("bud_monthly").set({
      userId: ALICE_UID,
      name: "Monthly Budget",
      periodType: "MONTHLY",
      limitAmount: 30000.0,
      startDateMillis: Date.now() - 86400000,
      endDateMillis: Date.now() + 86400000 * 29,
      alertThresholds: [50, 75, 90, 100],
      lastNotifiedThreshold: 0,
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("lending_records").doc("lend_1").set({
      userId: ALICE_UID,
      personName: "Rahim",
      principalAmount: 5000.0,
      returnedAmount: 0.0,
      accountId: "acc_cash",
      accountName: "Cash",
      transactionId: "tx_lend_1",
      lentDateMillis: Date.now() - 10000,
      description: "Short term loan",
      status: "OPEN",
      reminderEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("borrowing_records").doc("borrow_1").set({
      userId: ALICE_UID,
      personName: "Karim",
      principalAmount: 2000.0,
      repaidAmount: 0.0,
      accountId: "acc_bank",
      accountName: "City Bank",
      transactionId: "tx_borrow_1",
      borrowedDateMillis: Date.now() - 10000,
      description: "Urgent cash",
      status: "OPEN",
      reminderEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("repayments").doc("rep_1").set({
      userId: ALICE_UID,
      recordType: "LENDING",
      loanId: "lend_1",
      personName: "Rahim",
      amount: 2000.0,
      accountId: "acc_cash",
      accountName: "Cash",
      transactionId: "tx_rep_1",
      repaidAtMillis: Date.now() - 5000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").set({
      userId: ALICE_UID,
      title: "Budget 75% reached",
      message: "Food & Dining reached 75% of budget.",
      kind: "BUDGET_THRESHOLD",
      relatedId: "bud_monthly",
      read: false,
      triggeredAtMillis: Date.now() - 3000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cross-user denial
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("lending_records").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("repayments").get());
});

test("TransactionImages: owner create/replace/delete works, invalid type fails, cross-user denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transaction_images").doc("img_1").set({
      userId: ALICE_UID,
      transactionId: "tx_1",
      fileName: "receipt_01.jpg",
      fileType: "image/jpeg",
      storageRefPath: `users/${ALICE_UID}/transactions/tx_1/img_1_receipt_01.jpg`,
      downloadUrl: "https://example.com/receipt_01.jpg",
      localFilePath: "/data/user/0/com.aistudio.finledger.kvnxqp/files/receipts/receipt_01.jpg",
      fileSizeBytes: 245120,
      uploadedAtMillis: Date.now() - 2000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Replace / update metadata
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transaction_images").doc("img_1").update({
      fileName: "receipt_01_replaced.webp",
      fileType: "image/webp",
      fileSizeBytes: 180000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject disallowed fileType (e.g. application/pdf or image/gif)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transaction_images").doc("img_bad").set({
      userId: ALICE_UID,
      transactionId: "tx_1",
      fileName: "script.pdf",
      fileType: "application/pdf",
      storageRefPath: `users/${ALICE_UID}/transactions/tx_1/script.pdf`,
      fileSizeBytes: 1024,
      uploadedAtMillis: Date.now() - 2000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject cross-user read & write
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transaction_images").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transaction_images").doc("img_1").delete());

  // Owner can delete
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("transaction_images").doc("img_1").delete());
});
