package com.mytrackr.receipts.utils;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.mytrackr.receipts.workers.ReplacementPeriodWorker;

import java.util.concurrent.TimeUnit;

public class NotificationScheduler {
    private static final String TAG = "NotificationScheduler";
    private static final String WORK_NAME = "replacement_period_check";
    
    /**
     * Schedule periodic work to check for replacement period notifications
     * This runs daily to check if any receipts are approaching their replacement deadline
     */
    public static void scheduleReplacementPeriodCheck(Context context) {
        NotificationPreferences prefs = new NotificationPreferences(context);
        
        if (!prefs.isReplacementReminderEnabled()) {
            Log.d(TAG, "Replacement reminders disabled, cancelling work");
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
            return;
        }
        
        // Constraints: requires network (to fetch receipts from Firestore)
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();
        
        // Periodic work: run every 12 hours (minimum for PeriodicWorkRequest)
        // This ensures we check receipts at least twice a day
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
            ReplacementPeriodWorker.class,
            5,
            TimeUnit.MINUTES
        )
        .setConstraints(constraints)
        .build();
        
        // Use unique work to replace any existing work with the same name
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        );
        
        Log.d(TAG, "Scheduled replacement period check work");
    }
    
    /**
     * Schedule a one-time notification for a specific receipt
     * This is called when a receipt is saved
     * @param customNotificationTimestamp If > 0, use this as the notification time. Otherwise, calculate from receiptDateTimestamp.
     */
    public static void scheduleReceiptReplacementNotification(Context context, String receiptId, long receiptDateTimestamp, int replacementDays, int notificationDaysBefore, long customNotificationTimestamp) {
        NotificationPreferences prefs = new NotificationPreferences(context);
        
        if (!prefs.isReplacementReminderEnabled()) {
            return;
        }
        
        // Use custom notification date if provided, otherwise calculate from receipt date
        long notificationTime;
        if (customNotificationTimestamp > 0) {
            notificationTime = customNotificationTimestamp;
            Log.d(TAG, "Using custom notification timestamp: " + customNotificationTimestamp);
        } else {
            // Calculate when to send the notification
            // Notification date = receipt date + replacement days - notification days before
            notificationTime = receiptDateTimestamp + 
                ((replacementDays - notificationDaysBefore) * 24 * 60 * 60 * 1000L);
        }
        
        long currentTime = System.currentTimeMillis();
        
        // Log detailed calculation info
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
        Log.d(TAG, "Notification calculation for receipt " + receiptId + ":");
        Log.d(TAG, "  Receipt date timestamp: " + receiptDateTimestamp + " (" + sdf.format(new java.util.Date(receiptDateTimestamp)) + ")");
        if (customNotificationTimestamp > 0) {
            Log.d(TAG, "  Using custom notification timestamp: " + customNotificationTimestamp + " (" + sdf.format(new java.util.Date(customNotificationTimestamp)) + ")");
        } else {
            Log.d(TAG, "  Replacement days: " + replacementDays);
            Log.d(TAG, "  Notification days before: " + notificationDaysBefore);
        }
        Log.d(TAG, "  Notification time: " + notificationTime + " (" + sdf.format(new java.util.Date(notificationTime)) + ")");
        Log.d(TAG, "  Current time: " + currentTime + " (" + sdf.format(new java.util.Date(currentTime)) + ")");
        
        // Only schedule if notification time is in the future
        if (notificationTime <= currentTime) {
            Log.d(TAG, "Notification time has passed, not scheduling");
            return;
        }
        
        long delay = notificationTime - currentTime;
        long delayDays = delay / (24 * 60 * 60 * 1000L);
        long delayHours = (delay % (24 * 60 * 60 * 1000L)) / (60 * 60 * 1000L);
        
        // Constraints: requires network (to fetch receipt from Firestore)
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();
        
        // Use OneTimeWorkRequest for specific receipt notifications
        // Use unique work name per receipt to avoid overwriting
        String uniqueWorkName = "replacement_notification_" + receiptId;
        androidx.work.OneTimeWorkRequest workRequest = new androidx.work.OneTimeWorkRequest.Builder(
            ReplacementPeriodWorker.class
        )
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .addTag("receipt_notification")
        .addTag("receipt_" + receiptId)
        .build();
        
        // Use unique work to ensure each receipt gets its own notification
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        );
        Log.d(TAG, "Scheduled notification for receipt " + receiptId + " in " + delayDays + " days, " + delayHours + " hours (total delay: " + delay + " ms)");
    }
}

