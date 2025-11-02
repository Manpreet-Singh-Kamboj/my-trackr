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

        firestore
                .collection("users")
                .document(uid)
                .collection("transactions")
                .whereEqualTo("month", month)
                .whereEqualTo("year", year)
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
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Transaction transaction = doc.toObject(Transaction.class);
                            transaction.setId(doc.getId());
                            transactions.add(transaction);
                        }
                        transactionsLiveData.postValue(transactions);
                    }
                });
    }

    private String getCurrentUserId() {
        if (firebaseAuth.getCurrentUser() != null) {
            return firebaseAuth.getCurrentUser().getUid();
        }
        return null;
    }
}
