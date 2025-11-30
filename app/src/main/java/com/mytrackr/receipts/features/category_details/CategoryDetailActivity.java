package com.mytrackr.receipts.features.category_details;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.model.Transaction;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.data.models.ReceiptItem;
import com.mytrackr.receipts.data.repository.ReceiptRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CategoryDetailActivity extends AppCompatActivity {

    private static final String EXTRA_CATEGORY_NAME = "CATEGORY_NAME";
    private static final String EXTRA_CATEGORY_COLOR = "CATEGORY_COLOR";

    private String categoryName;
    private String categoryColor;

    private RecyclerView rvDetails;
    private DetailAdapter adapter;
    private List<DetailItem> detailList = new ArrayList<>();

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public static Intent newIntent(Context context, String categoryName, String categoryColor) {
        Intent intent = new Intent(context, CategoryDetailActivity.class);
        intent.putExtra(EXTRA_CATEGORY_NAME, categoryName);
        intent.putExtra(EXTRA_CATEGORY_COLOR, categoryColor);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_detail);

        // Get intent extras
        categoryName = getIntent().getStringExtra(EXTRA_CATEGORY_NAME);
        categoryColor = getIntent().getStringExtra(EXTRA_CATEGORY_COLOR);
        if (categoryName == null) categoryName = "Details";

        setupToolbar();
        initViews();
        loadData();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainContent), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            layoutParams.leftMargin = insets.left;
            layoutParams.topMargin = insets.top;
            layoutParams.rightMargin = insets.right;
            layoutParams.bottomMargin = insets.bottom;
            v.setLayoutParams(layoutParams);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(""); // Clear default title
        }

        // Set title using the TextView in toolbar_layout
        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        if (toolbarTitle != null) {
            toolbarTitle.setText(getString(R.string.expenses));
        }
    }

    private void initViews() {
        rvDetails = findViewById(R.id.rvDetails);
        rvDetails.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DetailAdapter(detailList);
        rvDetails.setAdapter(adapter);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadData() {
        // 1. Load Receipts
        ReceiptRepository.getInstance().fetchReceiptsForCurrentUser(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                detailList.clear();

                for (QueryDocumentSnapshot document : task.getResult()) {
                    try {
                        Receipt receipt = document.toObject(Receipt.class);
                        String storeName = getString(R.string.unknown_store);
                        String dateStr = getString(R.string.unknown_date);

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
                Toast.makeText(this, getString(R.string.failed_to_load_data), Toast.LENGTH_SHORT).show();
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
                                        getString(R.string.manual_transaction),
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
        if (detailList.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_records_found_for, categoryName), Toast.LENGTH_SHORT).show();
        }
    }

    private static class DetailItem {
        String itemName;
        String storeName;
        String date;
        double amount;
        long timestamp; // For sorting

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

