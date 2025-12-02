package com.mytrackr.receipts.utils;

import android.util.Log;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeminiApiService {
    private static final String TAG = "GeminiApiService";
    private final String apiKey;
    private final Executor executor;

    public interface GeminiCallback {
        void onSuccess(JSONObject structuredData);
        void onFailure(Exception e);
    }

    public GeminiApiService(String apiKey) {
        this.apiKey = apiKey;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void extractReceiptData(String ocrText, GeminiCallback callback) {
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onFailure(new IllegalArgumentException("Gemini API key is not configured"));
            return;
        }

        String cleanedOcr = sanitizeOcr(ocrText);
        String prompt = buildPrompt(cleanedOcr);

        executor.execute(() -> callGemini(prompt, callback, 0));
    }

    private void callGemini(String prompt, GeminiCallback callback, int attempt) {
        try {
            GenerativeModel baseModel = new GenerativeModel("gemini-2.5-flash", apiKey);
            GenerativeModelFutures model = GenerativeModelFutures.from(baseModel);

            Content content = new Content.Builder()
                    .addText(prompt)
                    .build();

            ListenableFuture<GenerateContentResponse> future = model.generateContent(content);

            Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    try {
                        String text = result.getText();

                        if (text == null || text.trim().isEmpty()) {
                            throw new IllegalStateException("Empty response from Gemini SDK");
                        }

                        text = text.trim();
                        if (text.startsWith("```json")) {
                            text = text.substring(7);
                        }
                        if (text.startsWith("```")) {
                            text = text.substring(3);
                        }
                        if (text.endsWith("```")) {
                            text = text.substring(0, text.length() - 3);
                        }
                        text = text.trim();

                        JSONObject structuredData = new JSONObject(text);
                        Log.d(TAG, "Structured data extracted successfully from Gemini SDK");
                        callback.onSuccess(structuredData);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse Gemini SDK response", e);
                        callback.onFailure(e);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    Log.e(TAG, "Gemini SDK request failed", t);

                    boolean isTimeout = isTimeoutException(t);
                    if (isTimeout && attempt < 1) {
                        Log.w(TAG, "Timeout from Gemini, retrying once (attempt " + (attempt + 1) + ")");
                        callGemini(prompt, callback, attempt + 1);
                    } else {
                        callback.onFailure(new Exception(t));
                    }
                }
            }, executor);
        } catch (Exception e) {
            Log.e(TAG, "Gemini SDK setup failed", e);
            callback.onFailure(e);
        }
    }

    private boolean isTimeoutException(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String sanitizeOcr(String ocrText) {
        if (ocrText == null) return "";
        String cleaned = ocrText
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n");
        int maxLen = 2000;
        return cleaned.length() > maxLen ? cleaned.substring(0, maxLen) : cleaned;
    }

    private String buildPrompt(String ocrText) {
        return "Extract structured receipt data from the following OCR text. " +
                "Return ONLY a valid JSON object in this exact format (no markdown, no code blocks, just raw JSON):\n\n" +
                "{\n" +
                "  \"store\": {\n" +
                "    \"name\": \"Store name or empty string\",\n" +
                "    \"address\": \"Store address or empty string\",\n" +
                "    \"phone\": \"Phone number or empty string\",\n" +
                "    \"website\": \"Website or empty string\"\n" +
                "  },\n" +
                "  \"receipt\": {\n" +
                "    \"receiptId\": \"Receipt ID if found or empty string\",\n" +
                "    \"date\": \"YYYY-MM-DD format or empty string\",\n" +
                "    \"time\": \"HH:MM format or empty string\",\n" +
                "    \"currency\": \"Currency code like CAD, USD, etc. or empty string\",\n" +
                "    \"paymentMethod\": \"Payment method or empty string\",\n" +
                "    \"cardLast4\": \"Last 4 digits of card or empty string\",\n" +
                "    \"subtotal\": 0.0,\n" +
                "    \"tax\": 0.0,\n" +
                "    \"total\": 0.0,\n" +
                "    \"category\": \"assign these categories based on receipt data (Groceries, Meal, Entertainment, Travel, Shopping, Other)\"\n" +
                "  },\n" +
                "  \"items\": [\n" +
                "    {\n" +
                "      \"name\": \"Item name\",\n" +
                "      \"quantity\": 1,\n" +
                "      \"unitPrice\": 0.0,\n" +
                "      \"totalPrice\": 0.0,\n" +
                "      \"category\": \"assign these categories based on receipt data (Groceries, Meal, Entertainment, Travel, Shopping, Other)\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"additional\": {\n" +
                "    \"taxNumber\": \"Tax number or empty string\",\n" +
                "    \"cashier\": \"Cashier name or empty string\",\n" +
                "    \"storeNumber\": \"Store number or empty string\",\n" +
                "    \"notes\": \"Any additional notes or empty string\"\n" +
                "  },\n" +
                "  \"metadata\": {\n" +
                "    \"ocrText\": \"Original OCR text here\",\n" +
                "    \"processedBy\": \"gemini\",\n" +
                "    \"uploadedAt\": \"ISO 8601 timestamp\",\n" +
                "    \"userId\": \"\"\n" +
                "  }\n" +
                "}\n\n" +
                "OCR Text:\n" + ocrText;
    }

}
