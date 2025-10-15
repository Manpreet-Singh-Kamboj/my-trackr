package com.mytrackr.receipts.viewmodels;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.util.Patterns;
import android.widget.EditText;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;
import com.mytrackr.receipts.data.repository.AuthRepository;
import com.mytrackr.receipts.databinding.ActivitySignInBinding;

public class AuthViewModel extends ViewModel {
    private final AuthRepository authRepository;
    public AuthViewModel(){
        authRepository = new AuthRepository();
    }
    public LiveData<FirebaseUser> getUser(){
        return authRepository.getUser();
    }

    private boolean passwordValidationError(String password,ActivitySignInBinding binding, int errorColor, int primaryColor){
        if(password.trim().isEmpty()){
            binding.passwordLayout.setErrorEnabled(true);
            binding.passwordLayout.setError("Password is required");
            binding.passwordLayout.setStartIconTintList(ColorStateList.valueOf(errorColor));
            return true;
        }
        binding.passwordLayout.setErrorEnabled(false);
        if(binding.passwordLayout.hasFocus()){
            binding.passwordLayout.setStartIconTintList(ColorStateList.valueOf(primaryColor));
        }
        return false;
    }

    private boolean emailValidationError(String s, ActivitySignInBinding binding, int errorColor, int primaryColor){
        String emailStr = s.trim();
        if (emailStr.isEmpty()) {
            binding.emailLayout.setErrorEnabled(true);
            binding.emailLayout.setStartIconTintList(
                    ColorStateList.valueOf(errorColor)
            );
            binding.emailLayout.setError("Email is required");
            return true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(emailStr).matches()) {
            binding.emailLayout.setErrorEnabled(true);
            binding.emailLayout.setStartIconTintList(
                    ColorStateList.valueOf(errorColor)
            );
            binding.emailLayout.setError("Email is not valid");
            return true;
        }
        if(binding.emailLayout.hasFocus()){
            binding.emailLayout.setStartIconTintList(
                    ColorStateList.valueOf(primaryColor)
            );
        }
        binding.emailLayout.setErrorEnabled(false);
        return false;
    }

    public LiveData<String> signInError(){
        return authRepository.getErrorMessage();
    }

    public void handleSignIn(ActivitySignInBinding binding, int errorColor, int primaryColor){
        if(binding.password.getText() == null || binding.email.getText() == null) {
            return;
        }
        String email = binding.email.getText().toString();
        if (emailValidationError(email,binding,errorColor,primaryColor)){
            return;
        }
        String password = binding.password.getText().toString().trim();
        if(passwordValidationError(password,binding, errorColor,primaryColor)){
            return;
        }
        authRepository.signInWithEmailAndPassword(email,password);
    }

    public void handleGoogleLogin(String clientId, Activity activity, int requestCode){
        authRepository.handleGoogleLogin(clientId,activity,requestCode);
    }
    public void handleGoogleSignInResult(Intent data){
        authRepository.handleGoogleSignInResult(data);
    }

    public void handleSignOut(){
        authRepository.signOut();
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

    @Override
    public void onCleared(){
        super.onCleared();
        authRepository.removeAuthStateListener();
    }
}
