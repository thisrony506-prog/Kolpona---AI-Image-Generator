package com.kolpona.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GeneratedImageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KolponaDatabase : RoomDatabase() {
    abstract fun generatedImageDao(): GeneratedImageDao

    companion object {
        fun create(context: Context): KolponaDatabase =
            Room.databaseBuilder(context, KolponaDatabase::class.java, "kolpona.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
