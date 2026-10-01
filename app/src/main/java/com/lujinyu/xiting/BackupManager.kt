// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 设备绑定自动备份：
 * 数据本体写入公共下载目录（卸载不删除），文件名固定；备份内含设备ID（ANDROID_ID，
 * 卸载重装不变）。重装后启动时自动发现同设备备份 → 弹窗询问恢复。
 * 完全离线、零权限（MediaStore.Downloads，Android 10+）。
 */
object BackupManager {

    private const val TAG = "XiTing"
    private const val FILE_NAME = "XiTing-backup.json"
    private val REL_DIR = Environment.DIRECTORY_DOWNLOADS + "/XiTing"

    private fun androidId(ctx: Context): String =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    /** 备份当前成长值/会话/分享状态（静默；Android 10+ 无需权限） */
    fun save(ctx: Context) {
        if (Build.VERSION.SDK_INT < 29) return // 9 及以下分区存储不稳定，跳过自动备份
        try {
            val prefs = ctx.getSharedPreferences("xiiting_prefs", Context.MODE_PRIVATE)
            val gp = EnergyStore.collectedTotal(ctx)
            val sessions = ctx.filesDir.resolve("sessions.json")
            val sessText = if (sessions.exists()) sessions.readText() else "[]"
            // 防护：本地为空（重装后未恢复）时不覆盖既有备份，避免毁掉历史数据
            if (gp <= 0 && (sessText == "[]" || sessText.isBlank())) {
                Log.i(TAG, "本地为空，跳过备份覆盖（保护历史备份）")
                return
            }
            val obj = JSONObject()
                .put("v", 1)
                .put("device", androidId(ctx))
                .put("gp", gp)
                .put("last_share_date", prefs.getString("last_share_date", "") ?: "")
                .put("sessions", JSONArray(sessText))
                .put("ts", System.currentTimeMillis())
            val bytes = obj.toString().toByteArray()

            val resolver = ctx.contentResolver
            // 先自愈命名：写入中断会留下孤儿行（media_type=0、无属主）占住正名路径，
            // 导致 insert 被去重成 "XiTing-backup (N).json" 越堆越多、而正名文件永远陈旧
            // （模拟器与真机均实测出现）。每次备份前释放正名路径并清掉历史去重副本。
            healBackupNames(resolver)
            try {
                insertAndWrite(resolver, FILE_NAME, bytes)
                Log.i(TAG, "备份已写入 Downloads/XiTing（gp=$gp）")
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                // 兜底：仍冲突则换时间戳文件名，保证数据不丢
                val fallback = "XiTing-backup-${System.currentTimeMillis()}.json"
                insertAndWrite(resolver, fallback, bytes)
                Log.w(TAG, "主备份名被占用，改用 $fallback")
            }
        } catch (e: Exception) {
            Log.w(TAG, "备份失败: $e")
        }
    }

    /** 备份命名自愈：先清历史去重副本（自有行，可删），再尝试释放正名路径
     *  （卸载残留的无主行可能删不动——失败不影响后续写入，只是继续用去重名） */
    private fun healBackupNames(resolver: android.content.ContentResolver) {
        // 1) 清理本应用写出的历史去重副本（XiTing-backup (N).json 等）
        try {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
                "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                arrayOf("XiTing-backup%.json"), null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                while (c.moveToNext()) {
                    if (c.getString(nameCol) != FILE_NAME) {
                        resolver.delete(
                            Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(idCol).toString()),
                            null, null
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        // 2) 尝试释放正名路径（孤儿行/旧行；无主行删不动时静默放弃）
        try {
            val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
            val canonical = "$base/$REL_DIR/$FILE_NAME"
            resolver.delete(filesUri, "_data=?", arrayOf(canonical))
        } catch (_: Exception) {
        }
    }

    private fun insertAndWrite(resolver: android.content.ContentResolver, name: String, bytes: ByteArray) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, REL_DIR)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert 返回 null")
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IllegalStateException("输出流打开失败")
    }

    /** 读取本机备份（若存在且为本设备创建）；返回 null 表示无可用备份 */
    fun findBackup(ctx: Context): JSONObject? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val resolver = ctx.contentResolver
            var found: JSONObject? = null
            // 前缀匹配：兼容主名 XiTing-backup.json 与孤儿兜底名 XiTing-backup-<ts>.json
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DATE_ADDED),
                "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                arrayOf("XiTing-backup%.json"), "${MediaStore.Downloads.DATE_ADDED} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                    try {
                        resolver.openInputStream(uri)?.use { input ->
                            found = JSONObject(input.readBytes().toString(Charsets.UTF_8))
                        }
                    } catch (_: Exception) {
                    }
                    if (found != null) break
                }
            }
            val obj = found
            // 设备校验：仅恢复同一台设备创建的备份
            if (obj != null && obj.optString("device") == androidId(ctx)) obj else null
        } catch (e: Exception) {
            Log.w(TAG, "读取备份失败: $e")
            null
        }
    }

    /** 从系统文件选择器返回的 URI 读取备份内容（SAF 授权通道，跨分区存储读取） */
    fun readFromUri(ctx: Context, uri: Uri): JSONObject? {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                JSONObject(input.readBytes().toString(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取所选备份失败: $e")
            null
        }
    }

    /** 备份是否来自本机（设备ID匹配；不匹配则可提示用户二次确认） */
    fun isSameDevice(ctx: Context, obj: JSONObject): Boolean =
        obj.optString("device") == androidId(ctx)

    /** 把备份数据恢复到本地（成长值/会话/分享状态） */
    fun restore(ctx: Context, obj: JSONObject): Boolean {
        return try {
            val gp = obj.optInt("gp", 0)
            val prefs = ctx.getSharedPreferences("xiiting_prefs", Context.MODE_PRIVATE)
            // 迁移标记置位，避免旧值再迁移覆盖
            prefs.edit()
                .putInt("energy_collected_total", gp)
                .putBoolean("energy_migrated_v1", true)
                .putString("last_share_date", obj.optString("last_share_date", ""))
                .apply()
            obj.optJSONArray("sessions")?.let { arr ->
                ctx.filesDir.resolve("sessions.json").writeText(arr.toString())
            }
            Log.i(TAG, "备份已恢复（gp=$gp）")
            true
        } catch (e: Exception) {
            Log.w(TAG, "恢复失败: $e")
            false
        }
    }
}
