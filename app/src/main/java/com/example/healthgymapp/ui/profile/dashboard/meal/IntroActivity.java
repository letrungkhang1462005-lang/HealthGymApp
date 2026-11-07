package com.example.healthgymapp.ui.profile.dashboard.meal;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.healthgymapp.R;

public class IntroActivity extends AppCompatActivity {

    // Dùng chung với ProfileActivity: sau khi lưu hồ sơ thì đặt KEY_PROFILE_DONE = true
    public static final String PREFS_NAME = "app_prefs";
    public static final String KEY_PROFILE_DONE = "profile_done";

    private TextView btnNextArrow;
    private TextView tvNewUser;
    private Button btnStart;
    private View bottomBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        btnNextArrow = findViewById(R.id.btnNextArrow);
        btnStart = findViewById(R.id.btnStart);
        tvNewUser = findViewById(R.id.tvNewUser);
        bottomBar = findViewById(R.id.bottomBar);

        // Cụm nút ở đáy không bị thanh điều hướng của hệ thống che (màn hình edge-to-edge)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootIntroLayout), (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) bottomBar.getLayoutParams();
            lp.bottomMargin = bars.bottom;
            bottomBar.setLayoutParams(lp);
            return windowInsets;
        });

        // Bắt đầu / mũi tên: tiếp tục với hồ sơ cũ (form điền sẵn), người dùng có thể chỉnh sửa rồi lưu
        btnStart.setOnClickListener(v -> openProfile(false));
        btnNextArrow.setOnClickListener(v -> openProfile(false));

        // Người mới: mở form trống để nhập dữ liệu mới
        tvNewUser.setOnClickListener(v -> onNewUserClicked());
    }

    private boolean hasSavedProfile() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_PROFILE_DONE, false);
    }

    private void onNewUserClicked() {
        if (!hasSavedProfile()) {
            openProfile(true);
            return;
        }
        // Đã có hồ sơ: hỏi lại để tránh bấm nhầm. Dữ liệu cũ chỉ bị thay khi người dùng bấm Lưu.
        new AlertDialog.Builder(this)
                .setTitle("Nhập thông tin mới?")
                .setMessage("Hồ sơ hiện tại sẽ được thay bằng thông tin mới khi bạn bấm Lưu.")
                .setPositiveButton("Tiếp tục", (dialog, which) -> openProfile(true))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void openProfile(boolean newProfile) {
        Intent intent = new Intent(IntroActivity.this, ProfileActivity.class);
        intent.putExtra(ProfileActivity.EXTRA_NEW_PROFILE, newProfile);
        startActivity(intent);
    }
}