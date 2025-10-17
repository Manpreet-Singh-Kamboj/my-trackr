package com.mytrackr.receipts;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.mytrackr.receipts.databinding.ActivityMainBinding;
import com.mytrackr.receipts.features.get_started.GetStartedActivity;
import com.mytrackr.receipts.viewmodels.AuthViewModel;

public class MainActivity extends AppCompatActivity {
    AuthViewModel authViewModel;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        binding.signOut.setOnClickListener(this::handleSignOut);
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    private void handleSignOut(View view){
        authViewModel.handleSignOut();
        startActivity(new Intent(this, GetStartedActivity.class));
        finishAffinity();
    }
}