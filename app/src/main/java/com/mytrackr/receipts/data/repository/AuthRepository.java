package com.mytrackr.receipts.data.repository;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.mytrackr.receipts.data.models.User;

public class AuthRepository {
    private static AuthRepository instance;
    private static UserRepository userRepository;
    private final GoogleSignInClient googleSignInClient;
    private final FirebaseAuth firebaseAuth;
    private final MutableLiveData<FirebaseUser> currentUser = new MutableLiveData<FirebaseUser>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>("");
    private final  MutableLiveData<String> successMessage = new MutableLiveData<>("");
    private final FirebaseAuth.AuthStateListener authStateListener = firebaseAuth -> {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        currentUser.postValue(user);
        if(user != null) {
            userRepository.getUserDetails(user.getUid(),errorMessage);
        }
    };
    private AuthRepository(Context context, String clientId){
        this.firebaseAuth = FirebaseAuth.getInstance();
        firebaseAuth.addAuthStateListener(authStateListener);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientId)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(context, gso);
        userRepository = UserRepository.getInstance();
    }

    public static synchronized AuthRepository getInstance(Context context, String clientId){
        if(instance == null){
            instance = new AuthRepository(context,clientId);
            Log.i("AUTH_REPO_INITIALIZED", "AUTH Repository is Initialized");
        }
        return instance;
    }

    public LiveData<FirebaseUser> getUser(){
        return currentUser;
    }
    public LiveData<String> getErrorMessage(){
        return errorMessage;
    }
    public LiveData<String> getSuccessMessage(){
        return successMessage;
    }
    public void clearErrorMessage(){
        errorMessage.postValue("");
    }
    public void clearSuccessMessage(){
        successMessage.postValue("");
    }
    public void handleGoogleLogin(Activity activity, int requestCode){
        Intent signInIntent = googleSignInClient.getSignInIntent();
        activity.startActivityForResult(signInIntent, requestCode);
    }
    public void handleGoogleSignInResult(Intent data){
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult();
            if (account != null) {
                String idToken = account.getIdToken();
                this.firebaseAuthWithGoogle(idToken);
            }
        } catch (Exception e) {
            Log.e("GOOGLE_LOGIN_ERROR", "Google Sign In Failed");
            errorMessage.postValue(e.getMessage());
        }
    }
    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth
                .signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        currentUser.postValue(user);
                        if(user != null){
                            String uid = user.getUid();
                            String fullName = user.getDisplayName();
                            String email = user.getEmail();
                            userRepository.checkIfGoogleUserDetailsExistOrNot(uid,fullName,email,errorMessage,user.getPhotoUrl());
                        }
                    } else {
                        String error = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown error occurred";
                        Log.e("GOOGLE_LOGIN_ERROR", error);
                        errorMessage.postValue(error);
                    }
                });
    }
    public void signInWithEmailAndPassword(String email, String password) {
        firebaseAuth
                .signInWithEmailAndPassword(email.toLowerCase(),password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        currentUser.postValue(user);
                    } else {
                        String error = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown error occurred";
                        Log.e("EMAIL_SIGN_IN_ERROR", error);
                        errorMessage.postValue(error);
                    }
                });
    }
    public void signUpWithEmailPassword(String fullName, String email, String password){
        firebaseAuth.createUserWithEmailAndPassword(email.toLowerCase(),password).addOnCompleteListener(task -> {
            if (task.isSuccessful()){
                FirebaseUser user = firebaseAuth.getCurrentUser();
                currentUser.postValue(user);
                if(user != null){
                    userRepository.storeUserDetailsToFirestore(user.getUid(),fullName,email,errorMessage,null);
                }
            }else{
                String error = task.getException() != null
                        ? task.getException().getMessage()
                        : "Unknown error occurred";
                Log.e("EMAIL_SIGN_IN_ERROR", error);
                errorMessage.postValue(error);
            }
        });
    }

    public void handleForgotPasswordRequest(String email){
        firebaseAuth
                .sendPasswordResetEmail(email.toLowerCase())
                .addOnCompleteListener(task -> {
                    if(task.isSuccessful()){
                        successMessage.postValue("Password reset email sent! Please check your inbox and follow the instructions to reset your password.");
                    }else {
                        String error = task.getException() != null ? task.getException().getMessage() : "Unknown error occurred";
                        Log.e("EMAIL_SIGN_IN_ERROR", error);
                        errorMessage.postValue(error);
                    }
                });
    }

    public void signOut(){
        FirebaseUser user = firebaseAuth.getCurrentUser();
        boolean isGoogleLogin = false;
        if(user != null){
            for(UserInfo userInfo: user.getProviderData()){
                if(userInfo.getProviderId().equals(GoogleAuthProvider.PROVIDER_ID)){
                    isGoogleLogin = true;
                    break;
                }
            }
            if(isGoogleLogin) {
                googleSignInClient.signOut().addOnCompleteListener(task -> {
                    if(task.isSuccessful()){
                        firebaseAuth.signOut();
                        currentUser.postValue(null);
                        userRepository.resetUserDetails();
                    }else{
                        Log.e("SIGN_OUT_ERROR", "SIGN_IN_FAILED");
                        errorMessage.postValue("Something Went Wrong. Please try again.");
                    }
                });
            }else{
                firebaseAuth.signOut();
                currentUser.postValue(null);
                userRepository.resetUserDetails();
            }
        }
    }
    public LiveData<User> getUserDetails(){
        if (currentUser.getValue() == null) {
            errorMessage.postValue("User is not authenticated. Please SignIn again to continue");
            return null;
        }
        return userRepository.getUserDetails(currentUser.getValue().getUid(), errorMessage);
    }
    public void removeAuthStateListener() {
        firebaseAuth.removeAuthStateListener(authStateListener);
    }
    public void refreshUserDetails() {
        if (currentUser.getValue() != null) {
            userRepository.getUserDetails(currentUser.getValue().getUid(), errorMessage);
        }
    }
    public boolean isGoogleSignedInUser(){
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if(user != null){
            for(UserInfo userInfo: user.getProviderData()){
                if(userInfo.getProviderId().equals(GoogleAuthProvider.PROVIDER_ID)){
                    return true;
                }
            }
        }
        return false;
    }
}
