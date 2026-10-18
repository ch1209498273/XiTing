package com.lujinyu.xiting;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.AtomicFile;
import android.widget.*;
import org.json.*;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.regex.*;

/** Emulator-only test receiver; no dependency on the target APK's Kotlin runtime. */
public class ContentReturnProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!"ranchu".equals(android.os.Build.HARDWARE) && !"goldfish".equals(android.os.Build.HARDWARE)) { finish(); return; }
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(32,40,32,40);
        ScrollView scroll = new ScrollView(this); scroll.addView(body); setContentView(scroll);
        addText(body,"内容找回探针 · 仅模拟器公开样本");
        EditText input = new EditText(this); input.setHint("粘贴原App复制的公开测试链接"); input.setContentDescription("公开测试分享文字"); body.addView(input);
        Button inspect = new Button(this); inspect.setText("检查输入的分享文字"); body.addView(inspect);
        inspect.setOnClickListener(v -> startActivity(new Intent(this, ContentReturnProbeActivity.class).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,input.getText().toString())));
        try {
            CharSequence subject = getIntent().getCharSequenceExtra(Intent.EXTRA_SUBJECT), text = getIntent().getCharSequenceExtra(Intent.EXTRA_TEXT);
            JSONObject result = parseShare(getIntent().getAction(),getIntent().getType(),subject==null?null:subject.toString(),text==null?null:text.toString());
            AtomicFile atomic = new AtomicFile(new java.io.File(getFilesDir(),"content-return-share.json")); FileOutputStream out = atomic.startWrite();
            try { out.write(result.toString().getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(out); }
            catch (Exception e) { atomic.failWrite(out); throw e; }
            addText(body,"受理："+result.getBoolean("accepted")); addText(body,"标题："+result.optString("subject")); addText(body,"分享文字："+result.optString("text"));
            JSONArray urls=result.getJSONArray("urls"); addText(body,"HTTP(S)链接数："+urls.length());
            for(int i=0;i<urls.length();i++) {
                final String url=urls.getString(i); Button open=new Button(this); open.setText("打开收到的链接 "+(i+1)); body.addView(open);
                open.setOnClickListener(v->{try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(android.content.ActivityNotFoundException e){addText(body,"没有可处理该链接的应用");}});
            }
        } catch(Exception e) { addText(body,"探针无法处理此输入"); }
    }
    private void addText(LinearLayout body,String value) { TextView view=new TextView(this);view.setText(value);view.setTextSize(16);view.setPadding(0,16,0,16);body.addView(view); }
    public static JSONObject parseShare(String action,String type,String subject,String text) throws JSONException {
        boolean accepted=Intent.ACTION_SEND.equals(action)&&"text/plain".equals(type)&&text!=null&&!text.trim().isEmpty()&&text.length()<=32768&&(subject==null||subject.length()<=500);
        LinkedHashSet<String> urls=new LinkedHashSet<>();
        if(accepted) { Matcher m=Pattern.compile("https?://[^\\s<>\"，。；！、）]+").matcher(text);
            while(m.find()) { String u=m.group().replaceAll("[).,;!\"']+$","");if(u.length()<=8192&&Uri.parse(u).getHost()!=null&&!Uri.parse(u).getHost().isEmpty())urls.add(u); }
        }
        return new JSONObject().put("accepted",accepted).put("subject",accepted&&subject!=null?subject:"").put("text",accepted?text:"").put("urls",new JSONArray(urls));
    }
}
