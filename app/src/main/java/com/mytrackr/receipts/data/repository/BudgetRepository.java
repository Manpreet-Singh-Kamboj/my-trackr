package com.mytrackr.receipts.data.repository;

import androidx.lifecycle.MutableLiveData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.mytrackr.receipts.data.model.Budget;
import java.util.HashMap;
import java.util.Map;
import com.google.firebase.firestore.DocumentSnapshot;

public class BudgetRepository {
    private static BudgetRepository instance;
    private final FirebaseFirestore firestore;
    private final FirebaseAuth firebaseAuth;

    private BudgetRepository() {
        firestore = FirebaseFirestore.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();
    }

    public static synchronized BudgetRepository getInstance() {
        if (instance == null) {
            instance = new BudgetRepository();
            FirebaseCrashlytics.getInstance().log("D/BudgetRepository: Budget Repository is Initialized");
        }
        return instance;
    }

    public void getBudget(String month, String year, MutableLiveData<Budget> budgetLiveData, MutableLiveData<String> errorMessage) {
        String uid = getCurrentUserId();
        if (uid == null) {
            errorMessage.postValue("User not authenticated");
            FirebaseCrashlytics.getInstance().log("W/BudgetRepository: Attempted to get budget for unauthenticated user");
            return;
        }

        DocumentReference budgetDoc = firestore
                .collection("users")
                .document(uid)
                .collection("budgets")
                .document(month + "_" + year);

        budgetDoc.get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Budget budget = documentSnapshot.toObject(Budget.class);
                        budgetLiveData.postValue(budget);
                        FirebaseCrashlytics.getInstance().log("D/BudgetRepository: Budget fetched successfully for " + month + "/" + year);
                    } else {
                        budgetLiveData.postValue(null);
                        FirebaseCrashlytics.getInstance().log("D/BudgetRepository: No budget found for " + month + "/" + year);
                    }
                })
                .addOnFailureListener(e -> {
                    FirebaseCrashlytics.getInstance().log("E/BudgetRepository: Failed to fetch budget");
                    FirebaseCrashlytics.getInstance().recordException(e);
                    errorMessage.postValue(e.getMessage());
                });
    }

    public void saveBudget(Budget budget, MutableLiveData<Boolean> successLiveData, MutableLiveData<String> errorMessage) {
        String uid = getCurrentUserId();
        if (uid == null) {
            errorMessage.postValue("User not authenticated");
            FirebaseCrashlytics.getInstance().log("W/BudgetRepository: Attempted to save budget for unauthenticated user");
            return;
        }

        String docId = budget.getMonth() + "_" + budget.getYear();
        Map<String, Object> budgetData = new HashMap<>();
        budgetData.put("amount", budget.getAmount());
        budgetData.put("month", budget.getMonth());
        budgetData.put("year", budget.getYear());
        budgetData.put("spent", budget.getSpent());

        firestore
                .collection("users")
                .document(uid)
                .collection("budgets")
                .document(docId)
                .set(budgetData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    FirebaseCrashlytics.getInstance().log("D/BudgetRepository: Budget saved successfully for " + docId);
                    successLiveData.postValue(true);
                })
                .addOnFailureListener(e -> {
                    FirebaseCrashlytics.getInstance().log("E/BudgetRepository: Failed to save budget for " + docId);
                    FirebaseCrashlytics.getInstance().recordException(e);
                    errorMessage.postValue(e.getMessage());
                    successLiveData.postValue(false);
                });
    }
    
    /**
     * Save budget silently without triggering success callbacks (for internal syncs)
     */
    public void saveBudgetSilently(Budget budget, MutableLiveData<String> errorMessage) {
        String uid = getCurrentUserId();
        if (uid == null) {
            errorMessage.postValue("User not authenticated");
            FirebaseCrashlytics.getInstance().log("W/BudgetRepository: Attempted to sync budget for unauthenticated user");
            return;
        }

        String docId = budget.getMonth() + "_" + budget.getYear();
        Map<String, Object> budgetData = new HashMap<>();
        budgetData.put("amount", budget.getAmount());
        budgetData.put("month", budget.getMonth());
        budgetData.put("year", budget.getYear());
        budgetData.put("spent", budget.getSpent());

        firestore
                .collection("users")
                .document(uid)
                .collection("budgets")
                .document(docId)
                .set(budgetData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    FirebaseCrashlytics.getInstance().log("D/BudgetRepository: Budget synced silently for " + docId);
                })
                .addOnFailureListener(e -> {
                    FirebaseCrashlytics.getInstance().log("E/BudgetRepository: Failed to sync budget for " + docId);
                    FirebaseCrashlytics.getInstance().recordException(e);
                    errorMessage.postValue(e.getMessage());
                });
    }

    public void updateSpentAmount(String month, String year, double spentAmount, MutableLiveData<Boolean> successLiveData, MutableLiveData<String> errorMessage) {
        String uid = getCurrentUserId();
        if (uid == null) {
            errorMessage.postValue("User not authenticated");
             FirebaseCrashlytics.getInstance().log("W/BudgetRepository: Attempted to update spent amount for unauthenticated user");
            return;
        }

        String docId = month + "_" + year;
        firestore
                .collection("users")
                .document(uid)
                .collection("budgets")
                .document(docId)
                .update("spent", spentAmount)
                .addOnSuccessListener(aVoid -> {
                    FirebaseCrashlytics.getInstance().log("D/BudgetRepository: Spent amount updated successfully for " + docId);
                    successLiveData.postValue(true);
                })
                .addOnFailureListener(e -> {
                    FirebaseCrashlytics.getInstance().log("E/BudgetRepository: Failed to update spent amount for " + docId);
                    FirebaseCrashlytics.getInstance().recordException(e);
                    errorMessage.postValue(e.getMessage());
                    successLiveData.postValue(false);
                });
    }

    public static Budget parseBudgetFromDocument(DocumentSnapshot document) {
        try {
            Budget budget = new Budget();
            Map<String, Object> data = document.getData();
            if (data == null) return null;

            if (data.containsKey("amount")) {
                Object amount = data.get("amount");
                if (amount instanceof Number) {
                    budget.setAmount(((Number) amount).doubleValue());
                }
            }
            if (data.containsKey("month")) {
                budget.setMonth((String) data.get("month"));
            }
            if (data.containsKey("year")) {
                budget.setYear((String) data.get("year"));
            }
            if (data.containsKey("spent")) {
                Object spent = data.get("spent");
                if (spent instanceof Number) {
                    budget.setSpent(((Number) spent).doubleValue());
                }
            }

            return budget;
        } catch (Exception e) {
            FirebaseCrashlytics.getInstance().log("E/BudgetRepository: Error parsing budget from document");
            FirebaseCrashlytics.getInstance().recordException(e);
            return null;
        }
    }

    private String getCurrentUserId() {
        if (firebaseAuth.getCurrentUser() != null) {
            return firebaseAuth.getCurrentUser().getUid();
        }
        return null;
    }
}
