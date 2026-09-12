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
    const val ISSUES_NEW_URL = "$URL/issues/new"
    const val SUPPORT_URL = "https://paypal.me/GlennDandy"
    private const val API_LATEST = "https://api.github.com/repos/$OWNER/$NAME/releases/latest"
    /**
     * Das eine rollende Vorab-Release. `releases/latest` überspringt Vorabversionen, es
     * muss also eigens gefragt werden.
     */
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
     *
     * @param includeDev auch den rollenden Entwicklungsbau berücksichtigen. Standardmäßig
     *   aus: Eine Vollversion soll auf Vollversionen zeigen und niemanden ungefragt auf
     *   einen Zwischenstand schicken. Wer mittestet, schaltet es in den
     *   Entwicklereinstellungen ein.
     */
    suspend fun check(
        currentVersion: String,
        includeDev: Boolean = false,
    ): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val stabil = release(Repo.apiLatest) ?: return@withContext UpdateResult.Failed
            // Der Entwicklungsbau darf fehlen, ohne die Prüfung zu versenken: Er wird
            // gelöscht und neu angelegt, und genau in diesem Augenblick gibt es ihn nicht.
            val dev = if (includeDev) release(Repo.apiDev) else null
            val beste = listOfNotNull(stabil, dev).maxWithOrNull { a, b ->
                compareVersions(a.version, b.version)
            } ?: return@withContext UpdateResult.Failed

            if (compareVersions(beste.version, currentVersion) <= 0) {
                UpdateResult.UpToDate
            } else {
                UpdateResult.Available(beste.version, beste.apk, beste.url)
            }
        } catch (_: Exception) {
            UpdateResult.Failed
        }
    }

    /** Ein Release, auf das Nötige eingedampft. */
    private class Fassung(val version: String, val apk: String?, val url: String)

    private fun release(api: String): Fassung? {
        val obj = JSONObject(fetch(api) ?: return null)
        val apk = firstApkUrl(obj)
        // **Die Version des Entwicklungsbaus steht im Dateinamen**, nicht im Tag: Der
        // heißt immer `dev-build`, weil es dasselbe Release bleibt. Der Anhang heißt
        // `BoatSpeedy-1.4.1-dev279.apk`, und daraus lässt sich vergleichen.
        val ausTag = obj.optString("tag_name").removePrefix("v").takeIf { it.isNotBlank() }
        val version = if (ausTag == null || ausTag == "dev-build") {
            apkVersion(obj) ?: return null
        } else {
            ausTag
        }
        return Fassung(version, apk, obj.optString("html_url", Repo.LATEST_RELEASE_URL))
    }

    // `BoatSpeedy-v1.4.0-release.apk` ergibt 1.4.0, `BoatSpeedy-1.4.1-dev279.apk` ergibt
    // 1.4.1-dev279. Der Zusatz gehört zur Version, sonst wären zwei Entwicklungsbauten
    // nicht auseinanderzuhalten.
    private val APK_VERSION = Regex("""BoatSpeedy-v?(.+?)(?:-release)?\.apk""")

    private fun apkVersion(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val name = assets.optJSONObject(i)?.optString("name") ?: continue
            APK_VERSION.find(name)?.let { return it.groupValues[1] }
        }
        return null
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

    /**
     * Vergleicht "1.2.3"-Versionen numerisch. >0 wenn a neuer als b.
     *
     * **Ein Zusatz wie `-dev267` macht die Fassung älter, nicht gleich alt.** Vorher wurde
     * die ganze Zeichenkette an den Punkten zerlegt, und aus `1.4.0-dev267` wurde
     * `[1, 4, 0-dev267]`; das letzte Stück ließ sich nicht als Zahl lesen und zählte als
     * 0. Damit galt ein Entwicklungsbau als genauso neu wie das fertige 1.4.0, und wer
     * mitgetestet hatte, bekam die Veröffentlichung nie angeboten.
     */
    fun compareVersions(a: String, b: String): Int {
        val pa = zahlen(a)
        val pb = zahlen(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        // Gleiche Zahlen: Wer keinen Zusatz trägt, ist die fertige Fassung und gewinnt.
        val za = a.substringAfter('-', "")
        val zb = b.substringAfter('-', "")
        return when {
            za == zb -> 0
            za.isEmpty() -> 1
            zb.isEmpty() -> -1
            else -> zusatzVergleich(za, zb)
        }
    }

    private val ZUSATZ = Regex("""^([^0-9]*)(\d+)$""")

    /**
     * Zwei Zusätze vergleichen. `dev279` gegen `dev1000` als Zeichenketten verglichen
     * ergäbe, dass `dev999` neuer ist als `dev1000` — die Laufnummer wird vierstellig, und
     * ab da bekäme niemand mehr einen Entwicklungsbau angeboten. Bei gleichem Wortteil
     * zählt deshalb die Zahl.
     */
    private fun zusatzVergleich(a: String, b: String): Int {
        val ma = ZUSATZ.find(a)
        val mb = ZUSATZ.find(b)
        if (ma != null && mb != null && ma.groupValues[1] == mb.groupValues[1]) {
            return ma.groupValues[2].toLong().compareTo(mb.groupValues[2].toLong())
        }
        return a.compareTo(b)
    }

    private fun zahlen(v: String) =
        v.substringBefore('-').split(".").map { it.toIntOrNull() ?: 0 }
}
