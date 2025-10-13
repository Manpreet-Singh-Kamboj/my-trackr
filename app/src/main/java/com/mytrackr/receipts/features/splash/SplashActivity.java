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
import com.mytrackr.receipts.features.auth.LoginActivity;
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
                        startActivity(new Intent(SplashActivity.this,MainActivity.class));
                        finish();
                    }else{
                        startActivity(new Intent(SplashActivity.this, LoginActivity.class));
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