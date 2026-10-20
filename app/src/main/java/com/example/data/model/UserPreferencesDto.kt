package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserPreferencesDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val activeSkin: String = "CLASSIC_DMG",
    val hapticsEnabled: Boolean = true,
    val fastForwardSpeed: Long = 2L,
    val rewindCapacityFrames: Long = 300L,
    val audioEnabled: Boolean = true,
    val audioVolume: Long = 80L,
    val screenScaleMode: String = "ASPECT_FIT",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "activeSkin" to activeSkin,
            "hapticsEnabled" to hapticsEnabled,
            "fastForwardSpeed" to fastForwardSpeed,
            "rewindCapacityFrames" to rewindCapacityFrames,
            "audioEnabled" to audioEnabled,
            "audioVolume" to audioVolume,
            "screenScaleMode" to screenScaleMode
        )
    }
}
