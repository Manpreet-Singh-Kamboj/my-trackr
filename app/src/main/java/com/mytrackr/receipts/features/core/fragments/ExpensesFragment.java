package com.mytrackr.receipts.features.core.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.model.Budget;
import com.mytrackr.receipts.ui.adapter.TransactionAdapter;
import com.mytrackr.receipts.ui.bottomsheet.AddExpenseBottomSheet;
import com.mytrackr.receipts.ui.bottomsheet.EditBudgetBottomSheet;
import com.mytrackr.receipts.ui.viewmodel.BudgetViewModel;
import java.text.NumberFormat;
import java.util.Locale;

public class ExpensesFragment extends Fragment {

    private BudgetViewModel budgetViewModel;
    
    // Views from budget_card.xml
    private MaterialCardView budgetCard;
    private TextView tvBudgetAmount, tvSpentAmount, tvRemainingAmount, tvNoBudget;
    private ProgressBar progressBudget;
    private ImageButton btnEditBudget;
    private com.google.android.material.button.MaterialButton btnTestSpent;
    
    // Transaction views
    private RecyclerView rvTransactions;
    private TextView tvNoTransactions;
    private TransactionAdapter transactionAdapter;

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

        // Initialize views
        budgetCard = view.findViewById(R.id.budgetCard);
        tvBudgetAmount = view.findViewById(R.id.tvBudgetAmount);
        tvSpentAmount = view.findViewById(R.id.tvSpentAmount);
        tvRemainingAmount = view.findViewById(R.id.tvRemainingAmount);
        tvNoBudget = view.findViewById(R.id.tvNoBudget);
        progressBudget = view.findViewById(R.id.progressBudget);
        btnEditBudget = view.findViewById(R.id.btnEditBudget);
        btnTestSpent = view.findViewById(R.id.btnTestSpent);
        rvTransactions = view.findViewById(R.id.rvTransactions);
        tvNoTransactions = view.findViewById(R.id.tvNoTransactions);

        // Set up RecyclerView
        transactionAdapter = new TransactionAdapter();
        rvTransactions.setLayoutManager(new LinearLayoutManager(getContext()));
        rvTransactions.setAdapter(transactionAdapter);

        // Set up observers
        setupObservers();

        // Set up click listeners
        btnEditBudget.setOnClickListener(v -> showEditBudgetDialog());
        btnTestSpent.setOnClickListener(v -> showAddExpenseDialog());

        // Load current month's budget and transactions
        budgetViewModel.loadCurrentMonthBudget();
        budgetViewModel.loadCurrentMonthTransactions();
    }

    private void setupObservers() {
        // Observe budget data
        budgetViewModel.getBudgetLiveData().observe(getViewLifecycleOwner(), budget -> {
            if (budget != null) {
                updateBudgetUI(budget);
            } else {
                showNoBudgetMessage();
            }
        });

        // Observe transactions
        budgetViewModel.getTransactionsLiveData().observe(getViewLifecycleOwner(), transactions -> {
            if (transactions != null && !transactions.isEmpty()) {
                transactionAdapter.setTransactions(transactions);
                rvTransactions.setVisibility(View.VISIBLE);
                tvNoTransactions.setVisibility(View.GONE);
            } else {
                rvTransactions.setVisibility(View.GONE);
                tvNoTransactions.setVisibility(View.VISIBLE);
            }
        });

        // Observe save success
        budgetViewModel.getSaveSuccessLiveData().observe(getViewLifecycleOwner(), success -> {
            if (success != null && success) {
                budgetViewModel.loadCurrentMonthBudget();
                budgetViewModel.loadCurrentMonthTransactions();
            }
        });

        // Observe errors
        budgetViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateBudgetUI(Budget budget) {
        tvNoBudget.setVisibility(View.GONE);
        tvBudgetAmount.setVisibility(View.VISIBLE);
        tvSpentAmount.setVisibility(View.VISIBLE);
        tvRemainingAmount.setVisibility(View.VISIBLE);
        progressBudget.setVisibility(View.VISIBLE);

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        
        tvBudgetAmount.setText(currencyFormat.format(budget.getAmount()));
        tvSpentAmount.setText(currencyFormat.format(budget.getSpent()));
        tvRemainingAmount.setText(currencyFormat.format(budget.getRemaining()));

        int progress = (int) budget.getSpentPercentage();
        progressBudget.setProgress(Math.min(progress, 100));
    }

    private void showNoBudgetMessage() {
        tvNoBudget.setVisibility(View.VISIBLE);
        tvBudgetAmount.setVisibility(View.GONE);
        tvSpentAmount.setVisibility(View.GONE);
        tvRemainingAmount.setVisibility(View.GONE);
        progressBudget.setVisibility(View.GONE);
        progressBudget.setProgress(0);
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
            // Add transaction to Firestore
            budgetViewModel.addTransaction(description, expenseAmount, "expense");
            
            // Get current budget details
            double currentSpent = currentBudget.getSpent();
            double newSpent = currentSpent + expenseAmount;
            
            // Update the budget with new spent amount
            budgetViewModel.updateBudget(
                currentBudget.getAmount(),
                currentBudget.getMonth(),
                currentBudget.getYear(),
                newSpent
            );
            
            Toast.makeText(getContext(), "Expense added: " + description + " - ₹" + expenseAmount, Toast.LENGTH_SHORT).show();
        });
        bottomSheet.show(getParentFragmentManager(), "AddExpenseBottomSheet");
    }
}