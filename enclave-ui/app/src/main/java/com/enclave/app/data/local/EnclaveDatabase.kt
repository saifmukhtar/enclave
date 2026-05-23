package com.enclave.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add readAt column to messages for read receipts
        db.execSQL("ALTER TABLE messages ADD COLUMN readAt INTEGER NOT NULL DEFAULT 0")

        // Create user_profiles table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS user_profiles (
                userId TEXT NOT NULL PRIMARY KEY,
                username TEXT NOT NULL DEFAULT '',
                displayName TEXT NOT NULL DEFAULT '',
                bio TEXT NOT NULL DEFAULT '',
                avatarUrl TEXT NOT NULL DEFAULT '',
                avatarLocalPath TEXT NOT NULL DEFAULT '',
                lastSeen INTEGER NOT NULL DEFAULT 0,
                isOnline INTEGER NOT NULL DEFAULT 0,
                isMe INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // Create status_stories table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS status_stories (
                id TEXT NOT NULL PRIMARY KEY,
                authorId TEXT NOT NULL,
                contentType TEXT NOT NULL DEFAULT 'TEXT',
                encryptedPayload TEXT NOT NULL DEFAULT '',
                backgroundColor TEXT NOT NULL DEFAULT '#FCE2E6',
                expiresAt INTEGER NOT NULL,
                createdAt INTEGER NOT NULL DEFAULT 0,
                viewedAt INTEGER NOT NULL DEFAULT 0,
                isFromMe INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // Index for fast story expiry queries
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_stories_expires ON status_stories (expiresAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_stories_author ON status_stories (authorId)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Call history log table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS call_logs (
                id TEXT NOT NULL PRIMARY KEY,
                callType TEXT NOT NULL DEFAULT 'VIDEO',
                direction TEXT NOT NULL DEFAULT 'OUTGOING',
                status TEXT NOT NULL DEFAULT 'MISSED',
                startedAt INTEGER NOT NULL DEFAULT 0,
                durationSeconds INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // Add isFavorite to media_metadata for Vault gallery favorites
        db.execSQL("ALTER TABLE media_metadata ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [
        MessageEntity::class,
        MediaMetadataEntity::class,
        LetterEntity::class,
        UserProfileEntity::class,
        StatusStoryEntity::class,
        CallLogEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class EnclaveDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun mediaMetadataDao(): MediaMetadataDao
    abstract fun letterDao(): LetterDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun statusStoryDao(): StatusStoryDao
    abstract fun callLogDao(): CallLogDao

    companion object {
        @Volatile
        private var INSTANCE: EnclaveDatabase? = null

        fun getInstance(context: android.content.Context): EnclaveDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    EnclaveDatabase::class.java,
                    "enclave_db"
                )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
