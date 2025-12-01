package com.mytrackr.receipts.data.repository;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageException;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.data.models.ReceiptItem;
import com.mytrackr.receipts.utils.CloudinaryUtils;
import com.mytrackr.receipts.utils.NotificationPreferences;
import com.mytrackr.receipts.utils.NotificationScheduler;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReceiptRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private static ReceiptRepository repositoryInstance;

    private static final Map<String, Long> lastScheduledTime = new HashMap<>();
    private static final long SCHEDULING_COOLDOWN_MS = 5000; // 5 seconds cooldown

    public static synchronized ReceiptRepository getInstance(){
        if(repositoryInstance == null){
            repositoryInstance = new ReceiptRepository();
            FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Receipt Repository is Initialized");
        }
        return repositoryInstance;
    }


    public interface SaveCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    private Map<String, Object> buildReceiptMap(Receipt receipt, String cloudinaryPublicId) {
        Map<String, Object> map = new HashMap<>();
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";

        if (receipt.getStore() != null) {
            Map<String, Object> storeMap = new HashMap<>();
            Receipt.StoreInfo store = receipt.getStore();
            if (store.getName() != null) storeMap.put("name", store.getName());
            if (store.getAddress() != null) storeMap.put("address", store.getAddress());
            if (store.getPhone() != null) storeMap.put("phone", store.getPhone());
            if (store.getWebsite() != null) storeMap.put("website", store.getWebsite());
            map.put("store", storeMap);
        }

        if (receipt.getReceipt() != null) {
            Map<String, Object> receiptMap = new HashMap<>();
            Receipt.ReceiptInfo receiptInfo = receipt.getReceipt();
            if (receiptInfo.getReceiptId() != null) receiptMap.put("receiptId", receiptInfo.getReceiptId());
            if (receiptInfo.getDate() != null) receiptMap.put("date", receiptInfo.getDate());
            if (receiptInfo.getTime() != null) receiptMap.put("time", receiptInfo.getTime());
            if (receiptInfo.getCurrency() != null) receiptMap.put("currency", receiptInfo.getCurrency());
            if (receiptInfo.getPaymentMethod() != null) receiptMap.put("paymentMethod", receiptInfo.getPaymentMethod());
            if (receiptInfo.getCardLast4() != null) receiptMap.put("cardLast4", receiptInfo.getCardLast4());
            if (receiptInfo.getCategory() != null) {
                String category = receiptInfo.getCategory().trim();
                if (!category.isEmpty() && !category.equals("null")) {
                    receiptMap.put("category", category);
                } else {
                    FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: Category is empty or 'null' string, not saving");
                }
            } else {
                FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: Category is null, not saving");
            }
            receiptMap.put("subtotal", receiptInfo.getSubtotal());
            receiptMap.put("tax", receiptInfo.getTax());
            receiptMap.put("total", receiptInfo.getTotal());
            receiptMap.put("dateTimestamp", receiptInfo.getDateTimestamp());

            if (receiptInfo.getReceiptDateTimestamp() > 0) {
                receiptMap.put("receiptDateTimestamp", receiptInfo.getReceiptDateTimestamp());
            } else if (receiptInfo.getDateTimestamp() > 0) {
                receiptMap.put("receiptDateTimestamp", receiptInfo.getDateTimestamp());
                FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Using dateTimestamp as receiptDateTimestamp fallback: " + receiptInfo.getDateTimestamp());
            }
            if (receiptInfo.getCustomNotificationTimestamp() > 0) {
                receiptMap.put("customNotificationTimestamp", receiptInfo.getCustomNotificationTimestamp());
            }
            map.put("receipt", receiptMap);
        }

        if (receipt.getItems() != null && !receipt.getItems().isEmpty()) {
            List<Map<String, Object>> itemsList = getMaps(receipt);
            map.put("items", itemsList);
        }

        if (receipt.getAdditional() != null) {
            Map<String, Object> additionalMap = new HashMap<>();
            Receipt.AdditionalInfo additional = receipt.getAdditional();
            if (additional.getTaxNumber() != null) additionalMap.put("taxNumber", additional.getTaxNumber());
            if (additional.getCashier() != null) additionalMap.put("cashier", additional.getCashier());
            if (additional.getStoreNumber() != null) additionalMap.put("storeNumber", additional.getStoreNumber());
            if (additional.getNotes() != null) additionalMap.put("notes", additional.getNotes());
            map.put("additional", additionalMap);
        }

        if (receipt.getMetadata() != null) {
            Map<String, Object> metadataMap = new HashMap<>();
            Receipt.ReceiptMetadata metadata = receipt.getMetadata();
            if (metadata.getOcrText() != null) metadataMap.put("ocrText", metadata.getOcrText());
            if (metadata.getProcessedBy() != null) metadataMap.put("processedBy", metadata.getProcessedBy());
            if (metadata.getUploadedAt() != null) metadataMap.put("uploadedAt", metadata.getUploadedAt());
            if (metadata.getUserId() != null) metadataMap.put("userId", metadata.getUserId());
            map.put("metadata", metadataMap);
        }

        if (receipt.getImageUrl() != null) {
            map.put("imageUrl", receipt.getImageUrl());
        }

        if (cloudinaryPublicId != null) {
            map.put("cloudinaryPublicId", cloudinaryPublicId);
        }

        if (receipt.getStore() != null && receipt.getStore().getName() != null) {
            map.put("storeName", receipt.getStore().getName());
        }
        if (receipt.getReceipt() != null) {
            map.put("date", receipt.getReceipt().getDateTimestamp());
            map.put("total", receipt.getReceipt().getTotal());
        }

        map.put("createdAt", FieldValue.serverTimestamp());

        return map;
    }

    @NonNull
    private static List<Map<String, Object>> getMaps(Receipt receipt) {
        List<Map<String, Object>> itemsList = new ArrayList<>();
        for (ReceiptItem item : receipt.getItems()) {
            Map<String, Object> itemMap = new HashMap<>();
            if (item.getName() != null) itemMap.put("name", item.getName());
            if (item.getQuantity() != null) itemMap.put("quantity", item.getQuantity());
            if (item.getUnitPrice() != null) itemMap.put("unitPrice", item.getUnitPrice());
            if (item.getTotalPrice() != null) itemMap.put("totalPrice", item.getTotalPrice());
            if (item.getCategory() != null) itemMap.put("category", item.getCategory());
            if (item.getTotalPrice() != null) {
                itemMap.put("price", item.getTotalPrice());
            } else if (item.getUnitPrice() != null && item.getQuantity() != null) {
                itemMap.put("price", item.getUnitPrice() * item.getQuantity());
            } else {
                itemMap.put("price", 0.0);
            }
            itemsList.add(itemMap);
        }
        return itemsList;
    }

    public void saveReceipt(Context context, Uri imageUri, Receipt receipt, SaveCallback callback) {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
        String id = UUID.randomUUID().toString();
        receipt.setId(id);

        FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: saveReceipt called: userId=" + userId + ", id=" + id + ", imageUri=" + (imageUri != null ? imageUri.toString() : "null"));

        if (imageUri == null) {
            if (callback != null) callback.onFailure(new IllegalArgumentException("imageUri is null"));
            return;
        }

        if (CloudinaryUtils.isConfigured(context)) {
            CloudinaryUtils.UploadConfig config = CloudinaryUtils.readConfig(context, id);
            if (config != null) {
                FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Attempting to upload to Cloudinary");
                CloudinaryUtils.uploadImage(context, imageUri, config, new CloudinaryUtils.CloudinaryUploadCallback() {
                    @Override
                    public void onSuccess(String secureUrl, String publicId) {
                        receipt.setImageUrl(secureUrl);
                        FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Cloudinary upload successful. Saving metadata.");
                        saveMetadataToFirestore(id, receipt, publicId, callback, context);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: Cloudinary upload failed, falling back to Firebase Storage");
                        FirebaseCrashlytics.getInstance().recordException(e);
                        saveReceiptFirebaseFallback(context, imageUri, receipt, id, callback);
                    }
                });
                return;
            }
        }

        FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Cloudinary not configured, falling back to Firebase Storage");
        saveReceiptFirebaseFallback(context, imageUri, receipt, id, callback);
    }

    private void saveMetadataToFirestore(String id, Receipt receipt, String cloudinaryPublicId, SaveCallback callback, Context context) {
        Map<String, Object> map = buildReceiptMap(receipt, cloudinaryPublicId);
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";

        db.collection("users").document(userId).collection("receipts").document(id).set(map, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Receipt metadata saved successfully for id: " + id);
                    if (context != null) {
                        scheduleReplacementNotification(context, id, receipt);
                    }
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: Failed to save receipt metadata for id: " + id);
                    FirebaseCrashlytics.getInstance().recordException(e);
                    if (callback != null) callback.onFailure(e);
                });
    }

    private void scheduleReplacementNotification(Context context, String receiptId, Receipt receipt) {
        if (receipt.getReceipt() == null) {
            FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: ReceiptInfo is null, cannot schedule notification");
            return;
        }

        synchronized (lastScheduledTime) {
            Long lastScheduled = lastScheduledTime.get(receiptId);
            long currentTime = System.currentTimeMillis();
            if (lastScheduled != null && (currentTime - lastScheduled) < SCHEDULING_COOLDOWN_MS) {
                FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Skipping duplicate notification scheduling for receipt " + receiptId);
                return;
            }
            lastScheduledTime.put(receiptId, currentTime);
        }

        long receiptDate = receipt.getReceipt().getReceiptDateTimestamp();
        if (receiptDate == 0) {
            receiptDate = receipt.getReceipt().getDateTimestamp();
            FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: receiptDateTimestamp is 0, using dateTimestamp as fallback: " + receiptDate);
        } else {
            FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Using receiptDateTimestamp: " + receiptDate);
        }

        if (receiptDate == 0) {
            FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: No receipt date available for notification scheduling");
            return;
        }

        NotificationPreferences prefs = new NotificationPreferences(context);

        if (!prefs.isReplacementReminderEnabled()) {
            FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Replacement reminders disabled, not scheduling");
            return;
        }

        int replacementDays = prefs.getReplacementDays();
        int notificationDaysBefore = prefs.getNotificationDaysBefore();

        FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Scheduling notification for receipt " + receiptId);

        long customNotificationTimestamp = receipt.getReceipt().getCustomNotificationTimestamp();

        NotificationScheduler.scheduleReceiptReplacementNotification(
                context,
                receiptId,
                receiptDate,
                replacementDays,
                notificationDaysBefore,
                customNotificationTimestamp
        );
    }

    private void saveReceiptFirebaseFallback(Context context, Uri imageUri, Receipt receipt, String id, SaveCallback callback) {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
        FirebaseStorage storageInstance = getPreferredStorage(context);
        StorageReference ref = storageInstance.getReference().child("receipts/" + userId + "/" + id + ".jpg");

        try {
            UploadTask uploadTask;
            InputStream inputToClose = null;
            if ("content".equals(imageUri.getScheme())) {
                inputToClose = context.getContentResolver().openInputStream(imageUri);
                if (inputToClose != null) {
                    FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Fallback: uploading via putStream to path=" + ref.getPath());
                    uploadTask = ref.putStream(inputToClose);
                } else {
                    FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Fallback: InputStream null; falling back to putFile for path=" + ref.getPath());
                    uploadTask = ref.putFile(imageUri);
                }
            } else {
                FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Fallback: uploading via putFile to path=" + ref.getPath());
                uploadTask = ref.putFile(imageUri);
            }

            attachUploadListeners(uploadTask, ref, inputToClose, receipt, id, callback, context, imageUri, false);

        } catch (Exception e) {
            FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: Fallback: Exception while uploading image");
            FirebaseCrashlytics.getInstance().recordException(e);
            if (callback != null) callback.onFailure(e);
        }
    }

    private void attachUploadListeners(UploadTask uploadTask, StorageReference ref, InputStream toClose, Receipt receipt, String id, SaveCallback callback, Context context, Uri originalUri, boolean reuploadAttempted) {
        if (uploadTask == null) {
            if (toClose != null) {
                try { toClose.close(); } catch (Exception ignored) {}
            }
            if (callback != null) callback.onFailure(new IllegalArgumentException("uploadTask is null"));
            return;
        }

        uploadTask.addOnSuccessListener((OnSuccessListener<UploadTask.TaskSnapshot>) taskSnapshot -> {
            if (toClose != null) {
                try { toClose.close(); } catch (Exception ex) { FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: Failed to close input stream after upload"); FirebaseCrashlytics.getInstance().recordException(ex); }
            }

            StorageReference uploadedRef = null;
            if (taskSnapshot != null && taskSnapshot.getMetadata() != null) uploadedRef = taskSnapshot.getMetadata().getReference();
            if (uploadedRef == null) uploadedRef = ref;

            FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Resolving download URL for uploadedRefPath=" + uploadedRef.getPath());
            getDownloadUrlWithRetries(uploadedRef, 3, 1000, new DownloadUrlCallback() {
                @Override
                public void onSuccess(Uri uri) {
                    FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: download URL resolved=" + uri.toString());
                    receipt.setImageUrl(uri.toString());
                    Map<String, Object> map = buildReceiptMap(receipt, null);

                    String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
                    db.collection("users").document(userId).collection("receipts").document(id).set(map, SetOptions.merge())
                            .addOnSuccessListener(aVoid -> {
                                FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Successfully saved receipt metadata after fallback upload");
                                if (context != null) {
                                    scheduleReplacementNotification(context, id, receipt);
                                }
                                if (callback != null) callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: Failed to save receipt metadata after fallback upload");
                                FirebaseCrashlytics.getInstance().recordException(e);
                                if (callback != null) callback.onFailure(e);
                            });
                }

                @Override
                public void onFailure(Exception e) {
                    FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: Failed to obtain download URL after upload (all retries)");
                    FirebaseCrashlytics.getInstance().recordException(e);
                    if (!reuploadAttempted && originalUri != null) {
                        FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: Attempting reupload as fallback");
                        try {
                            InputStream newStream = null;
                            UploadTask retryTask;
                            if ("content".equals(originalUri.getScheme())) {
                                newStream = context.getContentResolver().openInputStream(originalUri);
                                if (newStream != null) {
                                    retryTask = ref.putStream(newStream);
                                } else {
                                    retryTask = ref.putFile(originalUri);
                                }
                            } else {
                                retryTask = ref.putFile(originalUri);
                            }
                            attachUploadListeners(retryTask, ref, newStream, receipt, id, callback, context, originalUri, true);
                            return;
                        } catch (Exception ex) {
                            FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: Reupload attempt failed");
                            FirebaseCrashlytics.getInstance().recordException(ex);
                            if (callback != null) callback.onFailure(ex);
                            return;
                        }
                    }
                    if (callback != null) callback.onFailure(e);
                }
            });

        }).addOnFailureListener(e -> {
            if (toClose != null) {
                try { toClose.close(); } catch (Exception ex) { FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: Failed to close input stream after failed upload"); FirebaseCrashlytics.getInstance().recordException(ex); }
            }
            FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: Image upload failed");
            FirebaseCrashlytics.getInstance().recordException(e);

            boolean isNotFound = false;
            try {
                if (e instanceof StorageException) {
                    StorageException se = (StorageException) e;
                    int code = se.getErrorCode();
                    isNotFound = (code == StorageException.ERROR_OBJECT_NOT_FOUND) || (se.getMessage() != null && se.getMessage().toLowerCase().contains("not found"));
                }
            } catch (Exception ignored) {}

            if (isNotFound && !reuploadAttempted && originalUri != null) {
                try {
                    String configuredBucket = null;
                    try { configuredBucket = FirebaseApp.getInstance().getOptions().getStorageBucket(); } catch (Exception ex) { FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: Failed to read configured bucket"); FirebaseCrashlytics.getInstance().recordException(ex); }
                    String alternateBucket = null;
                    if (configuredBucket != null) {
                        if (configuredBucket.contains("firebasestorage.app")) {
                            alternateBucket = configuredBucket.replace("firebasestorage.app", "appspot.com");
                        } else if (configuredBucket.contains("appspot.com")) {
                            alternateBucket = configuredBucket.replace("appspot.com", "firebasestorage.app");
                        }
                    }

                    if (alternateBucket != null && !alternateBucket.equals(configuredBucket)) {
                        FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: Attempting upload with alternate bucket: " + alternateBucket);
                        FirebaseStorage altStorage = FirebaseStorage.getInstance("gs://" + alternateBucket);
                        StorageReference altRef = altStorage.getReference().child(ref.getPath());
                        InputStream retryStream = null;
                        UploadTask retryTask;
                        if ("content".equals(originalUri.getScheme())) {
                            retryStream = context.getContentResolver().openInputStream(originalUri);
                            if (retryStream != null) retryTask = altRef.putStream(retryStream);
                            else retryTask = altRef.putFile(originalUri);
                        } else {
                            retryTask = altRef.putFile(originalUri);
                        }
                        attachUploadListeners(retryTask, altRef, retryStream, receipt, id, callback, context, originalUri, true);
                        return;
                    }
                } catch (Exception ex) {
                    FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: Alternate-bucket retry failed");
                    FirebaseCrashlytics.getInstance().recordException(ex);
                }

                if (!reuploadAttempted && originalUri != null) {
                    try {
                        FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: Attempting putBytes fallback upload");
                        uploadBytesFallback(context, originalUri, ref, receipt, id, callback);
                        return;
                    } catch (Exception ex) {
                        FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: putBytes fallback failed");
                        FirebaseCrashlytics.getInstance().recordException(ex);
                    }
                }
            }

            if (callback != null) callback.onFailure(e);
        });
    }

    public void searchByStore(String storePrefix, OnCompleteListener<QuerySnapshot> listener) {
        db.collectionGroup("receipts")
                .whereGreaterThanOrEqualTo("storeName", storePrefix)
                .whereLessThanOrEqualTo("storeName", storePrefix + "\uf8ff")
                .get()
                .addOnCompleteListener(listener);
    }

    public void fetchReceiptsForCurrentUser(OnCompleteListener<QuerySnapshot> listener) {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
        db.collection("users").document(userId).collection("receipts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(listener);
    }

    public interface FetchReceiptCallback {
        void onSuccess(Receipt receipt);
        void onFailure(Exception e);
    }

    public void fetchReceiptById(String receiptId, FetchReceiptCallback callback) {
        if (receiptId == null || receiptId.isEmpty()) {
            if (callback != null) {
                callback.onFailure(new IllegalArgumentException("Receipt ID cannot be null or empty"));
            }
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "anonymous";

        db.collection("users").document(userId).collection("receipts").document(receiptId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Receipt receipt = ReceiptRepository.parseReceiptFromDocument(documentSnapshot);
                        if (receipt != null) {
                            if (callback != null) callback.onSuccess(receipt);
                        } else {
                            if (callback != null) callback.onFailure(new Exception("Failed to parse receipt"));
                        }
                    } else {
                        if (callback != null) callback.onFailure(new Exception("Receipt not found"));
                    }
                })
                .addOnFailureListener(e -> {
                    FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: Error fetching receipt: " + receiptId);
                    FirebaseCrashlytics.getInstance().recordException(e);
                    if (callback != null) callback.onFailure(e);
                });
    }

    public static Receipt parseReceiptFromDocument(com.google.firebase.firestore.DocumentSnapshot document) {
        try {
            Receipt receipt = new Receipt();
            Map<String, Object> data = document.getData();
            if (data == null) return null;

            receipt.setId(document.getId());

            if (data.containsKey("imageUrl")) {
                receipt.setImageUrl((String) data.get("imageUrl"));
            }

            if (data.containsKey("cloudinaryPublicId")) {
                receipt.setCloudinaryPublicId((String) data.get("cloudinaryPublicId"));
            }

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
            } else if (data.containsKey("storeName")) {
                Receipt.StoreInfo store = new Receipt.StoreInfo();
                store.setName((String) data.get("storeName"));
                receipt.setStore(store);
            }

            if (data.containsKey("receipt")) {
                Map<String, Object> receiptMap = (Map<String, Object>) data.get("receipt");
                Receipt.ReceiptInfo receiptInfo = new Receipt.ReceiptInfo();
                if (receiptMap != null) {
                    if (receiptMap.containsKey("receiptId")) receiptInfo.setReceiptId((String) receiptMap.get("receiptId"));
                    if (receiptMap.containsKey("date")) receiptInfo.setDate((String) receiptMap.get("date"));
                    if (receiptMap.containsKey("time")) receiptInfo.setTime((String) receiptMap.get("time"));
                    if (receiptMap.containsKey("currency")) receiptInfo.setCurrency((String) receiptMap.get("currency"));
                    if (receiptMap.containsKey("paymentMethod")) receiptInfo.setPaymentMethod((String) receiptMap.get("paymentMethod"));
                    if (receiptMap.containsKey("cardLast4")) receiptInfo.setCardLast4((String) receiptMap.get("cardLast4"));
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
                        if (subtotal instanceof Number) {
                            receiptInfo.setSubtotal(((Number) subtotal).doubleValue());
                        } else if (subtotal != null) {
                            try {
                                receiptInfo.setSubtotal(Double.parseDouble(subtotal.toString()));
                            } catch (NumberFormatException e) {
                                FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: Failed to parse subtotal: " + subtotal);
                            }
                        }
                    }
                    if (receiptMap.containsKey("tax")) {
                        Object tax = receiptMap.get("tax");
                        if (tax instanceof Number) {
                            receiptInfo.setTax(((Number) tax).doubleValue());
                        } else if (tax != null) {
                            try {
                                receiptInfo.setTax(Double.parseDouble(tax.toString()));
                            } catch (NumberFormatException e) {
                                FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: Failed to parse tax: " + tax);
                            }
                        }
                    }
                    if (receiptMap.containsKey("total")) {
                        Object total = receiptMap.get("total");
                        if (total instanceof Number) {
                            receiptInfo.setTotal(((Number) total).doubleValue());
                        } else if (total != null) {
                            try {
                                receiptInfo.setTotal(Double.parseDouble(total.toString()));
                            } catch (NumberFormatException e) {
                                FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: Failed to parse total: " + total);
                            }
                        }
                    }
                    if (receiptMap.containsKey("dateTimestamp")) {
                        Object dateTimestamp = receiptMap.get("dateTimestamp");
                        if (dateTimestamp instanceof Number) receiptInfo.setDateTimestamp(((Number) dateTimestamp).longValue());
                    }
                    if (receiptMap.containsKey("receiptDateTimestamp")) {
                        Object receiptDateTimestamp = receiptMap.get("receiptDateTimestamp");
                        if (receiptDateTimestamp instanceof Number) receiptInfo.setReceiptDateTimestamp(((Number) receiptDateTimestamp).longValue());
                    }
                    if (receiptMap.containsKey("customNotificationTimestamp")) {
                        Object customNotificationTimestamp = receiptMap.get("customNotificationTimestamp");
                        if (customNotificationTimestamp instanceof Number) receiptInfo.setCustomNotificationTimestamp(((Number) customNotificationTimestamp).longValue());
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
                        if (itemMap.containsKey("category")) item.setCategory((String) itemMap.get("category"));
                        items.add(item);
                    }
                    receipt.setItems(items);
                }
            }

            if (data.containsKey("additional")) {
                Map<String, Object> additionalMap = (Map<String, Object>) data.get("additional");
                Receipt.AdditionalInfo additional = new Receipt.AdditionalInfo();
                if (additionalMap != null) {
                    if (additionalMap.containsKey("taxNumber")) additional.setTaxNumber((String) additionalMap.get("taxNumber"));
                    if (additionalMap.containsKey("cashier")) additional.setCashier((String) additionalMap.get("cashier"));
                    if (additionalMap.containsKey("storeNumber")) additional.setStoreNumber((String) additionalMap.get("storeNumber"));
                    if (additionalMap.containsKey("notes")) additional.setNotes((String) additionalMap.get("notes"));
                }
                receipt.setAdditional(additional);
            }

            if (data.containsKey("metadata")) {
                Map<String, Object> metadataMap = (Map<String, Object>) data.get("metadata");
                Receipt.ReceiptMetadata metadata = new Receipt.ReceiptMetadata();
                if (metadataMap != null) {
                    if (metadataMap.containsKey("ocrText")) metadata.setOcrText((String) metadataMap.get("ocrText"));
                    if (metadataMap.containsKey("processedBy")) metadata.setProcessedBy((String) metadataMap.get("processedBy"));
                    if (metadataMap.containsKey("uploadedAt")) metadata.setUploadedAt((String) metadataMap.get("uploadedAt"));
                    if (metadataMap.containsKey("userId")) metadata.setUserId((String) metadataMap.get("userId"));
                }
                receipt.setMetadata(metadata);
            }

            return receipt;
        } catch (Exception e) {
            FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: Error parsing receipt from document");
            FirebaseCrashlytics.getInstance().recordException(e);
            return null;
        }
    }

    private interface DownloadUrlCallback {
        void onSuccess(Uri uri);
        void onFailure(Exception e);
    }

    private void getDownloadUrlWithRetries(StorageReference ref, int maxAttempts, long initialDelayMs, DownloadUrlCallback callback) {
        Handler handler = new Handler(Looper.getMainLooper());
        final int[] attemptCount = {0};
        final long[] currentDelay = {initialDelayMs};
        final long MAX_DELAY_MS = 10000; // 10 seconds max delay

        Runnable attempt = new Runnable() {
            @Override
            public void run() {
                attemptCount[0]++;
                FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Attempt " + attemptCount[0] + " to get download URL for " + ref.getPath());

                ref.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Successfully retrieved download URL on attempt " + attemptCount[0]);
                            if (callback != null) callback.onSuccess(uri);
                        })
                        .addOnFailureListener(e -> {
                            FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: getDownloadUrl failed on attempt " + attemptCount[0] + "/" + maxAttempts);
                            FirebaseCrashlytics.getInstance().recordException(e);

                            if (attemptCount[0] >= maxAttempts) {
                                FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: Max retry attempts (" + maxAttempts + ") reached");
                                if (callback != null) callback.onFailure(e);
                                return;
                            }

                            currentDelay[0] = Math.min(currentDelay[0] * 2, MAX_DELAY_MS);
                            FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Retrying in " + currentDelay[0] + "ms...");

                            handler.postDelayed(this, currentDelay[0]);
                        });
            }
        };

        handler.post(attempt);
    }

    private FirebaseStorage getPreferredStorage(Context context) {
        try {
            String configured = null;
            try { configured = FirebaseApp.getInstance().getOptions().getStorageBucket(); } catch (Exception ignored) {}
            if (configured != null) {
                if (configured.contains("firebasestorage.app")) {
                    String alt = configured.replace("firebasestorage.app", "appspot.com");
                    try {
                        FirebaseStorage altStorage = FirebaseStorage.getInstance("gs://" + alt);
                        FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Using alternate storage bucket gs://" + alt);
                        return altStorage;
                    } catch (Exception e) {
                        FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: Failed to use alternate storage bucket, falling back: " + alt);
                        FirebaseCrashlytics.getInstance().recordException(e);
                    }
                }
                try {
                    FirebaseStorage cfgStorage = FirebaseStorage.getInstance("gs://" + configured);
                    FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Using configured storage bucket gs://" + configured);
                    return cfgStorage;
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().log("W/ReceiptRepository: Failed to use configured storage bucket, falling back to default");
                    FirebaseCrashlytics.getInstance().recordException(e);
                }
            }
        } catch (Exception ignored) {}
        return FirebaseStorage.getInstance();
    }

    private void uploadBytesFallback(Context context, Uri imageUri, StorageReference ref, Receipt receipt, String id, SaveCallback callback) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            } finally {
                inputStream.close();
            }
            byte[] imageData = output.toByteArray();
            UploadTask uploadTask = ref.putBytes(imageData);

            uploadTask.addOnSuccessListener((OnSuccessListener<UploadTask.TaskSnapshot>) taskSnapshot -> {
                FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: putBytes upload success");

                StorageReference uploadedRef = null;
                if (taskSnapshot != null && taskSnapshot.getMetadata() != null) uploadedRef = taskSnapshot.getMetadata().getReference();
                if (uploadedRef == null) uploadedRef = ref;

                getDownloadUrlWithRetries(uploadedRef, 3, 1000, new DownloadUrlCallback() {
                    @Override
                    public void onSuccess(Uri uri) {
                        receipt.setImageUrl(uri.toString());
                        Map<String, Object> map = buildReceiptMap(receipt, null);

                        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
                        db.collection("users").document(userId).collection("receipts").document(id).set(map, SetOptions.merge())
                                .addOnSuccessListener(aVoid -> {
                                    FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Successfully saved receipt metadata after putBytes upload");
                                    if (context != null) {
                                        scheduleReplacementNotification(context, id, receipt);
                                    }
                                    if (callback != null) callback.onSuccess();
                                })
                                .addOnFailureListener(e -> {
                                    FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: Failed to save receipt metadata after putBytes upload");
                                    FirebaseCrashlytics.getInstance().recordException(e);
                                    if (callback != null) callback.onFailure(e);
                                });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: Failed to obtain download URL after putBytes upload");
                        FirebaseCrashlytics.getInstance().recordException(e);
                        if (callback != null) callback.onFailure(e);
                    }
                });

            }).addOnFailureListener(e -> {
                FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: putBytes upload failed");
                FirebaseCrashlytics.getInstance().recordException(e);
                if (callback != null) callback.onFailure(e);
            });
        } catch (Exception e) {
            FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: Exception in uploadBytesFallback");
            FirebaseCrashlytics.getInstance().recordException(e);
            if (callback != null) callback.onFailure(e);
        }
    }

    public void deleteReceipt(String receiptId, DeleteCallback callback) {
        if (receiptId == null || receiptId.isEmpty()) {
            if (callback != null) {
                callback.onFailure(new IllegalArgumentException("Receipt ID cannot be null or empty"));
            }
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "anonymous";

        db.collection("users").document(userId).collection("receipts").document(receiptId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    FirebaseCrashlytics.getInstance().log("D/ReceiptRepository: Receipt deleted successfully: " + receiptId);
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    FirebaseCrashlytics.getInstance().log("E/ReceiptRepository: Failed to delete receipt: " + receiptId);
                    FirebaseCrashlytics.getInstance().recordException(e);
                    if (callback != null) callback.onFailure(e);
                });
    }
}
