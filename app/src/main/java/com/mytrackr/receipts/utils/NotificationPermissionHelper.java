package com.mytrackr.receipts.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class NotificationPermissionHelper {
    private static final String TAG = "NotificationPermissionHelper";
    private static final int PERMISSION_REQUEST_CODE = 200;
    
    public static boolean hasNotificationPermission(Context context) {
        try {
            boolean notificationsEnabled = false;
            try {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled();
            } catch (SecurityException e) {
                Log.w(TAG, "SecurityException when checking if notifications are enabled - likely disabled at system level", e);
                return false;
            }
            
            if (!notificationsEnabled) {
                Log.d(TAG, "Notifications are disabled at system level");
                return false;
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    int result = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS);
                    return result == PackageManager.PERMISSION_GRANTED;
                } catch (SecurityException e) {
                    Log.w(TAG, "SecurityException when checking POST_NOTIFICATIONS permission", e);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception when checking notification permission", e);
            return false;
        }
    }
    
    public static boolean requestNotificationPermission(Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (hasNotificationPermission(activity)) {
                    Log.d(TAG, "Notification permission already granted");
                    return true;
                }
                
                try {
                    Log.d(TAG, "Requesting POST_NOTIFICATIONS permission");
                    ActivityCompat.requestPermissions(
                        activity,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_CODE
                    );
                    return false;
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException when requesting notification permission", e);
                    Toast.makeText(activity, 
                        "Unable to request notification permission. Please enable notifications in System Settings.", 
                        Toast.LENGTH_LONG).show();
                    return false;
                } catch (Exception e) {
                    Log.e(TAG, "Exception when requesting notification permission", e);
                    Toast.makeText(activity, 
                        "Unable to request notification permission. Please try again.", 
                        Toast.LENGTH_LONG).show();
                    return false;
                }
            }

            boolean notificationsEnabled = false;
            try {
                notificationsEnabled = NotificationManagerCompat.from(activity).areNotificationsEnabled();
            } catch (SecurityException e) {
                Log.w(TAG, "SecurityException when checking if notifications are enabled - likely disabled at system level", e);
                Toast.makeText(activity, 
                    "Please enable notifications for this app in System Settings to receive reminders.", 
                    Toast.LENGTH_LONG).show();
                return false;
            }
            
            if (!notificationsEnabled) {
                Log.d(TAG, "Notifications disabled at system level");
                Toast.makeText(activity, 
                    "Please enable notifications for this app in System Settings to receive reminders.", 
                    Toast.LENGTH_LONG).show();
                return false;
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception when requesting notification permission", e);
            Toast.makeText(activity, 
                "Unable to request notification permission. Please try again.", 
                Toast.LENGTH_LONG).show();
            return false;
        }
    }

    public static void handlePermissionResult(Activity activity, int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(activity, "Notification permission granted", Toast.LENGTH_SHORT).show();
                
                NotificationPreferences prefs = new NotificationPreferences(activity);
                prefs.setExpenseAlertsEnabled(true);
                prefs.setReplacementReminderEnabled(true);
            } else {
                Toast.makeText(activity, 
                    "Notification permission is required to receive reminders. You can enable it in Settings.", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }
}

