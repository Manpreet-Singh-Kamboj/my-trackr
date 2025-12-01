package com.mytrackr.receipts.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.data.models.ReceiptItem;
import com.mytrackr.receipts.data.repository.ReceiptRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HomeViewModel extends AndroidViewModel {

    private final ReceiptRepository receiptRepository;
    private final MutableLiveData<List<Receipt>> receipts = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Integer> receiptsCount = new MutableLiveData<>();

    public HomeViewModel(@NonNull Application application) {
        super(application);
        receiptRepository = new ReceiptRepository();
        FirebaseCrashlytics.getInstance().log("D/HomeViewModel: HomeViewModel initialized");
    }

    public LiveData<List<Receipt>> getReceipts() {
        return receipts;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Integer> getReceiptsCount() {
        return receiptsCount;
    }

    public void loadReceipts() {
        isLoading.setValue(true);
        errorMessage.setValue(null);
        FirebaseCrashlytics.getInstance().log("D/HomeViewModel: Loading receipts");

        receiptRepository.fetchReceiptsForCurrentUser(task -> {
            isLoading.postValue(false);

            if (task.isSuccessful()) {
                QuerySnapshot querySnapshot = task.getResult();
                List<Receipt> receiptsList = new ArrayList<>();

                if (querySnapshot != null && !querySnapshot.isEmpty()) {
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        try {
                            Receipt receipt = parseReceiptFromDocument(document);
                            if (receipt != null) {
                                receipt.setId(document.getId());
                                receiptsList.add(receipt);
                            }
                        } catch (Exception e) {
                            FirebaseCrashlytics.getInstance().log("E/HomeViewModel: Error parsing receipt document: " + document.getId());
                            FirebaseCrashlytics.getInstance().recordException(e);
                        }
                    }
                }

                receipts.postValue(receiptsList);
                receiptsCount.postValue(receiptsList.size());
                FirebaseCrashlytics.getInstance().log("D/HomeViewModel: Loaded " + receiptsList.size() + " receipts");
            } else {
                FirebaseCrashlytics.getInstance().log("E/HomeViewModel: Error fetching receipts");
                if (task.getException() != null) {
                    FirebaseCrashlytics.getInstance().recordException(task.getException());
                }
                String error = task.getException() != null
                        ? task.getException().getMessage()
                        : "Failed to load receipts";
                errorMessage.postValue(error);
                receipts.postValue(new ArrayList<>());
                receiptsCount.postValue(0);
            }
        });
    }

    public void refreshReceipts() {
        FirebaseCrashlytics.getInstance().log("D/HomeViewModel: Refreshing receipts");
        loadReceipts();
    }

    private Receipt parseReceiptFromDocument(DocumentSnapshot document) {
        try {
            Receipt receipt = new Receipt();
            Map<String, Object> data = document.getData();
            if (data == null) return null;

            if (data.containsKey("store")) {
                Map<String, Object> storeMap = (Map<String, Object>) data.get("store");
                Receipt.StoreInfo store = new Receipt.StoreInfo();
                if (storeMap != null) {
                    if (storeMap.containsKey("name")) store.setName((String) storeMap.get("name"));
                    if (storeMap.containsKey("address")) store.setAddress((String) storeMap.get("address"));
                    if (storeMap.containsKey("phone")) store.setPhone((String) storeMap.get("phone"));
                    if (storeMap.containsKey("website")) store.setWebsite((String) storeMap.get("website"));
                }
                receipt.setStore(store);
            }

            if (data.containsKey("receipt")) {
                Map<String, Object> receiptMap = (Map<String, Object>) data.get("receipt");
                Receipt.ReceiptInfo receiptInfo = new Receipt.ReceiptInfo();
                if (receiptMap != null) {
                    if (receiptMap.containsKey("receiptId"))
                        receiptInfo.setReceiptId((String) receiptMap.get("receiptId"));
                    if (receiptMap.containsKey("date")) receiptInfo.setDate((String) receiptMap.get("date"));
                    if (receiptMap.containsKey("time")) receiptInfo.setTime((String) receiptMap.get("time"));
                    if (receiptMap.containsKey("currency"))
                        receiptInfo.setCurrency((String) receiptMap.get("currency"));
                    if (receiptMap.containsKey("paymentMethod"))
                        receiptInfo.setPaymentMethod((String) receiptMap.get("paymentMethod"));
                    if (receiptMap.containsKey("cardLast4"))
                        receiptInfo.setCardLast4((String) receiptMap.get("cardLast4"));
                    if (receiptMap.containsKey("category")) {
                        Object categoryObj = receiptMap.get("category");
                        if (categoryObj != null) {
                            String category = categoryObj.toString().trim();
                            if (!category.isEmpty() && !category.equals("null")) {
                                receiptInfo.setCategory(category);
                            }
                        }
                    }
                    if (receiptMap.containsKey("subtotal")) {
                        Object subtotal = receiptMap.get("subtotal");
                        if (subtotal instanceof Number)
                            receiptInfo.setSubtotal(((Number) subtotal).doubleValue());
                    }
                    if (receiptMap.containsKey("tax")) {
                        Object tax = receiptMap.get("tax");
                        if (tax instanceof Number) receiptInfo.setTax(((Number) tax).doubleValue());
                    }
                    if (receiptMap.containsKey("total")) {
                        Object total = receiptMap.get("total");
                        if (total instanceof Number) receiptInfo.setTotal(((Number) total).doubleValue());
                    }
                    if (receiptMap.containsKey("dateTimestamp")) {
                        Object dateTimestamp = receiptMap.get("dateTimestamp");
                        if (dateTimestamp instanceof Number)
                            receiptInfo.setDateTimestamp(((Number) dateTimestamp).longValue());
                    }
                    if (receiptMap.containsKey("receiptDateTimestamp")) {
                        Object receiptDateTimestamp = receiptMap.get("receiptDateTimestamp");
                        if (receiptDateTimestamp instanceof Number)
                            receiptInfo.setReceiptDateTimestamp(((Number) receiptDateTimestamp).longValue());
                    }
                    if (receiptMap.containsKey("customNotificationTimestamp")) {
                        Object customNotificationTimestamp = receiptMap.get("customNotificationTimestamp");
                        if (customNotificationTimestamp instanceof Number)
                            receiptInfo.setCustomNotificationTimestamp(((Number) customNotificationTimestamp).longValue());
                    }
                }
                receipt.setReceipt(receiptInfo);
            }

            if (data.containsKey("items")) {
                List<Map<String, Object>> itemsMap = (List<Map<String, Object>>) data.get("items");
                if (itemsMap != null) {
                    List<ReceiptItem> items = new ArrayList<>();
                    for (Map<String, Object> itemMap : itemsMap) {
                        ReceiptItem item = new ReceiptItem();
                        if (itemMap.containsKey("name")) item.setName((String) itemMap.get("name"));
                        if (itemMap.containsKey("quantity")) {
                            Object qty = itemMap.get("quantity");
                            if (qty instanceof Number) item.setQuantity(((Number) qty).intValue());
                        }
                        if (itemMap.containsKey("unitPrice")) {
                            Object price = itemMap.get("unitPrice");
                            if (price instanceof Number) item.setUnitPrice(((Number) price).doubleValue());
                        }
                        if (itemMap.containsKey("totalPrice")) {
                            Object total = itemMap.get("totalPrice");
                            if (total instanceof Number) item.setTotalPrice(((Number) total).doubleValue());
                        }
                        if (itemMap.containsKey("category"))
                            item.setCategory((String) itemMap.get("category"));
                        items.add(item);
                    }
                    receipt.setItems(items);
                }
            }

            if (data.containsKey("additional")) {
                Map<String, Object> additionalMap = (Map<String, Object>) data.get("additional");
                Receipt.AdditionalInfo additional = new Receipt.AdditionalInfo();
                if (additionalMap != null) {
                    if (additionalMap.containsKey("taxNumber"))
                        additional.setTaxNumber((String) additionalMap.get("taxNumber"));
                    if (additionalMap.containsKey("cashier"))
                        additional.setCashier((String) additionalMap.get("cashier"));
                    if (additionalMap.containsKey("storeNumber"))
                        additional.setStoreNumber((String) additionalMap.get("storeNumber"));
                    if (additionalMap.containsKey("notes"))
                        additional.setNotes((String) additionalMap.get("notes"));
                }
                receipt.setAdditional(additional);
            }

            if (data.containsKey("metadata")) {
                Map<String, Object> metadataMap = (Map<String, Object>) data.get("metadata");
                Receipt.ReceiptMetadata metadata = new Receipt.ReceiptMetadata();
                if (metadataMap != null) {
                    if (metadataMap.containsKey("ocrText"))
                        metadata.setOcrText((String) metadataMap.get("ocrText"));
                    if (metadataMap.containsKey("processedBy"))
                        metadata.setProcessedBy((String) metadataMap.get("processedBy"));
                    if (metadataMap.containsKey("uploadedAt"))
                        metadata.setUploadedAt((String) metadataMap.get("uploadedAt"));
                    if (metadataMap.containsKey("userId"))
                        metadata.setUserId((String) metadataMap.get("userId"));
                }
                receipt.setMetadata(metadata);
            }

            if (data.containsKey("imageUrl")) {
                receipt.setImageUrl((String) data.get("imageUrl"));
            }

            if (data.containsKey("cloudinaryPublicId")) {
                receipt.setCloudinaryPublicId((String) data.get("cloudinaryPublicId"));
            }

            return receipt;
        } catch (Exception e) {
            FirebaseCrashlytics.getInstance().log("E/HomeViewModel: Error parsing receipt from document");
            FirebaseCrashlytics.getInstance().recordException(e);
            return null;
        }
    }
}
