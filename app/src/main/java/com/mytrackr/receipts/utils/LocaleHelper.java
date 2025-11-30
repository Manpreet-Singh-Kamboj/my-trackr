package com.mytrackr.receipts.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

public class LocaleHelper {
    
    public static void updateLocale(Context context, String languageCode) {
        Locale locale = getLocaleFromCode(languageCode);
        Locale.setDefault(locale);
        
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.setLocale(locale);
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
    }
    
    private static Locale getLocaleFromCode(String languageCode) {
        switch (languageCode) {
            case LanguagePreferences.LANGUAGE_FRENCH:
                return new Locale("fr");
            case LanguagePreferences.LANGUAGE_HINDI:
                return new Locale("hi");
            case LanguagePreferences.LANGUAGE_CHINESE:
                return new Locale("zh");
            case LanguagePreferences.LANGUAGE_ENGLISH:
            default:
                return new Locale("en");
        }
    }

    public static void applySavedLocale(Context context) {
        LanguagePreferences languagePreferences = new LanguagePreferences(context);
        String languageCode = languagePreferences.getLanguageCode();
        updateLocale(context, languageCode);
    }
}

