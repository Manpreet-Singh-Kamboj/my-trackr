package com.mytrackr.receipts.data.model;

public class DetailItem {
    private String itemName;
    private String storeName;
    private String date;
    private double amount;
    private long timestamp;

    public DetailItem(String itemName, String storeName, String date, double amount, long timestamp) {
        this.itemName = itemName;
        this.storeName = storeName;
        this.date = date;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

