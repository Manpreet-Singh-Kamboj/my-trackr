package com.mytrackr.receipts.features.change_password;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.interfaces.OnChangePasswordUpdateListener;
import com.mytrackr.receipts.databinding.ActivityChangePasswordBinding;
import com.mytrackr.receipts.features.get_started.GetStartedActivity;
import com.mytrackr.receipts.viewmodels.AuthViewModel;

public class ChangePasswordActivity extends AppCompatActivity {

    private ActivityChangePasswordBinding binding;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityChangePasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        authViewModel.getUser().observe(this, user->{
            if(user == null){
                startActivity(new Intent(this, GetStartedActivity.class));
                finishAffinity();
            }
        });
        setSupportActionBar(binding.changePasswordToolbar.toolbar);
        if(getSupportActionBar() != null){
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        binding.changePasswordToolbar.toolbarTitle.setText(getString(R.string.change_password));
        binding.changePasswordToolbar.toolbar.setNavigationOnClickListener(v-> getOnBackPressedDispatcher().onBackPressed());
        binding.updatePassword.setOnClickListener(this::handleUpdatePassword);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    private void handleUpdatePassword(View view) {
        binding.changePasswordLayout.setError(null);
        binding.confirmPasswordLayout.setError(null);
        binding.currentPasswordLayout.setError(null);

        String currentPassword = binding.currentPassword.getText() != null
                ? binding.currentPassword.getText().toString().trim()
                : "";
        String newPassword = binding.changePassword.getText() != null
                ? binding.changePassword.getText().toString().trim()
                : "";
        String confirmPassword = binding.confirmPassword.getText() != null
                ? binding.confirmPassword.getText().toString().trim()
                : "";

        boolean isValid = true;

        if(currentPassword.isEmpty()){
            binding.currentPasswordLayout.setError("Please enter your current password");
            isValid = false;
        }

        if (newPassword.isEmpty()) {
            binding.changePasswordLayout.setError("Please enter a new password");
            isValid = false;
        }

        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordLayout.setError("Please confirm your password");
            isValid = false;
        }

        if (!newPassword.equals(confirmPassword)) {
            binding.confirmPasswordLayout.setError("Passwords do not match");
            isValid = false;
        }


        if (!isValid) return;

        authViewModel.changePassword(
                currentPassword,
                newPassword,
                new OnChangePasswordUpdateListener() {
                    @Override
                    public void onSuccess() {
                        Snackbar.make(binding.getRoot(), "Password changed successfully", Snackbar.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onFailure(String errorMessage) {
                        Snackbar.make(binding.getRoot(), "Unable to change password: " + errorMessage, Snackbar.LENGTH_SHORT).show();
                    }
                });
    }

}