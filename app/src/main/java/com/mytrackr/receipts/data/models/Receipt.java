package com.mytrackr.receipts.data.models;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class Receipt implements Serializable {
    private String id;
    private String imageUrl;
    // Cloudinary public id returned by Cloudinary after upload (optional)
    private String cloudinaryPublicId;
    
    // Store information
    private StoreInfo store;
    
    // Receipt information
    private ReceiptInfo receipt;
    
    // Items list
    private List<ReceiptItem> items;
    
    // Additional information
    private AdditionalInfo additional;
    
    // Metadata
    private ReceiptMetadata metadata;

    public Receipt() {}

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public String getCloudinaryPublicId() { return cloudinaryPublicId; }
    public void setCloudinaryPublicId(String cloudinaryPublicId) { this.cloudinaryPublicId = cloudinaryPublicId; }
    
    public StoreInfo getStore() { return store; }
    public void setStore(StoreInfo store) { this.store = store; }
    
    public ReceiptInfo getReceipt() { return receipt; }
    public void setReceipt(ReceiptInfo receipt) { this.receipt = receipt; }
    
    public List<ReceiptItem> getItems() { return items; }
    public void setItems(List<ReceiptItem> items) { this.items = items; }
    
    public AdditionalInfo getAdditional() { return additional; }
    public void setAdditional(AdditionalInfo additional) { this.additional = additional; }
    
    public ReceiptMetadata getMetadata() { return metadata; }
    public void setMetadata(ReceiptMetadata metadata) { this.metadata = metadata; }

    // Nested classes for structured data
    public static class StoreInfo implements Serializable {
        private String name;
        private String address;
        private String phone;
        private String website;

        public StoreInfo() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        
        public String getWebsite() { return website; }
        public void setWebsite(String website) { this.website = website; }
    }

    public static class ReceiptInfo implements Serializable {
        private String receiptId;
        private String date; // YYYY-MM-DD format
        private String time; // HH:MM format
        private String currency;
        private String paymentMethod;
        private String cardLast4;
        private double subtotal;
        private double tax;
        private double total;
        private long dateTimestamp; // epoch millis for sorting (upload time)
        private long receiptDateTimestamp; // epoch millis for actual receipt date (for notifications)
        private String category;

        public ReceiptInfo() {}

        public String getReceiptId() { return receiptId; }
        public void setReceiptId(String receiptId) { this.receiptId = receiptId; }
        
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        
        public String getCardLast4() { return cardLast4; }
        public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }
        
        public double getSubtotal() { return subtotal; }
        public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
        
        public double getTax() { return tax; }
        public void setTax(double tax) { this.tax = tax; }
        
        public double getTotal() { return total; }
        public void setTotal(double total) { this.total = total; }
        
        public long getDateTimestamp() { return dateTimestamp; }
        public void setDateTimestamp(long dateTimestamp) { this.dateTimestamp = dateTimestamp; }
        
        public long getReceiptDateTimestamp() { return receiptDateTimestamp; }
        public void setReceiptDateTimestamp(long receiptDateTimestamp) { this.receiptDateTimestamp = receiptDateTimestamp; }
        
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }

    public static class AdditionalInfo implements Serializable {
        private String taxNumber;
        private String cashier;
        private String storeNumber;
        private String notes;

        public AdditionalInfo() {}

        public String getTaxNumber() { return taxNumber; }
        public void setTaxNumber(String taxNumber) { this.taxNumber = taxNumber; }
        
        public String getCashier() { return cashier; }
        public void setCashier(String cashier) { this.cashier = cashier; }
        
        public String getStoreNumber() { return storeNumber; }
        public void setStoreNumber(String storeNumber) { this.storeNumber = storeNumber; }
        
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class ReceiptMetadata implements Serializable {
        private String ocrText;
        private String processedBy;
        private String uploadedAt;
        private String userId;

        public ReceiptMetadata() {}

        public String getOcrText() { return ocrText; }
        public void setOcrText(String ocrText) { this.ocrText = ocrText; }
        
        public String getProcessedBy() { return processedBy; }
        public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }
        
        public String getUploadedAt() { return uploadedAt; }
        public void setUploadedAt(String uploadedAt) { this.uploadedAt = uploadedAt; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }
    
    // Helper methods for backward compatibility
    @Deprecated
    public String getStoreName() {
        return store != null ? store.getName() : null;
    }
    
    @Deprecated
    public void setStoreName(String storeName) {
        if (store == null) store = new StoreInfo();
        store.setName(storeName);
    }
    
    @Deprecated
    public long getDate() {
        return receipt != null ? receipt.getDateTimestamp() : 0;
    }
    
    @Deprecated
    public void setDate(long date) {
        if (receipt == null) receipt = new ReceiptInfo();
        receipt.setDateTimestamp(date);
    }
    
    @Deprecated
    public double getTotal() {
        return receipt != null ? receipt.getTotal() : 0.0;
    }
    
    @Deprecated
    public void setTotal(double total) {
        if (receipt == null) receipt = new ReceiptInfo();
        receipt.setTotal(total);
    }
}
