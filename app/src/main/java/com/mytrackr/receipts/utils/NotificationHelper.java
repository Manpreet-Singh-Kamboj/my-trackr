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

public class NotificationHelper {
    private static final String CHANNEL_ID = "receipt_notifications";
    private static final String CHANNEL_NAME = "Receipt Notifications";
    
    // Track recently shown notifications to prevent duplicates in the same execution (5 seconds)
    // This prevents the same notification from being triggered multiple times in quick succession
    private static final java.util.Map<String, Long> recentNotifications = new java.util.HashMap<>();
    private static final long NOTIFICATION_COOLDOWN_MS = 5 * 1000; // 5 seconds - only to prevent duplicates in same execution
    
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications for receipt and expense management");
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
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
        
        // Check if we've shown a notification for this receipt in the last 5 seconds (prevent duplicates in same execution)
        String receiptId = receipt.getId();
        long currentTime = System.currentTimeMillis();
        
        synchronized (recentNotifications) {
            Long lastShown = recentNotifications.get(receiptId);
            if (lastShown != null && (currentTime - lastShown) < NOTIFICATION_COOLDOWN_MS) {
                android.util.Log.d("NotificationHelper", "Skipping duplicate notification for receipt " + receiptId + 
                    " (shown " + (currentTime - lastShown) + " ms ago)");
                return;
            }
            
            // Mark this notification as shown and clean up old entries
            recentNotifications.put(receiptId, currentTime);
            recentNotifications.entrySet().removeIf(entry -> 
                (currentTime - entry.getValue()) > NOTIFICATION_COOLDOWN_MS);
        }
        
        createNotificationChannel(context);
        
        // Get store name with better null/empty handling
        String storeName = "Your receipt"; // Default fallback
        if (receipt.getStore() != null && receipt.getStore().getName() != null) {
            String name = receipt.getStore().getName().trim();
            if (!name.isEmpty() && !name.equals("null") && !name.equalsIgnoreCase("null")) {
                storeName = name;
            }
        }
        
        // Log for debugging
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
            // Use receipt ID hash as notification ID to ensure same receipt gets same notification ID
            int notificationId = receipt.getId().hashCode();
            notificationManager.notify(notificationId, notificationBuilder.build());
            android.util.Log.d("NotificationHelper", "Notification shown with ID: " + notificationId);
        }
    }
    
    /**
     * Test method to show a sample notification immediately
     * Useful for testing notification display and permissions
     * 
     * @param context Application context
     */
    public static void showTestNotification(Context context) {
        createNotificationChannel(context);
        
        String title = "Test Notification";
        String message = "This is a test notification to verify the notification system is working correctly.";
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(message));

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
        }
    }
}


