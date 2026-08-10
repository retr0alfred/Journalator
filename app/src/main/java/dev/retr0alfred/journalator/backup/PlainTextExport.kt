package dev.retr0alfred.journalator.backup

import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * The unprotected export. Deliberately separate from [JrnlxArchive] and deliberately plain:
 * anything that reads text files can read this, which is the whole point and also the whole
 * risk. The UI gates it behind a warning that says so in those words.
 */
object PlainTextExport {

    const val MIME_TYPE = "text/plain"
    const val FILE_EXTENSION = "txt"

    fun write(output: OutputStream, entries: List<BackupEntry>) {
        output.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.write("Journalator export — plain text, not protected in any way")
            writer.newLine()
            writer.write("${entries.size} entries")
            writer.newLine()
            entries.sortedBy { it.date }.forEach { entry ->
                writer.newLine()
                writer.write("======== ${entry.date} ========")
                writer.newLine()
                entry.mood?.let {
                    writer.write("mood: $it")
                    writer.newLine()
                }
                writer.write(entry.text)
                writer.newLine()
            }
            writer.flush()
        }
    }
}
