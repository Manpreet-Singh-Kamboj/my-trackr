package com.mytrackr.receipts.features.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.mytrackr.receipts.MainActivity;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.databinding.ActivitySignupBinding;
import com.mytrackr.receipts.viewmodels.AuthViewModel;

public class SignupActivity extends AppCompatActivity {
    private static final int GOOGLE_SIGN_IN_CODE = 1001;
    ActivitySignupBinding binding;
    AuthViewModel authViewModel;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        authViewModel.getUser().observe(this,user -> {
            if(user != null){
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });
        binding.backButton.setOnClickListener(this::onBackButtonPressed);
        setFocusListener(binding.fullNameLayout,binding.fullName);
        setFocusListener(binding.emailLayout, binding.email);
        setFocusListener(binding.passwordLayout, binding.password);
        binding.googleSignIn.setOnClickListener(this::handleContinueWithGoogle);
        binding.signUp.setOnClickListener(this::handleSignUp);
        authViewModel.error().observe(this, errorMessage->{
            if(!errorMessage.isEmpty()){
                authViewModel.showErrorSnackBar(binding,errorMessage);
            }
        });
    }
    @Override
    public void onActivityResult(int requestCode, int responseCode, Intent data){
        super.onActivityResult(requestCode,responseCode,data);
        if(requestCode == GOOGLE_SIGN_IN_CODE){
            authViewModel.handleGoogleSignInResult(data);
        }
    }
    private void onBackButtonPressed(View view){
        finish();
    }
    private void handleSignUp(View view){
        authViewModel.handleSignUp(binding,getColor(R.color.error), getColor(R.color.primary));
    }
    private void setFocusListener(TextInputLayout layout, EditText editText) {
        authViewModel.setFocusListener(
                layout,editText,
                getColor(R.color.primary),
                getColor(R.color.light_gray)
        );
    }
    private void handleContinueWithGoogle(View view){
        authViewModel.handleGoogleLogin(this,GOOGLE_SIGN_IN_CODE);
    }
}