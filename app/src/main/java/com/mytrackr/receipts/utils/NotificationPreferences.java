package com.mytrackr.receipts.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class NotificationPreferences {
    private static final String PREFS_NAME = "notification_preferences";
    
    // Keys
    private static final String KEY_REPLACEMENT_REMINDER = "replacement_reminder_enabled";
    private static final String KEY_RECEIPT_REMINDERS = "receipt_reminders_enabled";
    private static final String KEY_EXPENSE_ALERTS = "expense_alerts_enabled";
    private static final String KEY_BACKUP_REMINDERS = "backup_reminders_enabled";
    private static final String KEY_REPLACEMENT_DAYS = "replacement_days";
    private static final String KEY_NOTIFICATION_DAYS_BEFORE = "notification_days_before";
    
    // Default values
    private static final boolean DEFAULT_REPLACEMENT_REMINDER = false;
    private static final boolean DEFAULT_RECEIPT_REMINDERS = false;
    private static final boolean DEFAULT_EXPENSE_ALERTS = false;
    private static final boolean DEFAULT_BACKUP_REMINDERS = false;
    private static final int DEFAULT_REPLACEMENT_DAYS = 7;
    private static final int DEFAULT_NOTIFICATION_DAYS_BEFORE = 1;
    
    private SharedPreferences prefs;
    
    public NotificationPreferences(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    // Replacement Reminder
    public boolean isReplacementReminderEnabled() {
        return prefs.getBoolean(KEY_REPLACEMENT_REMINDER, DEFAULT_REPLACEMENT_REMINDER);
    }
    
    public void setReplacementReminderEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REPLACEMENT_REMINDER, enabled).apply();
    }
    
    // Receipt Reminders
    public boolean isReceiptRemindersEnabled() {
        return prefs.getBoolean(KEY_RECEIPT_REMINDERS, DEFAULT_RECEIPT_REMINDERS);
    }
    
    public void setReceiptRemindersEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_RECEIPT_REMINDERS, enabled).apply();
    }
    
    // Expense Alerts
    public boolean isExpenseAlertsEnabled() {
        return prefs.getBoolean(KEY_EXPENSE_ALERTS, DEFAULT_EXPENSE_ALERTS);
    }
    
    public void setExpenseAlertsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_EXPENSE_ALERTS, enabled).apply();
    }
    
    // Backup Reminders
    public boolean isBackupRemindersEnabled() {
        return prefs.getBoolean(KEY_BACKUP_REMINDERS, DEFAULT_BACKUP_REMINDERS);
    }
    
    public void setBackupRemindersEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BACKUP_REMINDERS, enabled).apply();
    }
    
    // Replacement Days
    public int getReplacementDays() {
        return prefs.getInt(KEY_REPLACEMENT_DAYS, DEFAULT_REPLACEMENT_DAYS);
    }
    
    public void setReplacementDays(int days) {
        prefs.edit().putInt(KEY_REPLACEMENT_DAYS, days).apply();
    }
    
    // Notification Days Before
    public int getNotificationDaysBefore() {
        return prefs.getInt(KEY_NOTIFICATION_DAYS_BEFORE, DEFAULT_NOTIFICATION_DAYS_BEFORE);
    }
    
    public void setNotificationDaysBefore(int days) {
        prefs.edit().putInt(KEY_NOTIFICATION_DAYS_BEFORE, days).apply();
    }
}

