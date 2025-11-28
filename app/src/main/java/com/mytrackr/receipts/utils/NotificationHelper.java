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
        
        createNotificationChannel(context);
        
        String storeName = receipt.getStore() != null && receipt.getStore().getName() != null 
            ? receipt.getStore().getName() : "Your receipt";
        
        String title = "Replacement Period Ending Soon";
        String message = String.format("%s - %d day%s remaining for return/exchange", 
            storeName, daysRemaining, daysRemaining == 1 ? "" : "s");
        
        Intent intent = new Intent(context, ReceiptDetailsActivity.class);
        intent.putExtra(ReceiptDetailsActivity.EXTRA_RECEIPT, receipt);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            receipt.getId() != null ? receipt.getId().hashCode() : (int) System.currentTimeMillis(),
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
            int notificationId = receipt.getId() != null ? receipt.getId().hashCode() : (int) System.currentTimeMillis();
            notificationManager.notify(notificationId, notificationBuilder.build());
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

