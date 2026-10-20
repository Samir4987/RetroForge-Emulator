package com.example.emulator

enum class RetroSystem(
    val code: String,
    val displayName: String,
    val releaseYear: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val colorDepth: String,
    val defaultAspect: Float,
    val isDualScreen: Boolean = false,
    val hasAnalogStick: Boolean = false,
    val supportedExtensions: List<String>
) {
    GB(
        code = "GB",
        displayName = "Game Boy",
        releaseYear = 1989,
        screenWidth = 160,
        screenHeight = 144,
        colorDepth = "4 Shades Monochrome",
        defaultAspect = 160f / 144f,
        isDualScreen = false,
        hasAnalogStick = false,
        supportedExtensions = listOf("gb")
    ),
    GBC(
        code = "GBC",
        displayName = "Game Boy Color",
        releaseYear = 1998,
        screenWidth = 160,
        screenHeight = 144,
        colorDepth = "15-bit RGB555 (32K Colors)",
        defaultAspect = 160f / 144f,
        isDualScreen = false,
        hasAnalogStick = false,
        supportedExtensions = listOf("gbc")
    ),
    GBA(
        code = "GBA",
        displayName = "Game Boy Advance",
        releaseYear = 2001,
        screenWidth = 240,
        screenHeight = 160,
        colorDepth = "15-bit RGB555 (Affine Mode 7)",
        defaultAspect = 240f / 160f,
        isDualScreen = false,
        hasAnalogStick = false,
        supportedExtensions = listOf("gba")
    ),
    NDS(
        code = "NDS",
        displayName = "Nintendo DS",
        releaseYear = 2004,
        screenWidth = 256,
        screenHeight = 192,
        colorDepth = "18-bit Dual Screen + Touch",
        defaultAspect = 256f / 192f,
        isDualScreen = true,
        hasAnalogStick = false,
        supportedExtensions = listOf("nds", "ds")
    ),
    N64(
        code = "N64",
        displayName = "Nintendo 64",
        releaseYear = 1996,
        screenWidth = 320,
        screenHeight = 240,
        colorDepth = "32-bit RGBA 3D Polygons",
        defaultAspect = 4f / 3f,
        isDualScreen = false,
        hasAnalogStick = true,
        supportedExtensions = listOf("n64", "z64", "v64")
    );

    companion object {
        fun fromExtension(ext: String): RetroSystem {
            val cleanExt = ext.lowercase().trimStart('.')
            return entries.firstOrNull { it.supportedExtensions.contains(cleanExt) } ?: GBA
        }

        fun fromCode(code: String): RetroSystem {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: GB
        }
    }
}
