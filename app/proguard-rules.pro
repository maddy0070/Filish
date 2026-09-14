# FILISH keeps almost nothing by hand: there is no reflection-based DI, no
# serialization framework, and no dynamically-loaded classes. R8's defaults
# plus the Compose and Media3 consumer rules cover the application.
#
# The exceptions below are the two places where something outside our code
# reaches in by name.

# Entry points named in the manifest.
-keep class com.filish.FilishApp { *; }
-keep class com.filish.MainActivity { *; }
-keep class com.filish.feature.viewer.ViewerActivity { *; }

# Media3 selects decoders and extractors reflectively at runtime; stripping
# them produces a player that fails only on the formats that were removed,
# which is the worst possible failure mode to ship.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Line numbers make a crash report from a release build readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
