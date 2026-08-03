package me.nillerusr.vpk

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.zip.CRC32

class VpkArchive private constructor(
    val version: Int,
    val entries: List<Entry>,
    private val directoryFile: File,
    private val chunks: Map<Int, File>,
    private val embeddedDataOffset: Long,
    private val ownsFiles: Boolean
) : AutoCloseable {
    data class Part(val archiveIndex: Int, val offset: Long, val length: Long)
    data class Entry(
        val path: String,
        val crc: Long,
        val preload: ByteArray,
        val parts: List<Part>
    ) {
        val size: Long get() = preload.size.toLong() + parts.sumOf { it.length }
    }

    fun extract(resolver: ContentResolver, destinationTree: Uri, progress: (Int, Int, String) -> Unit) {
        entries.forEachIndexed { index, entry ->
            validateArchivePath(entry.path)
            val segments = entry.path.split('/')
            var parent = destinationTree
            for (segment in segments.dropLast(1)) {
                parent = findOrCreateChild(resolver, parent, segment, DocumentsContract.Document.MIME_TYPE_DIR)
            }
            val outputUri = findOrCreateChild(resolver, parent, segments.last(), "application/octet-stream")
            val crc = CRC32()
            resolver.openOutputStream(outputUri, "wt")?.use { rawOutput ->
                BufferedOutputStream(rawOutput).use { output ->
                    output.write(entry.preload)
                    crc.update(entry.preload)
                    entry.parts.forEach { part -> copyPart(part, output, crc) }
                }
            } ?: error("Cannot create ${entry.path}")
            check(crc.value == entry.crc) { "CRC mismatch: ${entry.path}" }
            progress(index + 1, entries.size, entry.path)
        }
    }

    fun extract(destination: File, progress: (Int, Int, String) -> Unit) {
        require(destination.isDirectory || destination.mkdirs()) { "Cannot create ${destination.path}" }
        val root = destination.canonicalFile
        entries.forEachIndexed { index, entry ->
            validateArchivePath(entry.path)
            val output = File(root, entry.path).canonicalFile
            check(output.path.startsWith(root.path + File.separator)) { "Unsafe VPK path: ${entry.path}" }
            require(!output.exists()) { "File already exists: ${output.path}" }
            require(output.parentFile?.isDirectory == true || output.parentFile?.mkdirs() == true) {
                "Cannot create ${output.parent}"
            }
            val crc = CRC32()
            BufferedOutputStream(FileOutputStream(output)).use { stream ->
                stream.write(entry.preload)
                crc.update(entry.preload)
                entry.parts.forEach { part -> copyPart(part, stream, crc) }
            }
            check(crc.value == entry.crc) { "CRC mismatch: ${entry.path}" }
            progress(index + 1, entries.size, entry.path)
        }
    }

    private fun copyPart(part: Part, output: OutputStream, crc: CRC32) {
        val source = if (part.archiveIndex == EMBEDDED_INDEX) directoryFile else chunks[part.archiveIndex]
            ?: error("Missing archive chunk ${part.archiveIndex.toString().padStart(3, '0')}")
        RandomAccessFile(source, "r").use { input ->
            val sourceOffset = if (part.archiveIndex == EMBEDDED_INDEX) embeddedDataOffset + part.offset else part.offset
            check(sourceOffset >= 0 && part.length >= 0 && sourceOffset + part.length <= input.length()) {
                "VPK data range is outside ${source.name}"
            }
            input.seek(sourceOffset)
            var remaining = part.length
            val buffer = ByteArray(BUFFER_SIZE)
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                check(count > 0) { "Unexpected end of ${source.name}" }
                output.write(buffer, 0, count)
                crc.update(buffer, 0, count)
                remaining -= count
            }
        }
    }

    override fun close() {
        if (ownsFiles) {
            directoryFile.delete()
            chunks.values.forEach(File::delete)
        }
    }

    companion object {
        private const val SIGNATURE = 0x55aa1234L
        private const val EMBEDDED_INDEX = 0x7fff
        private const val ENTRY_TERMINATOR = 0xffff
        private const val MAX_TREE_SIZE = 64L * 1024L * 1024L
        private const val BUFFER_SIZE = 64 * 1024

        fun open(resolver: ContentResolver, uris: List<Uri>, cacheDir: File): VpkArchive {
            require(uris.isNotEmpty()) { "No VPK selected" }
            val files = uris.mapIndexed { index, uri ->
                val name = displayName(resolver, uri) ?: "selected_$index.vpk"
                val target = File.createTempFile("vpk_", ".cache", cacheDir)
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output, BUFFER_SIZE) }
                } ?: error("Cannot open $name")
                name to target
            }
            try {
                val directory = files.firstOrNull { it.first.lowercase(Locale.ROOT).endsWith("_dir.vpk") }
                    ?: files.firstOrNull { it.first.lowercase(Locale.ROOT).endsWith(".vpk") }
                    ?: error("No VPK file selected")
                val lowerName = directory.first.lowercase(Locale.ROOT)
                val base = if (lowerName.endsWith("_dir.vpk")) directory.first.dropLast(8) else directory.first.dropLast(4)
                val chunkPattern = Regex("^${Regex.escape(base)}_(\\d{3})\\.vpk$", RegexOption.IGNORE_CASE)
                val chunks = files.mapNotNull { (name, file) ->
                    chunkPattern.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to file }
                }.toMap()
                val unused = files.map { it.second }.toMutableSet().apply {
                    remove(directory.second)
                    removeAll(chunks.values.toSet())
                }
                unused.forEach(File::delete)
                return parse(directory.second, chunks, true)
            } catch (error: Throwable) {
                files.forEach { it.second.delete() }
                throw error
            }
        }

        fun open(file: File): VpkArchive {
            require(file.isFile && file.canRead()) { "Cannot read ${file.path}" }
            val directory = Regex("^(.+)_\\d{3}\\.vpk$", RegexOption.IGNORE_CASE).matchEntire(file.name)
                ?.groupValues?.get(1)?.let { File(file.parentFile, "${it}_dir.vpk") }
                ?.takeIf(File::isFile)
                ?: file
            val directoryName = directory.name.lowercase(Locale.ROOT)
            val chunks = if (directoryName.endsWith("_dir.vpk")) {
                val base = directory.name.dropLast(8)
                val pattern = Regex("^${Regex.escape(base)}_(\\d{3})\\.vpk$", RegexOption.IGNORE_CASE)
                directory.parentFile?.listFiles().orEmpty().mapNotNull { candidate ->
                    pattern.matchEntire(candidate.name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to candidate }
                }.toMap()
            } else {
                emptyMap()
            }
            return parse(directory, chunks, false)
        }

        private fun parse(directoryFile: File, chunks: Map<Int, File>, ownsFiles: Boolean = false): VpkArchive {
            RandomAccessFile(directoryFile, "r").use { input ->
                check(input.length() >= 12) { "VPK header is truncated" }
                check(input.readU32() == SIGNATURE) { "Invalid VPK signature" }
                val version = input.readU32().toInt()
                check(version == 1 || version == 2) { "Unsupported VPK version: $version" }
                val treeSize = input.readU32()
                check(treeSize in 1..MAX_TREE_SIZE) { "Invalid VPK tree size" }
                var embeddedSize = input.length() - 12 - treeSize
                if (version == 2) {
                    check(input.length() >= 28) { "VPK2 header is truncated" }
                    embeddedSize = input.readU32()
                    val archiveMd5Size = input.readU32()
                    val otherMd5Size = input.readU32()
                    val signatureSize = input.readU32()
                    check(28L + treeSize + embeddedSize + archiveMd5Size + otherMd5Size + signatureSize <= input.length()) {
                        "VPK2 sections exceed file size"
                    }
                }
                val headerSize = if (version == 1) 12L else 28L
                val treeEnd = headerSize + treeSize
                check(treeEnd <= input.length()) { "VPK tree is truncated" }
                val entries = ArrayList<Entry>()
                while (input.filePointer < treeEnd) {
                    val extension = input.readCString(treeEnd)
                    if (extension.isEmpty()) break
                    while (input.filePointer < treeEnd) {
                        val directory = input.readCString(treeEnd)
                        if (directory.isEmpty()) break
                        while (input.filePointer < treeEnd) {
                            val baseName = input.readCString(treeEnd)
                            if (baseName.isEmpty()) break
                            val crc = input.readU32()
                            val preloadSize = input.readU16()
                            val parts = ArrayList<Part>()
                            while (true) {
                                val archiveIndex = input.readU16()
                                if (archiveIndex == ENTRY_TERMINATOR) break
                                val offset = input.readU32()
                                val length = input.readU32()
                                parts += Part(archiveIndex, offset, length)
                            }
                            check(input.filePointer + preloadSize <= treeEnd) { "VPK preload data exceeds tree" }
                            val preload = ByteArray(preloadSize)
                            input.readFully(preload)
                            val fileName = if (extension == " ") baseName else "$baseName.$extension"
                            val path = if (directory == " ") fileName else "$directory/$fileName"
                            validateArchivePath(path)
                            parts.forEach { part ->
                                if (part.archiveIndex == EMBEDDED_INDEX) {
                                    check(part.offset + part.length <= embeddedSize) { "Embedded VPK entry exceeds data section: $path" }
                                }
                            }
                            entries += Entry(path, crc, preload, parts)
                        }
                    }
                }
                check(input.filePointer <= treeEnd) { "VPK tree parser exceeded its boundary" }
                return VpkArchive(version, entries, directoryFile, chunks, treeEnd, ownsFiles)
            }
        }

        private fun RandomAccessFile.readU16(): Int {
            val a = read()
            val b = read()
            check(a >= 0 && b >= 0) { "Unexpected end of VPK" }
            return a or (b shl 8)
        }

        private fun RandomAccessFile.readU32(): Long {
            val low = readU16()
            val high = readU16()
            return (low.toLong() or (high.toLong() shl 16)) and 0xffffffffL
        }

        private fun RandomAccessFile.readCString(limit: Long): String {
            val bytes = ByteArrayOutputStream()
            while (filePointer < limit) {
                val value = read()
                check(value >= 0) { "Unexpected end of VPK tree" }
                if (value == 0) return bytes.toString(Charsets.UTF_8.name())
                check(bytes.size() < 32768) { "VPK tree string is too long" }
                bytes.write(value)
            }
            error("Unterminated VPK tree string")
        }

        private fun validateArchivePath(path: String) {
            check(path.isNotBlank() && !path.startsWith('/') && !path.startsWith('\\')) { "Unsafe VPK path: $path" }
            check(!Regex("^[A-Za-z]:").containsMatchIn(path)) { "Unsafe VPK path: $path" }
            check(path.split('/').none { it.isEmpty() || it == "." || it == ".." || it.contains('\\') || it.contains('\u0000') }) {
                "Unsafe VPK path: $path"
            }
        }

        private fun displayName(resolver: ContentResolver, uri: Uri): String? =
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameColumn >= 0 && cursor.moveToFirst()) cursor.getString(nameColumn) else null
            }

        private fun findOrCreateChild(resolver: ContentResolver, parent: Uri, name: String, mimeType: String): Uri {
            val parentId = documentId(parent)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId)
            resolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameColumn) == name) {
                        return DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(idColumn))
                    }
                }
            }
            val parentDocument = DocumentsContract.buildDocumentUriUsingTree(parent, parentId)
            return DocumentsContract.createDocument(resolver, parentDocument, mimeType, name)
                ?: error("Cannot create $name")
        }

        private fun documentId(uri: Uri): String = try {
            DocumentsContract.getDocumentId(uri)
        } catch (_: IllegalArgumentException) {
            DocumentsContract.getTreeDocumentId(uri)
        }
    }
}

object VpkWriter {
    private const val SIGNATURE = 0x55aa1234L
    private const val EMBEDDED_INDEX = 0x7fff
    private const val TERMINATOR = 0xffff
    private const val BUFFER_SIZE = 64 * 1024

    private data class Source(
        val path: String,
        val uri: Uri? = null,
        val file: File? = null,
        var size: Long = 0,
        var crc: Long = 0,
        var offset: Long = 0
    )

    fun create(
        inputPaths: List<File>,
        output: File,
        version: Int,
        progress: (Int, Int, String) -> Unit
    ) {
        val outputPath = output.canonicalPath
        val sources = ArrayList<Source>()
        inputPaths.distinctBy { it.canonicalPath }.forEach { input ->
            require(input.exists() && input.canRead()) { "Cannot read ${input.path}" }
            if (input.isDirectory) {
                collectFileSources(input, input.name, outputPath, sources)
            } else if (input.canonicalPath != outputPath) {
                validateFileName(input.name)
                sources += Source(input.name, file = input)
            }
        }
        createFromSources(null, sources.sortedBy { it.path.lowercase(Locale.ROOT) }, null, output, version, progress)
    }

    fun create(
        resolver: ContentResolver,
        inputTree: Uri,
        output: Uri,
        version: Int,
        progress: (Int, Int, String) -> Unit
    ) {
        val sources = collectSources(resolver, inputTree, output).sortedBy { it.path.lowercase(Locale.ROOT) }
        createFromSources(resolver, sources, output, null, version, progress)
    }

    fun create(
        resolver: ContentResolver,
        inputFiles: List<Uri>,
        output: Uri,
        version: Int,
        progress: (Int, Int, String) -> Unit
    ) {
        val sources = inputFiles.distinct().filter { it != output }.map { uri ->
            val name = displayName(resolver, uri) ?: error("Cannot determine selected file name")
            validateFileName(name)
            Source(name, uri = uri)
        }.sortedBy { it.path.lowercase(Locale.ROOT) }
        createFromSources(resolver, sources, output, null, version, progress)
    }

    fun create(
        resolver: ContentResolver,
        inputTree: Uri,
        inputFiles: List<Uri>,
        output: Uri,
        version: Int,
        progress: (Int, Int, String) -> Unit
    ) {
        val sources = ArrayList<Source>()
        sources += collectSources(resolver, inputTree, output)
        sources += collectFileSources(resolver, inputFiles, output)
        createFromSources(resolver, sources.sortedBy { it.path.lowercase(Locale.ROOT) }, output, null, version, progress)
    }

    private fun createFromSources(
        resolver: ContentResolver?,
        sources: List<Source>,
        output: Uri?,
        outputFile: File?,
        version: Int,
        progress: (Int, Int, String) -> Unit
    ) {
        require(version == 1 || version == 2)
        require(sources.isNotEmpty()) { "No input files selected" }
        require(sources.map { it.path }.distinct().size == sources.size) { "Selected files contain duplicate names" }
        sources.forEachIndexed { index, source ->
            val crc = CRC32()
            var size = 0L
            openSource(resolver, source).use { input ->
                BufferedInputStream(input).use { buffered ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val count = buffered.read(buffer)
                        if (count < 0) break
                        crc.update(buffer, 0, count)
                        size += count
                    }
                }
            }
            require(size <= 0xffffffffL) { "File is too large for VPK: ${source.path}" }
            source.size = size
            source.crc = crc.value
            progress(index + 1, sources.size * 2, source.path)
        }
        var offset = 0L
        sources.forEach { source ->
            source.offset = offset
            offset += source.size
            require(offset <= 0xffffffffL) { "Embedded VPK data exceeds 4 GiB" }
        }
        val tree = buildTree(sources)
        require(tree.size.toLong() <= 0xffffffffL) { "VPK directory tree is too large" }
        val rawOutput = outputFile?.let(::FileOutputStream)
            ?: resolver?.openOutputStream(output ?: error("Missing output URI"), "wt")
        rawOutput?.use { openedOutput ->
            BufferedOutputStream(openedOutput).use { stream ->
                stream.writeU32(SIGNATURE)
                stream.writeU32(version.toLong())
                stream.writeU32(tree.size.toLong())
                if (version == 2) {
                    stream.writeU32(offset)
                    repeat(3) { stream.writeU32(0) }
                }
                stream.write(tree)
                sources.forEachIndexed { index, source ->
                    openSource(resolver, source).use { input -> input.copyTo(stream, BUFFER_SIZE) }
                    progress(sources.size + index + 1, sources.size * 2, source.path)
                }
            }
        } ?: error("Cannot create output VPK")
    }

    private fun openSource(resolver: ContentResolver?, source: Source): InputStream =
        source.file?.let(::FileInputStream)
            ?: resolver?.openInputStream(source.uri ?: error("Missing source URI"))
            ?: error("Cannot read ${source.path}")

    private fun collectFileSources(directory: File, relative: String, outputPath: String, result: MutableList<Source>) {
        validateFileName(directory.name)
        val children = directory.listFiles()?.sortedBy { it.name.lowercase(Locale.ROOT) }
            ?: error("Cannot list ${directory.path}")
        children.forEach { child ->
            if (child.canonicalPath == outputPath) return@forEach
            val path = "$relative/${child.name}"
            if (child.isDirectory) collectFileSources(child, path, outputPath, result)
            else {
                require(child.isFile && child.canRead()) { "Cannot read ${child.path}" }
                validateFileName(child.name)
                result += Source(path, file = child)
            }
        }
    }

    private fun collectSources(resolver: ContentResolver, tree: Uri, excluded: Uri): List<Source> {
        val result = ArrayList<Source>()
        val excludedId = try { DocumentsContract.getDocumentId(excluded) } catch (_: IllegalArgumentException) { null }
        fun visit(parent: Uri, relative: String) {
            val parentId = documentId(parent)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId)
            resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idColumn)
                    val name = cursor.getString(nameColumn)
                    val mime = cursor.getString(mimeColumn)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    val path = if (relative.isEmpty()) name else "$relative/$name"
                    if (id == excludedId) continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) visit(uri, path)
                    else {
                        validateFileName(name)
                        result += Source(path, uri = uri)
                    }
                }
            }
        }
        visit(tree, "")
        return result
    }

    private fun collectFileSources(resolver: ContentResolver, inputFiles: List<Uri>, output: Uri): List<Source> =
        inputFiles.distinct().filter { it != output }.map { uri ->
            val name = displayName(resolver, uri) ?: error("Cannot determine selected file name")
            validateFileName(name)
            Source(name, uri = uri)
        }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameColumn >= 0 && cursor.moveToFirst()) cursor.getString(nameColumn) else null
        }

    private fun validateFileName(name: String) {
        require(name.isNotBlank() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\') && !name.contains('\u0000')) {
            "Unsafe file name: $name"
        }
    }

    private fun documentId(uri: Uri): String = try {
        DocumentsContract.getDocumentId(uri)
    } catch (_: IllegalArgumentException) {
        DocumentsContract.getTreeDocumentId(uri)
    }

    private fun buildTree(sources: List<Source>): ByteArray {
        data class NameParts(val source: Source, val extension: String, val directory: String, val base: String)
        val parts = sources.map { source ->
            val slash = source.path.lastIndexOf('/')
            val directory = if (slash < 0) " " else source.path.substring(0, slash)
            val file = source.path.substring(slash + 1)
            val dot = file.lastIndexOf('.')
            val extension = if (dot <= 0 || dot == file.lastIndex) " " else file.substring(dot + 1)
            val base = if (extension == " ") file else file.substring(0, dot)
            NameParts(source, extension, directory, base)
        }
        require(parts.map { "${it.directory}/${it.base}.${it.extension}" }.distinct().size == parts.size) {
            "The selected directory contains duplicate VPK paths"
        }
        val output = ByteArrayOutputStream()
        parts.groupBy { it.extension }.toSortedMap().forEach { (extension, byExtension) ->
            output.writeCString(extension)
            byExtension.groupBy { it.directory }.toSortedMap().forEach { (directory, byDirectory) ->
                output.writeCString(directory)
                byDirectory.sortedBy { it.base.lowercase(Locale.ROOT) }.forEach { item ->
                    output.writeCString(item.base)
                    output.writeU32(item.source.crc)
                    output.writeU16(0)
                    output.writeU16(EMBEDDED_INDEX)
                    output.writeU32(item.source.offset)
                    output.writeU32(item.source.size)
                    output.writeU16(TERMINATOR)
                }
                output.write(0)
            }
            output.write(0)
        }
        output.write(0)
        return output.toByteArray()
    }

    private fun OutputStream.writeU16(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
    }

    private fun OutputStream.writeU32(value: Long) {
        writeU16((value and 0xffff).toInt())
        writeU16(((value ushr 16) and 0xffff).toInt())
    }

    private fun ByteArrayOutputStream.writeCString(value: String) {
        write(value.toByteArray(Charsets.UTF_8))
        write(0)
    }
}
