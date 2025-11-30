package com.mytrackr.receipts.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class NotificationPreferences {
    private static final String PREFS_NAME = "notification_preferences";

    private static final String KEY_REPLACEMENT_REMINDER = "replacement_reminder_enabled";
    private static final String KEY_EXPENSE_ALERTS = "expense_alerts_enabled";
    private static final String KEY_REPLACEMENT_DAYS = "replacement_days";
    private static final String KEY_NOTIFICATION_DAYS_BEFORE = "notification_days_before";

    private static final boolean DEFAULT_REPLACEMENT_REMINDER = false;
    private static final boolean DEFAULT_EXPENSE_ALERTS = false;
    private static final int DEFAULT_REPLACEMENT_DAYS = 7;
    private static final int DEFAULT_NOTIFICATION_DAYS_BEFORE = 1;

    private SharedPreferences prefs;

    public NotificationPreferences(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isReplacementReminderEnabled() {
        return prefs.getBoolean(KEY_REPLACEMENT_REMINDER, DEFAULT_REPLACEMENT_REMINDER);
    }

    public void setReplacementReminderEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REPLACEMENT_REMINDER, enabled).apply();
    }

    public boolean isExpenseAlertsEnabled() {
        return prefs.getBoolean(KEY_EXPENSE_ALERTS, DEFAULT_EXPENSE_ALERTS);
    }

    public void setExpenseAlertsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_EXPENSE_ALERTS, enabled).apply();
    }

    public int getReplacementDays() {
        return prefs.getInt(KEY_REPLACEMENT_DAYS, DEFAULT_REPLACEMENT_DAYS);
    }

    public void setReplacementDays(int days) {
        prefs.edit().putInt(KEY_REPLACEMENT_DAYS, days).apply();
    }

    public int getNotificationDaysBefore() {
        return prefs.getInt(KEY_NOTIFICATION_DAYS_BEFORE, DEFAULT_NOTIFICATION_DAYS_BEFORE);
    }

    public void setNotificationDaysBefore(int days) {
        prefs.edit().putInt(KEY_NOTIFICATION_DAYS_BEFORE, days).apply();
    }
}
