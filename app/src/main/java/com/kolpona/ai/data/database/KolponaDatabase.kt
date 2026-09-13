package com.kolpona.ai.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GeneratedImageEntity::class, ChatSessionEntity::class, ChatMessageEntity::class],
    version = 4,
    exportSchema = false
)
abstract class KolponaDatabase : RoomDatabase() {
    abstract fun generatedImageDao(): GeneratedImageDao
    abstract fun chatDao(): ChatDao

    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE generated_images ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chat_sessions ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context): KolponaDatabase =
            Room.databaseBuilder(context, KolponaDatabase::class.java, "kolpona.db")
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
