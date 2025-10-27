package com.mytrackr.receipts.features.receipts;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReceiptRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    // Do not store a static FirebaseStorage here - choose the correct bucket at runtime via getPreferredStorage(context)

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

        // upload image first - resolve the preferred storage instance for this app/context
        FirebaseStorage storageInstance = getPreferredStorage(context);
        StorageReference ref = storageInstance.getReference().child("receipts/" + userId + "/" + id + ".jpg");

        // Use putStream for content:// URIs (FileProvider) to avoid edge cases; fall back to putFile
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

    // Attach listeners to an UploadTask, retrieve download URL, persist metadata, and close stream if provided.
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
            // close stream first
            if (toClose != null) {
                try { toClose.close(); } catch (Exception ex) { Log.d("ReceiptRepository", "Failed to close input stream after upload", ex); }
            }

            StorageReference uploadedRef = null;
            if (taskSnapshot != null && taskSnapshot.getMetadata() != null) uploadedRef = taskSnapshot.getMetadata().getReference();
            if (uploadedRef == null) uploadedRef = ref;

            Log.d("ReceiptRepository", "Resolving download URL for uploadedRefPath=" + uploadedRef.getPath());
            // Try to get download URL with retries (transient consistency in Firebase Storage can cause "Object does not exist at location")
            getDownloadUrlWithRetries(uploadedRef, 3, 1000, new DownloadUrlCallback() {
                @Override
                public void onSuccess(Uri uri) {
                    Log.d("ReceiptRepository", "download URL resolved=" + uri.toString());
                    receipt.setImageUrl(uri.toString());
                    // save receipt to firestore
                    Map<String, Object> map = new HashMap<>();
                    // include the uploader id so we can query receipts per user
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
                    // Attempt a single reupload if not attempted yet
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
                            // attach listeners for retry (mark reuploadAttempted=true)
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
            // close stream on failure as well
            if (toClose != null) {
                try { toClose.close(); } catch (Exception ex) { Log.d("ReceiptRepository", "Failed to close input stream after failed upload", ex); }
            }
            Log.w("ReceiptRepository", "Image upload failed", e);

            // If we received a StorageException 404 (object/session not found), try an alternate bucket once
            boolean isNotFound = false;
            try {
                if (e instanceof StorageException) {
                    StorageException se = (StorageException) e;
                    int code = se.getErrorCode();
                    // -13010 often maps to 404 from underlying network
                    isNotFound = (code == StorageException.ERROR_OBJECT_NOT_FOUND) || (se.getMessage() != null && se.getMessage().toLowerCase().contains("not found"));
                } else if (e.getMessage() != null && e.getMessage().toLowerCase().contains("not found")) {
                    isNotFound = true;
                }
            } catch (Exception ignored) {}

            if (isNotFound && !reuploadAttempted && context != null && originalUri != null) {
                try {
                    // derive alternate bucket from configured FirebaseApp options
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

                // As a final fallback, try a single-shot putBytes upload (may use more memory but avoids resumable session issues)
                if (!reuploadAttempted && context != null && originalUri != null) {
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

    // Simple search by store name (prefix) and date range
    public void searchByStore(String storePrefix, OnCompleteListener<QuerySnapshot> listener) {
        db.collection("receipts")
                .whereGreaterThanOrEqualTo("storeName", storePrefix)
                .whereLessThanOrEqualTo("storeName", storePrefix + "\uf8ff")
                .get()
                .addOnCompleteListener(listener);
    }

    // Fetch receipts for the currently-signed-in user ordered by date desc
    public void fetchReceiptsForCurrentUser(OnCompleteListener<QuerySnapshot> listener) {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
        db.collection("receipts")
                .whereEqualTo("userId", userId)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(listener);
    }

    // Callback for download URL retrieval
    private interface DownloadUrlCallback {
        void onSuccess(Uri uri);
        void onFailure(Exception e);
    }

    // Attempt to get download URL with exponential backoff retry logic
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
                        
                        // Calculate next delay with exponential backoff, but cap it at MAX_DELAY_MS
                        currentDelay[0] = Math.min(currentDelay[0] * 2, MAX_DELAY_MS);
                        Log.d("ReceiptRepository", "Retrying in " + currentDelay[0] + "ms...");
                        
                        handler.postDelayed(this, currentDelay[0]);
                    });
            }
        };
        
        // Start the first attempt
        handler.post(attempt);
    }

    // Choose a FirebaseStorage instance based on configured bucket; prefer appspot.com form to avoid resumable upload 404s
    private FirebaseStorage getPreferredStorage(Context context) {
        try {
            String configured = null;
            try { configured = FirebaseApp.getInstance().getOptions().getStorageBucket(); } catch (Exception ignored) {}
            if (configured != null) {
                // If bucket uses the newer firebasestorage.app host, prefer appspot.com
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
                // Otherwise use the configured bucket directly
                try {
                    FirebaseStorage cfgStorage = FirebaseStorage.getInstance("gs://" + configured);
                    Log.d("ReceiptRepository", "Using configured storage bucket gs://" + configured);
                    return cfgStorage;
                } catch (Exception e) {
                    Log.d("ReceiptRepository", "Failed to use configured storage bucket, falling back to default", e);
                }
            }
        } catch (Exception ignored) {}
        // last resort: default instance
        return FirebaseStorage.getInstance();
    }

    // Fallback upload method using putBytes - reads entire image into memory
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
                // Try to get download URL with retries
                getDownloadUrlWithRetries(uploadedRef, 3, 1000, new DownloadUrlCallback() {
                    @Override
                    public void onSuccess(Uri uri) {
                        Log.d("ReceiptRepository", "download URL resolved=" + uri.toString());
                        receipt.setImageUrl(uri.toString());
                        // save receipt to firestore
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
