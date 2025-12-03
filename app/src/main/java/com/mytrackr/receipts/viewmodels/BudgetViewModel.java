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

    private boolean isSyncing = false;
    private final MutableLiveData<Boolean> syncInProgressLiveData = new MutableLiveData<>(false);
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

    public MutableLiveData<Boolean> getSyncInProgressLiveData() {
        return syncInProgressLiveData;
    }

    public void loadCurrentMonthBudget() {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.ENGLISH).format(calendar.getTime());
        String year = String.valueOf(calendar.get(Calendar.YEAR));

        MutableLiveData<Budget> tempBudgetLiveData = new MutableLiveData<>();
        MutableLiveData<String> tempErrorLiveData = new MutableLiveData<>();
        
        final boolean[] processed = {false};
        
        final androidx.lifecycle.Observer<Budget>[] budgetObserverRef = new androidx.lifecycle.Observer[1];
        
        budgetObserverRef[0] = budget -> {
            if (processed[0]) return;
            processed[0] = true;
            
            if (budget == null) {
                copyPreviousMonthBudget(month, year);
            } else {
                budgetLiveData.postValue(budget);
            }
            syncHandler.post(() -> tempBudgetLiveData.removeObserver(budgetObserverRef[0]));
        };
        
        tempBudgetLiveData.observeForever(budgetObserverRef[0]);

        budgetRepository.getBudget(month, year, tempBudgetLiveData, tempErrorLiveData);

        debounceSync(month, year, 500);
    }
    
    private void copyPreviousMonthBudget(String currentMonth, String currentYear) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, Integer.parseInt(currentYear));
        
        int currentMonthNum = getMonthNumber(currentMonth);
        calendar.set(Calendar.MONTH, currentMonthNum);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        
        calendar.add(Calendar.MONTH, -1);
        
        String previousMonth = new SimpleDateFormat("MMMM", Locale.ENGLISH).format(calendar.getTime());
        String previousYear = String.valueOf(calendar.get(Calendar.YEAR));
        
        Log.d(TAG, "Checking for previous month budget: " + previousMonth + " " + previousYear);
        
        MutableLiveData<Budget> previousBudgetLiveData = new MutableLiveData<>();
        MutableLiveData<String> tempErrorLiveData2 = new MutableLiveData<>();
        
        final boolean[] processed2 = {false};
        
        final androidx.lifecycle.Observer<Budget>[] previousBudgetObserverRef = new androidx.lifecycle.Observer[1];
        
        previousBudgetObserverRef[0] = previousBudget -> {
            if (processed2[0]) return;
            processed2[0] = true;
            
            if (previousBudget != null && previousBudget.getAmount() > 0) {
                Log.d(TAG, "Copying budget from " + previousMonth + " " + previousYear + 
                      " to " + currentMonth + " " + currentYear + 
                      " (Amount: " + previousBudget.getAmount() + ")");
                
                Budget newBudget = new Budget(previousBudget.getAmount(), currentMonth, currentYear);
                newBudget.setSpent(0.0);
                
                budgetRepository.saveBudget(newBudget, saveSuccessLiveData, errorMessage);
                
                budgetLiveData.postValue(newBudget);
            } else {
                budgetLiveData.postValue(null);
            }
            syncHandler.post(() -> previousBudgetLiveData.removeObserver(previousBudgetObserverRef[0]));
        };
        
        previousBudgetLiveData.observeForever(previousBudgetObserverRef[0]);
        
        budgetRepository.getBudget(previousMonth, previousYear, previousBudgetLiveData, tempErrorLiveData2);
    }

    public void refreshBudget() {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.ENGLISH).format(calendar.getTime());
        String year = String.valueOf(calendar.get(Calendar.YEAR));
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
            Log.d(TAG, "Sync already in progress, skipping...");
            return;
        }

        isSyncing = true;
        syncInProgressLiveData.postValue(true);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.w(TAG, "User not authenticated, cannot sync receipts");
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

        Log.d(TAG, "Syncing receipts for month: " + month + " " + year +
                " (from " + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(monthStart) +
                " to " + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(monthEnd) + ")");

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

                    syncManualTransactions(month, year, totalSpent, receiptCount);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error syncing receipts with budget", e);
                    errorMessage.postValue("Failed to sync receipts: " + e.getMessage());
                    isSyncing = false;
                    syncInProgressLiveData.postValue(false);
                });
    }

    private void syncManualTransactions(String month, String year, double receiptTotal, int receiptCount) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.w(TAG, "User not authenticated, cannot sync transactions");
            isSyncing = false;
            syncInProgressLiveData.postValue(false);
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

                    Log.d(TAG, "Manual transactions: " + transactionCount + ", Total: $" + manualExpenses);
                    Log.d(TAG, "Combined total spent: $" + totalSpent + " (Receipts: $" + receiptTotal + " + Manual: $" + manualExpenses + ")");

                    receiptCountLiveData.postValue(receiptCount);
                    manualTransactionCountLiveData.postValue(transactionCount);

                    double average = totalCount > 0 ? totalSpent / totalCount : 0.0;
                    averageExpenseLiveData.postValue(average);

                    Budget currentBudget = budgetLiveData.getValue();
                    if (currentBudget != null) {
                        if (Math.abs(currentBudget.getSpent() - totalSpent) > 0.01) {
                            updateBudget(currentBudget.getAmount(), month, year, totalSpent);
                            Log.d(TAG, "Synced budget spent amount: $" + currentBudget.getSpent() + " -> $" + totalSpent);
                        } else {
                            Log.d(TAG, "Budget spent amount unchanged: $" + totalSpent);
                        }
                    } else {
                        Log.d(TAG, "No budget exists yet, synced spent amount: $" + totalSpent);
                    }

                    isSyncing = false;
                    syncInProgressLiveData.postValue(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error syncing manual transactions", e);
                    Budget currentBudget = budgetLiveData.getValue();
                    if (currentBudget != null && Math.abs(currentBudget.getSpent() - receiptTotal) > 0.01) {
                        updateBudget(currentBudget.getAmount(), month, year, receiptTotal);
                    }
                    isSyncing = false;
                    syncInProgressLiveData.postValue(false);
                });
    }

    private int getMonthNumber(String monthName) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM", Locale.ENGLISH);
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(monthName));
            return cal.get(Calendar.MONTH);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing month: " + monthName, e);
            return Calendar.getInstance().get(Calendar.MONTH);
        }
    }

    public void loadCurrentMonthTransactions() {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.ENGLISH).format(calendar.getTime());
        String year = String.valueOf(calendar.get(Calendar.YEAR));
        transactionRepository.getRecentTransactions(month, year, 50, transactionsLiveData, errorMessage);
    }

    public void loadTransactionsForMonth(String month, String year) {
        // Calculate month start and end timestamps
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
        
        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            errorMessage.postValue("User not authenticated");
            return;
        }
        
        String userId = auth.getCurrentUser().getUid();
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        
        // Query transactions by timestamp instead of month/year strings
        db.collection("users")
                .document(userId)
                .collection("transactions")
                .whereGreaterThanOrEqualTo("timestamp", monthStart)
                .whereLessThan("timestamp", monthEnd)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<com.mytrackr.receipts.data.model.Transaction> transactions = new ArrayList<>();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot document : querySnapshot) {
                        try {
                            com.mytrackr.receipts.data.model.Transaction transaction = document.toObject(com.mytrackr.receipts.data.model.Transaction.class);
                            transaction.setId(document.getId());
                            if (transaction.isExpense()) {
                                transactions.add(transaction);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing transaction", e);
                        }
                    }
                    transactionsLiveData.postValue(transactions);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading transactions for month", e);
                    transactionsLiveData.postValue(new ArrayList<>());
                    errorMessage.postValue("Failed to load transactions: " + e.getMessage());
                });
    }

    public void loadCurrentMonthReceipts(MutableLiveData<List<com.mytrackr.receipts.data.models.Receipt>> receiptsLiveData) {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.ENGLISH).format(calendar.getTime());
        String year = String.valueOf(calendar.get(Calendar.YEAR));

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
                    receiptsLiveData.postValue(new ArrayList<>());
                    errorMessage.postValue("Failed to load receipts: " + e.getMessage());
                });
    }

    public void loadReceiptsForMonth(String month, String year, MutableLiveData<List<com.mytrackr.receipts.data.models.Receipt>> receiptsLiveData) {
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

        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            errorMessage.postValue("User not authenticated");
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();

        // Query by receiptDateTimestamp only (original receipt date, not upload time)
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
                    receiptsLiveData.postValue(new ArrayList<>());
                    errorMessage.postValue("Failed to load receipts: " + e.getMessage());
                });
    }

    public void loadBudget(String month, String year) {
        budgetRepository.getBudget(month, year, budgetLiveData, errorMessage);
        debounceSync(month, year, 500);
    }

    public void saveBudget(double amount) {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.ENGLISH).format(calendar.getTime());
        String year = String.valueOf(calendar.get(Calendar.YEAR));

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

        budgetRepository.saveBudgetSilently(budget, errorMessage);

        budgetLiveData.postValue(budget);
    }

    public void updateSpentAmount(String month, String year, double spentAmount) {
        budgetRepository.updateSpentAmount(month, year, spentAmount, saveSuccessLiveData, errorMessage);
    }

    public void addTransaction(String description, double amount, String type) {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.ENGLISH).format(calendar.getTime());
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

    public void deleteTransaction(String transactionId) {
        transactionRepository.deleteTransaction(transactionId, saveSuccessLiveData, errorMessage);

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            refreshBudget();
            loadCurrentMonthTransactions();
        }, 300);
    }

    public void loadCurrentYearTransactions() {
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        
        calendar.set(Calendar.YEAR, currentYear);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long yearStart = calendar.getTimeInMillis();
        
        calendar.set(Calendar.YEAR, currentYear + 1);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        long yearEnd = calendar.getTimeInMillis();
        
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            errorMessage.postValue("User not authenticated");
            return;
        }
        
        String userId = auth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection("users")
                .document(userId)
                .collection("transactions")
                .whereGreaterThanOrEqualTo("timestamp", yearStart)
                .whereLessThan("timestamp", yearEnd)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Transaction> transactions = new ArrayList<>();
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        try {
                            Transaction transaction = document.toObject(Transaction.class);
                            transaction.setId(document.getId());
                            if (transaction.isExpense()) {
                                transactions.add(transaction);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing transaction", e);
                        }
                    }
                    transactionsLiveData.postValue(transactions);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading transactions for year", e);
                    transactionsLiveData.postValue(new ArrayList<>());
                    errorMessage.postValue("Failed to load transactions: " + e.getMessage());
                });
    }

    public void loadCurrentYearReceipts(MutableLiveData<List<Receipt>> receiptsLiveData) {
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        
        calendar.set(Calendar.YEAR, currentYear);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long yearStart = calendar.getTimeInMillis();
        
        calendar.set(Calendar.YEAR, currentYear + 1);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        long yearEnd = calendar.getTimeInMillis();
        
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            errorMessage.postValue("User not authenticated");
            return;
        }
        
        String userId = auth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection("users")
                .document(userId)
                .collection("receipts")
                .whereGreaterThanOrEqualTo("receipt.receiptDateTimestamp", yearStart)
                .whereLessThan("receipt.receiptDateTimestamp", yearEnd)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Receipt> receipts = new ArrayList<>();
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        Receipt receipt = ReceiptRepository.parseReceiptFromDocument(document);
                        if (receipt != null && receipt.getReceipt() != null && receipt.getReceipt().getTotal() > 0) {
                            receipt.setId(document.getId());
                            receipts.add(receipt);
                        }
                    }
                    receiptsLiveData.postValue(receipts);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading receipts for year", e);
                    receiptsLiveData.postValue(new ArrayList<>());
                    errorMessage.postValue("Failed to load receipts: " + e.getMessage());
                });
    }
}