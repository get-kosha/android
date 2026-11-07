package app.getkosha.data

import android.content.Context
import android.util.AtomicFile
import java.io.File

/**
 * The last answer to each read, as JSON files on this phone, so screens open at once and work offline
 * ("Showing what this phone saved, while Kosha checks for anything new"). Keys name the read, such as
 * "meta" or "overview/2026-11/eur". Signing out deletes them all.
 * ponytail: one file per key with no size cap; months a person never revisits stay until sign-out.
 */
class Cache(context: Context) {
    private val dir = File(context.filesDir, "cache").apply { mkdirs() }

    private fun file(key: String) = AtomicFile(File(dir, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json"))

    fun read(key: String): String? = runCatching { String(file(key).readFully()) }.getOrNull()

    fun write(key: String, json: String) {
        val f = file(key)
        val out = f.startWrite()
        runCatching {
            out.write(json.toByteArray())
            f.finishWrite(out)
        }.onFailure { f.failWrite(out) }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
