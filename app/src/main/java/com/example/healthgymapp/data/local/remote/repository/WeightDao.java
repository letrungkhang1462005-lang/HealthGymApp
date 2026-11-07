package com.example.healthgymapp.data.local.remote.repository;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface WeightDao {

    // Cùng một ngày thì ghi đè mốc cũ
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(WeightRecord record);

    // Dùng cho màn Lịch sử / biểu đồ: tự cập nhật khi có mốc mới, sắp xếp theo ngày tăng dần
    @Query("SELECT * FROM weight_record ORDER BY date ASC")
    LiveData<List<WeightRecord>> getAllRecords();

    // Đọc một lần (gọi trong luồng nền)
    @Query("SELECT * FROM weight_record ORDER BY date ASC")
    List<WeightRecord> getAllOnce();
}