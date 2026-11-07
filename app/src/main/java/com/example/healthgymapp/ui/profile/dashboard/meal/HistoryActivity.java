package com.example.healthgymapp.ui.profile.dashboard.meal;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;

import com.example.healthgymapp.R;
import com.example.healthgymapp.data.local.remote.repository.AppDatabase;
import com.example.healthgymapp.data.local.remote.repository.UserProfile;
import com.example.healthgymapp.data.local.remote.repository.WeightRecord;
import com.example.healthgymapp.data.local.remote.repository.WorkoutLog;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private enum Filter { LAST_7_DAYS, THIS_MONTH, CUSTOM }

    private TextView tvTotalWorkoutCount, tvTotalTime, tvTotalCalories;
    private LinearLayout layoutActivityList;
    private TextView tvActivityEmpty;
    private LiveData<List<WorkoutLog>> activityLive;
    private TextView tvSeeAllActivity, tvSeeAllMeals;
    private TextView btnFilter7Days, btnFilterMonth, btnFilterCustom;
    private LinearLayout menuHome, menuHistory, menuAiCoach, menuSettings;

    // Biểu đồ cân nặng
    private LineChart chartWeight;
    private TextView tvWeightEmpty, tvWeightChange;
    private List<WeightRecord> allWeightRecords = new ArrayList<>();
    private Filter currentFilter = Filter.LAST_7_DAYS;
    private String customStartDate; // yyyy-MM-dd, dùng cho bộ lọc "Tùy chọn"
    private TextView activeFilterButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // 1. Ánh xạ các View
        tvTotalWorkoutCount = findViewById(R.id.tvTotalWorkoutCount);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        tvTotalCalories = findViewById(R.id.tvTotalCalories);

        layoutActivityList = findViewById(R.id.layoutActivityList);
        tvActivityEmpty = findViewById(R.id.tvActivityEmpty);
        tvSeeAllActivity = findViewById(R.id.tvSeeAllActivity);
        tvSeeAllMeals = findViewById(R.id.tvSeeAllMeals);

        btnFilter7Days = findViewById(R.id.btnFilter7Days);
        btnFilterMonth = findViewById(R.id.btnFilterMonth);
        btnFilterCustom = findViewById(R.id.btnFilterCustom);
        activeFilterButton = btnFilter7Days;

        chartWeight = findViewById(R.id.chartWeight);
        tvWeightEmpty = findViewById(R.id.tvWeightEmpty);
        tvWeightChange = findViewById(R.id.tvWeightChange);

        menuHome = findViewById(R.id.menuHome);
        menuHistory = findViewById(R.id.menuHistory);
        menuAiCoach = findViewById(R.id.menuAiCoach);
        menuSettings = findViewById(R.id.menuSettings);

        // 2. Hiển thị dữ liệu mặc định (7 ngày qua) từ Room
        refreshActivities();

        // Biểu đồ cân nặng: cấu hình kiểu dáng và theo dõi dữ liệu trong Room
        setupWeightChart();
        AppDatabase.getInstance(getApplicationContext()).weightDao().getAllRecords()
                .observe(this, records -> {
                    allWeightRecords = records != null ? records : new ArrayList<>();
                    refreshWeightChart();
                });

        // 3. Xử lý sự kiện chọn bộ lọc thời gian
        btnFilter7Days.setOnClickListener(v -> {
            setActiveFilter(btnFilter7Days);
            currentFilter = Filter.LAST_7_DAYS;
            refreshActivities();
            refreshWeightChart();
        });

        btnFilterMonth.setOnClickListener(v -> {
            setActiveFilter(btnFilterMonth);
            currentFilter = Filter.THIS_MONTH;
            refreshActivities();
            refreshWeightChart();
        });

        btnFilterCustom.setOnClickListener(v -> {
            TextView previous = activeFilterButton;
            setActiveFilter(btnFilterCustom);
            showDatePickerDialog(previous);
        });

        // 4. Sự kiện bấm nút "Xem tất cả" Hoạt động & Ăn uống
        if (tvSeeAllActivity != null) {
            tvSeeAllActivity.setOnClickListener(v -> startActivity(new Intent(HistoryActivity.this, ActivityListActivity.class)));
        }

        if (tvSeeAllMeals != null) {
            tvSeeAllMeals.setOnClickListener(v -> startActivity(new Intent(HistoryActivity.this, MealListActivity.class)));
        }

        // 5. Điều hướng menu dưới đáy
        if (menuHome != null) {
            menuHome.setOnClickListener(v -> {
                startActivity(new Intent(HistoryActivity.this, DashboardActivity.class));
                finish();
            });
        }

        if (menuAiCoach != null) {
            menuAiCoach.setOnClickListener(v -> {
                startActivity(new Intent(HistoryActivity.this, AiCoachActivity.class));
                finish();
            });
        }

        if (menuSettings != null) {
            menuSettings.setOnClickListener(v -> {
                startActivity(new Intent(HistoryActivity.this, SettingsActivity.class));
                finish();
            });
        }
    }

    // Hàm đổi màu nền và kiểu chữ cho nút bộ lọc đang được chọn
    private void setActiveFilter(TextView selectedButton) {
        // Reset tất cả về trạng thái chưa chọn
        btnFilter7Days.setBackgroundColor(getResources().getColor(android.R.color.transparent));
        btnFilter7Days.setTextColor(getResources().getColor(android.R.color.darker_gray));
        btnFilter7Days.setTypeface(null, android.graphics.Typeface.NORMAL);

        btnFilterMonth.setBackgroundColor(getResources().getColor(android.R.color.transparent));
        btnFilterMonth.setTextColor(getResources().getColor(android.R.color.darker_gray));
        btnFilterMonth.setTypeface(null, android.graphics.Typeface.NORMAL);

        btnFilterCustom.setBackgroundColor(getResources().getColor(android.R.color.transparent));
        btnFilterCustom.setTextColor(getResources().getColor(android.R.color.darker_gray));
        btnFilterCustom.setTypeface(null, android.graphics.Typeface.NORMAL);

        // Áp dụng style cho nút được bấm
        selectedButton.setBackgroundResource(R.drawable.bg_gender_selected);
        selectedButton.setTextColor(getResources().getColor(android.R.color.white));
        selectedButton.setTypeface(null, android.graphics.Typeface.BOLD);
        activeFilterButton = selectedButton;
    }

    // ===================== HOẠT ĐỘNG (workout_log) =====================

    /** Đăng ký lại theo dõi các bài tập trong khoảng thời gian của bộ lọc hiện tại. */
    private void refreshActivities() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String end = sdf.format(new Date());
        String start = rangeStart(sdf);
        if (activityLive != null) activityLive.removeObservers(this);
        activityLive = AppDatabase.getInstance(getApplicationContext())
                .workoutLogDao().observeRange(start, end);
        activityLive.observe(this, this::renderActivities);
    }

    /** Cập nhật 3 thẻ thống kê và danh sách hoạt động, nhóm theo từng ngày. */
    private void renderActivities(List<WorkoutLog> logs) {
        int count = 0, minutes = 0, kcal = 0;
        layoutActivityList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        String today = iso.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, -1);
        String yesterday = iso.format(cal.getTime());

        // Tổng kcal từng ngày (để hiện ở tiêu đề ngày)
        java.util.Map<String, Integer> dayKcal = new java.util.HashMap<>();
        if (logs != null) {
            for (WorkoutLog log : logs) {
                Integer old = dayKcal.get(log.getDate());
                dayKcal.put(log.getDate(), (old == null ? 0 : old) + log.getCalories());
            }
        }

        String lastDate = null;
        if (logs != null) {
            for (WorkoutLog log : logs) { // đã sắp xếp: ngày mới nhất trước
                count++;
                minutes += log.getDurationMin();
                kcal += log.getCalories();

                if (!log.getDate().equals(lastDate)) {
                    lastDate = log.getDate();
                    TextView header = new TextView(this);
                    String label = lastDate.equals(today) ? "Hôm nay"
                            : lastDate.equals(yesterday) ? "Hôm qua" : shortDate(lastDate);
                    header.setText("📅 " + label + "  •  🔥 -" + dayKcal.get(lastDate) + " kcal");
                    header.setTextColor(Color.parseColor("#059669"));
                    header.setTextSize(13f);
                    header.setTypeface(null, android.graphics.Typeface.BOLD);
                    header.setPadding(0, 8, 0, 8);
                    layoutActivityList.addView(header);
                }

                View row = inflater.inflate(R.layout.item_history_activity, layoutActivityList, false);
                ((TextView) row.findViewById(R.id.tvHisEmoji)).setText(log.getEmoji());
                ((TextView) row.findViewById(R.id.tvHisName)).setText(log.getName());
                ((TextView) row.findViewById(R.id.tvHisSub)).setText(
                        "⏱ " + log.getDurationMin() + " phút  •  🔥 -" + log.getCalories() + " kcal");
                layoutActivityList.addView(row);
            }
        }

        tvActivityEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        if (tvTotalWorkoutCount != null) tvTotalWorkoutCount.setText(String.valueOf(count));
        if (tvTotalTime != null) tvTotalTime.setText(String.valueOf(minutes));
        if (tvTotalCalories != null) {
            tvTotalCalories.setText(String.format(Locale.getDefault(), "%,d", kcal));
        }
    }

    // Hiển thị bảng chọn ngày khi bấm "Tùy chọn"
    private void showDatePickerDialog(TextView previousButton) {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, selectedYear, selectedMonth, selectedDay) -> {
            String selectedDate = selectedDay + "/" + (selectedMonth + 1) + "/" + selectedYear;
            Toast.makeText(this, "Xem từ ngày: " + selectedDate, Toast.LENGTH_SHORT).show();

            // Biểu đồ cân nặng: hiển thị từ ngày đã chọn đến hôm nay
            customStartDate = String.format(Locale.US, "%04d-%02d-%02d",
                    selectedYear, selectedMonth + 1, selectedDay);
            currentFilter = Filter.CUSTOM;
            refreshActivities();
            refreshWeightChart();
        }, year, month, day);

        // Bấm ra ngoài/hủy thì trả lại nút bộ lọc đang dùng trước đó
        datePickerDialog.setOnCancelListener(dialog -> setActiveFilter(previousButton));
        datePickerDialog.show();
    }

    // ===================== BIỂU ĐỒ CÂN NẶNG =====================

    private void setupWeightChart() {
        chartWeight.getDescription().setEnabled(false);
        chartWeight.getLegend().setEnabled(false);
        chartWeight.setTouchEnabled(true);
        chartWeight.setPinchZoom(false);
        chartWeight.setScaleEnabled(false);
        chartWeight.setNoDataText("");
        chartWeight.setExtraBottomOffset(4f);

        XAxis xAxis = chartWeight.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.parseColor("#6B7280"));
        xAxis.setTextSize(10f);

        YAxis left = chartWeight.getAxisLeft();
        left.setTextColor(Color.parseColor("#6B7280"));
        left.setTextSize(10f);
        left.setGridColor(Color.parseColor("#E5E7EB"));
        left.setDrawAxisLine(false);

        chartWeight.getAxisRight().setEnabled(false);
    }

    /** Lọc các mốc cân nặng theo bộ lọc đang chọn rồi vẽ lại biểu đồ. */
    private void refreshWeightChart() {
        if (chartWeight == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String end = sdf.format(new Date());
        String start = rangeStart(sdf);

        List<WeightRecord> inRange = new ArrayList<>();
        for (WeightRecord record : allWeightRecords) {
            String date = record.getDate();
            if (date.compareTo(start) >= 0 && date.compareTo(end) <= 0) {
                inRange.add(record);
            }
        }

        if (inRange.isEmpty()) {
            chartWeight.setVisibility(View.INVISIBLE);
            tvWeightEmpty.setVisibility(View.VISIBLE);
            tvWeightChange.setText("");
            return;
        }

        chartWeight.setVisibility(View.VISIBLE);
        tvWeightEmpty.setVisibility(View.GONE);

        int n = inRange.size();
        List<Entry> entries = new ArrayList<>();
        String[] labels = new String[n];
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            WeightRecord record = inRange.get(i);
            entries.add(new Entry(i, record.getWeight()));
            labels[i] = shortDate(record.getDate());
            min = Math.min(min, record.getWeight());
            max = Math.max(max, record.getWeight());
        }

        int green = Color.parseColor("#2BB673");
        LineDataSet dataSet = new LineDataSet(entries, "Cân nặng");
        dataSet.setColor(green);
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleColor(green);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(green);
        dataSet.setFillAlpha(40);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(Color.parseColor("#111827"));
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return formatOneDecimal(value);
            }
        });

        chartWeight.setData(new LineData(dataSet));

        XAxis xAxis = chartWeight.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelCount(Math.min(n, 6));
        xAxis.setAxisMinimum(-0.5f);
        xAxis.setAxisMaximum(n - 0.5f);

        // Chừa khoảng trống trên dưới để một mốc đơn lẻ vẫn hiển thị rõ
        YAxis left = chartWeight.getAxisLeft();
        left.setAxisMinimum(min - 2f);
        left.setAxisMaximum(max + 2f);

        chartWeight.invalidate();

        // Dòng tóm tắt thay đổi so với đầu kỳ
        if (n >= 2) {
            float first = inRange.get(0).getWeight();
            float last = inRange.get(n - 1).getWeight();
            float diff = last - first;
            String change;
            if (Math.abs(diff) < 0.05f) {
                change = "không đổi";
            } else if (diff > 0) {
                change = "+" + formatOneDecimal(diff) + " kg";
            } else {
                change = "-" + formatOneDecimal(Math.abs(diff)) + " kg";
            }
            tvWeightChange.setText("Từ " + formatOneDecimal(first) + " kg → "
                    + formatOneDecimal(last) + " kg (" + change + ")");
        } else {
            tvWeightChange.setText("Hiện tại: " + formatOneDecimal(inRange.get(0).getWeight())
                    + " kg. Cập nhật cân nặng ở các ngày khác để xem xu hướng.");
        }
    }

    /** Ngày bắt đầu (yyyy-MM-dd) của khoảng thời gian đang lọc. */
    private String rangeStart(SimpleDateFormat sdf) {
        if (currentFilter == Filter.CUSTOM && customStartDate != null) {
            return customStartDate;
        }
        Calendar cal = Calendar.getInstance();
        if (currentFilter == Filter.THIS_MONTH) {
            cal.set(Calendar.DAY_OF_MONTH, 1);
        } else {
            cal.add(Calendar.DAY_OF_YEAR, -6); // 7 ngày gồm cả hôm nay
        }
        return sdf.format(cal.getTime());
    }

    /** "2026-10-18" -> "18/10". */
    private String shortDate(String isoDate) {
        if (isoDate.length() >= 10) {
            return isoDate.substring(8, 10) + "/" + isoDate.substring(5, 7);
        }
        return isoDate;
    }

    private String formatOneDecimal(float value) {
        return UserProfile.formatNumber(Math.round(value * 10f) / 10f);
    }
}
