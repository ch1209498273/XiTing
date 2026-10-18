package com.lujinyu.xiting

import android.app.AlertDialog
import android.content.Context

object ListeningPresets {
    private const val KEY = "listening_presets_v1"
    private val suggestions = listOf(ListeningPreset(30, true), ListeningPreset(60, true))
    fun load(ctx: Context): List<ListeningPreset> {
        val stored = ctx.prefs().getString(KEY, null)
        return if (stored == null) suggestions else ListeningPresetRules.decode(stored)
    }
    fun save(ctx: Context, preset: ListeningPreset): Boolean {
        if (preset.minutes !in 1..600) return false
        val current = load(ctx)
        if (preset in current) return false
        write(ctx, current + preset)
        return true
    }
    fun remove(ctx: Context, preset: ListeningPreset) = write(ctx, load(ctx) - preset)
    private fun write(ctx: Context, items: List<ListeningPreset>) {
        ctx.prefs().edit().putString(KEY, ListeningPresetRules.encode(items)).apply()
    }
    fun label(ctx: Context, item: ListeningPreset): String = ctx.getString(R.string.extras_preset_format,
        item.minutes, ctx.getString(if (item.pauseKeepBlack) R.string.timer_pause_keep_black else R.string.timer_return_video))
}

internal fun MainActivity.showListeningPresets(): AlertDialog {
    val items = ListeningPresets.load(this)
    val builder = AlertDialog.Builder(this).setTitle(R.string.extras_presets)
        .setNegativeButton(R.string.dlg_cancel, null)
        .setNeutralButton(R.string.extras_manage_presets) { _, _ -> showManageListeningPresets() }
    if (items.isEmpty()) builder.setMessage(R.string.extras_no_presets)
    else builder.setItems(items.map { ListeningPresets.label(this, it) }.toTypedArray()) { _, index ->
        showTimerOptions(items[index])
    }
    return builder.show()
}

internal fun MainActivity.showManageListeningPresets(): AlertDialog {
    val items = ListeningPresets.load(this)
    val builder = AlertDialog.Builder(this).setTitle(R.string.extras_manage_presets)
        .setNegativeButton(R.string.dlg_cancel, null)
    if (items.isEmpty()) builder.setMessage(R.string.extras_no_presets)
    else builder.setItems(items.map { ListeningPresets.label(this, it) }.toTypedArray()) { _, index ->
        AlertDialog.Builder(this).setTitle(R.string.extras_delete_preset)
            .setMessage(ListeningPresets.label(this, items[index]))
            .setNegativeButton(R.string.dlg_cancel) { _, _ -> showManageListeningPresets() }
            .setPositiveButton(R.string.extras_delete) { _, _ ->
                ListeningPresets.remove(this, items[index]); showManageListeningPresets()
            }.show()
    }
    return builder.show()
}
