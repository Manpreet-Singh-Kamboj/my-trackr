package com.mytrackr.receipts.features.core.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.databinding.FragmentHomeBinding;
import com.mytrackr.receipts.ui.adapter.ReceiptAdapter;
import com.mytrackr.receipts.features.receipts.ReceiptDetailsActivity;
import com.mytrackr.receipts.features.receipts.ReceiptScanActivity;
import com.mytrackr.receipts.viewmodels.HomeViewModel;

import java.util.List;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";

    private RecyclerView receiptsRecyclerView;
    private ReceiptAdapter receiptAdapter;
    private View emptyStateLayout;
    private View loadingProgressLayout;
    private TextView receiptsCount;
    private HomeViewModel homeViewModel;
    private FragmentHomeBinding binding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(getLayoutInflater());
        View root = binding.getRoot();

        receiptsRecyclerView = binding.receiptsRecyclerView;
        emptyStateLayout = binding.emptyStateLayout;
        loadingProgressLayout = binding.loadingProgressLayout;
        receiptsCount = binding.receiptsCount;
        FloatingActionButton fab = binding.fabScan;

        receiptAdapter = new ReceiptAdapter();
        receiptAdapter.setOnReceiptClickListener(receipt -> {
            Intent intent = ReceiptDetailsActivity.createIntent(getActivity(), receipt);
            startActivity(intent);
        });

        receiptsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        receiptsRecyclerView.setAdapter(receiptAdapter);

        fab.setOnClickListener(v -> {
            Intent i = new Intent(getActivity(), ReceiptScanActivity.class);
            startActivity(i);
        });

        observeViewModel();

        homeViewModel.loadReceipts();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            layoutParams.leftMargin = insets.left;
            layoutParams.topMargin = insets.top;
            layoutParams.rightMargin = insets.right;
            layoutParams.bottomMargin = 0;
            v.setLayoutParams(layoutParams);
            return WindowInsetsCompat.CONSUMED;
        });

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        homeViewModel.refreshReceipts();
    }

    private void observeViewModel() {
        homeViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading != null) {
                updateLoadingState(isLoading);
            }
        });

        homeViewModel.getReceipts().observe(getViewLifecycleOwner(), receipts -> {
            if (receipts != null) {
                receiptAdapter.setReceipts(receipts);
                Boolean isLoading = homeViewModel.getIsLoading().getValue();
                if (isLoading == null || !isLoading) {
                    updateEmptyState(receipts.isEmpty());
                }
            }
        });

        homeViewModel.getReceiptsCount().observe(getViewLifecycleOwner(), count -> {
            if (count != null) {
                updateReceiptsCount(count);
            }
        });

        homeViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Log.e(TAG, "Error: " + errorMessage);
                Boolean isLoading = homeViewModel.getIsLoading().getValue();
                if (isLoading == null || !isLoading) {
                    updateEmptyState(true);
                }
            }
        });
    }

    private void updateEmptyState(boolean isEmpty) {
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (receiptsRecyclerView != null) {
            receiptsRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    private void updateReceiptsCount(int count) {
        if (receiptsCount != null) {
            String countText = count + " " + (count == 1 ? "receipt" : "receipts");
            receiptsCount.setText(countText);
        }
    }

    private void updateLoadingState(boolean isLoading) {
        if (loadingProgressLayout != null) {
            loadingProgressLayout.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        if (isLoading) {
            if (receiptsRecyclerView != null) {
                receiptsRecyclerView.setVisibility(View.GONE);
            }
            if (emptyStateLayout != null) {
                emptyStateLayout.setVisibility(View.GONE);
            }
        } else {
            List<Receipt> receipts = homeViewModel.getReceipts().getValue();
            if (receipts != null) {
                updateEmptyState(receipts.isEmpty());
            }
        }
    }
}