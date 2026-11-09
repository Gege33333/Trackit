# Trackit

Personal money tracker for Android (Kotlin, Jetpack Compose, Material 3, MVVM, Navigation Compose, Supabase).

## Run it
1. Extract the zip into a NEW folder, then in Android Studio: **File > Open** and select the `Trackit` folder.
2. Wait for Gradle Sync (internet needed the first time).
3. Open `local.properties` (Android Studio creates it) and add your Supabase values
   (see `local.properties.example`):
   ```
   SUPABASE_URL=https://YOUR-PROJECT-ID.supabase.co
   SUPABASE_KEY=YOUR-PUBLISHABLE-KEY
   ```
   Use only the public / publishable key. NEVER use the service_role / secret key in the app.
4. In Supabase, run `supabase_setup.sql` in the SQL Editor (if you already ran the earlier setup,
   run only "PART 2" at the bottom).
5. **Build > Rebuild Project**, then Run.

## Features
- Track: Plus / Minus dropdown form, Easy Input presets, today's summary and records
- Calendar: colored month grid, month total, daily details, save the month as a picture (Pictures > Trackit)
- Profile: Edit Profile, Privacy & Security (change password, sessions, delete account), appearance, log out
