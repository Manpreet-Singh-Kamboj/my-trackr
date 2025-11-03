package com.mytrackr.receipts.data.repository;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import android.os.Handler;
import android.os.Looper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.firebase.storage.StorageException;
import com.google.firebase.firestore.Query;

import com.mytrackr.receipts.data.models.Receipt;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// New imports for Cloudinary/OkHttp upload
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

// add import for R
import com.mytrackr.receipts.R;

public class ReceiptRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface SaveCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    public void saveReceipt(Context context, Uri imageUri, Receipt receipt, SaveCallback callback) {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
        String id = UUID.randomUUID().toString();
        receipt.setId(id);

        Log.d("ReceiptRepository", "saveReceipt called: userId=" + userId + " id=" + id + " imageUri=" + (imageUri != null ? imageUri.toString() : "null"));

        if (imageUri == null) {
            if (callback != null) callback.onFailure(new IllegalArgumentException("imageUri is null"));
            return;
        }

        // Check for Cloudinary config in resources. If present, attempt unsigned upload first.
        try {
            String cloudName = context.getString(R.string.cloudinary_cloud_name);
            String uploadPreset = context.getString(R.string.cloudinary_upload_preset);
            String folderRoot = context.getString(R.string.cloudinary_folder_root);
            if (cloudName != null && !cloudName.isEmpty() && uploadPreset != null && !uploadPreset.isEmpty()) {
                uploadToCloudinary(context, imageUri, receipt, id, cloudName, uploadPreset, folderRoot, callback);
                return; // will call callback later
            }
         } catch (Exception ex) {
             Log.d("ReceiptRepository", "Cloudinary config read failed, falling back to Firebase Storage", ex);
         }

         // ...existing Firebase upload code...
         FirebaseStorage storageInstance = getPreferredStorage(context);
         StorageReference ref = storageInstance.getReference().child("receipts/" + userId + "/" + id + ".jpg");

        try {
            UploadTask uploadTask;
            java.io.InputStream inputToClose = null;
            if ("content".equals(imageUri.getScheme())) {
                inputToClose = context.getContentResolver().openInputStream(imageUri);
                if (inputToClose != null) {
                    Log.d("ReceiptRepository", "Uploading via putStream to path=" + ref.getPath());
                    uploadTask = ref.putStream(inputToClose);
                } else {
                    Log.d("ReceiptRepository", "InputStream null; falling back to putFile for path=" + ref.getPath());
                    uploadTask = ref.putFile(imageUri);
                }
            } else {
                Log.d("ReceiptRepository", "Uploading via putFile to path=" + ref.getPath());
                uploadTask = ref.putFile(imageUri);
            }

            attachUploadListeners(uploadTask, ref, inputToClose, receipt, id, callback, context, imageUri, false);

        } catch (Exception e) {
            Log.w("ReceiptRepository", "Exception while uploading image", e);
            if (callback != null) callback.onFailure(e);
        }
    }

    // New helper: upload to Cloudinary (unsigned preset). On failure, fall back to Firebase Storage path by calling saveReceiptFirebaseFallback.
    private void uploadToCloudinary(Context context, Uri imageUri, Receipt receipt, String id, String cloudName, String uploadPreset, String folderRoot, SaveCallback callback) {
         Log.d("ReceiptRepository", "Attempting Cloudinary upload: cloud=" + cloudName + " preset=" + uploadPreset);
         OkHttpClient client = new OkHttpClient.Builder().build();

         try {
             InputStream is = context.getContentResolver().openInputStream(imageUri);
             if (is == null) throw new IllegalStateException("Could not open image input stream");
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             byte[] buffer = new byte[8192];
             int n;
             while ((n = is.read(buffer)) != -1) baos.write(buffer, 0, n);
             is.close();
             byte[] imageBytes = baos.toByteArray();

             MediaType mediaType = MediaType.parse("image/jpeg");
             RequestBody fileBody = RequestBody.create(imageBytes, mediaType);

            // compute folder path: <folderRoot>/<userId>/<receiptId>
            String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
            String folderPath = folderRoot != null && !folderRoot.isEmpty() ? folderRoot + "/" + userId + "/" + id : userId + "/" + id;

            MultipartBody.Builder mb = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", "receipt.jpg", fileBody)
                    .addFormDataPart("upload_preset", uploadPreset)
                    .addFormDataPart("folder", folderPath);

            MultipartBody requestBody = mb.build();

             String url = "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload";
             Request request = new Request.Builder().url(url).post(requestBody).build();

             client.newCall(request).enqueue(new Callback() {
                 @Override
                 public void onFailure(Call call, IOException e) {
                     Log.w("ReceiptRepository", "Cloudinary upload failed", e);
                     // fallback to Firebase Storage upload
                     new Handler(Looper.getMainLooper()).post(() -> saveReceiptFirebaseFallback(context, imageUri, receipt, id, callback));
                 }

                 @Override
                 public void onResponse(Call call, Response response) {
                     try {
                         if (!response.isSuccessful()) {
                             String respBody = response.body() != null ? response.body().string() : "";
                             Log.w("ReceiptRepository", "Cloudinary upload returned non-success: " + response.code() + " body=" + respBody);
                             new Handler(Looper.getMainLooper()).post(() -> saveReceiptFirebaseFallback(context, imageUri, receipt, id, callback));
                             return;
                         }
                         String body = response.body() != null ? response.body().string() : null;
                         try {
                             JSONObject json = new JSONObject(body);
                             final String secureUrl = json.optString("secure_url", null);
                            final String publicId = json.optString("public_id", null);
                             if (secureUrl == null || secureUrl.isEmpty()) {
                                 Log.w("ReceiptRepository", "Cloudinary response missing secure_url: " + body);
                                 new Handler(Looper.getMainLooper()).post(() -> saveReceiptFirebaseFallback(context, imageUri, receipt, id, callback));
                                 return;
                             }

                             Log.d("ReceiptRepository", "Cloudinary upload succeeded: " + secureUrl);
                             receipt.setImageUrl(secureUrl);
                             // Save public_id as well so we can reference/transform later
                            // pass publicId through to Firestore writer instead of modifying the model here
                             // Save metadata to Firestore on main thread
                             final String finalPublicId = (publicId != null && !publicId.isEmpty()) ? publicId : null;
                             new Handler(Looper.getMainLooper()).post(() -> saveMetadataToFirestore(id, receipt, finalPublicId, callback));

                         } catch (Exception ex) {
                             Log.w("ReceiptRepository", "Cloudinary response parsing failed", ex);
                             new Handler(Looper.getMainLooper()).post(() -> saveReceiptFirebaseFallback(context, imageUri, receipt, id, callback));
                         }
                     } catch (IOException ioEx) {
                         Log.w("ReceiptRepository", "Error reading Cloudinary response", ioEx);
                         new Handler(Looper.getMainLooper()).post(() -> saveReceiptFirebaseFallback(context, imageUri, receipt, id, callback));
                     } finally {
                         if (response.body() != null) response.close();
                     }
                  }
              });

         } catch (Exception e) {
             Log.w("ReceiptRepository", "Exception preparing Cloudinary upload", e);
             // fallback to Firebase Storage upload
             new Handler(Looper.getMainLooper()).post(() -> saveReceiptFirebaseFallback(context, imageUri, receipt, id, callback));
         }
     }

     // Helper: save metadata to Firestore (used by Cloudinary path)
     private void saveMetadataToFirestore(String id, Receipt receipt, String cloudinaryPublicId, SaveCallback callback) {
         Map<String, Object> map = new HashMap<>();
         String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
         map.put("storeName", receipt.getStoreName());
         map.put("date", receipt.getDate());
         map.put("total", receipt.getTotal());
         map.put("items", receipt.getItems());
         map.put("imageUrl", receipt.getImageUrl());
         // include cloudinary public id if present
         if (cloudinaryPublicId != null) map.put("cloudinaryPublicId", cloudinaryPublicId);
         map.put("rawText", receipt.getRawText());
         map.put("userId", userId);

         db.collection("receipts").document(id).set(map, SetOptions.merge())
                 .addOnSuccessListener(aVoid -> {
                     if (callback != null) callback.onSuccess();
                 })
                 .addOnFailureListener(e -> {
                     Log.w("ReceiptRepository", "Failed to save receipt metadata", e);
                     if (callback != null) callback.onFailure(e);
                 });
     }

    // Fallback path: call the original Firebase Storage upload logic (extracted here so Cloudinary path can reuse it)
    private void saveReceiptFirebaseFallback(Context context, Uri imageUri, Receipt receipt, String id, SaveCallback callback) {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
        FirebaseStorage storageInstance = getPreferredStorage(context);
        StorageReference ref = storageInstance.getReference().child("receipts/" + userId + "/" + id + ".jpg");

        try {
            UploadTask uploadTask;
            java.io.InputStream inputToClose = null;
            if ("content".equals(imageUri.getScheme())) {
                inputToClose = context.getContentResolver().openInputStream(imageUri);
                if (inputToClose != null) {
                    Log.d("ReceiptRepository", "Fallback: uploading via putStream to path=" + ref.getPath());
                    uploadTask = ref.putStream(inputToClose);
                } else {
                    Log.d("ReceiptRepository", "Fallback: InputStream null; falling back to putFile for path=" + ref.getPath());
                    uploadTask = ref.putFile(imageUri);
                }
            } else {
                Log.d("ReceiptRepository", "Fallback: uploading via putFile to path=" + ref.getPath());
                uploadTask = ref.putFile(imageUri);
            }

            attachUploadListeners(uploadTask, ref, inputToClose, receipt, id, callback, context, imageUri, false);

        } catch (Exception e) {
            Log.w("ReceiptRepository", "Fallback: Exception while uploading image", e);
            if (callback != null) callback.onFailure(e);
        }
    }

    private void attachUploadListeners(UploadTask uploadTask, StorageReference ref, java.io.InputStream toClose, Receipt receipt, String id, SaveCallback callback, Context context, Uri originalUri, boolean reuploadAttempted) {
        if (uploadTask == null) {
            if (toClose != null) {
                try { toClose.close(); } catch (Exception ignored) {}
            }
            if (callback != null) callback.onFailure(new IllegalArgumentException("uploadTask is null"));
            return;
        }

        uploadTask.addOnSuccessListener((OnSuccessListener<UploadTask.TaskSnapshot>) taskSnapshot -> {
            Log.d("ReceiptRepository", "upload success snapshot; metadataRefPath=" + (taskSnapshot != null && taskSnapshot.getMetadata() != null && taskSnapshot.getMetadata().getReference()!=null ? taskSnapshot.getMetadata().getReference().getPath() : "(null)"));
            if (toClose != null) {
                try { toClose.close(); } catch (Exception ex) { Log.d("ReceiptRepository", "Failed to close input stream after upload", ex); }
            }

            StorageReference uploadedRef = null;
            if (taskSnapshot != null && taskSnapshot.getMetadata() != null) uploadedRef = taskSnapshot.getMetadata().getReference();
            if (uploadedRef == null) uploadedRef = ref;

            Log.d("ReceiptRepository", "Resolving download URL for uploadedRefPath=" + uploadedRef.getPath());
            getDownloadUrlWithRetries(uploadedRef, 3, 1000, new DownloadUrlCallback() {
                @Override
                public void onSuccess(Uri uri) {
                    Log.d("ReceiptRepository", "download URL resolved=" + uri.toString());
                    receipt.setImageUrl(uri.toString());
                    Map<String, Object> map = new HashMap<>();
                    String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
                    map.put("storeName", receipt.getStoreName());
                    map.put("date", receipt.getDate());
                    map.put("total", receipt.getTotal());
                    map.put("items", receipt.getItems());
                    map.put("imageUrl", receipt.getImageUrl());
                    map.put("rawText", receipt.getRawText());
                    map.put("userId", userId);

                    db.collection("receipts").document(id).set(map, SetOptions.merge())
                            .addOnSuccessListener(aVoid -> {
                                if (callback != null) callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                Log.w("ReceiptRepository", "Failed to save receipt metadata", e);
                                if (callback != null) callback.onFailure(e);
                            });
                }

                @Override
                public void onFailure(Exception e) {
                    Log.w("ReceiptRepository", "Failed to obtain download URL after upload (all retries)", e);
                    if (!reuploadAttempted && originalUri != null) {
                        Log.w("ReceiptRepository", "Attempting reupload as fallback");
                        try {
                            java.io.InputStream newStream = null;
                            UploadTask retryTask;
                            if ("content".equals(originalUri.getScheme())) {
                                newStream = context.getContentResolver().openInputStream(originalUri);
                                if (newStream != null) {
                                    retryTask = ref.putStream(newStream);
                                } else {
                                    retryTask = ref.putFile(originalUri);
                                }
                            } else {
                                retryTask = ref.putFile(originalUri);
                            }
                            attachUploadListeners(retryTask, ref, newStream, receipt, id, callback, context, originalUri, true);
                            return;
                        } catch (Exception ex) {
                            Log.w("ReceiptRepository", "Reupload attempt failed", ex);
                            if (callback != null) callback.onFailure(ex);
                            return;
                        }
                    }
                    if (callback != null) callback.onFailure(e);
                }
            });

        }).addOnFailureListener(e -> {
            if (toClose != null) {
                try { toClose.close(); } catch (Exception ex) { Log.d("ReceiptRepository", "Failed to close input stream after failed upload", ex); }
            }
            Log.w("ReceiptRepository", "Image upload failed", e);

            boolean isNotFound = false;
            try {
                if (e instanceof StorageException) {
                    StorageException se = (StorageException) e;
                    int code = se.getErrorCode();
                    isNotFound = (code == StorageException.ERROR_OBJECT_NOT_FOUND) || (se.getMessage() != null && se.getMessage().toLowerCase().contains("not found"));
                } else if (e.getMessage() != null && e.getMessage().toLowerCase().contains("not found")) {
                    isNotFound = true;
                }
            } catch (Exception ignored) {}

            if (isNotFound && !reuploadAttempted && originalUri != null) {
                try {
                    String configuredBucket = null;
                    try { configuredBucket = FirebaseApp.getInstance().getOptions().getStorageBucket(); } catch (Exception ex) { Log.d("ReceiptRepository", "Failed to read configured bucket", ex); }
                    String alternateBucket = null;
                    if (configuredBucket != null) {
                        if (configuredBucket.contains("firebasestorage.app")) {
                            alternateBucket = configuredBucket.replace("firebasestorage.app", "appspot.com");
                        } else if (configuredBucket.contains("appspot.com")) {
                            alternateBucket = configuredBucket.replace("appspot.com", "firebasestorage.app");
                        }
                    }

                    if (alternateBucket != null && !alternateBucket.equals(configuredBucket)) {
                        Log.w("ReceiptRepository", "Attempting upload with alternate bucket: " + alternateBucket);
                        FirebaseStorage altStorage = FirebaseStorage.getInstance("gs://" + alternateBucket);
                        StorageReference altRef = altStorage.getReference().child(ref.getPath());
                        java.io.InputStream retryStream = null;
                        UploadTask retryTask;
                        if ("content".equals(originalUri.getScheme())) {
                            retryStream = context.getContentResolver().openInputStream(originalUri);
                            if (retryStream != null) retryTask = altRef.putStream(retryStream);
                            else retryTask = altRef.putFile(originalUri);
                        } else {
                            retryTask = altRef.putFile(originalUri);
                        }
                        attachUploadListeners(retryTask, altRef, retryStream, receipt, id, callback, context, originalUri, true);
                        return;
                    }
                } catch (Exception ex) {
                    Log.w("ReceiptRepository", "Alternate-bucket retry failed", ex);
                }

                if (!reuploadAttempted && originalUri != null) {
                    try {
                        Log.w("ReceiptRepository", "Attempting putBytes fallback upload");
                        uploadBytesFallback(context, originalUri, ref, receipt, id, callback);
                        return;
                    } catch (Exception ex) {
                        Log.w("ReceiptRepository", "putBytes fallback failed", ex);
                    }
                }
            }

            if (callback != null) callback.onFailure(e);
        });
    }

    public void searchByStore(String storePrefix, OnCompleteListener<QuerySnapshot> listener) {
        db.collection("receipts")
                .whereGreaterThanOrEqualTo("storeName", storePrefix)
                .whereLessThanOrEqualTo("storeName", storePrefix + "\uf8ff")
                .get()
                .addOnCompleteListener(listener);
    }

    public void fetchReceiptsForCurrentUser(OnCompleteListener<QuerySnapshot> listener) {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
        db.collection("receipts")
                .whereEqualTo("userId", userId)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(listener);
    }

    private interface DownloadUrlCallback {
        void onSuccess(Uri uri);
        void onFailure(Exception e);
    }

    private void getDownloadUrlWithRetries(StorageReference ref, int maxAttempts, long initialDelayMs, DownloadUrlCallback callback) {
        Handler handler = new Handler(Looper.getMainLooper());
        final int[] attemptCount = {0};
        final long[] currentDelay = {initialDelayMs};
        final long MAX_DELAY_MS = 10000; // 10 seconds max delay

        Runnable attempt = new Runnable() {
            @Override
            public void run() {
                attemptCount[0]++;
                Log.d("ReceiptRepository", "Attempt " + attemptCount[0] + " to get download URL for " + ref.getPath());
                
                ref.getDownloadUrl()
                    .addOnSuccessListener(uri -> {
                        Log.d("ReceiptRepository", "Successfully retrieved download URL on attempt " + attemptCount[0]);
                        if (callback != null) callback.onSuccess(uri);
                    })
                    .addOnFailureListener(e -> {
                        Log.w("ReceiptRepository", "getDownloadUrl failed on attempt " + attemptCount[0] + 
                                "/" + maxAttempts + ", error: " + e.getMessage());
                        
                        if (attemptCount[0] >= maxAttempts) {
                            Log.e("ReceiptRepository", "Max retry attempts (" + maxAttempts + ") reached");
                            if (callback != null) callback.onFailure(e);
                            return;
                        }
                        
                        currentDelay[0] = Math.min(currentDelay[0] * 2, MAX_DELAY_MS);
                        Log.d("ReceiptRepository", "Retrying in " + currentDelay[0] + "ms...");
                        
                        handler.postDelayed(this, currentDelay[0]);
                    });
            }
        };
        
        handler.post(attempt);
    }

    private FirebaseStorage getPreferredStorage(Context context) {
        try {
            String configured = null;
            try { configured = FirebaseApp.getInstance().getOptions().getStorageBucket(); } catch (Exception ignored) {}
            if (configured != null) {
                if (configured.contains("firebasestorage.app")) {
                    String alt = configured.replace("firebasestorage.app", "appspot.com");
                    try {
                        FirebaseStorage altStorage = FirebaseStorage.getInstance("gs://" + alt);
                        Log.d("ReceiptRepository", "Using alternate storage bucket gs://" + alt);
                        return altStorage;
                    } catch (Exception e) {
                        Log.d("ReceiptRepository", "Failed to use alternate storage bucket, falling back: " + alt, e);
                    }
                }
                try {
                    FirebaseStorage cfgStorage = FirebaseStorage.getInstance("gs://" + configured);
                    Log.d("ReceiptRepository", "Using configured storage bucket gs://" + configured);
                    return cfgStorage;
                } catch (Exception e) {
                    Log.d("ReceiptRepository", "Failed to use configured storage bucket, falling back to default", e);
                }
            }
        } catch (Exception ignored) {}
        return FirebaseStorage.getInstance();
    }

    private void uploadBytesFallback(Context context, Uri imageUri, StorageReference ref, Receipt receipt, String id, SaveCallback callback) {
        try {
            java.io.InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            byte[] buffer = new byte[8192];
            int bytesRead;
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            try {
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            } finally {
                inputStream.close();
            }
            byte[] imageData = output.toByteArray();
            UploadTask uploadTask = ref.putBytes(imageData);

            uploadTask.addOnSuccessListener((OnSuccessListener<UploadTask.TaskSnapshot>) taskSnapshot -> {
                Log.d("ReceiptRepository", "putBytes upload success snapshot; metadataRefPath=" + (taskSnapshot != null && taskSnapshot.getMetadata() != null && taskSnapshot.getMetadata().getReference()!=null ? taskSnapshot.getMetadata().getReference().getPath() : "(null)"));

                StorageReference uploadedRef = null;
                if (taskSnapshot != null && taskSnapshot.getMetadata() != null) uploadedRef = taskSnapshot.getMetadata().getReference();
                if (uploadedRef == null) uploadedRef = ref;

                Log.d("ReceiptRepository", "Resolving download URL for uploadedRefPath=" + uploadedRef.getPath());
                getDownloadUrlWithRetries(uploadedRef, 3, 1000, new DownloadUrlCallback() {
                    @Override
                    public void onSuccess(Uri uri) {
                        Log.d("ReceiptRepository", "download URL resolved=" + uri.toString());
                        receipt.setImageUrl(uri.toString());
                        Map<String, Object> map = new HashMap<>();
                        map.put("storeName", receipt.getStoreName());
                        map.put("date", receipt.getDate());
                        map.put("total", receipt.getTotal());
                        map.put("items", receipt.getItems());
                        map.put("imageUrl", receipt.getImageUrl());
                        map.put("rawText", receipt.getRawText());

                        db.collection("receipts").document(id).set(map, SetOptions.merge())
                                .addOnSuccessListener(aVoid -> {
                                    if (callback != null) callback.onSuccess();
                                })
                                .addOnFailureListener(e -> {
                                    Log.w("ReceiptRepository", "Failed to save receipt metadata", e);
                                    if (callback != null) callback.onFailure(e);
                                });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.w("ReceiptRepository", "Failed to obtain download URL after putBytes upload (all retries)", e);
                        if (callback != null) callback.onFailure(e);
                    }
                });

            }).addOnFailureListener(e -> {
                Log.w("ReceiptRepository", "putBytes upload failed", e);
                if (callback != null) callback.onFailure(e);
            });
        } catch (Exception e) {
            Log.w("ReceiptRepository", "Exception in uploadBytesFallback", e);
            if (callback != null) callback.onFailure(e);
        }
    }
}

