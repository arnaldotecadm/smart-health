# SmartHealth — Copilot Instructions

## Project Overview

Android app (Kotlin, minSdk 29) that reads health data from Samsung Health via the Samsung Health Data SDK (`libs/samsung-health-data-api-1.0.0.aar`) and syncs it to a backend REST API using Retrofit. The app uses the Jetpack Navigation component with a single `MainActivity`, Room for sync-state tracking, WorkManager for background sync, and DataStore for auth token persistence.

## Build & Test Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run a single unit test class
./gradlew test --tests "com.arvion.smarthealth.ExampleUnitTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# SonarQube analysis (requires local SonarQube on port 11000)
./gradlew sonar
```

## Architecture

```
MainActivity
 └─ NavHostFragment (nav_graph.xml)
     ├─ HomeFragment
     ├─ DashboardFragment
     ├─ NotificationsFragment
     ├─ SamsungHealthNotInstalledFragment  ← blocking screen
     └─ EnableDeveloperModeFragment        ← blocking screen
```

**Startup gate:** `MainActivity.onCreate` checks if `com.sec.android.app.shealth` is installed and if Samsung Health SDK developer mode is enabled before inflating the nav graph. If either check fails, the app navigates to a blocking screen and locks the drawer/bottom nav.

**Data flow for a health data type (e.g., HeartRate):**

1. `UpdateApiWorker` (WorkManager `CoroutineWorker`) orchestrates all sync types by calling service classes.
2. `*Service` classes (e.g., `HeartRateService`) read from `HealthDataStore` (Samsung SDK), check `SyncLogDao` to avoid re-syncing already-sent dates, then call `*ApiService`.
3. `*ApiService` extends `ApiBackend`, which builds the Retrofit `OkHttpClient` with `GzipRequestInterceptor` and `AuthInterceptor`.
4. On success, a `SyncLog` row is written to Room to mark that date as synced.

**Model/Mapper pattern:** Samsung SDK types are mapped to internal `model/` classes via `mapper/` objects using Kotlin extension functions (e.g., `List<HealthDataPoint>.toModel(dataType)`). The `HealthDataPoint` wrapper holds a `sessions: List<RecordSession>` where `RecordSession` is a sealed/interface type holding either `ExerciseSession`, `SleepSession`, or `HeartRateSeries`.

## Key Conventions

- **Package**: `com.arvion.smarthealth`
- **Samsung SDK import alias**: `com.samsung.android.sdk.health.data.data.HealthDataPoint` is aliased as `HealthDataPointSDK`; the internal model is `com.arvion.smarthealth.model.HealthDataPoint`.
- **Gson serialization**: All `Retrofit` calls use a shared `Utilities.gson` instance (defined in `utils/Utilities.kt`) registered with custom type adapters for `Instant`, `Duration`, `ZoneOffset`, and `LocalDate`. Always use this instance — do not create a bare `Gson()`.
- **Auth**: JWT token is stored in DataStore via `UserRepository`. `AuthInterceptor` reads it synchronously via `runBlocking` and attaches it as a `Bearer` token to every request.
- **Sync deduplication**: Before sending data for a given date, each service checks `SyncLogDao.getSyncLog(date, SyncType)`. Only insert a `SyncLog` row after all batches succeed.
- **Batching**: Heart rate data is chunked in batches of 1,000 before sending to the API.
- **API base URL**: Hardcoded to `http://192.168.1.131:8080/` in `ApiBackend`. Network security config (`res/xml/network_security_config.xml`) allows cleartext traffic for local dev.
- **Logging tag**: Use `Constants.TAG` (`"SmartHealth_TAG"`) for all `Log.*` calls.
- **New health data types**: Add a new `*Service`, `*ApiService` (extending `ApiBackend`), mapper in `mapper/`, model in `model/`, and a new `SyncType` enum value; wire it into `UpdateApiWorker`.
- **Room**: Only `SyncLog` is persisted. Add new entities to `AppDatabase` and bump `version`. Type converters for `LocalDate` and `LocalDateTime` are in `database/Converters.kt`.
- **Version catalog**: All dependency versions are managed in `gradle/libs.versions.toml`. Add new dependencies there rather than hardcoding versions in `app/build.gradle.kts`.
