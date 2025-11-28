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
        
        // Periodic work: run every 15 minutes (minimum for PeriodicWorkRequest is 15 minutes)
        // Use 15 minutes instead of 5 to reduce frequency and prevent spam
        // This ensures we check receipts periodically but not too aggressively
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
            ReplacementPeriodWorker.class,
            15,
            TimeUnit.MINUTES
        )
        .setConstraints(constraints)
        .setInitialDelay(15, TimeUnit.MINUTES) // Don't run immediately when app opens
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
     * Cancel existing notification for a specific receipt
     */
    public static void cancelReceiptNotification(Context context, String receiptId) {
        String uniqueWorkName = "replacement_notification_" + receiptId;
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName);
        Log.d(TAG, "Cancelled notification for receipt " + receiptId);
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
        
        // Cancel any existing notification for this receipt first to prevent duplicates
        cancelReceiptNotification(context, receiptId);
        
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
        long delayMinutes = (delay % (60 * 60 * 1000L)) / (60 * 1000L);
        long delaySeconds = (delay % (60 * 1000L)) / 1000L;
        
        // Constraints: don't require network or battery (to ensure notifications work when app is killed)
        // WorkManager will still try to fetch from Firestore, but won't fail if network is unavailable
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)
            .setRequiresDeviceIdle(false)
            .build();
        
        // Use OneTimeWorkRequest for specific receipt notifications
        // Use unique work name per receipt to avoid overwriting
        String uniqueWorkName = "replacement_notification_" + receiptId;
        
        // Pass receipt ID through input data so worker can fetch receipt when notification fires
        android.util.Log.d(TAG, "Creating work request with receipt ID: " + receiptId);
        androidx.work.Data inputData = new androidx.work.Data.Builder()
            .putString("receiptId", receiptId)
            .putLong("notificationTime", notificationTime)
            .putBoolean("isOneTimeNotification", true)
            .build();
        
        // Always use milliseconds for precise timing
        // WorkManager supports millisecond precision for OneTimeWorkRequest
        androidx.work.OneTimeWorkRequest workRequest = new androidx.work.OneTimeWorkRequest.Builder(
            ReplacementPeriodWorker.class
        )
        .setInputData(inputData)
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .addTag("receipt_notification")
        .addTag("receipt_" + receiptId)
        .build();
        
        // Use unique work to ensure each receipt gets its own notification
        // REPLACE policy will cancel any existing work with the same name
        try {
            WorkManager workManager = WorkManager.getInstance(context);
            workManager.enqueueUniqueWork(
                uniqueWorkName,
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            );
            
            // Verify the work was enqueued
            android.util.Log.d(TAG, "Work enqueued successfully. Work name: " + uniqueWorkName);
            Log.d(TAG, "Scheduled notification for receipt " + receiptId + " in " + delayDays + " days, " + delayHours + " hours, " + delayMinutes + " minutes, " + delaySeconds + " seconds (total delay: " + delay + " ms)");
        } catch (Exception e) {
            Log.e(TAG, "Error enqueueing work for notification", e);
            e.printStackTrace();
        }
    }
}

