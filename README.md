<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" alt="Analyser App Icon" width="128" />

  # Analyser

  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/palinkiewicz/system-analyser">
    <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60" />
  </a>
  <p align="center">
    <img src="https://img.shields.io/badge/Min%20SDK-29-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Min SDK" />
    <img src="https://img.shields.io/badge/Target%20SDK-36-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Target SDK" />
    <img src="https://img.shields.io/badge/Language-Kotlin%20100%25-7F52FF?style=for-the-badge" alt="Language" />
    <img src="https://img.shields.io/github/repo-size/palinkiewicz/system-analyser?style=for-the-badge&color=blue" alt="Repository Size" />
    <img src="https://img.shields.io/github/v/release/palinkiewicz/system-analyser?style=for-the-badge&color=orange" alt="Latest Release" />
  </p>
</div>

**Analyser** is a lightweight, privacy-respecting Free and Open Source Software (FOSS) utility for Android that combines two tools in one small package: a tech-stack detective for your installed apps, and a live, real-time device information dashboard. Built entirely with **Jetpack Compose** and **Material Design 3**, it reads only what it needs from the device, ships with zero trackers, ads, or telemetry, and stays true to a native, deterministic UI experience.

The application follows a clean, layered structure (`ui` → `viewmodel` → `data` → `domain`) with no dependency-injection framework, no local database, and no DataStore — persistence is handled through minimal, purpose-built `SharedPreferences` repositories for near-zero overhead.

## Key Features

### 🔍 App Tech-Stack Analysis
* **On-Device Framework Detection**: Streams and inspects installed APKs' internal file signatures to identify the underlying UI framework — Flutter, React Native, Jetpack Compose, Unity, Xamarin/.NET MAUI, Cordova/Ionic/Capacitor, and over a dozen others — without ever uploading data anywhere.
* **Weighted Probability Scoring**: Matched signatures are combined into normalized confidence percentages per detected framework, falling back to a "Native" classification when no framework crosses the certainty threshold.
* **Rich App Details**: Package metadata (size, version, target/minimum SDK, install and update dates) alongside a breakdown of exactly which files triggered each detection.
* **Instant Search & Filtering**: Live search across installed apps with a dedicated toggle to include or exclude system apps.

### 📊 Live Device Information Dashboard
* **System & Software**: Manufacturer, model, board and hardware identifiers, Android version, API level, security patch, build ID, and kernel version.
* **CPU Monitoring**: Per-core clock speeds, SoC identification, supported ABIs, live usage percentage (with a frequency-based estimate fallback where `/proc/stat` is SELinux-restricted), and available thermal zone temperatures.
* **Battery & "Ampere" Current Draw**: Real-time instantaneous current (auto-detected mA/µA units, sign-normalized against charge state, and smoothed against noise), session min/max readings, derived power draw, voltage, capacity, cycle count, health, and charging source.
* **Full Sensor Suite**: Live readings for every sensor the device exposes, with an optional simplified view and toggleable measurement-unit suffixes.
* **Memory & Storage**: RAM and internal storage usage with live progress indicators.
* **Display Information**: Resolution, density, refresh rate, and physical screen size.

### 🏠 Customizable Home Widget Grid
* **Launcher-Style Widgets**: Pin any Device Info card — including individual sensors — directly to the Home screen via a long-press context menu.
* **Drag, Resize, and Remove**: Long-press a card to reorder it, resize its column and row span with Android-16-style edge handles, or drag it onto the trash target to remove it, complete with haptic feedback throughout.
* **Adaptive Layout**: Rows automatically size themselves to their content, and the grid column count (1–5) is fully configurable from Settings.
* **Tap-Through Navigation**: Tapping a widget jumps straight to its corresponding tab on the Device Info screen.

### 🎨 Personalization
* **Multiple Color Themes**: Choose between the default light-blue "Dakil's Analyser" theme, Android 12+ dynamic system colors, or five additional hand-tuned presets (Ocean, Lavender, Sunset, Rose, Teal).
* **Dark Mode Control**: Follow system, force light, or force dark, with an optional pure-black variant for battery savings on OLED displays.
* **Configurable Units**: Independent toggles for temperature unit (Celsius/Fahrenheit/Kelvin) and sensor value units.

## Architecture & Technology Stack

The project follows a straightforward, unidirectional layering that keeps business logic isolated from UI concerns:

* **Domain Layer**: Plain Kotlin data classes and enums (`SystemInfo`, `CpuInfo`, `BatteryInfo`, `HomeWidget`, `TechStack`, etc.) with no Android or Compose dependencies.
* **Data Layer**: Repositories expose live device telemetry as cold `Flow`s (converted to `StateFlow` per screen via `WhileSubscribed`), perform APK signature scanning for tech-stack detection, and persist settings and Home widget layout as lightweight `SharedPreferences`-backed singletons — no Room, no DataStore.
* **Presentation Layer**: Built entirely with **Jetpack Compose (Material Design 3)** under an MVVM structure, with `AndroidViewModel`s exposing `StateFlow`s consumed directly by Composables. Reusable device-info cards are stateless and parameter-driven, letting the same card power both a Device Info tab and a Home widget.
* **Manual Dependency Wiring**: No DI framework (no Hilt/Dagger/Koin). ViewModels and repositories are constructed directly or via lightweight singleton accessors, keeping startup overhead minimal.

## Getting Started & Development

### Requirements
* **JDK 17** or newer
* **Android SDK** (Target SDK 36, Minimum SDK 29)
* An active Android Emulator or physical device

### Building and Installation
Execute the standard Gradle tasks via the wrapper at the repository root:

```bash
# Clone the repository
git clone https://github.com/palinkiewicz/system-analyser.git
cd system-analyser

# Build the debug configuration APK
./gradlew assembleDebug

# Build and deploy directly onto a connected device or emulator
./gradlew installDebug
```

## Verification & Testing

Maintain code quality and stability through the following automated validation tasks:

```bash
# Run JVM unit tests
./gradlew test

# Run JVM unit tests exclusively for the debug variant
./gradlew testDebugUnitTest

# Execute Android Lint checks
./gradlew lint

# Execute instrumented UI/integration tests (requires connected device)
./gradlew connectedAndroidTest
```

> [!NOTE]
> Dependencies are centrally managed via the Gradle Version Catalog (`gradle/libs.versions.toml`). Avoid declaring hardcoded versions inline within build scripts.

## Permissions

Analyser requests a single permission, `QUERY_ALL_PACKAGES`, used exclusively to enumerate installed applications for the tech-stack analyzer. Every device-information feature relies solely on permission-free system APIs.

## Localization & Contributions

We aim for global accessibility. When contributing new features or UI controls, ensure that corresponding translation entries are supplied across all active resource configurations under `app/src/main/res/values-*/strings.xml`.

## License

This project is licensed under the Free and Open Source Software regulations. Feel free to inspect, fork, and enhance the application following standard open-source compliance guidelines.
