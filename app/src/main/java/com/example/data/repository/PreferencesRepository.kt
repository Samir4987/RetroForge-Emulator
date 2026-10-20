package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.UserPreferencesDto
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PreferencesRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observePreferences(): Flow<UserPreferencesDto> = flow {
        val uid = requireUserId()
        val path = "users/$uid/preferences"
        val docRef = db.collection("users").document(uid).collection("preferences").document("default")

        val flow = docRef.snapshots().map { snapshot ->
            snapshot.toObject(UserPreferencesDto::class.java) ?: UserPreferencesDto(userId = uid)
        }.catch { error ->
            if (error is Exception) {
                handleFirestoreError(error, OperationType.GET, path)
            }
            throw error
        }

        flow.collect { emit(it) }
    }

    suspend fun savePreferences(preferences: UserPreferencesDto): Result<Unit> {
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("preferences").document("default")
            val payload = preferences.toMap() + mapOf(
                "userId" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            val uid = auth.currentUser?.uid
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/preferences/default")
            Result.failure(e)
        }
    }
}
