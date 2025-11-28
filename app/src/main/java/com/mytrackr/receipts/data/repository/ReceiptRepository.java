package com.mytrackr.receipts.data.repository;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.firebase.storage.StorageException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.FieldValue;

import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.data.models.ReceiptItem;
import com.mytrackr.receipts.utils.CloudinaryUtils;
import com.mytrackr.receipts.utils.NotificationPreferences;
import com.mytrackr.receipts.utils.NotificationScheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


// add import for R
import com.mytrackr.receipts.R;

public class ReceiptRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private static ReceiptRepository repositoryInstance;
    
    // Track last notification scheduling time per receipt to prevent duplicates
    private static final Map<String, Long> lastScheduledTime = new HashMap<>();
    private static final long SCHEDULING_COOLDOWN_MS = 5000; // 5 seconds cooldown

    public static synchronized ReceiptRepository getInstance(){
        if(repositoryInstance == null){
            repositoryInstance = new ReceiptRepository();
            Log.i("RECEIPT_REPO_INITIALIZED", "Receipt Repository is Initialized");
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

    // Build Firestore map from Receipt object with all structured fields
    private Map<String, Object> buildReceiptMap(Receipt receipt, String cloudinaryPublicId) {
        Map<String, Object> map = new HashMap<>();
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
        
        // Store information
        if (receipt.getStore() != null) {
            Map<String, Object> storeMap = new HashMap<>();
            Receipt.StoreInfo store = receipt.getStore();
            if (store.getName() != null) storeMap.put("name", store.getName());
            if (store.getAddress() != null) storeMap.put("address", store.getAddress());
            if (store.getPhone() != null) storeMap.put("phone", store.getPhone());
            if (store.getWebsite() != null) storeMap.put("website", store.getWebsite());
            map.put("store", storeMap);
        }
        
        // Receipt information
        if (receipt.getReceipt() != null) {
            Map<String, Object> receiptMap = new HashMap<>();
            Receipt.ReceiptInfo receiptInfo = receipt.getReceipt();
            if (receiptInfo.getReceiptId() != null) receiptMap.put("receiptId", receiptInfo.getReceiptId());
            if (receiptInfo.getDate() != null) receiptMap.put("date", receiptInfo.getDate());
            if (receiptInfo.getTime() != null) receiptMap.put("time", receiptInfo.getTime());
            if (receiptInfo.getCurrency() != null) receiptMap.put("currency", receiptInfo.getCurrency());
            if (receiptInfo.getPaymentMethod() != null) receiptMap.put("paymentMethod", receiptInfo.getPaymentMethod());
            if (receiptInfo.getCardLast4() != null) receiptMap.put("cardLast4", receiptInfo.getCardLast4());
            // Always save category if it exists (even if empty string, but not null)
            if (receiptInfo.getCategory() != null) {
                String category = receiptInfo.getCategory().trim();
                if (!category.isEmpty() && !category.equals("null")) {
                    receiptMap.put("category", category);
                    Log.d("ReceiptRepository", "Saving category to Firestore: " + category);
                } else {
                    Log.w("ReceiptRepository", "Category is empty or 'null' string, not saving");
                }
            } else {
                Log.w("ReceiptRepository", "Category is null, not saving");
            }
            receiptMap.put("subtotal", receiptInfo.getSubtotal());
            receiptMap.put("tax", receiptInfo.getTax());
            receiptMap.put("total", receiptInfo.getTotal());
            // Always save dateTimestamp (for sorting/upload time)
            receiptMap.put("dateTimestamp", receiptInfo.getDateTimestamp());
            
            // Always save receiptDateTimestamp if it exists, otherwise save dateTimestamp as fallback
            if (receiptInfo.getReceiptDateTimestamp() > 0) {
                receiptMap.put("receiptDateTimestamp", receiptInfo.getReceiptDateTimestamp()); // Actual receipt date
            } else if (receiptInfo.getDateTimestamp() > 0) {
                // If receiptDateTimestamp is 0 but dateTimestamp exists, use it as fallback
                receiptMap.put("receiptDateTimestamp", receiptInfo.getDateTimestamp());
                Log.d("ReceiptRepository", "Using dateTimestamp as receiptDateTimestamp fallback: " + receiptInfo.getDateTimestamp());
            }
            if (receiptInfo.getCustomNotificationTimestamp() > 0) {
                receiptMap.put("customNotificationTimestamp", receiptInfo.getCustomNotificationTimestamp()); // Custom notification date
            }
            map.put("receipt", receiptMap);
        }
        
        // Items - convert ReceiptItem objects to Maps for Firestore
        if (receipt.getItems() != null && !receipt.getItems().isEmpty()) {
            List<Map<String, Object>> itemsList = getMaps(receipt);
            map.put("items", itemsList);
        }
        
        // Additional information
        if (receipt.getAdditional() != null) {
            Map<String, Object> additionalMap = new HashMap<>();
            Receipt.AdditionalInfo additional = receipt.getAdditional();
            if (additional.getTaxNumber() != null) additionalMap.put("taxNumber", additional.getTaxNumber());
            if (additional.getCashier() != null) additionalMap.put("cashier", additional.getCashier());
            if (additional.getStoreNumber() != null) additionalMap.put("storeNumber", additional.getStoreNumber());
            if (additional.getNotes() != null) additionalMap.put("notes", additional.getNotes());
            map.put("additional", additionalMap);
        }
        
        // Metadata
        if (receipt.getMetadata() != null) {
            Map<String, Object> metadataMap = new HashMap<>();
            Receipt.ReceiptMetadata metadata = receipt.getMetadata();
            if (metadata.getOcrText() != null) metadataMap.put("ocrText", metadata.getOcrText());
            if (metadata.getProcessedBy() != null) metadataMap.put("processedBy", metadata.getProcessedBy());
            if (metadata.getUploadedAt() != null) metadataMap.put("uploadedAt", metadata.getUploadedAt());
            if (metadata.getUserId() != null) metadataMap.put("userId", metadata.getUserId());
            map.put("metadata", metadataMap);
        }
        
        // Image URL
        if (receipt.getImageUrl() != null) {
            map.put("imageUrl", receipt.getImageUrl());
        }
        
        // Cloudinary public id if present
        if (cloudinaryPublicId != null) {
            map.put("cloudinaryPublicId", cloudinaryPublicId);
        }
        
        // Backward compatibility fields (for existing queries)
        if (receipt.getStore() != null && receipt.getStore().getName() != null) {
            map.put("storeName", receipt.getStore().getName());
        }
        if (receipt.getReceipt() != null) {
            map.put("date", receipt.getReceipt().getDateTimestamp());
            map.put("total", receipt.getReceipt().getTotal());
        }
        
        // Add server timestamp for consistent ordering/audit
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
            // Keep backward compatibility with "price" field
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

        Log.d("ReceiptRepository", "saveReceipt called: userId=" + userId + " id=" + id + " imageUri=" + (imageUri != null ? imageUri.toString() : "null"));

        if (imageUri == null) {
            if (callback != null) callback.onFailure(new IllegalArgumentException("imageUri is null"));
            return;
        }

        // Check for Cloudinary config and attempt upload if available
        if (CloudinaryUtils.isConfigured(context)) {
            CloudinaryUtils.UploadConfig config = CloudinaryUtils.readConfig(context, id);
            if (config != null) {
                CloudinaryUtils.uploadImage(context, imageUri, config, new CloudinaryUtils.CloudinaryUploadCallback() {
                    @Override
                    public void onSuccess(String secureUrl, String publicId) {
                        receipt.setImageUrl(secureUrl);
                        saveMetadataToFirestore(id, receipt, publicId, callback, context);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.w("ReceiptRepository", "Cloudinary upload failed, falling back to Firebase Storage", e);
                        saveReceiptFirebaseFallback(context, imageUri, receipt, id, callback);
                    }
                });
                return;
            }
        }

         // Fallback to Firebase Storage if Cloudinary not configured
         saveReceiptFirebaseFallback(context, imageUri, receipt, id, callback);
    }

     // Helper: save metadata to Firestore (used by Cloudinary path)
     private void saveMetadataToFirestore(String id, Receipt receipt, String cloudinaryPublicId, SaveCallback callback, Context context) {
         Map<String, Object> map = buildReceiptMap(receipt, cloudinaryPublicId);
         String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";

        // Save receipt under the user's document: users/{userId}/receipts/{id}
        db.collection("users").document(userId).collection("receipts").document(id).set(map, SetOptions.merge())
                 .addOnSuccessListener(aVoid -> {
                     // Schedule replacement period notification
                     if (context != null) {
                         scheduleReplacementNotification(context, id, receipt);
                     }
                     if (callback != null) callback.onSuccess();
                 })
                 .addOnFailureListener(e -> {
                     Log.w("ReceiptRepository", "Failed to save receipt metadata", e);
                     if (callback != null) callback.onFailure(e);
                 });
     }
     
    private void scheduleReplacementNotification(Context context, String receiptId, Receipt receipt) {
        if (receipt.getReceipt() == null) {
            Log.w("ReceiptRepository", "ReceiptInfo is null, cannot schedule notification");
            return;
        }
        
        // Guard against duplicate scheduling within cooldown period
        synchronized (lastScheduledTime) {
            Long lastScheduled = lastScheduledTime.get(receiptId);
            long currentTime = System.currentTimeMillis();
            if (lastScheduled != null && (currentTime - lastScheduled) < SCHEDULING_COOLDOWN_MS) {
                Log.d("ReceiptRepository", "Skipping duplicate notification scheduling for receipt " + receiptId + 
                    " (last scheduled " + (currentTime - lastScheduled) + " ms ago)");
                return;
            }
            lastScheduledTime.put(receiptId, currentTime);
        }
        
        // Use receiptDateTimestamp for notification calculation (actual receipt date)
        // Fallback to dateTimestamp if receiptDateTimestamp is not set
        long receiptDate = receipt.getReceipt().getReceiptDateTimestamp();
        if (receiptDate == 0) {
            receiptDate = receipt.getReceipt().getDateTimestamp();
            Log.d("ReceiptRepository", "receiptDateTimestamp is 0, using dateTimestamp as fallback: " + receiptDate);
        } else {
            Log.d("ReceiptRepository", "Using receiptDateTimestamp: " + receiptDate);
        }
        
        if (receiptDate == 0) {
            Log.w("ReceiptRepository", "No receipt date available for notification scheduling");
            return;
        }
        
        NotificationPreferences prefs = new NotificationPreferences(context);
        
        if (!prefs.isReplacementReminderEnabled()) {
            Log.d("ReceiptRepository", "Replacement reminders disabled, not scheduling");
            return;
        }
        
        int replacementDays = prefs.getReplacementDays();
        int notificationDaysBefore = prefs.getNotificationDaysBefore();
        
        Log.d("ReceiptRepository", "Scheduling notification for receipt " + receiptId + 
            " with receiptDate: " + receiptDate + 
            ", replacementDays: " + replacementDays + 
            ", notificationDaysBefore: " + notificationDaysBefore);
        
        // Get custom notification timestamp if set
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

    // Fallback path: call the original Firebase Storage upload logic (extracted here so Cloudinary path can reuse it)
    private void saveReceiptFirebaseFallback(Context context, Uri imageUri, Receipt receipt, String id, SaveCallback callback) {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
        FirebaseStorage storageInstance = getPreferredStorage(context);
        StorageReference ref = storageInstance.getReference().child("receipts/" + userId + "/" + id + ".jpg");

        try {
            UploadTask uploadTask;
            java.io.InputStream inputToClose = null;
            if ("content".equals(imageUri.getScheme())) {
                inputToClose = context.getContentResolver().openInputStream(imageUri);
                if (inputToClose != null) {
                    Log.d("ReceiptRepository", "Fallback: uploading via putStream to path=" + ref.getPath());
                    uploadTask = ref.putStream(inputToClose);
                } else {
                    Log.d("ReceiptRepository", "Fallback: InputStream null; falling back to putFile for path=" + ref.getPath());
                    uploadTask = ref.putFile(imageUri);
                }
            } else {
                Log.d("ReceiptRepository", "Fallback: uploading via putFile to path=" + ref.getPath());
                uploadTask = ref.putFile(imageUri);
            }

            attachUploadListeners(uploadTask, ref, inputToClose, receipt, id, callback, context, imageUri, false);

        } catch (Exception e) {
            Log.w("ReceiptRepository", "Fallback: Exception while uploading image", e);
            if (callback != null) callback.onFailure(e);
        }
    }

    private void attachUploadListeners(UploadTask uploadTask, StorageReference ref, java.io.InputStream toClose, Receipt receipt, String id, SaveCallback callback, Context context, Uri originalUri, boolean reuploadAttempted) {
        if (uploadTask == null) {
            if (toClose != null) {
                try { toClose.close(); } catch (Exception ignored) {}
            }
            if (callback != null) callback.onFailure(new IllegalArgumentException("uploadTask is null"));
            return;
        }

        uploadTask.addOnSuccessListener((OnSuccessListener<UploadTask.TaskSnapshot>) taskSnapshot -> {
            Log.d("ReceiptRepository", "upload success snapshot; metadataRefPath=" + (taskSnapshot != null && taskSnapshot.getMetadata() != null && taskSnapshot.getMetadata().getReference()!=null ? taskSnapshot.getMetadata().getReference().getPath() : "(null)"));
            if (toClose != null) {
                try { toClose.close(); } catch (Exception ex) { Log.d("ReceiptRepository", "Failed to close input stream after upload", ex); }
            }

            StorageReference uploadedRef = null;
            if (taskSnapshot != null && taskSnapshot.getMetadata() != null) uploadedRef = taskSnapshot.getMetadata().getReference();
            if (uploadedRef == null) uploadedRef = ref;

            Log.d("ReceiptRepository", "Resolving download URL for uploadedRefPath=" + uploadedRef.getPath());
            getDownloadUrlWithRetries(uploadedRef, 3, 1000, new DownloadUrlCallback() {
                @Override
                public void onSuccess(Uri uri) {
                    Log.d("ReceiptRepository", "download URL resolved=" + uri.toString());
                    receipt.setImageUrl(uri.toString());
                    Map<String, Object> map = buildReceiptMap(receipt, null);

                    // Save under users/{userId}/receipts/{id}
                    String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
                    db.collection("users").document(userId).collection("receipts").document(id).set(map, SetOptions.merge())
                             .addOnSuccessListener(aVoid -> {
                                 // Schedule replacement period notification
                                 if (context != null) {
                                     scheduleReplacementNotification(context, id, receipt);
                                 }
                                 if (callback != null) callback.onSuccess();
                             })
                             .addOnFailureListener(e -> {
                                 Log.w("ReceiptRepository", "Failed to save receipt metadata", e);
                                 if (callback != null) callback.onFailure(e);
                             });
                }

                @Override
                public void onFailure(Exception e) {
                    Log.w("ReceiptRepository", "Failed to obtain download URL after upload (all retries)", e);
                    if (!reuploadAttempted && originalUri != null) {
                        Log.w("ReceiptRepository", "Attempting reupload as fallback");
                        try {
                            java.io.InputStream newStream = null;
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
                            Log.w("ReceiptRepository", "Reupload attempt failed", ex);
                            if (callback != null) callback.onFailure(ex);
                            return;
                        }
                    }
                    if (callback != null) callback.onFailure(e);
                }
            });

        }).addOnFailureListener(e -> {
            if (toClose != null) {
                try { toClose.close(); } catch (Exception ex) { Log.d("ReceiptRepository", "Failed to close input stream after failed upload", ex); }
            }
            Log.w("ReceiptRepository", "Image upload failed", e);

            boolean isNotFound = false;
            try {
                if (e instanceof StorageException) {
                    StorageException se = (StorageException) e;
                    int code = se.getErrorCode();
                    isNotFound = (code == StorageException.ERROR_OBJECT_NOT_FOUND) || (se.getMessage() != null && se.getMessage().toLowerCase().contains("not found"));
                } else if (e.getMessage() != null && e.getMessage().toLowerCase().contains("not found")) {
                    isNotFound = true;
                }
            } catch (Exception ignored) {}

            if (isNotFound && !reuploadAttempted && originalUri != null) {
                try {
                    String configuredBucket = null;
                    try { configuredBucket = FirebaseApp.getInstance().getOptions().getStorageBucket(); } catch (Exception ex) { Log.d("ReceiptRepository", "Failed to read configured bucket", ex); }
                    String alternateBucket = null;
                    if (configuredBucket != null) {
                        if (configuredBucket.contains("firebasestorage.app")) {
                            alternateBucket = configuredBucket.replace("firebasestorage.app", "appspot.com");
                        } else if (configuredBucket.contains("appspot.com")) {
                            alternateBucket = configuredBucket.replace("appspot.com", "firebasestorage.app");
                        }
                    }

                    if (alternateBucket != null && !alternateBucket.equals(configuredBucket)) {
                        Log.w("ReceiptRepository", "Attempting upload with alternate bucket: " + alternateBucket);
                        FirebaseStorage altStorage = FirebaseStorage.getInstance("gs://" + alternateBucket);
                        StorageReference altRef = altStorage.getReference().child(ref.getPath());
                        java.io.InputStream retryStream = null;
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
                    Log.w("ReceiptRepository", "Alternate-bucket retry failed", ex);
                }

                if (!reuploadAttempted && originalUri != null) {
                    try {
                        Log.w("ReceiptRepository", "Attempting putBytes fallback upload");
                        uploadBytesFallback(context, originalUri, ref, receipt, id, callback);
                        return;
                    } catch (Exception ex) {
                        Log.w("ReceiptRepository", "putBytes fallback failed", ex);
                    }
                }
            }

            if (callback != null) callback.onFailure(e);
        });
    }

    public void searchByStore(String storePrefix, OnCompleteListener<QuerySnapshot> listener) {
        // Search across all users' receipts using collectionGroup
        db.collectionGroup("receipts")
                .whereGreaterThanOrEqualTo("storeName", storePrefix)
                .whereLessThanOrEqualTo("storeName", storePrefix + "\uf8ff")
                .get()
                .addOnCompleteListener(listener);
    }

    public void fetchReceiptsForCurrentUser(OnCompleteListener<QuerySnapshot> listener) {
         String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
        // Fetch directly from the user's receipts subcollection, ordered by createdAt (uploadedAt)
        db.collection("users").document(userId).collection("receipts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(listener);
    }
    
    /**
     * Fetch a single receipt by ID from Firestore
     * @param receiptId The receipt ID to fetch
     * @param callback Callback with the parsed Receipt or error
     */
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
                Log.e("ReceiptRepository", "Error fetching receipt: " + receiptId, e);
                if (callback != null) callback.onFailure(e);
            });
    }
    
    public interface FetchReceiptCallback {
        void onSuccess(Receipt receipt);
        void onFailure(Exception e);
    }
    
    /**
     * Parse a receipt from a Firestore document (full parsing with all fields)
     * Made public so it can be reused by other classes like NotificationAlarmReceiver
     */
    public static Receipt parseReceiptFromDocument(com.google.firebase.firestore.DocumentSnapshot document) {
        try {
            Receipt receipt = new Receipt();
            Map<String, Object> data = document.getData();
            if (data == null) return null;

            receipt.setId(document.getId());
            
            // Parse image URL
            if (data.containsKey("imageUrl")) {
                receipt.setImageUrl((String) data.get("imageUrl"));
            }
            
            if (data.containsKey("cloudinaryPublicId")) {
                receipt.setCloudinaryPublicId((String) data.get("cloudinaryPublicId"));
            }

            // Parse store information
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
                // Backward compatibility
                Receipt.StoreInfo store = new Receipt.StoreInfo();
                store.setName((String) data.get("storeName"));
                receipt.setStore(store);
            }

            // Parse receipt information
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
                            // Try to parse as string if not a number
                            try {
                                receiptInfo.setSubtotal(Double.parseDouble(subtotal.toString()));
                            } catch (NumberFormatException e) {
                                Log.w("ReceiptRepository", "Failed to parse subtotal: " + subtotal);
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
                                Log.w("ReceiptRepository", "Failed to parse tax: " + tax);
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
                                Log.w("ReceiptRepository", "Failed to parse total: " + total);
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

            // Parse items
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

            // Parse additional information
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

            // Parse metadata
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
            Log.e("ReceiptRepository", "Error parsing receipt from document", e);
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
                Log.d("ReceiptRepository", "Attempt " + attemptCount[0] + " to get download URL for " + ref.getPath());
                
                ref.getDownloadUrl()
                    .addOnSuccessListener(uri -> {
                        Log.d("ReceiptRepository", "Successfully retrieved download URL on attempt " + attemptCount[0]);
                        if (callback != null) callback.onSuccess(uri);
                    })
                    .addOnFailureListener(e -> {
                        Log.w("ReceiptRepository", "getDownloadUrl failed on attempt " + attemptCount[0] + 
                                "/" + maxAttempts + ", error: " + e.getMessage());
                        
                        if (attemptCount[0] >= maxAttempts) {
                            Log.e("ReceiptRepository", "Max retry attempts (" + maxAttempts + ") reached");
                            if (callback != null) callback.onFailure(e);
                            return;
                        }
                        
                        currentDelay[0] = Math.min(currentDelay[0] * 2, MAX_DELAY_MS);
                        Log.d("ReceiptRepository", "Retrying in " + currentDelay[0] + "ms...");
                        
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
                        Log.d("ReceiptRepository", "Using alternate storage bucket gs://" + alt);
                        return altStorage;
                    } catch (Exception e) {
                        Log.d("ReceiptRepository", "Failed to use alternate storage bucket, falling back: " + alt, e);
                    }
                }
                try {
                    FirebaseStorage cfgStorage = FirebaseStorage.getInstance("gs://" + configured);
                    Log.d("ReceiptRepository", "Using configured storage bucket gs://" + configured);
                    return cfgStorage;
                } catch (Exception e) {
                    Log.d("ReceiptRepository", "Failed to use configured storage bucket, falling back to default", e);
                }
            }
        } catch (Exception ignored) {}
        return FirebaseStorage.getInstance();
    }

    private void uploadBytesFallback(Context context, Uri imageUri, StorageReference ref, Receipt receipt, String id, SaveCallback callback) {
        try {
            java.io.InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            byte[] buffer = new byte[8192];
            int bytesRead;
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            try {
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            } finally {
                inputStream.close();
            }
            byte[] imageData = output.toByteArray();
            UploadTask uploadTask = ref.putBytes(imageData);

            uploadTask.addOnSuccessListener((OnSuccessListener<UploadTask.TaskSnapshot>) taskSnapshot -> {
                Log.d("ReceiptRepository", "putBytes upload success snapshot; metadataRefPath=" + (taskSnapshot != null && taskSnapshot.getMetadata() != null && taskSnapshot.getMetadata().getReference()!=null ? taskSnapshot.getMetadata().getReference().getPath() : "(null)"));

                StorageReference uploadedRef = null;
                if (taskSnapshot != null && taskSnapshot.getMetadata() != null) uploadedRef = taskSnapshot.getMetadata().getReference();
                if (uploadedRef == null) uploadedRef = ref;

                Log.d("ReceiptRepository", "Resolving download URL for uploadedRefPath=" + uploadedRef.getPath());
                getDownloadUrlWithRetries(uploadedRef, 3, 1000, new DownloadUrlCallback() {
                    @Override
                    public void onSuccess(Uri uri) {
                        Log.d("ReceiptRepository", "download URL resolved=" + uri.toString());
                        receipt.setImageUrl(uri.toString());
                        Map<String, Object> map = buildReceiptMap(receipt, null);

                        // Save putBytes fallback result under users/{userId}/receipts/{id}
                        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
                        db.collection("users").document(userId).collection("receipts").document(id).set(map, SetOptions.merge())
                                .addOnSuccessListener(aVoid -> {
                                    // Schedule replacement period notification
                                    if (context != null) {
                                        scheduleReplacementNotification(context, id, receipt);
                                    }
                                    if (callback != null) callback.onSuccess();
                                })
                                .addOnFailureListener(e -> {
                                    Log.w("ReceiptRepository", "Failed to save receipt metadata", e);
                                    if (callback != null) callback.onFailure(e);
                                });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.w("ReceiptRepository", "Failed to obtain download URL after putBytes upload (all retries)", e);
                        if (callback != null) callback.onFailure(e);
                    }
                });

            }).addOnFailureListener(e -> {
                Log.w("ReceiptRepository", "putBytes upload failed", e);
                if (callback != null) callback.onFailure(e);
            });
        } catch (Exception e) {
            Log.w("ReceiptRepository", "Exception in uploadBytesFallback", e);
            if (callback != null) callback.onFailure(e);
        }
    }
    
    // Delete receipt from Firestore
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
                    Log.d("ReceiptRepository", "Receipt deleted successfully: " + receiptId);
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e("ReceiptRepository", "Failed to delete receipt: " + receiptId, e);
                    if (callback != null) callback.onFailure(e);
                });
    }
}

