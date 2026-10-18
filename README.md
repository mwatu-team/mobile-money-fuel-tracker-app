# Mobile Money Fuel Tracker: Android Gateway

PumpSync Gateway is the Android app that captures merchant mobile-money notification SMS from trusted senders, queues them offline in Room, and syncs them to the Mobile Money Fuel Tracker API. The API and station dashboard live in the separate `mobile-money-fuel-tracker-dashboard` repository.

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

## Physical phone and local backend

For a physical phone, connect it by USB, accept its debugging prompt, and create an ADB reverse
tunnel:

```bash
adb devices -l
adb reverse tcp:8000 tcp:8000
adb reverse --list
```

The reverse list must include `tcp:8000 tcp:8000`. Set the app's backend URL to
`http://localhost:8000/` and save. Each developer runs the same commands against their own connected
device and local backend; no developer-specific IP address belongs in the project configuration.
Recreate the reverse tunnel after disconnecting/reconnecting the phone, rebooting it, or restarting
the ADB server.

The development station ID is displayed/configurable for future device provisioning but the backend derives station scope from the authenticated gateway device.

The trusted-sender list controls which incoming SMS messages PumpSync captures. It accepts
equivalent Ugandan `07…`, `256…`, and `+256…` phone formats and case-insensitive alphanumeric IDs.
When the list is empty, new SMS messages are ignored by PumpSync but remain in the phone's normal
Messages app. Contact selection uses the system picker without broad contacts permission. Sender
filtering is not proof of payment or sender authenticity and must not be presented as such.

## Emulator SMS testing

With the emulator running and PumpSync Gateway installed, grant the runtime permission and use:

```bash
adb emu sms send 256770000000 "You have received UGX 100,000 from JOHN DOE at 2026-08-10 17:20:25. Fee: 0. Message: Till:33220001. New balance is: UGX 500,000. Transaction ID: EMUTEST001. Download MoMo App https://example.test/momo to get 500MBs."
```

Use a date/time close to a waiting expected payment to receive a suggestion. The receiver writes the exact joined SMS body to Room, then WorkManager parses and uploads it. The **Messages** screen displays the parsed payer name (or a masked phone for legacy formats) and queue status. Original previews and parser errors are shown only when the build is debug and **Parser debug mode** is enabled.

To exercise multipart handling, send a long fictional body from the emulator console or use two emulator SMS commands only if the emulator tooling actually delivers them as one multipart PDU; two independent SMS deliveries are correctly stored as two messages.

## Parser fixtures

The Kotlin parser must agree with the backend's Python parser. `app/src/test/resources/parser-fixtures.json` is this repository's own set of SMS parser fixtures, and the `FictionalSmsParserTest` suite runs every case in it. The backend repository (`mobile-money-fuel-tracker-dashboard`) keeps its own file of the same shape and tests it the same way. The two repositories are not linked by CI, so when parser behaviour changes, update the fixtures and tests in both. Keep the fixture structure (field names and types) identical across them.
