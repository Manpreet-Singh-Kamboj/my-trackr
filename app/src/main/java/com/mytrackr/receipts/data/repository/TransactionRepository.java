package com.mytrackr.receipts.data.repository;

import android.util.Log;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.mytrackr.receipts.data.model.Transaction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionRepository {
    private static TransactionRepository instance;
    private final FirebaseFirestore firestore;
    private final FirebaseAuth firebaseAuth;

    private TransactionRepository() {
        firestore = FirebaseFirestore.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();
    }

    public static synchronized TransactionRepository getInstance() {
        if (instance == null) {
            instance = new TransactionRepository();
            Log.i("TRANSACTION_REPO_INIT", "Transaction Repository is Initialized");
        }
        return instance;
    }

    public void addTransaction(Transaction transaction, MutableLiveData<Boolean> successLiveData, MutableLiveData<String> errorMessage) {
        String uid = getCurrentUserId();
        if (uid == null) {
            errorMessage.postValue("User not authenticated");
            return;
        }

        Map<String, Object> transactionData = new HashMap<>();
        transactionData.put("description", transaction.getDescription());
        transactionData.put("amount", transaction.getAmount());
        transactionData.put("type", transaction.getType());
        transactionData.put("timestamp", transaction.getTimestamp());
        transactionData.put("month", transaction.getMonth());
        transactionData.put("year", transaction.getYear());

        firestore
                .collection("users")
                .document(uid)
                .collection("transactions")
                .add(transactionData)
                .addOnSuccessListener(documentReference -> {
                    Log.i("TRANSACTION_ADDED", "Transaction added successfully: " + documentReference.getId());
                    successLiveData.postValue(true);
                })
                .addOnFailureListener(e -> {
                    Log.e("TRANSACTION_ADD_ERROR", "Failed to add transaction", e);
                    errorMessage.postValue(e.getMessage());
                    successLiveData.postValue(false);
                });
    }

    public void getRecentTransactions(String month, String year, int limit, MutableLiveData<List<Transaction>> transactionsLiveData, MutableLiveData<String> errorMessage) {
        String uid = getCurrentUserId();
        if (uid == null) {
            errorMessage.postValue("User not authenticated");
            return;
        }

        // Query without ordering first to avoid index requirement
        firestore
                .collection("users")
                .document(uid)
                .collection("transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100) // Get more, then filter client-side
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Log.e("TRANSACTION_FETCH_ERROR", "Failed to fetch transactions", e);
                        errorMessage.postValue(e.getMessage());
                        return;
                    }

                    if (queryDocumentSnapshots != null) {
                        List<Transaction> transactions = new ArrayList<>();
                        // Filter by month and year client-side
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Transaction transaction = doc.toObject(Transaction.class);
                            if (month.equals(transaction.getMonth()) && year.equals(transaction.getYear())) {
                                transaction.setId(doc.getId());
                                transactions.add(transaction);
                                if (transactions.size() >= limit) {
                                    break; // Stop once we have enough
                                }
                            }
                        }
                        transactionsLiveData.postValue(transactions);
                    }
                });
    }

    public void getRecentTransactionsLastMonth(int limit, MutableLiveData<List<Transaction>> transactionsLiveData, MutableLiveData<String> errorMessage) {
        String uid = getCurrentUserId();
        if (uid == null) {
            errorMessage.postValue("User not authenticated");
            return;
        }

        // Calculate timestamp for 30 days ago
        long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);

        firestore
                .collection("users")
                .document(uid)
                .collection("transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Log.e("TRANSACTION_FETCH_ERROR", "Failed to fetch transactions", e);
                        errorMessage.postValue(e.getMessage());
                        return;
                    }

                    if (queryDocumentSnapshots != null) {
                        List<Transaction> transactions = new ArrayList<>();
                        // Filter transactions from last 30 days
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Transaction transaction = doc.toObject(Transaction.class);
                            if (transaction.getTimestamp() >= thirtyDaysAgo) {
                                transaction.setId(doc.getId());
                                transactions.add(transaction);
                            }
                        }
                        transactionsLiveData.postValue(transactions);
                    }
                });
    }

    public void deleteTransaction(String transactionId, MutableLiveData<Boolean> successLiveData, MutableLiveData<String> errorMessage) {
        String uid = getCurrentUserId();
        if (uid == null) {
            errorMessage.postValue("User not authenticated");
            return;
        }

        firestore
                .collection("users")
                .document(uid)
                .collection("transactions")
                .document(transactionId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.i("TRANSACTION_DELETED", "Transaction deleted successfully: " + transactionId);
                    successLiveData.postValue(true);
                })
                .addOnFailureListener(e -> {
                    Log.e("TRANSACTION_DELETE_ERROR", "Failed to delete transaction", e);
                    errorMessage.postValue(e.getMessage());
                    successLiveData.postValue(false);
                });
    }

    private String getCurrentUserId() {
        if (firebaseAuth.getCurrentUser() != null) {
            return firebaseAuth.getCurrentUser().getUid();
        }
        return null;
    }
}
