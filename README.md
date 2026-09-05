# HardStop

A lightweight, Material 3 Android digital detox application designed to enforce healthy screen-time habits. Aegis Detox offers granular hourly application usage limits and scheduled night locks with dynamic system integration.

## Features

* **Hourly Usage Limits:** Set custom usage allowance limits and reset time windows per application group.
* **Scheduled Night Lock:** Define automated overnight blocking windows (e.g., 22:00 to 06:00) with custom day-of-week active schedules.
* **Material You Theming:** Native support for Android 12+ dynamic color accents, with automatic Dark and Light mode adaptations.
* **Performance-Optimized App Selector:** Instant loading and silky-smooth scrolling through installed launcher applications using background coroutine processing and memory-cached icons.
* **Minimalist Custom Inputs:** Clean Material 3 Time Pickers and custom pop-up dialogs replace cumbersome inline text fields.

## Tech Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material 3)
* **Architecture:** Unidirectional Data Flow, Android Architecture Components
* **Data Storage:** SharedPreferences with JSON array serialization
* **Async Processing:** Kotlin Coroutines & Flow (`Dispatchers.IO`)

## Prerequisites

* **Android Version:** Android 8.0 (API level 26) or higher.
* **Permissions:** Requires `Usage Access Permission` (`PACKAGE_USAGE_STATS`) to monitor application activity.
