package com.mytrackr.receipts.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemePreferences {
    private static final String PREF_NAME = "theme_preferences";
    private static final String KEY_THEME_MODE = "theme_mode";
    
    // Theme mode constants
    public static final int THEME_MODE_SYSTEM = 0;
    public static final int THEME_MODE_LIGHT = 1;
    public static final int THEME_MODE_DARK = 2;
    
    private final SharedPreferences preferences;
    
    public ThemePreferences(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Get the current theme mode
     * @return Theme mode (0 = System, 1 = Light, 2 = Dark)
     */
    public int getThemeMode() {
        return preferences.getInt(KEY_THEME_MODE, THEME_MODE_SYSTEM);
    }
    
    /**
     * Set the theme mode
     * @param themeMode Theme mode (0 = System, 1 = Light, 2 = Dark)
     */
    public void setThemeMode(int themeMode) {
        preferences.edit().putInt(KEY_THEME_MODE, themeMode).apply();
        applyThemeMode(themeMode);
    }
    
    /**
     * Apply the theme mode to the app
     * @param themeMode Theme mode to apply
     */
    public static void applyThemeMode(int themeMode) {
        switch (themeMode) {
            case THEME_MODE_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_MODE_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
    
    /**
     * Apply the saved theme mode on app startup
     * Should be called in Application class or MainActivity onCreate
     */
    public void applySavedThemeMode() {
        int themeMode = getThemeMode();
        applyThemeMode(themeMode);
    }
}

