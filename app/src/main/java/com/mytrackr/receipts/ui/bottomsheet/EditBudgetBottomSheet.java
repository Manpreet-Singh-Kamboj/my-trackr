package com.mytrackr.receipts.ui.bottomsheet;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.mytrackr.receipts.R;

public class EditBudgetBottomSheet extends BottomSheetDialogFragment {
    private TextInputEditText etBudgetAmount;
    private MaterialButton btnSave, btnCancel;
    private OnBudgetSavedListener listener;
    private double currentBudgetAmount = 0.0;

    public interface OnBudgetSavedListener {
        void onBudgetSaved(double amount);
    }

    public static EditBudgetBottomSheet newInstance(double currentAmount) {
        EditBudgetBottomSheet fragment = new EditBudgetBottomSheet();
        Bundle args = new Bundle();
        args.putDouble("current_amount", currentAmount);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentBudgetAmount = getArguments().getDouble("current_amount", 0.0);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_edit_budget, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etBudgetAmount = view.findViewById(R.id.etBudgetAmount);
        btnSave = view.findViewById(R.id.btnSave);
        btnCancel = view.findViewById(R.id.btnCancel);

        // Pre-fill current amount if exists
        if (currentBudgetAmount > 0) {
            etBudgetAmount.setText(String.valueOf(currentBudgetAmount));
        }

        btnSave.setOnClickListener(v -> saveBudget());
        btnCancel.setOnClickListener(v -> dismiss());
    }

    private void saveBudget() {
        String amountStr = etBudgetAmount.getText().toString().trim();

        if (TextUtils.isEmpty(amountStr)) {
            Toast.makeText(getContext(), getString(R.string.please_enter_budget_amount), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                Toast.makeText(getContext(), getString(R.string.please_enter_a_valid_amount), Toast.LENGTH_SHORT).show();
                return;
            }

            if (listener != null) {
                listener.onBudgetSaved(amount);
            }
            dismiss();
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), getString(R.string.please_enter_a_valid_number), Toast.LENGTH_SHORT).show();
        }
    }

    public void setOnBudgetSavedListener(OnBudgetSavedListener listener) {
        this.listener = listener;
    }
}