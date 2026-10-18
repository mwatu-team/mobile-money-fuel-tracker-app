# Coding-agent guide

## Repository

PumpSync Gateway, the Android SMS-capture app for the Mobile Money Fuel Tracker system. Kotlin, Compose, Room, WorkManager, Retrofit, and Android/JUnit tests. The API and dashboard live in the separate `mobile-money-fuel-tracker-railway` repository.

Command: `./gradlew test` (JDK 17 and Android SDK 35). Kotlin official style.

## Naming

PumpSync is only the customer-facing display name. Use `mobile-money-fuel-tracker` for repository and command identifiers and `com.mobilemoneyfueltracker.gateway` for the application ID. The application ID is permanent once published; do not change it. Environment variables use `MMFT_`.

## Parser fixtures

`app/src/test/resources/parser-fixtures.json` is a copy. The API repository owns the canonical file. Change it there first, copy it here, and keep both parsers passing. CI fails if the copies differ.

## Security restrictions

Never store or request a customer or merchant PIN. Never log complete SMS bodies, tokens, or unmasked phone numbers. Do not hard-code secrets or real customer/station data. Never commit `local.properties`, keystores, or signing credentials.

The receiver must persist the exact sender and body to Room before any parsing or networking. Capture fails closed against the trusted-sender allowlist. Do not integrate any mobile-network payment API. Do not describe PumpSync as official, approved, endorsed, integrated, or partnered with a mobile-network operator. This app verifies notification evidence; it does not initiate payments.

## API contract

The gateway talks to `/api/v1/gateway/*` on the API. Installed apps outlive server releases, so changes there must stay backward compatible.
