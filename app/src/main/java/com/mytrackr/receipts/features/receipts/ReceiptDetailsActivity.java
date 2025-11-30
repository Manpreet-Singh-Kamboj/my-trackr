package com.mytrackr.receipts.features.receipts;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.mytrackr.receipts.ui.adapter.ReceiptItemAdapter;
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
    private TextView notificationDate;
    private Button btnSetNotificationDate;

    private ReceiptRepository receiptRepository;
    private ActivityReceiptDetailsBinding binding;
    private LinearLayout itemsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReceiptDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        receipt = (Receipt) getIntent().getSerializableExtra(EXTRA_RECEIPT);
        if (receipt == null || receipt.getId() == null) {
            Log.e(TAG, "Receipt is null or missing ID, finishing activity");
            Toast.makeText(this, getString(R.string.receipt_not_found), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        receiptRepository = new ReceiptRepository();

        setupToolbar();
        initViews();

        if (isReceiptIncomplete(receipt)) {
            Log.d(TAG, "Receipt data incomplete, fetching from Firestore...");
            fetchReceiptFromFirestore(receipt.getId());
        } else {
            populateReceiptDetails();
        }

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
        notificationDate = binding.notificationDate;
        btnSetNotificationDate = binding.btnSetNotificationDate;
        itemsContainer = binding.itemsContainer;
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

        if (receipt.getStore() != null && receipt.getStore().getName() != null && !receipt.getStore().getName().isEmpty()) {
            merchantName.setText(receipt.getStore().getName());
        } else {
            merchantName.setText("-");
        }

        if (receipt.getReceipt() != null) {
            String dateStr = receipt.getReceipt().getDate();
            if (dateStr != null && !dateStr.isEmpty()) {
                receiptDate.setText(formatDate(dateStr));
            } else {
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

        populateNotificationDate();
    }

    private void populateNotificationDate() {
        if (receipt.getReceipt() == null) {
            notificationDate.setText("-");
            return;
        }

        long customNotificationTimestamp = receipt.getReceipt().getCustomNotificationTimestamp();
        if (customNotificationTimestamp > 0) {
            notificationDate.setText(formatTimestamp(customNotificationTimestamp) + " (Custom)");
        } else {
            long receiptDate = receipt.getReceipt().getReceiptDateTimestamp();
            if (receiptDate == 0) {
                receiptDate = receipt.getReceipt().getDateTimestamp();
            }

            if (receiptDate > 0) {
                com.mytrackr.receipts.utils.NotificationPreferences prefs =
                        new com.mytrackr.receipts.utils.NotificationPreferences(this);
                int replacementDays = prefs.getReplacementDays();
                int notificationDaysBefore = prefs.getNotificationDaysBefore();

                long defaultNotificationTime = receiptDate +
                        ((replacementDays - notificationDaysBefore) * 24 * 60 * 60 * 1000L);
                notificationDate.setText(formatTimestamp(defaultNotificationTime) + " (Default)");
            } else {
                notificationDate.setText("-");
            }
        }
    }

    private void populateItems() {
        if (receipt.getItems() == null || receipt.getItems().isEmpty()) {
            itemsContainer.setVisibility(View.GONE);
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
            Receipt.ReceiptInfo info = receipt.getReceipt();

            double subtotalValue = info.getSubtotal();
            subtotal.setText(formatCurrency(subtotalValue, currency));
            Log.d(TAG, "Subtotal: " + subtotalValue);

            double taxValue = info.getTax();
            tax.setText(formatCurrency(taxValue, currency));
            Log.d(TAG, "Tax: " + taxValue);

            double totalValue = info.getTotal();
            if (totalValue > 0) {
                total.setText(formatCurrency(totalValue, currency));
            } else {
                double calculatedTotal = subtotalValue + taxValue;
                if (calculatedTotal > 0) {
                    total.setText(formatCurrency(calculatedTotal, currency));
                    Log.d(TAG, "Total was 0, using calculated total: " + calculatedTotal);
                } else {
                    total.setText("-");
                }
            }
            Log.d(TAG, "Total: " + totalValue);
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
                Toast.makeText(this, getString(R.string.receipt_image_not_available), Toast.LENGTH_SHORT).show();
            }
        });

        btnDeleteReceipt.setOnClickListener(v -> showDeleteConfirmationDialog());

        btnSetNotificationDate.setOnClickListener(v -> showNotificationDatePicker());
    }

    private void showNotificationDatePicker() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();

        long initialDate = receipt.getReceipt().getCustomNotificationTimestamp();
        if (initialDate == 0) {
            long receiptDate = receipt.getReceipt().getReceiptDateTimestamp();
            if (receiptDate == 0) {
                receiptDate = receipt.getReceipt().getDateTimestamp();
            }
            if (receiptDate > 0) {
                com.mytrackr.receipts.utils.NotificationPreferences prefs =
                        new com.mytrackr.receipts.utils.NotificationPreferences(this);
                int replacementDays = prefs.getReplacementDays();
                int notificationDaysBefore = prefs.getNotificationDaysBefore();
                initialDate = receiptDate + ((replacementDays - notificationDaysBefore) * 24 * 60 * 60 * 1000L);
            } else {
                initialDate = System.currentTimeMillis();
            }
        }

        calendar.setTimeInMillis(initialDate);

        android.app.DatePickerDialog datePickerDialog = new android.app.DatePickerDialog(
                this,
                android.R.style.Theme_Material_Dialog,
                (view, year, month, dayOfMonth) -> {
                    java.util.Calendar selectedCalendar = java.util.Calendar.getInstance();
                    selectedCalendar.set(year, month, dayOfMonth);

                    android.app.TimePickerDialog timePickerDialog = new android.app.TimePickerDialog(
                            this,
                            android.R.style.Theme_Material_Dialog,
                            (timeView, hourOfDay, minute) -> {
                                selectedCalendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay);
                                selectedCalendar.set(java.util.Calendar.MINUTE, minute);
                                selectedCalendar.set(java.util.Calendar.SECOND, 0);
                                selectedCalendar.set(java.util.Calendar.MILLISECOND, 0);

                                long customTimestamp = selectedCalendar.getTimeInMillis();
                                saveCustomNotificationDate(customTimestamp);
                            },
                            calendar.get(java.util.Calendar.HOUR_OF_DAY),
                            calendar.get(java.util.Calendar.MINUTE),
                            android.text.format.DateFormat.is24HourFormat(this)
                    );
                    timePickerDialog.setTitle(R.string.select_reminder_time);
                    timePickerDialog.show();
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.setTitle(R.string.select_reminder_date);
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePickerDialog.show();
    }

    private void saveCustomNotificationDate(long customTimestamp) {
        if (receipt.getReceipt() == null) {
            receipt.setReceipt(new Receipt.ReceiptInfo());
        }

        receipt.getReceipt().setCustomNotificationTimestamp(customTimestamp);

        if (receipt.getId() != null && !receipt.getId().isEmpty()) {
            java.util.Map<String, Object> updateMap = new java.util.HashMap<>();
            updateMap.put("receipt.customNotificationTimestamp", customTimestamp);

            com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
            String userId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "anonymous";

            long originalDateTimestamp = receipt.getReceipt().getDateTimestamp();
            long originalReceiptDateTimestamp = receipt.getReceipt().getReceiptDateTimestamp();

            Log.d(TAG, "Updating customNotificationTimestamp for receipt " + receipt.getId() + " to: " + customTimestamp);
            Log.d(TAG, "BEFORE update - dateTimestamp: " + originalDateTimestamp +
                    ", receiptDateTimestamp: " + originalReceiptDateTimestamp);

            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .collection("receipts")
                    .document(receipt.getId())
                    .update(updateMap)
                    .addOnSuccessListener(aVoid -> {
                        runOnUiThread(() -> {
                            receipt.getReceipt().setCustomNotificationTimestamp(customTimestamp);

                            long currentDateTimestamp = receipt.getReceipt().getDateTimestamp();
                            long currentReceiptDateTimestamp = receipt.getReceipt().getReceiptDateTimestamp();

                            if (currentDateTimestamp != originalDateTimestamp) {
                                Log.e(TAG, "ERROR: dateTimestamp changed from " + originalDateTimestamp +
                                        " to " + currentDateTimestamp + " - restoring original value");
                                receipt.getReceipt().setDateTimestamp(originalDateTimestamp);
                            }
                            if (currentReceiptDateTimestamp != originalReceiptDateTimestamp) {
                                Log.e(TAG, "ERROR: receiptDateTimestamp changed from " + originalReceiptDateTimestamp +
                                        " to " + currentReceiptDateTimestamp + " - restoring original value");
                                receipt.getReceipt().setReceiptDateTimestamp(originalReceiptDateTimestamp);
                            }

                            Log.d(TAG, "AFTER update - dateTimestamp: " + receipt.getReceipt().getDateTimestamp() +
                                    ", receiptDateTimestamp: " + receipt.getReceipt().getReceiptDateTimestamp() +
                                    " (should match BEFORE values)");

                            Toast.makeText(this, getString(R.string.reminder_date_updated), Toast.LENGTH_SHORT).show();
                            populateNotificationDate();

                            com.mytrackr.receipts.utils.NotificationPreferences prefs =
                                    new com.mytrackr.receipts.utils.NotificationPreferences(this);
                            if (prefs.isReplacementReminderEnabled()) {
                                long receiptDate = receipt.getReceipt().getReceiptDateTimestamp();
                                if (receiptDate == 0) {
                                    receiptDate = receipt.getReceipt().getDateTimestamp();
                                }
                                com.mytrackr.receipts.utils.NotificationScheduler.scheduleReceiptReplacementNotification(
                                        this,
                                        receipt.getId(),
                                        receiptDate,
                                        prefs.getReplacementDays(),
                                        prefs.getNotificationDaysBefore(),
                                        customTimestamp
                                );
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to update custom notification date", e);
                        runOnUiThread(() -> {
                            Toast.makeText(this, getString(R.string.failed_to_update_reminder_date), Toast.LENGTH_SHORT).show();
                        });
                    });
        }
    }

    private void showDeleteConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_receipt_title)
                .setMessage(R.string.are_you_sure_you_want_to_delete_this_receipt)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteReceipt())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteReceipt() {
        if (receipt.getId() == null || receipt.getId().isEmpty()) {
            Toast.makeText(this, getString(R.string.receipt_id_not_found), Toast.LENGTH_SHORT).show();
            return;
        }

        btnDeleteReceipt.setEnabled(false);
        receiptRepository.deleteReceipt(receipt.getId(), new ReceiptRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ReceiptDetailsActivity.this, getString(R.string.receipt_deleted), Toast.LENGTH_SHORT).show();
                    // Cancel any scheduled notifications for this receipt
                    com.mytrackr.receipts.utils.NotificationScheduler.cancelReceiptNotification(
                            ReceiptDetailsActivity.this,
                            receipt.getId()
                    );
                    finish();
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    btnDeleteReceipt.setEnabled(true);
                    Log.e(TAG, "Failed to delete receipt", e);
                    Toast.makeText(ReceiptDetailsActivity.this, getString(R.string.failed_to_delete_receipt, e.getMessage()), Toast.LENGTH_LONG).show();
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

    private boolean isReceiptIncomplete(Receipt receipt) {
        if (receipt.getReceipt() == null) {
            Log.d(TAG, "Receipt incomplete: receipt info is null");
            return true;
        }

        if (receipt.getItems() == null || receipt.getItems().isEmpty()) {
            Log.d(TAG, "Receipt incomplete: items are missing or empty");
            return true;
        }

        Receipt.ReceiptInfo info = receipt.getReceipt();

        boolean hasFinancialData = info.getSubtotal() > 0 || info.getTax() > 0 || info.getTotal() > 0;
        if (!hasFinancialData) {
            Log.d(TAG, "Receipt incomplete: missing financial data (subtotal/tax/total all zero)");
            return true;
        }

        boolean hasDate = (info.getDate() != null && !info.getDate().isEmpty()) || info.getDateTimestamp() > 0 || info.getReceiptDateTimestamp() > 0;
        if (!hasDate) {
            Log.d(TAG, "Receipt incomplete: missing date information");
            return true;
        }

        Log.d(TAG, "Receipt appears complete, using provided data");
        return false;
    }

    private void fetchReceiptFromFirestore(String receiptId) {
        Log.d(TAG, "Fetching receipt from Firestore: " + receiptId);
        receiptRepository.fetchReceiptById(receiptId, new ReceiptRepository.FetchReceiptCallback() {
            @Override
            public void onSuccess(Receipt fetchedReceipt) {
                runOnUiThread(() -> {
                    if (fetchedReceipt != null) {
                        if (fetchedReceipt.getReceipt() != null) {
                            Receipt.ReceiptInfo info = fetchedReceipt.getReceipt();
                            Log.d(TAG, "Fetched receipt data:");
                            Log.d(TAG, "  Subtotal: " + info.getSubtotal());
                            Log.d(TAG, "  Tax: " + info.getTax());
                            Log.d(TAG, "  Total: " + info.getTotal());
                            Log.d(TAG, "  Currency: " + info.getCurrency());
                            Log.d(TAG, "  Items count: " + (fetchedReceipt.getItems() != null ? fetchedReceipt.getItems().size() : 0));
                        }

                        receipt = fetchedReceipt;
                        populateReceiptDetails();
                        Log.d(TAG, "Receipt data loaded from Firestore successfully");
                    } else {
                        Log.e(TAG, "Fetched receipt is null");
                        Toast.makeText(ReceiptDetailsActivity.this, getString(R.string.failed_to_load_receipt_data), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Failed to fetch receipt from Firestore", e);
                    populateReceiptDetails();
                    Toast.makeText(ReceiptDetailsActivity.this, getString(R.string.using_cached_receipt_data), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    public static Intent createIntent(android.content.Context context, Receipt receipt) {
        Intent intent = new Intent(context, ReceiptDetailsActivity.class);
        intent.putExtra(EXTRA_RECEIPT, receipt);
        return intent;
    }
}
