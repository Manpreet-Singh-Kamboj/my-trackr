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
    private View emptyStateContainer;
    private View spentRemainingContainer;
    private com.google.android.material.button.MaterialButton btnSetBudget;
    private com.google.android.material.button.MaterialButton btnEditBudget;

    private RecyclerView rvTransactions;
    private com.google.android.material.card.MaterialCardView cardNoTransactions;
    private TextView tvReceiptsCount, tvManualTransactionsCount;
    private ProgressBar progressLoading;
    private View loadingProgressLayout;
    private ExpenseItemAdapter expenseItemAdapter;
    private final MutableLiveData<List<Receipt>> receiptsLiveData = new MutableLiveData<>();
    
    private boolean transactionsLoaded = false;
    private boolean receiptsLoaded = false;

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
        emptyStateContainer = view.findViewById(R.id.emptyStateContainer);
        spentRemainingContainer = view.findViewById(R.id.spentRemainingContainer);
        btnSetBudget = view.findViewById(R.id.btnSetBudget);
        btnEditBudget = view.findViewById(R.id.btnEditBudget);
        com.google.android.material.button.MaterialButton btnAddExpense = view.findViewById(R.id.btnAddExpense);
        rvTransactions = view.findViewById(R.id.rvTransactions);
        cardNoTransactions = view.findViewById(R.id.cardNoTransactions);
        tvReceiptsCount = view.findViewById(R.id.tvReceiptsCount);
        tvManualTransactionsCount = view.findViewById(R.id.tvManualTransactionsCount);
        progressLoading = view.findViewById(R.id.progressLoading);
        loadingProgressLayout = view.findViewById(R.id.loadingProgressLayout);
        loadingProgressLayout = view.findViewById(R.id.loadingProgressLayout);

        expenseItemAdapter = new ExpenseItemAdapter();
        rvTransactions.setLayoutManager(new LinearLayoutManager(getContext()));
        expenseItemAdapter.setOnExpenseItemDeleteListener(this::showDeleteConfirmationDialog);
        rvTransactions.setAdapter(expenseItemAdapter);

        setupObservers();

        btnEditBudget.setOnClickListener(v -> showEditBudgetDialog());
        btnAddExpense.setOnClickListener(v -> showAddExpenseDialog());
        if (btnSetBudget != null) {
            btnSetBudget.setOnClickListener(v -> showEditBudgetDialog());
        }

        transactionsLoaded = false;
        receiptsLoaded = false;
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
            transactionsLoaded = true;
            combineAndDisplayExpenses(transactions, receiptsLiveData.getValue());
        });

        receiptsLiveData.observe(getViewLifecycleOwner(), receipts -> {
            receiptsLoaded = true;
            combineAndDisplayExpenses(budgetViewModel.getTransactionsLiveData().getValue(), receipts);
        });

        budgetViewModel.getSaveSuccessLiveData().observe(getViewLifecycleOwner(), success -> {
            if (success != null) {
                if (success) {
                    transactionsLoaded = false;
                    receiptsLoaded = false;
                    showLoading(true);
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
                transactionsLoaded = true;
                receiptsLoaded = true;
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
        if (transactionsLoaded && receiptsLoaded) {
            showLoading(false);
        }

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
        if (emptyStateContainer != null) {
            emptyStateContainer.setVisibility(View.GONE);
        }
        tvNoBudget.setVisibility(View.GONE);
        
        tvBudgetAmount.setVisibility(View.VISIBLE);
        tvSpentAmount.setVisibility(View.VISIBLE);
        tvRemainingAmount.setVisibility(View.VISIBLE);
        progressBudget.setVisibility(View.VISIBLE);
        tvBudgetStatus.setVisibility(View.VISIBLE);
        
        if (spentRemainingContainer != null) {
            spentRemainingContainer.setVisibility(View.VISIBLE);
        }
        if (btnEditBudget != null) {
            btnEditBudget.setVisibility(View.VISIBLE);
        }

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.CANADA);

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
        if (emptyStateContainer != null) {
            emptyStateContainer.setVisibility(View.VISIBLE);
        }
        
        tvBudgetAmount.setVisibility(View.GONE);
        tvSpentAmount.setVisibility(View.GONE);
        tvRemainingAmount.setVisibility(View.GONE);
        tvBudgetStatus.setVisibility(View.GONE);
        progressBudget.setVisibility(View.GONE);
        progressBudget.setProgress(0);
        
        if (spentRemainingContainer != null) {
            spentRemainingContainer.setVisibility(View.GONE);
        }
        if (btnEditBudget != null) {
            btnEditBudget.setVisibility(View.GONE);
        }
        
        tvNoBudget.setVisibility(View.GONE);

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
            Toast.makeText(getContext(), getString(R.string.please_set_a_budget_first), Toast.LENGTH_SHORT).show();
            return;
        }

        AddExpenseBottomSheet bottomSheet = AddExpenseBottomSheet.newInstance();
        bottomSheet.setOnExpenseAddedListener((description, expenseAmount) -> {
            transactionsLoaded = false;
            receiptsLoaded = false;
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

            Toast.makeText(getContext(), getString(R.string.expense_added, description, expenseAmount), Toast.LENGTH_SHORT).show();
        });
        bottomSheet.show(getParentFragmentManager(), "AddExpenseBottomSheet");
    }

    @Override
    public void onResume() {
        super.onResume();
        Budget currentBudget = budgetViewModel.getBudgetLiveData().getValue();
        if (currentBudget != null) {
            updateBudgetUI(currentBudget);
        }
        
        List<Transaction> transactions = budgetViewModel.getTransactionsLiveData().getValue();
        List<Receipt> receipts = receiptsLiveData.getValue();
        boolean hasExistingData = (transactions != null && !transactions.isEmpty()) || 
                                   (receipts != null && !receipts.isEmpty());
        
        transactionsLoaded = false;
        receiptsLoaded = false;
        
        if (hasExistingData) {
            combineAndDisplayExpenses(transactions, receipts);
        } else {
            showLoading(true);
        }
        
        budgetViewModel.refreshBudget();
        budgetViewModel.loadCurrentMonthReceipts(receiptsLiveData);
        budgetViewModel.loadCurrentMonthTransactions();
    }

    private void showDeleteConfirmationDialog(ExpenseItem item) {
        if (item.isReceipt()) {
            Toast.makeText(getContext(), getString(R.string.receipt_expenses_can_only_be_deleted_from_receipt_details), Toast.LENGTH_SHORT).show();
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_expense)
                .setMessage(getString(R.string.are_you_sure_you_want_to_delete, item.getDescription()))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    deleteExpenseItem(item);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteExpenseItem(ExpenseItem item) {
        if (item.isReceipt()) {
            Toast.makeText(getContext(), getString(R.string.cannot_delete_receipt_from_expense_list), Toast.LENGTH_SHORT).show();
            return;
        }

        String transactionId = item.getId();
        if (transactionId == null || transactionId.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.transaction_id_not_found), Toast.LENGTH_SHORT).show();
            return;
        }

        transactionsLoaded = false;
        receiptsLoaded = false;
        showLoading(true);

        budgetViewModel.deleteTransaction(transactionId);

        Toast.makeText(getContext(), getString(R.string.expense_deleted), Toast.LENGTH_SHORT).show();
    }

    private void showLoading(boolean show) {
        if (loadingProgressLayout != null) {
            loadingProgressLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        }
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