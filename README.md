# Nobi Scan 1.0.0-rc4

Nobi Scan is an offline Android document scanner. This workspace is the fourth release candidate, based on RC3 with the tested Alpha20 and Alpha21 improvements. It builds the stable package `com.nobi.scan`, separate from the existing `com.nobi.scan.alpha` tester build. The apps can coexist and each keeps its own private library. Use a PDF export to transfer a document.

## Build locally

Use JDK 17, Android SDK Platform 37, and Build Tools 36.0.0. Set `ANDROID_HOME` or the SDK path in `local.properties`. The Gradle wrapper downloads dependencies from Google Maven, Maven Central, the Gradle Plugin Portal, and the scoped Tesseract4Android JitPack repository.

```sh
./gradlew :app:assembleRelease :app:bundleRelease :app:lintRelease :app:testDebugUnitTest
```

The APK and AAB are under `app/build/outputs/`. The release targets API 36, supports API 32 and later, and packages ARM64 native libraries. The OpenCV detector is used in both local build types.

## Distribution

Sign the direct APK with the stable app-signing key. The Play bundle uses a separate upload key. Store the keys and build credentials individually outside this repository. For cross-channel updates, use the stable app-signing key when configuring Play App Signing. Keep the signed APK, hashes and matching source archive together per version. RC4 supplies a direct APK. Add a separately signed AAB when preparing a Play release. Alpha retains its existing package ID and signing key.

Check 16 KB alignment both for native ELF load segments and for generated APKs. Run a device or emulator smoke test where `adb shell getconf PAGE_SIZE` prints `16384` before stable publication. See [Android 16 KB guidance](https://developer.android.com/guide/practices/page-sizes).

## Source and notices

The intended FOSS license for Nobi Scan app code is GNU GPL-3.0-or-later. See [LICENSE](LICENSE) and the [source and dependency notes](THIRD_PARTY.md). Third-party code, fonts, and models retain their own terms. Package verification, the Play listing, and a complete corresponding-source review are publication steps. The local release candidate has no published store listing.
