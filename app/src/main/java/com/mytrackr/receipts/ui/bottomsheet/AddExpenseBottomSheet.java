package com.mytrackr.receipts.ui.bottomsheet;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.mytrackr.receipts.R;

public class AddExpenseBottomSheet extends BottomSheetDialogFragment {
    private TextInputEditText etExpenseDescription, etExpenseAmount;
    private Button btnAddExpense, btnCancelExpense;
    private OnExpenseAddedListener listener;

    public interface OnExpenseAddedListener {
        void onExpenseAdded(String description, double amount);
    }

    public static AddExpenseBottomSheet newInstance() {
        return new AddExpenseBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_expense, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etExpenseDescription = view.findViewById(R.id.etExpenseDescription);
        etExpenseAmount = view.findViewById(R.id.etExpenseAmount);
        btnAddExpense = view.findViewById(R.id.btnAddExpense);
        btnCancelExpense = view.findViewById(R.id.btnCancelExpense);

        btnAddExpense.setOnClickListener(v -> addExpense());
        btnCancelExpense.setOnClickListener(v -> dismiss());
    }

    private void addExpense() {
        String description = etExpenseDescription.getText().toString().trim();
        String amountStr = etExpenseAmount.getText().toString().trim();

        if (TextUtils.isEmpty(description)) {
            Toast.makeText(getContext(), "Please enter what you're spending on", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(amountStr)) {
            Toast.makeText(getContext(), "Please enter expense amount", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                Toast.makeText(getContext(), "Please enter a valid amount", Toast.LENGTH_SHORT).show();
                return;
            }

            if (listener != null) {
                listener.onExpenseAdded(description, amount);
            }
            dismiss();
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Please enter a valid number", Toast.LENGTH_SHORT).show();
        }
    }

    public void setOnExpenseAddedListener(OnExpenseAddedListener listener) {
        this.listener = listener;
    }
}
