package com.mytrackr.receipts.viewmodels;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.mytrackr.receipts.data.model.Budget;
import com.mytrackr.receipts.data.model.Transaction;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.data.repository.BudgetRepository;
import com.mytrackr.receipts.data.repository.ReceiptRepository;
import com.mytrackr.receipts.data.repository.TransactionRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class BudgetViewModel extends ViewModel {
    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final ReceiptRepository receiptRepository;
    private final MutableLiveData<Budget> budgetLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saveSuccessLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<Transaction>> transactionsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> receiptCountLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> manualTransactionCountLiveData = new MutableLiveData<>();
    private final MutableLiveData<Double> averageExpenseLiveData = new MutableLiveData<>();

    private boolean isSyncing = false;
    private android.os.Handler syncHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSyncRunnable = null;

    public BudgetViewModel() {
        budgetRepository = BudgetRepository.getInstance();
        transactionRepository = TransactionRepository.getInstance();
        receiptRepository = new ReceiptRepository();
        FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: BudgetViewModel initialized");
    }

    public MutableLiveData<Budget> getBudgetLiveData() {
        return budgetLiveData;
    }

    public MutableLiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public MutableLiveData<Boolean> getSaveSuccessLiveData() {
        return saveSuccessLiveData;
    }

    public MutableLiveData<List<Transaction>> getTransactionsLiveData() {
        return transactionsLiveData;
    }

    public MutableLiveData<Integer> getReceiptCountLiveData() {
        return receiptCountLiveData;
    }

    public MutableLiveData<Integer> getManualTransactionCountLiveData() {
        return manualTransactionCountLiveData;
    }

    public MutableLiveData<Double> getAverageExpenseLiveData() {
        return averageExpenseLiveData;
    }

    public void loadCurrentMonthBudget() {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.getTime());
        String year = String.valueOf(calendar.get(Calendar.YEAR));

        FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Loading budget for " + month + " " + year);
        budgetRepository.getBudget(month, year, budgetLiveData, errorMessage);

        debounceSync(month, year, 500);
    }

    public void refreshBudget() {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.getTime());
        String year = String.valueOf(calendar.get(Calendar.YEAR));
        FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Refreshing budget");
        debounceSync(month, year, 300);
    }

    private void debounceSync(String month, String year, long delayMs) {
        if (pendingSyncRunnable != null) {
            syncHandler.removeCallbacks(pendingSyncRunnable);
        }

        pendingSyncRunnable = () -> syncReceiptsWithBudget(month, year);
        syncHandler.postDelayed(pendingSyncRunnable, delayMs);
    }

    private void syncReceiptsWithBudget(String month, String year) {
        if (isSyncing) {
            FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Sync already in progress, skipping...");
            return;
        }

        isSyncing = true;
        FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Syncing receipts with budget for " + month + " " + year);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            FirebaseCrashlytics.getInstance().log("W/BudgetViewModel: User not authenticated, cannot sync receipts");
            isSyncing = false;
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, Integer.parseInt(year));
        calendar.set(Calendar.MONTH, getMonthNumber(month));
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long monthStart = calendar.getTimeInMillis();

        calendar.add(Calendar.MONTH, 1);
        long monthEnd = calendar.getTimeInMillis();

        db.collection("users")
                .document(userId)
                .collection("receipts")
                .whereGreaterThanOrEqualTo("receipt.receiptDateTimestamp", monthStart)
                .whereLessThan("receipt.receiptDateTimestamp", monthEnd)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    double totalSpent = 0.0;
                    int receiptCount = 0;

                    for (QueryDocumentSnapshot document : querySnapshot) {
                        Receipt receipt = ReceiptRepository.parseReceiptFromDocument(document);
                        if (receipt != null && receipt.getReceipt() != null) {
                            double total = receipt.getReceipt().getTotal();
                            if (total > 0) {
                                totalSpent += total;
                                receiptCount++;
                            }
                        }
                    }
                    FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Found " + receiptCount + " receipts with a total of " + totalSpent);
                    syncManualTransactions(month, year, totalSpent, receiptCount);
                })
                .addOnFailureListener(e -> {
                    FirebaseCrashlytics.getInstance().log("E/BudgetViewModel: Error syncing receipts with budget");
                    FirebaseCrashlytics.getInstance().recordException(e);
                    errorMessage.postValue("Failed to sync receipts: " + e.getMessage());
                    isSyncing = false;
                });
    }

    private void syncManualTransactions(String month, String year, double receiptTotal, int receiptCount) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            FirebaseCrashlytics.getInstance().log("W/BudgetViewModel: User not authenticated, cannot sync transactions");
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .document(userId)
                .collection("transactions")
                .whereEqualTo("month", month)
                .whereEqualTo("year", year)
                .whereEqualTo("type", "expense")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    double manualExpenses = 0.0;
                    int transactionCount = 0;

                    for (QueryDocumentSnapshot document : querySnapshot) {
                        com.mytrackr.receipts.data.model.Transaction transaction = document.toObject(com.mytrackr.receipts.data.model.Transaction.class);
                        if (transaction != null && transaction.isExpense()) {
                            manualExpenses += transaction.getAmount();
                            transactionCount++;
                        }
                    }

                    double totalSpent = receiptTotal + manualExpenses;
                    int totalCount = receiptCount + transactionCount;

                    FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Found " + transactionCount + " manual transactions with a total of " + manualExpenses);
                    FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Combined total spent: " + totalSpent);

                    receiptCountLiveData.postValue(receiptCount);
                    manualTransactionCountLiveData.postValue(transactionCount);

                    double average = totalCount > 0 ? totalSpent / totalCount : 0.0;
                    averageExpenseLiveData.postValue(average);

                    Budget currentBudget = budgetLiveData.getValue();
                    if (currentBudget != null) {
                        if (Math.abs(currentBudget.getSpent() - totalSpent) > 0.01) {
                            updateBudget(currentBudget.getAmount(), month, year, totalSpent);
                            FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Synced budget spent amount to " + totalSpent);
                        } else {
                            FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Budget spent amount unchanged at " + totalSpent);
                        }
                    } else {
                        FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: No budget exists yet, synced spent amount: " + totalSpent);
                    }

                    isSyncing = false;
                })
                .addOnFailureListener(e -> {
                    FirebaseCrashlytics.getInstance().log("E/BudgetViewModel: Error syncing manual transactions");
                    FirebaseCrashlytics.getInstance().recordException(e);
                    Budget currentBudget = budgetLiveData.getValue();
                    if (currentBudget != null && Math.abs(currentBudget.getSpent() - receiptTotal) > 0.01) {
                        updateBudget(currentBudget.getAmount(), month, year, receiptTotal);
                    }
                    isSyncing = false;
                });
    }

    private int getMonthNumber(String monthName) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM", Locale.getDefault());
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(monthName));
            return cal.get(Calendar.MONTH);
        } catch (Exception e) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MMMM", Locale.ENGLISH);
                Calendar cal = Calendar.getInstance();
                cal.setTime(sdf.parse(monthName));
                return cal.get(Calendar.MONTH);
            } catch (Exception e2) {
                FirebaseCrashlytics.getInstance().log("E/BudgetViewModel: Error parsing month: " + monthName);
                FirebaseCrashlytics.getInstance().recordException(e2);
                return Calendar.getInstance().get(Calendar.MONTH);
            }
        }
    }

    public void loadCurrentMonthTransactions() {
        FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Loading current month transactions");
        transactionRepository.getRecentTransactionsLastMonth(50, transactionsLiveData, errorMessage);
    }

    public void loadCurrentMonthReceipts(MutableLiveData<List<com.mytrackr.receipts.data.models.Receipt>> receiptsLiveData) {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.getTime());
        String year = String.valueOf(calendar.get(Calendar.YEAR));

        FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Loading receipts for " + month + " " + year);

        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long monthStart = calendar.getTimeInMillis();

        calendar.add(Calendar.MONTH, 1);
        long monthEnd = calendar.getTimeInMillis();

        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            errorMessage.postValue("User not authenticated");
            FirebaseCrashlytics.getInstance().log("W/BudgetViewModel: User not authenticated, cannot load receipts");
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();

        db.collection("users")
                .document(userId)
                .collection("receipts")
                .whereGreaterThanOrEqualTo("receipt.receiptDateTimestamp", monthStart)
                .whereLessThan("receipt.receiptDateTimestamp", monthEnd)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<com.mytrackr.receipts.data.models.Receipt> receipts = new ArrayList<>();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot document : querySnapshot) {
                        com.mytrackr.receipts.data.models.Receipt receipt = ReceiptRepository.parseReceiptFromDocument(document);
                        if (receipt != null && receipt.getReceipt() != null && receipt.getReceipt().getTotal() > 0) {
                            receipt.setId(document.getId());
                            receipts.add(receipt);
                        }
                    }
                    receiptsLiveData.postValue(receipts);
                    FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Loaded " + receipts.size() + " receipts for expenses");
                })
                .addOnFailureListener(e -> {
                    FirebaseCrashlytics.getInstance().log("E/BudgetViewModel: Error loading receipts for expenses");
                    FirebaseCrashlytics.getInstance().recordException(e);
                    errorMessage.postValue("Failed to load receipts: " + e.getMessage());
                });
    }

    public void loadBudget(String month, String year) {
        FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Loading budget for " + month + " " + year);
        budgetRepository.getBudget(month, year, budgetLiveData, errorMessage);
    }

    public void saveBudget(double amount) {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.getTime());
        String year = String.valueOf(calendar.get(Calendar.YEAR));

        FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Saving budget for " + month + " " + year);

        Budget currentBudget = budgetLiveData.getValue();
        double currentSpent = (currentBudget != null) ? currentBudget.getSpent() : 0.0;

        Budget budget = new Budget();
        budget.setAmount(amount);
        budget.setMonth(month);
        budget.setYear(year);
        budget.setSpent(currentSpent);

        budgetLiveData.postValue(budget);

        budgetRepository.saveBudget(budget, saveSuccessLiveData, errorMessage);
    }

    public void updateBudget(double amount, String month, String year, double spent) {
        Budget budget = new Budget();
        budget.setAmount(amount);
        budget.setMonth(month);
        budget.setYear(year);
        budget.setSpent(spent);

        FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Updating budget for " + month + " " + year);
        budgetRepository.saveBudgetSilently(budget, errorMessage);

        budgetLiveData.postValue(budget);
    }

    public void updateSpentAmount(String month, String year, double spentAmount) {
        FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Updating spent amount for " + month + " " + year);
        budgetRepository.updateSpentAmount(month, year, spentAmount, saveSuccessLiveData, errorMessage);
    }

    public void addTransaction(String description, double amount, String type) {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.getTime());
        String year = String.valueOf(calendar.get(Calendar.YEAR));
        long timestamp = System.currentTimeMillis();

        FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Adding transaction: " + description);

        Transaction transaction = new Transaction();
        transaction.setDescription(description);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setTimestamp(timestamp);
        transaction.setMonth(month);
        transaction.setYear(year);

        transactionRepository.addTransaction(transaction, saveSuccessLiveData, errorMessage);
    }

    public void deleteTransaction(String transactionId) {
        FirebaseCrashlytics.getInstance().log("D/BudgetViewModel: Deleting transaction: " + transactionId);
        transactionRepository.deleteTransaction(transactionId, saveSuccessLiveData, errorMessage);

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            refreshBudget();
            loadCurrentMonthTransactions();
        }, 300);
    }
}
