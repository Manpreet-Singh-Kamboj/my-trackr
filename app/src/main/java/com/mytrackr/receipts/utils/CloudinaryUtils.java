package com.mytrackr.receipts.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.mytrackr.receipts.R;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Utility class for uploading images to Cloudinary
 */
public class CloudinaryUtils {
    private static final String TAG = "CloudinaryUtils";

    /**
     * Callback interface for Cloudinary upload results
     */
    public interface CloudinaryUploadCallback {
        void onSuccess(String secureUrl, String publicId);
        void onFailure(Exception e);
    }

    /**
     * Configuration class for Cloudinary upload
     */
    public static class UploadConfig {
        public String cloudName;
        public String uploadPreset;
        public String folderRoot;
        public String userId;
        public String resourceId;

        public UploadConfig(String cloudName, String uploadPreset, String folderRoot,
                           String userId, String resourceId) {
            this.cloudName = cloudName;
            this.uploadPreset = uploadPreset;
            this.folderRoot = folderRoot;
            this.userId = userId;
            this.resourceId = resourceId;
        }
    }

    /**
     * Read Cloudinary configuration from app resources
     *
     * @param context Android context
     * @return UploadConfig if configuration is available, null otherwise
     */
    public static UploadConfig readConfig(Context context, String resourceId) {
        try {
            String cloudName = context.getString(R.string.cloudinary_cloud_name);
            String uploadPreset = context.getString(R.string.cloudinary_upload_preset);
            String folderRoot = context.getString(R.string.cloudinary_folder_root);

            if (cloudName != null && !cloudName.isEmpty() &&
                uploadPreset != null && !uploadPreset.isEmpty()) {

                String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                    : "anonymous";

                return new UploadConfig(cloudName, uploadPreset, folderRoot, userId, resourceId);
            }
        } catch (Exception ex) {
            Log.d(TAG, "Cloudinary config not available", ex);
        }
        return null;
    }

    /**
     * Check if Cloudinary is configured and available
     *
     * @param context Android context
     * @return true if Cloudinary is configured, false otherwise
     */
    public static boolean isConfigured(Context context) {
        try {
            String cloudName = context.getString(R.string.cloudinary_cloud_name);
            String uploadPreset = context.getString(R.string.cloudinary_upload_preset);
            return cloudName != null && !cloudName.isEmpty() &&
                   uploadPreset != null && !uploadPreset.isEmpty();
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Upload an image to Cloudinary using unsigned upload preset
     *
     * @param context Android context
     * @param imageUri URI of the image to upload
     * @param config Upload configuration
     * @param callback Callback for upload result
     */
    public static void uploadImage(Context context, Uri imageUri, UploadConfig config,
                                   CloudinaryUploadCallback callback) {
        Log.d(TAG, "Attempting Cloudinary upload: cloud=" + config.cloudName +
              " preset=" + config.uploadPreset);

        OkHttpClient client = new OkHttpClient.Builder().build();

        try {
            // Read image data from URI
            InputStream is = context.getContentResolver().openInputStream(imageUri);
            if (is == null) {
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onFailure(new IllegalStateException("Could not open image input stream")));
                }
                return;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = is.read(buffer)) != -1) {
                baos.write(buffer, 0, n);
            }
            is.close();
            byte[] imageBytes = baos.toByteArray();

            // Prepare multipart request
            MediaType mediaType = MediaType.parse("image/jpeg");
            RequestBody fileBody = RequestBody.create(imageBytes, mediaType);

            // Compute folder path: <folderRoot>/<userId>/<resourceId>
            String folderPath = buildFolderPath(config);

            MultipartBody.Builder mb = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "image.jpg", fileBody)
                .addFormDataPart("upload_preset", config.uploadPreset)
                .addFormDataPart("folder", folderPath);

            MultipartBody requestBody = mb.build();

            // Build request URL
            String url = "https://api.cloudinary.com/v1_1/" + config.cloudName + "/image/upload";
            Request request = new Request.Builder().url(url).post(requestBody).build();

            // Execute async request
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.w(TAG, "Cloudinary upload failed", e);
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() -> callback.onFailure(e));
                    }
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try {
                        if (!response.isSuccessful()) {
                            String respBody = response.body() != null ? response.body().string() : "";
                            Log.w(TAG, "Cloudinary upload returned non-success: " +
                                  response.code() + " body=" + respBody);

                            IOException error = new IOException("Upload failed: " + response.code());
                            if (callback != null) {
                                new Handler(Looper.getMainLooper()).post(() -> callback.onFailure(error));
                            }
                            return;
                        }

                        String body = response.body() != null ? response.body().string() : null;
                        if (body == null) {
                            IOException error = new IOException("Empty response body");
                            if (callback != null) {
                                new Handler(Looper.getMainLooper()).post(() -> callback.onFailure(error));
                            }
                            return;
                        }

                        try {
                            JSONObject json = new JSONObject(body);
                            final String secureUrl = json.optString("secure_url", null);
                            final String publicId = json.optString("public_id", null);

                            if (secureUrl == null || secureUrl.isEmpty()) {
                                Log.w(TAG, "Cloudinary response missing secure_url: " + body);
                                IOException error = new IOException("Missing secure_url in response");
                                if (callback != null) {
                                    new Handler(Looper.getMainLooper()).post(() -> callback.onFailure(error));
                                }
                                return;
                            }

                            Log.d(TAG, "Cloudinary upload succeeded: " + secureUrl);
                            if (callback != null) {
                                new Handler(Looper.getMainLooper()).post(() ->
                                    callback.onSuccess(secureUrl, publicId));
                            }

                        } catch (Exception ex) {
                            Log.w(TAG, "Cloudinary response parsing failed", ex);
                            if (callback != null) {
                                new Handler(Looper.getMainLooper()).post(() -> callback.onFailure(ex));
                            }
                        }
                    } catch (IOException ioEx) {
                        Log.w(TAG, "Error reading Cloudinary response", ioEx);
                        if (callback != null) {
                            new Handler(Looper.getMainLooper()).post(() -> callback.onFailure(ioEx));
                        }
                    } finally {
                        if (response.body() != null) response.close();
                    }
                }
            });

        } catch (Exception e) {
            Log.w(TAG, "Exception preparing Cloudinary upload", e);
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onFailure(e));
            }
        }
    }

    /**
     * Build the folder path for Cloudinary storage
     *
     * @param config Upload configuration
     * @return Folder path string
     */
    private static String buildFolderPath(UploadConfig config) {
        if (config.folderRoot != null && !config.folderRoot.isEmpty()) {
            return config.folderRoot + "/" + config.userId + "/" + config.resourceId;
        } else {
            return config.userId + "/" + config.resourceId;
        }
    }

    /**
     * Generate transformation URL for an uploaded image
     *
     * @param publicId The public ID from Cloudinary
     * @param cloudName The Cloudinary cloud name
     * @param transformations Transformation string (e.g., "w_300,h_300,c_fill")
     * @return Transformed image URL
     */
    public static String getTransformationUrl(String publicId, String cloudName, String transformations) {
        if (publicId == null || cloudName == null) return null;
        return "https://res.cloudinary.com/" + cloudName + "/image/upload/" +
               transformations + "/" + publicId;
    }

    /**
     * Generate thumbnail URL for an uploaded image
     *
     * @param publicId The public ID from Cloudinary
     * @param cloudName The Cloudinary cloud name
     * @param width Thumbnail width
     * @param height Thumbnail height
     * @return Thumbnail URL
     */
    public static String getThumbnailUrl(String publicId, String cloudName, int width, int height) {
        return getTransformationUrl(publicId, cloudName,
            "w_" + width + ",h_" + height + ",c_fill,q_auto");
    }
}

