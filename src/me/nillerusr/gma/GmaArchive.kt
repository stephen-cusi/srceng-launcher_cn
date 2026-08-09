package me.nillerusr.gma

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.zip.CRC32

class GmaArchive private constructor(
    val version: Int,
    val title: String,
    val description: String,
    val author: String,
    val entries: List<Entry>,
    private val source: File,
    private val dataOffset: Long
) : AutoCloseable {
    data class Entry(val path: String, val size: Long, val crc: Long, val offset: Long)

    fun read(path: String): ByteArray? {
        val entry = entries.firstOrNull { it.path == path } ?: return null
        if (entry.size > MAX_PREVIEW_SIZE) error("File is too large to preview")
        RandomAccessFile(source, "r").use { input ->
            input.seek(dataOffset + entry.offset)
            val output = ByteArrayOutputStream(entry.size.toInt())
            val crc = CRC32()
            copyRange(input, output, entry.size, crc)
            check(entry.crc == 0L || crc.value == entry.crc) { "CRC mismatch: ${entry.path}" }
            return output.toByteArray()
        }
    }

    fun extract(
        destination: File,
        selectedPaths: Set<String>? = null,
        overwritePaths: Set<String> = emptySet(),
        skippedPaths: Set<String> = emptySet(),
        progress: (Int, Int, String) -> Unit
    ) {
        require(destination.isDirectory || destination.mkdirs()) { "Cannot create ${destination.path}" }
        val root = destination.canonicalFile
        val chosen = if (selectedPaths == null) entries else entries.filter { entry ->
            selectedPaths.any { selected -> entry.path == selected || entry.path.startsWith("$selected/") }
        }
        require(chosen.isNotEmpty()) { "No files selected" }
        val extractedEntries = chosen.filterNot { it.path in skippedPaths }
        RandomAccessFile(source, "r").use { input ->
            extractedEntries.forEachIndexed { index, entry ->
                val output = File(root, entry.path).canonicalFile
                check(output.path.startsWith(root.path + File.separator)) { "Unsafe GMA path: ${entry.path}" }
                require(!output.exists() || entry.path in overwritePaths) { "File already exists: ${output.path}" }
                require(output.parentFile?.isDirectory == true || output.parentFile?.mkdirs() == true) {
                    "Cannot create ${output.parent}"
                }
                input.seek(dataOffset + entry.offset)
                val crc = CRC32()
                BufferedOutputStream(FileOutputStream(output)).use { stream ->
                    copyRange(input, stream, entry.size, crc)
                }
                check(entry.crc == 0L || crc.value == entry.crc) { "CRC mismatch: ${entry.path}" }
                progress(index + 1, extractedEntries.size, entry.path)
            }
        }
    }

    override fun close() = Unit

    companion object {
        private const val MAX_STRING = 1024 * 1024
        private const val MAX_PREVIEW_SIZE = 16L * 1024L * 1024L
        private const val BUFFER_SIZE = 64 * 1024

        fun open(file: File): GmaArchive {
            require(file.isFile && file.canRead()) { "Cannot read ${file.path}" }
            RandomAccessFile(file, "r").use { input ->
                require(input.length() >= 5 + 16 + 4) { "GMA header is truncated" }
                val ident = ByteArray(4).also(input::readFully)
                require(ident.contentEquals("GMAD".toByteArray(Charsets.US_ASCII))) { "Invalid GMA signature" }
                val version = input.readUnsignedByte()
                require(version in 1..3) { "Unsupported GMA version: $version" }
                input.readI64()
                input.readI64()
                if (version > 1) while (input.readCString().isNotEmpty()) Unit
                val title = input.readCString()
                val description = input.readCString()
                val author = input.readCString()
                input.readI32()
                val entries = ArrayList<Entry>()
                var offset = 0L
                val paths = HashSet<String>()
                while (true) {
                    val number = input.readU32()
                    if (number == 0L) break
                    val path = input.readCString().replace('\\', '/')
                    validatePath(path)
                    require(paths.add(path.lowercase(Locale.ROOT))) { "Duplicate GMA path: $path" }
                    val size = input.readI64()
                    require(size >= 0) { "Invalid GMA file size: $path" }
                    val crc = input.readU32()
                    entries += Entry(path, size, crc, offset)
                    offset = Math.addExact(offset, size)
                    require(entries.size <= 1_000_000) { "GMA contains too many files" }
                }
                val dataOffset = input.filePointer
                require(dataOffset + offset + 4 == input.length()) { "GMA data size does not match its index" }
                val expectedCrc = input.apply { seek(input.length() - 4) }.readU32()
                if (expectedCrc != 0L) {
                    val actualCrc = crcFile(file, file.length() - 4)
                    require(actualCrc == expectedCrc) { "GMA archive CRC mismatch" }
                }
                return GmaArchive(version, title, description, author, entries, file, dataOffset)
            }
        }

        private fun validatePath(path: String) {
            require(path.isNotBlank() && !path.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(path)) {
                "Unsafe GMA path: $path"
            }
            require(path.split('/').none { it.isEmpty() || it == "." || it == ".." || it.contains('\u0000') }) {
                "Unsafe GMA path: $path"
            }
        }

        private fun copyRange(input: RandomAccessFile, output: OutputStream, length: Long, crc: CRC32) {
            var remaining = length
            val buffer = ByteArray(BUFFER_SIZE)
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                require(count > 0) { "Unexpected end of GMA data" }
                output.write(buffer, 0, count)
                crc.update(buffer, 0, count)
                remaining -= count
            }
        }

        private fun crcFile(file: File, length: Long): Long {
            val crc = CRC32()
            BufferedInputStream(FileInputStream(file)).use { input ->
                var remaining = length
                val buffer = ByteArray(BUFFER_SIZE)
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    require(count > 0) { "Unexpected end of GMA" }
                    crc.update(buffer, 0, count)
                    remaining -= count
                }
            }
            return crc.value
        }

        private fun RandomAccessFile.readCString(): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val value = read()
                require(value >= 0) { "Unexpected end of GMA string" }
                if (value == 0) return bytes.toString(Charsets.UTF_8.name())
                require(bytes.size() < MAX_STRING) { "GMA string is too long" }
                bytes.write(value)
            }
        }

        private fun RandomAccessFile.readU32(): Long = readI32().toLong() and 0xffffffffL
        private fun RandomAccessFile.readI32(): Int = readUnsignedByte() or (readUnsignedByte() shl 8) or
            (readUnsignedByte() shl 16) or (readUnsignedByte() shl 24)
        private fun RandomAccessFile.readI64(): Long {
            val low = readU32()
            val high = readU32()
            return low or (high shl 32)
        }
    }
}
