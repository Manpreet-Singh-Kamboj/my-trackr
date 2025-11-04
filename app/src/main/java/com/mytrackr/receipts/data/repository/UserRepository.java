package com.mytrackr.receipts.data.repository;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.mytrackr.receipts.data.models.User;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {
    private static UserRepository instance;
    FirebaseFirestore firestore;
    private MutableLiveData<User> userLiveData;

    private UserRepository(){
        firestore = FirebaseFirestore.getInstance();
    }

    public static synchronized UserRepository getInstance(){
        if(instance == null){
            instance = new UserRepository();
            Log.i("USER_REPO_INITIALIZED", "USER Repository is Initialized");
        }
        return instance;
    }

    public void checkIfGoogleUserDetailsExistOrNot(String uid, String fullName, String email, MutableLiveData<String> errorMessage, @Nullable Uri profilePictureUrl){
        DocumentReference userDoc = firestore
                .collection("users")
                .document(uid);
        userDoc
                .get()
                .addOnCompleteListener(task -> {
                    if(task.isSuccessful()){
                        if(!task.getResult().exists()){
                            this.storeUserDetailsToFirestore(uid,fullName,email,errorMessage,profilePictureUrl);
                        }
                    }else{
                    String error = task.getException() != null
                            ? task.getException().getMessage()
                            : "Unknown error occurred";
                        Log.e("GOOGLE_USER_CHECK", "GOOGLE_USER_CHECK_FAILED IN DB");
                    errorMessage.postValue(error);
                }
        });
    }

    public void storeUserDetailsToFirestore(String uid, String fullName, String email, MutableLiveData<String> errorMessage, @Nullable Uri profilePictureUrl){
        Map<String, Object> userDetails = new HashMap<>();
        userDetails.put("fullName", fullName);
        userDetails.put("email", email);
        String pictureUrl = profilePictureUrl != null ? profilePictureUrl.toString() : "https://api.dicebear.com/9.x/initials/png?seed="+fullName;
        userDetails.put("profilePicture", pictureUrl);
        firestore
                .collection("users")
                .document(uid)
                .set(userDetails, SetOptions.merge())
                .addOnCompleteListener(task -> {
                    if(!task.isSuccessful()){
                        String error = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown error occurred";
                        Log.e("DB_PERSIST_FAILED", "DB Transaction Failed");
                        errorMessage.postValue(error);
                    }
                });
    }

    public LiveData<User> getUserDetails(String uid, MutableLiveData<String> errorMessage){
        if(userLiveData == null) {
            userLiveData = new MutableLiveData<User>();
            firestore
                    .collection("users")
                    .document(uid)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                User user = document.toObject(User.class);
                                if (user != null) {
                                    Log.i("USER_DETAILS: ", user.getFullName() + " " + user.getEmail() + " " + user.getProfilePicture());
                                    userLiveData.postValue(user);
                                }
                            }
                        } else {
                            errorMessage.postValue("Error getting document: " + task.getException());
                        }
                    });
        }
        return userLiveData;
    }

    public void resetUserDetails(){
        userLiveData = null;
    }
}
