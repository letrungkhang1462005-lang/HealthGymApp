package com.example.healthgymapp.data.local.remote.repository;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface UserDao {

    // Hồ sơ luôn có id = 1 nên REPLACE sẽ ghi đè đúng một dòng
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdateProfile(UserProfile userProfile);

    // Quan sát liên tục: dữ liệu đổi thì giao diện tự cập nhật
    @Query("SELECT * FROM user_profile WHERE id = 1")
    LiveData<UserProfile> getUserProfile();

    // Đọc một lần (PHẢI gọi trong luồng nền, không gọi trên main thread)
    @Query("SELECT * FROM user_profile WHERE id = 1")
    UserProfile getProfileOnce();
}