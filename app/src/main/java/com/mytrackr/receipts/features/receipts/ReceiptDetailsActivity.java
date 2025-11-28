package com.mytrackr.receipts.features.receipts;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.data.repository.ReceiptRepository;
import com.mytrackr.receipts.features.receipts.adapters.ReceiptItemAdapter;
import com.mytrackr.receipts.databinding.ActivityReceiptDetailsBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class ReceiptDetailsActivity extends AppCompatActivity {
    private static final String TAG = "ReceiptDetailsActivity";
    public static final String EXTRA_RECEIPT = "receipt";
    
    private Receipt receipt;
    private ImageView receiptPreviewImage;
    private TextView merchantName;
    private TextView receiptDate;
    private TextView receiptCategory;
    private TextView paymentMethod;
    private TextView subtotal;
    private TextView tax;
    private TextView total;
    private RecyclerView itemsRecyclerView;
    private ReceiptItemAdapter itemsAdapter;
    private Button btnViewReceipt;
    private Button btnDeleteReceipt;
    
    private ReceiptRepository receiptRepository;
    private ActivityReceiptDetailsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReceiptDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        receipt = (Receipt) getIntent().getSerializableExtra(EXTRA_RECEIPT);
        if (receipt == null) {
            Log.e(TAG, "Receipt is null, finishing activity");
            Toast.makeText(this, "Receipt not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        receiptRepository = new ReceiptRepository();
        
        setupToolbar();
        initViews();
        populateReceiptDetails();
        setupClickListeners();

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
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
        Toolbar toolbar = binding.toolbar.toolbar;
        toolbar.setTitle("");
        binding.toolbar.toolbarTitle.setText(R.string.receipt_details);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }
    
    private void initViews() {
        receiptPreviewImage = binding.receiptPreviewImage;
        merchantName = binding.merchantName;
        receiptDate = binding.receiptDate;
        receiptCategory = binding.receiptCategory;
        paymentMethod = binding.paymentMethod;
        subtotal = binding.subtotal;
        tax = binding.tax;
        total = binding.total;
        itemsRecyclerView = binding.itemsRecyclerView;
        itemsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        btnViewReceipt = binding.btnViewReceipt;
        btnDeleteReceipt = binding.btnDeleteReceipt;
    }
    
    private void populateReceiptDetails() {
        if (receipt.getImageUrl() != null && !receipt.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(receipt.getImageUrl())
                    .apply(RequestOptions.fitCenterTransform()
                            .placeholder(R.drawable.ic_receipt_icon)
                            .error(R.drawable.ic_receipt_icon))
                    .into(receiptPreviewImage);
        } else {
            receiptPreviewImage.setImageResource(R.drawable.ic_receipt_icon);
        }
        
        // Merchant name
        if (receipt.getStore() != null && receipt.getStore().getName() != null) {
            merchantName.setText(receipt.getStore().getName());
        } else {
            merchantName.setText("-");
        }
        
        // Date
        if (receipt.getReceipt() != null) {
            String dateStr = receipt.getReceipt().getDate();
            if (dateStr != null && !dateStr.isEmpty()) {
                receiptDate.setText(formatDate(dateStr));
            } else {
                // Fallback to timestamp
                long timestamp = receipt.getReceipt().getDateTimestamp();
                if (timestamp > 0) {
                    receiptDate.setText(formatTimestamp(timestamp));
                } else {
                    receiptDate.setText("-");
                }
            }
        } else {
            receiptDate.setText("-");
        }
        
        populateItems();
        
        populatePriceBreakdown();
        
        // Category
        if (receipt.getReceipt() != null && receipt.getReceipt().getCategory() != null 
            && !receipt.getReceipt().getCategory().isEmpty()) {
            receiptCategory.setText(receipt.getReceipt().getCategory());
        } else {
            receiptCategory.setText("-");
        }
        
        if (receipt.getReceipt() != null && receipt.getReceipt().getPaymentMethod() != null
            && !receipt.getReceipt().getPaymentMethod().isEmpty()) {
            String method = receipt.getReceipt().getPaymentMethod();
            if (receipt.getReceipt().getCardLast4() != null && !receipt.getReceipt().getCardLast4().isEmpty()) {
                method += " •••• " + receipt.getReceipt().getCardLast4();
            }
            paymentMethod.setText(method);
        } else {
            paymentMethod.setText("-");
        }
    }
    
    private void populateItems() {
        if (receipt.getItems() == null || receipt.getItems().isEmpty()) {
            itemsRecyclerView.setVisibility(View.GONE);
            return;
        }
        
        String currency = receipt.getReceipt() != null && receipt.getReceipt().getCurrency() != null 
            ? receipt.getReceipt().getCurrency() : "USD";
        
        if (receipt.getItems() != null && !receipt.getItems().isEmpty()) {
            itemsAdapter = new ReceiptItemAdapter(receipt.getItems(), currency);
            itemsRecyclerView.setAdapter(itemsAdapter);
            itemsRecyclerView.setVisibility(View.VISIBLE);
        } else {
            itemsRecyclerView.setVisibility(View.GONE);
        }
    }
    
    private void populatePriceBreakdown() {
        String currency = receipt.getReceipt() != null && receipt.getReceipt().getCurrency() != null 
            ? receipt.getReceipt().getCurrency() : "USD";
        
        if (receipt.getReceipt() != null) {
            double subtotalValue = receipt.getReceipt().getSubtotal();
            if (subtotalValue > 0) {
                subtotal.setText(formatCurrency(subtotalValue, currency));
            } else {
                subtotal.setText("-");
            }
            
            double taxValue = receipt.getReceipt().getTax();
            if (taxValue > 0) {
                tax.setText(formatCurrency(taxValue, currency));
            } else {
                tax.setText("-");
            }
            
            double totalValue = receipt.getReceipt().getTotal();
            if (totalValue > 0) {
                total.setText(formatCurrency(totalValue, currency));
            } else {
                total.setText("-");
            }
        } else {
            subtotal.setText("-");
            tax.setText("-");
            total.setText("-");
        }
    }
    
    private void setupClickListeners() {
        btnViewReceipt.setOnClickListener(v -> {
            if (receipt.getImageUrl() != null && !receipt.getImageUrl().isEmpty()) {
                Intent intent = new Intent(this, ViewReceiptImageActivity.class);
                intent.putExtra(ViewReceiptImageActivity.EXTRA_IMAGE_URL, receipt.getImageUrl());
                startActivity(intent);
            } else {
                Toast.makeText(this, "Receipt image not available", Toast.LENGTH_SHORT).show();
            }
        });
        
        btnDeleteReceipt.setOnClickListener(v -> showDeleteConfirmationDialog());
    }
    
    private void showDeleteConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Receipt")
                .setMessage("Are you sure you want to delete this receipt? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteReceipt())
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void deleteReceipt() {
        if (receipt.getId() == null || receipt.getId().isEmpty()) {
            Toast.makeText(this, "Receipt ID not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        btnDeleteReceipt.setEnabled(false);
        receiptRepository.deleteReceipt(receipt.getId(), new ReceiptRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ReceiptDetailsActivity.this, "Receipt deleted", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    btnDeleteReceipt.setEnabled(true);
                    Log.e(TAG, "Failed to delete receipt", e);
                    Toast.makeText(ReceiptDetailsActivity.this, "Failed to delete receipt: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private String formatDate(String dateStr) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);
            Date date = inputFormat.parse(dateStr);
            if (date != null) {
                return outputFormat.format(date);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to format date: " + dateStr, e);
        }
        return dateStr;
    }
    
    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);
        return sdf.format(new Date(timestamp));
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
    
    public static Intent createIntent(android.content.Context context, Receipt receipt) {
        Intent intent = new Intent(context, ReceiptDetailsActivity.class);
        intent.putExtra(EXTRA_RECEIPT, receipt);
        return intent;
    }
}

