package com.mytrackr.receipts.features.get_started;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import com.mytrackr.receipts.databinding.ActivityGetStartedBinding;
import com.mytrackr.receipts.features.auth.SignInActivity;
import com.mytrackr.receipts.features.auth.SignupActivity;

public class GetStartedActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityGetStartedBinding binding = ActivityGetStartedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.signIn.setOnClickListener(this::onSignInButtonClicked);
        binding.signUp.setOnClickListener(this::onSignUpButtonClicked);
    }
    private void onSignInButtonClicked(View view){
        startActivity(new Intent(this, SignInActivity.class));
    }
    private void onSignUpButtonClicked(View view){
        startActivity(new Intent(this, SignupActivity.class));
    }
}