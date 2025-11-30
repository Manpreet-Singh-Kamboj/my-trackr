package com.mytrackr.receipts.data.model;

import com.mytrackr.receipts.data.models.Receipt;

/**
 * Unified model to represent both receipts and manual transactions in expenses
 */
public class ExpenseItem {
    public enum ExpenseType {
        RECEIPT,
        MANUAL_TRANSACTION
    }
    
    private final ExpenseType expenseType;
    private final String id;
    private final String description;
    private final double amount;
    private final long timestamp;
    private final String category;
    private final String transactionType;
    
    public ExpenseItem(Receipt receipt) {
        this.expenseType = ExpenseType.RECEIPT;
        this.id = receipt.getId();
        this.amount = receipt.getReceipt() != null ? receipt.getReceipt().getTotal() : 0.0;
        this.timestamp = receipt.getReceipt() != null && receipt.getReceipt().getReceiptDateTimestamp() > 0
            ? receipt.getReceipt().getReceiptDateTimestamp()
            : (receipt.getReceipt() != null ? receipt.getReceipt().getDateTimestamp() : System.currentTimeMillis());
        this.description = receipt.getStore() != null && receipt.getStore().getName() != null && !receipt.getStore().getName().isEmpty()
            ? receipt.getStore().getName()
            : "Unknown Store";
        this.category = receipt.getReceipt() != null ? receipt.getReceipt().getCategory() : null;
        this.transactionType = "expense";
    }
    
    public ExpenseItem(Transaction transaction) {
        this.expenseType = ExpenseType.MANUAL_TRANSACTION;
        this.id = transaction.getId();
        this.amount = transaction.getAmount();
        this.timestamp = transaction.getTimestamp();
        this.description = transaction.getDescription();
        this.transactionType = transaction.getType();
        this.category = null;
    }
    
    public ExpenseType getExpenseType() {
        return expenseType;
    }
    
    public String getId() {
        return id;
    }
    
    public String getDescription() {
        return description;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getCategory() {
        return category;
    }
    
    public String getTransactionType() {
        return transactionType;
    }
    
    public boolean isExpense() {
        return "expense".equals(transactionType);
    }
    
    public boolean isReceipt() {
        return expenseType == ExpenseType.RECEIPT;
    }
}

