package com.example.healthgymapp.ui.profile.dashboard.meal;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.healthgymapp.R;
import com.example.healthgymapp.data.local.remote.repository.AppDatabase;
import com.example.healthgymapp.data.local.remote.repository.UserProfile;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Theo dõi đi bộ / chạy bộ: bản đồ (đường phố hoặc vệ tinh), quãng đường theo GPS, đếm bước chân,
 * thời gian, tốc độ và calories ước tính.
 */
public class TrackingActivity extends AppCompatActivity implements LocationListener, SensorEventListener {

    private static final String TAG = "Tracking";

    // Tạm thời bật để hiện thông báo kiểm tra kết nối máy chủ bản đồ (mã HTTP).
    // Khi bản đồ đã chạy ổn, đổi thành false để tắt thông báo.
    private static final boolean DEBUG_TILE_CHECK = true;

    // Các lớp bản đồ có thể chọn
    private static final String[] TILE_NAMES = {
            "Đường phố (Esri)",
            "Đường phố (OpenStreetMap)",
            "Vệ tinh (Esri)"
    };
    // Địa chỉ một ô bản đồ mẫu của từng lớp, dùng để kiểm tra kết nối
    private static final String[] TILE_TEST_URLS = {
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/1/0/0",
            "https://tile.openstreetmap.org/1/0/0.png",
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/1/0/0"
    };
    private static final int DEFAULT_TILE_INDEX = 0; // Esri đường phố

    // Bỏ qua các điểm GPS kém chính xác hoặc nhảy vọt bất thường
    private static final float MAX_ACCURACY_M = 30f;
    private static final float MIN_STEP_M = 3f;
    private static final double MAX_SPEED_MS = 12.0; // ~43 km/h

    // Hệ số MET để ước tính calories
    private static final double MET_WALK = 3.5;
    private static final double MET_RUN = 9.0;

    // Độ dài sải chân ước tính theo chiều cao (dùng khi máy không có cảm biến đếm bước)
    private static final double STRIDE_WALK = 0.415;
    private static final double STRIDE_RUN = 0.65;

    /** Nguồn bản đồ của Esri (URL theo thứ tự z/y/x, khác với kiểu z/x/y mặc định). */
    private static class EsriTileSource extends OnlineTileSourceBase {
        EsriTileSource(String name, String service, String copyright) {
            super(name, 0, 19, 256, ".jpg",
                    new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/" + service + "/MapServer/tile/"},
                    copyright);
        }

        @Override
        public String getTileURLString(long pMapTileIndex) {
            return getBaseUrl()
                    + MapTileIndex.getZoom(pMapTileIndex) + "/"
                    + MapTileIndex.getY(pMapTileIndex) + "/"
                    + MapTileIndex.getX(pMapTileIndex);
        }
    }

    // Giao diện
    private MapView mapView;
    private Marker positionMarker;
    private Polyline routeLine;
    private TextView btnBack, tvTypeWalk, tvTypeRun, btnMapLayer;
    private TextView tvDistance, tvStatus, tvTime, tvSteps, tvCalories, tvSpeed, tvStepSource;
    private TextView btnStartPause, btnStop;
    private int tileSourceIndex = DEFAULT_TILE_INDEX;

    // Hệ thống
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Trạng thái phiên
    private boolean running = false;       // đang trong một phiên (kể cả khi tạm dừng)
    private boolean paused = false;
    private boolean runType = false;       // false = đi bộ, true = chạy bộ
    private boolean gotFix = false;
    private boolean centeredOnce = false;
    private boolean stepSensorActive = false;

    private double distanceMeters = 0;
    private int sensorSteps = 0;
    private float lastCounterValue = -1f;
    private Location lastLocation;
    private long accumulatedMs = 0;
    private long segmentStartMs = 0;

    // Lấy từ hồ sơ
    private volatile double weightKg = 65;
    private volatile double heightCm = 170;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateStats();
            if (running && !paused) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                if (hasFineLocation()) {
                    checkGpsAndBegin();
                } else {
                    Toast.makeText(this, "Cần cấp quyền vị trí chính xác để đo quãng đường", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Cấu hình bản đồ OSM (phải làm trước khi tạo MapView)
        Configuration.getInstance().load(getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE));
        // User-Agent riêng của ứng dụng (máy chủ bản đồ từ chối User-Agent mặc định)
        Configuration.getInstance().setUserAgentValue("HealthGymApp/1.0");
        Configuration.getInstance().setOsmdroidBasePath(new File(getCacheDir(), "osmdroid"));
        Configuration.getInstance().setOsmdroidTileCache(new File(getCacheDir(), "osmdroid/tiles"));

        setContentView(R.layout.activity_tracking);

        mapView = findViewById(R.id.mapView);
        btnBack = findViewById(R.id.btnBack);
        btnMapLayer = findViewById(R.id.btnMapLayer);
        tvTypeWalk = findViewById(R.id.tvTypeWalk);
        tvTypeRun = findViewById(R.id.tvTypeRun);
        tvDistance = findViewById(R.id.tvDistance);
        tvStatus = findViewById(R.id.tvStatus);
        tvTime = findViewById(R.id.tvTime);
        tvSteps = findViewById(R.id.tvSteps);
        tvCalories = findViewById(R.id.tvCalories);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvStepSource = findViewById(R.id.tvStepSource);
        btnStartPause = findViewById(R.id.btnStartPause);
        btnStop = findViewById(R.id.btnStop);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;

        // Lớp bản đồ đã chọn lần trước (nếu có)
        tileSourceIndex = getSharedPreferences("tracking_prefs", MODE_PRIVATE)
                .getInt("tile_source_v2", DEFAULT_TILE_INDEX);
        if (tileSourceIndex < 0 || tileSourceIndex >= TILE_NAMES.length) {
            tileSourceIndex = DEFAULT_TILE_INDEX;
        }

        setupMap();
        loadProfile();
        setType(false);
        updateStats();
        centerOnLastKnownLocation();

        btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        btnMapLayer.setOnClickListener(v -> showLayerDialog());
        tvTypeWalk.setOnClickListener(v -> { if (!running) setType(false); });
        tvTypeRun.setOnClickListener(v -> { if (!running) setType(true); });

        btnStartPause.setOnClickListener(v -> {
            if (!running) {
                onStartClicked();
            } else if (paused) {
                resumeSession();
            } else {
                pauseSession();
            }
        });
        btnStop.setOnClickListener(v -> finishSession());

        // Đang ghi mà bấm Back thì hỏi lại, tránh mất dữ liệu
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (running) {
                    confirmExit();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    // ===================== BẢN ĐỒ =====================

    private void setupMap() {
        applyTileSource(tileSourceIndex);
        mapView.setMultiTouchControls(true);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mapView.getController().setZoom(16.0);
        mapView.getController().setCenter(new GeoPoint(21.0285, 105.8542)); // mặc định Hà Nội, sẽ dời tới vị trí thật

        routeLine = new Polyline();
        routeLine.getOutlinePaint().setColor(Color.parseColor("#FF5722")); // cam nổi bật trên cả bản đồ thường và vệ tinh
        routeLine.getOutlinePaint().setStrokeWidth(12f);
        mapView.getOverlays().add(routeLine);

        // Dòng ghi nguồn bản đồ (bắt buộc phải có)
        mapView.getOverlays().add(new CopyrightOverlay(this));
    }

    private ITileSource buildTileSource(int index) {
        switch (index) {
            case 1:
                return TileSourceFactory.MAPNIK;
            case 2:
                return new EsriTileSource("EsriWorldImagery", "World_Imagery",
                        "Tiles © Esri, Maxar, Earthstar Geographics");
            default:
                return new EsriTileSource("EsriWorldStreetMap", "World_Street_Map",
                        "Tiles © Esri, HERE, Garmin, OpenStreetMap contributors");
        }
    }

    private void applyTileSource(int index) {
        mapView.setTileSource(buildTileSource(index));
        mapView.invalidate();
        if (DEBUG_TILE_CHECK) {
            testTileServer(index);
        }
    }

    private void showLayerDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Lớp bản đồ")
                .setSingleChoiceItems(TILE_NAMES, tileSourceIndex, (dialog, which) -> {
                    tileSourceIndex = which;
                    getSharedPreferences("tracking_prefs", MODE_PRIVATE)
                            .edit().putInt("tile_source_v2", which).apply();
                    applyTileSource(which);
                    dialog.dismiss();
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    /** Kiểm tra máy chủ bản đồ có trả về ô bản đồ không, hiện mã HTTP để biết nguyên nhân khi bản đồ bị xám. */
    private void testTileServer(int index) {
        final String urlString = TILE_TEST_URLS[index];
        final String name = TILE_NAMES[index];
        new Thread(() -> {
            String result;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(urlString).openConnection();
                connection.setRequestProperty("User-Agent", Configuration.getInstance().getUserAgentValue());
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                result = "HTTP " + connection.getResponseCode();
            } catch (Exception e) {
                result = e.getClass().getSimpleName() + ": " + e.getMessage();
            } finally {
                if (connection != null) connection.disconnect();
            }
            final String message = "Kiểm tra " + name + ": " + result;
            Log.i(TAG, message);
            runOnUiThread(() -> Toast.makeText(TrackingActivity.this, message, Toast.LENGTH_LONG).show());
        }).start();
    }

    private void showMarker(GeoPoint point) {
        if (positionMarker == null) {
            positionMarker = new Marker(mapView);
            positionMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            positionMarker.setTitle("Vị trí của bạn");
            mapView.getOverlays().add(positionMarker);
        }
        positionMarker.setPosition(point);
        if (!centeredOnce) {
            mapView.getController().setZoom(17.0);
            mapView.getController().setCenter(point);
            centeredOnce = true;
        }
        mapView.invalidate();
    }

    /** Dời bản đồ tới vị trí đã biết gần nhất (nếu đã có quyền). */
    private void centerOnLastKnownLocation() {
        if (!hasFineLocation() || locationManager == null) return;
        try {
            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null) {
                last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (last != null) {
                showMarker(new GeoPoint(last.getLatitude(), last.getLongitude()));
            }
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "Không lấy được vị trí gần nhất", e);
        }
    }

    // ===================== QUYỀN & BẮT ĐẦU =====================

    private boolean hasFineLocation() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasActivityRecognition() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void onStartClicked() {
        List<String> need = new ArrayList<>();
        if (!hasFineLocation()) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!hasActivityRecognition()) {
            need.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (need.isEmpty()) {
            checkGpsAndBegin();
        } else {
            permissionLauncher.launch(need.toArray(new String[0]));
        }
    }

    private void checkGpsAndBegin() {
        if (locationManager == null || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            new AlertDialog.Builder(this)
                    .setTitle("Hãy bật GPS")
                    .setMessage("Cần bật định vị (GPS) để đo quãng đường.")
                    .setPositiveButton("Mở cài đặt", (d, w) ->
                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("Hủy", null)
                    .show();
            return;
        }
        beginSession();
    }

    // ===================== PHIÊN VẬN ĐỘNG =====================

    private void beginSession() {
        running = true;
        paused = false;
        gotFix = false;
        distanceMeters = 0;
        sensorSteps = 0;
        lastCounterValue = -1f;
        lastLocation = null;
        accumulatedMs = 0;
        segmentStartMs = SystemClock.elapsedRealtime();
        routeLine.setPoints(new ArrayList<GeoPoint>());

        if (!startLocationUpdates()) {
            running = false;
            Toast.makeText(this, "Không bật được định vị", Toast.LENGTH_LONG).show();
            return;
        }
        registerStepSensor();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        btnStartPause.setText("Tạm dừng");
        btnStop.setVisibility(View.VISIBLE);
        tvTypeWalk.setAlpha(0.5f);
        tvTypeRun.setAlpha(0.5f);
        tvStatus.setText("Đang chờ tín hiệu GPS...");
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void pauseSession() {
        accumulatedMs += SystemClock.elapsedRealtime() - segmentStartMs;
        paused = true;
        lastLocation = null; // lúc tiếp tục sẽ không cộng quãng đường nhảy từ điểm dừng
        handler.removeCallbacks(ticker);
        btnStartPause.setText("Tiếp tục");
        tvStatus.setText("Tạm dừng");
        updateStats();
    }

    private void resumeSession() {
        paused = false;
        segmentStartMs = SystemClock.elapsedRealtime();
        btnStartPause.setText("Tạm dừng");
        tvStatus.setText(gotFix ? "Đang ghi hoạt động" : "Đang chờ tín hiệu GPS...");
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void finishSession() {
        if (!running) return;
        if (!paused) {
            accumulatedMs += SystemClock.elapsedRealtime() - segmentStartMs;
        }
        running = false;
        paused = false;
        stopUpdates();
        handler.removeCallbacks(ticker);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        btnStartPause.setText("Bắt đầu");
        btnStop.setVisibility(View.GONE);
        tvTypeWalk.setAlpha(1f);
        tvTypeRun.setAlpha(1f);
        tvStatus.setText("Đã kết thúc");
        updateStats();

        long sec = elapsedSeconds();
        double km = distanceMeters / 1000.0;
        double hours = sec / 3600.0;
        String summary = String.format(Locale.getDefault(),
                "Quãng đường: %.2f km\nThời gian: %s\nBước chân: %,d\nCalories (ước tính): %d kcal\nTốc độ trung bình: %.1f km/h",
                km, formatTime(sec), currentSteps(), calories(sec), hours > 0 ? km / hours : 0.0);
        new AlertDialog.Builder(this)
                .setTitle(runType ? "Hoàn thành buổi chạy" : "Hoàn thành buổi đi bộ")
                .setMessage(summary)
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle("Thoát và hủy phiên?")
                .setMessage("Bạn đang ghi hoạt động. Thoát bây giờ sẽ mất dữ liệu của phiên này.")
                .setPositiveButton("Thoát", (d, w) -> {
                    running = false;
                    stopUpdates();
                    finish();
                })
                .setNegativeButton("Tiếp tục", null)
                .show();
    }

    // ===================== GPS & CẢM BIẾN =====================

    private boolean startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, MIN_STEP_M, this);
            return true;
        } catch (SecurityException | IllegalArgumentException e) {
            Log.e(TAG, "Không đăng ký được GPS", e);
            return false;
        }
    }

    private void registerStepSensor() {
        stepSensorActive = false;
        if (stepSensor != null && hasActivityRecognition()) {
            stepSensorActive = sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
        }
        tvStepSource.setText(stepSensorActive
                ? "Bước chân: cảm biến của máy"
                : "Bước chân: ước tính theo quãng đường");
    }

    private void stopUpdates() {
        try {
            if (locationManager != null) locationManager.removeUpdates(this);
        } catch (SecurityException e) {
            Log.w(TAG, "removeUpdates", e);
        }
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    @Override
    public void onLocationChanged(@NonNull Location loc) {
        if (loc.hasAccuracy() && loc.getAccuracy() > MAX_ACCURACY_M) return;

        GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
        showMarker(point);

        if (!running) return;
        if (!gotFix) {
            gotFix = true;
            if (!paused) tvStatus.setText("Đang ghi hoạt động");
        }
        if (paused) return;

        if (lastLocation == null) {
            lastLocation = loc;
            routeLine.addPoint(point);
        } else {
            float d = lastLocation.distanceTo(loc);
            double dt = (loc.getElapsedRealtimeNanos() - lastLocation.getElapsedRealtimeNanos()) / 1e9;
            float threshold = Math.max(MIN_STEP_M, loc.hasAccuracy() ? loc.getAccuracy() / 2f : 0f);
            boolean jump = dt > 0 && d / dt > MAX_SPEED_MS;
            if (d >= threshold && !jump) {
                distanceMeters += d;
                lastLocation = loc;
                routeLine.addPoint(point);
            }
        }
        mapView.getController().animateTo(point);
        mapView.invalidate();
        updateStats();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float value = event.values[0];
        if (lastCounterValue >= 0 && running && !paused && value >= lastCounterValue) {
            sensorSteps += (int) (value - lastCounterValue);
        }
        lastCounterValue = value;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    // Bắt buộc phải có với Android cũ (< API 30)
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    public void onProviderEnabled(@NonNull String provider) { }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        if (running) {
            Toast.makeText(this, "GPS đã bị tắt, hãy bật lại để tiếp tục đo", Toast.LENGTH_LONG).show();
        }
    }

    // ===================== SỐ LIỆU =====================

    private void setType(boolean run) {
        runType = run;
        styleToggle(tvTypeWalk, !run);
        styleToggle(tvTypeRun, run);
        updateStats();
    }

    private void styleToggle(TextView view, boolean selected) {
        if (selected) {
            view.setBackgroundResource(R.drawable.bg_gender_selected);
            view.setTextColor(Color.WHITE);
        } else {
            view.setBackgroundResource(0);
            view.setTextColor(Color.parseColor("#7A9187"));
        }
    }

    private long elapsedSeconds() {
        long ms = accumulatedMs;
        if (running && !paused) {
            ms += SystemClock.elapsedRealtime() - segmentStartMs;
        }
        return ms / 1000;
    }

    private int currentSteps() {
        if (stepSensorActive) return sensorSteps;
        double stride = heightCm / 100.0 * (runType ? STRIDE_RUN : STRIDE_WALK);
        return stride > 0 ? (int) (distanceMeters / stride) : 0;
    }

    private int calories(long seconds) {
        double met = runType ? MET_RUN : MET_WALK;
        return (int) Math.round(met * weightKg * (seconds / 3600.0));
    }

    private String formatTime(long sec) {
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
    }

    private void updateStats() {
        long sec = elapsedSeconds();
        double km = distanceMeters / 1000.0;
        double hours = sec / 3600.0;

        tvDistance.setText(String.format(Locale.getDefault(), "%.2f", km));
        tvTime.setText(formatTime(sec));
        tvSteps.setText(String.format(Locale.getDefault(), "%,d", currentSteps()));
        tvCalories.setText(String.valueOf(calories(sec)));
        tvSpeed.setText(String.format(Locale.getDefault(), "%.1f", hours > 0 ? km / hours : 0.0));
    }

    /** Lấy cân nặng, chiều cao từ hồ sơ trong Room để tính calories và độ dài sải chân. */
    private void loadProfile() {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                UserProfile profile = db.userDao().getProfileOnce();
                if (profile != null) {
                    weightKg = profile.getWeight();
                    heightCm = profile.getHeight();
                    runOnUiThread(this::updateStats);
                }
            } catch (Exception e) {
                Log.e(TAG, "Không đọc được hồ sơ", e);
            }
        });
    }

    // ===================== VÒNG ĐỜI =====================

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (running && !paused) {
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopUpdates();
        handler.removeCallbacks(ticker);
        mapView.onDetach();
    }
}