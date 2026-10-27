package com.example.emulator

/**
 * Describes real Libretro core candidates for RetroForge.
 *
 * This catalog intentionally does not claim a core is available just because a
 * system is recognized. A core becomes available only after its native library
 * is packaged for the device ABI and the Libretro frontend bridge is present.
 */
data class LibretroCoreSpec(
    val system: RetroSystem,
    val libraryFileName: String,
    val upstreamUrl: String,
    val extensions: Set<String>,
    val requiresNativeFrontend: Boolean = true
) {
    companion object {
        val supportedCandidates: List<LibretroCoreSpec> = listOf(
            LibretroCoreSpec(
                system = RetroSystem.GB,
                libraryFileName = "skyemu_libretro_android.so",
                upstreamUrl = "https://github.com/libretro/SkyEmu",
                extensions = setOf("gb")
            ),
            LibretroCoreSpec(
                system = RetroSystem.GBC,
                libraryFileName = "skyemu_libretro_android.so",
                upstreamUrl = "https://github.com/libretro/SkyEmu",
                extensions = setOf("gbc")
            ),
            LibretroCoreSpec(
                system = RetroSystem.GBA,
                libraryFileName = "mgba_libretro_android.so",
                upstreamUrl = "https://github.com/libretro/mgba",
                extensions = setOf("gba")
            ),
            LibretroCoreSpec(
                system = RetroSystem.NDS,
                libraryFileName = "skyemu_libretro_android.so",
                upstreamUrl = "https://github.com/libretro/SkyEmu",
                extensions = setOf("nds", "ds")
            ),
            LibretroCoreSpec(
                system = RetroSystem.N64,
                libraryFileName = "mupen64plus_next_libretro_android.so",
                upstreamUrl = "https://github.com/libretro/mupen64plus-libretro-nx",
                extensions = setOf("n64", "z64", "v64")
            )
        )

        fun forSystem(system: RetroSystem): LibretroCoreSpec? =
            supportedCandidates.firstOrNull { it.system == system }

        fun forExtension(extension: String): LibretroCoreSpec? {
            val normalized = extension.lowercase().trimStart('.')
            return supportedCandidates.firstOrNull { normalized in it.extensions }
        }
    }
}
