package com.mytrackr.receipts.features.core.fragments;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.models.ProfileMenuItem;
import com.mytrackr.receipts.databinding.FragmentProfileBinding;
import com.mytrackr.receipts.databinding.LogoutBottomSheetLayoutBinding;
import com.mytrackr.receipts.features.core.adapters.ProfileMenuAdapter;
import com.mytrackr.receipts.features.get_started.GetStartedActivity;
import com.mytrackr.receipts.utils.Utils;
import com.mytrackr.receipts.viewmodels.AuthViewModel;

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private AuthViewModel authViewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(getLayoutInflater());
        setupProfileMenuOptions();
        setupProfileSupportOptions();
        authViewModel.getUserDetails().observe(getViewLifecycleOwner(),user->{
            if(user != null){
                Glide.with(binding.getRoot().getContext())
                        .load(user.getProfilePicture())
                        .placeholder(R.drawable.ic_user_avatar)
                        .error(R.drawable.ic_user_avatar)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .skipMemoryCache(false)
                        .into(binding.profileImageView);
                binding.userFullName.setText(user.getFullName());
                binding.userEmail.setText(user.getEmail());
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            layoutParams.leftMargin = insets.left;
            layoutParams.topMargin = insets.top;
            layoutParams.rightMargin = insets.right;
            layoutParams.bottomMargin = insets.bottom;
            v.setLayoutParams(layoutParams);
            return WindowInsetsCompat.CONSUMED;
        });
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

    private void handleLogout(){
        LogoutBottomSheetLayoutBinding logoutBottomSheetLayoutBinding = LogoutBottomSheetLayoutBinding.inflate(
                LayoutInflater.from(requireContext())
        );
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        bottomSheetDialog.setContentView(logoutBottomSheetLayoutBinding.getRoot());
        bottomSheetDialog.show();
        logoutBottomSheetLayoutBinding.bottomSheetLogout.setOnClickListener(this::handleSignOut);
        logoutBottomSheetLayoutBinding.bottomSheetCancelLogout.setOnClickListener(view->{
            bottomSheetDialog.dismiss();
        });
    }

    private List<ProfileMenuItem> getProfileMenuItems(){
        List<ProfileMenuItem> profileMenuItems = new ArrayList<>();
        profileMenuItems.add(new ProfileMenuItem(
                R.drawable.ic_user_account,
                "Account Details",
                () -> {}
        ));
        profileMenuItems.add(new ProfileMenuItem(
                R.drawable.ic_notification_bell,
                "Notifications",
                () -> {}
        ));
        profileMenuItems.add(new ProfileMenuItem(
                R.drawable.ic_settings,
                "Settings",
                () -> {}
        ));
        return profileMenuItems;
    }
    private List<ProfileMenuItem> getProfileSupportItems(){
        List<ProfileMenuItem> profileMenuItems = new ArrayList<>();
        profileMenuItems.add(new ProfileMenuItem(
                R.drawable.ic_contact_us,
                "Contact Us",
                () -> {}
        ));
        profileMenuItems.add(new ProfileMenuItem(
                R.drawable.ic_privacy_policy,
                "Privacy Policy",
                () -> {}
        ));
        profileMenuItems.add(new ProfileMenuItem(
                R.drawable.ic_terms_and_conditions,
                "Terms & Conditions",
                () -> {}
        ));
        profileMenuItems.add(new ProfileMenuItem(
                R.drawable.ic_get_help,
                "Get Help",
                () -> {}
        ));
        profileMenuItems.add(new ProfileMenuItem(
                R.drawable.ic_logout,
                "Logout",
                this::handleLogout
        ));
        return profileMenuItems;
    }

    private void setupProfileMenuOptions() {
        List<ProfileMenuItem> profileMenuItems = getProfileMenuItems();
        ProfileMenuAdapter profileMenuAdapter = new ProfileMenuAdapter(profileMenuItems);
        binding.profileActionsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.profileActionsRecyclerView.setAdapter(profileMenuAdapter);
        binding.profileActionsRecyclerView.setClickable(true);
        binding.profileActionsRecyclerView.setNestedScrollingEnabled(false);
        binding.profileActionsRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    private void setupProfileSupportOptions(){
        List<ProfileMenuItem> profileSupportItems = getProfileSupportItems();
        ProfileMenuAdapter profileMenuAdapter = new ProfileMenuAdapter(profileSupportItems);
        binding.profileSupportRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.profileSupportRecyclerView.setAdapter(profileMenuAdapter);
        binding.profileSupportRecyclerView.setClickable(true);
        binding.profileSupportRecyclerView.setNestedScrollingEnabled(false);
        binding.profileSupportRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }
}