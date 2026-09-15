# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools proguard configuration.

# Keep Room entities
-keep class com.storytellerf.summer.data.db.entity.** { *; }
