package com.mytrackr.receipts.ui.viewmodel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.mytrackr.receipts.data.model.Budget;
import com.mytrackr.receipts.data.model.Transaction;
import com.mytrackr.receipts.data.repository.BudgetRepository;
import com.mytrackr.receipts.data.repository.TransactionRepository;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class BudgetViewModel extends ViewModel {
    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final MutableLiveData<Budget> budgetLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saveSuccessLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<Transaction>> transactionsLiveData = new MutableLiveData<>();

    public BudgetViewModel() {
        budgetRepository = BudgetRepository.getInstance();
        transactionRepository = TransactionRepository.getInstance();
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

    public void loadCurrentMonthBudget() {
        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.getTime());
        String year = String.valueOf(calendar.get(Calendar.YEAR));
        loadBudget(month, year);
    }

    public void loadCurrentMonthTransactions() {
        // Load transactions from last 30 days instead of just current month
        transactionRepository.getRecentTransactionsLastMonth(50, transactionsLiveData, errorMessage);
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

        budgetRepository.saveBudget(budget, saveSuccessLiveData, errorMessage);
    }

    public void updateBudget(double amount, String month, String year, double spent) {
        Budget budget = new Budget();
        budget.setAmount(amount);
        budget.setMonth(month);
        budget.setYear(year);
        budget.setSpent(spent);

        budgetRepository.saveBudget(budget, saveSuccessLiveData, errorMessage);
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
}
