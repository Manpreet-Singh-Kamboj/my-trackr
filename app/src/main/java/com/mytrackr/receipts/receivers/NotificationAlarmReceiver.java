package com.mytrackr.receipts.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.data.repository.ReceiptRepository;
import com.mytrackr.receipts.utils.NotificationHelper;
import com.mytrackr.receipts.utils.NotificationPreferences;

import java.util.concurrent.CountDownLatch;

public class NotificationAlarmReceiver extends BroadcastReceiver {
    public static final String EXTRA_RECEIPT_ID = "receipt_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        FirebaseCrashlytics.getInstance().log("D/NotificationAlarmReceiver: Alarm received for notification");

        try {
            try {
                FirebaseApp.getInstance();
            } catch (IllegalStateException e) {
                FirebaseCrashlytics.getInstance().log("E/NotificationAlarmReceiver: Firebase not initialized, attempting to initialize");
                FirebaseCrashlytics.getInstance().recordException(e);
                FirebaseApp.initializeApp(context);
            }

            String receiptId = intent.getStringExtra(EXTRA_RECEIPT_ID);
            if (receiptId == null || receiptId.isEmpty()) {
                FirebaseCrashlytics.getInstance().log("W/NotificationAlarmReceiver: No receipt ID in alarm intent");
                return;
            }

            NotificationPreferences prefs = new NotificationPreferences(context);
            if (!prefs.isReplacementReminderEnabled()) {
                FirebaseCrashlytics.getInstance().log("D/NotificationAlarmReceiver: Replacement reminders disabled, not showing notification");
                return;
            }

            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                FirebaseCrashlytics.getInstance().log("W/NotificationAlarmReceiver: No user logged in for notification");
                return;
            }

            String userId = auth.getCurrentUser().getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            CountDownLatch latch = new CountDownLatch(1);

            db.collection("users")
                    .document(userId)
                    .collection("receipts")
                    .document(receiptId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        try {
                            if (documentSnapshot.exists()) {
                                FirebaseCrashlytics.getInstance().log("D/NotificationAlarmReceiver: Receipt document found, parsing...");
                                Receipt receipt = ReceiptRepository.parseReceiptFromDocument(documentSnapshot);
                                if (receipt != null) {
                                    FirebaseCrashlytics.getInstance().log("D/NotificationAlarmReceiver: Receipt parsed successfully. Store: " +
                                            (receipt.getStore() != null && receipt.getStore().getName() != null
                                                    ? receipt.getStore().getName() : "null"));

                                    int replacementDays = prefs.getReplacementDays();

                                    long receiptDate = 0;
                                    if (receipt.getReceipt() != null) {
                                        receiptDate = receipt.getReceipt().getReceiptDateTimestamp();
                                        if (receiptDate == 0) {
                                            receiptDate = receipt.getReceipt().getDateTimestamp();
                                            FirebaseCrashlytics.getInstance().log("D/NotificationAlarmReceiver: Using dateTimestamp as fallback: " + receiptDate);
                                        }
                                    }

                                    long daysRemaining = 0;
                                    if (receiptDate > 0) {
                                        long replacementEndDate = receiptDate + (replacementDays * 24 * 60 * 60 * 1000L);
                                        long currentTime = System.currentTimeMillis();
                                        daysRemaining = (replacementEndDate - currentTime) / (24 * 60 * 60 * 1000L);
                                    } else {
                                        FirebaseCrashlytics.getInstance().log("W/NotificationAlarmReceiver: No valid receipt date found, defaulting daysRemaining to 0.");
                                    }

                                    FirebaseCrashlytics.getInstance().log("D/NotificationAlarmReceiver: Showing notification for receipt: " + receiptId + ", days remaining: " + daysRemaining);

                                    try {
                                        NotificationHelper.showReplacementPeriodNotification(
                                                context,
                                                receipt,
                                                (int) Math.max(0, daysRemaining)
                                        );
                                        FirebaseCrashlytics.getInstance().log("D/NotificationAlarmReceiver: Notification shown successfully");
                                    } catch (Exception e) {
                                        FirebaseCrashlytics.getInstance().log("E/NotificationAlarmReceiver: Error showing notification");
                                        FirebaseCrashlytics.getInstance().recordException(e);
                                    }
                                } else {
                                    FirebaseCrashlytics.getInstance().log("W/NotificationAlarmReceiver: Failed to parse receipt from document");
                                }
                            } else {
                                FirebaseCrashlytics.getInstance().log("W/NotificationAlarmReceiver: Receipt not found: " + receiptId);
                            }
                        } catch (Exception e) {
                            FirebaseCrashlytics.getInstance().log("E/NotificationAlarmReceiver: Error processing notification");
                            FirebaseCrashlytics.getInstance().recordException(e);
                        } finally {
                            latch.countDown();
                        }
                    })
                    .addOnFailureListener(e -> {
                        FirebaseCrashlytics.getInstance().log("E/NotificationAlarmReceiver: Error fetching receipt for notification: " + e.getMessage());
                        FirebaseCrashlytics.getInstance().recordException(e);
                        latch.countDown();
                    });

            try {
                boolean completed = latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
                if (!completed) {
                    FirebaseCrashlytics.getInstance().log("W/NotificationAlarmReceiver: Timeout waiting for receipt fetch");
                }
            } catch (InterruptedException e) {
                FirebaseCrashlytics.getInstance().log("E/NotificationAlarmReceiver: Interrupted while waiting for receipt fetch");
                FirebaseCrashlytics.getInstance().recordException(e);
            }

        } catch (Exception e) {
            FirebaseCrashlytics.getInstance().log("E/NotificationAlarmReceiver: Unexpected error in onReceive");
            FirebaseCrashlytics.getInstance().recordException(e);
        }
    }
}
