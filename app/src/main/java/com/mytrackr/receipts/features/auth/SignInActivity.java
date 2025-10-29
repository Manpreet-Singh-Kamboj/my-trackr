package com.mytrackr.receipts.features.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.textfield.TextInputLayout;
import com.mytrackr.receipts.features.core.MainActivity;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.databinding.ActivitySignInBinding;
import com.mytrackr.receipts.viewmodels.AuthViewModel;

public class SignInActivity extends AppCompatActivity {
    private static final int GOOGLE_SIGN_IN_CODE = 1001;
    ActivitySignInBinding binding;
    AuthViewModel authViewModel;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignInBinding.inflate(getLayoutInflater());
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        setContentView(binding.getRoot());
        setFocusListener(binding.emailLayout, binding.email);
        setFocusListener(binding.passwordLayout, binding.password);
        authViewModel.getUser().observe(this,user -> {
            if(user != null){
                // Existing user signed in - go directly to main activity
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });
        authViewModel.error().observe(this, errorMessage->{
            if(!errorMessage.isEmpty()){
                authViewModel.showErrorSnackBar(binding,errorMessage);
                Log.e("SIGN_IN_ERROR", errorMessage);
            }
        });
        binding.signIn.setOnClickListener(this::handleSignIn);
        binding.signUp.setOnClickListener(this::onSignUpButtonClick);
        binding.googleSignIn.setOnClickListener(this::handleContinueWithGoogle);
        binding.backButton.setOnClickListener(this::onBackButtonPressed);
        binding.forgotPassword.setOnClickListener(this::onForgotPasswordClick);
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GOOGLE_SIGN_IN_CODE) {
            authViewModel.handleGoogleSignInResult(data);
        }
    }
    private void onForgotPasswordClick(View view){
        startActivity(new Intent(this, ForgotPassword.class));
    }
    private void handleContinueWithGoogle(View view){
        authViewModel.handleGoogleLogin(this,GOOGLE_SIGN_IN_CODE);
    }

    private void onBackButtonPressed(View view){
        finish();
    }
    private void onSignUpButtonClick(View view){
        startActivity(new Intent(this, SignupActivity.class));
    }
    public void handleSignIn(View view){
        authViewModel.handleSignIn(binding,getColor(R.color.error), getColor(R.color.primary));
    }
    private void setFocusListener(TextInputLayout layout, EditText editText) {
        authViewModel.setFocusListener(
                layout,editText,
                getColor(R.color.primary),
                getColor(R.color.light_gray)
        );
    }
}