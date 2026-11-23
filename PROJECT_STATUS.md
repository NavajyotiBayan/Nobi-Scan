# Nobi Scan project status

Updated November 22, 2026. The public testing series starts at 1.0-alpha1 with the dedicated scanner tray. A Moto Edge 30 capture-to-PDF smoke test passed.

## 1.0.0-rc6 launch-time update offer

RC6 shows an update dialog when a foreground check finds a newer published GitHub release. The dialog shows the version, download size and release notes with Install now and Later. Later dismisses that version’s automatic offer, and Settings → About → Check for updates can reopen it. The offer waits until the user is outside the scanner and PDF tools.

The signed ARM64 APK upgraded RC5 on the Moto Edge 30. Build, lint, fifteen JVM tests, signature, and 16 KB APK and ELF alignment passed. The signed build showed the private-feed message from the About check. An isolated device fixture simulated a newer release and verified the launch dialog, changelog, Later dismissal and reopening from About. GitHub download and installer handoff remain pending a live public release. RC5 can detect RC6 once published. The RC6 launch dialog can be exercised against a later published build.

## 1.0.0-rc5 GitHub updater

The direct APK checks GitHub on foreground entry at most once per day. Settings → About → Check for updates checks on demand. Published Stable and RC tags can be selected by increasing Android version code. The user starts the download, and RC5 verifies the SHA-256, package, version and current signing certificate before opening the Android installer. The build property `-Pnobi.githubUpdates=false` removes the update UI and its network and install permissions from the release configuration.

Release assembly, lint, fourteen JVM tests, APK signature and native and ZIP 16 KB alignment passed. RC5 upgraded RC4 on the Moto Edge 30. The direct APK declared Internet and package-install permissions. The Play configuration manifest excluded both and set the updater flag to false. The manual check displayed the private-feed message on device. The namespace and package remain `com.nobi.scan`, version code 10004, API 32 minimum, API 36 target, ARM64. An end-to-end RC5 to RC6 upgrade still needs a published feed and a newer signed APK. No release or Play bundle was published.

## 1.0.0-rc4 batch import and large PDF compression

RC4 promotes the tested Alpha20 multi-image scanner import and Alpha21 large PDF compression fix. The current Home layout and floating glass dock are the design baseline. RC4 inherits the reader contrast and installer branding fixes from the earlier Stable candidates. Stable uses `com.nobi.scan`, version code 10003, minimum API 32, target API 36, and the existing Stable signing key.

Release assembly, lint, eleven JVM tests, APK signature, and ZIP alignment passed. All ten uncompressed ARM64 native libraries have 16 KB ELF load alignment. The 49,687,597-byte signed APK upgraded RC3 and retained the Stable library on the Moto Edge 30. Three selected images appeared as three draft pages. Balanced compression produced 4,594,368 bytes from the 175,549,459-byte fixture. The exported file contained all six pages with identical extracted text. The device uses 4 KB kernel pages. A 16 KB runtime test and a fresh Android 12L install remain open.

## 1.0.0-rc3 installer branding

The APK application and round icon metadata now point directly to the bundled Nobi Scan PNG. The launcher activity continues to declare its adaptive icon. This candidate uses the same Stable package and signing key, with version code 10002, minimum API 32 and target API 36.

Release assembly, lint, eleven JVM tests, signature and 16 KB APK and native ELF alignment checks passed. The signed ARM64 APK is 49,654,829 bytes. Android's update prompt on the Moto Edge 30 showed the Nobi Scan logo beside the app name. Play Protect then offered to upload the APK for scanning. The device remains on RC2. The earlier 16 KB runtime and fresh Android 12L checks remain open.

## 1.0.0-rc2 stable release candidate

Promotes the tested Alpha19 features, including the built-in PDF reader, review editing tray, unified crop editor, haptics, and Settings. The dark PDF reader applies the active theme's foreground and system bar colors. Stable retains CameraX 1.5.3, its OpenCV-only detector, the `com.nobi.scan` package and RC1 signing identity. The version code is 10001, with minimum API 32 and target API 36.

The signed direct ARM64 APK is 49,654,885 bytes. Release assembly, lint, eleven JVM tests, APK signature and 16 KB ZIP alignment passed. All ten ARM64 native libraries are stored uncompressed and their LOAD segments are 16 KB aligned. On the Moto Edge 30, an in-place upgrade retained the RC1 PDF. Settings showed RC2, and the import, trim, searchable PDF save, and dark reader smoke test passed. The Moto reported 4 KB kernel pages. A 16 KB runtime test and a fresh Android 12L install remain open. This checkpoint delivers the direct APK with matching source and hashes.

## 1.0.0-rc1 stable release candidate

The standalone candidate workspace was copied from the tested alpha14 source. Stable uses `com.nobi.scan`, version code 10000, target API 36, minimum API 32, and ARM64. The original alpha workspace and package retain their previous version and signing key. The two apps use separate app-private document libraries.

CameraX moved from 1.3.4 to 1.5.3. All ten native libraries have 16 KB ELF load alignment in both deliverables. Native libraries are page-aligned and stored uncompressed in the direct APK. The Play bundle declares PAGE_ALIGNMENT_16K. The direct APK passed zipalign and signing verification, and bundletool validated the signed bundle. The app-signing and upload keys are separate and stored outside the workspace.

Release assembly, bundle build, lint, and eleven JVM tests passed. The direct APK installed alongside alpha14 on Moto Edge 30. The camera captured a page, an imported English fixture ran OCR in airplane mode and a two-page searchable PDF saved. Device-targeted APKs generated from the AAB installed over the direct APK using the stable signing key, and the saved PDF remained in the library. Alpha14 opened independently with its existing library. The Moto uses a 4 KB kernel. Runtime testing on 16 KB hardware or an emulator and a fresh Android 12L device remains open.

The candidate is local. Complete the inherited-source and transitive license audit, and complete developer verification and Play testing before public distribution. The separate FOSS candidate source and its build instructions are in this workspace.

## Alpha development history

## Baseline and roadmap

The active workspace started empty. The sibling `Nobi-Scan` folder held a Kotlin/Compose prototype, an older debug APK, Rust source and three reference repositories. The four-screen glass mockup was beside that folder. This workspace contains a copy of the prototype source and [the design reference](design/Nobi%20Scan%20four-screen%20glass%20UI%20showcase.png). The sibling project and its APK were left in place. Neither source folder contained usable Git history.

The [shared roadmap](https://chatgpt.com/share/6b020822-70ec-83e8-a457-05a48a5e079a) sets an offline multipage scanner as the first deliverable, followed by multilingual OCR, searchable PDFs, and the PDF toolkit. This stage continues the existing `com.nobi.scan` app. The roadmap's FairScan fork, alternate application ID and Android 8.0 floor were proposals. The original Glasmio build used Android 13 (API 33) as its minimum. The current alpha targets devices running Android 12L or later.

## Source reuse audit

- **FairScan.** The inherited crop editor adapts FairScan's corner handling, coordinate utilities, crop state and magnifier. The prototype omitted upstream headers. The five crop files now carry the FairScan copyright and GPL-3.0-or-later notice. The upstream license text is in `licenses/FairScan-GPL-3.0.txt`. Nobi's active capture stack uses CameraX, OpenCV contour detection and Android graphics. FairScan's segmentation model, OCR and PDF pipeline are absent.
- **DocScannerPro.** The inherited detector cites DocScannerPro in a contour fallback comment. That reference alone does not establish source reuse. Book mode, Hindi OCR, QR/barcodes and DOCX export are absent.
- **Docucraft.** The current library uses app-private PDF files, filename search, Android PDF thumbnails and an external viewer intent. Docucraft's document database, folders, tags, in-app viewer and full-text search are absent.
- **OSS Document Scanner and OpenScan.** Listed as workflow references in the roadmap. No modules from either are declared in the app build.

The source notices correct the verified FairScan attribution. The existing alpha03 APK predates this audit. Document exact upstream revisions and review GPL distribution and corresponding-source requirements before sharing a public build.

## Stage 1 implementation

- Home reads PDFs from app-private storage, sorts them by save time and supports filename search. A PDF can open in an installed viewer. Its menu can export a copy with Android's system file picker or open the share sheet.
- CameraX displays a rear-camera preview with an OpenCV edge overlay. A still image gets its own edge detection. The system photo picker can also import an image, including when camera permission is denied.
- Crop supports four adjustable corners. The editor corrects perspective, rotates in 90-degree steps and offers Original, Grayscale, Document and Vivid filters. When detection returns no candidate, the crop begins at the image bounds.
- Add page stages an edited page in cache. Save PDF writes the draft and current page to an A4 PDF, using each page's orientation. The scanner's draft button can save already-added pages. A temporary output file keeps incomplete writes out of the library.
- The Compose UI uses the reference's lavender colors, translucent cards, purple controls and floating navigation. Glasmio records the lavender backdrop and renders the dock, Scan and Save PDF surfaces. Home, Tools and Settings fade when switching tabs. Tools and Settings describe the available features without simulated OCR results or inactive settings switches.

## Liquid Glass concept refresh

The [approved concept mockup](design/nobi-liquid-glass-concept.png) informed the Home and camera refresh. The library shows actual PDF thumbnails, page counts, sizes and saved dates. The floating dock has four labeled tabs and a lavender selection indicator. The Scan button and camera controls compress on press, show a highlight at the touch point, and spring back with a fading ring. Camera controls use translucent Compose surfaces over the live preview. The Home Scan action leaves a short interval for the release animation before opening the camera.

## Dependency and license decisions

- CameraX 1.3.4, AndroidX Activity and Compose Material 3 provide lifecycle, capture and UI. OpenCV 4.12.0 provides contour analysis and publishes its Android AAR under Apache 2.0. The SDK provides image decoding, projective transforms, PDF generation and the document picker. [AndroidX license](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) · [OpenCV license](https://opencv.org/license/)
- Glasmio is Apache 2.0 and offers custom refraction, transitions and navigation components. The original 0.1.3 glass artifact declared minimum API 33. Alpha5 upgrades the published dependency to 0.1.4. The Gradle build retains AAR and Kotlin metadata checks. [Glasmio](https://github.com/NEORUAA/Glasmio)
- The inherited Rust prototype remains in `nobi-core`. The APK configuration no longer packages its prebuilt JNI binaries. The Android graphics path handles this stage's warp and filters. Benchmark before bringing Rust into the build.
- Tesseract upstream lists distinct Assamese (`asm`), Hindi (`hin`) and English (`eng`) trained models. Tesseract4Android is an Apache 2.0 candidate. Alpha8 integrates the engine and these three printed-text models. Audit model versions, bundled notices, fonts and source provenance before distributing a public build. The inherited scanner source should be compared with the reference projects before choosing the app's distribution license. [Tesseract model support](https://tesseract-ocr.github.io/tessdoc/Data-Files-in-different-versions.html) · [Tesseract4Android license](https://github.com/adaptech-cz/Tesseract4Android/blob/master/LICENSE)

## Build and Moto Edge 30 test

Use JDK 17, Android SDK Platform 37 and Build Tools 36.0.0. The Gradle wrapper is 9.4.1. The project pins AGP 9.2.0 and matching Kotlin and Compose compiler plugins at 2.4.21. The app targets API 34. Android Studio can populate `local.properties`; the current machine's SDK path is `/home/nava/.toolchains/android-sdk`. The first build may need access to Google Maven, Maven Central and the Gradle Plugin Portal. [AGP compatibility](https://developer.android.com/build/releases/agp-9-2-0-release-notes) · [Kotlin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html)

```bash
export JAVA_HOME=/home/nava/.toolchains/jdk-17
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Enable USB debugging under Developer options, connect the phone, and accept the RSA prompt. Use the APK from this workspace after the build succeeds. Unit-test task coverage depends on whether test sources are present.

1. Open Nobi Scan and verify the empty library. Check the floating dock and purple Scan button, then switch between Home, Tools and Settings. Confirm labels are legible and the fade runs smoothly. Hold and release Scan to check that the highlight follows the touch and the scanner opens once.
2. Tap **Scan**, grant camera access, and scan a sheet at an angle. Check the shutter response, overlay and flash states. Drag the crop corners and confirm the corrected preview looks rectangular.
3. Apply a filter, rotate, and tap **Add page**. Import a second image with the gallery button, crop it and tap **Save PDF**.
4. Open the saved PDF and check both pages, their order and orientation. Search for its name, export a copy, and open the share sheet.
5. Force-stop and reopen the app to check library persistence. Repeat with airplane mode and a locally stored image. Deny camera permission once to check that image import remains available.

## Verification and known issues

- Gradle passed `assembleDebug` and `lintDebug` and produced the alpha03 [debug APK](app/build/outputs/apk/debug/app-debug.apk). APK signature verification passed. The earlier alpha03 universal APK was about 203 MiB, declares minimum API 33 and contains the arm64 Glasmio and OpenCV libraries for the Moto Edge 30.
- SDK Platform 37 and Build Tools 36.0.0 were installed during the approved build. AGP reports deprecation warnings for the external Kotlin Android plugin and the legacy DSL.
- One Moto Edge 30 capture-to-PDF smoke test passed. Broader camera, animation and detector comparisons remain pending.
- PDFs are image-only. Opening one from the library requires an installed PDF viewer. Drafts do not recover after process death. Live edge accuracy, page memory use and camera compatibility need device measurements. The shutter is manual, with automatic edge detection.

## FairScan detector evaluation

The local FairScan build pins the [v1.2.0 TFLite release](https://github.com/pynicolas/fairscan-segmentation-model/releases/tag/v1.2.0). The downloaded binary was 4.69 MiB with SHA-256 `96e14d7e610dd0c27b768b228fbc553b4ec119ebe68f3a3594029a25400691d2`. The [model repository](https://github.com/pynicolas/fairscan-segmentation-model) declares GPLv3. The separate [training dataset](https://github.com/pynicolas/fairscan-dataset) declares CC BY-NC-SA 4.0. Review both before bundling or redistributing the model. The benchmark used locally generated fixtures and kept the released binary outside the app.

Desktop smoke results report intersection-over-union (IoU) against the known page outline and the median of seven warm runs. The OpenCV column is a Python approximation of Nobi's still-image contour detection. The FairScan column measures the thresholded segmentation mask, before FairScan's corner-fitting code. The flat book proxy includes a center seam and cannot test curved book pages.

| Synthetic fixture | Nobi OpenCV IoU / ms | FairScan mask IoU / ms |
| --- | ---: | ---: |
| High contrast | 0.991 / 3.3 | 0.986 / 40.7 |
| Oblique | 0.991 / 1.4 | 0.987 / 40.0 |
| Low contrast | Miss / 1.2 | 0.982 / 40.5 |
| Shadow | 0.991 / 2.0 | 0.990 / 39.3 |
| Open book proxy | 0.991 / 1.4 | 0.988 / 39.4 |

Reproduce with `python tools/benchmark_detectors.py /path/to/fairscan-v1.2.0.tflite` using Python 3.14, ai-edge-litert 2.4.0 and opencv-python-headless 5.1.0.95. Desktop measurements do not establish camera preview speed or corner accuracy on Android. ADB returned an empty device list. The result supports testing the model as a low-confidence fallback while retaining the existing detector as the default.

## Debug FairScan fallback

The debug variant tries FairScan v1.2.0 once on a captured or imported still when OpenCV confidence is at or below 0.72. The live camera analyzer continues to use OpenCV. The model runs locally through standalone LiteRT 1.4.1 on a background dispatcher. A convex quadrilateral fitted to the dominant mask goes to the existing crop editor. Missing models, rejected shapes and interpreter failures preserve the OpenCV suggestion or full-page crop. Release uses OpenCV alone.

The local model is in `app/src/debug/assets/fairscan-segmentation-model.tflite` and excluded from version control. For a fresh checkout, place the verified upstream model there before building the debug APK. For a baseline build, omit that asset. The Android comparison logs detector route, OpenCV confidence and duration, and total detection duration under `NobiDetection`. Read it with `adb logcat -s NobiDetection:I`. These timings exclude camera shutter latency and crop-screen rendering. On the Moto Edge 30, test the same photos in both builds and record how often the suggested corners need adjustment. The phone was absent from the device list during implementation.
Gradle passed `assembleDebug`, `assembleRelease`, and `lintDebug`. The signed debug APK contains the checksum-matched model and arm64 LiteRT. The unsigned release APK excludes the model and LiteRT native libraries. The current universal debug APK is about 256 MiB.

## Moto Edge 30 smoke test

ADB launched the installed debug build and the live camera preview opened. A camera capture triggered FairScan at OpenCV confidence 0.707. OpenCV took 62 ms and total detector time was 283 ms. The crop editor accepted the suggestion and opened the warped preview. Adding the page to a draft and saving from page review produced a valid one-page A4 PDF. This was one capture, so it does not establish typical latency or corner accuracy. The Home status icons had low contrast on the pale backdrop, and the dock appeared darker and more opaque than the mockup.

## Alpha04 UI refinement

System-bar icon contrast follows the active screen, with dark icons on the pale library and editor and white icons on camera and crop. The floating dock uses Glasmio's Clear material with a lighter tint, reduced surface opacity and equal-width tab targets. The library keeps up to 4 MiB of rendered PDF previews in memory to avoid rendering the same thumbnails again when revisiting it. The final alpha04 debug APK built and passed lint. It was installed on the Moto Edge 30 and the refreshed Home dock and system bars were checked on-device. The camera preview retained white status icons, and the existing test PDF survived the update.

## Alpha05 scan review

Camera capture and image import now open a corrected page preview before manual editing. Review offers Retake, Crop, Rotate, Filters, Keep scanning and Save PDF. Crop returns to review and retains the page filter and rotation. Earlier draft pages stay queued when retaking the current page. The Moto Edge 30 displayed the review after capture, opened Crop, and returned to review where the Filters menu opened. Keep scanning followed by Save PDF produced a verified two-page PDF on the phone. Debug assembly and lint passed.

## Alpha06 multipage editor

The draft review shows a scrollable thumbnail strip, selected page number and page count. Crop, Rotate, Filters, Rescan and Delete apply to the selected page. Move arrows reorder pages. Keep scanning and the camera page badge let users return to the same draft. Canceling a rescan keeps the original; a successful rescan replaces it in place. The source photos are cached and edits are applied per page on PDF export. Drafts remain session-scoped. Alpha06 was installed on the Moto Edge 30. The smoke test captured two pages, selected the earlier page, rotated it, rescanned it in place, reordered it, deleted it after confirmation and verified the saved PDF contained the remaining page. Debug build and lint passed.

## Alpha07 stack motion

The draft editor draws the selected scan above up to two offset sheets. Horizontal swipes switch pages, short drags spring back and thumbnails still provide direct selection. The camera draft control fans its page icon when reopening the camera with multiple pages. The dock uses a single animated glass-colored selection highlight between Home, Tools and Settings. These interactions use existing Compose and Glasmio dependencies. Debug assembly and lint passed. On the Moto Edge 30, Tools selected its dock highlight, a swipe changed page 2 to page 1, and the camera badge reopened that same two-page draft.

## Alpha08 draggable glass dock

Horizontal dragging on the dock lifts and moves the glass highlight with the finger. Release springs to the nearest tab and opens it. Cancel returns the highlight to the active tab. Tabs still support direct taps. On the Moto Edge 30, a mid-drag capture showed the highlight between the starting and destination tabs, the Home-to-Tools and Tools-to-Settings drags opened their destinations, and tapping Home returned to the library. Debug assembly and lint passed.

## Alpha09 light and dark appearance

The Home header has a moon/sun button to switch between Pearl + Amethyst and Graphite + Lilac. The initial appearance follows Android until a theme is selected. The selection is stored in app preferences and applies to Material 3 menus and dialogs, Glasmio surfaces, the library, Tools, Settings, and scan review. System bar icons adapt to light surfaces and remain white in the camera and crop editor. Scan previews retain their document colors. Debug assembly and lint passed. Alpha09 was installed on the Moto Edge 30, where live switches in both directions updated the dock and status icons. Dark mode persisted after force-stop and relaunch.

## Alpha10 Home controls

The dock shows Home, Tools and Settings and retains the draggable glass selection. The sole Scan document button above it uses a larger framed scanner icon and a raised glass pill. Saved PDFs have a Delete action in their overflow menu with a filename confirmation. Deletion applies to the library copy. On the Moto Edge 30, Cancel preserved a disposable PDF, confirming Delete removed it, and dragging the three-tab dock selected Tools. Debug assembly and lint passed.

## Alpha11 PDF actions and named export

Long-pressing a saved PDF opens the same actions menu as its overflow button. Actions include Share, Rename, Edit copy, Delete and Export a copy. Rename changes the library filename and checks for collisions. Edit copy renders the saved PDF pages into a new editable draft and preserves the source PDF. A saved edited copy is image-based. The scan review's Save PDF action opens a name field and offers Save PDF or Save & share. Save & share stores the named PDF in the library and opens Android’s share sheet. Alpha11 was installed on the Moto Edge 30. I checked long-press, renamed a disposable PDF, opened and saved an edited copy, verified both copies existed and that the Android share sheet opened, and verified a fresh scan prompted for its name. Build and lint passed.

## Alpha12 UI haptics

Compose requests Android touch feedback for dock taps and drag boundaries, page and filter changes, theme toggle, scan entry and shutter, crop confirmation, and successful PDF save, rename or deletion. Long-press uses Compose's existing feedback. The app follows Android’s touch feedback setting and uses no direct vibrator permission. The Moto Edge 30 reported completed Nobi Scan touch effects for dock dragging, tab selection, shutter and applied crop. Its lightest tick was unsupported, so the dock uses Android's standard segment tick. Debug build and lint passed.

## Alpha13 launcher artwork

The approved amethyst document logo is used in the adaptive and round launcher icons and the Nobi Scan Home header. A monochrome layer supports Android themed icons. Alpha13 built and passed lint, and the logo was verified in Android App info and the Home header on the Moto Edge 30.

## 1.0-alpha1 public tester build

The dedicated camera layout places the title and flash above a rounded camera viewport. The detection status, Import, shutter and Pages sit in a distinct tray below it. Detection uses a thin lilac outline and short corner brackets.

The release variant builds as `com.nobi.scan.alpha` and appears as **Nobi Scan Alpha**. It installs separately from the development package so testers can keep their existing library. It supports Android 13 and newer on ARM64, compresses native libraries inside the APK, and shrinks bytecode and resources. The release uses OpenCV for both preview and capture. Experimental FairScan model assets and LiteRT stay in debug builds.

For each public test build, increment `versionCode` and advance `versionName` to the next `1.0-alphaN`. Build with `./gradlew :app:assembleRelease :app:lintRelease` using Android SDK 37 and JDK 17. Sign the resulting release APK with the same public-alpha key using Android Build Tools `apksigner`. The signing material is stored outside the project in `~/.local/share/nobi-scan/public-alpha`. Keep it private and backed up to preserve future tester updates. Share the APK and matching source archive together, including the adapted FairScan files and notices. A complete dependency and source provenance audit remains part of public release preparation.

The signed 1.0-alpha1 APK is 14,861,689 bytes (14.9 MB), compared with 238,380,536 bytes for the preceding universal debug build. Signature verification passed. Release build and lint passed with zero lint errors. The release APK was installed alongside the development app on the Moto Edge 30. The camera displayed its dedicated preview and controls, the shutter opened Review scan, and saving returned to the library with a one-page PDF.

## 1.0-alpha2 Library-first Home

Home puts filename search above a two-column grid of saved PDFs. Cards use higher-resolution first-page thumbnails and retain tap-to-open, long-press and overflow actions. The single Scan document button floats above the Home, Tools and Settings dock. The dock uses Glasmio to sample the page content underneath while keeping its draggable tab highlight and haptics. The dedicated camera tray is accessible from Scan document.

Release assembly, lint and APK signature verification passed. The signed ARM64 APK is 14,890,361 bytes. The update installed and launched on the Moto Edge 30. Device checks covered the empty library, a no-match search, light and dark Home, dragging from Home to Tools, tapping Home and opening the camera tray. The device library was empty during testing, so populated-grid rendering, PDF actions in the new cards and dock refraction while scrolling PDF cards remain device checks.

## 1.0-alpha3 camera flash control

The scan header has a two-state flash button. Flash off uses a crossed-out icon and muted pill, and Flash on uses an amber pill. CameraX reports the current torch state to the label. With the torch enabled, the light illuminates the live preview and stays on during capture. The button is disabled while the request is running and on cameras without a flash.

Release assembly, lint and signature verification passed. The signed ARM64 APK is 14,890,361 bytes. Installed on the Moto Edge 30 and verified Off → On → Off with matching preview illumination. Closing the scanner while the light was on and reopening it restored Flash off.

## 1.0-alpha4 Merge PDFs

Tools now combines PDFs from the Nobi library and Android's multi-file picker. Drag the handles to order documents. The handle also exposes Move up and Move down as accessibility actions. Name the output and use Save PDF or Save & share. The output is committed as a new library PDF after a successful merge. Existing source files stay intact, and a name collision receives a numeric suffix.

Merge runs locally using PdfBox-Android 2.0.27.0 (Apache 2.0). The merge copies PDF page content and uses temporary storage for PDFBox scratch data. Encrypted inputs are unsupported. The upstream license and notices are included in the source and APK. OCR, page organization and PDF compression remain future Tools updates.

Release assembly, lint and signature verification passed. The signed ARM64 APK is 21,325,183 bytes. On the Moto Edge 30, we selected two PDFs in Files, dragged the second above the first, saved the result, opened it in Files by Google and exported it. Poppler reported two pages and extracted both original text strings in the reordered sequence. On the final signed APK we also combined a library PDF with a picked file, entered a new name and verified that Save & share saved a three-page PDF and showed Android's share sheet.

## 1.0-alpha5 touch feedback and Android 12L

Shared AndroidX touch feedback covers library and document actions, dock taps and dragging, scanning and previous-draft controls, flash, crop, review, filters, page reordering and Merge PDFs. Drag and selection feedback is discrete, and save feedback follows successful completion. Android's touch-feedback preference applies and the app requests no vibration permission.

The public APK remains ARM64-only and now declares minSdk 32 (Android 12L). Glasmio 0.1.4 provides simpler glass on Android 12L and uses shader refraction on supported Android 13+ devices. APK installation is density-independent. Home switches to a single PDF column below 320 dp so filenames retain room, and the scan header can wrap on narrow screens.

Release assembly, lint and signature verification passed. The signed APK is 21,382,527 bytes. The Moto Edge 30 verified touch feedback for navigation, Merge tool entry and controls, Scan entry, flash on/off and scanner back. Home, Tools, Merge PDFs and camera were smoke-tested at 240 and 640 dpi overrides, and the phone's original density was restored. The phone runs Android 17, so Android 12L installation and rendering still require an API 32 device test.

## 1.0-alpha6 Organize PDF

Tools opens a PDF from the Nobi library or Android's document picker. Page thumbnails show the order. Drag handles reorder pages and expose Move up and Move down accessibility actions. Remove pages from the draft or select pages and extract them into a new PDF. Both Save PDF and Save & share create a named library copy. The source stays intact.

Page copies use the existing offline PDFBox dependency and preserve PDF text, page geometry and rotation. The copies omit annotations and interactive forms. Encrypted files are unsupported. The selected input is staged in app cache while the editor is open and cleaned up when the editor exits normally.

The release build, lint and signature verification passed. The signed ARM64 APK is 21,419,391 bytes. On the Moto Edge 30, a PDF picked from Files was reordered from pages 1, 2, 3 to 3, 1 after removal. Poppler confirmed page order, searchable text and the retained 90-degree rotation of page 3. The organized copy was reopened from the library and its second page extracted. The new PDF contained only the expected text and Save & share opened Android's share sheet. The original input checksum matched and Merge PDFs still reordered its inputs.

## 1.0-alpha7 Split PDF by page range

Open Tools → Organize PDF → Split PDF. Enter two or more comma-separated page ranges or single pages, then choose a base filename. The dialog previews which pages go into each numbered part. Page numbers follow the current order shown in the editor. The split runs offline, saves each part to the private library, and keeps the source intact. Parts contain page content and omit annotations and interactive forms. Encrypted inputs remain unsupported.

Release assembly, lint and signature verification passed. The signed ARM64 APK is 21,431,679 bytes and declares Android 12L (API 32) as its minimum. Installed and tested on the Moto Edge 30. The dialog rejected an out-of-range entry, then previewed and saved two named parts after a page reorder. Export inspection verified page counts of two and one, searchable text in the selected order, and the original 90-degree page rotation. The source checksum matched after splitting.

## 1.0-alpha8 offline OCR preview

Review scan → Recognize text runs OCR on the current page with its crop, rotation and filter applied. Choose English, Assamese, Hindi or Mixed. Review the selectable Unicode text and use Copy text. All three models ship in the APK and are copied to private storage on first use. Prior recognition is kept only in the open dialog. The release has no Internet permission.

The engine pins Tesseract4Android 4.9.0 (Tesseract 5.5.1, Apache 2.0) to its JitPack publication. The LSTM models pin upstream tessdata_fast 4.1.0 commit `65727574dfcd264acbb0c3e07860e4e9e9b22185`. Upstream engine, model and native component licenses are in `app/src/main/assets/notices/ocr/`. Building from source requires access to JitPack for this dependency. Runtime is fully offline.

| Model | SHA-256 |
| --- | --- |
| Assamese | `299c7f6135ac72ca4820d4c39e3cf65b32b24127fc78c4938d11153adbb9fa77` |
| English | `7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2` |
| Hindi | `4c73ffc59d497c186b19d1e90f5d721d678ea6b2e277b719bee4e2af12271825` |

Release assembly, lint and signature verification passed. The signed ARM64 APK is 29,116,822 bytes and declares minimum API 32. On the Moto Edge 30, all four modes produced text from printed image fixtures in airplane mode with Wi-Fi off. The Assamese sample had a vowel-mark error and the mixed sample also showed an Assamese word error. Hindi Copy text placed the result on Android's clipboard. Airplane mode and Wi-Fi were restored after testing. Handwriting, broader capture quality, searchable PDF export and text search remain pending.

## 1.0-alpha9 on-page text detection

Review scan → Detect text highlights recognized words on the corrected page. Tap a word or drag across words, then Copy or Share the selection. Select all is available. The existing Recognize text dialog still offers a plain-text view. Both tools use the bundled English, Assamese, Hindi and Mixed OCR modes. Detection runs on the captured or imported page in review. Live preview OCR and searchable PDF export remain pending.

Release assembly, lint and signature verification passed. The ARM64 APK is 29,129,110 bytes and declares minimum API 32. On the Moto Edge 30, English highlights aligned after correcting the rotation of an imported image. Mixed-language highlights aligned on an imported English, Assamese and Hindi fixture. Both recognition checks ran in airplane mode with Wi-Fi off. Tap and drag selection worked, and the selected phrase appeared in the clipboard and Android share sheet. A new camera capture reached Detect text and exercised the no-text state. Successful recognition on a controlled physical camera target is still pending.

## 1.0-alpha10 Compress PDF

Tools → Compress PDF accepts a Nobi library PDF or an Android-picked PDF. High quality, Balanced and Smallest recalculate the resulting file size before saving. The tool recompresses eligible embedded images and preserves PDF page content, including existing searchable text. The named result is saved as a separate library copy, with optional sharing. If the result is the same size or larger, saving is disabled. Password-protected and digitally signed inputs are unsupported. Some image encodings and images with transparency are left untouched.

The release build, lint and APK signature verification passed. The signed ARM64 APK is 29,161,878 bytes. On Moto Edge 30, the 4,506,594-byte image-rich fixture previewed about 357 kB, 175 kB and 86.64 kB across the three presets. Save & share opened the Android share sheet. Export verification confirmed that the Smallest copy was 86,636 bytes and retained the original page size and exact searchable text. The source checksum matched. The library picker and no-savings state were checked with an existing text PDF.

## 1.0-alpha11 searchable PDF export

In Review scan → Save PDF, enable Searchable PDF and choose English, Assamese, Hindi or Mixed. The app applies the draft crop, rotation and filter, runs offline OCR for each page, and saves the scan image with invisible word text placed at the OCR coordinates. Ordinary image-only PDF export remains the default. A page with no recognition is still included. If the entire draft has no usable text, the dialog reports the failure and retains the draft. Use Find in a PDF viewer. Home still searches filenames.

PDFBox embeds subsets of Noto Sans, Noto Sans Bengali and Noto Sans Devanagari with Unicode mappings. The bundled fonts come from the local Debian fonts-noto-core package and their SIL OFL notice is in app assets. [Noto Bengali](https://github.com/notofonts/bengali) · [Noto Devanagari](https://github.com/notofonts/devanagari)

Release assembly, lint and signature verification passed. The signed ARM64 APK is 29,637,353 bytes. On Moto Edge 30, a two-page English and mixed-language draft exported in airplane mode with Wi-Fi off. Files by Google found the reference number 4728. Android PdfRenderer found and selected the English number, Assamese word অসমীয়া and Hindi word राजधानी on their expected pages. Poppler verified all three Unicode font mappings, and render-versus-bounds inspection confirmed alignment. The OCR output retained a known Assamese vowel-mark error in a different word. A separate ordinary PDF save also passed. Connectivity settings were restored.

## 1.0-alpha12 private Documents folder

The library creates `files/Nobi Scan/Documents/` on first open. All save routes use it. Existing library PDFs move from `files/documents/` by a same-storage rename without a second persistent copy. A filename collision gets a numeric suffix. If a move fails, the old file stays available through the library and the app retries migration on its next launch. The old directory is removed when empty. Sharing and opening issues new FileProvider URIs for moved files. Previously granted URIs for a moved file can require sharing again. Export a copy remains the explicit way to save outside the private library.

Release assembly, lint and signature verification passed. The signed ARM64 APK is 29,641,449 bytes. On Moto Edge 30, the upgrade preserved the existing searchable QA PDF. Its viewer grant used the new directory, and a rename, share, and export succeeded. The export matched the pre-upgrade SHA-256, and the original display name was restored. An additional QA scan saved to the library and was successfully deleted, with absence verified after restarting. The device was tested via upgrade, with no clear-storage or uninstall performed.

## 1.0-alpha13 Auto Capture

The camera tray has an Auto on/off switch. It starts off for each scanner visit. Auto checks that a four-corner candidate fits in the actual preview and that lighting, geometry and scene motion remain within thresholds for at least 900 ms. The tray shows guidance while waiting. Automatic capture uses the same shutter and review pipeline as manual capture. When the same scene is still visible after review, the scanner waits for the next page. Auto pauses in the photo picker and when the app is in the background.

Release assembly, lint and APK signature verification passed. Seven debug JVM tests passed, covering stable and angled pages, clipping, brightness, movement, a localized occlusion, stale frames and repeat capture suppression. The signed ARM64 APK is 29,649,641 bytes, minimum API 32. On the Moto Edge 30, enabling Auto captured one page, review opened, and returning to the same page showed Move to the next page without capturing again. The manual shutter added a second page even while Auto was waiting. The temporary draft was discarded. Low-light and hand-movement cases were simulated in the gate tests and still need broader physical camera checks.

## 1.0-alpha14 document crop and trim

Review scan → Crop opens Document crop for four-corner perspective correction. Trim works on the corrected page and supports draggable rectangular handles, pinch zoom, a fine straightening slider, quarter-turn rotation and Free, Original, and A4 ratio presets. Switching modes preserves draft adjustments. Trim Reset clears the rectangular crop, fine angle, rotation and viewport zoom. Applying stores edits in the draft and leaves the original image intact. Review, thumbnails, OCR and both PDF save paths share the same page rendering.

Release assembly, lint and signature verification passed. Eleven JVM tests passed, including four new crop geometry cases. The signed ARM64 APK is 29,735,657 bytes, minimum API 32. On Moto Edge 30, injected two-finger input zoomed the preview, A4 constrained the crop and survived a quarter turn, and Reset restored the trim controls. A tilted import was straightened and trimmed to retain the center line. Both on-device OCR and extracted searchable PDF text contained only KEEP CENTER 4728. The exported PDF rendered the center line alone. A fresh camera capture also opened both crop modes and returned to review after applying.

## Next steps

1. Run the Moto Edge 30 checks. Validate oblique captures, low contrast, large images, denied permissions and multipage export.
2. Expand OCR and searchable PDF testing with controlled camera captures and varied printed text.
3. Extend the library with folders and OCR text search.
4. Compare both detectors on the Moto Edge 30 with the same annotated photos. Include FairScan corner fitting, manual correction rates and end-to-end preview latency before selecting a default or fallback.
