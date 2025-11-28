package com.mytrackr.receipts.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

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
            
            String userId = auth.getCurrentUser().getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            
            int replacementDays = prefs.getReplacementDays();
            int notificationDaysBefore = prefs.getNotificationDaysBefore();
            
            // Calculate the target date range for receipts that need notification
            // We want receipts where: receiptDate + replacementDays - notificationDaysBefore = today (approximately)
            // So: receiptDate = today - (replacementDays - notificationDaysBefore)
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
            
            // Query receipts where dateTimestamp is in the target range
            // Use a blocking call since this is a Worker
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final Result[] workResult = {Result.success()};
            
            db.collection("users")
                .document(userId)
                .collection("receipts")
                .whereGreaterThanOrEqualTo("receipt.dateTimestamp", receiptDateStart)
                .whereLessThan("receipt.dateTimestamp", receiptDateEnd)
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
            
            // Wait for the query to complete (with timeout)
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
        
        // Use receiptDateTimestamp for notification calculation (actual receipt date)
        // Fallback to dateTimestamp if receiptDateTimestamp is not set
        long receiptDate = receipt.getReceipt().getReceiptDateTimestamp();
        if (receiptDate == 0) {
            receiptDate = receipt.getReceipt().getDateTimestamp();
        }
        
        if (receiptDate == 0) {
            return;
        }
        long replacementEndDate = receiptDate + (replacementDays * 24 * 60 * 60 * 1000L);
        long currentTime = System.currentTimeMillis();
        
        // Calculate days remaining
        long daysRemaining = (replacementEndDate - currentTime) / (24 * 60 * 60 * 1000L);
        
        // Only notify if we're within the notification window
        if (daysRemaining >= 0 && daysRemaining <= notificationDaysBefore) {
            Log.d(TAG, "Sending notification for receipt: " + receipt.getId() + ", days remaining: " + daysRemaining);
            NotificationHelper.showReplacementPeriodNotification(
                context, 
                receipt, 
                (int) daysRemaining
            );
        }
    }
    
    private Receipt parseReceiptFromDocument(QueryDocumentSnapshot document) {
        try {
            Receipt receipt = new Receipt();
            Map<String, Object> data = document.getData();
            if (data == null) return null;
            
            receipt.setId(document.getId());
            
            // Parse receipt information
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
                }
                receipt.setReceipt(receiptInfo);
            }
            
            // Parse store information
            if (data.containsKey("store")) {
                Map<String, Object> storeMap = (Map<String, Object>) data.get("store");
                Receipt.StoreInfo store = new Receipt.StoreInfo();
                if (storeMap != null) {
                    if (storeMap.containsKey("name")) store.setName((String) storeMap.get("name"));
                }
                receipt.setStore(store);
            }
            
            return receipt;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing receipt", e);
            return null;
        }
    }
}

