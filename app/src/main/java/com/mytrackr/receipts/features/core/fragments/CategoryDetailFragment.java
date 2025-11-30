package com.mytrackr.receipts.features.core.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.data.models.ReceiptItem;
import com.mytrackr.receipts.data.repository.ReceiptRepository;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.mytrackr.receipts.data.model.Transaction;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CategoryDetailFragment extends Fragment {

    private static final String ARG_CATEGORY_NAME = "CATEGORY_NAME";
    private static final String ARG_CATEGORY_COLOR = "CATEGORY_COLOR";

    private String categoryName;
    private String categoryColor;

    private RecyclerView rvDetails;
    private DetailAdapter adapter;
    private List<DetailItem> detailList = new ArrayList<>();

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public static CategoryDetailFragment newInstance(String categoryName, String categoryColor) {
        CategoryDetailFragment fragment = new CategoryDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY_NAME, categoryName);
        args.putString(ARG_CATEGORY_COLOR, categoryColor);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            categoryName = getArguments().getString(ARG_CATEGORY_NAME);
            categoryColor = getArguments().getString(ARG_CATEGORY_COLOR);
        }
        if (categoryName == null) categoryName = "Details";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_category_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(categoryName + " Expenses");
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left);

        toolbar.setNavigationOnClickListener(v -> {
            if (getParentFragmentManager() != null) {
                getParentFragmentManager().popBackStack();
            }
        });

        if (categoryColor != null) {
            try {
                toolbar.setBackgroundColor(Color.parseColor(categoryColor));
            } catch (Exception e) {
                Log.e("CategoryDetail", "Invalid color format", e);
            }
        }

        rvDetails = view.findViewById(R.id.rvDetails);
        rvDetails.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DetailAdapter(detailList);
        rvDetails.setAdapter(adapter);

        loadData();
    }

    private void loadData() {
        // 1. Load Receipts
        ReceiptRepository.getInstance().fetchReceiptsForCurrentUser(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                detailList.clear();

                for (QueryDocumentSnapshot document : task.getResult()) {
                    try {
                        Receipt receipt = document.toObject(Receipt.class);
                        String storeName = "Unknown Store";
                        String dateStr = "Unknown Date";

                        if (receipt.getStore() != null && receipt.getStore().getName() != null) {
                            storeName = receipt.getStore().getName();
                        }

                        long timestamp = 0;
                        if (receipt.getReceipt() != null) {
                            timestamp = receipt.getReceipt().getReceiptDateTimestamp();
                            if (timestamp == 0) timestamp = receipt.getReceipt().getDateTimestamp();

                            if (timestamp > 0) {
                                dateStr = dateFormat.format(new Date(timestamp));
                            } else if (receipt.getReceipt().getDate() != null) {
                                dateStr = receipt.getReceipt().getDate();
                            }
                        }

                        // Filter Items
                        if (receipt.getItems() != null) {
                            for (ReceiptItem item : receipt.getItems()) {
                                String cat = item.getCategory();

                                boolean isMatch = false;
                                if ("Other".equalsIgnoreCase(categoryName)) {
                                    isMatch = "Other".equalsIgnoreCase(cat);
                                } else if (cat != null) {
                                    isMatch = categoryName.equalsIgnoreCase(cat);
                                }

                                if (isMatch) {
                                    Double price = item.getEffectiveTotalPrice();
                                    detailList.add(new DetailItem(
                                            item.getName(),
                                            storeName,
                                            dateStr,
                                            price != null ? price : 0.0,
                                            timestamp // Store timestamp for sorting if needed
                                    ));
                                }
                            }
                        }

                        // Filter Tax
                        if ("Tax".equalsIgnoreCase(categoryName)) {
                            if (receipt.getReceipt() != null && receipt.getReceipt().getTax() > 0) {
                                detailList.add(new DetailItem(
                                        "Tax",
                                        storeName,
                                        dateStr,
                                        receipt.getReceipt().getTax(),
                                        timestamp
                                ));
                            }
                        }

                    } catch (Exception e) {
                        Log.e("CategoryDetail", "Error parsing receipt", e);
                    }
                }

                // 2. Load Transactions (Only if category is "Other")
                if ("Other".equalsIgnoreCase(categoryName)) {
                    loadTransactionsAndMerge();
                } else {
                    adapter.notifyDataSetChanged();
                    checkEmpty();
                }

            } else {
                Toast.makeText(getContext(), "Failed to load data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadTransactionsAndMerge() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("transactions")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        try {
                            Transaction transaction = doc.toObject(Transaction.class);
                            if (transaction.isExpense()) {
                                String dateStr = dateFormat.format(new Date(transaction.getTimestamp()));
                                detailList.add(new DetailItem(
                                        transaction.getDescription(),
                                        "Manual Transaction",
                                        dateStr,
                                        transaction.getAmount(),
                                        transaction.getTimestamp()
                                ));
                            }
                        } catch (Exception e) {
                            Log.e("CategoryDetail", "Transaction Parse error", e);
                        }
                    }
                    // Notify update after both sources are loaded
                    adapter.notifyDataSetChanged();
                    checkEmpty();
                })
                .addOnFailureListener(e -> {
                    Log.e("CategoryDetail", "Failed to load transactions", e);
                    adapter.notifyDataSetChanged();
                    checkEmpty();
                });
    }

    private void checkEmpty() {
        if (detailList.isEmpty() && getContext() != null) {
            Toast.makeText(getContext(), "No records found for " + categoryName, Toast.LENGTH_SHORT).show();
        }
    }

    private static class DetailItem {
        String itemName;
        String storeName;
        String date;
        double amount;
        long timestamp; // 用于排序

        public DetailItem(String itemName, String storeName, String date, double amount, long timestamp) {
            this.itemName = itemName;
            this.storeName = storeName;
            this.date = date;
            this.amount = amount;
            this.timestamp = timestamp;
        }
    }

    private class DetailAdapter extends RecyclerView.Adapter<DetailAdapter.ViewHolder> {
        private List<DetailItem> items;

        public DetailAdapter(List<DetailItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_detail_record, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DetailItem item = items.get(position);
            holder.tvItemName.setText(item.itemName);
            holder.tvStoreInfo.setText(item.date); // Only show date as requested previously
            holder.tvAmount.setText(String.format("$%.2f", item.amount));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvItemName, tvStoreInfo, tvAmount;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvItemName = itemView.findViewById(R.id.tvItemName);
                tvStoreInfo = itemView.findViewById(R.id.tvStoreInfo);
                tvAmount = itemView.findViewById(R.id.tvAmount);
            }
        }
    }
}