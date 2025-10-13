package com.mytrackr.receipts.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.auth.FirebaseUser;
import com.mytrackr.receipts.data.repository.AuthRepository;

public class AuthViewModel extends ViewModel {
    private final AuthRepository authRepository;
    public AuthViewModel(){
        authRepository = new AuthRepository();
    }
    public LiveData<FirebaseUser> getUser(){
        return authRepository.getUser();
    }
    @Override
    public void onCleared(){
        super.onCleared();
        authRepository.removeAuthStateListener();
    }
}
