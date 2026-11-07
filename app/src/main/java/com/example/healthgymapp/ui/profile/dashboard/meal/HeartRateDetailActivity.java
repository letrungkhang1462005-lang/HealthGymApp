package com.example.healthgymapp.ui.profile.dashboard.meal;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.healthgymapp.R;
import com.example.healthgymapp.data.local.remote.repository.AppDatabase;
import com.example.healthgymapp.data.local.remote.repository.HeartRateRecord;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HeartRateDetailActivity extends AppCompatActivity {

    private static final int TAB_DAY = 1;
    private static final int TAB_WEEK = 2;
    private static final int TAB_MONTH = 3;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private TextView btnBack, tabDay, tabWeek, tabMonth, btnAddHeartRate;
    private TextView tvCurrentBpm, tvBpmUpdated, tvBpmStatus;
    private TextView tvDetailTitle, tvAvgBpm, tvMaxBpm, tvMinBpm, tvChartEmpty;
    private LineChart chartHeartRate;
    private LinearLayout menuHome, menuHistory, menuAiCoach, menuSettings;

    private int currentTab = TAB_DAY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate_detail);

        // 1. Ánh xạ các thành phần giao diện
        btnBack = findViewById(R.id.btnBack);
        tabDay = findViewById(R.id.tabDay);
        tabWeek = findViewById(R.id.tabWeek);
        tabMonth = findViewById(R.id.tabMonth);
        btnAddHeartRate = findViewById(R.id.btnAddHeartRate);

        tvCurrentBpm = findViewById(R.id.tvCurrentBpm);
        tvBpmUpdated = findViewById(R.id.tvBpmUpdated);
        tvBpmStatus = findViewById(R.id.tvBpmStatus);
        tvDetailTitle = findViewById(R.id.tvDetailTitle);
        tvAvgBpm = findViewById(R.id.tvAvgBpm);
        tvMaxBpm = findViewById(R.id.tvMaxBpm);
        tvMinBpm = findViewById(R.id.tvMinBpm);
        tvChartEmpty = findViewById(R.id.tvChartEmpty);
        chartHeartRate = findViewById(R.id.chartHeartRate);

        menuHome = findViewById(R.id.menuHome);
        menuHistory = findViewById(R.id.menuHistory);
        menuAiCoach = findViewById(R.id.menuAiCoach);
        menuSettings = findViewById(R.id.menuSettings);

        setupChart();

        // 2. Nút quay lại (Mũi tên ←)
        btnBack.setOnClickListener(v -> finish());

        // 3. Chọn Tab Ngày, Tuần, Tháng
        tabDay.setOnClickListener(v -> selectTab(TAB_DAY));
        tabWeek.setOnClickListener(v -> selectTab(TAB_WEEK));
        tabMonth.setOnClickListener(v -> selectTab(TAB_MONTH));

        // 4. Ghi nhịp tim mới
        btnAddHeartRate.setOnClickListener(v -> showAddHeartRateDialog());

        // 5. Thanh điều hướng dưới đáy
        menuHome.setOnClickListener(v -> finish());
        menuHistory.setOnClickListener(v ->
                startActivity(new Intent(HeartRateDetailActivity.this, HistoryActivity.class)));
        menuAiCoach.setOnClickListener(v ->
                startActivity(new Intent(HeartRateDetailActivity.this, AiCoachActivity.class)));
        menuSettings.setOnClickListener(v ->
                startActivity(new Intent(HeartRateDetailActivity.this, SettingsActivity.class)));

        selectTab(TAB_DAY);
    }

    // ===================== TAB & TẢI DỮ LIỆU =====================

    private void selectTab(int tab) {
        currentTab = tab;
        styleTab(tabDay, tab == TAB_DAY);
        styleTab(tabWeek, tab == TAB_WEEK);
        styleTab(tabMonth, tab == TAB_MONTH);
        loadData();
    }

    private void styleTab(TextView tabView, boolean selected) {
        if (selected) {
            tabView.setBackgroundResource(R.drawable.bg_gender_selected);
            tabView.setTextColor(Color.WHITE);
        } else {
            tabView.setBackgroundResource(0);
            tabView.setTextColor(Color.parseColor("#7A9187"));
        }
    }

    /** Đọc nhịp tim trong khoảng thời gian của tab đang chọn (luồng nền), rồi hiển thị. */
    private void loadData() {
        final int tab = currentTab;
        final long start = rangeStart(tab);
        final long end = startOfToday() + DAY_MS;
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<HeartRateRecord> records = db.heartRateDao().getBetween(start, end);
                HeartRateRecord latest = db.heartRateDao().getLatest();
                runOnUiThread(() -> showData(tab, start, records, latest));
            } catch (Exception e) {
                Log.e("HeartRateDetail", "Không đọc được dữ liệu nhịp tim", e);
            }
        });
    }

    private long startOfToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** Thời điểm bắt đầu của khoảng: hôm nay, 7 ngày qua hoặc 30 ngày qua. */
    private long rangeStart(int tab) {
        long today = startOfToday();
        if (tab == TAB_WEEK) return today - 6 * DAY_MS;
        if (tab == TAB_MONTH) return today - 29 * DAY_MS;
        return today;
    }

    private void showData(int tab, long start, List<HeartRateRecord> records, HeartRateRecord latest) {
        if (tab != currentTab) return; // người dùng đã chuyển tab khác, bỏ kết quả cũ

        // Nhịp tim gần nhất
        if (latest == null) {
            tvCurrentBpm.setText("--");
            tvBpmUpdated.setText("Chưa có dữ liệu");
            tvBpmStatus.setText("");
        } else {
            tvCurrentBpm.setText(String.valueOf(latest.getBpm()));
            tvBpmUpdated.setText("Lúc " + new SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
                    .format(new Date(latest.getTimestamp())));
            applyStatus(latest.getBpm());
        }

        // Chi tiết: trung bình, cao nhất, thấp nhất trong khoảng đang xem
        tvDetailTitle.setText(tab == TAB_DAY ? "Chi tiết hôm nay"
                : tab == TAB_WEEK ? "Chi tiết 7 ngày qua" : "Chi tiết 30 ngày qua");
        if (records.isEmpty()) {
            tvAvgBpm.setText("--");
            tvMaxBpm.setText("--");
            tvMinBpm.setText("--");
        } else {
            int sum = 0;
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (HeartRateRecord r : records) {
                sum += r.getBpm();
                min = Math.min(min, r.getBpm());
                max = Math.max(max, r.getBpm());
            }
            tvAvgBpm.setText(Math.round((float) sum / records.size()) + " bpm");
            tvMaxBpm.setText(max + " bpm");
            tvMinBpm.setText(min + " bpm");
        }

        drawChart(tab, start, records);
    }

    /** Phân loại nhịp tim (chỉ mang tính tham khảo). */
    private void applyStatus(int bpm) {
        if (bpm < 60) {
            tvBpmStatus.setText("Thấp");
            tvBpmStatus.setTextColor(Color.parseColor("#D97706"));
        } else if (bpm <= 100) {
            tvBpmStatus.setText("Bình thường");
            tvBpmStatus.setTextColor(Color.parseColor("#059669"));
        } else {
            tvBpmStatus.setText("Cao");
            tvBpmStatus.setTextColor(Color.parseColor("#D97706"));
        }
    }

    // ===================== BIỂU ĐỒ =====================

    private void setupChart() {
        chartHeartRate.getDescription().setEnabled(false);
        chartHeartRate.getLegend().setEnabled(false);
        chartHeartRate.setTouchEnabled(true);
        chartHeartRate.setPinchZoom(false);
        chartHeartRate.setScaleEnabled(false);
        chartHeartRate.setNoDataText("");
        chartHeartRate.setExtraBottomOffset(4f);

        XAxis xAxis = chartHeartRate.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.parseColor("#94A3B8"));
        xAxis.setTextSize(10f);

        YAxis left = chartHeartRate.getAxisLeft();
        left.setTextColor(Color.parseColor("#94A3B8"));
        left.setTextSize(10f);
        left.setGridColor(Color.parseColor("#E2E8F0"));
        left.setDrawAxisLine(false);

        chartHeartRate.getAxisRight().setEnabled(false);
    }

    private void drawChart(int tab, long start, List<HeartRateRecord> records) {
        List<Entry> entries = new ArrayList<>();
        int minBpm = Integer.MAX_VALUE;
        int maxBpm = Integer.MIN_VALUE;

        if (tab == TAB_DAY) {
            // Trục X là giờ trong ngày (0 -> 24)
            for (HeartRateRecord r : records) {
                entries.add(new Entry((r.getTimestamp() - start) / 3600000f, r.getBpm()));
                minBpm = Math.min(minBpm, r.getBpm());
                maxBpm = Math.max(maxBpm, r.getBpm());
            }
        } else {
            // Tuần/Tháng: mỗi ngày một điểm là nhịp tim trung bình của ngày đó
            int days = tab == TAB_WEEK ? 7 : 30;
            int[] sum = new int[days];
            int[] count = new int[days];
            for (HeartRateRecord r : records) {
                int index = (int) ((r.getTimestamp() - start) / DAY_MS);
                if (index >= 0 && index < days) {
                    sum[index] += r.getBpm();
                    count[index]++;
                }
            }
            for (int i = 0; i < days; i++) {
                if (count[i] > 0) {
                    float avg = Math.round((float) sum[i] / count[i]);
                    entries.add(new Entry(i, avg));
                    minBpm = Math.min(minBpm, (int) avg);
                    maxBpm = Math.max(maxBpm, (int) avg);
                }
            }
        }

        if (entries.isEmpty()) {
            chartHeartRate.setVisibility(View.INVISIBLE);
            tvChartEmpty.setVisibility(View.VISIBLE);
            return;
        }
        chartHeartRate.setVisibility(View.VISIBLE);
        tvChartEmpty.setVisibility(View.GONE);

        int green = Color.parseColor("#059669");
        LineDataSet dataSet = new LineDataSet(entries, "Nhịp tim");
        dataSet.setColor(green);
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleColor(green);
        dataSet.setCircleRadius(3.5f);
        dataSet.setDrawCircleHole(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(green);
        dataSet.setFillAlpha(35);
        dataSet.setDrawValues(entries.size() <= 10); // ít điểm thì hiện số, nhiều điểm thì ẩn cho gọn
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(Color.parseColor("#1A3E30"));
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf(Math.round(value));
            }
        });
        chartHeartRate.setData(new LineData(dataSet));

        // Trục X theo từng tab
        XAxis xAxis = chartHeartRate.getXAxis();
        if (tab == TAB_DAY) {
            xAxis.setAxisMinimum(0f);
            xAxis.setAxisMaximum(24f);
            xAxis.setLabelCount(5, true); // 00:00, 06:00, 12:00, 18:00, 24:00
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.format(Locale.getDefault(), "%02d:00", (int) value);
                }
            });
        } else {
            final int days = tab == TAB_WEEK ? 7 : 30;
            final SimpleDateFormat dayFormat = new SimpleDateFormat("dd/MM", Locale.getDefault());
            xAxis.setAxisMinimum(-0.5f);
            xAxis.setAxisMaximum(days - 0.5f);
            xAxis.setLabelCount(tab == TAB_WEEK ? 7 : 6, false);
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return dayFormat.format(new Date(start + Math.round(value) * DAY_MS));
                }
            });
        }

        // Trục Y tự co giãn theo dữ liệu
        YAxis left = chartHeartRate.getAxisLeft();
        left.setAxisMinimum(Math.max(30f, minBpm - 15f));
        left.setAxisMaximum(maxBpm + 15f);

        chartHeartRate.invalidate();
    }

    // ===================== GHI NHỊP TIM =====================

    private void showAddHeartRateDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Ví dụ: 72");
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});

        FrameLayout container = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Ghi nhịp tim")
                .setMessage("Nhập nhịp tim hiện tại của bạn (lần/phút)")
                .setView(container)
                .setPositiveButton("Lưu", null) // xử lý bên dưới để kiểm tra dữ liệu mà không đóng hộp thoại
                .setNegativeButton("Hủy", null)
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    int bpm;
                    try {
                        bpm = Integer.parseInt(input.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        input.setError("Vui lòng nhập một số");
                        return;
                    }
                    if (bpm < 30 || bpm > 220) {
                        input.setError("Nhịp tim nên từ 30 đến 220");
                        return;
                    }
                    saveHeartRate(bpm);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void saveHeartRate(int bpm) {
        HeartRateRecord record = new HeartRateRecord(System.currentTimeMillis(), bpm);
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                db.heartRateDao().insert(record);
                runOnUiThread(() -> {
                    Toast.makeText(HeartRateDetailActivity.this, "Đã ghi nhịp tim " + bpm + " bpm", Toast.LENGTH_SHORT).show();
                    loadData();
                });
            } catch (Exception e) {
                Log.e("HeartRateDetail", "Không lưu được nhịp tim", e);
                runOnUiThread(() -> Toast.makeText(HeartRateDetailActivity.this,
                        "Không lưu được: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
}