package com.mytrackr.receipts.features.core.fragments;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.mytrackr.receipts.R;
import com.mytrackr.receipts.databinding.FragmentProfileBinding;
import com.mytrackr.receipts.features.get_started.GetStartedActivity;
import com.mytrackr.receipts.viewmodels.AuthViewModel;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private AuthViewModel authViewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(getLayoutInflater());
        binding.signOut.setOnClickListener(this::handleSignOut);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView(){
        super.onDestroyView();
        binding = null;
        authViewModel = null;
    }

    private void handleSignOut(View view){
        authViewModel.handleSignOut();
        startActivity(new Intent(requireContext(), GetStartedActivity.class));
        requireActivity().finishAffinity();
    }
}