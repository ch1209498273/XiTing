package com.lujinyu.xiting

import android.content.Context
import android.graphics.Typeface
import android.media.MediaMetadata
import android.media.session.MediaController
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** App identity stays short in the selected field; current content belongs in the expanded list. */
internal object PlayerRows {
    fun appsAdapter(ctx:Context,apps:List<PlaybackApp>,expanded:Boolean=false):ArrayAdapter<String> =
        object:ArrayAdapter<String>(ctx,android.R.layout.simple_spinner_item,apps.map { it.label }) {
            override fun getView(position:Int,convertView:View?,parent:ViewGroup):View=appRow(ctx,apps[position],expanded)
            override fun getDropDownView(position:Int,convertView:View?,parent:ViewGroup):View=appRow(ctx,apps[position],true)
        }

    private fun appRow(ctx:Context,app:PlaybackApp,expanded:Boolean):View {
        fun dp(v:Int)=(v*ctx.resources.displayMetrics.density).toInt()
        return LinearLayout(ctx).apply {
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(48);setPadding(dp(12),dp(8),dp(12),dp(8))
            addView(ImageView(ctx).apply { setImageDrawable(ctx.playerIcon(app.packageName));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },
                LinearLayout.LayoutParams(dp(32),dp(32)).apply { marginEnd=dp(12) })
            addView(LinearLayout(ctx).apply {
                orientation=LinearLayout.VERTICAL
                addView(TextView(ctx).apply { text=app.label;textSize=16f;setTextColor(ctx.getColor(R.color.text_primary));maxLines=1;ellipsize=TextUtils.TruncateAt.END })
                if(expanded)addView(TextView(ctx).apply { text=app.description(ctx);textSize=13f;setTextColor(ctx.getColor(R.color.text_secondary));maxLines=2;ellipsize=TextUtils.TruncateAt.END })
            },LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        }
    }
    fun adapter(ctx: Context, sources: List<MediaController>): ArrayAdapter<String> =
        rows(ctx, sources, expanded = false)

    fun listAdapter(ctx: Context, sources: List<MediaController>): ArrayAdapter<String> =
        rows(ctx, sources, expanded = true)

    private fun rows(ctx: Context, sources: List<MediaController>, expanded: Boolean): ArrayAdapter<String> =
        object : ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item,
            if (sources.isEmpty()) listOf(ctx.getString(R.string.ui_no_session))
            else sources.map { ctx.playerLabel(it) }) {
            // Timer review can clear/addAll the backing strings. Identity still comes from the live source.
            override fun getItem(position: Int): String? = sources.getOrNull(position)?.let { ctx.playerLabel(it) }
                ?: ctx.getString(R.string.ui_no_session)

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                row(ctx, sources.getOrNull(position), expanded)

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                row(ctx, sources.getOrNull(position), expanded = true)
        }

    private fun row(ctx: Context, source: MediaController?, expanded: Boolean): View {
        fun dp(value: Int) = (value * ctx.resources.displayMetrics.density).toInt()
        val name = source?.let { ctx.playerLabel(it) } ?: ctx.getString(R.string.ui_no_session)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(ImageView(ctx).apply {
                val icon = source?.let { ctx.playerIcon(it.packageName) }
                if (icon != null) setImageDrawable(icon)
                else setImageResource(android.R.drawable.ic_media_play)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(12) })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(ctx).apply {
                    text = name
                    textSize = 16f
                    setTextColor(ctx.getColor(R.color.text_primary))
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.END
                    if (expanded) setTypeface(typeface, Typeface.BOLD)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                if (expanded) addView(TextView(ctx).apply {
                    text = source?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                        ?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.ui_no_session)
                    textSize = 13f
                    setTextColor(ctx.getColor(R.color.text_secondary))
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }
}
