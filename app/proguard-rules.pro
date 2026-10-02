# PdfBox-Android loads fonts and resources reflectively.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# PDFium JNI bindings.
-keep class io.legere.pdfiumandroid.** { *; }
