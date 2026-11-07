package com.example.healthgymapp.data.local.remote.repository;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MealLogDao {

    @Insert
    void insert(MealLog log);

    // Các bữa đã lưu trong một ngày (gọi trong luồng nền)
    @Query("SELECT * FROM meal_log WHERE date = :date ORDER BY timestamp ASC")
    List<MealLog> getByDate(String date);

    // Tổng calo đã nạp trong một ngày (gọi trong luồng nền)
    @Query("SELECT IFNULL(SUM(calories), 0) FROM meal_log WHERE date = :date")
    int sumCaloriesByDate(String date);

    // Toàn bộ nhật ký, mới nhất trước (dùng cho màn Lịch sử / danh sách bữa ăn)
    @Query("SELECT * FROM meal_log ORDER BY timestamp DESC")
    LiveData<List<MealLog>> getAll();
}