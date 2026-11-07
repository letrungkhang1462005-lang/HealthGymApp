package com.example.healthgymapp.data.local.remote.repository;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HeartRateDao {

    @Insert
    void insert(HeartRateRecord record);

    // Các lần ghi trong khoảng [from, to), sắp xếp theo thời gian tăng dần (gọi trong luồng nền)
    @Query("SELECT * FROM heart_rate_record WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp ASC")
    List<HeartRateRecord> getBetween(long from, long to);

    // Lần ghi gần nhất (gọi trong luồng nền)
    @Query("SELECT * FROM heart_rate_record ORDER BY timestamp DESC LIMIT 1")
    HeartRateRecord getLatest();
}