package io.github.karljuderojas.freepdf.files

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Overwrites a file without leaving it half written if the write fails part way. The new content
 * is first produced in full into a scratch file, so a failure while producing it never touches the
 * target at all. Then, for a plain file, the scratch file is renamed over it, which is atomic.
 * A content:// target (a document from the storage access framework) cannot be renamed, so its
 * previous bytes are copied to a backup first and put back if the write fails.
 */
object SafeWrite {

    /** Writes what [write] produces over [target]. Throws if the file could not be written. */
    fun write(context: Context, target: Uri, write: (OutputStream) -> Unit) {
        val file = if (target.scheme == "file" || target.scheme == null) target.path?.let(::File) else null
        if (file != null && canWriteBeside(file)) {
            writeFile(file, write)
        } else {
            val resolver = context.contentResolver
            writeStream(
                scratchDir = context.cacheDir,
                openInput = { resolver.openInputStream(target) },
                // "wt" truncates so a shorter file leaves no stale bytes.
                openOutput = { resolver.openOutputStream(target, "wt") ?: error("Cannot write $target") },
                write = write,
            )
        }
    }

    /** Writes the whole of [source] over [target] (see [write]). */
    fun write(context: Context, target: Uri, source: File) =
        write(context, target) { out -> source.inputStream().use { it.copyTo(out) } }

    /** Produces the content into a temporary file beside [target], then renames it over [target]. */
    fun writeFile(target: File, write: (OutputStream) -> Unit) {
        val dir = target.absoluteFile.parentFile ?: throw IOException("$target has no folder")
        val temp = File(dir, ".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temp).use { out ->
                write(out)
                out.flush()
                runCatching { out.fd.sync() }
            }
            moveOver(temp, target)
        } finally {
            temp.delete()
        }
    }

    /**
     * Writes to a target that only offers streams: the content is produced into a scratch file
     * under [scratchDir], the target's current bytes are backed up through [openInput] (skipped
     * when it cannot be read), the scratch file is copied through [openOutput], and if that copy
     * fails the backup is copied back the same way before the failure is rethrown.
     */
    fun writeStream(
        scratchDir: File,
        openInput: () -> InputStream?,
        openOutput: () -> OutputStream,
        write: (OutputStream) -> Unit,
    ) {
        val scratch = File(scratchDir, "save").apply { mkdirs() }
        val id = UUID.randomUUID()
        val staged = File(scratch, "staged-$id.tmp")
        val backup = File(scratch, "backup-$id.tmp")
        try {
            staged.outputStream().use(write)
            val backedUp = runCatching {
                openInput()?.use { input -> backup.outputStream().use { input.copyTo(it) } } != null
            }.getOrDefault(false)
            try {
                copy(staged, openOutput)
            } catch (e: Throwable) {
                if (backedUp) runCatching { copy(backup, openOutput) }
                throw e
            }
        } finally {
            staged.delete()
            backup.delete()
        }
    }

    private fun copy(from: File, openOutput: () -> OutputStream) {
        openOutput().use { out ->
            from.inputStream().use { it.copyTo(out) }
            out.flush()
        }
    }

    /** True if a temporary file can go beside [file], which the rename needs. */
    private fun canWriteBeside(file: File): Boolean {
        val dir = file.absoluteFile.parentFile ?: return false
        return dir.isDirectory && dir.canWrite()
    }

    private fun moveOver(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
