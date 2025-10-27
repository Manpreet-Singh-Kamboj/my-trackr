package com.mytrackr.receipts.features.receipts;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Scans a receipt image using ML Kit on-device Text Recognition, extracts fields with heuristics
 * and then saves the receipt using the existing ReceiptRepository.
 *
 * Usage:
 *   new ReceiptScanner().scanAndSave(context, imageUri, new ReceiptRepository.SaveCallback() { ... });
 */
public class ReceiptScanner {
    private static final String TAG = "ReceiptScanner";
    private final ReceiptRepository repository = new ReceiptRepository();
    private final Executor bgExecutor = Executors.newSingleThreadExecutor();

    public interface ScanCallback {
        void onScanned(Receipt receipt);
        void onError(Exception e);
    }

    /**
     * Runs OCR on the provided image URI, fills a Receipt model using heuristics, and saves it.
     * If you only want OCR results without saving, pass a null saveCallback and use scanCallback to get the Receipt.
     */
    public void scanAndSave(Context context, Uri imageUri, ReceiptRepository.SaveCallback saveCallback, ScanCallback scanCallback) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);

            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener(new OnSuccessListener<Text>() {
                        @Override
                        public void onSuccess(Text text) {
                            try {
                                String rawText = text.getText();
                                Log.d(TAG, "OCR raw text length=" + (rawText != null ? rawText.length() : 0));

                                // Use the existing parser to build a Receipt from OCR text
                                Receipt parsed = ReceiptParser.parse(rawText);
                                if (parsed == null) parsed = new Receipt();

                                if (scanCallback != null) scanCallback.onScanned(parsed);

                                if (saveCallback != null) {
                                    // save image and metadata via existing repository
                                    repository.saveReceipt(context, imageUri, parsed, saveCallback);
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Error processing OCR result", e);
                                if (scanCallback != null) scanCallback.onError(e);
                                if (saveCallback != null) saveCallback.onFailure(e);
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            Log.w(TAG, "Text recognition failed", e);
                            if (scanCallback != null) scanCallback.onError(e);
                            if (saveCallback != null) saveCallback.onFailure(e);
                        }
                    });

        } catch (Exception e) {
            Log.w(TAG, "Failed to create InputImage from URI", e);
            if (scanCallback != null) scanCallback.onError(e);
            if (saveCallback != null) saveCallback.onFailure(e);
        }
    }
}
