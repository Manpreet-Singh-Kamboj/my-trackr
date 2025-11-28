package com.mytrackr.receipts.features.core.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.data.models.ReceiptItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReceiptAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_DATE_HEADER = 0;
    private static final int TYPE_RECEIPT = 1;
    
    private final List<Object> items;
    private OnReceiptClickListener listener;

    public interface OnReceiptClickListener {
        void onReceiptClick(Receipt receipt);
    }

    public ReceiptAdapter() {
        this.items = new ArrayList<>();
    }

    public void setOnReceiptClickListener(OnReceiptClickListener listener) {
        this.listener = listener;
    }

    public void setReceipts(List<Receipt> receipts) {
        items.clear();
        if (receipts != null && !receipts.isEmpty()) {
            // Group receipts by date
            Map<String, List<Receipt>> groupedReceipts = groupReceiptsByDate(receipts);
            
            // Add date headers and receipts to items list
            for (Map.Entry<String, List<Receipt>> entry : groupedReceipts.entrySet()) {
                items.add(entry.getKey()); // Add date header
                items.addAll(entry.getValue()); // Add receipts for that date
            }
        }
        notifyDataSetChanged();
    }

    private Map<String, List<Receipt>> groupReceiptsByDate(List<Receipt> receipts) {
        Map<String, List<Receipt>> grouped = new LinkedHashMap<>();
        SimpleDateFormat headerFormat = new SimpleDateFormat("MMMM dd yyyy", Locale.US);
        
        for (Receipt receipt : receipts) {
            String dateKey;
            long timestamp = 0;
            
            if (receipt.getReceipt().getDateTimestamp() > 0) {
                timestamp = receipt.getReceipt().getDateTimestamp();
            } else if (receipt.getDate() > 0) {
                timestamp = receipt.getDate();
            } else {
                timestamp = System.currentTimeMillis();
            }
            
            dateKey = headerFormat.format(new Date(timestamp)).toUpperCase();
            
            if (!grouped.containsKey(dateKey)) {
                grouped.put(dateKey, new ArrayList<>());
            }
            grouped.get(dateKey).add(receipt);
        }
        
        return grouped;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        return item instanceof String ? TYPE_DATE_HEADER : TYPE_RECEIPT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_DATE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_receipt_date_header, parent, false);
            return new DateHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_receipt, parent, false);
            return new ReceiptViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof DateHeaderViewHolder) {
            String dateHeader = (String) items.get(position);
            ((DateHeaderViewHolder) holder).bind(dateHeader);
        } else if (holder instanceof ReceiptViewHolder) {
            Receipt receipt = (Receipt) items.get(position);
            ((ReceiptViewHolder) holder).bind(receipt);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class DateHeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView dateHeader;

        public DateHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            dateHeader = (TextView) itemView;
        }

        public void bind(String date) {
            dateHeader.setText(date);
        }
    }

    class ReceiptViewHolder extends RecyclerView.ViewHolder {
        private final ImageView receiptImage;
        private final TextView storeName;
        private final TextView categoryTag;
        private final TextView receiptTotal;

        public ReceiptViewHolder(@NonNull View itemView) {
            super(itemView);
            receiptImage = itemView.findViewById(R.id.receiptImage);
            storeName = itemView.findViewById(R.id.storeName);
            categoryTag = itemView.findViewById(R.id.categoryTag);
            receiptTotal = itemView.findViewById(R.id.receiptTotal);
        }

        public void bind(Receipt receipt) {
            String storeNameText = "Unknown Store";
            if (receipt.getStore() != null && receipt.getStore().getName() != null && !receipt.getStore().getName().isEmpty()) {
                storeNameText = receipt.getStore().getName();
            } else if (receipt.getStoreName() != null && !receipt.getStoreName().isEmpty()) {
                storeNameText = receipt.getStoreName();
            }
            storeName.setText(storeNameText);

            String category = getCategoryFromReceipt(receipt);
            if (category != null && !category.isEmpty() && !category.equals("null")) {
                categoryTag.setVisibility(View.VISIBLE);
                categoryTag.setText(category);
            } else {
                categoryTag.setVisibility(View.GONE);
            }

            String totalText = "$0.00";
            if (receipt.getReceipt() != null) {
                double total = receipt.getReceipt().getTotal();
                String currency = receipt.getReceipt().getCurrency();
                if (currency != null && !currency.isEmpty()) {
                    totalText = formatCurrency(total, currency);
                } else {
                    totalText = formatCurrency(total, "USD");
                }
            } else if (receipt.getTotal() > 0) {
                totalText = formatCurrency(receipt.getTotal(), "USD");
            }
            receiptTotal.setText(totalText);

            if (receipt.getImageUrl() != null && !receipt.getImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(receipt.getImageUrl())
                        .apply(RequestOptions.centerCropTransform()
                                .placeholder(R.drawable.dashboard)
                                .error(R.drawable.dashboard))
                        .into(receiptImage);
            } else {
                receiptImage.setImageResource(R.drawable.dashboard);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReceiptClick(receipt);
                }
            });
        }

        private String getCategoryFromReceipt(Receipt receipt) {
            // First try to get category from receipt level
            if (receipt.getReceipt() != null) {
                String receiptCategory = receipt.getReceipt().getCategory();
                if (receiptCategory != null && !receiptCategory.trim().isEmpty() && !receiptCategory.equals("null")) {
                    Log.d("ReceiptAdapter", "Found category from receipt: " + receiptCategory);
                    return receiptCategory.trim();
                } else {
                    Log.d("ReceiptAdapter", "Receipt category is null/empty: " + receiptCategory);
                }
            } else {
                Log.d("ReceiptAdapter", "Receipt.getReceipt() is null");
            }
            
            // Fallback: get category from first item if available
            if (receipt.getItems() != null && !receipt.getItems().isEmpty()) {
                for (ReceiptItem item : receipt.getItems()) {
                    if (item.getCategory() != null && !item.getCategory().isEmpty() && !item.getCategory().equals("null")) {
                        Log.d("ReceiptAdapter", "Found category from item: " + item.getCategory());
                        return item.getCategory();
                    }
                }
            }
            
            Log.d("ReceiptAdapter", "No category found for receipt");
            return null;
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
                case "KRW": return "₩";
                default: return currency + " ";
            }
        }
    }
}
