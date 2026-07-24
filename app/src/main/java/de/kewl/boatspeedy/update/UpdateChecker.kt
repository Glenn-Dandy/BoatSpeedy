package de.kewl.boatspeedy.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** GitHub-Repo-Koordinaten (auch für Links im Über-Screen). */
object Repo {
    const val OWNER = "Glenn-Dandy"
    const val NAME = "BoatSpeedy"
    const val URL = "https://github.com/$OWNER/$NAME"
    const val LATEST_RELEASE_URL = "$URL/releases/latest"
    const val DEV_RELEASE_URL = "$URL/releases/tag/dev-build"
    private const val API_LATEST = "https://api.github.com/repos/$OWNER/$NAME/releases/latest"
    private const val API_DEV = "https://api.github.com/repos/$OWNER/$NAME/releases/tags/dev-build"

    val apiLatest: String get() = API_LATEST
    val apiDev: String get() = API_DEV
}

/** Ergebnis der Update-Prüfung. */
sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(
        val version: String,
        val downloadUrl: String?,
        val releaseUrl: String,
    ) : UpdateResult
    data object Failed : UpdateResult
}

object UpdateChecker {

    /**
     * Fragt das neueste Release ab und vergleicht mit [currentVersion] (z. B. "1.0.1").
     * Ist [allowDev] true, wird zusätzlich der aktuelle DEV-Build angeboten (rollend,
     * daher immer als „verfügbar" zum manuellen Laden).
     */
    suspend fun check(currentVersion: String, allowDev: Boolean = false): UpdateResult = withContext(Dispatchers.IO) {
        if (allowDev) {
            val devJson = fetch(Repo.apiDev)
            if (devJson != null) {
                val dev = JSONObject(devJson)
                val apk = firstApkUrl(dev)
                if (apk != null) {
                    val name = dev.optString("name").ifEmpty { "dev-build" }
                    return@withContext UpdateResult.Available(
                        version = name,
                        downloadUrl = apk,
                        releaseUrl = dev.optString("html_url", Repo.DEV_RELEASE_URL),
                    )
                }
            }
        }
        try {
            val json = fetch(Repo.apiLatest) ?: return@withContext UpdateResult.Failed
            val obj = JSONObject(json)
            val tag = obj.optString("tag_name").ifEmpty { return@withContext UpdateResult.Failed }
            val latest = tag.removePrefix("v")
            val releaseUrl = obj.optString("html_url", Repo.LATEST_RELEASE_URL)

            if (compareVersions(latest, currentVersion) <= 0) {
                UpdateResult.UpToDate
            } else {
                val apk = firstApkUrl(obj)
                UpdateResult.Available(version = tag, downloadUrl = apk, releaseUrl = releaseUrl)
            }
        } catch (_: Exception) {
            UpdateResult.Failed
        }
    }

    private fun fetch(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "BoatSpeedy")
        }
        return try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun firstApkUrl(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                return a.optString("browser_download_url").ifEmpty { null }
            }
        }
        return null
    }

    /** Vergleicht "1.2.3"-Versionen numerisch. >0 wenn a neuer als b. */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}
