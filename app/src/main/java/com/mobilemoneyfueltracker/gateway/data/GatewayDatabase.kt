package com.mobilemoneyfueltracker.gateway.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LocalSmsMessage::class], version = 2, exportSchema = true)
abstract class GatewayDatabase : RoomDatabase() {
    abstract fun smsMessageDao(): SmsMessageDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sms_messages ADD COLUMN customerName TEXT")
            }
        }
    }
}
