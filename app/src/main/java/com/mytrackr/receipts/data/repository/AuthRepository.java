package com.mytrackr.receipts.data.repository;

import android.app.Activity;
import android.content.Intent;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class AuthRepository {
    private GoogleSignInClient googleSignInClient;
    private final FirebaseAuth firebaseAuth;
    private final MutableLiveData<FirebaseUser> currentUser = new MutableLiveData<FirebaseUser>();
    private final FirebaseAuth.AuthStateListener authStateListener = firebaseAuth -> {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        currentUser.postValue(user);
    };
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>("");
    public AuthRepository(){
        this.firebaseAuth = FirebaseAuth.getInstance();
        firebaseAuth.addAuthStateListener(authStateListener);
    }
    public LiveData<FirebaseUser> getUser(){
        return currentUser;
    }
    public LiveData<String> getErrorMessage(){
        return errorMessage;
    }
    public void handleGoogleLogin(String clientId, Activity activity, int requestCode){
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientId)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(activity, gso);
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
                    } else {
                        String error = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown error occurred";
                        errorMessage.postValue(error);
                    }
                });
    }
    public void signInWithEmailAndPassword(String email, String password) {
        firebaseAuth
                .signInWithEmailAndPassword(email,password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        currentUser.postValue(user);
                    } else {
                        String error = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown error occurred";
                        errorMessage.postValue(error);
                    }
                });
    }
    public void signOut(){
        firebaseAuth.signOut();
    }
    public void removeAuthStateListener() {
        firebaseAuth.removeAuthStateListener(authStateListener);
    }
}
