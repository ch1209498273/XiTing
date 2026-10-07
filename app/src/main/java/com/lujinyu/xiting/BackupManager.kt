// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.annotation.SuppressLint
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

    /**
     * 备份格式版本。
     *
     * v1 = 只有 gp / 会话 / 分享日期
     * v2 = 加上养成进度（形态、皮肤、已解锁集合、悬浮球样式、成就）
     *
     * **读的时候永远不要用版本号做分支**：v1 的备份合法地缺这些字段，
     * 用 `optXxx(key, 默认值)` 自然回落；只有将来真要**含义变更**时才需要
     * 区分版本。加版本号是为了将来能改语义，不是为了现在这层。
     */
    const val CURRENT_VERSION = 2

    /** 导入会话条数上限，与 SessionLog.MAX 对齐：超出的部分不恢复 */
    private const val MAX_IMPORT_SESSIONS = 500
    private val REL_DIR = Environment.DIRECTORY_DOWNLOADS + "/XiTing"

    private fun androidId(ctx: Context): String =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    /** 组装备份 JSON（自动备份与手动导出共用同一格式） */
    fun buildBackupJson(ctx: Context): JSONObject {
        val prefs = ctx.prefs()
        val sessText = ctx.filesDir.resolve("sessions.json").takeIf { it.exists() }?.readText() ?: "[]"
        return JSONObject()
            // 注意：下面是备份文件的 JSON 键，属于对外数据格式，改名会让旧备份失效。
            // 它们与 prefs 键「碰巧同名字符串」不是一回事，勿与 Prefs 常量混用。
            .put("v", CURRENT_VERSION)
            .put("device", androidId(ctx))
            .put("gp", EnergyStore.collectedTotal(ctx))
            .put("last_share_date", prefs.getString(Prefs.LAST_SHARE_DATE, "") ?: "")
            .put("sessions", JSONArray(sessText))
            .put("ts", System.currentTimeMillis())
            // ↓ v2 新增：养成进度。之前只备份了「积累了多少」，没备份「用积累换了什么」——
            // 用户换手机后成长值还在，但精灵退回默认形态、皮肤全丢、徽章清零。
            // 攒了半年的东西因为换了台手机就没了，这个断裂是设计上的漏洞。
            .put("pet_form", PetForm.selected(ctx))
            .put("pet_skin", PetSkins.active(ctx).id)
            .put("skins_unlocked", JSONArray(PetSkins.unlockedIds(ctx).toList()))
            .put("bubble_uses_pet", PetForm.bubbleUsesPet(ctx))
            .put("ach_unlocked", JSONArray(Achievements.unlockedIds(ctx).toList()))
    }

    /** 导出到用户所选位置（SAF 手动备份） */
    fun exportToUri(ctx: Context, uri: Uri): Boolean {
        return try {
            ctx.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(buildBackupJson(ctx).toString().toByteArray())
            } ?: return false
            Log.i(TAG, "备份已导出到所选位置")
            true
        } catch (e: Exception) {
            Log.w(TAG, "导出失败: $e")
            false
        }
    }

    /** 备份当前成长值/会话/分享状态（静默；Android 10+ 无需权限） */
    fun save(ctx: Context) {
        if (Build.VERSION.SDK_INT < 29) return // 9 及以下分区存储不稳定，跳过自动备份
        try {
            val prefs = ctx.prefs()
            val gp = EnergyStore.collectedTotal(ctx)
            val sessText = ctx.filesDir.resolve("sessions.json").takeIf { it.exists() }?.readText() ?: "[]"
            // 防护：本地为空（重装后未恢复）时不覆盖既有备份，避免毁掉历史数据
            if (gp <= 0 && (sessText == "[]" || sessText.isBlank())) {
                Log.i(TAG, "本地为空，跳过备份覆盖（保护历史备份）")
                return
            }
            val bytes = buildBackupJson(ctx).toString().toByteArray()

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
    /**
     * 修补旧备份的文件名（改名为主备份 / 清理同前缀的历史件）。
     *
     * 唯一入口 [save] 已有 `SDK_INT < 29` 早退，这里消掉 NewApi 告警即可：
     * 否则 lint 会在每一行报 error（MediaStore.Downloads 字段 29 才存在），
     * 而这些常驻 error 会让人对整个 lint 报告麻本 —— 真正「新引入」的问题反而看不见了。
     *
     * 注：本项目零第三方依赖，没有 androidx，所以用框架自带的 @SuppressLint 而不是
     * androidx 的 @RequiresApi。
     */
    @SuppressLint("NewApi")
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

    /** 同 [healBackupNames]：只在 [save]（已守 SDK>=29）里被调用 */
    @SuppressLint("NewApi")
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
    /**
     * 读取本机备份（若存在且为本设备创建）；返回 null 表示无可用备份。
     *
     * **不能标 @RequiresApi(29)**：这个方法是自我守护的（内部 `SDK_INT < 29` 返回 null），
     * 调用方在低版本上调用它完全合法，标了注解反而会把错误推到调用点。
     * 所以这里只消告警并把理由写在原地，免得后人以为这 4 个 error 是待修的 bug。
     */
    @SuppressLint("NewApi")
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

    /** 把备份数据恢复到本地（成长值/会话/分享状态/养成进度） */
    fun restore(ctx: Context, obj: JSONObject): Boolean {
        return try {
            // gp 来自外部文件，不能直接写进 prefs：负数会让精灵进度异常，
            // 极大值在后续累加时溢出成负
            val gp = obj.optInt("gp", 0).coerceIn(0, Int.MAX_VALUE)
            val prefs = ctx.prefs()
            // 迁移标记置位，避免旧值再迁移覆盖
            val ed = prefs.edit()
                .putInt(Prefs.ENERGY_COLLECTED, gp)
                .putBoolean(Prefs.ENERGY_MIGRATED, true)
                .putString(Prefs.LAST_SHARE_DATE, obj.optString("last_share_date", ""))
            // ↓ v2：养成进度。v1 备份没有这些字段，optXxx 全返回 null →
            // 保持现状不动（绝不能因为「备份是旧的」就把用户现有数据清空）。
            applyProgress(ctx, obj, ed)
            ed.apply()
            obj.optJSONArray("sessions")?.let { arr ->
                // 导入数据不可信：原来整段 arr.toString() 原样落盘，
                // 一个超大或畸形的数组会在之后的 SessionLog.sessions() 里
                // 被主线程全量解析，轻则 ANR 重则 OOM。
                val kept = filterImportSessions(arr, MAX_IMPORT_SESSIONS)
                if (kept.length() > 0) {
                    ctx.filesDir.resolve("sessions.json").writeText(kept.toString())
                }
                Log.i(TAG, "导入会话 ${kept.length()} 条（原文件 ${arr.length()} 条，超限或结构不合法者已丢弃）")
            }
            Log.i(TAG, "备份已恢复（gp=$gp）")
            true
        } catch (e: Exception) {
            Log.w(TAG, "恢复失败: $e")
            false
        }
    }

    /**
     * 从备份恢复养成进度（形态 / 皮肤 / 已解锁集合 / 悬浮球样式 / 成就）。
     *
     * 两条硬规则：
     * 1. **v1 备份没有这些字段**（optInt 返回 -1、optJSONArray 返回 null），此时
     *    **完全不动**现有设置。不能因为「备份里没这项」就把用户当前状态清掉。
     * 2. **导入数据不可信**：形态夹到合法区间，皮肤/成就 id 过白名单。
     *    否则一个手改的 JSON 就能写入不存在的 id，渲染时崩溃。
     */
    private fun applyProgress(ctx: Context, obj: JSONObject, ed: android.content.SharedPreferences.Editor) {
        obj.optInt("pet_form", -1).takeIf { it >= 0 }?.let { stage ->
            ed.putInt(Prefs.PET_FORM, stage.coerceIn(PetView.STAGE_SPARK, PetView.STAGE_KING))
        }
        obj.optString("pet_skin", "").takeIf { it.isNotEmpty() }?.let { id ->
            if (PetSkins.ALL.any { it.id == id }) ed.putString(Prefs.PET_SKIN, id)
        }
        obj.optJSONArray("skins_unlocked")?.let { arr ->
            val ids = arr.toStringSet().filter { cand -> PetSkins.ALL.any { it.id == cand } }
            // 经典配色永远解锁：它是所有其他皮肤的前提
            if (ids.isNotEmpty()) ed.putStringSet(Prefs.SKINS_UNLOCKED, (ids + DEFAULT_SKIN_ID).toSet())
        }
        // boolean 必须用 has() 区分「false」与「字段不存在」——v1 里就没有
        if (obj.has("bubble_uses_pet")) {
            ed.putString(
                Prefs.BUBBLE_STYLE,
                if (obj.optBoolean("bubble_uses_pet", false)) "pet" else "text"
            )
        }
        obj.optJSONArray("ach_unlocked")?.let { arr ->
            val ids = arr.toStringSet().filter { cand -> Achievements.ALL.any { it.id == cand } }
            if (ids.isNotEmpty()) ed.putStringSet(Prefs.ACH_UNLOCKED, ids.toSet())
        }
    }

    /** 经典配色 id，写死而不是读 [PetSkins.ALL] 0 号：列表顺序变了这里也不会错 */
    private const val DEFAULT_SKIN_ID = "default"
}

/**
 * 从备份里筛出结构完整的会话记录，最多 max 条（纯函数，可单测）。
 *
 * 导入数据不可信：非对象元素、缺字段的记录都要挡掉，否则它们会在之后的
 * SessionLog 解析路径里被逐条跳过，白白占用配额；超大数组则直接导致
 * 主线程全量解析时 ANR/OOM。
 */
/**
 * JSONArray → Set<String>（纯函数，可单测）。
 *
 * ⚠ 必须判 `is String` 而不能直接用 `optString`：org.json 的 `optString`
 * 对非字符串会做**宽松转换**（数字 42 会变成 "42"），于是一个畸形的备份里
 * 只要混进一个数字，就会凭空多出一个不存在的 id 写进 prefs。
 */
internal fun JSONArray.toStringSet(): Set<String> {
    val out = LinkedHashSet<String>()
    for (i in 0 until length()) {
        val v = opt(i)
        if (v is String && v.isNotEmpty()) out.add(v)
    }
    return out
}

internal fun filterImportSessions(arr: JSONArray, max: Int): JSONArray {
    val kept = JSONArray()
    val limit = minOf(arr.length(), max)
    for (i in 0 until limit) {
        val o = arr.optJSONObject(i) ?: continue
        if (o.has("s") && o.has("e") && o.has("d") && o.has("m")) kept.put(o)
    }
    return kept
}