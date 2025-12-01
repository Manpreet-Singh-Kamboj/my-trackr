package com.mytrackr.receipts.data.repository;

import androidx.lifecycle.MutableLiveData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
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
            FirebaseCrashlytics.getInstance().log("D/TransactionRepository: Transaction Repository is Initialized");
        }
        return instance;
    }

    public void addTransaction(Transaction transaction, MutableLiveData<Boolean> successLiveData, MutableLiveData<String> errorMessage) {
        String uid = getCurrentUserId();
        if (uid == null) {
            errorMessage.postValue("User not authenticated");
            FirebaseCrashlytics.getInstance().log("W/TransactionRepository: Attempted to add transaction for unauthenticated user");
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
                    FirebaseCrashlytics.getInstance().log("D/TransactionRepository: Transaction added successfully: " + documentReference.getId());
                    successLiveData.postValue(true);
                })
                .addOnFailureListener(e -> {
                    FirebaseCrashlytics.getInstance().log("E/TransactionRepository: Failed to add transaction");
                    FirebaseCrashlytics.getInstance().recordException(e);
                    errorMessage.postValue(e.getMessage());
                    successLiveData.postValue(false);
                });
    }

    public void getRecentTransactions(String month, String year, int limit, MutableLiveData<List<Transaction>> transactionsLiveData, MutableLiveData<String> errorMessage) {
        String uid = getCurrentUserId();
        if (uid == null) {
            errorMessage.postValue("User not authenticated");
            FirebaseCrashlytics.getInstance().log("W/TransactionRepository: Attempted to get recent transactions for unauthenticated user");
            return;
        }

        firestore
                .collection("users")
                .document(uid)
                .collection("transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100) 
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        FirebaseCrashlytics.getInstance().log("E/TransactionRepository: Failed to fetch transactions");
                        FirebaseCrashlytics.getInstance().recordException(e);
                        errorMessage.postValue(e.getMessage());
                        return;
                    }

                    if (queryDocumentSnapshots != null) {
                        List<Transaction> transactions = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Transaction transaction = doc.toObject(Transaction.class);
                            if (month.equals(transaction.getMonth()) && year.equals(transaction.getYear())) {
                                transaction.setId(doc.getId());
                                transactions.add(transaction);
                                if (transactions.size() >= limit) {
                                    break; 
                                }
                            }
                        }
                        transactionsLiveData.postValue(transactions);
                        FirebaseCrashlytics.getInstance().log("D/TransactionRepository: Fetched " + transactions.size() + " transactions for " + month + "/" + year);
                    }
                });
    }

    public void getRecentTransactionsLastMonth(int limit, MutableLiveData<List<Transaction>> transactionsLiveData, MutableLiveData<String> errorMessage) {
        String uid = getCurrentUserId();
        if (uid == null) {
            errorMessage.postValue("User not authenticated");
            FirebaseCrashlytics.getInstance().log("W/TransactionRepository: Attempted to get recent transactions for unauthenticated user");
            return;
        }

        long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);

        firestore
                .collection("users")
                .document(uid)
                .collection("transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        FirebaseCrashlytics.getInstance().log("E/TransactionRepository: Failed to fetch transactions for last month");
                        FirebaseCrashlytics.getInstance().recordException(e);
                        errorMessage.postValue(e.getMessage());
                        return;
                    }

                    if (queryDocumentSnapshots != null) {
                        List<Transaction> transactions = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Transaction transaction = doc.toObject(Transaction.class);
                            if (transaction.getTimestamp() >= thirtyDaysAgo) {
                                transaction.setId(doc.getId());
                                transactions.add(transaction);
                            }
                        }
                        transactionsLiveData.postValue(transactions);
                         FirebaseCrashlytics.getInstance().log("D/TransactionRepository: Fetched " + transactions.size() + " transactions from last month");
                    }
                });
    }

    public void deleteTransaction(String transactionId, MutableLiveData<Boolean> successLiveData, MutableLiveData<String> errorMessage) {
        String uid = getCurrentUserId();
        if (uid == null) {
            errorMessage.postValue("User not authenticated");
            FirebaseCrashlytics.getInstance().log("W/TransactionRepository: Attempted to delete transaction for unauthenticated user");
            return;
        }

        firestore
                .collection("users")
                .document(uid)
                .collection("transactions")
                .document(transactionId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    FirebaseCrashlytics.getInstance().log("D/TransactionRepository: Transaction deleted successfully: " + transactionId);
                    successLiveData.postValue(true);
                })
                .addOnFailureListener(e -> {
                    FirebaseCrashlytics.getInstance().log("E/TransactionRepository: Failed to delete transaction: " + transactionId);
                    FirebaseCrashlytics.getInstance().recordException(e);
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
