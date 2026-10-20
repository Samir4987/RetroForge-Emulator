package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.SaveStateDto
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

class SaveStateRepository(private val db: FirebaseFirestore) {

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

    fun observeSaveStates(gameId: String): Flow<List<SaveStateDto>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/save_states"
        val query = db.collection("users").document(uid).collection("save_states")
            .whereEqualTo("gameId", gameId)

        val flow = query.snapshots().map { snapshot ->
            snapshot.toObjects(SaveStateDto::class.java)
        }.catch { error ->
            if (error is Exception) {
                handleFirestoreError(error, OperationType.LIST, path)
            }
            throw error
        }

        flow.collect { emit(it) }
    }

    fun observeAllSaveStates(): Flow<List<SaveStateDto>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/save_states"
        val query = db.collection("users").document(uid).collection("save_states")

        val flow = query.snapshots().map { snapshot ->
            snapshot.toObjects(SaveStateDto::class.java)
        }.catch { error ->
            if (error is Exception) {
                handleFirestoreError(error, OperationType.LIST, path)
            }
            throw error
        }

        flow.collect { emit(it) }
    }

    suspend fun saveState(dto: SaveStateDto): Result<String> {
        return try {
            val uid = requireUserId()
            val docId = if (dto.id.isNotBlank()) dto.id else "${dto.gameId}_slot_${dto.slot}"
            val docRef = db.collection("users").document(uid).collection("save_states").document(docId)

            val payload = dto.toMap() + mapOf(
                "userId" to uid,
                "createdAt" to (dto.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: Exception) {
            val uid = auth.currentUser?.uid
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/save_states/${dto.id}")
            Result.failure(e)
        }
    }

    suspend fun deleteSaveState(saveStateId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("save_states").document(saveStateId)
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            val uid = auth.currentUser?.uid
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/save_states/$saveStateId")
            Result.failure(e)
        }
    }

    suspend fun getSaveState(saveStateId: String): Result<SaveStateDto?> {
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("save_states").document(saveStateId)
            val snapshot = docRef.get().await()
            Result.success(snapshot.toObject(SaveStateDto::class.java))
        } catch (e: Exception) {
            val uid = auth.currentUser?.uid
            handleFirestoreError(e, OperationType.GET, "users/$uid/save_states/$saveStateId")
            Result.failure(e)
        }
    }
}
