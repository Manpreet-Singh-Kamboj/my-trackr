package com.mytrackr.receipts.data.repository;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.tasks.Task;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
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
            FirebaseCrashlytics.getInstance().log("D/UserRepository: User Repository is Initialized");
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
                        FirebaseCrashlytics.getInstance().log("E/UserRepository: " + error);
                        if (task.getException() != null) {
                            FirebaseCrashlytics.getInstance().recordException(task.getException());
                        }
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
                    if(task.isSuccessful()){
                        FirebaseCrashlytics.getInstance().log("D/UserRepository: User details stored to Firestore successfully");
                    } else {
                        String error = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown error occurred";
                        FirebaseCrashlytics.getInstance().log("E/UserRepository: " + error);
                        if (task.getException() != null) {
                            FirebaseCrashlytics.getInstance().recordException(task.getException());
                        }
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
                                    FirebaseCrashlytics.getInstance().log("D/UserRepository: User details fetched successfully");
                                    userLiveData.postValue(user);
                                }
                            }
                        } else {
                            String error = task.getException() != null ? task.getException().getMessage() : "Error getting document";
                            FirebaseCrashlytics.getInstance().log("E/UserRepository: " + error);
                             if (task.getException() != null) {
                                FirebaseCrashlytics.getInstance().recordException(task.getException());
                            }
                            errorMessage.postValue(error);
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
                        FirebaseCrashlytics.getInstance().log("D/UserRepository: User profile metadata saved successfully");
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
                            Exception e = task.getException() != null ? task.getException() :
                                    new Exception("Unknown error saving user profile");
                            FirebaseCrashlytics.getInstance().log("E/UserRepository: Error saving user profile metadata");
                            FirebaseCrashlytics.getInstance().recordException(e);
                            taskCompletionSource.setException(e);
                        }
                    });
        };

        if (newProfilePictureUri != null && CloudinaryUtils.isConfigured(context)) {
            String id = UUID.randomUUID().toString();
            CloudinaryUtils.UploadConfig config = CloudinaryUtils.readConfig(context, id);
            if (config != null) {
                FirebaseCrashlytics.getInstance().log("D/UserRepository: Uploading user profile image to Cloudinary");
                CloudinaryUtils.uploadImage(context, newProfilePictureUri, config, new CloudinaryUtils.CloudinaryUploadCallback() {
                    @Override
                    public void onSuccess(String secureUrl, String publicId) {
                        updatedUserDetails.put("profilePicture", secureUrl);
                        FirebaseCrashlytics.getInstance().log("D/UserRepository: Cloudinary upload successful for User Profile Image");
                        persistToFirestore.run();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        FirebaseCrashlytics.getInstance().log("E/UserRepository: Cloudinary upload failed");
                        FirebaseCrashlytics.getInstance().recordException(e);
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
