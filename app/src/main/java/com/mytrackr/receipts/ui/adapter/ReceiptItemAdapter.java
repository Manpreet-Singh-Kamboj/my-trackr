package com.mytrackr.receipts.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.models.ReceiptItem;

import java.util.List;
import java.util.Locale;

public class ReceiptItemAdapter extends RecyclerView.Adapter<ReceiptItemAdapter.ItemViewHolder> {
    private final List<ReceiptItem> items;
    private final String currency;

    public ReceiptItemAdapter(List<ReceiptItem> items, String currency) {
        this.items = items;
        this.currency = currency != null && !currency.isEmpty() ? currency : "USD";
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_receipt_detail, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        ReceiptItem item = items.get(position);
        holder.bind(item, currency);
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        private final TextView itemName;
        private final TextView itemQuantity;
        private final TextView itemUnitPrice;
        private final TextView itemTotal;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            itemName = itemView.findViewById(R.id.itemName);
            itemQuantity = itemView.findViewById(R.id.itemQuantity);
            itemUnitPrice = itemView.findViewById(R.id.itemUnitPrice);
            itemTotal = itemView.findViewById(R.id.itemTotal);
        }

        void bind(ReceiptItem item, String currency) {
            if (item.getName() != null && !item.getName().isEmpty()) {
                itemName.setText(item.getName());
            } else {
                itemName.setText("Unknown Item");
            }

            if (item.getQuantity() != null && item.getQuantity() > 0) {
                itemQuantity.setText("Qty: " + item.getQuantity());
                itemQuantity.setVisibility(View.VISIBLE);
            } else {
                itemQuantity.setVisibility(View.GONE);
            }

            if (item.getUnitPrice() != null && item.getUnitPrice() > 0) {
                itemUnitPrice.setText("@ " + formatCurrency(item.getUnitPrice(), currency));
                itemUnitPrice.setVisibility(View.VISIBLE);
            } else {
                itemUnitPrice.setVisibility(View.GONE);
            }

            double itemPrice = item.getEffectiveTotalPrice();
            itemTotal.setText(formatCurrency(itemPrice, currency));
        }

        private String formatCurrency(double amount, String currency) {
            String symbol = getCurrencySymbol(currency);
            return String.format(Locale.US, "%s%.2f", symbol, amount);
        }

        private String getCurrencySymbol(String currency) {
            if (currency == null) return "$";
            switch (currency.toUpperCase()) {
                case "USD": return "$";
                case "CAD": return "C$";
                case "EUR": return "€";
                case "GBP": return "£";
                case "INR": return "₹";
                case "NGN": return "₦";
                case "KRW": return "₩";
                default: return currency + " ";
            }
        }
    }
}
