package com.mytrackr.receipts.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthRepository {
    private final FirebaseAuth firebaseAuth;
    private final MutableLiveData<FirebaseUser> currentUser = new MutableLiveData<FirebaseUser>();
    private final FirebaseAuth.AuthStateListener authStateListener = firebaseAuth -> {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        currentUser.postValue(user);
    };
    public AuthRepository(){
        this.firebaseAuth = FirebaseAuth.getInstance();
        firebaseAuth.addAuthStateListener(authStateListener);
    }
    public LiveData<FirebaseUser> getUser(){
        return currentUser;
    }
    public void signOut(){
        firebaseAuth.signOut();
    }
    public void removeAuthStateListener() {
        firebaseAuth.removeAuthStateListener(authStateListener);
    }
}
