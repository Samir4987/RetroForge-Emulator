package com.example.emulator

import androidx.compose.ui.graphics.Color

enum class ControllerSkin(
    val id: String,
    val displayName: String,
    val description: String,
    val bodyColor: Color,
    val bezelColor: Color,
    val dpadColor: Color,
    val buttonAColor: Color,
    val buttonBColor: Color,
    val buttonXColor: Color,
    val buttonYColor: Color,
    val accentColor: Color,
    val textColor: Color
) {
    CLASSIC_DMG(
        id = "CLASSIC_DMG",
        displayName = "Classic DMG-01",
        description = "Original 1989 retro gray shell with burgundy action buttons",
        bodyColor = Color(0xFFC4C2B4),
        bezelColor = Color(0xFF6B6E7B),
        dpadColor = Color(0xFF262626),
        buttonAColor = Color(0xFF8F1E44),
        buttonBColor = Color(0xFF8F1E44),
        buttonXColor = Color(0xFF751636),
        buttonYColor = Color(0xFF751636),
        accentColor = Color(0xFF8F1E44),
        textColor = Color(0xFF2B2D42)
    ),
    ATOMIC_PURPLE(
        id = "ATOMIC_PURPLE",
        displayName = "Atomic Purple",
        description = "Iconic transparent deep purple casing with circuit aesthetics",
        bodyColor = Color(0xFF4A346E),
        bezelColor = Color(0xFF2C1E45),
        dpadColor = Color(0xFF1E172E),
        buttonAColor = Color(0xFF9B4F96),
        buttonBColor = Color(0xFF793B75),
        buttonXColor = Color(0xFF9B4F96),
        buttonYColor = Color(0xFF793B75),
        accentColor = Color(0xFFD66FE0),
        textColor = Color(0xFFF3E8FF)
    ),
    SNES_CLASSIC(
        id = "SNES_CLASSIC",
        displayName = "Super 16-Bit",
        description = "Two-tone light gray console body with classic colored diamond buttons",
        bodyColor = Color(0xFFDCDAE3),
        bezelColor = Color(0xFF8A8896),
        dpadColor = Color(0xFF4E4D57),
        buttonAColor = Color(0xFFE52521), // Red
        buttonBColor = Color(0xFFF4C430), // Yellow
        buttonXColor = Color(0xFF0066B2), // Blue
        buttonYColor = Color(0xFF009640), // Green
        accentColor = Color(0xFF5D5B8D),
        textColor = Color(0xFF23222B)
    ),
    CYBER_NEON(
        id = "CYBER_NEON",
        displayName = "Cyber Neon",
        description = "Futuristic OLED pitch black with vibrant glowing cyan and magenta",
        bodyColor = Color(0xFF0D0E15),
        bezelColor = Color(0xFF161828),
        dpadColor = Color(0xFF00F0FF),
        buttonAColor = Color(0xFFFF007F),
        buttonBColor = Color(0xFF00F0FF),
        buttonXColor = Color(0xFFFFE600),
        buttonYColor = Color(0xFFB026FF),
        accentColor = Color(0xFF00F0FF),
        textColor = Color(0xFFFFFFFF)
    ),
    STEALTH_DARK(
        id = "STEALTH_DARK",
        displayName = "Stealth Obsidian",
        description = "Matte gunmetal carbon texture with minimalist satin buttons",
        bodyColor = Color(0xFF1C1D24),
        bezelColor = Color(0xFF2A2B36),
        dpadColor = Color(0xFF383A48),
        buttonAColor = Color(0xFF505366),
        buttonBColor = Color(0xFF434555),
        buttonXColor = Color(0xFF505366),
        buttonYColor = Color(0xFF434555),
        accentColor = Color(0xFF8E92AA),
        textColor = Color(0xFFD1D4E0)
    ),
    NDS_DUAL(
        id = "NDS_DUAL",
        displayName = "DS Polar White",
        description = "Clean modern dual-screen aesthetic with stylus touch controls",
        bodyColor = Color(0xFFF0F1F5),
        bezelColor = Color(0xFF333540),
        dpadColor = Color(0xFF5A5C69),
        buttonAColor = Color(0xFF2C3E50),
        buttonBColor = Color(0xFF34495E),
        buttonXColor = Color(0xFF2C3E50),
        buttonYColor = Color(0xFF34495E),
        accentColor = Color(0xFF3498DB),
        textColor = Color(0xFF2C3E50)
    ),
    N64_TRIDENT(
        id = "N64_TRIDENT",
        displayName = "Ultra 64 Trident",
        description = "Analog control stick, blue A, red B, and yellow directional C-buttons",
        bodyColor = Color(0xFF383942),
        bezelColor = Color(0xFF212228),
        dpadColor = Color(0xFF626470),
        buttonAColor = Color(0xFF2575FC), // Blue A
        buttonBColor = Color(0xFFEE2A24), // Red B
        buttonXColor = Color(0xFFFFCC00), // C-Up
        buttonYColor = Color(0xFFFFCC00), // C-Left
        accentColor = Color(0xFFFFCC00), // C-Buttons
        textColor = Color(0xFFF0F0F5)
    );

    companion object {
        fun fromId(id: String): ControllerSkin {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CLASSIC_DMG
        }
    }
}
