package com.mytrackr.receipts.features.onboarding;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.databinding.ActivityOnboardingBinding;
import com.mytrackr.receipts.features.core.MainActivity;

public class OnboardingActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "onboarding_prefs";
    private static final String PREF_ONBOARDING_COMPLETED = "onboarding_completed";
    
    private ActivityOnboardingBinding binding;
    private OnboardingAdapter adapter;
    private int currentPage = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        setupViewPager();
        setupClickListeners();
        updateUI();
    }
    
    private void setupViewPager() {
        adapter = new OnboardingAdapter();
        binding.viewPager.setAdapter(adapter);
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updateUI();
            }
        });
        
        binding.dotsIndicator.attachTo(binding.viewPager);
    }
    
    private void setupClickListeners() {
        binding.btnNext.setOnClickListener(v -> {
            if (currentPage < adapter.getItemCount() - 1) {
                binding.viewPager.setCurrentItem(currentPage + 1);
            } else {
                finishOnboarding();
            }
        });
        
        binding.btnPrevious.setOnClickListener(v -> {
            if (currentPage > 0) {
                binding.viewPager.setCurrentItem(currentPage - 1);
            }
        });
        
        binding.tvSkip.setOnClickListener(v -> finishOnboarding());
    }
    
    private void updateUI() {
        // Update Previous button visibility
        binding.btnPrevious.setVisibility(currentPage > 0 ? View.VISIBLE : View.INVISIBLE);
        
        // Update Next/Get Started button text
        if (currentPage == adapter.getItemCount() - 1) {
            binding.btnNext.setText(R.string.get_started);
        } else {
            binding.btnNext.setText(R.string.next);
        }
        
        // Update Skip button visibility
        binding.tvSkip.setVisibility(currentPage < adapter.getItemCount() - 1 ? View.VISIBLE : View.INVISIBLE);
    }
    
    private void finishOnboarding() {
        // Mark onboarding as completed
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_ONBOARDING_COMPLETED, true).apply();
        
        // Navigate to main activity
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    public static boolean isOnboardingCompleted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_ONBOARDING_COMPLETED, false);
    }
}