package com.example.summerapp.data.llmd

enum class LlmdTarget(
    val preferenceValue: String,
    val displayName: String,
    val packageName: String,
) {
    Release(
        preferenceValue = "release",
        displayName = "Release",
        packageName = "com.storytellerf.llmd",
    ),
    Alpha(
        preferenceValue = "alpha",
        displayName = "Alpha",
        packageName = "com.storytellerf.llmd.alpha",
    ),
    Debug(
        preferenceValue = "debug",
        displayName = "Debug",
        packageName = "com.storytellerf.llmd.debug",
    );

    companion object {
        const val SERVICE_CLASS_NAME = "com.storytellerf.llmd.LlmdIpcService"

        fun fromPreference(value: String?): LlmdTarget =
            entries.firstOrNull { it.preferenceValue == value } ?: Release
    }
}
