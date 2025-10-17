package com.mytrackr.receipts.features.auth;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.mytrackr.receipts.R;
import com.mytrackr.receipts.databinding.ActivityForgotPasswordBinding;
import com.mytrackr.receipts.viewmodels.AuthViewModel;

public class ForgotPassword extends AppCompatActivity {
    ActivityForgotPasswordBinding binding;
    AuthViewModel authViewModel;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        authViewModel = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(AuthViewModel.class);
        binding.backButton.setOnClickListener(this::onBackButtonPressed);
        binding.forgotPassword.setOnClickListener(this::onForgotPassword);
        authViewModel.success().observe(this,message -> {
            if(message != null && !message.isEmpty()){
                authViewModel.showSuccessSnackBar(binding, message);
                Log.i("FORGOT_PASSWORD_SUCCESS", message);
                binding.email.setText("");
            }
        });
        authViewModel.error().observe(this,error -> {
            if(error != null && !error.isEmpty()){
                authViewModel.showErrorSnackBar(binding, error);
                Log.e("FORGOT_PASSWORD_ERROR", error);
            }
        });
    }
    private void onForgotPassword(View view){
        authViewModel.handleForgotPasswordRequest(binding);
    }
    private void onBackButtonPressed(View view){
        finish();
    }
}