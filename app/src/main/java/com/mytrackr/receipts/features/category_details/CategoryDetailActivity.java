package com.mytrackr.receipts.features.category_details;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.model.DetailItem;
import com.mytrackr.receipts.ui.adapter.CategoryDetailAdapter;
import com.mytrackr.receipts.viewmodels.CategoryDetailViewModel;

import java.util.ArrayList;
import java.util.List;

public class CategoryDetailActivity extends AppCompatActivity {

    private static final String EXTRA_CATEGORY_NAME = "CATEGORY_NAME";
    private static final String EXTRA_CATEGORY_COLOR = "CATEGORY_COLOR";

    private String categoryName;

    private RecyclerView rvDetails;
    private CategoryDetailAdapter adapter;
    private CategoryDetailViewModel viewModel;

    public static Intent newIntent(Context context, String categoryName, String categoryColor) {
        Intent intent = new Intent(context, CategoryDetailActivity.class);
        intent.putExtra(EXTRA_CATEGORY_NAME, categoryName);
        intent.putExtra(EXTRA_CATEGORY_COLOR, categoryColor);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_detail);

        categoryName = getIntent().getStringExtra(EXTRA_CATEGORY_NAME);
        String categoryColor = getIntent().getStringExtra(EXTRA_CATEGORY_COLOR);
        if (categoryName == null) categoryName = "Details";

        viewModel = new ViewModelProvider(this).get(CategoryDetailViewModel.class);

        setupToolbar();
        initViews();
        observeViewModel();
        loadData();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainContent), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            layoutParams.leftMargin = insets.left;
            layoutParams.topMargin = insets.top;
            layoutParams.rightMargin = insets.right;
            layoutParams.bottomMargin = insets.bottom;
            v.setLayoutParams(layoutParams);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(""); // Clear default title
        }

        // Set title using the TextView in toolbar_layout
        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        if (toolbarTitle != null) {
            toolbarTitle.setText(getString(R.string.expenses));
        }
    }

    private void initViews() {
        rvDetails = findViewById(R.id.rvDetails);
        rvDetails.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CategoryDetailAdapter(new ArrayList<>());
        rvDetails.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getDetailList().observe(this, detailList -> {
            if (detailList != null) {
                adapter.updateList(detailList);
                checkEmpty(detailList);
            }
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            // You can show/hide loading indicator here if needed
        });

        viewModel.getErrorMessage().observe(this, errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadData() {
        if (categoryName != null) {
            viewModel.loadData(categoryName);
        }
    }

    private void checkEmpty(List<DetailItem> detailList) {
        if (detailList == null || detailList.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_records_found_for, categoryName), Toast.LENGTH_SHORT).show();
        }
    }
}

