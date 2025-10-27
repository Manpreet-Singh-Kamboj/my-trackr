package com.mytrackr.receipts.features.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.mytrackr.receipts.MainActivity;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.databinding.ActivitySignInBinding;
import com.mytrackr.receipts.features.onboarding.OnboardingActivity;
import com.mytrackr.receipts.viewmodels.AuthViewModel;

public class SignInActivity extends AppCompatActivity {
    
    private ActivitySignInBinding binding;
    private FirebaseAuth firebaseAuth;
    private AuthViewModel authViewModel;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        firebaseAuth = FirebaseAuth.getInstance();
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        
        setupClickListeners();
        observeAuthState();
        // TEMP: Launch onboarding directly for testing
        binding.tempLogin.setOnClickListener(v -> {
            Intent intent = new Intent(this, OnboardingActivity.class);
            startActivity(intent);
        });
    }
    
    private void setupClickListeners() {
        binding.signIn.setOnClickListener(v -> signInUser());
        binding.signUp.setOnClickListener(v -> {
            // TODO: Navigate to sign up activity when implemented
            Toast.makeText(this, "Sign up functionality coming soon!", Toast.LENGTH_SHORT).show();
        });
        binding.forgotPassword.setOnClickListener(v -> {
            // TODO: Implement forgot password functionality
            Toast.makeText(this, "Forgot password functionality coming soon!", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void observeAuthState() {
        authViewModel.getUser().observe(this, user -> {
            if (user != null) {
                // User successfully signed in
                navigateAfterLogin();
            }
        });
    }
    
    private void signInUser() {
        String email = binding.email.getText().toString().trim();
        String password = binding.password.getText().toString().trim();
        
        if (!validateInput(email, password)) {
            return;
        }
        
        // Show loading state
        binding.signIn.setEnabled(false);
        binding.signIn.setText("Signing in...");
        
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    binding.signIn.setEnabled(true);
                    binding.signIn.setText(R.string.sign_in_heading);
                    
                    if (task.isSuccessful()) {
                        // Sign in success - the observer will handle navigation
                        Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show();
                    } else {
                        // Sign in failed
                        String errorMessage = task.getException() != null ? 
                                task.getException().getMessage() : "Authentication failed";
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }
    
    private boolean validateInput(String email, String password) {
        if (TextUtils.isEmpty(email)) {
            binding.email.setError("Email is required");
            binding.email.requestFocus();
            return false;
        }
        
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.email.setError("Please enter a valid email address");
            binding.email.requestFocus();
            return false;
        }
        
        if (TextUtils.isEmpty(password)) {
            binding.password.setError("Password is required");
            binding.password.requestFocus();
            return false;
        }
        
        if (password.length() < 6) {
            binding.password.setError("Password must be at least 6 characters");
            binding.password.requestFocus();
            return false;
        }
        
        return true;
    }
    
    private void navigateAfterLogin() {
        Intent intent;
        if (OnboardingActivity.isOnboardingCompleted(this)) {
            // User has already seen onboarding, go directly to main activity
            intent = new Intent(this, MainActivity.class);
        } else {
            // First time login, show onboarding
            intent = new Intent(this, OnboardingActivity.class);
        }
        
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}