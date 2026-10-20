package com.example.data.model

data class RetroGame(
    val id: String,
    val title: String,
    val system: String, // "GB", "GBC", "GBA", "NDS", "N64"
    val developer: String,
    val description: String,
    val isBuiltIn: Boolean = true,
    val romFileName: String? = null,
    val romBytes: ByteArray? = null,
    val iconEmoji: String = "🎮",
    val releaseYear: String = "1995"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RetroGame
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
