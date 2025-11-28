package com.mytrackr.receipts.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.utils.NotificationHelper;
import com.mytrackr.receipts.utils.NotificationPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class ReplacementPeriodWorker extends Worker {
    private static final String TAG = "ReplacementPeriodWorker";
    
    public ReplacementPeriodWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context context = getApplicationContext();
            NotificationPreferences prefs = new NotificationPreferences(context);
            
            if (!prefs.isReplacementReminderEnabled()) {
                Log.d(TAG, "Replacement reminders are disabled");
                return Result.success();
            }
            
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                Log.d(TAG, "No user logged in");
                return Result.success();
            }
            
            androidx.work.Data inputData = getInputData();
            String receiptId = inputData.getString("receiptId");
            boolean isOneTimeNotification = inputData.getBoolean("isOneTimeNotification", false);
            
            if (isOneTimeNotification && receiptId != null && !receiptId.isEmpty()) {
                Log.d(TAG, "Processing one-time notification for receipt: " + receiptId);
                return handleOneTimeNotification(context, receiptId);
            }
            
            String userId = auth.getCurrentUser().getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            
            int replacementDays = prefs.getReplacementDays();
            int notificationDaysBefore = prefs.getNotificationDaysBefore();
            
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.add(Calendar.DAY_OF_YEAR, -(replacementDays - notificationDaysBefore));
            long receiptDateStart = calendar.getTimeInMillis();
            
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            long receiptDateEnd = calendar.getTimeInMillis();
            
            Log.d(TAG, "Checking receipts with date between " + new Date(receiptDateStart) + " and " + new Date(receiptDateEnd));
            
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final Result[] workResult = {Result.success()};
            
            db.collection("users")
                .document(userId)
                .collection("receipts")
                .whereGreaterThanOrEqualTo("receipt.receiptDateTimestamp", receiptDateStart)
                .whereLessThan("receipt.receiptDateTimestamp", receiptDateEnd)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Found " + querySnapshot.size() + " receipts to check");
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        try {
                            Receipt receipt = parseReceiptFromDocument(document);
                            if (receipt != null) {
                                checkAndNotifyReplacementPeriod(context, receipt, replacementDays, notificationDaysBefore);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing receipt: " + document.getId(), e);
                        }
                    }
                    latch.countDown();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching receipts", e);
                    workResult[0] = Result.retry();
                    latch.countDown();
                });
            
            try {
                latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for query", e);
                return Result.retry();
            }
            
            return workResult[0];
        } catch (Exception e) {
            Log.e(TAG, "Error in replacement period worker", e);
            return Result.retry();
        }
    }
    
    private void checkAndNotifyReplacementPeriod(Context context, Receipt receipt, int replacementDays, int notificationDaysBefore) {
        if (receipt.getReceipt() == null) {
            return;
        }
        
        long customNotificationTimestamp = receipt.getReceipt().getCustomNotificationTimestamp();
        if (customNotificationTimestamp > 0) {
            Log.d(TAG, "Skipping periodic notification for receipt " + receipt.getId() + " - has custom notification timestamp");
            return;
        }
        
        long receiptDate = receipt.getReceipt().getReceiptDateTimestamp();
        if (receiptDate == 0) {
            receiptDate = receipt.getReceipt().getDateTimestamp();
        }
        
        if (receiptDate == 0) {
            return;
        }
        long replacementEndDate = receiptDate + (replacementDays * 24 * 60 * 60 * 1000L);
        long currentTime = System.currentTimeMillis();
        
        long daysRemaining = (replacementEndDate - currentTime) / (24 * 60 * 60 * 1000L);
        
        if (daysRemaining >= 0 && daysRemaining <= notificationDaysBefore) {
            Log.d(TAG, "Sending periodic notification for receipt: " + receipt.getId() + ", days remaining: " + daysRemaining);
            NotificationHelper.showReplacementPeriodNotification(
                context, 
                receipt, 
                (int) daysRemaining
            );
        }
    }
    
    private Result handleOneTimeNotification(Context context, String receiptId) {
        Log.d(TAG, "handleOneTimeNotification called for receipt: " + receiptId);
        Log.d(TAG, "App context: " + context.getClass().getName());
        Log.d(TAG, "Current time: " + System.currentTimeMillis());
        
        try {
            try {
                FirebaseApp.getInstance();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Firebase not initialized, attempting to initialize", e);
                FirebaseApp.initializeApp(context);
            }
            
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                Log.w(TAG, "No user logged in for one-time notification");
                return Result.success();
            }
            
            String userId = auth.getCurrentUser().getUid();
            Log.d(TAG, "User ID: " + userId);
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final Result[] workResult = {Result.success()};
            final boolean[] notificationShown = {false};
            
            db.collection("users")
                .document(userId)
                .collection("receipts")
                .document(receiptId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    try {
                        if (documentSnapshot.exists()) {
                            Log.d(TAG, "Receipt document found, parsing...");
                            Receipt receipt = parseReceiptFromDocument(documentSnapshot);
                            if (receipt != null) {
                                Log.d(TAG, "Receipt parsed successfully. Store: " + 
                                    (receipt.getStore() != null && receipt.getStore().getName() != null 
                                        ? receipt.getStore().getName() : "null"));
                                Log.d(TAG, "Receipt date timestamp: " + 
                                    (receipt.getReceipt() != null ? receipt.getReceipt().getReceiptDateTimestamp() : "null"));
                                
                                NotificationPreferences prefs = new NotificationPreferences(context);
                                if (!prefs.isReplacementReminderEnabled()) {
                                    Log.d(TAG, "Replacement reminders disabled, not showing notification");
                                    latch.countDown();
                                    return;
                                }
                                
                                int replacementDays = prefs.getReplacementDays();
                                int notificationDaysBefore = prefs.getNotificationDaysBefore();
                                
                                long receiptDate = 0;
                                if (receipt.getReceipt() != null) {
                                    receiptDate = receipt.getReceipt().getReceiptDateTimestamp();
                                    if (receiptDate == 0) {
                                        receiptDate = receipt.getReceipt().getDateTimestamp();
                                        Log.d(TAG, "Using dateTimestamp as fallback: " + receiptDate);
                                    }
                                }
                                
                                int daysRemaining = 0;
                                if (receiptDate > 0) {
                                    long replacementEndDate = receiptDate + (replacementDays * 24 * 60 * 60 * 1000L);
                                    long currentTime = System.currentTimeMillis();
                                    daysRemaining = (int) ((replacementEndDate - currentTime) / (24 * 60 * 60 * 1000L));
                                    daysRemaining = Math.max(0, daysRemaining);
                                } else {
                                    Log.w(TAG, "No receipt date found for one-time notification, showing with 0 days remaining");
                                    daysRemaining = 0;
                                }
                                
                                Log.d(TAG, "Showing one-time notification for receipt: " + receiptId +
                                    ", days remaining: " + daysRemaining + 
                                    ", store: " + (receipt.getStore() != null && receipt.getStore().getName() != null 
                                        ? receipt.getStore().getName() : "null"));
                                
                                try {
                                    NotificationHelper.showReplacementPeriodNotification(
                                        context,
                                        receipt,
                                        daysRemaining
                                    );
                                    notificationShown[0] = true;
                                    Log.d(TAG, "Notification shown successfully");
                                } catch (Exception e) {
                                    Log.e(TAG, "Error showing notification", e);
                                    e.printStackTrace();
                                }
                            } else {
                                Log.w(TAG, "Failed to parse receipt from document");
                            }
                        } else {
                            Log.w(TAG, "Receipt not found for one-time notification: " + receiptId);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing one-time notification", e);
                        e.printStackTrace();
                        workResult[0] = Result.success();
                    } finally {
                        latch.countDown();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching receipt for one-time notification: " + e.getMessage(), e);
                    workResult[0] = Result.success();
                    latch.countDown();
                });
            
            try {
                boolean completed = latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
                if (!completed) {
                    Log.w(TAG, "Timeout waiting for receipt fetch");
                    return Result.success();
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for receipt fetch", e);
                return Result.success();
            }
            
            if (notificationShown[0]) {
                Log.d(TAG, "One-time notification successfully shown");
            }
            
            return workResult[0];
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in handleOneTimeNotification", e);
            return Result.success();
        }
    }
    
    private Receipt parseReceiptFromDocument(com.google.firebase.firestore.DocumentSnapshot document) {
        try {
            Receipt receipt = new Receipt();
            Map<String, Object> data = document.getData();
            if (data == null) return null;
            
            receipt.setId(document.getId());
            
            if (data.containsKey("receipt")) {
                Map<String, Object> receiptMap = (Map<String, Object>) data.get("receipt");
                Receipt.ReceiptInfo receiptInfo = new Receipt.ReceiptInfo();
                if (receiptMap != null) {
                    if (receiptMap.containsKey("dateTimestamp")) {
                        Object dateTimestamp = receiptMap.get("dateTimestamp");
                        if (dateTimestamp instanceof Number) {
                            receiptInfo.setDateTimestamp(((Number) dateTimestamp).longValue());
                        }
                    }
                    if (receiptMap.containsKey("receiptDateTimestamp")) {
                        Object receiptDateTimestamp = receiptMap.get("receiptDateTimestamp");
                        if (receiptDateTimestamp instanceof Number) {
                            receiptInfo.setReceiptDateTimestamp(((Number) receiptDateTimestamp).longValue());
                        }
                    }
                    if (receiptMap.containsKey("customNotificationTimestamp")) {
                        Object customNotificationTimestamp = receiptMap.get("customNotificationTimestamp");
                        if (customNotificationTimestamp instanceof Number) {
                            receiptInfo.setCustomNotificationTimestamp(((Number) customNotificationTimestamp).longValue());
                        }
                    }
                }
                receipt.setReceipt(receiptInfo);
            }
            
            Receipt.StoreInfo store = new Receipt.StoreInfo();
            boolean storeParsed = false;
            
            if (data.containsKey("store")) {
                Map<String, Object> storeMap = (Map<String, Object>) data.get("store");
                if (storeMap != null) {
                    if (storeMap.containsKey("name")) {
                        Object nameObj = storeMap.get("name");
                        if (nameObj != null) {
                            String storeName = nameObj.toString().trim();
                            if (!storeName.isEmpty() && !storeName.equals("null") && !storeName.equalsIgnoreCase("null")) {
                                store.setName(storeName);
                                storeParsed = true;
                                Log.d(TAG, "Parsed store name from store.name: '" + storeName + "'");
                            } else {
                                Log.w(TAG, "Store name is empty or 'null' string in store.name: '" + storeName + "'");
                            }
                        } else {
                            Log.w(TAG, "Store name object is null in store.name");
                        }
                    } else {
                        Log.w(TAG, "Store map doesn't contain 'name' key. Keys: " + storeMap.keySet());
                    }
                } else {
                    Log.w(TAG, "Store map is null");
                }
            } else {
                Log.w(TAG, "Data doesn't contain 'store' key. Available keys: " + data.keySet());
            }
            
            if (!storeParsed && data.containsKey("storeName")) {
                Object nameObj = data.get("storeName");
                if (nameObj != null) {
                    String storeName = nameObj.toString().trim();
                    if (!storeName.isEmpty() && !storeName.equals("null") && !storeName.equalsIgnoreCase("null")) {
                        store.setName(storeName);
                        storeParsed = true;
                        Log.d(TAG, "Parsed store name from storeName field: '" + storeName + "'");
                    } else {
                        Log.w(TAG, "Store name is empty or 'null' string in storeName field: '" + storeName + "'");
                    }
                }
            }
            
            if (storeParsed) {
                receipt.setStore(store);
                Log.d(TAG, "Store successfully parsed for receipt " + document.getId() + ": '" + store.getName() + "'");
            } else {
                Log.w(TAG, "No valid store name found for receipt " + document.getId() + ". Available data keys: " + data.keySet());
                receipt.setStore(null);
            }
            
            return receipt;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing receipt", e);
            return null;
        }
    }
    
}

