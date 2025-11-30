package com.mytrackr.receipts.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.data.repository.ReceiptRepository;
import com.mytrackr.receipts.utils.NotificationHelper;
import com.mytrackr.receipts.utils.NotificationPreferences;

import java.util.concurrent.CountDownLatch;

public class NotificationAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationAlarmReceiver";
    public static final String EXTRA_RECEIPT_ID = "receipt_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm received for notification");

        try {
            // Initialize Firebase if not already initialized
            try {
                FirebaseApp.getInstance();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Firebase not initialized, attempting to initialize", e);
                FirebaseApp.initializeApp(context);
            }

            String receiptId = intent.getStringExtra(EXTRA_RECEIPT_ID);
            if (receiptId == null || receiptId.isEmpty()) {
                Log.w(TAG, "No receipt ID in alarm intent");
                return;
            }

            NotificationPreferences prefs = new NotificationPreferences(context);
            if (!prefs.isReplacementReminderEnabled()) {
                Log.d(TAG, "Replacement reminders disabled, not showing notification");
                return;
            }

            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                Log.w(TAG, "No user logged in for notification");
                return;
            }

            String userId = auth.getCurrentUser().getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            // Fetch receipt from Firestore
            CountDownLatch latch = new CountDownLatch(1);
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
                                Receipt receipt = ReceiptRepository.parseReceiptFromDocument(documentSnapshot);
                                if (receipt != null) {
                                    Log.d(TAG, "Receipt parsed successfully. Store: " +
                                            (receipt.getStore() != null && receipt.getStore().getName() != null
                                                    ? receipt.getStore().getName() : "null"));

                                    int replacementDays = prefs.getReplacementDays();

                                    // Calculate days remaining
                                    long receiptDate = 0;
                                    if (receipt.getReceipt() != null) {
                                        receiptDate = receipt.getReceipt().getReceiptDateTimestamp();
                                        if (receiptDate == 0) {
                                            receiptDate = receipt.getReceipt().getDateTimestamp();
                                            Log.d(TAG, "Using dateTimestamp as fallback: " + receiptDate);
                                        }
                                    }

                                    long daysRemaining = 0;
                                    if (receiptDate > 0) {
                                        long replacementEndDate = receiptDate + (replacementDays * 24 * 60 * 60 * 1000L);
                                        long currentTime = System.currentTimeMillis();
                                        daysRemaining = (replacementEndDate - currentTime) / (24 * 60 * 60 * 1000L);
                                    } else {
                                        Log.w(TAG, "No valid receipt date found, defaulting daysRemaining to 0.");
                                    }

                                    // Show notification
                                    Log.d(TAG, "Showing notification for receipt: " + receiptId +
                                            ", days remaining: " + daysRemaining);

                                    try {
                                        NotificationHelper.showReplacementPeriodNotification(
                                                context,
                                                receipt,
                                                (int) Math.max(0, daysRemaining)
                                        );
                                        notificationShown[0] = true;
                                        Log.d(TAG, "Notification shown successfully");
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error showing notification", e);
                                    }
                                } else {
                                    Log.w(TAG, "Failed to parse receipt from document");
                                }
                            } else {
                                Log.w(TAG, "Receipt not found: " + receiptId);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing notification", e);
                        } finally {
                            latch.countDown();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error fetching receipt for notification: " + e.getMessage(), e);
                        latch.countDown();
                    });

            try {
                boolean completed = latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
                if (!completed) {
                    Log.w(TAG, "Timeout waiting for receipt fetch");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for receipt fetch", e);
            }

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in onReceive", e);
        }
    }
}
