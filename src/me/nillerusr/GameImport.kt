package me.nillerusr

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Copies a game directory into app-private storage with incremental resume support. */
object GameImport {
    interface Progress {
        fun onProgress(copiedBytes: Long, totalBytes: Long, files: Int)
    }

    class Stats(var bytes: Long = 0L, var files: Int = 0)

    fun sourceName(path: String): String =
        File(path).name.trim().ifEmpty { "srceng" }

    fun targetDir(context: Context, path: String): File =
        File(context.filesDir, sourceName(path))

    fun computeStats(dir: File): Stats {
        val stats = Stats()
        visitFiles(dir) { file ->
            stats.bytes += file.length()
            stats.files++
        }
        return stats
    }

    private fun visitFiles(dir: File, visit: (File) -> Unit) {
        if (!dir.isDirectory) return
        val queue = ArrayDeque<File>()
        queue.addLast(dir)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val children = try { current.listFiles() } catch (_: Throwable) { null } ?: continue
            for (child in children) {
                try {
                    if (child.isDirectory) queue.addLast(child) else if (child.isFile) visit(child)
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Recursively copies [source] to [target].
     * Files with matching size and mtime are skipped, so an interrupted or
     * repeated import only copies the remainder. Returns false when cancelled
     * or when any file fails to copy.
     */
    fun copyDir(source: File, target: File, progress: Progress, isCancelled: () -> Boolean): Boolean {
        if (!source.isDirectory) return false
        val total = computeStats(source)
        val copied = Stats()
        return try {
            copyRecursive(source, target, total.bytes, copied, progress, isCancelled) && !isCancelled()
        } catch (_: Throwable) {
            false
        }
    }

    private fun copyRecursive(
        source: File,
        target: File,
        totalBytes: Long,
        copied: Stats,
        progress: Progress,
        isCancelled: () -> Boolean
    ): Boolean {
        if (isCancelled()) return false
        val children = try { source.listFiles() } catch (_: Throwable) { null } ?: return true
        for (child in children) {
            if (isCancelled()) return false
            val destination = File(target, child.name)
            if (child.isDirectory) {
                try { destination.mkdirs() } catch (_: Throwable) {}
                if (!copyRecursive(child, destination, totalBytes, copied, progress, isCancelled)) return false
                continue
            }
            if (!child.isFile) continue
            if (!copyFile(child, destination)) return false
            copied.bytes += child.length()
            copied.files++
            progress.onProgress(copied.bytes, totalBytes, copied.files)
        }
        return true
    }

    private fun copyFile(source: File, target: File): Boolean {
        if (target.exists() &&
            target.length() == source.length() &&
            target.lastModified() == source.lastModified()
        ) return true
        val parent = target.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) return false
        val okay: Boolean = try {
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(1 shl 20)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                    try { output.fd.sync() } catch (_: Throwable) {}
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
        if (!okay) {
            try { target.delete() } catch (_: Throwable) {}
            return false
        }
        return try {
            target.setLastModified(source.lastModified())
            true
        } catch (_: Throwable) {
            true
        }
    }
}
