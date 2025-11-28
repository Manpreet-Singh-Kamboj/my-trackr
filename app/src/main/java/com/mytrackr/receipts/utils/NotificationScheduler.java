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
    
    public static void scheduleReplacementPeriodCheck(Context context) {
        NotificationPreferences prefs = new NotificationPreferences(context);
        
        if (!prefs.isReplacementReminderEnabled()) {
            Log.d(TAG, "Replacement reminders disabled, cancelling work");
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
            return;
        }
        
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();
        
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
            ReplacementPeriodWorker.class,
            15,
            TimeUnit.MINUTES
        )
        .setConstraints(constraints)
        .setInitialDelay(15, TimeUnit.MINUTES)
        .build();
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        );
        
        Log.d(TAG, "Scheduled replacement period check work");
    }
    
    public static void cancelReceiptNotification(Context context, String receiptId) {
        String uniqueWorkName = "replacement_notification_" + receiptId;
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName);
        Log.d(TAG, "Cancelled notification for receipt " + receiptId);
    }
    

    public static void scheduleReceiptReplacementNotification(Context context, String receiptId, long receiptDateTimestamp, int replacementDays, int notificationDaysBefore, long customNotificationTimestamp) {
        NotificationPreferences prefs = new NotificationPreferences(context);
        
        if (!prefs.isReplacementReminderEnabled()) {
            return;
        }
        
        cancelReceiptNotification(context, receiptId);
        
        long notificationTime;
        if (customNotificationTimestamp > 0) {
            notificationTime = customNotificationTimestamp;
            Log.d(TAG, "Using custom notification timestamp: " + customNotificationTimestamp);
        } else {
            notificationTime = receiptDateTimestamp +
                ((replacementDays - notificationDaysBefore) * 24 * 60 * 60 * 1000L);
        }
        
        long currentTime = System.currentTimeMillis();
        
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
        
        if (notificationTime <= currentTime) {
            Log.d(TAG, "Notification time has passed, not scheduling");
            return;
        }
        
        long delay = notificationTime - currentTime;
        long delayDays = delay / (24 * 60 * 60 * 1000L);
        long delayHours = (delay % (24 * 60 * 60 * 1000L)) / (60 * 60 * 1000L);
        long delayMinutes = (delay % (60 * 60 * 1000L)) / (60 * 1000L);
        long delaySeconds = (delay % (60 * 1000L)) / 1000L;

        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)
            .setRequiresDeviceIdle(false)
            .build();
        
        String uniqueWorkName = "replacement_notification_" + receiptId;
        
        android.util.Log.d(TAG, "Creating work request with receipt ID: " + receiptId);
        androidx.work.Data inputData = new androidx.work.Data.Builder()
            .putString("receiptId", receiptId)
            .putLong("notificationTime", notificationTime)
            .putBoolean("isOneTimeNotification", true)
            .build();
        
        androidx.work.OneTimeWorkRequest workRequest = new androidx.work.OneTimeWorkRequest.Builder(
            ReplacementPeriodWorker.class
        )
        .setInputData(inputData)
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .addTag("receipt_notification")
        .addTag("receipt_" + receiptId)
        .build();
        
        try {
            WorkManager workManager = WorkManager.getInstance(context);
            workManager.enqueueUniqueWork(
                uniqueWorkName,
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            );
            
            android.util.Log.d(TAG, "Work enqueued successfully. Work name: " + uniqueWorkName);
            Log.d(TAG, "Scheduled notification for receipt " + receiptId + " in " + delayDays + " days, " + delayHours + " hours, " + delayMinutes + " minutes, " + delaySeconds + " seconds (total delay: " + delay + " ms)");
        } catch (Exception e) {
            Log.e(TAG, "Error enqueueing work for notification", e);
            e.printStackTrace();
        }
    }
}

