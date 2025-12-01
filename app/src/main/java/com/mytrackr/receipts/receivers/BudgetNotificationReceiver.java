package com.mytrackr.receipts.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mytrackr.receipts.data.model.Budget;
import com.mytrackr.receipts.utils.NotificationHelper;
import com.mytrackr.receipts.utils.NotificationPreferences;
import com.mytrackr.receipts.utils.NotificationScheduler;

public class BudgetNotificationReceiver extends BroadcastReceiver {
    public static final String EXTRA_BUDGET_MONTH = "budget_month";
    public static final String EXTRA_BUDGET_YEAR = "budget_year";

    @Override
    public void onReceive(Context context, Intent intent) {
        FirebaseCrashlytics.getInstance().log("D/BudgetNotificationReceiver: Alarm received for budget notification");

        final PendingResult pendingResult = goAsync();

        try {
            try {
                FirebaseApp.getInstance();
            } catch (IllegalStateException e) {
                FirebaseCrashlytics.getInstance().log("E/BudgetNotificationReceiver: Firebase not initialized, attempting to initialize");
                FirebaseCrashlytics.getInstance().recordException(e);
                FirebaseApp.initializeApp(context);
            }

            String month = intent.getStringExtra(EXTRA_BUDGET_MONTH);
            String year = intent.getStringExtra(EXTRA_BUDGET_YEAR);

            if (month == null || year == null) {
                FirebaseCrashlytics.getInstance().log("W/BudgetNotificationReceiver: No budget month/year in alarm intent");
                pendingResult.finish();
                return;
            }

            NotificationPreferences prefs = new NotificationPreferences(context);
            if (!prefs.isExpenseAlertsEnabled()) {
                FirebaseCrashlytics.getInstance().log("D/BudgetNotificationReceiver: Expense alerts disabled, not showing notification");
                pendingResult.finish();
                return;
            }

            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                FirebaseCrashlytics.getInstance().log("W/BudgetNotificationReceiver: No user logged in for notification");
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
                                FirebaseCrashlytics.getInstance().log("D/BudgetNotificationReceiver: Budget document found, parsing...");
                                Budget budget = com.mytrackr.receipts.data.repository.BudgetRepository.parseBudgetFromDocument(documentSnapshot);
                                if (budget != null) {
                                    double percentage = budget.getSpentPercentage();
                                    String status = getBudgetStatus(percentage);

                                    FirebaseCrashlytics.getInstance().log("D/BudgetNotificationReceiver: Budget parsed successfully. Status: " + status + ", Percentage: " + percentage + "%");

                                    NotificationHelper.showBudgetAlertNotification(
                                            context,
                                            budget,
                                            status
                                    );
                                    FirebaseCrashlytics.getInstance().log("D/BudgetNotificationReceiver: Budget notification shown successfully");
                                } else {
                                    FirebaseCrashlytics.getInstance().log("W/BudgetNotificationReceiver: Failed to parse budget from document");
                                }
                            } else {
                                FirebaseCrashlytics.getInstance().log("W/BudgetNotificationReceiver: Budget not found: " + docId);
                            }
                        } catch (Exception e) {
                            FirebaseCrashlytics.getInstance().log("E/BudgetNotificationReceiver: Error processing notification");
                            FirebaseCrashlytics.getInstance().recordException(e);
                        } finally {
                            try {
                                NotificationScheduler.scheduleWeeklyBudgetCheck(context);
                            } catch (Exception e) {
                                FirebaseCrashlytics.getInstance().log("E/BudgetNotificationReceiver: Error scheduling next weekly budget check");
                                FirebaseCrashlytics.getInstance().recordException(e);
                            }
                            pendingResult.finish();
                        }
                    })
                    .addOnFailureListener(e -> {
                        FirebaseCrashlytics.getInstance().log("E/BudgetNotificationReceiver: Error fetching budget for notification: " + e.getMessage());
                        FirebaseCrashlytics.getInstance().recordException(e);
                        try {
                            NotificationScheduler.scheduleWeeklyBudgetCheck(context);
                        } catch (Exception ex) {
                            FirebaseCrashlytics.getInstance().log("E/BudgetNotificationReceiver: Error scheduling next weekly budget check after failure");
                            FirebaseCrashlytics.getInstance().recordException(ex);
                        }
                        pendingResult.finish();
                    });

        } catch (Exception e) {
            FirebaseCrashlytics.getInstance().log("E/BudgetNotificationReceiver: Unexpected error in onReceive");
            FirebaseCrashlytics.getInstance().recordException(e);
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
