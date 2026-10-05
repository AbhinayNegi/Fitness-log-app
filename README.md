# Rep Log

An offline-first Android workout log built with Kotlin, Jetpack Compose, and Room.

## MVP features

- Create and name a workout.
- Add one or more exercises to it.
- Add or remove sets for each exercise, enter reps and weight per set, and confirm before removing a set.
- Open a saved workout plan and start a timed workout session.
- Edit actual reps and weight while training, then check or uncheck each completed set.
- Complete a session to see elapsed time, completed sets and exercises, and total volume; completed sessions appear in recent history.
- Save workout plans and session history to a local database.

Each row number is derived from its position and updates automatically when a set is removed. Reps and weight are editable per row.

## Open and run

Open this folder in Android Studio, allow Gradle to sync, then run the `app` configuration on an Android device or emulator (Android 8.0 / API 26 or newer). The project is configured for Android SDK 36. Gradle needs network access the first time to download its plugins and libraries.

Workout records stay on the device in a Room/SQLite database. Cloud sync and weight tracking are not part of this first version.
