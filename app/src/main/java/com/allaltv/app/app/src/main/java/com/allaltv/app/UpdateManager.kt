
package com.allaltv.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val RELEASE_URL =
        "https://api.github.com/repos/alarsadi/ALLAL-TV/releases/latest"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String
    )

    fun checkForUpdate(
        activity: Activity,
        onResult: (UpdateInfo?, String) -> Unit
    ) {
        Thread {
            try {
                val connection =
                    URL(RELEASE_URL).openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.setRequestProperty(
                    "Accept",
                    "application/vnd.github+json"
                )
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                connection.disconnect()

                val release = JSONObject(response)
                val tag = release.getString("tag_name")
                val version = tag.removePrefix("v").toIntOrNull()
                    ?: throw Exception("رقم الإصدار غير صالح")

                val assets = release.getJSONArray("assets")
                var apkUrl: String? = null

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")

                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                if (apkUrl == null) {
                    throw Exception("ملف APK غير موجود")
                }

                val packageInfo = activity.packageManager
                    .getPackageInfo(activity.packageName, 0)

                val currentVersion = if (Build.VERSION.SDK_INT >= 28) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }

                if (version > currentVersion) {
                    val info = UpdateInfo(
                        versionCode = version,
                        versionName = tag,
                        apkUrl = apkUrl
                    )

                    activity.runOnUiThread {
                        onResult(info, "تحديث جديد متوفر")
                    }
                } else {
                    activity.runOnUiThread {
                        onResult(null, "أنت تستعمل آخر إصدار")
                    }
                }

            } catch (e: Exception) {
                activity.runOnUiThread {
                    onResult(
                        null,
                        "تعذر التحقق من التحديث: ${e.message}"
                    )
                }
            }
        }.start()
    }

    fun downloadAndInstall(
        activity: Activity,
        apkUrl: String,
        onStatus: (String) -> Unit
    ) {
        Thread {
            try {
                activity.runOnUiThread {
                    onStatus("جاري تحميل التحديث...")
                }

                val connection =
                    URL(apkUrl).openConnection() as HttpURLConnection

                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true

                val folder = File(activity.cacheDir, "updates")
                if (!folder.exists()) folder.mkdirs()

                val apkFile = File(folder, "app-update.apk")

                connection.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        input.copyTo(output)
                    }
                }

                connection.disconnect()

                if (!apkFile.exists() || apkFile.length() == 0L) {
                    throw Exception("فشل تحميل ملف التحديث")
                }

                activity.runOnUiThread {
                    installApk(activity, apkFile, onStatus)
                }

            } catch (e: Exception) {
                activity.runOnUiThread {
                    onStatus("فشل التحديث: ${e.message}")
                }
            }
        }.start()
    }

    private fun installApk(
        activity: Activity,
        apkFile: File,
        onStatus: (String) -> Unit
    ) {
        try {
            if (Build.VERSION.SDK_INT >= 26 &&
                !activity.packageManager.canRequestPackageInstalls()
            ) {
                onStatus("اسمح للتطبيق بتثبيت التطبيقات من هذا المصدر")

                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )

                activity.startActivity(settingsIntent)
                return
            }

            val apkUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    apkUri,
                    "application/vnd.android.package-archive"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            activity.startActivity(installIntent)
            onStatus("تم تحميل التحديث، أكمل التثبيت")

        } catch (e: Exception) {
            onStatus("تعذر فتح التثبيت: ${e.message}")
        }
    }
}
