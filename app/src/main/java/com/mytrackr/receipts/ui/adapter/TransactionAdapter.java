package com.mytrackr.receipts.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.model.Transaction;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {
    private List<Transaction> transactions = new ArrayList<>();
    private final NumberFormat currencyFormat;

    public TransactionAdapter() {
        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        Transaction transaction = transactions.get(position);
        holder.bind(transaction);
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    public void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
        notifyDataSetChanged();
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivTransactionIcon;
        private final TextView tvTransactionDescription;
        private final TextView tvTransactionDate;
        private final TextView tvTransactionAmount;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            ivTransactionIcon = itemView.findViewById(R.id.ivTransactionIcon);
            tvTransactionDescription = itemView.findViewById(R.id.tvTransactionDescription);
            tvTransactionDate = itemView.findViewById(R.id.tvTransactionDate);
            tvTransactionAmount = itemView.findViewById(R.id.tvTransactionAmount);
        }

        public void bind(Transaction transaction) {
            tvTransactionDescription.setText(transaction.getDescription());
            tvTransactionDate.setText(getFormattedDateWithMonth(transaction.getTimestamp()));

            // Format amount with + or - prefix
            String amountStr;
            int textColor;
            if (transaction.isExpense()) {
                amountStr = "- " + currencyFormat.format(transaction.getAmount());
                textColor = itemView.getContext().getColor(R.color.light_red);
            } else {
                amountStr = "+ " + currencyFormat.format(transaction.getAmount());
                textColor = itemView.getContext().getColor(R.color.dark_green);
            }
            tvTransactionAmount.setText(amountStr);
            tvTransactionAmount.setTextColor(textColor);
        }

        private String getFormattedDateWithMonth(long timestamp) {
            Calendar transactionDate = Calendar.getInstance();
            transactionDate.setTimeInMillis(timestamp);

            Calendar today = Calendar.getInstance();
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);

            // Check if it's in current month
            boolean isSameMonth = transactionDate.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                                  transactionDate.get(Calendar.YEAR) == today.get(Calendar.YEAR);

            if (isSameDay(transactionDate, today)) {
                return "Today";
            } else if (isSameDay(transactionDate, yesterday)) {
                return "Yesterday";
            } else if (isSameMonth) {
                // Same month, just show date
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
                return dateFormat.format(new Date(timestamp));
            } else {
                // Different month, show month and date
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                return dateFormat.format(new Date(timestamp));
            }
        }

        private String getFormattedDate(long timestamp) {
            Calendar transactionDate = Calendar.getInstance();
            transactionDate.setTimeInMillis(timestamp);

            Calendar today = Calendar.getInstance();
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);

            if (isSameDay(transactionDate, today)) {
                return "Today";
            } else if (isSameDay(transactionDate, yesterday)) {
                return "Yesterday";
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
