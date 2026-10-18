package com.lujinyu.xiting

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration

object AppearanceSettings {
    private const val KEY = "appearance_mode_v1"
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
    private val modes = listOf(SYSTEM, LIGHT, DARK)
    fun current(ctx: Context): String = ctx.prefs().getString(KEY, SYSTEM).let { if (it in modes) it!! else SYSTEM }
    fun set(ctx: Context, mode: String) {
        require(mode in modes)
        ctx.prefs().edit().putString(KEY, mode).apply()
    }
    /** Wrap in attachBaseContext, after AppLocales.wrap; Activity resources may already be
     * initialized by framework setTheme before onCreate, so overrideConfiguration is too late. */
    fun wrap(ctx: Context): Context {
        val night = when (current(ctx)) {
            LIGHT -> Configuration.UI_MODE_NIGHT_NO
            DARK -> Configuration.UI_MODE_NIGHT_YES
            else -> return ctx
        }
        val config = Configuration(ctx.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return ctx.createConfigurationContext(config)
    }
}

object HomeEntryOrder {
    private const val KEY = "home_entry_order_v1"
    fun keys(ctx: Context): List<String> = HomeEntryOrderRules.normalize(ctx.prefs().getString(KEY, "") ?: "")
    fun orderedIds(ctx: Context): List<Int> = keys(ctx).map { key -> when (key) {
        "timer" -> R.id.timer_chip
        "player" -> R.id.playback_source_card
        "demo" -> R.id.btn_first_use
        else -> R.id.home_permissions_title
    } }
    fun promote(ctx: Context, key: String) {
        if (key !in HomeEntryOrderRules.defaults) return
        ctx.prefs().edit().putString(KEY, (listOf(key) + keys(ctx).filter { it != key }).joinToString(",")).apply()
    }
    fun reset(ctx: Context) { ctx.prefs().edit().remove(KEY).apply() }
    fun label(ctx: Context, key: String): String = ctx.getString(when (key) {
        "timer" -> R.string.extras_entry_timer
        "player" -> R.string.extras_entry_player
        "demo" -> R.string.extras_entry_demo
        else -> R.string.extras_entry_permissions
    })
}

internal fun MainActivity.showAppearanceSettings(): AlertDialog {
    val modes=listOf(AppearanceSettings.SYSTEM,AppearanceSettings.LIGHT,AppearanceSettings.DARK)
    return AlertDialog.Builder(this).setTitle(R.string.fix_theme)
        .setSingleChoiceItems(arrayOf(getString(R.string.extras_theme_system),getString(R.string.extras_theme_light),getString(R.string.extras_theme_dark)),
            modes.indexOf(AppearanceSettings.current(this))) { dialog,index ->
            AppearanceSettings.set(this,modes[index]);dialog.dismiss();recreate()
        }.setNegativeButton(R.string.dlg_cancel,null).show()
}

internal fun MainActivity.showHomeEntryOrder(): AlertDialog {
    val keys = HomeEntryOrder.keys(this)
    return AlertDialog.Builder(this).setTitle(R.string.extras_entry_order)
        .setItems(keys.map { HomeEntryOrder.label(this, it) }.toTypedArray()) { _, which ->
            HomeEntryOrder.promote(this, keys[which]); recreate()
        }.setNeutralButton(R.string.extras_reset_order) { _, _ -> HomeEntryOrder.reset(this); recreate() }
        .setNegativeButton(R.string.dlg_cancel, null).show().also {
            android.widget.Toast.makeText(this, R.string.extras_entry_order_hint, android.widget.Toast.LENGTH_LONG).show()
        }
}
