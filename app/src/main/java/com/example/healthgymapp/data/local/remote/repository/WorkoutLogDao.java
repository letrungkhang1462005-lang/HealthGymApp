package com.example.healthgymapp.data.local.remote.repository;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface WorkoutLogDao {

    @Insert
    void insert(WorkoutLog log);

    @Query("DELETE FROM workout_log WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM workout_log WHERE date = :date AND name = :name")
    void deleteByDateAndName(String date, String name);

    // Các bài tập đã chọn trong một ngày (gọi trong luồng nền)
    @Query("SELECT * FROM workout_log WHERE date = :date ORDER BY timestamp ASC")
    List<WorkoutLog> getByDate(String date);

    // Theo dõi các bài tập của một ngày (Trang chủ)
    @Query("SELECT * FROM workout_log WHERE date = :date ORDER BY timestamp ASC")
    LiveData<List<WorkoutLog>> observeByDate(String date);

    // Theo dõi các bài tập trong khoảng ngày, mới nhất trước (Lịch sử)
    @Query("SELECT * FROM workout_log WHERE date >= :start AND date <= :end "
            + "ORDER BY date DESC, timestamp DESC")
    LiveData<List<WorkoutLog>> observeRange(String start, String end);
}
