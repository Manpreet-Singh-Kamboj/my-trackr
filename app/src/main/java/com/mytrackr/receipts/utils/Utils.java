package com.mytrackr.receipts.utils;

import android.content.res.ColorStateList;
import android.util.Log;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

public class Utils {
    private static Utils instance;

    public static synchronized Utils getInstance(){
        if(instance == null){
            instance = new Utils();
            Log.i("UTILS_INITIALIZED", "Utils Singleton Initialized");
        }
        return instance;
    }
    public void setFocusListener(TextInputLayout layout, EditText editText, int focusedColor, int unfocusedColor){
        editText.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                layout.setStartIconTintList(ColorStateList.valueOf(focusedColor));
            } else {
                layout.setStartIconTintList(ColorStateList.valueOf(unfocusedColor));
            }
        });
    }
}