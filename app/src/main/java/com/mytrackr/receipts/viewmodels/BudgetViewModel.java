package com.mytrackr.receipts.viewmodels;

import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
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
    private static final String TAG = "BudgetViewModel";
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

    // Flag to prevent multiple simultaneous syncs
    private boolean isSyncing = false;
    private android.os.Handler syncHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSyncRunnable = null;

    public BudgetViewModel() {
        budgetRepository = BudgetRepository.getInstance();
        transactionRepository = TransactionRepository.getInstance();
        receiptRepository = new ReceiptRepository();
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

        // Load budget first, then sync
        budgetRepository.getBudget(month, year, budgetLiveData, errorMessage);

        // Debounce sync to prevent multiple calls
        debounceSync(month, year, 500);
    }

    /**
     * Manually refresh budget by syncing with receipts
     * Useful when receipts are added/updated
     */
    public void refreshBudget() {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.getTime());
        String year = String.valueOf(calendar.get(Calendar.YEAR));
        debounceSync(month, year, 300);
    }

    /**
     * Debounce sync calls to prevent multiple simultaneous syncs
     */
    private void debounceSync(String month, String year, long delayMs) {
        // Cancel any pending sync
        if (pendingSyncRunnable != null) {
            syncHandler.removeCallbacks(pendingSyncRunnable);
        }

        // Schedule new sync
        pendingSyncRunnable = () -> syncReceiptsWithBudget(month, year);
        syncHandler.postDelayed(pendingSyncRunnable, delayMs);
    }

    /**
     * Sync receipts from current month/year and update budget spent amount
     */
    private void syncReceiptsWithBudget(String month, String year) {
        // Prevent multiple simultaneous syncs
        if (isSyncing) {
            Log.d(TAG, "Sync already in progress, skipping...");
            return;
        }

        isSyncing = true;

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.w(TAG, "User not authenticated, cannot sync receipts");
            isSyncing = false;
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Calculate start and end of current month
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

        Log.d(TAG, "Syncing receipts for month: " + month + " " + year +
                " (from " + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(monthStart) +
                " to " + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(monthEnd) + ")");

        // Query receipts with receiptDateTimestamp in current month
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
                                Log.d(TAG, "Found receipt: " + receipt.getId() +
                                        " with total: $" + total +
                                        " from store: " + (receipt.getStore() != null && receipt.getStore().getName() != null
                                        ? receipt.getStore().getName() : "Unknown"));
                            }
                        }
                    }

                    Log.d(TAG, "Total receipts found: " + receiptCount + ", Total from receipts: $" + totalSpent);

                    // Also get manual transactions (expenses) for the current month
                    syncManualTransactions(month, year, totalSpent, receiptCount);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error syncing receipts with budget", e);
                    errorMessage.postValue("Failed to sync receipts: " + e.getMessage());
                    isSyncing = false;
                });
    }

    /**
     * Sync manual transactions (expenses) and combine with receipts
     */
    private void syncManualTransactions(String month, String year, double receiptTotal, int receiptCount) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.w(TAG, "User not authenticated, cannot sync transactions");
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Get transactions for current month
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

                    // Combine receipts and manual transactions
                    double totalSpent = receiptTotal + manualExpenses;
                    int totalCount = receiptCount + transactionCount;

                    Log.d(TAG, "Manual transactions: " + transactionCount + ", Total: $" + manualExpenses);
                    Log.d(TAG, "Combined total spent: $" + totalSpent + " (Receipts: $" + receiptTotal + " + Manual: $" + manualExpenses + ")");

                    // Update receipt count and manual transaction count separately
                    receiptCountLiveData.postValue(receiptCount);
                    manualTransactionCountLiveData.postValue(transactionCount);

                    // Update average expense
                    double average = totalCount > 0 ? totalSpent / totalCount : 0.0;
                    averageExpenseLiveData.postValue(average);

                    // Update budget with synced amount
                    Budget currentBudget = budgetLiveData.getValue();
                    if (currentBudget != null) {
                        // Only update if the value actually changed to prevent unnecessary saves
                        if (Math.abs(currentBudget.getSpent() - totalSpent) > 0.01) {
                            updateBudget(currentBudget.getAmount(), month, year, totalSpent);
                            Log.d(TAG, "Synced budget spent amount: $" + currentBudget.getSpent() + " -> $" + totalSpent);
                        } else {
                            Log.d(TAG, "Budget spent amount unchanged: $" + totalSpent);
                        }
                    } else {
                        // If no budget exists, we still track the spent amount
                        // When user creates a budget, the spent amount will already be synced
                        Log.d(TAG, "No budget exists yet, synced spent amount: $" + totalSpent);
                    }

                    isSyncing = false;
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error syncing manual transactions", e);
                    // Still update with receipt total if manual transactions fail
                    Budget currentBudget = budgetLiveData.getValue();
                    if (currentBudget != null && Math.abs(currentBudget.getSpent() - receiptTotal) > 0.01) {
                        updateBudget(currentBudget.getAmount(), month, year, receiptTotal);
                    }
                    isSyncing = false;
                });
    }

    /**
     * Convert month name to month number (0-11)
     */
    private int getMonthNumber(String monthName) {
        try {
            // Try parsing with current locale first
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM", Locale.getDefault());
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(monthName));
            return cal.get(Calendar.MONTH);
        } catch (Exception e) {
            try {
                // Fallback to English locale
                SimpleDateFormat sdf = new SimpleDateFormat("MMMM", Locale.ENGLISH);
                Calendar cal = Calendar.getInstance();
                cal.setTime(sdf.parse(monthName));
                return cal.get(Calendar.MONTH);
            } catch (Exception e2) {
                Log.e(TAG, "Error parsing month: " + monthName, e2);
                // Return current month as fallback
                return Calendar.getInstance().get(Calendar.MONTH);
            }
        }
    }

    public void loadCurrentMonthTransactions() {
        // Load transactions from last 30 days instead of just current month
        transactionRepository.getRecentTransactionsLastMonth(50, transactionsLiveData, errorMessage);
    }

    /**
     * Load receipts for current month to display in expenses
     */
    public void loadCurrentMonthReceipts(MutableLiveData<List<com.mytrackr.receipts.data.models.Receipt>> receiptsLiveData) {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.getTime());
        String year = String.valueOf(calendar.get(Calendar.YEAR));

        // Calculate start and end of current month
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
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();

        // Query receipts with receiptDateTimestamp in current month
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
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading receipts for expenses", e);
                    // Post empty list on error so UI can still update
                    receiptsLiveData.postValue(new ArrayList<>());
                    errorMessage.postValue("Failed to load receipts: " + e.getMessage());
                });
    }

    public void loadBudget(String month, String year) {
        budgetRepository.getBudget(month, year, budgetLiveData, errorMessage);
    }

    public void saveBudget(double amount) {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.getTime());
        String year = String.valueOf(calendar.get(Calendar.YEAR));

        // Get current budget to preserve spent amount
        Budget currentBudget = budgetLiveData.getValue();
        double currentSpent = (currentBudget != null) ? currentBudget.getSpent() : 0.0;

        Budget budget = new Budget();
        budget.setAmount(amount);
        budget.setMonth(month);
        budget.setYear(year);
        budget.setSpent(currentSpent); // Preserve existing spent amount

        // Update LiveData immediately for UI update
        budgetLiveData.postValue(budget);

        budgetRepository.saveBudget(budget, saveSuccessLiveData, errorMessage);
    }

    public void updateBudget(double amount, String month, String year, double spent) {
        Budget budget = new Budget();
        budget.setAmount(amount);
        budget.setMonth(month);
        budget.setYear(year);
        budget.setSpent(spent);

        // Use a silent update that doesn't trigger saveSuccessLiveData to prevent loops
        budgetRepository.saveBudgetSilently(budget, errorMessage);

        // Update the LiveData directly without triggering observers
        budgetLiveData.postValue(budget);
    }

    public void updateSpentAmount(String month, String year, double spentAmount) {
        budgetRepository.updateSpentAmount(month, year, spentAmount, saveSuccessLiveData, errorMessage);
    }

    public void addTransaction(String description, double amount, String type) {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.getTime());
        String year = String.valueOf(calendar.get(Calendar.YEAR));
        long timestamp = System.currentTimeMillis();

        Transaction transaction = new Transaction();
        transaction.setDescription(description);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setTimestamp(timestamp);
        transaction.setMonth(month);
        transaction.setYear(year);

        transactionRepository.addTransaction(transaction, saveSuccessLiveData, errorMessage);
    }

    /**
     * Delete a manual transaction and update budget
     */
    public void deleteTransaction(String transactionId) {
        transactionRepository.deleteTransaction(transactionId, saveSuccessLiveData, errorMessage);

        // After deletion, refresh budget to sync with updated transactions (in background)
        // UI is already updated optimistically, so this is just for final sync
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            refreshBudget();
            loadCurrentMonthTransactions();
        }, 300);
    }
}