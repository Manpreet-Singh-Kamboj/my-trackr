package com.mytrackr.receipts.features.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.mytrackr.receipts.MainActivity;
import com.mytrackr.receipts.databinding.ActivitySplashBinding;
import com.mytrackr.receipts.features.get_started.GetStartedActivity;
import com.mytrackr.receipts.features.onboarding.OnboardingActivity;
import com.mytrackr.receipts.viewmodels.AuthViewModel;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.splashTitle.animate()
                .alpha(1f)
                .setDuration(800)
                .setStartDelay(250)
                .start();
        AuthViewModel authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                authViewModel.getUser().observe(SplashActivity.this, user->{
                    if(user != null){
                        // User is logged in, check if onboarding is completed
                        if (OnboardingActivity.isOnboardingCompleted(SplashActivity.this)) {
                            // User has completed onboarding, go to main activity
                            startActivity(new Intent(SplashActivity.this,MainActivity.class));
                        } else {
                            // User hasn't seen onboarding yet, show onboarding
                            startActivity(new Intent(SplashActivity.this, OnboardingActivity.class));
                        }
                        finish();
                    }else{
                        startActivity(new Intent(SplashActivity.this, GetStartedActivity.class));
                        finish();
                    }
                });
            }
        };
        new Handler()
                .postDelayed(runnable,2500);
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}