package com.mobilemoneyfueltracker.gateway

import android.app.Application
import androidx.room.Room
import com.mobilemoneyfueltracker.gateway.data.GatewayDatabase
import com.mobilemoneyfueltracker.gateway.data.GatewaySettingsRepository
import com.mobilemoneyfueltracker.gateway.data.SmsQueueRepository
import com.mobilemoneyfueltracker.gateway.pairing.PairingRepository
import com.mobilemoneyfueltracker.gateway.parser.FictionalSmsParser

class GatewayApplication : Application() {
    lateinit var database: GatewayDatabase
        private set
    lateinit var settingsRepository: GatewaySettingsRepository
        private set
    lateinit var queueRepository: SmsQueueRepository
        private set
    lateinit var pairingRepository: PairingRepository
        private set
    val smsParser = FictionalSmsParser()

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            GatewayDatabase::class.java,
            "mobile_money_fuel_tracker_gateway.db",
        ).addMigrations(GatewayDatabase.MIGRATION_1_2).build()
        settingsRepository = GatewaySettingsRepository(applicationContext)
        queueRepository = SmsQueueRepository(applicationContext, database.smsMessageDao())
        pairingRepository = PairingRepository(
            backendBaseUrl = { settingsRepository.settings.value.backendBaseUrl },
            savePairing = settingsRepository::savePairing,
        )
        queueRepository.enqueueSync()
    }
}
