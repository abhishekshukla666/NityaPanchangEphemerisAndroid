package com.nityapanchangam.ephemeris

import android.content.Context
import java.io.File

/**
 * The Swiss Ephemeris C library needs a real filesystem path (fopen), which a library AAR's
 * assets/ can't provide directly — this copies the bundled .se1 data files out to app-private
 * storage once, then hands back that directory for swe_set_ephe_path.
 */
internal object EphemerisAssets {

    private const val ASSET_DIR = "ephe"
    private val FILE_NAMES = listOf("sepl_18.se1", "semo_18.se1")

    fun ensureExtracted(context: Context): String {
        val targetDir = File(context.filesDir, ASSET_DIR)
        if (!targetDir.exists()) targetDir.mkdirs()

        for (name in FILE_NAMES) {
            val targetFile = File(targetDir, name)
            // AAPT deflates .se1 as a regular asset (no recognized "already compressed"
            // extension), so AssetManager.openFd() can't read it — only the decompressing
            // open() stream works, which means there's no cheap way to compare sizes without
            // fully reading the asset. A plain existence check is enough since this data never
            // changes across app versions.
            if (targetFile.exists()) continue

            context.assets.open("$ASSET_DIR/$name").use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return targetDir.absolutePath
    }
}
