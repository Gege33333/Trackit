# Trackit

Personal money tracker for Android (Kotlin, Jetpack Compose, Material 3, MVVM, Navigation Compose).

## Cara menjalankan
1. Ekstrak zip, lalu di Android Studio pilih **File > Open** dan arahkan ke folder `Trackit`.
2. Tunggu Gradle Sync selesai (butuh internet untuk mengunduh dependency pertama kali).
   Jika Android Studio menawarkan "Update Gradle/AGP", boleh diterima.
3. Pilih emulator/HP, lalu tekan **Run**.

## Penyimpanan data (sementara)
Supabase belum dihubungkan. Akun, record, dan Easy Input disimpan lokal di perangkat
(`data/Repositories.kt`, kelas `Local*Repository`). Semua layar hanya bergantung pada interface
`AuthRepository`, `RecordRepository`, `PresetRepository`, jadi nanti cukup membuat implementasi
Supabase dan menggantinya di `AppContainer` (`TrackitApp.kt`).
