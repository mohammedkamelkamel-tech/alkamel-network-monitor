package ye.alkamel.networkmonitor

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object BackupManager {
    private const val SETTINGS = "network_backup_settings"
    private const val TREE_URI = "backup_tree_uri"
    private const val WORK_NAME = "alkamel_network_daily_backup"

    fun saveFolder(context: Context, uri: Uri) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit()
            .putString(TREE_URI, uri.toString()).apply()
        schedule(context)
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<NetworkBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun createBackup(context: Context): Boolean {
        val value = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
            .getString(TREE_URI, null) ?: return false
        return try { writeBackup(context, Uri.parse(value)) } catch (_: Exception) { false }
    }

    fun restoreBackup(context: Context, backupUri: Uri): Boolean {
        return try {
            val json = context.contentResolver.openInputStream(backupUri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: return false
            val root = JSONObject(json)
            if (root.optString("app") != "مراقبة شبكة الكامل" || root.optInt("formatVersion", -1) != 1) return false
            val data = root.optJSONObject("data") ?: return false
            val editor = context.getSharedPreferences("network_monitor", Context.MODE_PRIVATE).edit().clear()
            val keys = data.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                when (val value = data.get(key)) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    else -> editor.putString(key, value.toString())
                }
            }
            editor.commit()
        } catch (_: Exception) {
            false
        }
    }

    internal fun writeBackup(context: Context, treeUri: Uri): Boolean {
        val resolver = context.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
        val name = "alkamel_network_backup_" +
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date()) + ".json"
        val file = DocumentsContract.createDocument(resolver, parent, "application/json", name) ?: return false

        val prefs = context.getSharedPreferences("network_monitor", Context.MODE_PRIVATE).all
        val root = JSONObject()
        root.put("app", "مراقبة شبكة الكامل")
        root.put("formatVersion", 1)
        root.put("createdAt", System.currentTimeMillis())
        val data = JSONObject()
        prefs.forEach { (key, value) ->
            when (value) {
                is String -> data.put(key, value)
                is Boolean -> data.put(key, value)
                is Int -> data.put(key, value)
                is Long -> data.put(key, value)
                is Float -> data.put(key, value.toDouble())
                else -> data.put(key, value.toString())
            }
        }
        root.put("data", data)
        resolver.openOutputStream(file, "w")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(root.toString(2))
        } ?: return false
        return true
    }
}

class NetworkBackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result =
        if (BackupManager.createBackup(applicationContext)) Result.success()
        else Result.retry()
}
