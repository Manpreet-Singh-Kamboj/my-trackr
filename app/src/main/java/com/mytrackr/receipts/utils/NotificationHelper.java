package com.mytrackr.receipts.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.mytrackr.receipts.R;
import com.mytrackr.receipts.features.core.MainActivity;
import com.mytrackr.receipts.features.receipts.ReceiptDetailsActivity;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.data.model.Budget;

public class NotificationHelper {
    private static final String CHANNEL_ID = "receipt_notifications";
    private static final String CHANNEL_NAME = "Receipt Notifications";

    private static final java.util.Map<String, Long> recentNotifications = new java.util.HashMap<>();
    private static final long NOTIFICATION_COOLDOWN_MS = 5 * 1000;

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for receipt and expense management");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                android.util.Log.d("NotificationHelper", "Notification channel created: " + CHANNEL_ID);
            }
        }
    }

    public static void showReplacementPeriodNotification(Context context, Receipt receipt, int daysRemaining) {
        if (!new NotificationPreferences(context).isReplacementReminderEnabled()) {
            return;
        }

        if (receipt == null || receipt.getId() == null) {
            android.util.Log.w("NotificationHelper", "Cannot show notification - receipt or receipt ID is null");
            return;
        }

        String receiptId = receipt.getId();
        long currentTime = System.currentTimeMillis();

        synchronized (recentNotifications) {
            Long lastShown = recentNotifications.get(receiptId);
            if (lastShown != null && (currentTime - lastShown) < NOTIFICATION_COOLDOWN_MS) {
                android.util.Log.d("NotificationHelper", "Skipping duplicate notification for receipt " + receiptId +
                        " (shown " + (currentTime - lastShown) + " ms ago)");
                return;
            }

            recentNotifications.put(receiptId, currentTime);
            recentNotifications.entrySet().removeIf(entry ->
                    (currentTime - entry.getValue()) > NOTIFICATION_COOLDOWN_MS);
        }

        createNotificationChannel(context);

        String storeName = "Your receipt";
        if (receipt.getStore() != null && receipt.getStore().getName() != null) {
            String name = receipt.getStore().getName().trim();
            if (!name.isEmpty() && !name.equals("null") && !name.equalsIgnoreCase("null")) {
                storeName = name;
            }
        }

        android.util.Log.d("NotificationHelper", "Showing notification for receipt " + receipt.getId());
        android.util.Log.d("NotificationHelper", "  Store object: " + (receipt.getStore() != null ? "exists" : "null"));
        android.util.Log.d("NotificationHelper", "  Store name: '" + storeName + "'");
        android.util.Log.d("NotificationHelper", "  Days remaining: " + daysRemaining);

        String title = "Replacement Period Ending Soon";
        String message = String.format("%s - %d day%s remaining for return/exchange",
                storeName, daysRemaining, daysRemaining == 1 ? "" : "s");

        Intent intent = new Intent(context, ReceiptDetailsActivity.class);
        intent.putExtra(ReceiptDetailsActivity.EXTRA_RECEIPT, receipt);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                receipt.getId().hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_receipt_icon)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message));

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            int notificationId = receipt.getId().hashCode();
            notificationManager.notify(notificationId, notificationBuilder.build());
            android.util.Log.d("NotificationHelper", "Notification shown with ID: " + notificationId);
        }
    }

    public static void showTestNotification(Context context) {
        android.util.Log.d("NotificationHelper", "=== showTestNotification called ===");
        createNotificationChannel(context);

        String title = "Test Notification";
        String message = "This is a test notification to verify the notification system is working correctly.";

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_receipt_icon)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message));

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            int notificationId = (int) System.currentTimeMillis();
            notificationManager.notify(notificationId, notificationBuilder.build());
            android.util.Log.d("NotificationHelper", "Test notification shown with ID: " + notificationId);
        } else {
            android.util.Log.e("NotificationHelper", "NotificationManager is null for test notification");
        }
    }

    public static void showBudgetAlertNotification(Context context, Budget budget, String status) {
        android.util.Log.d("NotificationHelper", "=== showBudgetAlertNotification START ===");
        android.util.Log.d("NotificationHelper", "Status: " + status);

        if (context == null) {
            android.util.Log.e("NotificationHelper", "Context is null!");
            return;
        }

        NotificationPreferences prefs = new NotificationPreferences(context);
        boolean alertsEnabled = prefs.isExpenseAlertsEnabled();
        android.util.Log.d("NotificationHelper", "Expense alerts enabled: " + alertsEnabled);

        if (!alertsEnabled) {
            android.util.Log.w("NotificationHelper", "Expense alerts are disabled, not showing notification");
            return;
        }

        if (budget == null) {
            android.util.Log.e("NotificationHelper", "Cannot show notification - budget is null");
            return;
        }

        android.util.Log.d("NotificationHelper", "Budget: $" + budget.getAmount() + ", Spent: $" + budget.getSpent() + ", Percentage: " + budget.getSpentPercentage() + "%");

        android.util.Log.d("NotificationHelper", "Creating notification channel...");
        createNotificationChannel(context);
        android.util.Log.d("NotificationHelper", "Notification channel created");

        java.text.NumberFormat currencyFormat = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.CANADA);
        String spentAmount = currencyFormat.format(budget.getSpent());
        String budgetAmount = currencyFormat.format(budget.getAmount());
        double percentage = budget.getSpentPercentage();

        String title;
        String message;
        int priority = NotificationCompat.PRIORITY_DEFAULT;

        if (status.equals("budget_exceeded")) {
            title = context.getString(R.string.budget_exceeded);
            message = String.format("Weekly summary: You have exceeded your budget. Spent %s out of %s", spentAmount, budgetAmount);
            priority = NotificationCompat.PRIORITY_HIGH;
        } else if (status.equals("almost_exceeded")) {
            title = context.getString(R.string.almost_exceeded);
            message = String.format("Weekly summary: You are close to exceeding your budget. Spent %s out of %s", spentAmount, budgetAmount);
            priority = NotificationCompat.PRIORITY_DEFAULT;
        } else if (status.equals("spending_high")) {
            title = context.getString(R.string.spending_high);
            message = String.format("Weekly summary: You are spending high. Spent %s out of %s", spentAmount, budgetAmount);
            priority = NotificationCompat.PRIORITY_DEFAULT;
        } else {
            // "on_track" or any other status: still send a friendly summary
            title = "Budget on track";
            message = String.format("Weekly summary: Your spending is on track. Spent %s out of %s", spentAmount, budgetAmount);
            priority = NotificationCompat.PRIORITY_DEFAULT;
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (budget.getMonth() + "_" + budget.getYear() + "_" + status).hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_transaction)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message));

        android.util.Log.d("NotificationHelper", "Getting NotificationManager...");
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            android.util.Log.e("NotificationHelper", "NotificationManager is null, cannot show notification");
            return;
        }

        android.util.Log.d("NotificationHelper", "NotificationManager obtained");

        // Check if notifications are enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            boolean areNotificationsEnabled = notificationManager.areNotificationsEnabled();
            android.util.Log.d("NotificationHelper", "System notifications enabled: " + areNotificationsEnabled);
            if (!areNotificationsEnabled) {
                android.util.Log.w("NotificationHelper", "Notifications are disabled in system settings - notification will not show");
            }
        }

        // Check channel on Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
            if (channel != null) {
                android.util.Log.d("NotificationHelper", "Channel found - ID: " + channel.getId() +
                        ", Importance: " + channel.getImportance() +
                        ", Enabled: " + (channel.getImportance() != NotificationManager.IMPORTANCE_NONE));
            } else {
                android.util.Log.w("NotificationHelper", "Channel not found! Creating it again...");
                createNotificationChannel(context);
            }
        }

        int notificationId = (budget.getMonth() + "_" + budget.getYear() + "_" + status).hashCode();
        android.util.Log.d("NotificationHelper", "Notification ID: " + notificationId);
        android.util.Log.d("NotificationHelper", "Title: " + title);
        android.util.Log.d("NotificationHelper", "Message: " + message);

        try {
            android.util.Log.d("NotificationHelper", "Calling notificationManager.notify()...");
            notificationManager.notify(notificationId, notificationBuilder.build());
            android.util.Log.d("NotificationHelper", "=== NOTIFICATION SHOWN SUCCESSFULLY ===");
            android.util.Log.d("NotificationHelper", "Notification ID: " + notificationId +
                    ", Title: " + title + ", Message: " + message);
        } catch (Exception e) {
            android.util.Log.e("NotificationHelper", "ERROR showing notification", e);
            e.printStackTrace();
        }
    }
}

