package com.mytrackr.receipts.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mytrackr.receipts.data.model.Budget;
import com.mytrackr.receipts.data.repository.BudgetRepository;
import com.mytrackr.receipts.utils.NotificationHelper;
import com.mytrackr.receipts.utils.NotificationPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class BudgetNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "BudgetNotificationReceiver";
    public static final String EXTRA_BUDGET_MONTH = "budget_month";
    public static final String EXTRA_BUDGET_YEAR = "budget_year";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm received for budget notification");

        final PendingResult pendingResult = goAsync();

        try {
            try {
                FirebaseApp.getInstance();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Firebase not initialized, attempting to initialize", e);
                FirebaseApp.initializeApp(context);
            }

            String month = intent.getStringExtra(EXTRA_BUDGET_MONTH);
            String year = intent.getStringExtra(EXTRA_BUDGET_YEAR);

            if (month == null || year == null) {
                Log.w(TAG, "No budget month/year in alarm intent");
                pendingResult.finish();
                return;
            }

            NotificationPreferences prefs = new NotificationPreferences(context);
            if (!prefs.isExpenseAlertsEnabled()) {
                Log.d(TAG, "Expense alerts disabled, not showing notification");
                pendingResult.finish();
                return;
            }

            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                Log.w(TAG, "No user logged in for notification");
                pendingResult.finish();
                return;
            }

            String userId = auth.getCurrentUser().getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            String docId = month + "_" + year;
            db.collection("users")
                .document(userId)
                .collection("budgets")
                .document(docId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    try {
                        if (documentSnapshot.exists()) {
                            Log.d(TAG, "Budget document found, parsing...");
                            Budget budget = com.mytrackr.receipts.data.repository.BudgetRepository.parseBudgetFromDocument(documentSnapshot);
                            if (budget != null) {
                                double percentage = budget.getSpentPercentage();
                                String status = getBudgetStatus(percentage);

                                Log.d(TAG, "Budget parsed successfully. Status: " + status +
                                        ", Percentage: " + percentage + "%");

                                NotificationHelper.showBudgetAlertNotification(
                                    context,
                                    budget,
                                    status
                                );
                                Log.d(TAG, "Budget notification shown successfully");
                            } else {
                                Log.w(TAG, "Failed to parse budget from document");
                            }
                        } else {
                            Log.w(TAG, "Budget not found: " + docId);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing notification", e);
                    } finally {
                        try {
                            com.mytrackr.receipts.utils.NotificationScheduler.scheduleWeeklyBudgetCheck(context);
                        } catch (Exception e) {
                            Log.e(TAG, "Error scheduling next weekly budget check", e);
                        }
                        pendingResult.finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching budget for notification: " + e.getMessage(), e);
                    try {
                        com.mytrackr.receipts.utils.NotificationScheduler.scheduleWeeklyBudgetCheck(context);
                    } catch (Exception ex) {
                        Log.e(TAG, "Error scheduling next weekly budget check after failure", ex);
                    }
                    pendingResult.finish();
                });

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in onReceive", e);
            pendingResult.finish();
        }
    }
    
    private String getBudgetStatus(double percentage) {
        if (percentage >= 100) {
            return "budget_exceeded";
        } else if (percentage >= 85) {
            return "almost_exceeded";
        } else if (percentage >= 70) {
            return "spending_high";
        }
        return "on_track";
    }
}

