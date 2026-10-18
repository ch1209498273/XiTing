package com.lujinyu.xiting.listening

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.*
import com.lujinyu.xiting.*
import java.text.DateFormat
import java.util.Date

/** A secondary screen. Main navigation and the assistant's start/stop action stay intact. */
class ListeningActivity:Activity() {
    private lateinit var body:LinearLayout
    private var page=0
    private var current:String?=null
    private var previewText:String?=null
    private var selectedUrl:String?=null
    private var draftTitle=""
    private var titleInput:EditText?=null
    private var route="list"
    private var offset=0
    private var busy=false
    private var ready=true
    override fun attachBaseContext(base:Context) {
        ready=RestoreSnapshot.recoverPending(base)
        super.attachBaseContext(AppearanceSettings.wrap(AppLocales.wrap(base)))
    }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        if(!ready){Toast.makeText(this,R.string.rel_recovery_storage_fail,Toast.LENGTH_LONG).show();finish();return}
        val savedPreview=state?.getString("preview")
        current=state?.getString("entry")
        selectedUrl=state?.getString("url");draftTitle=state?.getString("title").orEmpty()
        if(savedPreview!=null)preview(savedPreview,current)
        else if(state!=null && current!=null)detail(current!!)
        else if(intent.action==Intent.ACTION_SEND) {
            if(intent.type!="text/plain"){toast(R.string.listen_bad_link);finish();return}
            val raw=try {intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()}catch(_:RuntimeException){""}
            if(raw.isBlank()){toast(R.string.listen_bad_link);finish();return}
            preview(raw)
        }else showList()
    }
    override fun onResume() {
        super.onResume()
        if(ready && ::body.isInitialized && !busy) {
            when(route){"list"->showList();"detail"->current?.let{detail(it)}}
        }
    }
    override fun onStop() {
        super.onStop()
        if(ready)ListeningWork.submit({BackupManager.save(applicationContext)})
    }
    override fun onSaveInstanceState(out:Bundle) {
        super.onSaveInstanceState(out)
        out.putString("preview",previewText);out.putString("entry",current);out.putString("url",selectedUrl)
        out.putString("title",titleInput?.text?.toString() ?: draftTitle)
    }
    @Deprecated("Deprecated in Java") override fun onBackPressed() {
        if(route=="preview" && intent.action==Intent.ACTION_SEND)finish()
        else if(route!="list")showList() else super.onBackPressed()
    }
    private fun dp(n:Int)=(resources.displayMetrics.density*n).toInt()
    private fun toast(res:Int)=Toast.makeText(this,res,Toast.LENGTH_SHORT).show()
    private fun text(value:String,size:Float=15f)=TextView(this).apply {
        text=value;textSize=size;setTextColor(getColor(R.color.text_primary));setPadding(dp(8),dp(8),dp(8),dp(8))
    }
    private fun screen(title:Int) {
        page++;busy=false;titleInput=null
        val root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(getColor(R.color.bg_page));fitsSystemWindows=true}
        root.setPadding(0,0,0,dp(12))
        val header=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(10),dp(16),dp(6))}
        header.addView(TextView(this).apply {
            text="‹ "+getString(R.string.listen_back);textSize=15f;gravity=Gravity.CENTER
            setTextColor(getColor(R.color.brand));contentDescription=getString(R.string.listen_back)
            isFocusable=true;setOnClickListener{onBackPressed()}
        },LinearLayout.LayoutParams(dp(72),dp(52)))
        header.addView(text(getString(title),21f),LinearLayout.LayoutParams(0,-2,1f));root.addView(header)
        body=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(16),0,dp(16),dp(20))}
        root.addView(ScrollView(this).apply{isFillViewport=true;addView(body)},LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        window.statusBarColor=getColor(R.color.bg_page);window.navigationBarColor=getColor(R.color.bg_page)
    }
    private fun button(label:Int,tag:String="",action:()->Unit)=button(getString(label),tag,action)
    private fun button(label:String,tag:String="",action:()->Unit):Button=Button(this).apply {
        text=label;this.tag=tag;contentDescription=label;minHeight=dp(48);isAllCaps=false
        setBackgroundResource(R.drawable.bg_card);setPadding(dp(14),dp(10),dp(14),dp(10))
        setTextColor(getColor(R.color.brand));setOnClickListener{if(!busy)action()}
        body.addView(this,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(5)})
    }
    private fun <T> load(work:()->T,done:(T)->Unit) {
        val token=page;busy=true
        ListeningWork.submit(work) {result->
            if(isFinishing || isDestroyed || token!=page)return@submit
            busy=false;result.onSuccess(done).onFailure{toast(R.string.listen_storage_error)}
        }
    }
    private fun date(at:Long)=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(at))
    private fun position(at:Long)=String.format(java.util.Locale.ROOT,"%d:%02d",at/60000,at/1000%60)
    private fun displayTitle(entry:ListeningEntry)=entry.title?.takeIf{it.isNotBlank()} ?: getString(R.string.listen_unknown)
    private fun confirm(message:String,action:()->Unit) {
        AlertDialog.Builder(this).setTitle(R.string.listen_confirm).setMessage(message)
            .setPositiveButton(R.string.dlg_ok){_,_->action()}.setNegativeButton(R.string.dlg_cancel,null).show()
    }
    private fun intro() {
        if(prefs().getBoolean(ListeningStore.INTRO,false))return
        AlertDialog.Builder(this).setTitle(R.string.listen_recent).setMessage(R.string.listen_intro)
            .setPositiveButton(R.string.dlg_ok){_,_->prefs().edit().putBoolean(ListeningStore.INTRO,true).apply()}
            .setNeutralButton(R.string.listen_auto_off){_,_->prefs().edit().putBoolean(ListeningStore.INTRO,true).putBoolean(ListeningStore.AUTO,false).apply()}
            .show()
    }
    private fun showList() {
        route="list";previewText=null;selectedUrl=null;draftTitle="";current=null;offset=0
        screen(R.string.listen_recent)
        button(R.string.listen_save_link,"listen_save_link"){paste()}
        button(R.string.listen_manage,"listen_manage"){manage()}
        val rows=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};body.addView(rows)
        val more=button(R.string.listen_more,"listen_more"){}
        fun next() {
            load({ListeningStore.list(this,offset)}) {entries->
                if(offset==0 && entries.isEmpty())rows.addView(text(getString(R.string.listen_empty)).apply{tag="listen_empty"})
                for(entry in entries)rows.addView(Button(this).apply {
                    text=listOf(displayTitle(entry),entry.app.takeIf{it.isNotBlank()},date(entry.updated)).filterNotNull().joinToString("\n")
                    tag="listen_entry_${entry.id}";isAllCaps=false;gravity=Gravity.START or Gravity.CENTER_VERTICAL
                    setBackgroundResource(R.drawable.bg_card);setPadding(dp(14),dp(12),dp(14),dp(12))
                    if(entry.pkg.isNotBlank())playerIcon(entry.pkg)?.let {icon->icon.setBounds(0,0,dp(28),dp(28));setCompoundDrawables(icon,null,null,null);compoundDrawablePadding=dp(12)}
                    minHeight=dp(84);setTextColor(getColor(R.color.text_primary));setOnClickListener{if(!busy)detail(entry.id)}
                },LinearLayout.LayoutParams(-1,-2))
                offset+=entries.size;more.visibility=if(entries.size==50)View.VISIBLE else View.GONE
                if(offset<=50)maybeClean()
            }
        }
        more.setOnClickListener{if(!busy)next()};next();intro()
    }
    private fun maybeClean() {
        val p=prefs();val now=System.currentTimeMillis()
        if(now-p.getLong("listening_last_clean_v1",0)<86_400_000)return
        val days=p.getInt(ListeningStore.DAYS,90)
        load({ListeningStore.expiredIds(this,now,days)}) {ids->
            if(ids.isEmpty())return@load
            if(!p.getBoolean("listening_clean_confirmed_v1",false)) {
                confirm(getString(R.string.listen_clean_preview,ids.size)) {
                    load({ListeningStore.clean(this,ids,System.currentTimeMillis(),days)}) {
                        p.edit().putBoolean("listening_clean_confirmed_v1",true).putLong("listening_last_clean_v1",now).apply();showList()
                    }
                }
            }else load({ListeningStore.clean(this,ids,now,days)}) {n->p.edit().putLong("listening_last_clean_v1",now).apply();if(n>0)showList()}
        }
    }
    private fun detail(id:String) {
        route="detail";previewText=null;current=id;screen(R.string.listen_detail)
        load({Triple(ListeningStore.get(this,id),ListeningStore.marks(this,id),ListeningStore.latest(this,id))}) {data->
            val entry=data.first ?: run{showList();return@load}
            body.addView(text(displayTitle(entry),22f))
            if(entry.app.isNotBlank())body.addView(text(entry.app))
            body.addView(text(date(entry.updated)))
            if(entry.url!=null) {
                button(R.string.listen_open_content,"listen_open_content"){open(entry.url)}
                body.addView(text(getString(R.string.listen_link_hint),13f))
            }else if(entry.pkg.isNotBlank()) {
                button(R.string.listen_open_app,"listen_open_app") {
                    try {val launch=packageManager.getLaunchIntentForPackage(entry.pkg);if(launch==null)toast(R.string.listen_open_failed) else startActivity(launch)}catch(_:RuntimeException){toast(R.string.listen_open_failed)}
                };body.addView(text(getString(R.string.listen_no_location),13f))
            }else body.addView(text(getString(R.string.listen_no_location)))
            data.third?.position?.let{body.addView(text(getString(R.string.listen_position,position(it))))}
            for(mark in data.second) {
                val label=when(mark.kind){"timer"->R.string.listen_timer_start;"note"->R.string.listen_note;else->R.string.listen_mark}
                body.addView(text(getString(label)+" · "+date(mark.at)+(mark.position?.let{" · "+position(it)} ?: ""),16f))
                if(mark.kind=="timer" && mark.status.isNotBlank()) {
                    val status=when(mark.status){"cancelled"->R.string.listen_cancelled;"replaced"->R.string.listen_replaced;"returned"->R.string.listen_returned;"pause_confirmed"->R.string.listen_pause_confirmed;"interrupted"->R.string.listen_interrupted;else->R.string.listen_timer_ended}
                    body.addView(text(getString(status),13f))
                }
                if(mark.note.isNotBlank())body.addView(text(mark.note))
                button(R.string.listen_edit_note,"listen_note_${mark.id}"){editNote(mark.id,mark.note){detail(id)}}
                button(R.string.listen_delete_mark){confirm(getString(R.string.listen_delete_mark_hint)){load({ListeningStore.deleteMark(this,mark.id)}){detail(id)}}}
            }
            button(R.string.listen_add_note,"listen_add_note") {
                val input=EditText(this).apply{filters=arrayOf(InputFilter.LengthFilter(2000))}
                AlertDialog.Builder(this).setTitle(R.string.listen_add_note).setView(input).setPositiveButton(R.string.listen_save){_,_->
                    val value=input.text.toString().trim();if(value.isNotEmpty())load({
                        val s=ContentSnapshot(entry.pkg,entry.app,entry.title,null,null,null)
                        val mark=ListeningStore.mark(this,id,s,"note");ListeningStore.note(this,mark,value)
                    }){detail(id)}
                }.setNegativeButton(R.string.dlg_cancel,null).show()
            }
            button(R.string.listen_associate,"listen_associate"){paste(id)}
            button(if(entry.kept)R.string.listen_unkeep else R.string.listen_keep,"listen_keep") {load({ListeningStore.keep(this,id,!entry.kept)}){detail(id)}}
            body.addView(text(getString(R.string.listen_protection),13f))
            button(R.string.listen_delete,"listen_delete"){confirm(getString(R.string.listen_delete_hint)){load({ListeningStore.delete(this,id)}){showList()}}}
        }
    }
    private fun editNote(id:String,value:String,done:()->Unit) {
        val input=EditText(this).apply {setText(value);filters=arrayOf(InputFilter.LengthFilter(2000))}
        AlertDialog.Builder(this).setTitle(R.string.listen_note).setView(input).setPositiveButton(R.string.listen_save){_,_->load({ListeningStore.note(this,id,input.text.toString().trim())}){done()}}
            .setNegativeButton(R.string.dlg_cancel,null).show()
    }
    private fun paste(associate:String?=null) {
        val raw=EditText(this).apply{hint=getString(R.string.listen_link_hint)}
        val dialog=AlertDialog.Builder(this).setTitle(R.string.listen_save_link).setView(raw)
            .setNeutralButton(R.string.listen_paste,null)
            .setPositiveButton(R.string.listen_preview,null).setNegativeButton(R.string.dlg_cancel,null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val clip=(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                val value=if(clip!=null && clip.itemCount>0)clip.getItemAt(0).text?.toString() else null
                if(value!=null && value.length<=65536)raw.setText(value) else toast(R.string.listen_bad_link)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value=raw.text.toString()
                if(SharedContentParser.parse(value).urls.isEmpty())toast(R.string.listen_bad_link)
                else {dialog.dismiss();selectedUrl=null;draftTitle="";preview(value,associate)}
            }
        };dialog.show()
    }
    private fun preview(raw:String,associate:String?=null) {
        route="preview";previewText=raw;current=associate;screen(R.string.listen_preview)
        val parsed=SharedContentParser.parse(raw)
        if(parsed.error!=null){body.addView(text(getString(R.string.listen_bad_link)));button(R.string.dlg_cancel){showList()};return}
        val input=EditText(this).apply {hint=getString(R.string.listen_title_hint);setText(draftTitle.ifBlank{parsed.title});filters=arrayOf(InputFilter.LengthFilter(500))}
        titleInput=input;body.addView(input)
        val choices=RadioGroup(this)
        if(parsed.urls.size==1)selectedUrl=parsed.urls.first()
        for(url in parsed.urls)choices.addView(RadioButton(this).apply {
            id=View.generateViewId();text=url;setTextColor(getColor(R.color.text_primary));isChecked=url==selectedUrl;minHeight=dp(48)
            setOnClickListener{selectedUrl=url}
        })
        body.addView(choices);body.addView(text(getString(R.string.listen_save_hint),13f))
        button(R.string.listen_save,"listen_save") {
            val url=selectedUrl
            if(url==null){toast(R.string.listen_choose_link);return@button}
            val title=input.text.toString().trim()
            load({if(associate!=null){if(ListeningStore.associate(this,associate,url))associate else null} else ListeningStore.saveLink(this,title,url)}) {id->
                if(id!=null){toast(R.string.listen_saved);detail(id)}
                else if(associate!=null)confirm(getString(R.string.listen_merge_hint)) {
                    load({check(ListeningStore.associate(this,associate,url,true))}){toast(R.string.listen_saved);detail(associate)}
                }
            }
        }
        button(R.string.dlg_cancel,"listen_cancel"){if(associate!=null)detail(associate) else if(intent.action==Intent.ACTION_SEND)finish() else showList()}
    }
    private fun open(url:String) {
        if(!SharedContentParser.validUrl(url)){toast(R.string.listen_bad_link);return}
        try {startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))}
        catch(_:RuntimeException){toast(R.string.listen_open_failed)}
    }
    private fun manage() {
        route="manage";previewText=null;current=null;screen(R.string.listen_manage)
        val auto=Switch(this).apply {
            text=getString(R.string.listen_auto);isChecked=prefs().getBoolean(ListeningStore.AUTO,true);minHeight=dp(56)
            setTextColor(getColor(R.color.text_primary));setOnCheckedChangeListener{_,v->prefs().edit().putBoolean(ListeningStore.AUTO,v).apply()}
        };body.addView(auto)
        val mark=Switch(this).apply {
            text=getString(R.string.listen_mark_toggle);contentDescription=text;tag="listen_mark_toggle"
            isChecked=prefs().getBoolean(ListeningStore.MARK_SHOW,false);minHeight=dp(56)
            setTextColor(getColor(R.color.text_primary));setOnCheckedChangeListener{_,v->prefs().edit().putBoolean(ListeningStore.MARK_SHOW,v).apply()}
        };body.addView(mark)
        body.addView(text(getString(R.string.listen_protection),13f))
        val values=intArrayOf(30,90,180,0)
        val labels=arrayOf(getString(R.string.listen_days,30),getString(R.string.listen_days,90),getString(R.string.listen_days,180),getString(R.string.listen_forever))
        val select=Spinner(this);select.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,labels)
        select.setSelection(values.indexOf(prefs().getInt(ListeningStore.DAYS,90)).coerceAtLeast(0))
        body.addView(text(getString(R.string.listen_retention)));body.addView(select)
        select.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:AdapterView<*>?)=Unit
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){prefs().edit().putInt(ListeningStore.DAYS,values[position]).apply()}
        }
        button(R.string.listen_clean,"listen_clean") {
            val days=prefs().getInt(ListeningStore.DAYS,90);val now=System.currentTimeMillis()
            load({ListeningStore.expiredIds(this,now,days)}) {ids->confirm(getString(R.string.listen_clean_preview,ids.size)) {
                load({ListeningStore.clean(this,ids,System.currentTimeMillis(),days)}) {n->toast(R.string.listen_done);prefs().edit().putBoolean("listening_clean_confirmed_v1",true).putLong("listening_last_clean_v1",now).apply();manage()}
            }}
        }
        button(R.string.listen_clear,"listen_clear"){confirm(getString(R.string.listen_clear_hint)){load({ListeningStore.clear(this)}){toast(R.string.listen_done);manage()}}}
    }
}
