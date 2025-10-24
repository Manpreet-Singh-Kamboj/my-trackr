package com.mytrackr.receipts.features.core;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.mytrackr.receipts.R;
import com.mytrackr.receipts.databinding.ActivityMainBinding;
import com.mytrackr.receipts.features.core.fragments.DashboardFragment;
import com.mytrackr.receipts.features.core.fragments.ExpensesFragment;
import com.mytrackr.receipts.features.core.fragments.HomeFragment;
import com.mytrackr.receipts.features.core.fragments.ProfileFragment;


public class MainActivity extends AppCompatActivity {
    private static FragmentManager fragmentManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        fragmentManager = getSupportFragmentManager();
        loadFragments(new HomeFragment(),true);
        binding.bottomNavigation.setOnItemSelectedListener(this::onBottomNavItemSelected);
    }

    private boolean onBottomNavItemSelected(MenuItem item){
        int itemId = item.getItemId();
        if (itemId == R.id.navHome) {
            loadFragments(new HomeFragment(),false);
        } else if (itemId == R.id.navExpenses) {
            loadFragments(new ExpensesFragment(),false);
        } else if (itemId == R.id.navDashboard) {
            loadFragments(new DashboardFragment(), false);
        } else if (itemId == R.id.navProfile) {
            loadFragments(new ProfileFragment(), false);
        } else {
            Log.e("INVALID_BOTTOM_NAV_ID", "The provided bottom nav itemId is invalid");
        }

        return true;
    }

    private void loadFragments(Fragment fragment, boolean isAppInitialized){
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        if (isAppInitialized) {
            fragmentTransaction.add(R.id.frameLayout,fragment);
        }else {
            fragmentTransaction.replace(R.id.frameLayout, fragment);
        }
        fragmentTransaction.commit();
    }
}