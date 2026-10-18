# Mobile Money Fuel Tracker: Android Gateway

PumpSync Gateway is the Android app that captures merchant mobile-money notification SMS from trusted senders, queues them offline in Room, and syncs them to the Mobile Money Fuel Tracker API. The API and station dashboard live in the separate `mobile-money-fuel-tracker-railway` repository.

PumpSync is not an official MTN product, approved integration, or partnership. The app verifies notification evidence; it does not initiate payments. All included messages, people, and numbers are fictional.

> Prototype: development token authentication and local token storage are not production-ready.

## Build and test

Requires JDK 17 and Android SDK 35. Open the project in Android Studio, or:

```bash
./gradlew test
./gradlew assembleDebug
./gradlew connectedAndroidTest   # emulator/device
```

Copy the relevant lines from `local.properties.example` into `local.properties` (gitignored) to bake in the backend URL, device token, and device identifier for a pilot build.

## First launch

1. Allow SMS reception.
2. Open **Settings** and set the backend URL (`http://10.0.2.2:8000/` for the Android Emulator).
3. Set the device ID (seeded: `demo-gateway-001`) and the gateway token (`MMFT_GATEWAY_DEVICE_TOKEN` on the API).
4. Under **Trusted SMS senders**, add the exact originating number or sender ID. Messages from other senders are not stored.
5. Save. Queued messages retry automatically.

Emulator test:

```bash
adb emu sms send 256770000000 "You have received UGX 100,000 from JOHN DOE at 2026-08-10 17:20:25. Fee: 0. Message: Till:33220001. New balance is: UGX 500,000. Transaction ID: EMUTEST001. Download MoMo App https://example.test/momo to get 500MBs."
```

## Parser fixtures

The Kotlin parser must agree with the backend's Python parser. `app/src/test/resources/parser-fixtures.json` is a copy of the file owned by the API repository (`sample-data/sms/parser-fixtures.json`). Update it there first, then copy it here. The `fixtures-drift` CI job fails if the two differ and needs an `API_REPO_READ_TOKEN` repository secret with read access to the API repository.
