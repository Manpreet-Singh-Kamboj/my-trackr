package com.mytrackr.receipts.features.notifications;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.databinding.ActivityNotificationSettingsBinding;
import com.mytrackr.receipts.utils.NotificationHelper;
import com.mytrackr.receipts.utils.NotificationPermissionHelper;
import com.mytrackr.receipts.utils.NotificationPreferences;

public class NotificationSettingsActivity extends AppCompatActivity {
    private ActivityNotificationSettingsBinding binding;
    private NotificationPreferences notificationPrefs;
    private SwitchMaterial switchReplacementReminder;
    private SwitchMaterial switchExpenseAlerts;
    private TextInputEditText editReplacementDays;
    private TextInputEditText editNotificationDaysBefore;
    private final static int PERMISSION_REQUEST_CODE = 2345;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        notificationPrefs = new NotificationPreferences(this);
        
        setupToolbar();
        initViews();
        loadPreferences();
        setupListeners();
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    
    private void setupToolbar() {
        Toolbar toolbar = binding.toolbar.toolbar;
        toolbar.setTitle("");
        binding.toolbar.toolbarTitle.setText("Notifications");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }
    
    private void initViews() {
        switchReplacementReminder = binding.switchReplacementReminder;
        switchExpenseAlerts = binding.switchExpenseAlerts;
        editReplacementDays = binding.editReplacementDays;
        editNotificationDaysBefore = binding.editNotificationDaysBefore;
    }
    
    private void loadPreferences() {
        switchReplacementReminder.setChecked(notificationPrefs.isReplacementReminderEnabled());
        switchExpenseAlerts.setChecked(notificationPrefs.isExpenseAlertsEnabled());
        editReplacementDays.setText(String.valueOf(notificationPrefs.getReplacementDays()));
        editNotificationDaysBefore.setText(String.valueOf(notificationPrefs.getNotificationDaysBefore()));
    }
    
    private void setupListeners() {
        switchReplacementReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            notificationPrefs.setReplacementReminderEnabled(isChecked);
        });

        switchExpenseAlerts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            notificationPrefs.setExpenseAlertsEnabled(isChecked);
        });
        

        editReplacementDays.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveReplacementDays();
            }
        });
        
        editNotificationDaysBefore.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveNotificationDaysBefore();
            }
        });
    }
    
    private void saveReplacementDays() {
        String daysStr = editReplacementDays.getText() != null ? editReplacementDays.getText().toString() : "";
        if (!TextUtils.isEmpty(daysStr)) {
            try {
                int days = Integer.parseInt(daysStr);
                if (days > 0 && days <= 365) {
                    notificationPrefs.setReplacementDays(days);
                } else {
                    Toast.makeText(this, "Please enter a value between 1 and 365", Toast.LENGTH_SHORT).show();
                    editReplacementDays.setText(String.valueOf(notificationPrefs.getReplacementDays()));
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show();
                editReplacementDays.setText(String.valueOf(notificationPrefs.getReplacementDays()));
            }
        }
    }
    
    private void saveNotificationDaysBefore() {
        String daysStr = editNotificationDaysBefore.getText() != null ? editNotificationDaysBefore.getText().toString() : "";
        if (!TextUtils.isEmpty(daysStr)) {
            try {
                int days = Integer.parseInt(daysStr);
                if (days >= 0 && days <= 30) {
                    notificationPrefs.setNotificationDaysBefore(days);
                } else {
                    Toast.makeText(this, "Please enter a value between 0 and 30", Toast.LENGTH_SHORT).show();
                    editNotificationDaysBefore.setText(String.valueOf(notificationPrefs.getNotificationDaysBefore()));
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show();
                editNotificationDaysBefore.setText(String.valueOf(notificationPrefs.getNotificationDaysBefore()));
            }
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            saveReplacementDays();
            saveNotificationDaysBefore();
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        saveReplacementDays();
        saveNotificationDaysBefore();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
                notificationPrefs.setExpenseAlertsEnabled(true);
                notificationPrefs.setReplacementReminderEnabled(true);
                switchReplacementReminder.setChecked(true);
                switchExpenseAlerts.setChecked(true);
            } else {
                Toast.makeText(this, "Notification permission is required to receive reminders", Toast.LENGTH_LONG).show();
            }
        }
    }
}

