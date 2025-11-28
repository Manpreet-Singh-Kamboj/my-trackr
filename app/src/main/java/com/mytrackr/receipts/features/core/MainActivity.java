package com.mytrackr.receipts.features.core;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.mytrackr.receipts.R;
import com.mytrackr.receipts.databinding.ActivityMainBinding;
import com.mytrackr.receipts.features.core.fragments.DashboardFragment;
import com.mytrackr.receipts.features.core.fragments.ExpensesFragment;
import com.mytrackr.receipts.features.core.fragments.HomeFragment;
import com.mytrackr.receipts.features.core.fragments.ProfileFragment;
import com.mytrackr.receipts.features.get_started.GetStartedActivity;
import com.mytrackr.receipts.utils.NotificationHelper;
import com.mytrackr.receipts.utils.NotificationPermissionHelper;
import com.mytrackr.receipts.utils.NotificationScheduler;
import com.mytrackr.receipts.utils.ThemePreferences;
import com.mytrackr.receipts.viewmodels.AuthViewModel;


public class MainActivity extends AppCompatActivity {
    private static FragmentManager fragmentManager;
    private static AuthViewModel authViewModel;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferences themePreferences = new ThemePreferences(this);
        themePreferences.applySavedThemeMode();
        
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        authViewModel.error().observeForever(error->{
            if(!error.isEmpty()){
                authViewModel.showErrorSnackBar(binding,error);
                Log.e("MAIN_ACTIVITY_ERROR", error);
                if(error.equals("User is not authenticated. Please SignIn again to continue")){
                    startActivity(new Intent(this, GetStartedActivity.class));
                    finishAffinity();
                }
            }
        });
        fragmentManager = getSupportFragmentManager();
        if(savedInstanceState == null){
            loadFragments(new HomeFragment());
        }
        binding.bottomNavigation.setOnItemSelectedListener(this::onBottomNavItemSelected);
        
        initializeNotifications();
        requestNotificationPermissionIfNeeded();
    }
    
    private void initializeNotifications() {
        NotificationHelper.createNotificationChannel(this);
        NotificationScheduler.scheduleReplacementPeriodCheck(this);
    }
    
    private void requestNotificationPermissionIfNeeded() {
        if (!NotificationPermissionHelper.hasNotificationPermission(this)) {
            NotificationPermissionHelper.requestNotificationPermission(this);
        }
    }

    private boolean onBottomNavItemSelected(MenuItem item){
        int itemId = item.getItemId();
        if (itemId == R.id.navHome) {
            loadFragments(new HomeFragment());
        } else if (itemId == R.id.navExpenses) {
            loadFragments(new ExpensesFragment());
        } else if (itemId == R.id.navDashboard) {
            loadFragments(new DashboardFragment());
        } else if (itemId == R.id.navProfile) {
            loadFragments(new ProfileFragment());
        } else {
            Log.e("INVALID_BOTTOM_NAV_ID", "The provided bottom nav itemId is invalid");
        }

        return true;
    }

    private void loadFragments(Fragment fragment){
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.frameLayout, fragment);
        fragmentTransaction.commit();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        NotificationPermissionHelper.handlePermissionResult(this, requestCode, permissions, grantResults);
    }
}