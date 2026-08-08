package com.mobilemoneyfueltracker.gateway.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [LocalSmsMessage::class], version = 1, exportSchema = true)
abstract class GatewayDatabase : RoomDatabase() {
    abstract fun smsMessageDao(): SmsMessageDao
}
