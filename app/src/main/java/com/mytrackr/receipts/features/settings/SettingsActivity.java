package com.mytrackr.receipts.features.settings;

import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.databinding.ActivitySettingsBinding;
import com.mytrackr.receipts.utils.NotificationPermissionHelper;
import com.mytrackr.receipts.utils.ThemePreferences;

public class SettingsActivity extends AppCompatActivity {
    private ActivitySettingsBinding binding;
    private ThemePreferences themePreferences;
    private static final int PERMISSION_REQUEST_CODE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        themePreferences = new ThemePreferences(this);

        setupToolbar();
        setupThemeSelection();
        setupNotificationPermission();

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupToolbar() {
        Toolbar toolbar = binding.toolbar.toolbar;
        toolbar.setTitle("");
        binding.toolbar.toolbarTitle.setText("Settings");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }

    private void setupThemeSelection() {
        RadioGroup radioGroupTheme = binding.radioGroupTheme;

        int currentTheme = themePreferences.getThemeMode();
        switch (currentTheme) {
            case ThemePreferences.THEME_MODE_LIGHT:
                binding.radioLight.setChecked(true);
                break;
            case ThemePreferences.THEME_MODE_DARK:
                binding.radioDark.setChecked(true);
                break;
            case ThemePreferences.THEME_MODE_SYSTEM:
            default:
                binding.radioSystem.setChecked(true);
                break;
        }

        radioGroupTheme.setOnCheckedChangeListener((group, checkedId) -> {
            int themeMode;
            if (checkedId == R.id.radioLight) {
                themeMode = ThemePreferences.THEME_MODE_LIGHT;
            } else if (checkedId == R.id.radioDark) {
                themeMode = ThemePreferences.THEME_MODE_DARK;
            } else {
                themeMode = ThemePreferences.THEME_MODE_SYSTEM;
            }

            themePreferences.setThemeMode(themeMode);
            recreate();
        });
    }

    private void setupNotificationPermission() {
        MaterialButton btnRequestPermission = binding.btnRequestNotificationPermission;

        updateNotificationPermissionStatus();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            btnRequestPermission.setVisibility(android.view.View.GONE);
            binding.textNotificationStatus.setText("Status: Enabled (Android 12 and below)");
            return;
        }

        btnRequestPermission.setOnClickListener(v -> {
            if (!NotificationPermissionHelper.hasNotificationPermission(this)) {
                NotificationPermissionHelper.requestNotificationPermission(this);
            } else {
                Toast.makeText(this, "Notification permission is already granted", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateNotificationPermissionStatus() {
        boolean hasPermission = NotificationPermissionHelper.hasNotificationPermission(this);
        if (hasPermission) {
            binding.textNotificationStatus.setText("Status: Granted");
            binding.btnRequestNotificationPermission.setText("Granted");
            binding.btnRequestNotificationPermission.setEnabled(false);
        } else {
            binding.textNotificationStatus.setText("Status: Not granted");
            binding.btnRequestNotificationPermission.setText("Enable");
            binding.btnRequestNotificationPermission.setEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        NotificationPermissionHelper.handlePermissionResult(this, requestCode, permissions, grantResults);
        updateNotificationPermissionStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationPermissionStatus();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
