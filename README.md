# MoonReader

Aplikasi pembaca manga dan komik **Android Native** (Kotlin + Jetpack Compose + Material 3) yang terinspirasi dari arsitektur **Tachiyomi / Mihon**, dilengkapi sistem *Source* modular, pembaca arsip lokal `.cbz` / `.zip`, integrasi API publik resmi **MangaDex v5**, serta *reader* dengan tiga mode baca.

---

## Fitur Utama

### 1. Perpustakaan & Manajemen Koleksi
* **Kategori Kustom & Swipe:** Kelola kategori (`Umum` atau tambah/hapus kategori sendiri), dan **geser kanan/kiri** langsung di Pustaka untuk berpindah antar kategori.
* **Lencana, Filter & Pull-to-Refresh:** Hitungan chapter belum dibaca (*unread badge*), filter "Belum dibaca saja", pencarian langsung di perpustakaan, serta tarik ke bawah (*pull-to-refresh*) untuk memperbarui chapter terbaru.
* **Tampilan Fleksibel:** Grid Padat, Grid Nyaman, dan Daftar (*List*), serta pengaturan jumlah kolom grid (2–4 kolom).
* **Migrasi Sumber (`MigrateHubScreen` & `MigrateSearchScreen`):** Jika sumber lama bermasalah, cari judul yang sama di seluruh sumber aktif lain secara paralel lalu migrasikan komik beserta progres chapter terbaca, bookmark, dan kategorinya.

### 2. Pembaruan & Riwayat Baca
* **Tab Pembaruan (*Updates*):** Menampilkan daftar chapter terbaru dari komik yang tersimpan di perpustakaan, dikelompokkan berdasarkan tanggal.
* **Tab Riwayat (*History*):** Mencatat chapter dan halaman terakhir yang dibaca lengkap dengan tombol lanjutkan (*Resume*) 1 ketuk.

### 3. Arsitektur *Source* Modular (`MangaSource`)
Semua sumber konten mengimplementasikan kontrak [`MangaSource`](app/src/main/java/com/moonreader/app/domain/source/MangaSource.kt) sehingga penambahan sumber baru tidak perlu mengubah UI maupun *repository*:
* **MangaDex (`MangaDexSource`)** — Integrasi langsung ke MangaDex Public Open API v5 (`api.mangadex.org`) untuk Populer, Terbaru, Pencarian, Detail, Daftar Chapter (prioritas bahasa `id` / `en`), dan pemuatan halaman via `@home/server`.
* **Konektor OPDS (`OpdsSource`)** — Klien OPDS v1.2 / OPDS-PSE yang mendukung server pribadi maupun publik (Komga, Kavita, Suwayomi-Server, atau katalog OPDS terbuka) dengan opsi tanpa login atau *Basic Auth*.
* **Komik Lokal (`LocalCbzSource`)** — Impor arsip `.cbz` / `.zip` via Storage Access Framework (SAF), pengurutan halaman numerik alami (*natural order*), ekstraksi sampul otomatis, dan pembacaan *offline*.
* **Katalog Demo (`DemoCatalogSource`)** — Katalog *offline* bawaan dengan halaman komik yang di-*render* secara prosedural via `DemoPageFetcher` untuk menguji seluruh alur baca tanpa koneksi internet.

### 4. Comic Reader
* **3 Mode Baca:**
  * `WEBTOON` (gulir vertikal mulus tanpa jeda antar halaman)
  * `RTL` (Manga — kanan ke kiri dengan `HorizontalPager`)
  * `LTR` (Komik — kiri ke kanan dengan `HorizontalPager`)
* **Kontrol Interaktif:** *Double-tap* & *pinch-to-zoom* (pada mode halaman), zona ketuk tepi & tengah, *slider* halaman, navigasi *chapter* sebelum/berikutnya, pengatur kecerahan layar, dan opsi layar tetap menyala (*keep screen on*).
* **Pelacakan Otomatis:** Halaman terakhir tersimpan otomatis ke Room Database setiap kali berpindah halaman.

### 5. Unduhan Offline, Backup & Tema
* **Antrean Unduhan (`ChapterDownloader`):** Simpan halaman *chapter* ke penyimpanan aplikasi untuk dibaca *offline*.
* **Backup & Restore (`BackupManager` & `TachibkParser`):** Ekspor dan impor perpustakaan dalam format JSON MoonReader, serta mendukung **restore langsung file backup Tachiyomi / Mihon (`.tachibk`)** lengkap dengan kategori, daftar komik, sampul, dan progres chapter terbaca.
* **Tema Material 3:** Mendukung `Sistem`, `Terang`, `Gelap`, dan `AMOLED Hitam`.
* **In-App OTA Update (`AppUpdater`):** Cek rilis APK terbaru langsung dari dalam aplikasi via **GitHub Releases** (`https://github.com/prastowoardi/MoonReader`) lalu unduh via browser.

---

## Arsitektur Proyek

```text
app/src/main/java/com/moonreader/app/
├── MoonReaderApp.kt                 # AppContainer (DI manual) & Coil ImageLoaderFactory
├── MainActivity.kt                  # Edge-to-edge Compose host
├── domain/
│   ├── model/Models.kt              # Model domain (Manga, Chapter, Page, Category, History, Download)
│   └── source/MangaSource.kt        # Kontrak modular Source (Populer, Terbaru, Cari, Detail, Chapter, Halaman)
├── data/
│   ├── local/
│   │   ├── MoonDatabase.kt          # Room Database, Entity & DAO
│   │   ├── PreferencesStore.kt      # Penyimpanan preferensi tema, reader, grid & bahasa
│   │   └── BackupManager.kt         # Serialisasi & deserialisasi backup JSON
│   ├── source/
│   │   ├── SourceManager.kt         # Registry seluruh sumber aktif
│   │   ├── MangaDexSource.kt        # Klien & parser MangaDex API v5
│   │   ├── LocalCbzSource.kt        # Impor & ekstraktor arsip .cbz / .zip
│   │   ├── DemoCatalogSource.kt     # Sumber demo offline + Coil DemoPageFetcher
│   │   └── ChapterDownloader.kt     # Pengunduh chapter offline
│   └── repository/
│       └── MangaRepository.kt       # Koordinasi Room DB, SourceManager, dan Downloader
└── ui/
    ├── theme/Theme.kt               # Skema warna Light, Dark, dan AMOLED
    ├── state/MainViewModel.kt       # StateFlow & aksi UI terpusat
    ├── navigation/MoonNavGraph.kt   # Navigasi 5 tab utama + Detail + Reader
    ├── components/MangaComponents.kt
    └── screens/
        ├── library/LibraryScreen.kt
        ├── updates/UpdatesScreen.kt
        ├── history/HistoryScreen.kt
        ├── browse/BrowseScreen.kt
        ├── detail/MangaDetailScreen.kt
        ├── reader/ReaderScreen.kt
        └── more/MoreScreen.kt
```

---

## Stack Teknologi

* **Bahasa & UI:** Kotlin 2.0, Jetpack Compose (Material 3), Navigation Compose
* **Database Lokal:** Room + KSP
* **Jaringan & Gambar:** OkHttp 4, Coil 2 (`SubcomposeAsyncImage` + `Fetcher` kustom)
* **Serialisasi & Konkurensi:** `kotlinx.serialization`, Kotlin Coroutines & Flow
* **Target SDK:** `minSdk 26` / `compileSdk 34` / `targetSdk 34`

---

## Cara Menjalankan & Build

### Prasyarat
* **JDK 17** (direkomendasikan untuk AGP 8.7.3)
* **Android SDK** (Platform Android 34)
* Gradle Wrapper sudah tersedia di repositori (`./gradlew`)

### 1. Konfigurasi `local.properties`
Buat file `local.properties` di root proyek (file ini diabaikan oleh `.gitignore`):
```properties
sdk.dir=/path/ke/Android/sdk
```

### 2. Menjalankan Unit Test
```bash
./gradlew testDebugUnitTest
```

### 3. Build Debug APK (Siap Install untuk Testing)
Pastikan menggunakan **JDK 17** (di macOS jika default JDK berbeda, set `JAVA_HOME`):
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home ./gradlew assembleDebug
```
*(Atau langsung `./gradlew assembleDebug` jika JDK aktif sudah versi 17.)*

Hasil APK akan dibuat di:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### 4. Build Signed Release APK
Proyek sudah dikonfigurasi dengan R8 (`isMinifyEnabled = true` + `isShrinkResources = true`) dan membaca `keystore.properties` (jangan di-commit ke Git):
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home ./gradlew assembleRelease
```
Output berada di `app/build/outputs/apk/release/app-release.apk`.

### 5. Install ke Perangkat / Emulator via ADB
Sambungkan HP Android (aktifkan *USB Debugging*) atau jalankan emulator, lalu jalankan:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Atau salin file `app-debug.apk` / `app-release.apk` langsung ke HP Android dan buka untuk menginstal.

---

## Rilis Versi Baru & Tagging (GitHub Actions)

Workflow [`.github/workflows/release-apk.yml`](.github/workflows/release-apk.yml) akan otomatis menjalankan unit test, mem-build APK, dan menerbitkannya ke **GitHub Releases** setiap kali *tag* berawalan `v` (contoh: `v1.0.2`) di-push ke GitHub.

### 1. Siapkan Secrets Signing (Sekali Saja, Opsional)
Agar APK hasil build GitHub Actions ditandatangani dengan *release keystore* yang sama seperti build lokal, buka **GitHub Repo → Settings → Secrets and variables → Actions** lalu tambahkan:
* `SIGNING_KEYSTORE_BASE64`: hasil dari `base64 -i keystore/moonreader-release.jks | pbcopy`
* `SIGNING_STORE_PASSWORD`: password keystore
* `SIGNING_KEY_ALIAS`: alias key (misal `moonreader`)
* `SIGNING_KEY_PASSWORD`: password key

*(Jika secrets belum diisi, workflow tetap berjalan dan mem-build fallback `app-debug.apk`.)*

### 2. Langkah Membuat Tag & Release Baru
1. Naikkan `versionCode` dan `versionName` di [`app/build.gradle.kts`](app/build.gradle.kts) (misalnya `versionCode = 2` dan `versionName = "1.0.2"`).
2. Commit & push perubahan ke branch `main`:
   ```bash
   git add .
   git commit -m "Release v1.0.2"
   git push origin main
   ```
3. Buat *tag* baru lalu push ke `origin`:
   ```bash
   git tag v1.0.2
   git push origin v1.0.2
   ```
4. Pantau progresnya di tab **Actions** GitHub hingga selesai, lalu buka MoonReader di HP → **Lainnya → Pembaruan Aplikasi (GitHub) → Cek Pembaruan** untuk mengunduh rilis terbaru lewat browser.

> **Catatan:** Jangan push ke branch bernama `release` untuk memicu build, karena trigger workflow hanya membaca *tag* `v*` (atau dijalankan manual dari tab Actions via `workflow_dispatch`). Jika perlu mengulang tag yang sudah terlanjur di-push:
> ```bash
> git tag -d v1.0.2
> git push origin :refs/tags/v1.0.2
> git tag v1.0.2 && git push origin v1.0.2
> ```

---

## Menambahkan Sumber (*Source*) Baru

Untuk menambahkan sumber bacaan baru (misalnya feed OPDS, server Komga/Kavita, atau API pribadi):
1. Buat kelas baru di `data/source/` yang mengimplementasikan interface [`MangaSource`](app/src/main/java/com/moonreader/app/domain/source/MangaSource.kt).
2. Daftarkan *instance* sumber tersebut di [`SourceManager`](app/src/main/java/com/moonreader/app/data/source/SourceManager.kt) dan sediakan di [`AppContainer`](app/src/main/java/com/moonreader/app/MoonReaderApp.kt).
3. Sumber baru otomatis muncul di tab **Jelajah** dan pengaturan **Lainnya**.

---

## Lisensi & Catatan

Proyek ini dibuat untuk pembelajaran dan penggunaan pribadi dengan integrasi ke sumber terbuka/legal (API resmi MangaDex v5 dan pembaca file lokal `.cbz`/`.zip`).
