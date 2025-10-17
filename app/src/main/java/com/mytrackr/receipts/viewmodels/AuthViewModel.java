package com.mytrackr.receipts.viewmodels;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.util.Patterns;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.repository.AuthRepository;
import com.mytrackr.receipts.databinding.ActivityForgotPasswordBinding;
import com.mytrackr.receipts.databinding.ActivitySignInBinding;
import com.mytrackr.receipts.databinding.ActivitySignupBinding;
import com.mytrackr.receipts.utils.Utils;

public class AuthViewModel extends AndroidViewModel {
    private final AuthRepository authRepository;
    private final Utils utils;
    public AuthViewModel(@NonNull Application application){
        super(application);
        authRepository = AuthRepository.getInstance(application.getApplicationContext(),application.getString(R.string.google_web_client_id));
        utils = Utils.getInstance();
    }
    public LiveData<FirebaseUser> getUser(){
        return authRepository.getUser();
    }

    private boolean passwordValidationError(String password, TextInputLayout passwordLayout, int errorColor, int primaryColor) {
        if (password.trim().isEmpty()) {
            passwordLayout.setErrorEnabled(true);
            passwordLayout.setError("Password is required");
            passwordLayout.setStartIconTintList(ColorStateList.valueOf(errorColor));
            return true;
        }

        passwordLayout.setErrorEnabled(false);

        if (passwordLayout.hasFocus()) {
            passwordLayout.setStartIconTintList(ColorStateList.valueOf(primaryColor));
        }

        return false;
    }
    private boolean emailValidationError(String s, TextInputLayout emailLayout, int errorColor, int primaryColor){
        String emailStr = s.trim();
        if (emailStr.isEmpty()) {
            emailLayout.setErrorEnabled(true);
            emailLayout.setStartIconTintList(
                    ColorStateList.valueOf(errorColor)
            );
            emailLayout.setError("Email is required");
            return true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(emailStr).matches()) {
            emailLayout.setErrorEnabled(true);
            emailLayout.setStartIconTintList(
                    ColorStateList.valueOf(errorColor)
            );
            emailLayout.setError("Email is not valid");
            return true;
        }
        if(emailLayout.hasFocus()){
            emailLayout.setStartIconTintList(
                    ColorStateList.valueOf(primaryColor)
            );
        }
        emailLayout.setErrorEnabled(false);
        return false;
    }
    private boolean fullNameValidationError(String fullName,TextInputLayout fullNameLayout, int errorColor, int primaryColor){
        if(fullName.trim().isEmpty()){
            fullNameLayout.setErrorEnabled(true);
            fullNameLayout.setError("Full Name is required");
            fullNameLayout.setStartIconTintList(ColorStateList.valueOf(errorColor));
            return true;
        }
        fullNameLayout.setErrorEnabled(false);
        if(fullNameLayout.hasFocus()){
            fullNameLayout.setStartIconTintList(ColorStateList.valueOf(primaryColor));
        }
        return false;
    }

    public LiveData<String> error(){
        return authRepository.getErrorMessage();
    }
    public LiveData<String> success(){
        return authRepository.getSuccessMessage();
    }

    public void handleSignIn(ActivitySignInBinding binding, int errorColor, int primaryColor){
        if(binding.password.getText() == null || binding.email.getText() == null) {
            return;
        }
        String email = binding.email.getText().toString();
        if (emailValidationError(email,binding.emailLayout,errorColor,primaryColor)){
            return;
        }
        String password = binding.password.getText().toString().trim();
        if(passwordValidationError(password,binding.passwordLayout, errorColor,primaryColor)){
            return;
        }
        authRepository.signInWithEmailAndPassword(email,password);
    }

    public void handleGoogleLogin(Activity activity, int requestCode){
        authRepository.handleGoogleLogin(activity,requestCode);
    }
    public void handleGoogleSignInResult(Intent data){
        authRepository.handleGoogleSignInResult(data);
    }

    public void handleSignOut(){
        authRepository.signOut();
    }
    public void setFocusListener(TextInputLayout layout, EditText editText, int focusedColor, int unfocusedColor){
        utils.setFocusListener(layout,editText,focusedColor,unfocusedColor);
    }

    public void handleSignUp(ActivitySignupBinding binding, int errorColor, int primaryColor){
        if(binding.password.getText() == null || binding.email.getText() == null || binding.fullName.getText() == null) {
            return;
        }
        String fullName = binding.fullName.getText().toString();
        if (fullNameValidationError(fullName,binding.fullNameLayout,errorColor,primaryColor)){
            return;
        }
        String email = binding.email.getText().toString();
        if (emailValidationError(email,binding.emailLayout,errorColor,primaryColor)){
            return;
        }
        String password = binding.password.getText().toString().trim();
        if(passwordValidationError(password,binding.passwordLayout, errorColor,primaryColor)){
            return;
        }
        authRepository.signUpWithEmailPassword(fullName,email,password);
    }

    public <T extends ViewBinding> void showErrorSnackBar(T binding, String errorMessage) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getApplication().getColor(R.color.light_red));
        snackbar.setTextColor(getApplication().getColor(R.color.error));
        snackbar.setText(errorMessage);
        snackbar.show();
        authRepository.clearErrorMessage();
    }
    public <T extends ViewBinding> void showSuccessSnackBar(T binding, String successMessage) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), successMessage, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getApplication().getColor(R.color.light_green));
        snackbar.setTextColor(getApplication().getColor(R.color.dark_green));
        snackbar.setText(successMessage);
        snackbar.show();
        authRepository.clearSuccessMessage();
    }
    public void handleForgotPasswordRequest(ActivityForgotPasswordBinding binding){
        if(binding.email.getText() == null){
            return;
        }
        String email = binding.email.getText().toString();
        if(emailValidationError(email,binding.emailLayout, getApplication().getColor(R.color.error), getApplication().getColor(R.color.primary))){
            return;
        }
        authRepository.handleForgotPasswordRequest(email);
    }

    @Override
    public void onCleared(){
        super.onCleared();
        authRepository.removeAuthStateListener();
    }
}
