package com.mytrackr.receipts.viewmodels;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.model.DetailItem;
import com.mytrackr.receipts.data.model.Transaction;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.data.models.ReceiptItem;
import com.mytrackr.receipts.data.repository.ReceiptRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CategoryDetailViewModel extends AndroidViewModel {
    private final MutableLiveData<List<DetailItem>> detailList = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final ReceiptRepository receiptRepository;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public CategoryDetailViewModel(Application application) {
        super(application);
        receiptRepository = ReceiptRepository.getInstance();
    }

    public MutableLiveData<List<DetailItem>> getDetailList() {
        return detailList;
    }

    public MutableLiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public MutableLiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void loadData(String categoryName) {
        isLoading.setValue(true);
        errorMessage.setValue(null);

        receiptRepository.fetchReceiptsForCurrentUser(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                List<DetailItem> items = new ArrayList<>();

                for (QueryDocumentSnapshot document : task.getResult()) {
                    try {
                        Receipt receipt = document.toObject(Receipt.class);
                        String storeName = getApplication().getString(R.string.unknown_store);
                        String dateStr = getApplication().getString(R.string.unknown_date);

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
                                    items.add(new DetailItem(
                                            item.getName(),
                                            storeName,
                                            dateStr,
                                            price != null ? price : 0.0,
                                            timestamp
                                    ));
                                }
                            }
                        }

                        if ("Tax".equalsIgnoreCase(categoryName)) {
                            if (receipt.getReceipt() != null && receipt.getReceipt().getTax() > 0) {
                                items.add(new DetailItem(
                                        "Tax",
                                        storeName,
                                        dateStr,
                                        receipt.getReceipt().getTax(),
                                        timestamp
                                ));
                            }
                        }

                    } catch (Exception e) {
                        android.util.Log.e("CategoryDetailViewModel", "Error parsing receipt", e);
                    }
                }

                if ("Other".equalsIgnoreCase(categoryName)) {
                    loadTransactionsAndMerge(items, categoryName);
                } else {
                    detailList.postValue(items);
                    isLoading.postValue(false);
                }

            } else {
                errorMessage.postValue(getApplication().getString(R.string.failed_to_load_data));
                isLoading.postValue(false);
            }
        });
    }

    private void loadTransactionsAndMerge(List<DetailItem> items, String categoryName) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() 
                : null;
        if (uid == null) {
            detailList.postValue(items);
            isLoading.postValue(false);
            return;
        }

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
                                items.add(new DetailItem(
                                        transaction.getDescription(),
                                        getApplication().getString(R.string.manual_transaction),
                                        dateStr,
                                        transaction.getAmount(),
                                        transaction.getTimestamp()
                                ));
                            }
                        } catch (Exception e) {
                            android.util.Log.e("CategoryDetailViewModel", "Transaction Parse error", e);
                        }
                    }
                    detailList.postValue(items);
                    isLoading.postValue(false);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("CategoryDetailViewModel", "Failed to load transactions", e);
                    detailList.postValue(items);
                    isLoading.postValue(false);
                });
    }
}
