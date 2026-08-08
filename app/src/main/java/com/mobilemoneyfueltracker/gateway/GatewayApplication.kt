package com.mobilemoneyfueltracker.gateway

import android.app.Application
import androidx.room.Room
import com.mobilemoneyfueltracker.gateway.data.GatewayDatabase
import com.mobilemoneyfueltracker.gateway.data.GatewaySettingsRepository
import com.mobilemoneyfueltracker.gateway.data.SmsQueueRepository
import com.mobilemoneyfueltracker.gateway.parser.FictionalSmsParser

class GatewayApplication : Application() {
    lateinit var database: GatewayDatabase
        private set
    lateinit var settingsRepository: GatewaySettingsRepository
        private set
    lateinit var queueRepository: SmsQueueRepository
        private set
    val smsParser = FictionalSmsParser()

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            GatewayDatabase::class.java,
            "mobile_money_fuel_tracker_gateway.db",
        ).build()
        settingsRepository = GatewaySettingsRepository(applicationContext)
        queueRepository = SmsQueueRepository(applicationContext, database.smsMessageDao())
        queueRepository.enqueueSync()
    }
}
