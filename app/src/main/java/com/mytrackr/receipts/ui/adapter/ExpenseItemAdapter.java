package com.mytrackr.receipts.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.model.ExpenseItem;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExpenseItemAdapter extends RecyclerView.Adapter<ExpenseItemAdapter.ExpenseItemViewHolder> {
    private List<ExpenseItem> expenseItems = new ArrayList<>();
    private final NumberFormat currencyFormat;
    private OnExpenseItemDeleteListener deleteListener;

    public interface OnExpenseItemDeleteListener {
        void onDeleteRequested(ExpenseItem item);
    }

    public ExpenseItemAdapter() {
        currencyFormat = NumberFormat.getCurrencyInstance(Locale.CANADA);
    }

    public void setOnExpenseItemDeleteListener(OnExpenseItemDeleteListener listener) {
        this.deleteListener = listener;
    }

    @NonNull
    @Override
    public ExpenseItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new ExpenseItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExpenseItemViewHolder holder, int position) {
        ExpenseItem item = expenseItems.get(position);
        holder.bind(item);

        holder.itemView.setOnLongClickListener(v -> {
            if (deleteListener != null) {
                ExpenseItem expenseItem = expenseItems.get(position);
                deleteListener.onDeleteRequested(expenseItem);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return expenseItems.size();
    }

    public void setExpenseItems(List<ExpenseItem> items) {
        this.expenseItems = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    class ExpenseItemViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivTransactionIcon;
        private final MaterialCardView iconContainer;
        private final TextView tvTransactionDescription;
        private final TextView tvTransactionDate;
        private final TextView tvTransactionAmount;

        public ExpenseItemViewHolder(@NonNull View itemView) {
            super(itemView);
            ivTransactionIcon = itemView.findViewById(R.id.ivTransactionIcon);
            iconContainer = itemView.findViewById(R.id.iconContainer);
            tvTransactionDescription = itemView.findViewById(R.id.tvTransactionDescription);
            tvTransactionDate = itemView.findViewById(R.id.tvTransactionDate);
            tvTransactionAmount = itemView.findViewById(R.id.tvTransactionAmount);
        }

        public void bind(ExpenseItem item) {
            tvTransactionDescription.setText(item.getDescription());

            tvTransactionDate.setText(getFormattedDateWithMonth(item.getTimestamp()));

            IconInfo iconInfo = getIconForExpenseItem(item);

            ivTransactionIcon.setImageResource(iconInfo.iconRes);
            ivTransactionIcon.setColorFilter(iconInfo.iconTintColor);
            if (iconContainer != null) {
                iconContainer.setCardBackgroundColor(iconInfo.containerColor);
                iconContainer.getBackground().setAlpha(iconInfo.containerAlpha);
            }

            String amountStr;
            if (item.isExpense()) {
                amountStr = "- " + currencyFormat.format(item.getAmount());
            } else {
                amountStr = "+ " + currencyFormat.format(item.getAmount());
            }
            tvTransactionAmount.setText(amountStr);
            tvTransactionAmount.setTextColor(iconInfo.amountColor);
        }

        private IconInfo getIconForExpenseItem(ExpenseItem item) {
            String description = item.getDescription().toLowerCase();
            String category = item.getCategory() != null ? item.getCategory().toLowerCase() : "";

            int iconRes;
            int iconTintColor;
            int containerColor;
            int amountColor;
            int containerAlpha;

            if (item.isReceipt()) {
                iconRes = getReceiptIcon(description, category);
                iconTintColor = ContextCompat.getColor(itemView.getContext(), R.color.primary);

                if (category.contains("grocery") || category.contains("food") || category.contains("supermarket") ||
                        description.contains("walmart") || description.contains("target") ||
                        description.contains("costco") || description.contains("kroger")) {
                    containerColor = ContextCompat.getColor(itemView.getContext(), R.color.primary);
                } else if (category.contains("restaurant") || category.contains("dining") ||
                        description.contains("restaurant") || description.contains("cafe") ||
                        description.contains("mcdonald") || description.contains("starbucks")) {
                    containerColor = ContextCompat.getColor(itemView.getContext(), R.color.budget_warning);
                } else if (category.contains("gas") || category.contains("fuel") ||
                        description.contains("shell") || description.contains("exxon") ||
                        description.contains("gas") || description.contains("fuel")) {
                    containerColor = ContextCompat.getColor(itemView.getContext(), R.color.budget_danger);
                } else {
                    containerColor = ContextCompat.getColor(itemView.getContext(), R.color.primary);
                }

                amountColor = ContextCompat.getColor(itemView.getContext(), R.color.error);
                containerAlpha = 38;
            } else if (item.isExpense()) {
                iconRes = getManualExpenseIcon(description);
                iconTintColor = ContextCompat.getColor(itemView.getContext(), R.color.error);

                if (description.contains("food") || description.contains("lunch") ||
                        description.contains("dinner") || description.contains("restaurant")) {
                    containerColor = ContextCompat.getColor(itemView.getContext(), R.color.budget_warning);
                } else if (description.contains("gas") || description.contains("fuel") ||
                        description.contains("transport")) {
                    containerColor = ContextCompat.getColor(itemView.getContext(), R.color.budget_danger);
                } else if (description.contains("shopping") || description.contains("store") ||
                        description.contains("mall")) {
                    containerColor = ContextCompat.getColor(itemView.getContext(), R.color.primary);
                } else {
                    containerColor = ContextCompat.getColor(itemView.getContext(), R.color.error);
                }

                amountColor = ContextCompat.getColor(itemView.getContext(), R.color.error);
                containerAlpha = 38;
            } else {
                iconRes = R.drawable.ic_transaction;
                iconTintColor = ContextCompat.getColor(itemView.getContext(), R.color.budget_safe);
                containerColor = ContextCompat.getColor(itemView.getContext(), R.color.budget_safe);
                amountColor = ContextCompat.getColor(itemView.getContext(), R.color.budget_safe);
                containerAlpha = 38;
            }

            return new IconInfo(iconRes, iconTintColor, containerColor, amountColor, containerAlpha);
        }

        private int getReceiptIcon(String description, String category) {
            return R.drawable.ic_receipt_icon;
        }

        private int getManualExpenseIcon(String description) {
            return R.drawable.ic_expense;
        }

        private class IconInfo {
            final int iconRes;
            final int iconTintColor;
            final int containerColor;
            final int amountColor;
            final int containerAlpha;

            IconInfo(int iconRes, int iconTintColor, int containerColor, int amountColor, int containerAlpha) {
                this.iconRes = iconRes;
                this.iconTintColor = iconTintColor;
                this.containerColor = containerColor;
                this.amountColor = amountColor;
                this.containerAlpha = containerAlpha;
            }
        }

        private String getFormattedDateWithMonth(long timestamp) {
            Calendar transactionDate = Calendar.getInstance();
            transactionDate.setTimeInMillis(timestamp);

            Calendar today = Calendar.getInstance();
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);

            boolean isSameMonth = transactionDate.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                    transactionDate.get(Calendar.YEAR) == today.get(Calendar.YEAR);

            if (isSameDay(transactionDate, today)) {
                return itemView.getContext().getString(R.string.today);
            } else if (isSameDay(transactionDate, yesterday)) {
                return itemView.getContext().getString(R.string.yesterday);
            } else if (isSameMonth) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
                return dateFormat.format(new Date(timestamp));
            } else {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                return dateFormat.format(new Date(timestamp));
            }
        }

        private boolean isSameDay(Calendar cal1, Calendar cal2) {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
        }
    }
}
