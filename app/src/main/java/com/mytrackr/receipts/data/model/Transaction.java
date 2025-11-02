package com.mytrackr.receipts.data.model;

public class Transaction {
    private String id;
    private String description;
    private double amount;
    private String type; // "expense" or "income"
    private long timestamp;
    private String month;
    private String year;

    // Empty constructor for Firestore
    public Transaction() {
    }

    public Transaction(String id, String description, double amount, String type, long timestamp, String month, String year) {
        this.id = id;
        this.description = description;
        this.amount = amount;
        this.type = type;
        this.timestamp = timestamp;
        this.month = month;
        this.year = year;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public boolean isExpense() {
        return "expense".equals(type);
    }

    public boolean isIncome() {
        return "income".equals(type);
    }
}
