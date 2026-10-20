package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class SaveStateDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val gameId: String = "",
    val gameTitle: String = "",
    val system: String = "GB",
    val slot: Long = 0L,
    val label: String = "",
    val stateData: String = "",
    val thumbnailBase64: String = "",
    val playTimeSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "userId" to userId,
            "gameId" to gameId,
            "gameTitle" to gameTitle,
            "system" to system,
            "slot" to slot,
            "stateData" to stateData
        )
        if (label.isNotBlank()) map["label"] = label
        if (thumbnailBase64.isNotBlank()) map["thumbnailBase64"] = thumbnailBase64
        if (playTimeSeconds > 0) map["playTimeSeconds"] = playTimeSeconds
        return map
    }
}
