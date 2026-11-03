package com.example.emulator

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileOutputStream

data class N3dsSystemDataImportSummary(
    val filesCopied: Int,
    val bytesCopied: Long,
    val destination: File
)

/**
 * Imports a user-selected Azahar user-data folder into app-private storage.
 * The source must be a folder named Azahar or contain an Azahar child folder.
 */
object N3dsSystemDataImporter {
    private const val MAX_DEPTH = 32
    private const val MAX_FILES = 100_000
    private const val MAX_TOTAL_BYTES = 8L * 1024L * 1024L * 1024L

    fun importFromTree(
        context: Context,
        treeUri: Uri,
        destination: File
    ): Result<N3dsSystemDataImportSummary> = runCatching {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val selectedRoot = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        val selectedName = queryDisplayName(context, selectedRoot)
        val sourceRoot = if (selectedName.equals("Azahar", ignoreCase = true)) {
            selectedRoot
        } else {
            findChildDirectory(context, treeUri, selectedRoot, "Azahar") ?: selectedRoot
        }

        val rootNames = listChildren(context, treeUri, sourceRoot)
            .filter { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
            .map { it.displayName.lowercase() }
            .toSet()
        require(rootNames.any { it in setOf("sysdata", "nand", "sdmc") }) {
            "Select the Azahar folder containing sysdata, nand, or sdmc."
        }

        val parent = destination.parentFile
            ?: throw IllegalArgumentException("The app-private Azahar directory has no parent.")
        check(parent.exists() || parent.mkdirs()) { "Could not create the app-private 3DS directory." }

        val staging = File(parent, "${destination.name}.importing")
        val backup = File(parent, "${destination.name}.previous")
        // Recover the last known-good directory if the app was interrupted
        // between moving the old destination aside and activating the staging copy.
        if (!destination.exists() && backup.exists()) {
            check(backup.renameTo(destination)) {
                "Could not recover the previous Azahar system-data directory."
            }
        }
        staging.deleteRecursively()
        if (backup.exists()) {
            check(backup.deleteRecursively()) { "Could not clear the previous Azahar import backup." }
        }
        check(staging.mkdirs()) { "Could not create a temporary import directory." }

        val stats = CopyStats()
        try {
            copyDirectory(context, treeUri, sourceRoot, staging, stats, depth = 0)
            if (destination.exists()) {
                check(destination.renameTo(backup)) { "Could not preserve the existing Azahar data before import." }
            }
            if (!staging.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                throw IllegalStateException("Could not activate the imported Azahar data.")
            }
            backup.deleteRecursively()
        } catch (failure: Exception) {
            staging.deleteRecursively()
            if (!destination.exists() && backup.exists()) backup.renameTo(destination)
            throw failure
        }

        N3dsSystemDataImportSummary(stats.files, stats.bytes, destination)
    }

    private fun copyDirectory(
        context: Context,
        treeUri: Uri,
        source: Uri,
        destination: File,
        stats: CopyStats,
        depth: Int
    ) {
        require(depth <= MAX_DEPTH) { "Azahar data folder is nested too deeply." }
        check(destination.exists() || destination.mkdirs()) {
            "Could not create ${destination.name} in the app-private directory."
        }

        listChildren(context, treeUri, source).forEach { child ->
            val safeName = sanitizeName(child.displayName)
            if (safeName.isBlank()) return@forEach
            require(safeName != "." && safeName != "..") {
                "Azahar data contains an unsafe directory or file name."
            }
            val output = File(destination, safeName)
            check(!output.exists()) {
                "Azahar data contains duplicate names after sanitization: $safeName"
            }
            if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                copyDirectory(context, treeUri, child.uri, output, stats, depth + 1)
            } else {
                check(stats.files < MAX_FILES) { "Azahar data contains too many files." }
                val input = context.contentResolver.openInputStream(child.uri)
                    ?: throw IllegalStateException("Could not read ${child.displayName} from the selected provider.")
                input.use { sourceStream ->
                    FileOutputStream(output).use { fileOutput ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = sourceStream.read(buffer)
                            if (count < 0) break
                            stats.bytes += count
                            require(stats.bytes <= MAX_TOTAL_BYTES) {
                                "Azahar data exceeds the 8 GiB import safety limit."
                            }
                            fileOutput.write(buffer, 0, count)
                        }
                        fileOutput.fd.sync()
                    }
                }
                stats.files++
            }
        }
    }

    private fun findChildDirectory(
        context: Context,
        treeUri: Uri,
        parent: Uri,
        name: String
    ): Uri? = listChildren(context, treeUri, parent).firstOrNull {
        it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
            it.displayName.equals(name, ignoreCase = true)
    }?.uri

    private fun listChildren(context: Context, treeUri: Uri, parent: Uri): List<ChildDocument> {
        val documentId = DocumentsContract.getDocumentId(parent)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val result = mutableListOf<ChildDocument>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idColumn)
                val name = cursor.getString(nameColumn) ?: continue
                val mime = cursor.getString(mimeColumn) ?: "application/octet-stream"
                result += ChildDocument(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    displayName = name,
                    mimeType = mime
                )
            }
        } ?: throw IllegalStateException("The selected provider could not list the Azahar folder.")
        return result
    }

    private fun queryDisplayName(context: Context, documentUri: Uri): String? {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return context.contentResolver.query(documentUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
            } else null
        }
    }

    private fun sanitizeName(name: String): String =
        name.filter { it != '/' && it != '\\' && it.code >= 0x20 }
            .trim()
            .take(240)

    private data class ChildDocument(val uri: Uri, val displayName: String, val mimeType: String)
    private data class CopyStats(var files: Int = 0, var bytes: Long = 0L)
}
