# Manual release gates

The following checks require physical hardware, external apps, controlled storage conditions, or real documents and cannot be represented honestly by standard GitHub-hosted Android emulator jobs.

## Scanner and lifecycle

- First scan after a fresh install, with and without internet.
- Google Play services unavailable, scanner cancellation, Back, rotation, backgrounding, and process death.
- Samsung, Pixel/stock Android, Xiaomi/Redmi/Poco, a low-memory phone, and a recent phone.
- Resume an incomplete exam after force-stop and device restart without duplicates or missing pages.

## Document quality

- A4 portrait and landscape; white/dark backgrounds; weak light; shadows; folds; curves; handwriting; printed text; diagrams; small text; angled capture; and two visible sheets.
- Confirm all corners and answers remain visible and the paper-only label is readable.

## Storage and stress

- 100, 200, and 400 real scan images while observing memory, scrolling, PDF, and ZIP behavior.
- Less than 500 MB free and storage exhaustion during image save, PDF export, and ZIP export.
- Confirm failures preserve existing scans, do not leave corrupted exports, and allow retry.

## Sharing and release signing

- Share PDF to Gmail, Drive, and WhatsApp; share ZIP to Drive; cancel the chooser; verify receivers can open the content URI.
- Install the signed release APK, launch ML Kit, export PDF/ZIP, share both, and verify minified behavior.
- A production signing keystore and credentials must be supplied through GitHub Secrets. Never commit them.
