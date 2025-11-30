package com.mytrackr.receipts.features.core.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.model.Transaction;
import com.mytrackr.receipts.data.models.ProfileMenuItem;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.databinding.FragmentProfileBinding;
import com.mytrackr.receipts.databinding.LogoutBottomSheetLayoutBinding;
import com.mytrackr.receipts.features.change_password.ChangePasswordActivity;
import com.mytrackr.receipts.ui.adapter.ProfileMenuAdapter;
import com.mytrackr.receipts.features.edit_profile.EditProfileActivity;
import com.mytrackr.receipts.features.get_started.GetStartedActivity;
import com.mytrackr.receipts.features.settings.SettingsActivity;
import com.mytrackr.receipts.viewmodels.AuthViewModel;
import com.mytrackr.receipts.viewmodels.BudgetViewModel;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private AuthViewModel authViewModel;
    private BudgetViewModel budgetViewModel;
    private final java.util.List<Transaction> lastTransactions = new ArrayList<>();
    private final java.util.List<Receipt> lastReceipts = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(getLayoutInflater());
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        budgetViewModel = new ViewModelProvider(this).get(BudgetViewModel.class);
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

        // Keep latest transactions for CSV export
        budgetViewModel.getTransactionsLiveData().observe(getViewLifecycleOwner(), transactions -> {
            lastTransactions.clear();
            if (transactions != null) {
                lastTransactions.addAll(transactions);
            }
        });

        // Initial load of current month data for export
        refreshExportData();

        return binding.getRoot();
    }

    @Override
    public void onDestroyView(){
        super.onDestroyView();
        binding = null;
        authViewModel = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        authViewModel.refreshUserDetails();
        // Refresh export data when returning to profile so manual + receipt expenses are up to date
        refreshExportData();
    }

    private void refreshExportData() {
        if (budgetViewModel == null) return;

        // Load current month manual transactions
        budgetViewModel.loadCurrentMonthTransactions();

        // Load current month receipts into lastReceipts
        budgetViewModel.loadCurrentMonthReceipts(new MutableLiveData<java.util.List<Receipt>>() {
            @Override
            public void postValue(java.util.List<Receipt> value) {
                super.postValue(value);
                lastReceipts.clear();
                if (value != null) {
                    lastReceipts.addAll(value);
                }
            }

            @Override
            public void setValue(java.util.List<Receipt> value) {
                super.setValue(value);
                lastReceipts.clear();
                if (value != null) {
                    lastReceipts.addAll(value);
                }
            }
        });
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

    private void handleEditProfileClick(){
        startActivity(new Intent(getContext(), EditProfileActivity.class));
    }

    private void handleChangePasswordClick(){
        startActivity(new Intent(getContext(), ChangePasswordActivity.class));
    }

    private void handleExportCsvClick() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        1001);
                Toast.makeText(getContext(), getString(R.string.please_grant_storage_permission_to_export_csv), Toast.LENGTH_SHORT).show();
                return;
            }
        }
        exportExpensesToCsv();
    }

    private void exportExpensesToCsv() {
        if (lastTransactions.isEmpty() && lastReceipts.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.no_expenses_to_export_for_this_month), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir == null) {
                Toast.makeText(getContext(), getString(R.string.unable_to_access_downloads_folder), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                Toast.makeText(getContext(), getString(R.string.unable_to_create_downloads_folder), Toast.LENGTH_SHORT).show();
                return;
            }

            String monthYear = new SimpleDateFormat("MMM_yyyy", Locale.US).format(new Date());
            String fileName = "Expenses_Report_" + monthYear + ".csv";
            File csvFile = new File(downloadsDir, fileName);

            FileWriter writer = new FileWriter(csvFile);

            writer.append("date,store,expense_category,amount,tax,total_items,type,payment_method,tax_number,source\n");

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);

            for (Receipt receipt : lastReceipts) {
                if (receipt == null || receipt.getReceipt() == null) continue;

                Receipt.ReceiptInfo info = receipt.getReceipt();
                String date = info.getReceiptDateTimestamp() > 0
                        ? dateFormat.format(new java.util.Date(info.getReceiptDateTimestamp()))
                        : (info.getDate() != null ? info.getDate() : "");

                String storeName = "";
                if (receipt.getStore() != null && receipt.getStore().getName() != null) {
                    String name = receipt.getStore().getName().trim();
                    if (!name.isEmpty() && !"null".equalsIgnoreCase(name)) {
                        storeName = name;
                    }
                }

                String category = info.getCategory() != null ? info.getCategory() : "";
                double amount = info.getTotal();
                double tax = info.getTax();
                int totalItems = receipt.getItems() != null ? receipt.getItems().size() : 0;
                String paymentMethod = info.getPaymentMethod() != null ? info.getPaymentMethod() : "";
                String taxNumber = receipt.getAdditional() != null && receipt.getAdditional().getTaxNumber() != null
                        ? receipt.getAdditional().getTaxNumber()
                        : "";

                String type = "expense";
                String source = "receipt";

                writer.append(escapeCsv(date)).append(',')
                        .append(escapeCsv(storeName)).append(',')
                        .append(escapeCsv(category)).append(',')
                        .append(String.valueOf(amount)).append(',')
                        .append(String.valueOf(tax)).append(',')
                        .append(String.valueOf(totalItems)).append(',')
                        .append(escapeCsv(type)).append(',')
                        .append(escapeCsv(paymentMethod)).append(',')
                        .append(escapeCsv(taxNumber)).append(',')
                        .append(escapeCsv(source))
                        .append('\n');
            }

            for (Transaction transaction : lastTransactions) {
                if (transaction == null || !transaction.isExpense()) continue;

                String date = dateFormat.format(new java.util.Date(transaction.getTimestamp()));
                String storeName = transaction.getDescription() != null ? transaction.getDescription() : "";
                String category = "";
                double amount = transaction.getAmount();
                String type = transaction.getType() != null ? transaction.getType() : "expense";

                writer.append(escapeCsv(date)).append(',')
                        .append(escapeCsv(storeName)).append(',')
                        .append(escapeCsv(category)).append(',')
                        .append(String.valueOf(amount)).append(',')
                        .append("")  // tax not tracked for manual entries
                        .append(',')
                        .append("")  // total_items not tracked for manual entries
                        .append(',')
                        .append(escapeCsv(type)).append(',')
                        .append("")  // payment_method unknown
                        .append(',')
                        .append("")  // tax_number unknown
                        .append(',')
                        .append("manual")
                        .append('\n');
            }

            writer.flush();
            writer.close();

            Toast.makeText(getContext(), getString(R.string.csv_saved_in_downloads, fileName), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), getString(R.string.failed_to_export_csv, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        String v = value.replace("\"", "\"\"");
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v + "\"";
        }
        return v;
    }

    private List<ProfileMenuItem> getProfileMenuItems(){
        List<ProfileMenuItem> profileMenuItems = new ArrayList<>();
        profileMenuItems.add(new ProfileMenuItem(
                R.drawable.ic_user_account,
                "Edit Profile Details",
                this::handleEditProfileClick
        ));
        if(!authViewModel.isGoogleSignedInUser()){
            profileMenuItems.add(new ProfileMenuItem(
                    R.drawable.ic_change_password,
                    "Change Password",
                    this::handleChangePasswordClick
            ));
        }
        profileMenuItems.add(new ProfileMenuItem(
                R.drawable.ic_notification_bell,
                "Notifications",
                () -> startActivity(new Intent(getContext(), com.mytrackr.receipts.features.notifications.NotificationSettingsActivity.class))
        ));
        profileMenuItems.add(new ProfileMenuItem(
                R.drawable.ic_download,
                "Export Expenses CSV",
                this::handleExportCsvClick
        ));
        profileMenuItems.add(new ProfileMenuItem(
                R.drawable.ic_settings,
                "Settings",
                () -> startActivity(new Intent(getContext(), SettingsActivity.class))
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