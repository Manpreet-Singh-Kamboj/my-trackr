package com.mytrackr.receipts.features.core.fragments;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mytrackr.receipts.features.receipts.ReceiptScanActivity;
import com.mytrackr.receipts.R;

public class HomeFragment extends Fragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        FloatingActionButton fab = root.findViewById(R.id.fabScan);
        fab.setOnClickListener(v -> {
            Intent i = new Intent(getActivity(), ReceiptScanActivity.class);
            startActivity(i);
        });
        return root;
    }
}