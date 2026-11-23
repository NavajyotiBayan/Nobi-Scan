# Source and dependency notes

- The document crop editor adapts five FairScan files under `ui/crop`. The preserved file notices license them under GPL-3.0-or-later. The local comparison reference is [FairScan commit 1f0fce51d74b1b2618a8e0a363961ff4906799f8](https://github.com/pynicolas/FairScan/tree/1f0fce51d74b1b2618a8e0a363961ff4906799f8). The original inherited import commit is unknown. The RC includes the attribution and license and uses OpenCV without FairScan's experimental model.
- CameraX 1.5.3 and the AndroidX Compose libraries are distributed under Apache 2.0. The pinned coordinates are in [the Gradle catalog](gradle/libs.versions.toml). [AndroidX source and license](https://github.com/androidx/androidx)
- OpenCV 4.12.0 provides the contour detector. Glasmio 0.1.4 provides glass surfaces. Both projects publish under Apache 2.0. [OpenCV source](https://github.com/opencv/opencv/tree/4.12.0) · [Glasmio source](https://github.com/NEORUAA/Glasmio)
- PdfBox-Android 2.0.27.0 provides PDF manipulation. Its license and NOTICE are included. [PdfBox-Android source](https://github.com/TomRoush/PdfBox-Android)
- Tesseract4Android 4.9.0 runs the OCR engine. Its native dependency notices and the tessdata_fast language model license are in `app/src/main/assets/notices/ocr/`. Model checksums and the exact upstream commit are in [PROJECT_STATUS.md](PROJECT_STATUS.md). [Tesseract4Android source](https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0)
- Noto Sans, Noto Sans Bengali, and Noto Sans Devanagari provide the searchable PDF font subsets. Their SIL OFL notices are bundled with the app. [Noto fonts](https://github.com/notofonts)
- Finish auditing exact source revisions and corresponding source for all shipped and inherited components before distributing Stable publicly. The candidate archive contains the app's buildable source and bundled assets.

The Alpha debug source can load a separately supplied FairScan segmentation model.
The distributed Alpha APK uses OpenCV and the optional model is outside this repository.
