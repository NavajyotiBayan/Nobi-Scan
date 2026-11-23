package com.nobi.scan.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.CoroutineContext

internal data class GitHubUpdate(
    val tag: String,
    val versionCode: Long,
    val apkName: String,
    val bytes: Long,
    val sha256: String,
    val notes: String
)

internal class UpdateUnavailable(message: String) : IOException(message)

internal class GitHubUpdates(private val context: Context) {
    private val packageManager = context.packageManager
    private val installed = packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    private val cache = File(context.cacheDir, "verified-updates")

    suspend fun check(): GitHubUpdate? = withContext(Dispatchers.IO) {
        val response = readText(
            URL("https://api.github.com/repos/$REPOSITORY/releases?per_page=30"),
            1_048_576
        )
        val releases = JSONArray(response)
        var newest: GitHubUpdate? = null
        for (index in 0 until releases.length()) {
            ensureActive()
            val release = releases.getJSONObject(index)
            val tag = release.optString("tag_name")
            if (release.optBoolean("draft") || !STABLE_TAG.matches(tag)) continue
            val assets = release.optJSONArray("assets") ?: continue
            val apk = "NobiScan-$tag-arm64.apk"
            val size = findAsset(assets, apk)?.optLong("size") ?: continue
            if (size !in 1..MAX_APK_BYTES) continue
            val metadataAsset = findAsset(assets, "update.json") ?: continue
            if (metadataAsset.optLong("size") !in 1..MAX_METADATA_BYTES.toLong()) continue
            // Only fetch assets from a validated release path on the configured repository.
            val metadata = try {
                JSONObject(readText(assetUrl(tag, "update.json"), MAX_METADATA_BYTES))
            } catch (_: JSONException) {
                continue
            }
            val code = metadata.optLong("versionCode")
            val hash = metadata.optString("sha256").lowercase()
            if (!releaseMatches(tag, metadata.optString("versionName"), metadata.optString("apk"), hash,
                    code, maxOf(installed.longVersionCode, newest?.versionCode ?: 0L))) continue
            newest = GitHubUpdate(tag, code, apk, size, hash, release.optString("body").take(1800))
        }
        newest
    }

    suspend fun download(update: GitHubUpdate, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        if (!cache.isDirectory && !cache.mkdirs()) throw IOException("Could not create update cache.")
        cache.listFiles()?.filter { it.extension in setOf("part", "apk") }?.forEach { it.delete() }
        val temporary = File.createTempFile("download-", ".part", cache)
        val target = File(cache, update.apkName)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val job = coroutineContext
            downloadTo(assetUrl(update.tag, update.apkName), temporary, update, digest, job, progress)
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash != update.sha256) throw IOException("The update checksum did not match.")
            ensureActive()
            // Parse with the APK suffix for OEM package parsers. The file stays private until verified.
            if (!temporary.renameTo(target)) throw IOException("Could not stage the update.")
            verifyArchive(target, update)
            target
        } catch (exception: Exception) {
            target.delete()
            throw exception
        } finally {
            temporary.delete()
        }
    }

    private fun verifyArchive(file: File, update: GitHubUpdate) {
        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IOException("Android could not read the downloaded APK.")
        if (archive.packageName != context.packageName || archive.longVersionCode != update.versionCode ||
            archive.versionName != update.tag.removePrefix("v") ||
            archive.longVersionCode <= installed.longVersionCode ||
            (archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) > Build.VERSION.SDK_INT ||
            !signersMatch(archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }.orEmpty(),
                installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }.orEmpty())) {
            throw IOException("The APK does not match this installation.")
        }
    }

    private fun downloadTo(
        url: URL, file: File, update: GitHubUpdate, digest: MessageDigest, job: CoroutineContext,
        progress: (Float) -> Unit
    ) {
        open(url) { input ->
            file.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                var written = 0L
                var reported = -1
                while (true) {
                    job.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    written += read
                    if (written > update.bytes) throw IOException("The update is larger than expected.")
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    val percent = (written * 100 / update.bytes).toInt()
                    if (percent != reported) {
                        progress(percent / 100f)
                        reported = percent
                    }
                }
                if (written != update.bytes) throw IOException("The update download was incomplete.")
            }
        }
    }

    private fun readText(url: URL, limit: Int): String = open(url) { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) throw IOException("Update metadata is too large.")
            output.write(buffer, 0, count)
        }
        output.toString(Charsets.UTF_8.name())
    }

    private fun <T> open(url: URL, read: (InputStream) -> T): T {
        var next = url
        repeat(5) {
            if (next.protocol != "https" || next.host !in DOWNLOAD_HOSTS ||
                next.userInfo != null || next.port != -1) throw IOException("Unexpected update download host.")
            val connection = next.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "NobiScan-Android")
            try {
                when (val status = connection.responseCode) {
                    200 -> return connection.inputStream.use(read)
                    301, 302, 303, 307, 308 -> {
                        val location = connection.getHeaderField("Location")
                            ?: throw IOException("Missing update redirect.")
                        next = URL(next, location)
                    }
                    404 -> throw UpdateUnavailable("The GitHub update feed is not publicly available yet.")
                    403, 429 -> throw UpdateUnavailable("GitHub is limiting update checks. Try again later.")
                    else -> throw IOException("GitHub returned HTTP $status.")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many update redirects.")
    }

    private fun findAsset(assets: JSONArray, name: String): JSONObject? =
        (0 until assets.length()).asSequence().map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == name && it.optString("state") == "uploaded" }

    private fun assetUrl(tag: String, name: String) =
        URL("https://github.com/$REPOSITORY/releases/download/$tag/$name")

    companion object {
        private const val REPOSITORY = "NavajyotiBayan/Nobi-Scan"
        private const val MAX_METADATA_BYTES = 8_192
        private const val MAX_APK_BYTES = 200L * 1024 * 1024
        internal val STABLE_TAG = Regex("""v\d+\.\d+\.\d+(?:-rc\d+)?""")
        private val SHA256 = Regex("[0-9a-f]{64}")

        internal fun releaseMatches(
            tag: String, versionName: String, apk: String, hash: String, code: Long, minimumCode: Long
        ): Boolean = STABLE_TAG.matches(tag) && versionName == tag.removePrefix("v") &&
            apk == "NobiScan-$tag-arm64.apk" && SHA256.matches(hash) && code > minimumCode

        private val DOWNLOAD_HOSTS = setOf("api.github.com", "github.com", "release-assets.githubusercontent.com",
            "objects.githubusercontent.com")
    }
}

internal fun signersMatch(candidate: List<String>, installed: List<String>): Boolean =
    installed.isNotEmpty() && candidate.size == installed.size && candidate.toSet() == installed.toSet()
