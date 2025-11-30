package com.mytrackr.receipts.features.core.fragments;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mytrackr.receipts.R;
import com.mytrackr.receipts.data.models.Receipt;
import com.mytrackr.receipts.data.models.ReceiptItem;
import com.mytrackr.receipts.data.repository.ReceiptRepository;
import com.mytrackr.receipts.data.model.Transaction;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardFragment extends Fragment implements OnChartValueSelectedListener {

    private RecyclerView rvCategories;
    private BarChart barChart;
    private LineChart lineChart;
    private ImageView btnSwitchChart;
    private PieChart pieChart;
    private CategoryAdapter categoryAdapter;
    private List<CategoryItemDisplay> displayList = new ArrayList<>();
    private TextView tabWeek, tabMonth, tabYear, tabAll, tabRange;
    private FrameLayout dateNavContainer;
    private LinearLayout navStandard, navRange;
    private TextView tvDateDisplay;
    private ImageView btnPrev, btnNext;
    private TextView tvRangeStart, tvRangeEnd;
    private View cardStatistics;

    private static final int MODE_WEEK = 0;
    private static final int MODE_MONTH = 1;
    private static final int MODE_YEAR = 2;
    private static final int MODE_ALL = 3;
    private static final int MODE_RANGE = 4;
    private int currentMode = MODE_MONTH;

    private boolean isLineMode = false;

    private Calendar anchorCalendar = Calendar.getInstance();
    private Calendar rangeStartCalendar = Calendar.getInstance();
    private Calendar rangeEndCalendar = Calendar.getInstance();

    private List<Receipt> mAllReceipts = new ArrayList<>();
    private List<Transaction> mAllTransactions = new ArrayList<>();
    private double mGrandTotal = 0.0;

    private SimpleDateFormat sdfWeekRange = new SimpleDateFormat("MMM d", Locale.ENGLISH);
    private SimpleDateFormat sdfMonth = new SimpleDateFormat("MMM, yyyy", Locale.ENGLISH);
    private SimpleDateFormat sdfYear = new SimpleDateFormat("yyyy", Locale.ENGLISH);
    private SimpleDateFormat sdfRange = new SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH);

    private static final Map<String, String> CATEGORY_COLORS = new HashMap<>();
    static {
        CATEGORY_COLORS.put("Groceries", "#4CAF50");
        CATEGORY_COLORS.put("Meal", "#FFC107");
        CATEGORY_COLORS.put("Entertainment", "#9C27B0");
        CATEGORY_COLORS.put("Travel", "#2196F3");
        CATEGORY_COLORS.put("Shopping", "#E91E63");
        CATEGORY_COLORS.put("Tax", "#FF5722");
        CATEGORY_COLORS.put("Other", "#9E9E9E");
    }

    private static final Map<String, Integer> CATEGORY_ICONS = new HashMap<>();
    static {
        CATEGORY_ICONS.put("Groceries", R.drawable.ic_groceries);
        CATEGORY_ICONS.put("Meal", R.drawable.ic_meal);
        CATEGORY_ICONS.put("Entertainment", R.drawable.ic_entertainment);
        CATEGORY_ICONS.put("Travel", R.drawable.ic_travel);
        CATEGORY_ICONS.put("Shopping", R.drawable.ic_shopping);
        CATEGORY_ICONS.put("Tax", R.drawable.ic_tax);
        CATEGORY_ICONS.put("Other", R.drawable.ic_other);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupCharts();
        setupListeners();

        updateTabStyles();
        updateDateNavigationDisplay();

        fetchAllDataOnce();
    }

    private void initViews(View view) {
        rvCategories = view.findViewById(R.id.rvCategories);
        rvCategories.setLayoutManager(new LinearLayoutManager(getContext()));
        categoryAdapter = new CategoryAdapter(displayList);
        rvCategories.setAdapter(categoryAdapter);

        categoryAdapter.setOnItemClickListener(item -> {
            CategoryDetailFragment detailFragment = CategoryDetailFragment.newInstance(item.name, item.colorHex);
            if (getParentFragmentManager() != null && getView() != null && getView().getParent() != null) {
                int containerId = ((ViewGroup) getView().getParent()).getId();
                getParentFragmentManager().beginTransaction()
                        .replace(containerId, detailFragment)
                        .addToBackStack(null)
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                        .commit();
            }
        });

        barChart = view.findViewById(R.id.barChart);
        lineChart = view.findViewById(R.id.lineChart);
        btnSwitchChart = view.findViewById(R.id.btnSwitchChart);
        pieChart = view.findViewById(R.id.pieChart);
        cardStatistics = view.findViewById(R.id.cardStatistics);

        tabWeek = view.findViewById(R.id.tabWeek);
        tabMonth = view.findViewById(R.id.tabMonth);
        tabYear = view.findViewById(R.id.tabYear);
        tabAll = view.findViewById(R.id.tabAll);
        tabRange = view.findViewById(R.id.tabRange);

        dateNavContainer = view.findViewById(R.id.dateNavContainer);
        navStandard = view.findViewById(R.id.navStandard);
        navRange = view.findViewById(R.id.navRange);

        tvDateDisplay = view.findViewById(R.id.tvDateDisplay);
        btnPrev = view.findViewById(R.id.btnPrev);
        btnNext = view.findViewById(R.id.btnNext);

        tvRangeStart = view.findViewById(R.id.tvRangeStart);
        tvRangeEnd = view.findViewById(R.id.tvRangeEnd);
    }

    private void setupCharts() {
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setTouchEnabled(true);
        barChart.setScaleEnabled(false);
        barChart.setFitBars(true);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#999999"));
        xAxis.setTextSize(10f);
        xAxis.setGranularity(1f);

        barChart.getAxisRight().setEnabled(false);
        barChart.getAxisLeft().setEnabled(false);
        barChart.getAxisLeft().setDrawGridLines(true);
        barChart.getAxisLeft().setGridColor(Color.parseColor("#F0F0F0"));
        barChart.getAxisLeft().setAxisMinimum(0f);

        lineChart.getDescription().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
        lineChart.setDrawGridBackground(false);
        lineChart.setTouchEnabled(true);

        XAxis lineXAxis = lineChart.getXAxis();
        lineXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        lineXAxis.setDrawGridLines(false);
        lineXAxis.setTextColor(Color.parseColor("#999999"));
        lineXAxis.setTextSize(10f);
        lineXAxis.setGranularity(1f);

        lineChart.getAxisRight().setEnabled(false);
        lineChart.getAxisLeft().setEnabled(false);
        lineChart.getAxisLeft().setDrawGridLines(true);
        lineChart.getAxisLeft().setGridColor(Color.parseColor("#F0F0F0"));
        lineChart.getAxisLeft().setAxisMinimum(0f);

        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setDrawEntryLabels(false);
        pieChart.setTouchEnabled(true);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setHoleRadius(55f);
        pieChart.setTransparentCircleRadius(60f);
        pieChart.setNoDataText("Loading data...");
        pieChart.setExtraOffsets(45.f, 10.f, 45.f, 10.f);
        pieChart.setOnChartValueSelectedListener(this);
    }

    private void setupListeners() {
        View.OnClickListener tabListener = v -> {
            if (v.getId() == R.id.tabWeek) currentMode = MODE_WEEK;
            else if (v.getId() == R.id.tabMonth) currentMode = MODE_MONTH;
            else if (v.getId() == R.id.tabYear) currentMode = MODE_YEAR;
            else if (v.getId() == R.id.tabAll) currentMode = MODE_ALL;
            else if (v.getId() == R.id.tabRange) {
                currentMode = MODE_RANGE;
                initRangeDefaults();
            }
            updateTabStyles();
            updateDateNavigationDisplay();
            filterAndRenderData();
        };

        tabWeek.setOnClickListener(tabListener);
        tabMonth.setOnClickListener(tabListener);
        tabYear.setOnClickListener(tabListener);
        tabAll.setOnClickListener(tabListener);
        tabRange.setOnClickListener(tabListener);

        btnPrev.setOnClickListener(v -> {
            moveAnchor(-1);
            updateDateNavigationDisplay();
            filterAndRenderData();
        });
        btnNext.setOnClickListener(v -> {
            moveAnchor(1);
            updateDateNavigationDisplay();
            filterAndRenderData();
        });

        tvRangeStart.setOnClickListener(v -> showDatePicker(true));
        tvRangeEnd.setOnClickListener(v -> showDatePicker(false));

        btnSwitchChart.setOnClickListener(v -> {
            isLineMode = !isLineMode;

            if (isLineMode) {
                // Switch to Line
                barChart.setVisibility(View.GONE);
                lineChart.setVisibility(View.VISIBLE);
                btnSwitchChart.setImageResource(R.drawable.ic_chart_icon);
            } else {
                // Switch to Bar
                lineChart.setVisibility(View.GONE);
                barChart.setVisibility(View.VISIBLE);
                btnSwitchChart.setImageResource(R.drawable.ic_chart_line);
            }

            filterAndRenderData();
        });
    }

    private void initRangeDefaults() {
        Calendar now = Calendar.getInstance();
        rangeEndCalendar.setTime(now.getTime());
        rangeEndCalendar.set(Calendar.DAY_OF_MONTH, rangeEndCalendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        rangeStartCalendar.setTime(now.getTime());
        rangeStartCalendar.add(Calendar.MONTH, -3);
        rangeStartCalendar.set(Calendar.DAY_OF_MONTH, 1);
    }

    private void moveAnchor(int direction) {
        if (currentMode == MODE_WEEK) {
            anchorCalendar.add(Calendar.WEEK_OF_YEAR, direction);
        } else if (currentMode == MODE_MONTH) {
            anchorCalendar.add(Calendar.MONTH, direction);
        } else if (currentMode == MODE_YEAR) {
            anchorCalendar.add(Calendar.YEAR, direction);
        }
    }

    private void updateDateNavigationDisplay() {
        dateNavContainer.setVisibility(View.VISIBLE);
        navStandard.setVisibility(View.GONE);
        navRange.setVisibility(View.GONE);

        if (currentMode == MODE_ALL) {
            dateNavContainer.setVisibility(View.GONE);
        } else if (currentMode == MODE_RANGE) {
            navRange.setVisibility(View.VISIBLE);
            tvRangeStart.setText(sdfRange.format(rangeStartCalendar.getTime()));
            tvRangeEnd.setText(sdfRange.format(rangeEndCalendar.getTime()));
        } else {
            navStandard.setVisibility(View.VISIBLE);
            String display = "";
            if (currentMode == MODE_WEEK) {
                Calendar startOfWeek = (Calendar) anchorCalendar.clone();
                startOfWeek.set(Calendar.DAY_OF_WEEK, startOfWeek.getFirstDayOfWeek());
                Calendar endOfWeek = (Calendar) startOfWeek.clone();
                endOfWeek.add(Calendar.DAY_OF_WEEK, 6);
                String startStr = sdfWeekRange.format(startOfWeek.getTime());
                String endStr = sdfWeekRange.format(endOfWeek.getTime());
                String yearStr = sdfYear.format(endOfWeek.getTime());
                display = startStr + " ~ " + endStr + ", " + yearStr;
            } else if (currentMode == MODE_MONTH) {
                display = sdfMonth.format(anchorCalendar.getTime());
            } else if (currentMode == MODE_YEAR) {
                display = sdfYear.format(anchorCalendar.getTime());
            }
            tvDateDisplay.setText(display);
        }
    }

    private void showDatePicker(boolean isStart) {
        Calendar target = isStart ? rangeStartCalendar : rangeEndCalendar;
        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    target.set(year, month, dayOfMonth);
                    updateDateNavigationDisplay();
                    filterAndRenderData();
                },
                target.get(Calendar.YEAR),
                target.get(Calendar.MONTH),
                target.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void fetchAllDataOnce() {
        // Fetch Receipts
        ReceiptRepository.getInstance().fetchReceiptsForCurrentUser(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                mAllReceipts.clear();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    try {
                        Receipt receipt = document.toObject(Receipt.class);
                        receipt.setId(document.getId());
                        mAllReceipts.add(receipt);
                    } catch (Exception e) {
                        Log.e("Dashboard", "Receipt Parse error", e);
                    }
                }
                fetchTransactionsAndRender();
            } else {
                Toast.makeText(getContext(), "Failed to load receipts", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchTransactionsAndRender() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("transactions")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    mAllTransactions.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        try {
                            Transaction transaction = doc.toObject(Transaction.class);
                            transaction.setId(doc.getId());
                            // 只获取 Expense 类型的 Transaction
                            if (transaction.isExpense()) {
                                mAllTransactions.add(transaction);
                            }
                        } catch (Exception e) {
                            Log.e("Dashboard", "Transaction Parse error", e);
                        }
                    }

                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Loaded: " + mAllReceipts.size() + " Receipts, " + mAllTransactions.size() + " Transactions", Toast.LENGTH_SHORT).show();
                    }

                    filterAndRenderData();
                })
                .addOnFailureListener(e -> {
                    Log.e("Dashboard", "Failed to load transactions", e);
                    filterAndRenderData();
                });
    }

    private void filterAndRenderData() {
        if (currentMode == MODE_ALL || currentMode == MODE_RANGE) {
            if (cardStatistics != null) cardStatistics.setVisibility(View.GONE);
        } else {
            if (cardStatistics != null) cardStatistics.setVisibility(View.VISIBLE);
        }

        long startTime = 0;
        long endTime = 0;
        Calendar calcCal = (Calendar) anchorCalendar.clone();

        Map<Integer, Double> barEntriesMap = new HashMap<>();
        int barCount = 0;

        if (currentMode == MODE_WEEK) {
            calcCal.set(Calendar.DAY_OF_WEEK, calcCal.getFirstDayOfWeek());
            setStartOfDay(calcCal);
            startTime = calcCal.getTimeInMillis();
            calcCal.add(Calendar.DAY_OF_WEEK, 6);
            setEndOfDay(calcCal);
            endTime = calcCal.getTimeInMillis();
            barCount = 7;
        } else if (currentMode == MODE_MONTH) {
            calcCal.set(Calendar.DAY_OF_MONTH, 1);
            setStartOfDay(calcCal);
            startTime = calcCal.getTimeInMillis();
            barCount = calcCal.getActualMaximum(Calendar.DAY_OF_MONTH);
            calcCal.set(Calendar.DAY_OF_MONTH, barCount);
            setEndOfDay(calcCal);
            endTime = calcCal.getTimeInMillis();
        } else if (currentMode == MODE_YEAR) {
            calcCal.set(Calendar.DAY_OF_YEAR, 1);
            setStartOfDay(calcCal);
            startTime = calcCal.getTimeInMillis();
            calcCal.set(Calendar.DAY_OF_YEAR, calcCal.getActualMaximum(Calendar.DAY_OF_YEAR));
            setEndOfDay(calcCal);
            endTime = calcCal.getTimeInMillis();
            barCount = 12;
        } else if (currentMode == MODE_RANGE) {
            Calendar s = (Calendar) rangeStartCalendar.clone();
            setStartOfDay(s);
            startTime = s.getTimeInMillis();
            Calendar e = (Calendar) rangeEndCalendar.clone();
            setEndOfDay(e);
            endTime = e.getTimeInMillis();
        }

        if (currentMode != MODE_ALL && currentMode != MODE_RANGE) {
            for (int i = 1; i <= barCount; i++) {
                barEntriesMap.put(i, 0.0);
            }
        }

        Map<String, Double> categoryTotals = new HashMap<>();
        for (String key : CATEGORY_COLORS.keySet()) categoryTotals.put(key, 0.0);
        double grandTotal = 0.0;

        Calendar tempCal = Calendar.getInstance();

        for (Receipt receipt : mAllReceipts) {
            long rDate = 0;
            if (receipt.getReceipt() != null) {
                rDate = receipt.getReceipt().getReceiptDateTimestamp();
                if (rDate == 0) rDate = receipt.getReceipt().getDateTimestamp();
            }

            boolean inRange = true;
            if (currentMode != MODE_ALL) {
                if (rDate == 0 || rDate < startTime || rDate > endTime) inRange = false;
            }

            if (inRange) {
                double receiptTotal = 0.0;

                // Items
                if (receipt.getItems() != null) {
                    for (ReceiptItem item : receipt.getItems()) {
                        String cat = item.getCategory();
                        double price = (item.getEffectiveTotalPrice() != null) ? item.getEffectiveTotalPrice() : 0.0;
                        String target = "Other";
                        if (cat != null) {
                            for (String key : CATEGORY_COLORS.keySet()) {
                                if (key.equalsIgnoreCase(cat)) { target = key; break; }
                            }
                        }
                        categoryTotals.put(target, categoryTotals.get(target) + price);
                        grandTotal += price;
                        receiptTotal += price;
                    }
                }
                // Tax
                if (receipt.getReceipt() != null && receipt.getReceipt().getTax() > 0) {
                    double tax = receipt.getReceipt().getTax();
                    categoryTotals.put("Tax", categoryTotals.get("Tax") + tax);
                    grandTotal += tax;
                    receiptTotal += tax;
                }

                if (currentMode != MODE_ALL && currentMode != MODE_RANGE) {
                    addToBarChartMap(barEntriesMap, rDate, tempCal, receiptTotal);
                }
            }
        }

        for (Transaction trans : mAllTransactions) {
            // ... Transaction filtering ...
            long tDate = trans.getTimestamp();

            boolean inRange = true;
            if (currentMode != MODE_ALL) {
                if (tDate == 0 || tDate < startTime || tDate > endTime) inRange = false;
            }

            if (inRange) {
                String target = "Other";
                double amount = trans.getAmount();
                categoryTotals.put(target, categoryTotals.get(target) + amount);
                grandTotal += amount;

                if (currentMode != MODE_ALL && currentMode != MODE_RANGE) {
                    addToBarChartMap(barEntriesMap, tDate, tempCal, amount);
                }
            }
        }

        mGrandTotal = grandTotal;
        updateUI(categoryTotals, grandTotal);

        // --- Render Chart based on Mode ---
        if (currentMode != MODE_ALL && currentMode != MODE_RANGE) {
            if (isLineMode) {
                renderLineChart(barEntriesMap, barCount);
            } else {
                renderBarChart(barEntriesMap, barCount);
            }
        }
    }

    // ... addToBarChartMap & setStart/EndOfDay ...
    private void addToBarChartMap(Map<Integer, Double> map, long timestamp, Calendar cal, double amount) {
        cal.setTimeInMillis(timestamp);
        int key = -1;

        if (currentMode == MODE_WEEK) {
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            int firstDay = anchorCalendar.getFirstDayOfWeek();
            int offset = dayOfWeek - firstDay;
            if (offset < 0) offset += 7;
            key = offset + 1;
        } else if (currentMode == MODE_MONTH) {
            key = cal.get(Calendar.DAY_OF_MONTH);
        } else if (currentMode == MODE_YEAR) {
            key = cal.get(Calendar.MONTH) + 1;
        }

        if (key != -1 && map.containsKey(key)) {
            map.put(key, map.get(key) + amount);
        }
    }

    private void renderBarChart(Map<Integer, Double> dataMap, int count) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> xLabels = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            float val = dataMap.containsKey(i) ? dataMap.get(i).floatValue() : 0f;
            entries.add(new BarEntry(i - 1, val));

            if (currentMode == MODE_WEEK) {
                Calendar c = (Calendar) anchorCalendar.clone();
                c.set(Calendar.DAY_OF_WEEK, c.getFirstDayOfWeek());
                c.add(Calendar.DAY_OF_WEEK, i - 1);
                xLabels.add(new SimpleDateFormat("EEE", Locale.ENGLISH).format(c.getTime()));
            } else if (currentMode == MODE_MONTH) {
                if (i % 5 == 1 || i == count) {
                    xLabels.add(String.valueOf(i));
                } else {
                    xLabels.add("");
                }
            } else if (currentMode == MODE_YEAR) {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.MONTH, i - 1);
                xLabels.add(new SimpleDateFormat("MMM", Locale.ENGLISH).format(c.getTime()).substring(0, 1));
            }
        }

        BarDataSet set = new BarDataSet(entries, "Expense");
        set.setColor(Color.parseColor("#FF5252"));
        set.setHighLightAlpha(0);
        set.setDrawValues(true);
        set.setValueTextSize(10f);
        set.setValueTextColor(Color.parseColor("#999999"));

        set.setValueFormatter(new ValueFormatter() {
            @Override
            public String getBarLabel(BarEntry barEntry) {
                return barEntry.getY() > 0 ? String.format("%.0f", barEntry.getY()) : "";
            }
        });

        BarData data = new BarData(set);
        data.setBarWidth(0.5f);

        barChart.getXAxis().setValueFormatter(null);
        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));
        barChart.getXAxis().setLabelCount(count);

        if (currentMode == MODE_MONTH) {
            barChart.getXAxis().setLabelCount(6, false);
        }

        barChart.getAxisLeft().setAxisMinimum(0f);

        barChart.clear();
        barChart.setData(data);
        barChart.notifyDataSetChanged();
        barChart.invalidate();
        barChart.animateY(500);
    }

    private void renderLineChart(Map<Integer, Double> dataMap, int count) {
        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<String> xLabels = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            float val = dataMap.containsKey(i) ? dataMap.get(i).floatValue() : 0f;
            entries.add(new Entry(i - 1, val));

            if (currentMode == MODE_WEEK) {
                Calendar c = (Calendar) anchorCalendar.clone();
                c.set(Calendar.DAY_OF_WEEK, c.getFirstDayOfWeek());
                c.add(Calendar.DAY_OF_WEEK, i - 1);
                xLabels.add(new SimpleDateFormat("EEE", Locale.ENGLISH).format(c.getTime()));
            } else if (currentMode == MODE_MONTH) {
                if (i % 5 == 1 || i == count) {
                    xLabels.add(String.valueOf(i));
                } else {
                    xLabels.add("");
                }
            } else if (currentMode == MODE_YEAR) {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.MONTH, i - 1);
                xLabels.add(new SimpleDateFormat("MMM", Locale.ENGLISH).format(c.getTime()).substring(0, 1));
            }
        }

        LineDataSet set = new LineDataSet(entries, "Expense");
        set.setColor(Color.parseColor("#FF5252"));
        set.setLineWidth(2f);
        set.setCircleColor(Color.parseColor("#FF5252"));
        set.setCircleRadius(3f);
        set.setDrawCircleHole(false);
        set.setValueTextSize(10f);
        set.setValueTextColor(Color.parseColor("#999999"));
        set.setMode(LineDataSet.Mode.LINEAR);

        set.setValueFormatter(new ValueFormatter() {
            @Override
            public String getPointLabel(Entry entry) {
                return entry.getY() > 0 ? String.format("%.0f", entry.getY()) : "";
            }
        });

        LineData data = new LineData(set);

        lineChart.getXAxis().setValueFormatter(null);
        lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));
        lineChart.getXAxis().setLabelCount(count);

        if (currentMode == MODE_MONTH) {
            lineChart.getXAxis().setLabelCount(6, false);
        }

        lineChart.getAxisLeft().setAxisMinimum(0f);

        lineChart.clear();
        lineChart.setData(data);
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
        lineChart.animateY(500);
    }

    private void setStartOfDay(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private void setEndOfDay(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
    }

    private void updateTabStyles() {
        resetTabStyle(tabWeek);
        resetTabStyle(tabMonth);
        resetTabStyle(tabYear);
        resetTabStyle(tabAll);
        resetTabStyle(tabRange);

        TextView selected = null;
        if (currentMode == MODE_WEEK) selected = tabWeek;
        else if (currentMode == MODE_MONTH) selected = tabMonth;
        else if (currentMode == MODE_YEAR) selected = tabYear;
        else if (currentMode == MODE_ALL) selected = tabAll;
        else if (currentMode == MODE_RANGE) selected = tabRange;

        if (selected != null) {
            selected.setBackgroundResource(R.drawable.bg_tab_selected);
            selected.setTextColor(Color.BLACK);
            selected.setTypeface(null, Typeface.BOLD);
            selected.setElevation(4f);
        }
    }

    private void resetTabStyle(TextView tab) {
        tab.setBackgroundResource(android.R.color.transparent);
        tab.setTextColor(Color.parseColor("#888888"));
        tab.setTypeface(null, Typeface.NORMAL);
        tab.setElevation(0f);
    }

    private void updateUI(Map<String, Double> categoryTotals, double grandTotal) {
        displayList.clear();

        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            String name = entry.getKey();
            double amount = entry.getValue();

            if (amount > 0) {
                double percentVal = (grandTotal > 0) ? (amount / grandTotal) * 100 : 0;
                String percentStr = String.format("%.2f%%", percentVal);
                String amountStr = String.format("$%.2f", amount);
                String color = CATEGORY_COLORS.get(name);

                displayList.add(new CategoryItemDisplay(
                        name,
                        percentStr,
                        amountStr,
                        amount,
                        "",
                        0,
                        (int) percentVal,
                        color
                ));
            }
        }

        Collections.sort(displayList, (o1, o2) -> Double.compare(o2.rawAmount, o1.rawAmount));

        categoryAdapter.notifyDataSetChanged();
        updatePieChartData(displayList, grandTotal);
    }

    private void updatePieChartData(List<CategoryItemDisplay> list, double grandTotal) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        for (CategoryItemDisplay item : list) {
            entries.add(new PieEntry((float) item.rawAmount, item.name, item.rawAmount));
            colors.add(Color.parseColor(item.colorHex));
        }

        PieDataSet dataSet = new PieDataSet(entries, "Categories");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(2f);
        dataSet.setSelectionShift(8f);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setValueLinePart1OffsetPercentage(80.f);
        dataSet.setValueLinePart1Length(0.3f);
        dataSet.setValueLinePart2Length(0.5f);
        dataSet.setValueLineColor(Color.LTGRAY);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new ValueFormatter() {
            @Override
            public String getPieLabel(float value, PieEntry pieEntry) {
                if (pieEntry != null) {
                    return pieEntry.getLabel() + "\n" + String.format("%.1f%%", value);
                }
                return String.format("%.1f%%", value);
            }
        });

        data.setValueTextSize(10f);
        data.setValueTextColor(Color.BLACK);

        pieChart.setData(data);
        updateCenterText("Expense", grandTotal);

        pieChart.highlightValues(null);
        pieChart.invalidate();
        pieChart.animateY(500);
    }

    private void updateCenterText(String label, double amount) {
        String totalStr = String.format("$%.2f", amount);
        SpannableString s = new SpannableString(label + "\n" + totalStr);

        s.setSpan(new RelativeSizeSpan(0.8f), 0, label.length(), 0);
        s.setSpan(new ForegroundColorSpan(Color.GRAY), 0, label.length(), 0);

        int startAmount = label.length() + 1;
        s.setSpan(new StyleSpan(Typeface.BOLD), startAmount, s.length(), 0);
        s.setSpan(new RelativeSizeSpan(1.5f), startAmount, s.length(), 0);
        s.setSpan(new ForegroundColorSpan(Color.BLACK), startAmount, s.length(), 0);

        pieChart.setCenterText(s);
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        if (e == null) return;
        if (e.getData() instanceof Double) {
            double amount = (Double) e.getData();
            updateCenterText("Expense", amount);
        }
    }

    @Override
    public void onNothingSelected() {
        updateCenterText("Expense", mGrandTotal);
    }


    public static class CategoryItemDisplay {
        String name;
        String percent;
        String amountStr;
        double rawAmount;
        String change;
        int count;
        int progress;
        String colorHex;

        public CategoryItemDisplay(String name, String percent, String amountStr, double rawAmount, String change, int count, int progress, String colorHex) {
            this.name = name;
            this.percent = percent;
            this.amountStr = amountStr;
            this.rawAmount = rawAmount;
            this.change = change;
            this.count = count;
            this.progress = progress;
            this.colorHex = colorHex;
        }
    }

    private static class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {
        private List<CategoryItemDisplay> items;
        private OnItemClickListener listener;

        public interface OnItemClickListener {
            void onItemClick(CategoryItemDisplay item);
        }

        public void setOnItemClickListener(OnItemClickListener listener) {
            this.listener = listener;
        }

        public CategoryAdapter(List<CategoryItemDisplay> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_dashboard_category, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CategoryItemDisplay item = items.get(position);

            holder.tvName.setText(item.name);
            holder.tvPercent.setText(item.percent);
            holder.tvAmount.setText(item.amountStr);
            holder.tvCount.setText(item.count > 0 ? String.valueOf(item.count) : "");

            int themeColor = Color.parseColor(item.colorHex);
            String alphaColorHex = "#15" + item.colorHex.substring(1);

            if (holder.imgIcon.getBackground() != null) {
                holder.imgIcon.getBackground().setTint(Color.parseColor(alphaColorHex));
            }

            Integer iconRes = CATEGORY_ICONS.get(item.name);
            if (iconRes != null) holder.imgIcon.setImageResource(iconRes);
            else holder.imgIcon.setImageResource(R.drawable.ic_other);

            holder.imgIcon.setColorFilter(themeColor);

            if (item.change == null || item.change.isEmpty()) {
                holder.tvChange.setVisibility(View.GONE);
            } else {
                holder.tvChange.setVisibility(View.VISIBLE);
                holder.tvChange.setText(item.change);
            }

            holder.progressBar.setProgress(item.progress);
            holder.progressBar.setProgressTintList(ColorStateList.valueOf(themeColor));

            int trackColor = ColorUtils.setAlphaComponent(themeColor, 40);
            holder.progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(trackColor));

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvPercent, tvAmount, tvChange, tvCount;
            ImageView imgIcon;
            ProgressBar progressBar;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvCategoryName);
                tvPercent = itemView.findViewById(R.id.tvPercent);
                tvAmount = itemView.findViewById(R.id.tvPrice);
                tvChange = itemView.findViewById(R.id.tvChange);
                tvCount = itemView.findViewById(R.id.tvCount);
                imgIcon = itemView.findViewById(R.id.imgIcon);
                progressBar = itemView.findViewById(R.id.progressBar);
            }
        }
    }
}