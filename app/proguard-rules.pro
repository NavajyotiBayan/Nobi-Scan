# Nobi-Scan Proguard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# OpenCV JNI looks up Java classes and members by their original names.
-keep class org.opencv.** { *; }

# PDFBox's optional JPEG 2000 codec is used for raster decoding/encoding.
# Merge copies PDF page streams and does not install that codec.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder

# Tesseract and Leptonica JNI resolve Java classes and methods by name.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
