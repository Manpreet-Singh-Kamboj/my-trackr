package com.mytrackr.receipts.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.mytrackr.receipts.receivers.NotificationAlarmReceiver;
import com.mytrackr.receipts.receivers.BudgetNotificationReceiver;

public class NotificationScheduler {
    private static final String TAG = "NotificationScheduler";
    
    public static void cancelReceiptNotification(Context context, String receiptId) {
        // Cancel AlarmManager notification
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            Intent intent = new Intent(context, NotificationAlarmReceiver.class);
            intent.putExtra(NotificationAlarmReceiver.EXTRA_RECEIPT_ID, receiptId);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                receiptId.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            alarmManager.cancel(pendingIntent);
            Log.d(TAG, "Cancelled alarm notification for receipt " + receiptId);
        }
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

        // Use AlarmManager for precise notification scheduling
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null, cannot schedule notification");
            return;
        }
        
        Intent intent = new Intent(context, NotificationAlarmReceiver.class);
        intent.putExtra(NotificationAlarmReceiver.EXTRA_RECEIPT_ID, receiptId);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            receiptId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        try {
            // Use exact alarm for precise timing (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Check if exact alarms are allowed
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        notificationTime,
                        pendingIntent
                    );
                    Log.d(TAG, "Scheduled exact alarm for receipt " + receiptId + " at " + sdf.format(new java.util.Date(notificationTime)));
                } else {
                    // Fallback to inexact alarm if exact alarms not allowed
                    Log.w(TAG, "Exact alarms not allowed, using inexact alarm");
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        notificationTime,
                        pendingIntent
                    );
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0-11: Use setExactAndAllowWhileIdle
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    notificationTime,
                    pendingIntent
                );
            } else {
                // Android 5.1 and below: Use setExact
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    notificationTime,
                    pendingIntent
                );
            }
            
            Log.d(TAG, "Scheduled notification for receipt " + receiptId + " in " + delayDays + " days, " + delayHours + " hours, " + delayMinutes + " minutes, " + delaySeconds + " seconds (total delay: " + delay + " ms)");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when scheduling alarm - exact alarms may not be allowed. Please enable exact alarms in system settings.", e);
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling alarm", e);
        }
    }
    
    public static void scheduleBudgetNotification(Context context, String month, String year, long notificationTime) {
        NotificationPreferences prefs = new NotificationPreferences(context);
        
        if (!prefs.isExpenseAlertsEnabled()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        if (notificationTime <= currentTime) {
            Log.d(TAG, "Budget notification time has passed, not scheduling");
            return;
        }
        
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null, cannot schedule budget notification");
            return;
        }
        
        Intent intent = new Intent(context, BudgetNotificationReceiver.class);
        intent.putExtra(BudgetNotificationReceiver.EXTRA_BUDGET_MONTH, month);
        intent.putExtra(BudgetNotificationReceiver.EXTRA_BUDGET_YEAR, year);
        
        String uniqueId = month + "_" + year;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            uniqueId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        notificationTime,
                        pendingIntent
                    );
                    Log.d(TAG, "Scheduled exact budget alarm for " + month + " " + year);
                } else {
                    Log.w(TAG, "Exact alarms not allowed, using inexact alarm for budget");
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        notificationTime,
                        pendingIntent
                    );
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    notificationTime,
                    pendingIntent
                );
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    notificationTime,
                    pendingIntent
                );
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when scheduling budget alarm", e);
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling budget alarm", e);
        }
    }
    
    public static void cancelBudgetNotification(Context context, String month, String year) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            Intent intent = new Intent(context, BudgetNotificationReceiver.class);
            intent.putExtra(BudgetNotificationReceiver.EXTRA_BUDGET_MONTH, month);
            intent.putExtra(BudgetNotificationReceiver.EXTRA_BUDGET_YEAR, year);
            
            String uniqueId = month + "_" + year;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                uniqueId.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            alarmManager.cancel(pendingIntent);
            Log.d(TAG, "Cancelled budget alarm for " + month + " " + year);
        }
    }

    /**
     * Schedule a daily budget check at 7:00 AM for the current month/year.
     * This is called from MainActivity and from the BudgetNotificationReceiver after each run.
     */
    public static void scheduleDailyBudgetCheck(Context context) {
        NotificationPreferences prefs = new NotificationPreferences(context);
        if (!prefs.isExpenseAlertsEnabled()) {
            Log.d(TAG, "Expense alerts disabled, not scheduling daily budget check");
            return;
        }

        java.util.Calendar calendar = java.util.Calendar.getInstance();
        String month = new java.text.SimpleDateFormat("MMMM", java.util.Locale.getDefault()).format(calendar.getTime());
        String year = String.valueOf(calendar.get(java.util.Calendar.YEAR));

        // Compute next 7:00 AM
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 7);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);

        long now = System.currentTimeMillis();
        long notificationTime = calendar.getTimeInMillis();

        // If it's already past 7:00 AM today, schedule for tomorrow
        if (notificationTime <= now) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1);
            notificationTime = calendar.getTimeInMillis();
        }

        // Guard: only schedule once per day even across cold starts
        android.content.SharedPreferences metaPrefs =
                context.getSharedPreferences("budget_notification_meta", android.content.Context.MODE_PRIVATE);
        String lastScheduledDate = metaPrefs.getString("last_scheduled_date", null);
        String todayDate = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date(notificationTime));

        if (todayDate.equals(lastScheduledDate)) {
            Log.d(TAG, "Daily budget check already scheduled for " + todayDate + ", skipping re-schedule");
            return;
        }

        Log.d(TAG, "Scheduling daily budget check at 7:00 AM, trigger time: " +
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .format(new java.util.Date(notificationTime)));

        // Just schedule; using the same PendingIntent/uniqueId means the latest call wins
        scheduleBudgetNotification(context, month, year, notificationTime);

        // Remember that we've scheduled for this date
        metaPrefs.edit().putString("last_scheduled_date", todayDate).apply();
    }
}

