package com.mytrackr.receipts.utils;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiApiService {
    private static final String TAG = "GeminiApiService";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient client;
    private final String apiKey;

    public interface GeminiCallback {
        void onSuccess(JSONObject structuredData);
        void onFailure(Exception e);
    }

    public GeminiApiService(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void extractReceiptData(String ocrText, GeminiCallback callback) {
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onFailure(new IllegalArgumentException("Gemini API key is not configured"));
            return;
        }

        String prompt = buildPrompt(ocrText);
        JSONObject requestBody = buildRequestBody(prompt);

        RequestBody body = RequestBody.create(requestBody.toString(), JSON);
        Request request = new Request.Builder()
                .url(GEMINI_API_URL + apiKey)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Gemini API request failed", e);
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    Log.e(TAG, "Gemini API error: " + response.code() + " - " + errorBody);
                    callback.onFailure(new IOException("API error: " + response.code() + " - " + errorBody));
                    return;
                }

                String responseBody = null;
                try {
                    responseBody = response.body().string();
                    Log.d(TAG, "Gemini API response received, length: " + responseBody.length());
                    Log.d(TAG, "Response body preview: " + responseBody.substring(0, Math.min(500, responseBody.length())));
                    
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    
                    // Extract the structured JSON from Gemini's response
                    JSONObject structuredData = extractStructuredData(jsonResponse);
                    Log.d(TAG, "Structured data extracted successfully, keys: " + structuredData.keys().toString());
                    callback.onSuccess(structuredData);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse Gemini response", e);
                    e.printStackTrace();
                    // Log the full response for debugging
                    if (responseBody != null) {
                        Log.e(TAG, "Full response body: " + responseBody);
                    } else {
                        Log.e(TAG, "Could not read response body");
                    }
                    callback.onFailure(e);
                }
            }
        });
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
                "    \"category\": \"Category name - assign most appropriate category based on receipt data (e.g., Groceries, Meal & Entertainment, Travel, Shopping, Gas, Others) or empty string\"\n" +
                "  },\n" +
                "  \"items\": [\n" +
                "    {\n" +
                "      \"name\": \"Item name\",\n" +
                "      \"quantity\": 1,\n" +
                "      \"unitPrice\": 0.0,\n" +
                "      \"totalPrice\": 0.0,\n" +
                "      \"category\": \"Category name or empty string\"\n" +
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

    private JSONObject buildRequestBody(String prompt) {
        try {
            JSONObject requestBody = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", prompt);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            requestBody.put("contents", contents);
            
            // Add generation config for JSON response
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.1);
            generationConfig.put("responseMimeType", "application/json");
            requestBody.put("generationConfig", generationConfig);
            
            return requestBody;
        } catch (Exception e) {
            Log.e(TAG, "Failed to build request body", e);
            return new JSONObject();
        }
    }

    private JSONObject extractStructuredData(JSONObject response) throws Exception {
        Log.d(TAG, "Extracting structured data from response");
        // Gemini returns: { "candidates": [ { "content": { "parts": [ { "text": "..." } ] } } ] }
        if (!response.has("candidates")) {
            Log.e(TAG, "Response missing 'candidates' key. Response: " + response.toString());
            throw new Exception("Response missing 'candidates' key");
        }
        
        JSONArray candidates = response.getJSONArray("candidates");
        if (candidates.length() == 0) {
            Log.e(TAG, "No candidates in response");
            throw new Exception("No candidates in response");
        }
        
        JSONObject candidate = candidates.getJSONObject(0);
        if (!candidate.has("content")) {
            Log.e(TAG, "Candidate missing 'content' key");
            throw new Exception("Candidate missing 'content' key");
        }
        
        JSONObject content = candidate.getJSONObject("content");
        if (!content.has("parts")) {
            Log.e(TAG, "Content missing 'parts' key");
            throw new Exception("Content missing 'parts' key");
        }
        
        JSONArray parts = content.getJSONArray("parts");
        if (parts.length() == 0) {
            Log.e(TAG, "No parts in response");
            throw new Exception("No parts in response");
        }
        
        JSONObject part = parts.getJSONObject(0);
        if (!part.has("text")) {
            Log.e(TAG, "Part missing 'text' key");
            throw new Exception("Part missing 'text' key");
        }
        
        String text = part.getString("text");
        Log.d(TAG, "Extracted text from response, length: " + text.length());
        Log.d(TAG, "Text preview: " + text.substring(0, Math.min(200, text.length())));
        
        // Parse the JSON text response
        // Remove markdown code blocks if present
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
        
        Log.d(TAG, "Parsing JSON text");
        JSONObject result = new JSONObject(text);
        Log.d(TAG, "Successfully parsed JSON object");
        return result;
    }
}

