package com.mytrackr.receipts.features.receipts;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import com.google.firebase.auth.FirebaseAuth;
import com.mytrackr.receipts.BuildConfig;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.data.models.ReceiptItem;
import com.mytrackr.receipts.data.repository.ReceiptRepository;
import com.mytrackr.receipts.databinding.ActivityReceiptScanBinding;
import com.mytrackr.receipts.utils.GeminiApiService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Locale;

public class ReceiptScanActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA = 1001;
    private static final String TAG = "ReceiptScanActivity";
    private ActivityReceiptScanBinding binding;
    private Uri imageUri;

    private ImageView previewImageView;
    private TextView ocrTextView;
    private View ocrProcessingProgressBar;
    private View cameraCard, galleryCard;
    private View emptyStateLayout;
    private View ocrResultCard;
    private View cornerEditSection;
    private Button btnProcess, btnSave;
    private View geminiStepsLayout;
    private TextView tvGeminiStep1, tvGeminiStep2, tvGeminiStep3;
    private android.os.Handler geminiStepsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int geminiStepIndex = 0;

    // New UI for corner editing
    private com.mytrackr.receipts.features.receipts.CornerOverlayView cornerOverlay;
    private Button btnEditCorners, btnAcceptCrop, btnCancelCrop;

    private Receipt currentReceipt;

    // Keep a reference to the original bitmap currently loaded (may be large). Null when none.
    private Bitmap lastBitmapOriginal = null;

    // Activity Result launchers
    private ActivityResultLauncher<IntentSenderRequest> scanLauncher;
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;

    // progress overlay view (in-layout) shown while processing (OCR/enhance)
    private View progressOverlay;

    // Gemini API service
    private GeminiApiService geminiApiService;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable edge-to-edge display
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        binding = ActivityReceiptScanBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        // Setup toolbar
        com.google.android.material.appbar.MaterialToolbar toolbar = binding.toolbar.toolbar;
        TextView toolbarTitle = binding.toolbar.toolbarTitle;
        toolbarTitle.setText(getString(R.string.receipt_scan));
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        previewImageView = binding.previewImageView;
        ocrTextView = binding.ocrTextView;
        ocrProcessingProgressBar = binding.processingBar;
        cameraCard = binding.cameraCard;
        galleryCard = binding.galleryCard;
        emptyStateLayout = binding.emptyStateLayout;
        ocrResultCard = binding.ocrResultCard;
        cornerEditSection = binding.cornerEditSection;
        btnProcess = binding.btnProcess;
        btnSave = binding.btnSave;
        geminiStepsLayout = binding.geminiStepsLayout;
        tvGeminiStep1 = binding.tvGeminiStep1;
        tvGeminiStep2 = binding.tvGeminiStep2;
        tvGeminiStep3 = binding.tvGeminiStep3;

        // corner editing controls
        cornerOverlay = binding.cornerOverlay;
        btnEditCorners = binding.btnEditCorners;
        btnAcceptCrop = binding.btnAcceptCrop;
        btnCancelCrop = binding.btnCancelCrop;

        // Register Activity Result launchers
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            Boolean readStorageGranted = result.get(Manifest.permission.READ_EXTERNAL_STORAGE);
            Boolean readMediaGranted = result.get(Manifest.permission.READ_MEDIA_IMAGES);

            if ((readStorageGranted != null && readStorageGranted) ||
                    (readMediaGranted != null && readMediaGranted)) {
                // Permission granted, open gallery
                galleryLauncher.launch("image/*");
            } else {
                Toast.makeText(this, getString(R.string.storage_permission_is_required_to_access_gallery), Toast.LENGTH_LONG).show();
            }
        });

        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                handlePickedImageUri(uri);
            }
        });

        scanLauncher = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
            if (result != null && result.getResultCode() == Activity.RESULT_OK) {
                handleScanResult(result.getData());
            }
        });

        // Setup click listeners for card-based buttons
        cameraCard.setOnClickListener(v -> launchDocumentScanner());
        galleryCard.setOnClickListener(v -> openGalleryWithPermissionCheck());
        btnProcess.setOnClickListener(v -> processImageForText());
        btnSave.setOnClickListener(v -> saveReceipt());

        // corner edit handlers
        btnEditCorners.setOnClickListener(v -> enterCornerEditMode());
        btnAcceptCrop.setOnClickListener(v -> applyUserCropAndReprocess());
        btnCancelCrop.setOnClickListener(v -> exitCornerEditMode(false));

        // when user drags corners, we may want to preview something — currently we'll reprocess only on accept
        cornerOverlay.setOnCornersChangedListener(viewCorners -> {
            // no-op for now; could provide live preview when dragging
        });

        // bind progress overlay from layout
        progressOverlay = binding.progressOverlay;

        // Initialize Gemini API service
        geminiApiService = new GeminiApiService(BuildConfig.GEMINI_API_KEY);

        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        String[] perms = {Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE};
        boolean need = false;
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                need = true; break;
            }
        }
        if (need) ActivityCompat.requestPermissions(this, perms, 1234);
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    // Use the Activity Result API for gallery
    private void openGalleryWithPermissionCheck() {
        // Check Android version for appropriate permission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED) {
                galleryLauncher.launch("image/*");
            } else {
                permissionLauncher.launch(new String[]{Manifest.permission.READ_MEDIA_IMAGES});
            }
        } else {
            // Android 12 and below use READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                galleryLauncher.launch("image/*");
            } else {
                permissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
            }
        }
    }

    private void openGallery() {
        galleryLauncher.launch("image/*");
    }

    private void launchDocumentScanner() {
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                // allow gallery import so scanner can also import an existing photo if user wants
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build();
        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);
        scanner.getStartScanIntent(this)
                .addOnSuccessListener(intentSender -> {
                    try {
                        // Use Activity Result API instead of deprecated startIntentSenderForResult
                        IntentSenderRequest req = new IntentSenderRequest.Builder(intentSender).build();
                        scanLauncher.launch(req);
                    } catch (Exception e) {
                        Toast.makeText(this, getString(R.string.failed_to_start_scanner, e.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.scanner_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
    }

    // Keep a minimal onActivityResult just in case other legacy flows call it (camera fallback)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) return;
        if (requestCode == REQUEST_CAMERA) {
            if (imageUri != null) {
                launchDocumentScanner();
            }
        }
    }

    // Handle the scanner result (extracted from previous onActivityResult code)
    private void handleScanResult(Intent data) {
        GmsDocumentScanningResult result = GmsDocumentScanningResult.fromActivityResultIntent(data);
        if (result == null) return;
        List<GmsDocumentScanningResult.Page> pages = result.getPages();
        if (pages == null || pages.isEmpty()) return;
        GmsDocumentScanningResult.Page page = pages.get(0);
        Bitmap scannedImage = null;

        try {
            Method m = page.getClass().getMethod("getImage");
            Object img = m.invoke(page);
            if (img instanceof Bitmap) scannedImage = (Bitmap) img;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // not available on this runtime - that's fine
            Log.d(TAG, "page.getImage reflection not available", e);
        }

        if (scannedImage == null) {
            try {
                Method m2 = page.getClass().getMethod("getBitmap");
                Object img2 = m2.invoke(page);
                if (img2 instanceof Bitmap) scannedImage = (Bitmap) img2;
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                Log.d(TAG, "page.getBitmap reflection not available", e);
            }
        }

        // Try to get corner points from the Page (if the runtime ML Kit provides them)
        float[] pageCorners = extractCornerPointsFromPage(page);

        if (scannedImage == null) {
            // attempt to load from a URI if page provided one
            String[] uriMethodNames = new String[]{"getImageUri", "getContentUri", "getUri", "getContentUriString", "getImageUriString"};
            for (String name : uriMethodNames) {
                if (scannedImage != null) break;
                try {
                    Method mu = page.getClass().getMethod(name);
                    Object uriObj = mu.invoke(page);
                    if (uriObj instanceof Uri) {
                        try {
                            scannedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), (Uri) uriObj);
                            imageUri = (Uri) uriObj;
                            break;
                        } catch (IOException ioe) {
                            Log.w(TAG, "failed to load Uri from page method: " + name, ioe);
                        }
                    } else if (uriObj instanceof String) {
                        String s = (String) uriObj;
                        try {
                            Uri parsed = Uri.parse(s);
                            scannedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), parsed);
                            imageUri = parsed;
                            break;
                        } catch (Exception ex) {
                            try {
                                Uri parsed = Uri.parse(s);
                                InputStream is = null;
                                try {
                                    is = getContentResolver().openInputStream(parsed);
                                    if (is != null) {
                                        scannedImage = android.graphics.BitmapFactory.decodeStream(is);
                                        imageUri = parsed;
                                        break;
                                    }
                                } finally {
                                    if (is != null) try { is.close(); } catch (IOException closeEx) { Log.w(TAG, "failed to close stream", closeEx); }
                                }
                            } catch (Exception e) { Log.w(TAG, "failed to parse page string uri: " + s, e); }
                        }
                    }
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    Log.d(TAG, "reflection helper missing", e);
                }
            }
        }

        if (scannedImage == null && imageUri != null) {
            try {
                scannedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            } catch (IOException ioe) { Log.w(TAG, "failed to load fallback imageUri", ioe); }
        }

        if (scannedImage != null) {
            // If we have page corners, apply perspective crop first (C1)
            if (pageCorners != null) {
                try {
                    float[] norm = normalizeAndOrderCorners(pageCorners, scannedImage);
                    Bitmap cropped = perspectiveCrop(scannedImage, norm != null ? norm : pageCorners);
                    if (cropped != null) {
                        // recycle previous scannedImage to free memory if different
                        if (scannedImage != cropped && !scannedImage.isRecycled()) scannedImage.recycle();
                        scannedImage = cropped;
                    }
                } catch (Exception e) { Log.w(TAG, "perspective crop failed", e); }
            }

            // Keep a high-resolution copy for corner editing (C2)
            try {
                if (lastBitmapOriginal != null && !lastBitmapOriginal.isRecycled()) lastBitmapOriginal.recycle();
            } catch (Exception e) { Log.d(TAG, "non-fatal error", e); }
            try {
                lastBitmapOriginal = scannedImage.copy(Bitmap.Config.ARGB_8888, true);
            } catch (Exception e) {
                // fallback to the reference
                lastBitmapOriginal = scannedImage;
            }
            // enable corner edit button now that an image exists
            try { if (btnEditCorners != null) btnEditCorners.setEnabled(true); } catch (Exception e) { Log.d(TAG, "failed enabling edit button", e); }

            // Downscale for processing to limit memory / speed up convolution and OCR
            try {
                Bitmap scaled = scaleBitmapToMaxDim(scannedImage, 1600);
                if (scaled != scannedImage) {
                    if (!scannedImage.isRecycled()) scannedImage.recycle();
                    scannedImage = scaled;
                }
            } catch (Exception e) { Log.d(TAG, "downscale failure", e); }

            // Trim whitespace margins to make the preview tighter
            try { scannedImage = trimWhitespace(scannedImage, 230); } catch (Exception e) { Log.d(TAG, "trimWhitespace failed", e); }

            // Apply contrast / sharpen enhancement (B)
            try { scannedImage = enhanceBitmap(scannedImage); } catch (Exception e) { Log.d(TAG, "enhanceBitmap failed", e); }

            try {
                File scannedFile = createImageFile();
                FileOutputStream out = new FileOutputStream(scannedFile);
                scannedImage.compress(Bitmap.CompressFormat.JPEG, 90, out);
                out.flush(); out.close();
                imageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", scannedFile);
                loadImageIntoPreview(imageUri);
            } catch (IOException e) {
                Toast.makeText(this, getString(R.string.failed_to_save_scanned_image), Toast.LENGTH_SHORT).show();
                previewImageView.setImageBitmap(scannedImage);
            }
        } else {
            Toast.makeText(this, getString(R.string.no_scanned_image_available), Toast.LENGTH_SHORT).show();
        }
    }

    // Attempt to extract corner points (x0,y0,...x3,y3) from the Page object via reflection
    private float[] extractCornerPointsFromPage(Object page) {
        if (page == null) return null;
        String[] candidateMethods = new String[]{"getQuadrilateral", "getCornerPoints", "getCorners", "getPoints", "getDocumentCorners", "getCornerPointFs", "getCornerPointF", "getDetectedCorners"};
        for (String name : candidateMethods) {
            try {
                Method m = page.getClass().getMethod(name);
                Object res = m.invoke(page);
                if (res == null) continue;
                // Array of PointF or Point
                if (res instanceof float[]) {
                    float[] f = (float[]) res;
                    if (f.length >= 8) return new float[]{f[0],f[1],f[2],f[3],f[4],f[5],f[6],f[7]};
                }
                if (res instanceof double[]) {
                    double[] d = (double[]) res;
                    if (d.length >= 8) return new float[]{(float)d[0],(float)d[1],(float)d[2],(float)d[3],(float)d[4],(float)d[5],(float)d[6],(float)d[7]};
                }
                if (res instanceof int[]) {
                    int[] ii = (int[]) res;
                    if (ii.length >= 8) return new float[]{ii[0],ii[1],ii[2],ii[3],ii[4],ii[5],ii[6],ii[7]};
                }
                if (res instanceof java.util.List) {
                    java.util.List<?> list = (java.util.List<?>) res;
                    if (list.size() >= 4) {
                        float[] out = new float[8];
                        for (int i=0;i<4;i++) {
                            Object p = list.get(i);
                            float x = getXFromPointLike(p);
                            float y = getYFromPointLike(p);
                            out[i*2] = x; out[i*2+1] = y;
                        }
                        return out;
                    }
                }
                if (res instanceof Object[]) {
                    Object[] arr = (Object[]) res;
                    if (arr.length >= 4) {
                        float[] out = new float[8];
                        for (int i=0;i<4;i++) {
                            Object p = arr[i];
                            float x = getXFromPointLike(p);
                            float y = getYFromPointLike(p);
                            out[i*2] = x; out[i*2+1] = y;
                        }
                        return out;
                    }
                }
                // Single object with methods getTopLeft/getTopRight etc
                float[] mapped = tryNamedCornerAccessors(res);
                if (mapped != null) return mapped;
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                Log.w(TAG, "reflection failed extracting corners", e);
            }
        }
        return null;
    }

    private float getXFromPointLike(Object p) {
        if (p == null) return 0f;
        try {
            Method gx = p.getClass().getMethod("getX");
            Object ox = gx.invoke(p);
            if (ox instanceof Number) return ((Number)ox).floatValue();
        } catch (Exception e) {
            Log.d(TAG, "getXFromPointLike:getX not available", e);
        }
        try {
            Method gx = p.getClass().getMethod("x");
            Object ox = gx.invoke(p);
            if (ox instanceof Number) return ((Number)ox).floatValue();
        } catch (Exception e) {
            Log.d(TAG, "getXFromPointLike:x not available", e);
        }
        try {
            Method gx = p.getClass().getMethod("getXf");
            Object ox = gx.invoke(p);
            if (ox instanceof Number) return ((Number)ox).floatValue();
        } catch (Exception e) {
            Log.d(TAG, "getXFromPointLike:getXf not available", e);
        }
        return 0f;
    }
    private float getYFromPointLike(Object p) {
        if (p == null) return 0f;
        try {
            Method gy = p.getClass().getMethod("getY");
            Object oy = gy.invoke(p);
            if (oy instanceof Number) return ((Number)oy).floatValue();
        } catch (Exception e) {
            Log.d(TAG, "getYFromPointLike:getY not available", e);
        }
        try {
            Method gy = p.getClass().getMethod("y");
            Object oy = gy.invoke(p);
            if (oy instanceof Number) return ((Number)oy).floatValue();
        } catch (Exception e) {
            Log.d(TAG, "getYFromPointLike:y not available", e);
        }
        try {
            Method gy = p.getClass().getMethod("getYf");
            Object oy = gy.invoke(p);
            if (oy instanceof Number) return ((Number)oy).floatValue();
        } catch (Exception e) {
            Log.d(TAG, "getYFromPointLike:getYf not available", e);
        }
        return 0f;
    }

    private float[] tryNamedCornerAccessors(Object obj) {
        if (obj == null) return null;
        String[] names = new String[]{"getTopLeft","getTopRight","getBottomRight","getBottomLeft","topLeft","topRight","bottomRight","bottomLeft"};
        try {
            float[] out = new float[8];
            for (int i=0;i<4;i++) {
                String nm = names[i];
                Method m = obj.getClass().getMethod(nm);
                Object p = m.invoke(obj);
                float x = getXFromPointLike(p);
                float y = getYFromPointLike(p);
                out[i*2] = x; out[i*2+1] = y;
            }
            return out;
        } catch (Exception e) {
            Log.d(TAG, "tryNamedCornerAccessors reflection failed", e);
        }
        return null;
    }

    // Perform perspective crop using 4 source corner points (x0,y0..x3,y3). Returns a new bitmap or null.
    private Bitmap perspectiveCrop(Bitmap src, float[] srcPts) {
        if (src == null || srcPts == null || srcPts.length < 8) return null;
        // srcPts assumed in order [tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y]
        float tlx = srcPts[0], tly = srcPts[1];
        float trx = srcPts[2], tryy = srcPts[3];
        float brx = srcPts[4], bry = srcPts[5];
        float blx = srcPts[6], bly = srcPts[7];
        // compute target width/height based on distances
        float widthTop = distance(tlx, tly, trx, tryy);
        float widthBottom = distance(blx, bly, brx, bry);
        int dstW = Math.max(1, Math.round(Math.max(widthTop, widthBottom)));
        float heightLeft = distance(tlx, tly, blx, bly);
        float heightRight = distance(trx, tryy, brx, bry);
        int dstH = Math.max(1, Math.round(Math.max(heightLeft, heightRight)));

        float[] dst = new float[]{0f,0f, dstW,0f, dstW,(float)dstH, 0f,(float)dstH};
        float[] srcf = new float[]{tlx,tly, trx,tryy, brx,bry, blx,bly};
        Matrix matrix = new Matrix();
        boolean ok = matrix.setPolyToPoly(srcf, 0, dst, 0, 4);
        if (!ok) return null;
        try {
            Bitmap out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            c.drawBitmap(src, matrix, p);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private float distance(float x1,float y1,float x2,float y2){
        float dx = x2-x1; float dy = y2-y1; return (float)Math.hypot(dx,dy);
    }

    // Normalize corner coordinates (if they're normalized 0..1) to bitmap pixels and order them TL,TR,BR,BL.
    private float[] normalizeAndOrderCorners(float[] corners, Bitmap src) {
        if (corners == null || corners.length < 8 || src == null) return corners;
        float w = src.getWidth();
        float h = src.getHeight();
        // copy
        float[] pts = new float[8];
        System.arraycopy(corners, 0, pts, 0, 8);
        // detect if values are normalized (<= 1.01)
        boolean normalized = true;
        for (int i = 0; i < 8; i++) {
            if (Math.abs(pts[i]) > 1.01f) { normalized = false; break; }
        }
        if (normalized) {
            for (int i = 0; i < 8; i += 2) {
                pts[i] = pts[i] * w; // x
                pts[i+1] = pts[i+1] * h; // y
            }
        }

        // compute centroid
        float cx = 0f, cy = 0f;
        for (int i = 0; i < 8; i += 2) { cx += pts[i]; cy += pts[i+1]; }
        cx /= 4f; cy /= 4f;

        // Make final copies so lambdas/anonymous comparators can capture them safely
        final float cxFinal = cx;
        final float cyFinal = cy;

        // build array of indices and sort by angle around centroid
        Integer[] idx = new Integer[]{0,1,2,3};
        java.util.Arrays.sort(idx, (a,b) -> {
            float ax = pts[a*2] - cxFinal; float ay = pts[a*2+1] - cyFinal;
            float bx = pts[b*2] - cxFinal; float by = pts[b*2+1] - cyFinal;
            double angA = Math.atan2(ay, ax);
            double angB = Math.atan2(by, bx);
            return Double.compare(angA, angB);
        });
        float[][] ordered = new float[4][2];
        for (int i = 0; i < 4; i++) {
            ordered[i][0] = pts[idx[i]*2];
            ordered[i][1] = pts[idx[i]*2+1];
        }
        // find index of top-left (min y, then min x)
        int topLeftIndex = 0;
        float bestY = ordered[0][1], bestX = ordered[0][0];
        for (int i = 1; i < 4; i++) {
            if (ordered[i][1] < bestY - 1e-3f || (Math.abs(ordered[i][1]-bestY) < 1e-3f && ordered[i][0] < bestX)) {
                bestY = ordered[i][1]; bestX = ordered[i][0]; topLeftIndex = i;
            }
        }

        float[] out = new float[8];
        for (int i = 0; i < 4; i++) {
            int srcIdx = (topLeftIndex + i) % 4;
            out[i*2] = ordered[srcIdx][0];
            out[i*2+1] = ordered[srcIdx][1];
        }
        return out;
    }

    // Scale bitmap to a maximum dimension (preserve aspect ratio). If already small enough, returns the same instance.
    private Bitmap scaleBitmapToMaxDim(Bitmap src, int maxDim) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        int max = Math.max(w, h);
        if (max <= maxDim) return src;
        float scale = (float) maxDim / (float) max;
        int nw = Math.max(1, Math.round(w * scale));
        int nh = Math.max(1, Math.round(h * scale));
        try {
            Bitmap scaled = Bitmap.createScaledBitmap(src, nw, nh, true);
            return scaled;
        } catch (Exception e) {
            return src;
        }
    }

    private void showProcessingDialog() {
        try {
            if (progressOverlay != null) progressOverlay.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {}
    }

    private void hideProcessingDialog() {
        try {
            if (progressOverlay != null) progressOverlay.setVisibility(View.GONE);
        } catch (Exception ignored) {}
    }

    // Enhancement: contrast boost + a lightweight sharpen pass
    private Bitmap enhanceBitmap(Bitmap src) {
        if (src == null) return null;
        Bitmap contrasted = applyContrast(src, 1.15f, -10f);
        try {
            Bitmap sharpened = applySharpen(contrasted);
            if (sharpened != null) return sharpened;
        } catch (Exception ignored) {}
        return contrasted;
    }

    // Save a bitmap to a temporary file and return a content Uri (FileProvider). Returns null on failure.
    private Uri saveBitmapToTempUri(Bitmap bmp) {
        if (bmp == null) return null;
        try {
            File f = createImageFile();
            FileOutputStream out = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush(); out.close();
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
        } catch (Exception e) {
            return null;
        }
    }

    // Simple contrast/brightness adjustment using ColorMatrix
    private Bitmap applyContrast(Bitmap src, float contrast, float brightness) {
        if (src == null) return null;
        try {
            Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
            ColorMatrix cm = new ColorMatrix(new float[]{
                    contrast, 0, 0, 0, brightness,
                    0, contrast, 0, 0, brightness,
                    0, 0, contrast, 0, brightness,
                    0, 0, 0, 1, 0
            });
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setColorFilter(new ColorMatrixColorFilter(cm));
            c.drawBitmap(src, 0, 0, p);
            return out;
        } catch (Exception e) {
            return src;
        }
    }

    // Lightweight sharpen via a 3x3 convolution kernel
    private Bitmap applySharpen(Bitmap src) {
        if (src == null) return null;
        try {
            int w = src.getWidth();
            int h = src.getHeight();
            int[] in = new int[w * h];
            int[] out = new int[w * h];
            src.getPixels(in, 0, w, 0, 0, w, h);

            // sharpen kernel
            int[] k = new int[]{0, -1, 0, -1, 5, -1, 0, -1, 0};
            int kIdx;
            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    int r = 0, g = 0, b = 0;
                    kIdx = 0;
                    for (int ky = -1; ky <= 1; ky++) {
                        for (int kx = -1; kx <= 1; kx++, kIdx++) {
                            int px = in[(y + ky) * w + (x + kx)];
                            int kr = (px >> 16) & 0xFF;
                            int kg = (px >> 8) & 0xFF;
                            int kb = px & 0xFF;
                            int kval = k[kIdx];
                            r += kr * kval;
                            g += kg * kval;
                            b += kb * kval;
                        }
                    }
                    // clamp
                    r = Math.min(255, Math.max(0, r));
                    g = Math.min(255, Math.max(0, g));
                    b = Math.min(255, Math.max(0, b));
                    int a = (in[y * w + x] >> 24) & 0xFF;
                    out[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
            // copy edges from source
            for (int x = 0; x < w; x++) { out[x] = in[x]; out[(h - 1) * w + x] = in[(h - 1) * w + x]; }
            for (int y = 0; y < h; y++) { out[y * w] = in[y * w]; out[y * w + w - 1] = in[y * w + w - 1]; }

            Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            result.setPixels(out, 0, w, 0, 0, w, h);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    // Update handlePickedImageUri to also load and store the original bitmap for corner editing
    private void handlePickedImageUri(Uri uri) {
        if (uri == null) return;
        imageUri = uri;
        // load full-resolution bitmap (may be large) and keep a reference for cropping
        try {
            Bitmap bm = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            if (bm != null) {
                // recycle previous original
                if (lastBitmapOriginal != null && !lastBitmapOriginal.isRecycled()) lastBitmapOriginal.recycle();
                lastBitmapOriginal = bm;
            }
        } catch (Exception e) {
            Log.w(TAG, "failed to load original bitmap from gallery", e);
            lastBitmapOriginal = null;
        }
        ocrTextView.setText("");
        btnProcess.setEnabled(true);
        btnSave.setEnabled(false);
        loadImageIntoPreview(uri);
    }

    // Helper: map bitmap coordinate points to view coordinates for overlay (fitCenter)
    private float[] bitmapPointsToView(float[] bmpPts, Bitmap bmp) {
        if (bmpPts == null || bmp == null || bmpPts.length < 8) return null;
        int vw = previewImageView.getWidth();
        int vh = previewImageView.getHeight();
        if (vw == 0 || vh == 0) return null;
        int iw = bmp.getWidth();
        int ih = bmp.getHeight();
        float scale = Math.min((float) vw / iw, (float) vh / ih);
        float displayedW = iw * scale;
        float displayedH = ih * scale;
        float offsetX = (vw - displayedW) / 2f;
        float offsetY = (vh - displayedH) / 2f;
        float[] out = new float[8];
        for (int i = 0; i < 4; i++) {
            out[i*2] = bmpPts[i*2] * scale + offsetX;
            out[i*2+1] = bmpPts[i*2+1] * scale + offsetY;
        }
        return out;
    }

    // Reverse: map view coords from overlay to bitmap coords
    private float[] viewPointsToBitmap(float[] viewPts, Bitmap bmp) {
        if (viewPts == null || bmp == null || viewPts.length < 8) return null;
        int vw = previewImageView.getWidth();
        int vh = previewImageView.getHeight();
        if (vw == 0 || vh == 0) return null;
        int iw = bmp.getWidth();
        int ih = bmp.getHeight();
        float scale = Math.min((float) vw / iw, (float) vh / ih);
        float displayedW = iw * scale;
        float displayedH = ih * scale;
        float offsetX = (vw - displayedW) / 2f;
        float offsetY = (vh - displayedH) / 2f;
        float[] out = new float[8];
        for (int i = 0; i < 4; i++) {
            out[i*2] = (viewPts[i*2] - offsetX) / scale;
            out[i*2+1] = (viewPts[i*2+1] - offsetY) / scale;
        }
        return out;
    }

    // Enter corner edit mode: show overlay and allow dragging
    private void enterCornerEditMode() {
        if (lastBitmapOriginal == null) {
            Toast.makeText(this, getString(R.string.no_image_available_to_edit_corners), Toast.LENGTH_SHORT).show();
            return;
        }
        // determine default corners: use full bitmap corners
        final float[] bmpCorners = new float[]{0f,0f, (float)lastBitmapOriginal.getWidth(),0f, (float)lastBitmapOriginal.getWidth(), (float)lastBitmapOriginal.getHeight(), 0f, (float)lastBitmapOriginal.getHeight()};
        // Map to view coords after preview ImageView has laid out
        previewImageView.post(() -> {
            float[] viewCorners = bitmapPointsToView(bmpCorners, lastBitmapOriginal);
            if (viewCorners != null) {
                cornerOverlay.setCornersViewCoords(viewCorners);
                cornerOverlay.show();

                // Show corner edit section, hide other buttons
                if (cornerEditSection != null) cornerEditSection.setVisibility(View.VISIBLE);
                if (btnEditCorners != null) btnEditCorners.setVisibility(View.GONE);
                if (btnProcess != null) btnProcess.setEnabled(false);
                if (btnSave != null) btnSave.setEnabled(false);
            } else {
                Toast.makeText(this, getString(R.string.unable_to_show_corner_editor), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Exit corner edit mode; if applyCrop==true we will have already applied changes in applyUserCropAndReprocess()
    private void exitCornerEditMode(boolean restoreButtons) {
        cornerOverlay.hide();

        // Hide corner edit section, show edit corners button
        if (cornerEditSection != null) cornerEditSection.setVisibility(View.GONE);
        if (btnEditCorners != null) btnEditCorners.setVisibility(View.VISIBLE);

        if (restoreButtons) {
            if (btnProcess != null) btnProcess.setEnabled(true);
            if (btnSave != null) btnSave.setEnabled(true);
        }
    }

    // Apply user crop from the overlay, update image and re-run OCR (automatic)
    private void applyUserCropAndReprocess() {
        if (lastBitmapOriginal == null) {
            Toast.makeText(this, getString(R.string.no_image_to_crop), Toast.LENGTH_SHORT).show();
            exitCornerEditMode(true);
            return;
        }
        float[] viewCorners = cornerOverlay.getCornersViewCoords();
        if (viewCorners == null) { Toast.makeText(this, getString(R.string.no_corners_available), Toast.LENGTH_SHORT).show(); return; }
        float[] bmpCorners = viewPointsToBitmap(viewCorners, lastBitmapOriginal);
        if (bmpCorners == null) { Toast.makeText(this, getString(R.string.failed_to_map_corners), Toast.LENGTH_SHORT).show(); return; }

        showProcessingDialog();
        try {
            Bitmap cropped = perspectiveCrop(lastBitmapOriginal, bmpCorners);
            if (cropped == null) {
                hideProcessingDialog();
                Toast.makeText(this, getString(R.string.crop_failed), Toast.LENGTH_SHORT).show();
                exitCornerEditMode(true);
                return;
            }
            // replace lastBitmapOriginal with cropped
            if (!lastBitmapOriginal.isRecycled()) lastBitmapOriginal.recycle();
            lastBitmapOriginal = cropped;
            // save enhanced cropped preview into temp file and update imageUri
            // optionally we may enhance before saving — keep behavior consistent with earlier flows
            Bitmap proc = scaleBitmapToMaxDim(cropped, 1600);
            try { proc = enhanceBitmap(proc); } catch (Exception e) { Log.d(TAG, "enhanceBitmap failed during crop flow", e); }
            Uri newUri = saveBitmapToTempUri(proc != null ? proc : cropped);
            if (newUri != null) imageUri = newUri;
            // update preview
            previewImageView.setImageBitmap(cropped);

            // exit edit mode and re-run OCR on cropped/enhanced image
            exitCornerEditMode(false);
            // process OCR on cropped image
            processImageForText();
        } finally {
            hideProcessingDialog();
        }
    }

    // Modify processImageForText to prefer lastBitmapOriginal if present
    private void processImageForText() {
        if (imageUri == null && lastBitmapOriginal == null) {
            Toast.makeText(this, getString(R.string.no_image_selected_to_process), Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button to prevent multiple calls while processing
        if (btnProcess != null) {
            btnProcess.setEnabled(false);
        }

        showProcessingDialog();
        try {
            Bitmap bm = null;
            if (lastBitmapOriginal != null) {
                // use a copy or scaled version to avoid modifying the master
                bm = lastBitmapOriginal;
            } else {
                try {
                    bm = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    lastBitmapOriginal = bm; // keep reference
                } catch (IOException e) {
                    hideProcessingDialog();
                    // Re-enable button on image load failure
                    if (btnProcess != null) {
                        btnProcess.setEnabled(true);
                    }
                    Toast.makeText(this, getString(R.string.failed_to_load_image_for_ocr), Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // operate on a processing copy (downscale to manageable size)
            Bitmap proc = scaleBitmapToMaxDim(bm, 1600);
            if (proc != bm) {
                // if we created a downscaled proc and bm is lastBitmapOriginal we keep original
            }

            try { proc = trimWhitespace(proc, 230); } catch (Exception e) { Log.d(TAG, "trimWhitespace failed in processImageForText", e); }
            try { proc = enhanceBitmap(proc); } catch (Exception e) { Log.d(TAG, "enhanceBitmap failed in processImageForText", e); }

            previewImageView.setImageBitmap(proc);

            // save enhanced proc to temp file to upload later
            Uri newUri = saveBitmapToTempUri(proc);
            if (newUri != null) imageUri = newUri;

            InputImage image = InputImage.fromBitmap(proc, 0);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        String fullText = text.getText();
                        Log.d(TAG, "OCR completed, text length: " + (fullText != null ? fullText.length() : 0));

                        // Show OCR result card first
                        if (ocrResultCard != null) {
                            ocrResultCard.setVisibility(View.VISIBLE);
                            Log.d(TAG, "OCR result card made visible");
                        }

                        // Hide text view and show progress bar while processing
                        if (ocrTextView != null) {
                            ocrTextView.setVisibility(View.GONE);
                        }
                        if (ocrProcessingProgressBar != null) {
                            ocrProcessingProgressBar.setVisibility(View.VISIBLE);
                        }

                        // Call Gemini API to extract structured data
                        if (fullText != null && !fullText.trim().isEmpty() && geminiApiService != null) {
                            Log.d(TAG, "Calling Gemini API");
                            callGeminiApi(fullText);
                        } else {
                            Log.d(TAG, "Skipping Gemini API - fullText: " + (fullText != null ? "not null" : "null") + ", geminiApiService: " + (geminiApiService != null ? "not null" : "null"));
                            // Fallback to basic parser if Gemini is not available
                            hideProcessingDialog();
                            // Re-enable button
                            if (btnProcess != null) {
                                btnProcess.setEnabled(true);
                            }
                            // Hide progress and show text
                            if (ocrProcessingProgressBar != null) {
                                ocrProcessingProgressBar.setVisibility(View.GONE);
                            }
                            if (ocrTextView != null) {
                                ocrTextView.setVisibility(View.VISIBLE);
                                ocrTextView.setText(fullText != null ? fullText : "");
                            }
                            currentReceipt = ReceiptParser.parse(fullText);
                            if (btnSave != null) {
                                btnSave.setVisibility(View.VISIBLE);
                                btnSave.setEnabled(true);
                            }
                            Toast.makeText(this, getString(R.string.ocr_complete), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        hideProcessingDialog();
                        // Re-enable button on OCR failure
                        if (btnProcess != null) {
                            btnProcess.setEnabled(true);
                        }
                        Toast.makeText(this, getString(R.string.ocr_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                    });
        } finally {
            // do not hideProcessingDialog here; callbacks will hide/handle it
        }
    }

    // Trim near-white margins from a bitmap. threshold is 0-255 where higher = more aggressive trimming.
    private Bitmap trimWhitespace(Bitmap src, int brightnessThreshold) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        int[] pixels = new int[w * h];
        src.getPixels(pixels, 0, w, 0, 0, w, h);

        int top = 0, left = 0, right = w - 1, bottom = h - 1;
        boolean found = false;

        // find top
        outerTop:
        for (int y = 0; y < h; y++) {
            int rowIndex = y * w;
            for (int x = 0; x < w; x++) {
                int p = pixels[rowIndex + x];
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                if (r < brightnessThreshold || g < brightnessThreshold || b < brightnessThreshold) {
                    top = y;
                    found = true;
                    break outerTop;
                }
            }
        }
        if (!found) return src; // image is all white-ish

        // find bottom
        outerBottom:
        for (int y = h - 1; y >= 0; y--) {
            int rowIndex = y * w;
            for (int x = 0; x < w; x++) {
                int p = pixels[rowIndex + x];
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                if (r < brightnessThreshold || g < brightnessThreshold || b < brightnessThreshold) {
                    bottom = y;
                    break outerBottom;
                }
            }
        }

        // find left
        outerLeft:
        for (int x = 0; x < w; x++) {
            for (int y = top; y <= bottom; y++) {
                int p = pixels[y * w + x];
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                if (r < brightnessThreshold || g < brightnessThreshold || b < brightnessThreshold) {
                    left = x;
                    break outerLeft;
                }
            }
        }

        // find right
        outerRight:
        for (int x = w - 1; x >= 0; x--) {
            for (int y = top; y <= bottom; y++) {
                int p = pixels[y * w + x];
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                if (r < brightnessThreshold || g < brightnessThreshold || b < brightnessThreshold) {
                    right = x;
                    break outerRight;
                }
            }
        }

        // clamp bounds
        int cropW = Math.max(1, right - left + 1);
        int cropH = Math.max(1, bottom - top + 1);
        try {
            Bitmap out = Bitmap.createBitmap(src, left, top, cropW, cropH);
            return out;
        } catch (Exception e) {
            return src;
        }
    }

    private void saveReceipt() {
        if (imageUri == null) {
            Toast.makeText(this, getString(R.string.no_image_to_save), Toast.LENGTH_SHORT).show();
            return;
        }
        // ensure OCR text present
        String ocrText = ocrTextView.getText() != null ? ocrTextView.getText().toString() : null;
        if ((ocrText == null || ocrText.trim().isEmpty()) && currentReceipt == null) {
            Toast.makeText(this, getString(R.string.please_run_ocr_before_saving), Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentReceipt == null) {
            currentReceipt = ReceiptParser.parse(ocrText);
            // Update metadata with OCR text if metadata exists
            if (currentReceipt.getMetadata() != null) {
                currentReceipt.getMetadata().setOcrText(ocrText);
            }
        } else {
            // Update metadata with OCR text if metadata exists
            if (currentReceipt.getMetadata() != null) {
                currentReceipt.getMetadata().setOcrText(ocrText);
            }
        }

        // Set dateTimestamp to current upload time (for sorting)
        if (currentReceipt.getReceipt() == null) {
            currentReceipt.setReceipt(new Receipt.ReceiptInfo());
        }

        long currentUploadTime = System.currentTimeMillis();
        currentReceipt.getReceipt().setDateTimestamp(currentUploadTime);
        Log.d(TAG, "Set dateTimestamp to current upload time: " + currentUploadTime);

        // If receiptDateTimestamp is not set, use current time as fallback
        if (currentReceipt.getReceipt().getReceiptDateTimestamp() == 0) {
            currentReceipt.getReceipt().setReceiptDateTimestamp(currentUploadTime);
            Log.d(TAG, "Set receiptDateTimestamp to current time (fallback)");
        } else {
            Log.d(TAG, "Using receipt date for receiptDateTimestamp: " + currentReceipt.getReceipt().getReceiptDateTimestamp());
        }

        // Log category before saving for debugging
        if (currentReceipt.getReceipt() != null && currentReceipt.getReceipt().getCategory() != null) {
            Log.d(TAG, "Saving receipt with category: " + currentReceipt.getReceipt().getCategory());
        } else {
            Log.w(TAG, "Receipt category is null or receipt.getReceipt() is null before saving");
        }

        // call repository to save
        ReceiptRepository repo = new ReceiptRepository();
        btnSave.setEnabled(false);
        repo.saveReceipt(this, imageUri, currentReceipt, new ReceiptRepository.SaveCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ReceiptScanActivity.this, getString(R.string.receipt_saved), Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                    // free high-res bitmap to reduce memory usage after successful upload
                    try { if (lastBitmapOriginal != null && !lastBitmapOriginal.isRecycled()) { lastBitmapOriginal.recycle(); } } catch (Exception ignored) {}
                    lastBitmapOriginal = null;
                    finish();
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(ReceiptScanActivity.this, getString(R.string.save_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                    btnSave.setEnabled(true);
                });
            }
        });
    }

    // Call Gemini API to extract structured receipt data from OCR text
    private void callGeminiApi(String ocrText) {
        // Button is already disabled in processImageForText(), keep it disabled during Gemini call
        if (geminiApiService == null) {
            hideProcessingDialog();
            // Re-enable button
            if (btnProcess != null) {
                btnProcess.setEnabled(true);
            }
            currentReceipt = ReceiptParser.parse(ocrText);
            if (btnSave != null) {
                btnSave.setVisibility(View.VISIBLE);
                btnSave.setEnabled(true);
            }
            Toast.makeText(this, getString(R.string.ocr_complete_gemini_not_configured), Toast.LENGTH_SHORT).show();
            return;
        }

        startGeminiStepsAnimation();

        geminiApiService.extractReceiptData(ocrText, new GeminiApiService.GeminiCallback() {
            @Override
            public void onSuccess(JSONObject structuredData) {
                Log.d(TAG, "Gemini API success, received structured data");
                runOnUiThread(() -> {
                    hideProcessingDialog();
                    // Re-enable button after successful Gemini call
                    if (btnProcess != null) {
                        btnProcess.setEnabled(true);
                    }
                    try {
                        // Display formatted JSON in the OCR text view
                        String formattedJson = formatJsonForDisplay(structuredData);
                        Log.d(TAG, "Formatted JSON length: " + formattedJson.length());

                        // Hide progress bar and show text view with JSON
                        if (ocrProcessingProgressBar != null) {
                            ocrProcessingProgressBar.setVisibility(View.GONE);
                        }
                        if (ocrTextView != null) {
                            ocrTextView.setVisibility(View.VISIBLE);
                            ocrTextView.setText(formattedJson);
                            Log.d(TAG, "JSON set in TextView");
                        } else {
                            Log.e(TAG, "ocrTextView is null!");
                        }

                        // Ensure OCR card is visible
                        if (ocrResultCard != null) {
                            ocrResultCard.setVisibility(View.VISIBLE);
                        }

                        currentReceipt = mapGeminiResponseToReceipt(structuredData, ocrText);
                        if (btnSave != null) {
                            btnSave.setVisibility(View.VISIBLE);
                            btnSave.setEnabled(true);
                        }
                        stopGeminiStepsAnimation();
                        Toast.makeText(ReceiptScanActivity.this, getString(R.string.receipt_data_extracted_successfully), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to map Gemini response to Receipt", e);
                        e.printStackTrace();
                        // Re-enable button on parsing error
                        if (btnProcess != null) {
                            btnProcess.setEnabled(true);
                        }
                        // Hide progress bar and show error in TextView
                        if (ocrProcessingProgressBar != null) {
                            ocrProcessingProgressBar.setVisibility(View.GONE);
                        }
                        if (ocrTextView != null) {
                            ocrTextView.setVisibility(View.VISIBLE);
                            ocrTextView.setText(getString(R.string.error_parsing_response, e.getMessage(), ocrText));
                        }
                        // Fallback to basic parser
                        currentReceipt = ReceiptParser.parse(ocrText);
                        if (btnSave != null) {
                            btnSave.setVisibility(View.VISIBLE);
                            btnSave.setEnabled(true);
                        }
                        stopGeminiStepsAnimation();
                        Toast.makeText(ReceiptScanActivity.this, getString(R.string.ocr_complete_parsing_fallback), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Gemini API call failed", e);
                e.printStackTrace();
                runOnUiThread(() -> {
                    hideProcessingDialog();
                    // Re-enable button after Gemini call failure
                    if (btnProcess != null) {
                        btnProcess.setEnabled(true);
                    }
                    // Hide progress bar and show error message in TextView
                    if (ocrProcessingProgressBar != null) {
                        ocrProcessingProgressBar.setVisibility(View.GONE);
                    }
                    if (ocrTextView != null) {
                        ocrTextView.setVisibility(View.VISIBLE);
                        ocrTextView.setText(getString(R.string.gemini_api_error, e.getMessage(), ocrText));
                    }
                    // Ensure OCR card is visible
                    if (ocrResultCard != null) {
                        ocrResultCard.setVisibility(View.VISIBLE);
                    }
                    // Fallback to basic parser
                    currentReceipt = ReceiptParser.parse(ocrText);
                    if (btnSave != null) {
                        btnSave.setVisibility(View.VISIBLE);
                        btnSave.setEnabled(true);
                    }
                    stopGeminiStepsAnimation();
                    Toast.makeText(ReceiptScanActivity.this, getString(R.string.gemini_unavailable, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void startGeminiStepsAnimation() {
        if (geminiStepsLayout == null) return;
        geminiStepIndex = 0;
        geminiStepsLayout.setVisibility(View.VISIBLE);
        updateGeminiStepsUI();

        geminiStepsHandler.removeCallbacksAndMessages(null);
        geminiStepsHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                geminiStepIndex = (geminiStepIndex + 1) % 3;
                updateGeminiStepsUI();
                geminiStepsHandler.postDelayed(this, 1500);
            }
        }, 1500);
    }

    private void stopGeminiStepsAnimation() {
        geminiStepsHandler.removeCallbacksAndMessages(null);
        if (geminiStepsLayout != null) {
            geminiStepsLayout.setVisibility(View.GONE);
        }
    }

    private void updateGeminiStepsUI() {
        if (tvGeminiStep1 == null || tvGeminiStep2 == null || tvGeminiStep3 == null) return;

        tvGeminiStep1.setVisibility(geminiStepIndex == 0 ? View.VISIBLE : View.GONE);
        tvGeminiStep2.setVisibility(geminiStepIndex == 1 ? View.VISIBLE : View.GONE);
        tvGeminiStep3.setVisibility(geminiStepIndex == 2 ? View.VISIBLE : View.GONE);
    }

    // Format JSON for display in TextView with proper indentation
    private String formatJsonForDisplay(JSONObject json) {
        try {
            // toString(2) provides nicely formatted JSON with 2-space indentation
            return json.toString(2);
        } catch (Exception e) {
            Log.e(TAG, "Failed to format JSON for display", e);
            try {
                return json.toString();
            } catch (Exception ex) {
                return "Error formatting JSON";
            }
        }
    }

    // Map Gemini's structured JSON response to Receipt object
    private Receipt mapGeminiResponseToReceipt(JSONObject structuredData, String ocrText) throws Exception {
        Receipt receipt = new Receipt();

        // Extract store information
        if (structuredData.has("store")) {
            JSONObject storeObj = structuredData.getJSONObject("store");
            Receipt.StoreInfo store = new Receipt.StoreInfo();
            if (storeObj.has("name")) store.setName(storeObj.getString("name"));
            if (storeObj.has("address")) store.setAddress(storeObj.getString("address"));
            if (storeObj.has("phone")) store.setPhone(storeObj.getString("phone"));
            if (storeObj.has("website")) store.setWebsite(storeObj.getString("website"));
            receipt.setStore(store);
        }

        // Extract receipt information
        Receipt.ReceiptInfo receiptInfo = new Receipt.ReceiptInfo();
        if (structuredData.has("receipt")) {
            JSONObject receiptObj = structuredData.getJSONObject("receipt");

            if (receiptObj.has("receiptId")) receiptInfo.setReceiptId(receiptObj.getString("receiptId"));
            if (receiptObj.has("date")) receiptInfo.setDate(receiptObj.getString("date"));
            if (receiptObj.has("time")) receiptInfo.setTime(receiptObj.getString("time"));
            if (receiptObj.has("currency")) receiptInfo.setCurrency(receiptObj.getString("currency"));
            if (receiptObj.has("paymentMethod")) receiptInfo.setPaymentMethod(receiptObj.getString("paymentMethod"));
            if (receiptObj.has("cardLast4")) receiptInfo.setCardLast4(receiptObj.getString("cardLast4"));
            if (receiptObj.has("subtotal")) receiptInfo.setSubtotal(receiptObj.getDouble("subtotal"));
            if (receiptObj.has("tax")) receiptInfo.setTax(receiptObj.getDouble("tax"));
            if (receiptObj.has("total")) receiptInfo.setTotal(receiptObj.getDouble("total"));
            if (receiptObj.has("category")) {
                String category = receiptObj.getString("category");
                receiptInfo.setCategory(category);
                Log.d(TAG, "Extracted category from Gemini response: " + category);
            } else {
                Log.w(TAG, "Category field not found in Gemini receipt object");
            }

            // Parse date string to receiptDateTimestamp (actual receipt date for notifications)
            if (receiptInfo.getDate() != null && !receiptInfo.getDate().isEmpty()) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    Date date = sdf.parse(receiptInfo.getDate());
                    if (date != null) {
                        receiptInfo.setReceiptDateTimestamp(date.getTime());
                        Log.d(TAG, "Set receiptDateTimestamp from receipt date: " + receiptInfo.getDate() + " = " + date.getTime());
                    } else {
                        receiptInfo.setReceiptDateTimestamp(System.currentTimeMillis());
                    }
                } catch (ParseException e) {
                    Log.w(TAG, "Failed to parse date from Gemini response", e);
                    receiptInfo.setReceiptDateTimestamp(System.currentTimeMillis());
                }
            } else {
                receiptInfo.setReceiptDateTimestamp(System.currentTimeMillis());
            }
        } else {
            receiptInfo.setReceiptDateTimestamp(System.currentTimeMillis());
        }

        // Extract items
        String primaryCategoryFromItems = null;
        if (structuredData.has("items")) {
            JSONArray itemsArray = structuredData.getJSONArray("items");
            List<ReceiptItem> items = new ArrayList<>();

            for (int i = 0; i < itemsArray.length(); i++) {
                JSONObject itemObj = itemsArray.getJSONObject(i);
                ReceiptItem item = new ReceiptItem();

                if (itemObj.has("name")) item.setName(itemObj.getString("name"));
                if (itemObj.has("quantity")) item.setQuantity(itemObj.getInt("quantity"));
                if (itemObj.has("unitPrice")) item.setUnitPrice(itemObj.getDouble("unitPrice"));
                if (itemObj.has("totalPrice")) item.setTotalPrice(itemObj.getDouble("totalPrice"));
                if (itemObj.has("category")) {
                    String itemCategory = itemObj.getString("category");
                    item.setCategory(itemCategory);
                    // Use first non-empty category from items as fallback
                    if (primaryCategoryFromItems == null && itemCategory != null && !itemCategory.isEmpty()) {
                        primaryCategoryFromItems = itemCategory;
                    }
                }

                items.add(item);
            }
            receipt.setItems(items);
        }

        // If receipt-level category is not set, use primary category from items as fallback
        if (receiptInfo.getCategory() == null || receiptInfo.getCategory().isEmpty()) {
            if (primaryCategoryFromItems != null && !primaryCategoryFromItems.isEmpty()) {
                receiptInfo.setCategory(primaryCategoryFromItems);
                Log.d(TAG, "Set category from items: " + primaryCategoryFromItems);
            }
        }

        // Log category for debugging
        if (receiptInfo.getCategory() != null) {
            Log.d(TAG, "Final receipt category: " + receiptInfo.getCategory());
        } else {
            Log.w(TAG, "Receipt category is null after mapping");
        }

        receipt.setReceipt(receiptInfo);

        // Extract additional information
        if (structuredData.has("additional")) {
            JSONObject additionalObj = structuredData.getJSONObject("additional");
            Receipt.AdditionalInfo additional = new Receipt.AdditionalInfo();
            if (additionalObj.has("taxNumber")) additional.setTaxNumber(additionalObj.getString("taxNumber"));
            if (additionalObj.has("cashier")) additional.setCashier(additionalObj.getString("cashier"));
            if (additionalObj.has("storeNumber")) additional.setStoreNumber(additionalObj.getString("storeNumber"));
            if (additionalObj.has("notes")) additional.setNotes(additionalObj.getString("notes"));
            receipt.setAdditional(additional);
        }

        // Extract metadata
        Receipt.ReceiptMetadata metadata = new Receipt.ReceiptMetadata();
        if (structuredData.has("metadata")) {
            JSONObject metadataObj = structuredData.getJSONObject("metadata");
            if (metadataObj.has("ocrText")) metadata.setOcrText(metadataObj.getString("ocrText"));
            if (metadataObj.has("processedBy")) metadata.setProcessedBy(metadataObj.getString("processedBy"));
            if (metadataObj.has("uploadedAt")) metadata.setUploadedAt(metadataObj.getString("uploadedAt"));
            if (metadataObj.has("userId")) metadata.setUserId(metadataObj.getString("userId"));
        }
        // Always set/override with current values
        metadata.setOcrText(ocrText);
        metadata.setProcessedBy("gemini");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        metadata.setUploadedAt(sdf.format(new Date()));
        // Set userId from Firebase Auth
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() != null) {
                metadata.setUserId(auth.getCurrentUser().getUid());
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get user ID for metadata", e);
        }
        receipt.setMetadata(metadata);

        return receipt;
    }

    // Helper to load an image Uri into the preview ImageView using Glide.
    private void loadImageIntoPreview(Uri uri) {
        if (uri == null || previewImageView == null) return;
        try {
            // Hide empty state and show preview
            if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.GONE);
            if (previewImageView != null) previewImageView.setVisibility(View.VISIBLE);

            Glide.with(this)
                    .load(uri)
                    .apply(RequestOptions.centerInsideTransform())
                    .into(previewImageView);
            // ensure overlay is hidden when showing a new preview
            previewImageView.post(() -> {
                try {
                    cornerOverlay.hide();
                } catch (Exception ex) {
                    Log.d(TAG, "cornerOverlay hide failed", ex);
                }
                try {
                    btnEditCorners.setVisibility(View.VISIBLE);
                    btnEditCorners.setEnabled(true);
                    btnProcess.setVisibility(View.VISIBLE);
                } catch (Exception ex) { /* ignore */ }
            });
        } catch (Exception e) {
            Log.w(TAG, "failed to load preview via Glide", e);
            try {
                if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.GONE);
                if (previewImageView != null) {
                    previewImageView.setVisibility(View.VISIBLE);
                    previewImageView.setImageURI(uri);
                }
            } catch (Exception ex) { Log.d(TAG, "setImageURI fallback failed", ex); }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Handle camera and storage permissions (request code 1234)
        // Notification permission is now handled in MainActivity
    }
}