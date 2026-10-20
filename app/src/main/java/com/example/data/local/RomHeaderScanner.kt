package com.example.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.emulator.RetroSystem
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

object RomHeaderScanner {

    data class ScanResult(
        val title: String,
        val system: RetroSystem,
        val gameCode: String,
        val publisher: String,
        val genre: String,
        val releaseYear: String,
        val cartridgeMapper: String,
        val headerChecksum: String,
        val boxArtCoverKey: String,
        val boxArtPrimaryColor: Long,
        val boxArtSecondaryColor: Long,
        val iconEmoji: String
    )

    fun scanUri(context: Context, uri: Uri): ScannedRomEntity? {
        return try {
            var fileName = "Unknown_ROM.bin"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val headerBytes = ByteArray(1024)
            val bytesRead = inputStream.read(headerBytes)
            inputStream.close()

            if (bytesRead < 64) return null
            if (fileSize <= 0) fileSize = bytesRead.toLong()

            val ext = fileName.substringAfterLast('.', "").lowercase()
            val result = parseHeader(headerBytes, ext, fileName)

            val id = "rom_${UUID.nameUUIDFromBytes((fileName + fileSize).toByteArray())}"

            ScannedRomEntity(
                id = id,
                title = result.title,
                system = result.system.code,
                fileName = fileName,
                fileUri = uri.toString(),
                fileSize = fileSize,
                gameCode = result.gameCode,
                publisher = result.publisher,
                genre = result.genre,
                releaseYear = result.releaseYear,
                cartridgeMapper = result.cartridgeMapper,
                headerChecksum = result.headerChecksum,
                boxArtCoverKey = result.boxArtCoverKey,
                boxArtPrimaryColor = result.boxArtPrimaryColor,
                boxArtSecondaryColor = result.boxArtSecondaryColor,
                iconEmoji = result.iconEmoji,
                isBuiltIn = false,
                scannedAt = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun parseHeader(bytes: ByteArray, extension: String, fileName: String): ScanResult {
        return when (extension) {
            "gb", "gbc" -> parseGbGbcHeader(bytes, extension, fileName)
            "gba" -> parseGbaHeader(bytes, fileName)
            "nds", "ds" -> parseNdsHeader(bytes, fileName)
            "n64", "z64", "v64" -> parseN64Header(bytes, extension, fileName)
            else -> {
                // Infer from header signature or file name
                if (bytes.size > 0x134 && (bytes[0x0104].toInt() and 0xFF) == 0xCE) {
                    parseGbGbcHeader(bytes, "gb", fileName)
                } else if (bytes.size > 0xB2 && (bytes[0x00B2].toInt() and 0xFF) == 0x96) {
                    parseGbaHeader(bytes, fileName)
                } else {
                    fallbackResult(fileName, extension)
                }
            }
        }
    }

    private fun parseGbGbcHeader(bytes: ByteArray, ext: String, fileName: String): ScanResult {
        if (bytes.size < 0x150) return fallbackResult(fileName, ext)

        // Title at 0x0134 - 0x0143
        val rawTitle = String(bytes, 0x0134, 16, StandardCharsets.US_ASCII).replace("\u0000", "").trim()
        val title = cleanTitle(if (rawTitle.isNotBlank()) rawTitle else fileName.substringBeforeLast('.'))

        // GBC flag at 0x0143
        val cgbFlag = bytes[0x0143].toInt() and 0xFF
        val isGbc = (cgbFlag == 0x80 || cgbFlag == 0xC0) || ext == "gbc"
        val system = if (isGbc) RetroSystem.GBC else RetroSystem.GB

        // Cartridge type at 0x0147
        val cartTypeByte = bytes[0x0147].toInt() and 0xFF
        val mapper = when (cartTypeByte) {
            0x00 -> "ROM ONLY"
            0x01, 0x02, 0x03 -> "MBC1"
            0x05, 0x06 -> "MBC2"
            0x0F, 0x10, 0x11, 0x12, 0x13 -> "MBC3+RTC"
            0x19, 0x1A, 0x1B, 0x1C, 0x1D -> "MBC5"
            0x20 -> "MBC6"
            0x22 -> "MBC7"
            else -> "MBC-Cart"
        }

        // Licensee code at 0x014B
        val oldLic = bytes[0x014B].toInt() and 0xFF
        val publisher = if (oldLic == 0x01) "Nintendo" else if (oldLic == 0x08) "Capcom" else "Licensed Retro"
        val checksum = String.format("%02X", bytes[0x014D].toInt() and 0xFF)

        val (genre, icon) = inferGenre(title)
        val pColor = if (isGbc) 0xFF8E44AD else 0xFF9BBB0F
        val sColor = if (isGbc) 0xFF2C3E50 else 0xFF262626

        return ScanResult(
            title = title,
            system = system,
            gameCode = if (isGbc) "CGB-${checksum}" else "DMG-${checksum}",
            publisher = publisher,
            genre = genre,
            releaseYear = if (isGbc) "1998" else "1989",
            cartridgeMapper = mapper,
            headerChecksum = "0x$checksum",
            boxArtCoverKey = if (isGbc) "cover_gbc" else "cover_gb",
            boxArtPrimaryColor = pColor,
            boxArtSecondaryColor = sColor,
            iconEmoji = icon
        )
    }

    private fun parseGbaHeader(bytes: ByteArray, fileName: String): ScanResult {
        if (bytes.size < 0xC0) return fallbackResult(fileName, "gba")

        val rawTitle = String(bytes, 0x00A0, 12, StandardCharsets.US_ASCII).replace("\u0000", "").trim()
        val title = cleanTitle(if (rawTitle.isNotBlank()) rawTitle else fileName.substringBeforeLast('.'))

        val gameCode = String(bytes, 0x00AC, 4, StandardCharsets.US_ASCII).replace("\u0000", "").trim()
        val makerCode = String(bytes, 0x00B0, 2, StandardCharsets.US_ASCII).replace("\u0000", "").trim()

        val publisher = when (makerCode) {
            "01" -> "Nintendo"
            "08" -> "Capcom"
            "41" -> "Ubisoft"
            "52" -> "Activision"
            "69" -> "Electronic Arts"
            "70" -> "Atari"
            "78" -> "THQ"
            "A4" -> "Konami"
            "B2" -> "Bandai"
            else -> "GBA Publisher"
        }

        val checksum = String.format("%02X", bytes[0x00BD].toInt() and 0xFF)
        val (genre, icon) = inferGenre(title)

        return ScanResult(
            title = title,
            system = RetroSystem.GBA,
            gameCode = if (gameCode.isNotBlank()) gameCode else "AGB-$checksum",
            publisher = publisher,
            genre = genre,
            releaseYear = "2001",
            cartridgeMapper = "ARM7TDMI 32-Bit",
            headerChecksum = "0x$checksum",
            boxArtCoverKey = "cover_gba",
            boxArtPrimaryColor = 0xFF2980B9,
            boxArtSecondaryColor = 0xFF1A5276,
            iconEmoji = icon
        )
    }

    private fun parseNdsHeader(bytes: ByteArray, fileName: String): ScanResult {
        if (bytes.size < 0x80) return fallbackResult(fileName, "nds")

        val rawTitle = String(bytes, 0x0000, 12, StandardCharsets.US_ASCII).replace("\u0000", "").trim()
        val title = cleanTitle(if (rawTitle.isNotBlank()) rawTitle else fileName.substringBeforeLast('.'))
        val gameCode = String(bytes, 0x000C, 4, StandardCharsets.US_ASCII).replace("\u0000", "").trim()

        val (genre, icon) = inferGenre(title)

        return ScanResult(
            title = title,
            system = RetroSystem.NDS,
            gameCode = if (gameCode.isNotBlank()) "NTR-$gameCode" else "NTR-ROM",
            publisher = "Nintendo DS",
            genre = genre,
            releaseYear = "2004",
            cartridgeMapper = "Dual ARM9/ARM7 + Touch",
            headerChecksum = "NTR-OK",
            boxArtCoverKey = "cover_nds",
            boxArtPrimaryColor = 0xFF16A085,
            boxArtSecondaryColor = 0xFF117A65,
            iconEmoji = icon
        )
    }

    private fun parseN64Header(bytes: ByteArray, ext: String, fileName: String): ScanResult {
        if (bytes.size < 0x40) return fallbackResult(fileName, ext)

        // Detect endianness and adjust bytes
        val magic = ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)

        val normalized = ByteArray(0x40)
        System.arraycopy(bytes, 0, normalized, 0, 0x40)

        if (magic == 0x37804012) {
            // Byte-swapped (.v64)
            for (i in 0 until 0x40 step 2) {
                val temp = normalized[i]
                normalized[i] = normalized[i + 1]
                normalized[i + 1] = temp
            }
        } else if (magic == 0x40123780) {
            // Little-endian (.n64)
            for (i in 0 until 0x40 step 4) {
                val b0 = normalized[i]
                val b1 = normalized[i + 1]
                val b2 = normalized[i + 2]
                val b3 = normalized[i + 3]
                normalized[i] = b3
                normalized[i + 1] = b2
                normalized[i + 2] = b1
                normalized[i + 3] = b0
            }
        }

        val rawTitle = String(normalized, 0x0020, 20, StandardCharsets.US_ASCII).replace("\u0000", "").trim()
        val title = cleanTitle(if (rawTitle.isNotBlank()) rawTitle else fileName.substringBeforeLast('.'))

        val cartId = String(normalized, 0x003C, 2, StandardCharsets.US_ASCII).replace("\u0000", "").trim()
        val country = normalized[0x003E].toInt().toChar()

        val (genre, icon) = inferGenre(title)

        return ScanResult(
            title = title,
            system = RetroSystem.N64,
            gameCode = "NUS-${cartId}$country",
            publisher = "Nintendo 64",
            genre = genre,
            releaseYear = "1996",
            cartridgeMapper = "Reality 64-Bit RCP",
            headerChecksum = "NUS-001",
            boxArtCoverKey = "cover_n64",
            boxArtPrimaryColor = 0xFFD35400,
            boxArtSecondaryColor = 0xFF962D00,
            iconEmoji = icon
        )
    }

    private fun fallbackResult(fileName: String, ext: String): ScanResult {
        val cleanName = cleanTitle(fileName.substringBeforeLast('.'))
        val system = RetroSystem.fromExtension(ext)
        val (genre, icon) = inferGenre(cleanName)

        val (pColor, sColor) = when (system) {
            RetroSystem.GB -> Pair(0xFF9BBB0F, 0xFF262626)
            RetroSystem.GBC -> Pair(0xFF8E44AD, 0xFF2C3E50)
            RetroSystem.GBA -> Pair(0xFF2980B9, 0xFF1A5276)
            RetroSystem.NDS -> Pair(0xFF16A085, 0xFF117A65)
            RetroSystem.N64 -> Pair(0xFFD35400, 0xFF962D00)
        }

        return ScanResult(
            title = cleanName,
            system = system,
            gameCode = "${system.code}-AUTO",
            publisher = "RetroForge Library",
            genre = genre,
            releaseYear = system.releaseYear.toString(),
            cartridgeMapper = "Standard ROM",
            headerChecksum = "AUTO",
            boxArtCoverKey = "cover_${system.code.lowercase()}",
            boxArtPrimaryColor = pColor,
            boxArtSecondaryColor = sColor,
            iconEmoji = icon
        )
    }

    private fun cleanTitle(raw: String): String {
        // Strip common dump tags: (USA), (Europe), [!], (v1.1)
        var s = raw.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").trim()
        s = s.replace('_', ' ').replace('-', ' ').trim()
        s = s.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return if (s.isNotBlank()) s else "Retro Title"
    }

    private fun inferGenre(title: String): Pair<String, String> {
        val t = title.lowercase()
        return when {
            t.contains("kart") || t.contains("race") || t.contains("speed") || t.contains("drive") || t.contains("f1") ->
                Pair("Racing", "🏎️")
            t.contains("quest") || t.contains("zelda") || t.contains("rpg") || t.contains("fantasy") || t.contains("mon") ->
                Pair("Role-Playing (RPG)", "🗡️")
            t.contains("mario") || t.contains("sonic") || t.contains("jump") || t.contains("land") || t.contains("cavern") ->
                Pair("Platformer", "🍄")
            t.contains("star") || t.contains("space") || t.contains("wars") || t.contains("poly") || t.contains("flight") ->
                Pair("3D Flight / Space", "🚀")
            t.contains("arena") || t.contains("touch") || t.contains("fight") || t.contains("combat") || t.contains("brawl") ->
                Pair("Action Combat", "🥊")
            t.contains("tetris") || t.contains("puzzle") || t.contains("block") || t.contains("dr") ->
                Pair("Puzzle", "🧩")
            else ->
                Pair("Retro Adventure", "🎮")
        }
    }
}
