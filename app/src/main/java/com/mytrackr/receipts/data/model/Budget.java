package com.mytrackr.receipts.data.model;

public class Budget {
    private double amount;
    private String month;
    private String year;
    private double spent;

    public Budget() {
        // Required empty constructor for Firestore
    }

    public Budget(double amount, String month, String year) {
        this.amount = amount;
        this.month = month;
        this.year = year;
        this.spent = 0.0;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
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

    public double getSpent() {
        return spent;
    }

    public void setSpent(double spent) {
        this.spent = spent;
    }

    public double getRemaining() {
        return amount - spent;
    }

    public double getSpentPercentage() {
        if (amount == 0) return 0;
        return (spent / amount) * 100;
    }
}
