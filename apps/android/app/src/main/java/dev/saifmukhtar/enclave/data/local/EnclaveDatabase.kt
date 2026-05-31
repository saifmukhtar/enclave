package dev.saifmukhtar.enclave.data.local

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

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN loveLanguage TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN locationCity TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS outbox_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                targetId TEXT NOT NULL,
                type TEXT NOT NULL,
                payload TEXT NOT NULL,
                contentType TEXT,
                messageId TEXT,
                timestamp INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS time_capsules (
                id TEXT NOT NULL PRIMARY KEY,
                targetId TEXT NOT NULL,
                payloadText TEXT NOT NULL,
                sendAt INTEGER NOT NULL,
                createdAt INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS encrypted_notes (
                id TEXT NOT NULL PRIMARY KEY,
                titlePayload BLOB NOT NULL,
                contentPayload BLOB NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                authorId TEXT NOT NULL,
                isSynced INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

@Database(
    entities = [
        MessageEntity::class,
        MediaMetadataEntity::class,
        LetterEntity::class,
        UserProfileEntity::class,
        StatusStoryEntity::class,
        CallLogEntity::class,
        OutboxEntity::class,
        TimeCapsuleEntity::class,
        EncryptedNoteEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class EnclaveDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun mediaMetadataDao(): MediaMetadataDao
    abstract fun letterDao(): LetterDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun statusStoryDao(): StatusStoryDao
    abstract fun callLogDao(): CallLogDao
    abstract fun outboxDao(): OutboxDao
    abstract fun timeCapsuleDao(): TimeCapsuleDao
    abstract fun encryptedNoteDao(): EncryptedNoteDao

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
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
