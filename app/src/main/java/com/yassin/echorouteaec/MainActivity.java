package com.yassin.echorouteaec;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Process;
import android.view.*;
import android.widget.*;
import java.util.*;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private final int BG=Color.rgb(8,13,19), CARD=Color.rgb(17,25,33), TEXT=Color.WHITE, MUTED=Color.rgb(160,174,185), ACCENT=Color.rgb(53,208,181);
    private Spinner appSpinner; private LinearLayout logBox; private TextView status, privilege; private Button start; private CheckBox aec,ns,agc,comm; private Switch sourceMic,sourceVc; private SeekBar priority;
    private final List<AppEntry> apps=new ArrayList<>(); private IEchoUserService service; private boolean bound, uiReady; private android.content.SharedPreferences prefs; private boolean arabic;
    private final IEchoCallback callback=new IEchoCallback.Stub(){public void onState(String s,String d){runOnUiThread(()->{addLog(s,d);status.setText(d);updateButton();});}};
    private final ServiceConnection conn=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){bound=true;service=IEchoUserService.Stub.asInterface(b);addLog("SERVICE","Privileged UserService connected");updateButton();}public void onServiceDisconnected(ComponentName n){bound=false;service=null;addLog("ERROR","Privileged UserService disconnected");updateButton();}};
    private final Shizuku.OnBinderReceivedListener br=()->runOnUiThread(this::refreshShizuku);
    private final Shizuku.OnBinderDeadListener bd=()->runOnUiThread(()->{addLog("ERROR","Shizuku binder is offline");refreshShizuku();});
    private final Shizuku.OnRequestPermissionResultListener pr=(code,result)->runOnUiThread(()->{if(code!=7311)return;if(result==PackageManager.PERMISSION_GRANTED){addLog("PERMISSION","Shizuku permission granted");bindService();}else addLog("ERROR","Shizuku permission denied");});

    @Override protected void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("settings",0); arabic=prefs.getBoolean("arabic", false); buildUi();uiReady=true;loadApps();Shizuku.addBinderReceivedListenerSticky(br);Shizuku.addBinderDeadListener(bd);Shizuku.addRequestPermissionResultListener(pr);refreshShizuku();}
    @Override protected void onDestroy(){uiReady=false;Shizuku.removeBinderReceivedListener(br);Shizuku.removeBinderDeadListener(bd);Shizuku.removeRequestPermissionResultListener(pr);super.onDestroy();}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        LinearLayout top=new LinearLayout(this);top.setPadding(22,24,22,12);top.setGravity(Gravity.CENTER_VERTICAL); TextView logo=text("◉",34,ACCENT);top.addView(logo,new LinearLayout.LayoutParams(54,54));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(text("EchoRoute",24,TEXT));titles.addView(text(tr("System microphone processing","معالجة ميكروفون النظام"),12,MUTED));top.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        Button about=button("ⓘ");about.setOnClickListener(v->showAbout());Button settings=button("⚙");settings.setOnClickListener(v->showSettings());top.addView(about);top.addView(settings);root.addView(top);
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(18,4,18,28);
        privilege=text("Checking Shizuku…",13,MUTED);body.addView(card(privilege));
        body.addView(section(tr("Target","التطبيق المستهدف"), tr("Choose the app whose new microphone sessions should receive the selected system preprocessing.","اختر التطبيق الذي ستتلقى جلسات الميكروفون الجديدة فيه المعالجة المحددة.")));
        appSpinner=new Spinner(this);body.addView(card(appSpinner));
        body.addView(section(tr("Processing","المعالجة"), tr("Only effects exposed by the phone's AudioEffect/HAL stack can be installed.","يمكن تثبيت المؤثرات التي يكشفها نظام AudioEffect وطبقة HAL في الهاتف فقط.")));
        aec=check(tr("Acoustic Echo Canceler (AEC)","إلغاء الصدى الصوتي (AEC)"),tr("Reduces speaker-to-microphone echo when the platform exposes AEC.","يقلل الصدى الواصل من السماعة إلى الميكروفون عند توفر AEC."),true);
        ns=check(tr("Noise Suppression (NS)","تقليل الضوضاء (NS)"),tr("Reduces steady background noise using the platform implementation.","يقلل الضوضاء الخلفية باستخدام تنفيذ النظام."),true);
        agc=check(tr("Automatic Gain Control (AGC)","التحكم التلقائي بالكسب (AGC)"),tr("Lets the platform normalize microphone level when AGC is available.","يسمح للنظام بتسوية مستوى الميكروفون عند توفر AGC."),false);
        comm=check(tr("Communication audio mode","وضع صوت الاتصالات"),tr("Requests MODE_IN_COMMUNICATION while active. Some Xiaomi builds may handle this differently.","يطلب وضع MODE_IN_COMMUNICATION أثناء التشغيل. بعض إصدارات Xiaomi قد تتعامل معه بشكل مختلف."),true);
        body.addView(card(aec));body.addView(card(ns));body.addView(card(agc));body.addView(card(comm));
        body.addView(section(tr("Sources","مصادر التسجيل"),tr("Apply to recording sources commonly used by voice apps.","طبّق المعالجة على مصادر التسجيل الشائعة في تطبيقات الصوت.")));
        sourceMic=sw(tr("MIC (1)","MIC (1)"),tr("Standard microphone source","مصدر الميكروفون القياسي"),true);sourceVc=sw(tr("VOICE_COMMUNICATION (7)","VOICE_COMMUNICATION (7)"),tr("Telephony/communication source","مصدر الاتصالات والمكالمات"),true);body.addView(card(sourceMic));body.addView(card(sourceVc));
        body.addView(section(tr("Priority","الأولوية"),tr("Higher priority asks AudioPolicy to place the effect with higher precedence. It cannot override vendor/HAL policy.","الأولوية الأعلى تطلب من AudioPolicy وضع المؤثر بأسبقية أعلى، لكنها لا تتجاوز سياسة الشركة أو HAL.")));
        priority=new SeekBar(this);priority.setMax(2000);priority.setProgress(prefs.getInt("priority",1000));priority.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){prefs.edit().putInt("priority",s.getProgress()).apply();}});body.addView(card(priority));
        status=text("Waiting for Shizuku…",14,MUTED);body.addView(card(status));start=button(tr("START PROCESSING","بدء المعالجة"));start.setOnClickListener(v->toggle());body.addView(start,new LinearLayout.LayoutParams(-1,54));
        body.addView(section(tr("Event log","سجل الأحداث"),tr("Every important state, warning, failure and operation is recorded here.","يتم تسجيل الحالات والتحذيرات والأخطاء والعمليات المهمة هنا.")));
        logBox=new LinearLayout(this);logBox.setOrientation(LinearLayout.VERTICAL);body.addView(card(logBox));
        Button clear=button(tr("CLEAR LOG","مسح السجل"));clear.setOnClickListener(v->logBox.removeAllViews());body.addView(clear);
        Button explain=button(tr("HOW IT WORKS","طريقة العمل"));explain.setOnClickListener(v->showAbout());body.addView(explain);
        scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        loadPrefs();
    }
    private void loadPrefs(){ns.setChecked(prefs.getBoolean("ns",true));agc.setChecked(prefs.getBoolean("agc",false));comm.setChecked(prefs.getBoolean("comm",true));sourceMic.setChecked(prefs.getBoolean("mic",true));sourceVc.setChecked(prefs.getBoolean("vc",true));}
    private LinearLayout card(View child){LinearLayout l=new LinearLayout(this);l.setPadding(14,10,14,10);l.setGravity(Gravity.CENTER_VERTICAL);GradientDrawable g=new GradientDrawable();g.setColor(CARD);g.setCornerRadius(18);l.setBackground(g);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,6,0,6);l.setLayoutParams(p);l.addView(child);return l;}
    private LinearLayout section(String h,String d){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(4,18,4,2);l.addView(text(h,19,TEXT));l.addView(text(d,12,MUTED));return l;}
    private CheckBox check(String h,String d,boolean on){CheckBox c=new CheckBox(this);c.setText(h+"\n"+d);c.setTextColor(TEXT);c.setTextSize(14);c.setChecked(on);return c;}
    private Switch sw(String h,String d,boolean on){Switch s=new Switch(this);s.setText(h+"\n"+d);s.setTextColor(TEXT);s.setTextSize(14);s.setChecked(on);return s;}
    private Button button(String t){Button b=new Button(this);b.setText(t);b.setTextColor(TEXT);b.setTextSize(12);return b;}
    private String tr(String en,String ar){return arabic?ar:en;}
    private TextView text(String s,float z,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);return t;}

    private void loadApps(){new Thread(()->{PackageManager pm=getPackageManager();Intent q=new Intent(Intent.ACTION_MAIN);q.addCategory(Intent.CATEGORY_LAUNCHER);List<android.content.pm.ResolveInfo> rs=pm.queryIntentActivities(q,0);List<AppEntry> tmp=new ArrayList<>();for(var r:rs){String p=r.activityInfo.packageName;if(p.equals(getPackageName())||p.equals("moe.shizuku.privileged.api"))continue;CharSequence l=r.loadLabel(pm);tmp.add(new AppEntry(p,l==null?p:l.toString()));}tmp.sort(Comparator.comparing(a->a.label.toLowerCase(Locale.ROOT)));runOnUiThread(()->{apps.clear();apps.addAll(tmp);List<String> names=new ArrayList<>();for(AppEntry e:apps)names.add(e.label+"\n"+e.packageName);ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);appSpinner.setAdapter(ad);refreshShizuku();});}).start();}
    private void refreshShizuku(){if(!uiReady)return;boolean ready=false;int uid=-1;try{ready=Shizuku.pingBinder();if(ready)uid=Shizuku.getUid();}catch(Throwable ignored){}privilege.setText(ready?"Shizuku connected • UID "+uid+(uid==2000?" • ADB/SHELL":uid==0?" • ROOT":"") : "Shizuku offline — start Shizuku first");start.setEnabled(ready&&!apps.isEmpty());updateButton();}
    private void toggle(){try{if(service!=null&&service.isRunning()){service.stop();addLog("STOPPED","Stopping and restoring previous audio policy…");return;}}catch(Throwable ignored){}if(!Shizuku.pingBinder()){addLog("ERROR","Shizuku is not running.");return;}try{if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED)bindService();else Shizuku.requestPermission(7311);}catch(Throwable e){addLog("ERROR","Shizuku error: "+e);}}
    private void bindService(){if(bound&&service!=null){startSelected();return;}Shizuku.UserServiceArgs args=new Shizuku.UserServiceArgs(new ComponentName(getPackageName(),EchoUserService.class.getName())).daemon(false).processNameSuffix("audio").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE);try{Shizuku.bindUserService(args,conn);addLog("SERVICE","Binding privileged audio service…");}catch(Throwable e){addLog("ERROR","UserService bind failed: "+e);}}
    private void startSelected(){if(apps.isEmpty()||service==null)return;AppEntry e=apps.get(appSpinner.getSelectedItemPosition());if(!aec.isChecked()&&!ns.isChecked()&&!agc.isChecked()){addLog("ERROR","Select at least one audio effect.");return;}int[] src;if(sourceMic.isChecked()&&sourceVc.isChecked())src=new int[]{1,7};else if(sourceMic.isChecked())src=new int[]{1};else if(sourceVc.isChecked())src=new int[]{7};else{addLog("ERROR","Select at least one source.");return;}prefs.edit().putBoolean("ns",ns.isChecked()).putBoolean("agc",agc.isChecked()).putBoolean("comm",comm.isChecked()).putBoolean("mic",sourceMic.isChecked()).putBoolean("vc",sourceVc.isChecked()).apply();try{service.start(e.packageName,comm.isChecked(),aec.isChecked(),ns.isChecked(),agc.isChecked(),src,priority.getProgress(),callback);addLog("START","Requested processing for "+e.label+" ("+e.packageName+")");}catch(Throwable x){addLog("ERROR","Start failed: "+x);}}
    private void updateButton(){if(!uiReady||start==null)return;boolean r=false;try{r=service!=null&&service.isRunning();}catch(Throwable ignored){}start.setText(r?"STOP PROCESSING":"START PROCESSING");}
    private void addLog(String state,String detail){if(logBox==null)return;TextView t=text(state+"\n"+detail,12,state.equals("ERROR")?Color.rgb(255,110,110):state.equals("WARNING")?Color.rgb(255,196,90):state.equals("ACTIVE")?ACCENT:MUTED);t.setPadding(8,8,8,8);logBox.addView(t,0);while(logBox.getChildCount()>150)logBox.removeViewAt(logBox.getChildCount()-1);}
    private void showAbout(){new android.app.AlertDialog.Builder(this).setTitle("EchoRoute — How it works").setMessage("EchoRoute does not create a fake microphone. It uses the official Shizuku API to run a privileged UserService, normally as Android shell UID 2000 in ADB mode or UID 0 when Shizuku is backed by root/Sui.\n\nThe service discovers AudioEffect implementations, calls the device's AudioPolicy service through reflection, installs source-default AEC/NS/AGC effects, restarts the selected target, then removes the exact effect IDs on stop.\n\nImportant: success of the policy call does not guarantee that the vendor Audio HAL actually processes PCM with the requested effect. Xiaomi/Redmi, Samsung and other vendors can differ. The event log exposes those failures and limitations instead of hiding them.").setPositiveButton("OK",null).show();}
    private void showSettings(){new android.app.AlertDialog.Builder(this).setTitle(tr("Settings","الإعدادات")).setItems(new String[]{tr("Arabic","العربية"),tr("English","الإنجليزية"),tr("Reset preferences","إعادة الإعدادات")},(d,w)->{if(w==0||w==1){arabic=w==0;prefs.edit().putBoolean("arabic",arabic).apply();recreate();}else{prefs.edit().clear().apply();recreate();}}).show();}
    static final class AppEntry{final String packageName,label;AppEntry(String p,String l){packageName=p;label=l;}}
}
