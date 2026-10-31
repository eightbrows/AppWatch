package io.github.eightbrows.appwatch.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import io.github.eightbrows.appwatch.model.AppWarning

/**
 * アプリのデータベース（仕様書 8章）。
 * スキーマは app/schemas に JSON で書き出す。表を変えるときはバージョンを上げ、移行（Migration）を書くこと。
 */
@Database(
    entities = [
        DatasetEntity::class,
        BaselineAppEntity::class,
        AppCurrentEntity::class,
        AppEventEntity::class,
        AppPolicyEntity::class,
        ScanDailyEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppWatchDatabase : RoomDatabase() {
    abstract fun datasetDao(): DatasetDao
    abstract fun appRecordDao(): AppRecordDao
    abstract fun policyDao(): PolicyDao
    abstract fun scanDailyDao(): ScanDailyDao

    companion object {
        const val FILE_NAME = "appwatch.db"

        fun create(context: Context): AppWatchDatabase =
            Room.databaseBuilder(context.applicationContext, AppWatchDatabase::class.java, FILE_NAME).build()
    }
}

/** 列挙型以外で、そのまま保存できない型の変換。 */
class Converters {
    @TypeConverter
    fun warningsToString(warnings: Set<AppWarning>): String = warnings.map { it.name }.sorted().joinToString(",")

    @TypeConverter
    fun stringToWarnings(value: String): Set<AppWarning> =
        value.split(',').filter { it.isNotBlank() }
            .mapNotNullTo(LinkedHashSet()) { name -> AppWarning.entries.firstOrNull { it.name == name } }

    @TypeConverter
    fun stringListToString(values: List<String>): String = values.joinToString(",")

    @TypeConverter
    fun stringToStringList(value: String): List<String> = value.split(',').filter { it.isNotBlank() }
}
