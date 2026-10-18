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
 * 数据本体写入公共下载目录（卸载不删除），变化时新增带时间戳的文件，保留旧备份；备份内含设备ID（ANDROID_ID，
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
    const val CURRENT_VERSION = 5

    private val REL_DIR = Environment.DIRECTORY_DOWNLOADS + "/XiTing"

    /** 最近完成写入并成功关闭输出流的时间；失败/取消/空数据跳过不更新。 */
    fun lastSuccessfulBackupAt(ctx: Context): Long? =
        ctx.prefs().getLong(Prefs.LAST_SUCCESSFUL_BACKUP_AT, 0L).takeIf { it > 0L }

    private fun recordBackupSuccess(ctx: Context) {
        ctx.prefs().edit().putLong(Prefs.LAST_SUCCESSFUL_BACKUP_AT, System.currentTimeMillis()).putBoolean("last_backup_failed",false).apply()
    }

    private fun androidId(ctx: Context): String =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    /** 组装备份 JSON（自动备份与手动导出共用同一格式） */
    @Synchronized
    fun buildBackupJson(ctx: Context): JSONObject = SessionLog.withDataLock { com.lujinyu.xiting.listening.ListeningStore.locked {
        check(RestoreSnapshot.recoverPending(ctx)) { "Pending restore must settle before backup" }
        val prefs = ctx.prefs()
        SessionLog.sessions(ctx) // initialize lifetime aggregates before snapshotting them
        val sessText = ctx.filesDir.resolve("sessions.json").takeIf { it.exists() }?.readText() ?: "[]"
        JSONObject()
            // 注意：下面是备份文件的 JSON 键，属于对外数据格式，改名会让旧备份失效。
            // 它们与 prefs 键「碰巧同名字符串」不是一回事，勿与 Prefs 常量混用。
            .put("v", CURRENT_VERSION)
            .put("device", androidId(ctx))
            .put("gp", EnergyStore.collectedTotal(ctx))
            .put("last_share_date", prefs.getString(Prefs.LAST_SHARE_DATE, "") ?: "")
            .put("sessions", JSONArray(sessText))
            .put("session_outcomes", SessionOutcomeStore.encode(ctx))
            .put("ts", System.currentTimeMillis())
            // ↓ v2 新增：养成进度。之前只备份了「积累了多少」，没备份「用积累换了什么」——
            // 用户换手机后成长值还在，但精灵退回默认形态、皮肤全丢、徽章清零。
            // 攒了半年的东西因为换了台手机就没了，这个断裂是设计上的漏洞。
            .put("pet_form", PetForm.selected(ctx))
            .put("pet_skin", PetSkins.active(ctx).id)
            .put("skins_unlocked", JSONArray(PetSkins.unlockedIds(ctx).toList()))
            .put("bubble_uses_pet", PetForm.bubbleUsesPet(ctx))
            .put("ach_unlocked", JSONArray(Achievements.unlockedIds(ctx).toList()))
            .put("ach_history", JSONArray(AchievementHistory.encode(Achievements.history(ctx))))
            .put("pending_energy", CompleteBackup.pending(JSONArray(prefs.getString(Prefs.ENERGY_PENDING,"[]")),System.currentTimeMillis()))
            .put("extras",CompleteBackup.encode(prefs))
            .put("listening_library",com.lujinyu.xiting.listening.ListeningStore.export(ctx))
    } }

    /** 导出到用户所选位置（SAF 手动备份） */
    @Synchronized
    fun exportToUri(ctx: Context, uri: Uri): Boolean {
        return try {
            ctx.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(buildBackupJson(ctx).toString().toByteArray())
            } ?: return false
            recordBackupSuccess(ctx)
            Log.i(TAG, "备份已导出到所选位置")
            true
        } catch (e: Exception) {
            Log.w(TAG, "导出失败: $e")
            false
        }
    }

    /** 备份当前成长值/会话/分享状态（静默；Android 10+ 无需权限） */
    @Synchronized
    fun save(ctx: Context) {
        if (Build.VERSION.SDK_INT < 29) return // 9 及以下分区存储不稳定，跳过自动备份
        if(!ctx.prefs().getBoolean("auto_backup_enabled",true))return
        try {
            val prefs = ctx.prefs()
            val gp = EnergyStore.collectedTotal(ctx)
            val sessText = ctx.filesDir.resolve("sessions.json").takeIf { it.exists() }?.readText() ?: "[]"
            // 防护：本地为空（重装后未恢复）时不覆盖既有备份，避免毁掉历史数据
            if (com.lujinyu.xiting.listening.ListeningStore.count(ctx)==0 && SessionOutcomeStore.list(ctx).isEmpty() && gp <= 0 && EnergyStore.pending(ctx).isEmpty() && HistoryStore.totalCount(ctx)==0L && (sessText == "[]" || sessText.isBlank())) {
                Log.i(TAG, "本地为空，跳过备份覆盖（保护历史备份）")
                return
            }
            val backup=buildBackupJson(ctx)
            val previous=findBackup(ctx)
            if(previous!=null) {
                val before=JSONObject(previous.toString()).apply { remove("ts") }
                val after=JSONObject(backup.toString()).apply { remove("ts") }
                if(before.toString()==after.toString())return
            }
            val bytes = backup.toString().toByteArray()

            val resolver = ctx.contentResolver
            // Preserve old backups; only changed content produces a new complete file.
            val name="XiTing-backup-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(8)}.json"
            insertAndWrite(resolver,name,bytes)
            recordBackupSuccess(ctx)
        } catch (e: Exception) {
            ctx.prefs().edit().putBoolean("last_backup_failed",true).apply()
            Log.w(TAG, "备份失败: $e")
        }
    }

    /** Only adds a new backup; never deletes older downloads. */
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
                arrayOf("XiTing-backup%.json"), "${MediaStore.Downloads.DATE_ADDED} DESC, ${MediaStore.Downloads._ID} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                    try {
                        val candidate=readFromUri(ctx,uri)
                        if(candidate!=null && isSameDevice(ctx,candidate))found=candidate
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
                // v5 adds up to 16MiB of local library rows; bound the whole external document to 20MiB.
                val bytes=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                while(true) {
                    val count=input.read(buffer)
                    if(count<0)break
                    if(bytes.size()+count>20*1_048_576)return null
                    bytes.write(buffer,0,count)
                }
                JSONObject(bytes.toString("UTF-8")).takeIf { isReadableBackup(it) }
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
    @Synchronized
    fun restore(ctx: Context, obj: JSONObject): Boolean = SessionLog.withDataLock { com.lujinyu.xiting.listening.ListeningStore.locked { restoreLocked(ctx,obj) } }

    private fun restoreLocked(ctx: Context, obj: JSONObject): Boolean {
        if(!isReadableBackup(obj) || OverlayService.isRunning)return false
        if(!RestoreSnapshot.recoverPending(ctx))return false
        SessionLog.sessions(ctx) // settle any interrupted history deletion before taking rollback state
        val preferences=ctx.prefs()
        val file=ctx.filesDir.resolve("sessions.json")
        return try {
            RestoreSnapshot.capture(ctx)
            // gp 来自外部文件，不能直接写进 prefs：负数会让精灵进度异常，
            // 极大值在后续累加时溢出成负
            val gp = backupGrowth(obj)
            val prefs = ctx.prefs()
            // 迁移标记置位，避免旧值再迁移覆盖
            val ed = prefs.edit()
                .putInt(Prefs.ENERGY_COLLECTED, gp)
                .putBoolean(Prefs.ENERGY_MIGRATED, true)
                // Imported growth is a restored state, not a newly earned evolution.
                .putInt(Prefs.LAST_SEEN_STAGE,PetView.stageOf(gp.toLong()))
                .putString(Prefs.LAST_SHARE_DATE, obj.optString("last_share_date", ""))
            // ↓ v2：养成进度。v1 备份没有这些字段，optXxx 全返回 null →
            // 保持现状不动（绝不能因为「备份是旧的」就把用户现有数据清空）。
            applyProgress(ctx, obj, ed)
            CompleteBackup.apply(obj.optJSONObject("extras"),ed)
            obj.optJSONArray("pending_energy")?.let {
                ed.putString(Prefs.ENERGY_PENDING,CompleteBackup.pending(it,System.currentTimeMillis()).toString())
            }
            obj.optJSONArray("sessions")?.let { arr ->
                // 导入数据不可信：原来整段 arr.toString() 原样落盘，
                // 一个超大或畸形的数组会在之后的 SessionLog.sessions() 里
                // 被主线程全量解析，轻则 ANR 重则 OOM。
                val kept = validBackupSessions(arr)
                if (kept.length() > 0 || (obj.optInt("v",1)>=3 && arr.length()==0)) {
                    writeSessionsAtomic(file,kept.toString().toByteArray(Charsets.UTF_8))
                }
                Log.i(TAG, "导入会话 ${kept.length()} 条（原文件 ${arr.length()} 条，超限或结构不合法者已丢弃）")
            }
            obj.optJSONArray("session_outcomes")?.let { SessionOutcomeStore.restore(ctx,it) }
            if(obj.has("listening_library"))com.lujinyu.xiting.listening.ListeningStore.replace(ctx,obj.getJSONObject("listening_library"))
            ed.putBoolean("listening_clean_confirmed_v1",false).remove("listening_last_clean_v1")
            check(ed.commit()) { "Restored preferences could not be persisted" }
            // v4.0：聚合是明细的派生数据（累计/最长/连续天数/月口径），恢复后必须重算，
            // 否则聚合还停留在恢复前的值。备份文件本身不带聚合（派生数据不进备份）。
            if(!CompleteBackup.hasAggregate(obj.optJSONObject("extras")))
                HistoryStore.rebuildFromSessions(ctx, SessionLog.sessions(ctx))
            // Flush aggregate apply() before removing the crash-recovery marker.
            check(preferences.edit().putBoolean(Prefs.STATS_MIGRATED,true).commit()) { "Restored aggregates could not be persisted" }
            RestoreSnapshot.finish(ctx)
            Log.i(TAG, "备份已恢复（gp=$gp）")
            true
        } catch (e: Exception) {
            // Keep file and preferences from becoming a half-restored state.
            try {
                check(RestoreSnapshot.recoverPending(ctx)) { "Rollback could not be persisted" }
                RestoreSnapshot.finish(ctx)
            } catch(rollbackFailure:Exception) { Log.w(TAG,"Restore rollback failed",rollbackFailure) }
            Log.w(TAG, "恢复失败: $e")
            false
        }
    }

    private fun writeSessionsAtomic(file:java.io.File,bytes:ByteArray) {
        val atomic=android.util.AtomicFile(file)
        val stream=atomic.startWrite()
        try { stream.write(bytes);atomic.finishWrite(stream) }
        catch(t:Throwable) { atomic.failWrite(stream);throw t }
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
        if (obj.optJSONArray("ach_unlocked") != null || obj.optJSONArray("ach_history") != null) {
            val restored = AchievementHistory.mergeBackup(Achievements.history(ctx), Achievements.unlockedIds(ctx), obj)
            ed.putStringSet(Prefs.ACH_UNLOCKED, restored.unlocked)
                .putString(Prefs.ACH_HISTORY, AchievementHistory.encode(restored.records))
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
