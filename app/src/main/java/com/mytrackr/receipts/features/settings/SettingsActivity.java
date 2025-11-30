package com.mytrackr.receipts.features.settings;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.databinding.ActivitySettingsBinding;
import com.mytrackr.receipts.utils.LanguagePreferences;
import com.mytrackr.receipts.utils.LocaleHelper;
import com.mytrackr.receipts.utils.NotificationPermissionHelper;
import com.mytrackr.receipts.utils.ThemePreferences;

public class SettingsActivity extends AppCompatActivity {
    private ActivitySettingsBinding binding;
    private ThemePreferences themePreferences;
    private LanguagePreferences languagePreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        themePreferences = new ThemePreferences(this);
        languagePreferences = new LanguagePreferences(this);

        setupToolbar();
        setupThemeSelection();
        setupLanguageSelection();
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
        binding.toolbar.toolbarTitle.setText(R.string.settings);
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

    private void setupLanguageSelection() {
        MaterialButton btnChangeLanguage = binding.btnChangeLanguage;
        btnChangeLanguage.setOnClickListener(v -> showLanguageSelectionDialog());
    }

    private void showLanguageSelectionDialog() {
        String currentLanguageCode = languagePreferences.getLanguageCode();
        String[] languages = {
            getString(R.string.english),
            getString(R.string.french),
            getString(R.string.hindi),
            getString(R.string.chinese)
        };
        
        String[] languageCodes = {
            LanguagePreferences.LANGUAGE_ENGLISH,
            LanguagePreferences.LANGUAGE_FRENCH,
            LanguagePreferences.LANGUAGE_HINDI,
            LanguagePreferences.LANGUAGE_CHINESE
        };
        
        int selectedIndex = 0;
        for (int i = 0; i < languageCodes.length; i++) {
            if (languageCodes[i].equals(currentLanguageCode)) {
                selectedIndex = i;
                break;
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme);
        builder.setTitle(R.string.select_language);
        builder.setSingleChoiceItems(languages, selectedIndex, (dialog, which) -> {
            String selectedLanguageCode = languageCodes[which];
            if (!selectedLanguageCode.equals(currentLanguageCode)) {
                languagePreferences.setLanguageCode(selectedLanguageCode);
                LocaleHelper.updateLocale(this, selectedLanguageCode);
                setResult(RESULT_OK);
                showRestartDialog();
            }
            dialog.dismiss();
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showRestartDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.restart_required)
                .setMessage(R.string.restart_message)
                .setPositiveButton(R.string.restart, (dialog, which) -> {
                    // Navigate to MainActivity with flag to trigger recreate
                    Intent intent = new Intent(this, com.mytrackr.receipts.features.core.MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    intent.putExtra("RESTART_FOR_LANGUAGE", true);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(R.string.later, null)
                .setCancelable(false)
                .show();
    }

    private void setupNotificationPermission() {
        MaterialButton btnRequestPermission = binding.btnRequestNotificationPermission;

        updateNotificationPermissionStatus();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            btnRequestPermission.setVisibility(android.view.View.GONE);
            binding.textNotificationStatus.setText(R.string.status_enabled_android_12_below);
            return;
        }

        btnRequestPermission.setOnClickListener(v -> {
            if (!NotificationPermissionHelper.hasNotificationPermission(this)) {
                NotificationPermissionHelper.requestNotificationPermission(this);
            } else {
                Toast.makeText(this, getString(R.string.notification_permission_is_already_granted), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateNotificationPermissionStatus() {
        boolean hasPermission = NotificationPermissionHelper.hasNotificationPermission(this);
        if (hasPermission) {
            binding.textNotificationStatus.setText(R.string.status_granted);
            binding.btnRequestNotificationPermission.setText(R.string.granted);
            binding.btnRequestNotificationPermission.setEnabled(false);
        } else {
            binding.textNotificationStatus.setText(R.string.status_not_granted);
            binding.btnRequestNotificationPermission.setText(R.string.enable);
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
