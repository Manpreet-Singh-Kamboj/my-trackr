package com.mytrackr.receipts.data.repository;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.mytrackr.receipts.data.models.User;
import com.mytrackr.receipts.utils.CloudinaryUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
        }

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
        return userLiveData;
    }

    public void resetUserDetails(){
        if (userLiveData != null) userLiveData.postValue(null);
    }

    private Task<Void> saveUserProfileMetaDataToFirebase(String uid, Map<String,Object> details){
        return firestore
                .collection("users")
                .document(uid)
                .set(details, SetOptions.merge())
                .addOnSuccessListener(task -> {
                    if (userLiveData != null && userLiveData.getValue() != null) {
                        User updatedUser = userLiveData.getValue();
                        updatedUser.setFullName(details.get("fullName").toString());
                        if (details.get("aboutMe") != null) updatedUser.setAboutMe(details.get("aboutMe").toString());
                        if (details.get("phoneNo") != null) updatedUser.setPhoneNo(details.get("phoneNo").toString());
                        if (details.get("city") != null) updatedUser.setCity(details.get("city").toString());
                        userLiveData.postValue(updatedUser);
                    }
                });
    }

    public Task<Void> updateUserProfile(Context context, String uid, String fullName,
                                        @Nullable String aboutMe, @Nullable String phoneNo,
                                        @Nullable String city, @Nullable Uri newProfilePictureUri) {

        Map<String, Object> updatedUserDetails = new HashMap<>();
        updatedUserDetails.put("fullName", fullName);
        if (aboutMe != null) updatedUserDetails.put("aboutMe", aboutMe);
        if (phoneNo != null) updatedUserDetails.put("phoneNo", phoneNo);
        if (city != null) updatedUserDetails.put("city", city);

        com.google.android.gms.tasks.TaskCompletionSource<Void> taskCompletionSource = new com.google.android.gms.tasks.TaskCompletionSource<>();

        Runnable persistToFirestore = () -> {
            saveUserProfileMetaDataToFirebase(uid, updatedUserDetails)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            taskCompletionSource.setResult(null);
                        } else {
                            taskCompletionSource.setException(task.getException() != null ? task.getException() :
                                    new Exception("Unknown error saving user profile"));
                        }
                    });
        };

        if (newProfilePictureUri != null && CloudinaryUtils.isConfigured(context)) {
            String id = UUID.randomUUID().toString();
            CloudinaryUtils.UploadConfig config = CloudinaryUtils.readConfig(context, id);
            if (config != null) {
                CloudinaryUtils.uploadImage(context, newProfilePictureUri, config, new CloudinaryUtils.CloudinaryUploadCallback() {
                    @Override
                    public void onSuccess(String secureUrl, String publicId) {
                        updatedUserDetails.put("profilePicture", secureUrl);
                        Log.i("UserRepository", "Cloudinary upload successful, for User Profile Image");
                        persistToFirestore.run();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.w("UserRepository", "Cloudinary upload failed", e);
                        taskCompletionSource.setException(e);
                    }
                });
                return taskCompletionSource.getTask();
            }
        }

        persistToFirestore.run();
        return taskCompletionSource.getTask();
    }

}
