package com.watchvideo.data.update

import com.watchvideo.data.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/danweiyuancircle/veya/releases/latest"

/** 检查更新超时：GitHub 不通时快速失败，不挂起、不打扰。 */
private const val CHECK_TIMEOUT_MS = 8000L

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val body: String = "",
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

data class UpdateInfo(
    val versionName: String,
    val releaseUrl: String,
    val apkDownloadUrl: String?,
    val notes: String,
)

class UpdateChecker(private val clientFactory: () -> HttpClient = ::createHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * GET 最新 release，若比 currentVersion 新返回 UpdateInfo，否则 null。
     * 网络/解析失败或超时（GitHub 不通）均返回 null —— 检查更新失败绝不打扰用户。
     */
    suspend fun checkLatest(currentVersion: String): UpdateInfo? {
        val release = try {
            withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                clientFactory().use { client ->
                    val body = client.get(LATEST_RELEASE_URL) {
                        header("User-Agent", "Veya-App")
                    }.bodyAsText()
                    json.decodeFromString<GithubRelease>(body)
                }
            } ?: return null
        } catch (e: Exception) {
            return null
        }
        if (!isNewerVersion(release.tagName, currentVersion)) return null
        val apkUrl = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?.browserDownloadUrl
        return UpdateInfo(
            versionName = release.tagName.trimStart('v', 'V'),
            releaseUrl = release.htmlUrl,
            apkDownloadUrl = apkUrl,
            notes = release.body,
        )
    }
}
