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
     * Schedule a weekly budget check at 7:00 AM every Monday for the current month/year.
     * This is called from MainActivity and from the BudgetNotificationReceiver after each run.
     */
    public static void scheduleWeeklyBudgetCheck(Context context) {
        NotificationPreferences prefs = new NotificationPreferences(context);
        if (!prefs.isExpenseAlertsEnabled()) {
            Log.d(TAG, "Expense alerts disabled, not scheduling weekly budget check");
            return;
        }

        java.util.Calendar calendar = java.util.Calendar.getInstance();
        String month = new java.text.SimpleDateFormat("MMMM", java.util.Locale.getDefault()).format(calendar.getTime());
        String year = String.valueOf(calendar.get(java.util.Calendar.YEAR));

        // Compute next Monday at 7:00 AM
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 7);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);

        int todayDow = calendar.get(java.util.Calendar.DAY_OF_WEEK);
        int daysUntilMonday = (java.util.Calendar.MONDAY - todayDow + 7) % 7;
        if (daysUntilMonday == 0) {
            // If we're already past 7:00 AM Monday, schedule for next week
            long now = System.currentTimeMillis();
            long candidate = calendar.getTimeInMillis();
            if (candidate <= now) {
                daysUntilMonday = 7;
            }
        } else {
            // Move to upcoming Monday
            calendar.add(java.util.Calendar.DAY_OF_YEAR, daysUntilMonday);
        }

        long notificationTime = calendar.getTimeInMillis();

        // Guard: only schedule once per week even across cold starts
        android.content.SharedPreferences metaPrefs =
                context.getSharedPreferences("budget_notification_meta", android.content.Context.MODE_PRIVATE);
        String lastScheduledWeek = metaPrefs.getString("last_scheduled_week", null);
        String weekKey = new java.text.SimpleDateFormat("YYYY-'W'ww", java.util.Locale.US)
                .format(new java.util.Date(notificationTime));

        if (weekKey.equals(lastScheduledWeek)) {
            Log.d(TAG, "Weekly budget check already scheduled for week " + weekKey + ", skipping re-schedule");
            return;
        }

        Log.d(TAG, "Scheduling weekly budget check (Monday 7:00 AM), trigger time: " +
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .format(new java.util.Date(notificationTime)));

        // Just schedule; using the same PendingIntent/uniqueId means the latest call wins
        scheduleBudgetNotification(context, month, year, notificationTime);

        // Remember that we've scheduled for this week
        metaPrefs.edit().putString("last_scheduled_week", weekKey).apply();
    }
}
