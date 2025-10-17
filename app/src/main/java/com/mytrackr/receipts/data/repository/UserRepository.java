package com.mytrackr.receipts.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {
    private static UserRepository instance;
    FirebaseFirestore firestore;

    private UserRepository(){
        firestore = FirebaseFirestore.getInstance();
    }

    public static synchronized UserRepository getInstance(){
        if(instance == null){
            instance = new UserRepository();
        }
        return instance;
    }

    public void checkIfGoogleUserDetailsExistOrNot(String uid, String fullName, String email, MutableLiveData<String> errorMessage){
        DocumentReference userDoc = firestore
                .collection("users")
                .document(uid);
        userDoc
                .get()
                .addOnCompleteListener(task -> {
                    if(task.isSuccessful()){
                        if(!task.getResult().exists()){
                            this.storeUserDetailsToFirestore(uid,fullName,email,errorMessage);
                        }
                    }else{
                    String error = task.getException() != null
                            ? task.getException().getMessage()
                            : "Unknown error occurred";
                    errorMessage.postValue(error);
                }
        });
    }

    public void storeUserDetailsToFirestore(String uid, String fullName, String email, MutableLiveData<String> errorMessage){
        Map<String, Object> userDetails = new HashMap<>();
        userDetails.put("fullName", fullName);
        userDetails.put("email", email);
        firestore
                .collection("users")
                .document(uid)
                .set(userDetails, SetOptions.merge())
                .addOnCompleteListener(task -> {
                    if(!task.isSuccessful()){
                        String error = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown error occurred";
                        errorMessage.postValue(error);
                    }
                });
    }
}
