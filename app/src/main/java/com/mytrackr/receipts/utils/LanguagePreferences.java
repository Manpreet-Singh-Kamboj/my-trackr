package com.mytrackr.receipts.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class LanguagePreferences {
    private static final String PREF_NAME = "language_preferences";
    private static final String KEY_LANGUAGE_CODE = "language_code";
    
    public static final String LANGUAGE_ENGLISH = "en";
    public static final String LANGUAGE_FRENCH = "fr";
    public static final String LANGUAGE_HINDI = "hi";
    public static final String LANGUAGE_CHINESE = "zh";
    
    public static final String DEFAULT_LANGUAGE = LANGUAGE_ENGLISH;
    
    private final SharedPreferences preferences;
    
    public LanguagePreferences(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public String getLanguageCode() {
        return preferences.getString(KEY_LANGUAGE_CODE, DEFAULT_LANGUAGE);
    }
    
    public void setLanguageCode(String languageCode) {
        preferences.edit().putString(KEY_LANGUAGE_CODE, languageCode).apply();
    }
}

