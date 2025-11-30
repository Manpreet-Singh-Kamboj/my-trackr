package com.mytrackr.receipts.features.core.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.model.Budget;
import com.mytrackr.receipts.data.model.ExpenseItem;
import com.mytrackr.receipts.data.model.Transaction;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.ui.adapter.ExpenseItemAdapter;
import com.mytrackr.receipts.ui.bottomsheet.AddExpenseBottomSheet;
import androidx.lifecycle.MutableLiveData;
import java.util.ArrayList;
import java.util.List;
import com.mytrackr.receipts.ui.bottomsheet.EditBudgetBottomSheet;
import com.mytrackr.receipts.viewmodels.BudgetViewModel;
import java.text.NumberFormat;
import java.util.Locale;

public class ExpensesFragment extends Fragment {

    private BudgetViewModel budgetViewModel;

    private TextView tvBudgetAmount, tvSpentAmount, tvRemainingAmount, tvNoBudget, tvBudgetStatus, tvBudgetMonth;
    private ProgressBar progressBudget;

    private RecyclerView rvTransactions;
    private com.google.android.material.card.MaterialCardView cardNoTransactions;
    private TextView tvReceiptsCount, tvManualTransactionsCount;
    private ProgressBar progressLoading;
    private ExpenseItemAdapter expenseItemAdapter;
    private final MutableLiveData<List<Receipt>> receiptsLiveData = new MutableLiveData<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        budgetViewModel = new ViewModelProvider(this).get(BudgetViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_expenses, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvBudgetAmount = view.findViewById(R.id.tvBudgetAmount);
        tvSpentAmount = view.findViewById(R.id.tvSpentAmount);
        tvRemainingAmount = view.findViewById(R.id.tvRemainingAmount);
        tvNoBudget = view.findViewById(R.id.tvNoBudget);
        tvBudgetStatus = view.findViewById(R.id.tvBudgetStatus);
        tvBudgetMonth = view.findViewById(R.id.tvBudgetMonth);
        progressBudget = view.findViewById(R.id.progressBudget);
        com.google.android.material.button.MaterialButton btnEditBudget = view.findViewById(R.id.btnEditBudget);
        com.google.android.material.button.MaterialButton btnAddExpense = view.findViewById(R.id.btnAddExpense);
        rvTransactions = view.findViewById(R.id.rvTransactions);
        cardNoTransactions = view.findViewById(R.id.cardNoTransactions);
        tvReceiptsCount = view.findViewById(R.id.tvReceiptsCount);
        tvManualTransactionsCount = view.findViewById(R.id.tvManualTransactionsCount);
        progressLoading = view.findViewById(R.id.progressLoading);

        expenseItemAdapter = new ExpenseItemAdapter();
        rvTransactions.setLayoutManager(new LinearLayoutManager(getContext()));
        expenseItemAdapter.setOnExpenseItemDeleteListener(this::showDeleteConfirmationDialog);
        rvTransactions.setAdapter(expenseItemAdapter);

        setupObservers();

        btnEditBudget.setOnClickListener(v -> showEditBudgetDialog());
        btnAddExpense.setOnClickListener(v -> showAddExpenseDialog());

        showLoading(true);
        budgetViewModel.loadCurrentMonthBudget();
        budgetViewModel.loadCurrentMonthTransactions();
        budgetViewModel.loadCurrentMonthReceipts(receiptsLiveData);

        ViewCompat.setOnApplyWindowInsetsListener(requireView(), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            layoutParams.leftMargin = insets.left;
            layoutParams.topMargin = insets.top;
            layoutParams.rightMargin = insets.right;
            layoutParams.bottomMargin = 0;
            v.setLayoutParams(layoutParams);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupObservers() {
        budgetViewModel.getBudgetLiveData().observe(getViewLifecycleOwner(), budget -> {
            if (budget != null) {
                updateBudgetUI(budget);
            } else {
                showNoBudgetMessage();
            }
        });

        budgetViewModel.getTransactionsLiveData().observe(getViewLifecycleOwner(), transactions -> {
            combineAndDisplayExpenses(transactions, receiptsLiveData.getValue());
        });

        receiptsLiveData.observe(getViewLifecycleOwner(), receipts -> {
            combineAndDisplayExpenses(budgetViewModel.getTransactionsLiveData().getValue(), receipts);
        });

        budgetViewModel.getSaveSuccessLiveData().observe(getViewLifecycleOwner(), success -> {
            if (success != null) {
                if (success) {
                    // Reload budget to ensure UI is in sync with Firestore
                    budgetViewModel.loadCurrentMonthBudget();
                    budgetViewModel.loadCurrentMonthTransactions();
                    budgetViewModel.loadCurrentMonthReceipts(receiptsLiveData);
                } else {
                    showLoading(false);
                }
            }
        });

        budgetViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                showLoading(false);
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });

        budgetViewModel.getReceiptCountLiveData().observe(getViewLifecycleOwner(), count -> {
            if (tvReceiptsCount != null && count != null) {
                tvReceiptsCount.setText(String.valueOf(count));
            }
        });

        budgetViewModel.getManualTransactionCountLiveData().observe(getViewLifecycleOwner(), count -> {
            if (tvManualTransactionsCount != null && count != null) {
                tvManualTransactionsCount.setText(String.valueOf(count));
            }
        });
    }


    private void combineAndDisplayExpenses(List<Transaction> transactions, List<Receipt> receipts) {
        showLoading(false);

        List<ExpenseItem> expenseItems = new ArrayList<>();

        int receiptCount = 0;
        if (receipts != null) {
            for (Receipt receipt : receipts) {
                if (receipt.getReceipt() != null && receipt.getReceipt().getTotal() > 0) {
                    expenseItems.add(new ExpenseItem(receipt));
                    receiptCount++;
                }
            }
        }

        int manualTransactionCount = 0;
        if (transactions != null) {
            for (Transaction transaction : transactions) {
                if (transaction.isExpense()) {
                    expenseItems.add(new ExpenseItem(transaction));
                    manualTransactionCount++;
                }
            }
        }

        if (tvReceiptsCount != null) {
            tvReceiptsCount.setText(String.valueOf(receiptCount));
        }
        if (tvManualTransactionsCount != null) {
            tvManualTransactionsCount.setText(String.valueOf(manualTransactionCount));
        }

        expenseItems.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        if (!expenseItems.isEmpty()) {
            expenseItemAdapter.setExpenseItems(expenseItems);
            rvTransactions.setVisibility(View.VISIBLE);
            cardNoTransactions.setVisibility(View.GONE);
        } else {
            rvTransactions.setVisibility(View.GONE);
            cardNoTransactions.setVisibility(View.VISIBLE);
        }
    }

    private void updateBudgetUI(Budget budget) {
        tvNoBudget.setVisibility(View.GONE);
        tvBudgetAmount.setVisibility(View.VISIBLE);
        tvSpentAmount.setVisibility(View.VISIBLE);
        tvRemainingAmount.setVisibility(View.VISIBLE);
        progressBudget.setVisibility(View.VISIBLE);
        tvBudgetStatus.setVisibility(View.VISIBLE);

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "US"));

        if (budget.getAmount() > 0) {
            tvBudgetAmount.setText(currencyFormat.format(budget.getAmount()));
        } else {
            tvBudgetAmount.setText("");
        }

        tvSpentAmount.setText(currencyFormat.format(budget.getSpent()));
        tvRemainingAmount.setText(currencyFormat.format(budget.getRemaining()));

        if (tvBudgetMonth != null && budget.getMonth() != null && budget.getYear() != null) {
            String monthYear = budget.getMonth() + " " + budget.getYear();
            tvBudgetMonth.setText(monthYear);
        } else if (tvBudgetMonth != null) {
            tvBudgetMonth.setText("");
        }

        double percentage = budget.getSpentPercentage();
        int progress = (int) Math.round(percentage);
        progressBudget.setProgress(Math.min(Math.max(progress, 0), 100));

        updateBudgetStatus(budget);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void updateBudgetStatus(Budget budget) {
        double percentage = budget.getSpentPercentage();
        int errorColor = getResources().getColor(R.color.error, null);
        int primaryColor = getResources().getColor(R.color.primary, null);

        if (percentage >= 100) {
            progressBudget.setProgressDrawable(getResources().getDrawable(R.drawable.progress_budget_exceeded, null));
            tvBudgetStatus.setText(R.string.budget_exceeded);
            tvBudgetStatus.setTextColor(errorColor);
            tvRemainingAmount.setTextColor(errorColor);
            tvBudgetAmount.setTextColor(errorColor);
        } else if (percentage >= 85) {
            progressBudget.setProgressDrawable(getResources().getDrawable(R.drawable.progress_budget_danger, null));
            tvBudgetStatus.setText(R.string.almost_exceeded);
            tvBudgetStatus.setTextColor(getResources().getColor(R.color.budget_danger, null));
            tvRemainingAmount.setTextColor(getResources().getColor(R.color.budget_danger, null));
            tvBudgetAmount.setTextColor(primaryColor);
        } else if (percentage >= 70) {
            progressBudget.setProgressDrawable(getResources().getDrawable(R.drawable.progress_budget_warning, null));
            tvBudgetStatus.setText(R.string.spending_high);
            tvBudgetStatus.setTextColor(getResources().getColor(R.color.budget_warning, null));
            tvRemainingAmount.setTextColor(getResources().getColor(R.color.budget_warning, null));
            tvBudgetAmount.setTextColor(primaryColor);
        } else {
            progressBudget.setProgressDrawable(getResources().getDrawable(R.drawable.progress_budget_safe, null));
            tvBudgetStatus.setText(R.string.on_track);
            tvBudgetStatus.setTextColor(getResources().getColor(R.color.budget_safe, null));
            tvRemainingAmount.setTextColor(primaryColor);
            tvBudgetAmount.setTextColor(primaryColor);
        }
    }

    private void showNoBudgetMessage() {
        tvNoBudget.setVisibility(View.VISIBLE);
        tvBudgetAmount.setVisibility(View.GONE);
        tvSpentAmount.setVisibility(View.GONE);
        tvRemainingAmount.setVisibility(View.GONE);
        tvBudgetStatus.setVisibility(View.GONE);
        progressBudget.setVisibility(View.GONE);
        progressBudget.setProgress(0);

        if (tvBudgetAmount != null) tvBudgetAmount.setText("");
        if (tvSpentAmount != null) tvSpentAmount.setText("");
        if (tvRemainingAmount != null) tvRemainingAmount.setText("");
        if (tvBudgetMonth != null) tvBudgetMonth.setText("");
        if (tvBudgetStatus != null) tvBudgetStatus.setText("");
    }

    private void showEditBudgetDialog() {
        Budget currentBudget = budgetViewModel.getBudgetLiveData().getValue();
        double currentAmount = currentBudget != null ? currentBudget.getAmount() : 0.0;

        EditBudgetBottomSheet bottomSheet = EditBudgetBottomSheet.newInstance(currentAmount);
        bottomSheet.setOnBudgetSavedListener(amount -> budgetViewModel.saveBudget(amount));
        bottomSheet.show(getParentFragmentManager(), "EditBudgetBottomSheet");
    }

    private void showAddExpenseDialog() {
        Budget currentBudget = budgetViewModel.getBudgetLiveData().getValue();

        if (currentBudget == null) {
            Toast.makeText(getContext(), "Please set a budget first!", Toast.LENGTH_SHORT).show();
            return;
        }

        AddExpenseBottomSheet bottomSheet = AddExpenseBottomSheet.newInstance();
        bottomSheet.setOnExpenseAddedListener((description, expenseAmount) -> {
            showLoading(true);

            budgetViewModel.addTransaction(description, expenseAmount, "expense");

            double currentSpent = currentBudget.getSpent();
            double newSpent = currentSpent + expenseAmount;

            budgetViewModel.updateBudget(
                    currentBudget.getAmount(),
                    currentBudget.getMonth(),
                    currentBudget.getYear(),
                    newSpent
            );

            Toast.makeText(getContext(), "Expense added: " + description + " - $" + expenseAmount, Toast.LENGTH_SHORT).show();
        });
        bottomSheet.show(getParentFragmentManager(), "AddExpenseBottomSheet");
    }

    @Override
    public void onResume() {
        super.onResume();
        budgetViewModel.refreshBudget();
        budgetViewModel.loadCurrentMonthReceipts(receiptsLiveData);
        budgetViewModel.loadCurrentMonthTransactions();
    }

    private void showDeleteConfirmationDialog(ExpenseItem item) {
        if (item.isReceipt()) {
            Toast.makeText(getContext(), "Receipt Expenses can only be deleted from receipt details", Toast.LENGTH_SHORT).show();
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Expense")
                .setMessage("Are you sure you want to delete \"" + item.getDescription() + "\"? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteExpenseItem(item);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteExpenseItem(ExpenseItem item) {
        if (item.isReceipt()) {
            Toast.makeText(getContext(), "Cannot delete receipt from expense list", Toast.LENGTH_SHORT).show();
            return;
        }

        String transactionId = item.getId();
        if (transactionId == null || transactionId.isEmpty()) {
            Toast.makeText(getContext(), "Transaction ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        budgetViewModel.deleteTransaction(transactionId);

        Toast.makeText(getContext(), "Expense deleted", Toast.LENGTH_SHORT).show();
    }

    private void showLoading(boolean show) {
        if (progressLoading != null) {
            progressLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (rvTransactions != null) {
            rvTransactions.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (cardNoTransactions != null) {
            cardNoTransactions.setVisibility(View.GONE);
        }
    }
}