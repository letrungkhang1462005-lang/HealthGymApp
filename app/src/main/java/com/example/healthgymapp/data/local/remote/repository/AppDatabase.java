package com.example.healthgymapp.data.local.remote.repository;


import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// version = 5: thêm bảng workout_log (nhật ký tập luyện)
@Database(entities = {UserProfile.class, WeightRecord.class, HeartRateRecord.class, MealLog.class,
        WorkoutLog.class},
        version = 5, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    // Luồng nền dùng chung để đọc/ghi database (Room không cho truy vấn trên main thread)
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(2);

    // Nâng cấp 2 -> 3: thêm bảng nhịp tim, GIỮ NGUYÊN dữ liệu cũ
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `heart_rate_record` "
                    + "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`timestamp` INTEGER NOT NULL, "
                    + "`bpm` INTEGER NOT NULL)");
        }
    };

    // Nâng cấp 3 -> 4: thêm bảng nhật ký ăn uống, GIỮ NGUYÊN dữ liệu cũ
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `meal_log` "
                    + "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`date` TEXT NOT NULL, "
                    + "`mealType` TEXT NOT NULL, "
                    + "`name` TEXT NOT NULL, "
                    + "`calories` INTEGER NOT NULL, "
                    + "`protein` INTEGER NOT NULL, "
                    + "`carbs` INTEGER NOT NULL, "
                    + "`fat` INTEGER NOT NULL, "
                    + "`timestamp` INTEGER NOT NULL)");
        }
    };

    // Nâng cấp 4 -> 5: thêm bảng nhật ký tập luyện, GIỮ NGUYÊN dữ liệu cũ
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `workout_log` "
                    + "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`date` TEXT NOT NULL, "
                    + "`name` TEXT NOT NULL, "
                    + "`emoji` TEXT NOT NULL, "
                    + "`durationMin` INTEGER NOT NULL, "
                    + "`calories` INTEGER NOT NULL, "
                    + "`timestamp` INTEGER NOT NULL)");
        }
    };

    public abstract UserDao userDao();

    public abstract WeightDao weightDao();

    public abstract HeartRateDao heartRateDao();

    public abstract MealLogDao mealLogDao();

    public abstract WorkoutLogDao workoutLogDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "health_gym_db")
                            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
