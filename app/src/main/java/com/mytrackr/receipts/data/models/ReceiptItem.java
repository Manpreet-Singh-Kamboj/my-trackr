package com.mytrackr.receipts.data.models;

import java.io.Serializable;

public class ReceiptItem implements Serializable {
    private String name;
    private Integer quantity;
    private Double unitPrice;
    private Double totalPrice;
    private String category;

    public ReceiptItem() {}

    public ReceiptItem(String name, Integer quantity, Double unitPrice, Double totalPrice, String category) {
        this.name = name;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.category = category;
    }

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(Double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * Calculate total price if not set, using unitPrice * quantity
     */
    public Double calculateTotalPrice() {
        if (totalPrice != null && totalPrice > 0) {
            return totalPrice;
        }
        if (unitPrice != null && quantity != null && quantity > 0) {
            return unitPrice * quantity;
        }
        return 0.0;
    }

    /**
     * Get the effective total price (either totalPrice or calculated)
     */
    public Double getEffectiveTotalPrice() {
        return calculateTotalPrice();
    }
}

