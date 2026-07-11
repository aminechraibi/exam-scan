# ExamScan

A local-first Android application for quickly scanning and organizing exam papers with **Kotlin**, **Jetpack Compose**, **Room**, and **Google ML Kit Document Scanner API**.

## Main features

- Create exam folders with a name, date (defaults to today), and expected pages per paper.
- Scan one paper with an exact page limit.
- Bulk scan many consecutive pages; pages are automatically grouped into papers.
- Papers are numbered `Paper 1`, `Paper 2`, ... and listed by scan time.
- Each page keeps:
  - an unmodified copy of the JPEG returned by ML Kit (`*_original.jpg`);
  - a separate labeled JPEG with only `Paper N` in the top-left corner.
- Paper editor: preview, retake, delete, append, and insert pages.
- Page numbers are recalculated after inserts/deletions, while every page keeps the visible paper-only label.
- Incomplete and over-complete papers are visibly flagged.
- Export the complete exam as one PDF.
- Export all labeled images as a ZIP organized by paper.
- Share exports using Android's system share sheet.
- Room persistence and app-private file storage; no account or server required.
- GitHub Actions for CI and tag-based release APKs.

## Important ML Kit limitation

ML Kit Document Scanner returns the processed document JPEG/PDF, but does **not** expose the untouched camera sensor image. Therefore this project uses the term **original** for an unchanged local copy of the JPEG returned by ML Kit before ExamScan adds its tracking label. The labeled copy is always separate.

## Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35
- Physical Android device with Google Play services
- Android 6.0 (API 23) or newer

ML Kit itself requires API 21+, a supported Google Play services device, and at least 1.7 GB total RAM. The scanner components may download on first use.

## Run locally

1. Open the `ExamScan` folder in Android Studio.
2. Let Gradle sync.
3. Connect a physical Android device with Google Play services.
4. Run the `app` configuration.

The document scanner generally should be tested on a real device. The first scan can take longer because Google Play services may download scanner components.

## Build from command line

```bash
./gradlew testDebugUnitTest assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release APK (unsigned by default):

```bash
./gradlew assembleRelease
```

## Signed releases

The included `apk-release.yml` builds an unsigned release APK. For Play Store or distributable signed releases, add a keystore and configure Gradle signing with GitHub Secrets. Do not commit the keystore or passwords.

## Bulk grouping behavior

For an exam configured with 2 pages per paper:

- 10 scanned pages → 5 complete papers.
- 9 scanned pages → 4 complete papers + 1 incomplete paper with 1 page.

Open the incomplete paper and use **Add page** or **Insert after**. Page numbering is regenerated automatically, and the visible label remains the paper ID only.

## Storage layout

```text
files/scans/
└── exam_<id>/
    └── paper_<number>/
        ├── page_1_<timestamp>_original.jpg
        └── page_1_<timestamp>_labeled.jpg
```

Exports are generated in cache and shared through a `FileProvider`.

## CI

- `.github/workflows/android.yml`: unit tests + debug APK on pushes and pull requests.
- `.github/workflows/apk-release.yml`: release APK artifact on manual runs and Git tags such as `v1.0.0`; tag pushes also create a GitHub Release.

## Privacy

Scans stay in app-private local storage. The application requests no network permission. ML Kit's scanner implementation is supplied by Google Play services and may download its components dynamically.

## Diagnostic logs for manual testing

Open the gear icon on the home screen to enable opt-in diagnostics. Logging can be enabled separately for ML Kit, document quality, storage, sharing, lifecycle/reboot, and release-signing checks. Use **Record checkpoint** before or after a manual scenario, then **Share log** to export newline-delimited JSON for troubleshooting.

Diagnostic logs are disabled by default, rotate at approximately 1 MB, and exclude exam names, scan images, file paths, and content URIs. The screen also provides a **Clear** action.
