# nutri-log-app
An android app taking advantage of AI to easily enter food and exercise.

A personal, offline-first nutrition, exercise and measurement journal written in Kotlin with Jetpack Compose and Material 3. Package name `dev.nutrilog`. Minimum Android version 8.0 (API 26).

## Open and build

1. Extract the archive, then open the **NutriLog** folder in Android Studio. Select the folder containing `settings.gradle.kts`.
2. Use Android Studio's bundled JDK 21. Install Android SDK Platform 36.1 and Build Tools 36.0.0 through SDK Manager.
3. Sync Gradle. Select the **app** configuration and press **Run** with a phone or emulator selected.
4. On Windows, use `gradlew.bat testDebugUnitTest assembleDebug` from this folder for a command-line build. On Linux/macOS, use `sh gradlew testDebugUnitTest assembleDebug`. Gradle downloads dependencies on the first build.
5. Your APK appears at `app/build/outputs/apk/debug/app-debug.apk`. Use a consistent signing key for updates on your own phone. Export the journal before switching signing identities, since Android requires uninstalling the previous build when the key changes.

## First entry

Use Today → New entry for manual tracking. For AI estimates, open Settings → Continue with ChatGPT. Authorize plan usage in the system browser and return to NutriLog. Check plan limits and optional credit use in ChatGPT Settings before requesting an estimate. The app has no API-key mode or paid fallback. Once a response completes, edit the draft and confirm to save it.

AI estimates send the submitted text, entry time, and any unsaved items being corrected to OpenAI. The saved journal and body history are not automatically attached. Android dictation uses your installed speech provider and its privacy settings. GitHub has no runtime connection to the app.

Tap a saved row in Today, History or Body to edit or delete it manually. The only conversational correction path operates on an unsaved draft. Copying creates a new entry with a new ID. Enter a recipe name in review to save reusable ingredients. Food and exercise energy are totaled separately. Optional net calories appear in Settings.

## Daily tracking in 0.4.0

Today has five nutrient meters and a macro-energy pie chart. Protein and carbohydrate contribute 4 kcal/g and fat contributes 9 kcal/g to the three slices. Fibre has a separate editable minimum, initially 30 g for a new goal configuration.

History groups days Monday through Sunday. Nutrition averages include days with at least one food entry. Fibre averages include only logged days with a known fibre value on every food. Expand any day to review and manually edit its entries.

Use Goals to set an effective date, daily calories, macro percentages, fibre minimum, target weight and measurement targets. Macro percentages must add to 100. Body shows recorded readings alongside targets effective on the measurement date.

## Where to edit the code

| Kotlin file under app/src/main/java/dev/nutrilog | Responsibility |
|---|---|
| MainActivity.kt | Five-page navigation, Android activity results, settings, API requests and review flow |
| Dashboard.kt | Day selection, intake summary, history rows and time-series charts |
| Editor.kt | Manual fields, quantity scaling, review actions and deletion confirmation |
| Records.kt | Domain model, units, date-effective goals and validation rules |
| Database.kt | SQLite schema, transaction boundaries and versioned JSON backup format |
| Ai.kt | Plan-authorized response streaming, JSON validation and Keystore-backed credentials |
| PlanSession.kt | Browser sign-in, token validation, session renewal and revocation |

Unit tests live in `app/src/test/java/dev/nutrilog/RecordsTest.kt`. The Gradle wrapper and build files are included. Build caches, API credentials, signing keys and machine-specific SDK paths are excluded.

## Data and recovery

Journal data stays in the application's private SQLite database. Records store device-local wall-clock dates. Charts average repeated body readings within each day and omit days with no matching observations. Weight is stored in kilograms and lengths in centimetres.

Manual export uses Android's file picker. Select Drive if the installed Google Drive app exposes it there. Backups contain entries, saved foods, meal ingredients, goals and display preferences. They are readable JSON with an integrity checksum. Provider credentials are excluded. On import the application verifies the checksum, version, count, IDs and record values, then asks before replacing journal records in a single SQLite transaction. A checksum detects damage and does not authenticate a backup.

## Scope of version 0.2.0

This build uses Android's built-in SQLiteOpenHelper. Room integration and encrypted scheduled Google Drive backups remain open. Goal history, selectable trend ranges, manual Drive file export, reusable meals and single-item favorites are implemented. Additional refinements from the specification include first-run setup, configurable meal times, richer recipe management, weekly statistics, weight-goal projections, optional body-fat estimates, undo and broader instrumented UI/database tests.

To test on a phone, create a manual meal, scale its quantity, save and reopen it, then duplicate it on another day. Export your test journal, restore it and compare both days. Test voice transcription and sign-in with your own ChatGPT account before relying on AI logging.
