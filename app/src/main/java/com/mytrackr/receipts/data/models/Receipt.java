package com.mytrackr.receipts.data.models;

import java.util.List;
import java.util.Map;

public class Receipt {
    private String id;
    private String storeName;
    private long date; // epoch millis
    private double total;
    private List<Map<String, Object>> items; // simple list of item maps: {name,price}
    private String imageUrl;
    private String rawText;
    // Cloudinary public id returned by Cloudinary after upload (optional)
    private String cloudinaryPublicId;

    public Receipt() {}

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }
    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }
    public List<Map<String, Object>> getItems() { return items; }
    public void setItems(List<Map<String, Object>> items) { this.items = items; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    // Cloudinary public_id accessors
    public String getCloudinaryPublicId() { return cloudinaryPublicId; }
    public void setCloudinaryPublicId(String cloudinaryPublicId) { this.cloudinaryPublicId = cloudinaryPublicId; }
}
