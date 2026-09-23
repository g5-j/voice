package com.yassin.echorouteaec;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Privileged Shizuku service. UID is normally 2000 (ADB/shell) or 0 (root/Sui). */
public final class EchoUserService extends IEchoUserService.Stub {
    private static final String TAG = "EchoRoute";
    private final Context context;
    private final List<Integer> effectIds = new ArrayList<>();
    private int oldMode = AudioManager.MODE_NORMAL;
    private boolean modeChanged;
    private boolean running;
    private String targetPackage;
    private IEchoCallback activeCallback;

    public EchoUserService(Context context) { this.context = context; }

    @Override public synchronized void start(String targetPackage, boolean communicationMode,
            boolean aec, boolean ns, boolean agc, int[] sources, int priority,
            IEchoCallback callback) {
        if (running) { send(callback, "WARNING", "Already active for " + this.targetPackage); return; }
        this.targetPackage = targetPackage;
        this.activeCallback = callback;
        try {
            int uid = Process.myUid();
            send(callback, "PRIVILEGE", "Privileged service UID=" + uid + (uid == 0 ? " (ROOT)" : uid == 2000 ? " (ADB/SHELL)" : ""));
            send(callback, "SCAN", "Scanning AudioEffect implementations…");
            Map<UUID, EffectInfo> effects = scanEffects();
            send(callback, "SCAN", "Found " + effects.size() + " audio-effect implementations.");

            forceStop(targetPackage, callback);
            int installed = 0;
            if (aec) installed += installAcrossSources(AudioEffect.EFFECT_TYPE_AEC, "AEC", sources, priority, effects, callback);
            if (ns) installed += installAcrossSources(AudioEffect.EFFECT_TYPE_NS, "NS", sources, priority, effects, callback);
            if (agc) installed += installAcrossSources(AudioEffect.EFFECT_TYPE_AGC, "AGC", sources, priority, effects, callback);
            if (installed == 0) throw new IllegalStateException("No selected effect could be installed on this AudioPolicy implementation");

            if (communicationMode) {
                try {
                    AudioManager am = context.getSystemService(AudioManager.class);
                    if (am != null) {
                        oldMode = am.getMode();
                        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                        modeChanged = true;
                        send(callback, "AUDIO_MODE", "MODE_IN_COMMUNICATION enabled");
                    }
                } catch (Throwable t) { send(callback, "WARNING", "Could not change audio mode: " + rootMessage(t)); }
            }

            running = true;
            send(callback, "ACTIVE", "Installed " + installed + " source-default effect(s). Relaunching target…");
            launchPackage(targetPackage, callback);
            send(callback, "VERIFY", "Policy call succeeded. Actual DSP processing still depends on the device audio HAL.");
        } catch (Throwable t) {
            Log.e(TAG, "start failed", t);
            cleanup(callback);
            send(callback, "ERROR", rootMessage(t));
        }
    }

    @Override public synchronized void stop() { cleanup(activeCallback); }
    @Override public synchronized boolean isRunning() { return running; }

    private int installAcrossSources(UUID type, String label, int[] sources, int priority,
            Map<UUID, EffectInfo> effects, IEchoCallback cb) {
        EffectInfo effect = effects.get(type);
        if (effect == null) { send(cb, "WARNING", label + " is not exposed by AudioEffect.queryEffects() on this device."); return 0; }
        int count = 0;
        for (int source : sources) {
            try { installDefaultEffect(type, label, source, priority, effect); count++; }
            catch (Throwable t) { send(cb, "WARNING", label + " source=" + source + " failed: " + rootMessage(t)); }
        }
        return count;
    }

    private void installDefaultEffect(UUID type, String label, int source, int priority, EffectInfo effect) throws Exception {
        Object svc = getAudioPolicyService();
        Class<?> uuidClass = Class.forName("android.media.audio.common.AudioUuid");
        Method add = findPolicyMethod(svc.getClass(), "addSourceDefaultEffect", 5);
        if (add == null) throw new NoSuchMethodException("addSourceDefaultEffect signature not found");
        Object typeUuid = toAudioUuid(type);
        Object implUuid = toAudioUuid(effect.implUuid);
        Object result = add.invoke(svc, typeUuid, context.getPackageName(), implUuid, priority, source);
        int id = result instanceof Integer ? (Integer) result : ((Number) result).intValue();
        if (id <= 0) throw new IllegalStateException(label + " returned effect id=" + id);
        effectIds.add(id);
        send(activeCallback, "INSTALLED", label + " / source=" + source + " / id=" + id + " / " + effect.name + " / " + effect.implementor);
    }

    private Method findPolicyMethod(Class<?> cls, String name, int count) {
        for (Method m : cls.getMethods()) if (m.getName().equals(name) && m.getParameterTypes().length == count) { m.setAccessible(true); return m; }
        for (Method m : cls.getDeclaredMethods()) if (m.getName().equals(name) && m.getParameterTypes().length == count) { m.setAccessible(true); return m; }
        return null;
    }

    private Object getAudioPolicyService() throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Method get = sm.getMethod("getService", String.class);
        IBinder binder = (IBinder) get.invoke(null, "media.audio_policy");
        if (binder == null) binder = (IBinder) get.invoke(null, "audio_policy");
        if (binder == null) throw new IllegalStateException("AudioPolicy binder unavailable");
        Class<?> stub = Class.forName("android.media.IAudioPolicyService$Stub");
        return stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
    }

    private Object toAudioUuid(UUID u) throws Exception {
        Class<?> c = Class.forName("android.media.audio.common.AudioUuid");
        Object out = c.getConstructor().newInstance();
        long msb = u.getMostSignificantBits(), lsb = u.getLeastSignificantBits();
        setInt(c, out, "timeLow", (int)(msb >>> 32));
        setInt(c, out, "timeMid", (int)((msb >>> 16) & 0xffff));
        setInt(c, out, "timeHiAndVersion", (int)(msb & 0xffff));
        setInt(c, out, "clockSeq", (int)((lsb >>> 48) & 0xffff));
        byte[] node = new byte[6];
        for (int i=0;i<6;i++) node[i]=(byte)((lsb >>> (40-8*i)) & 0xff);
        Field f=c.getField("node"); f.set(out,node);
        return out;
    }
    private void setInt(Class<?> c,Object o,String n,int v)throws Exception{c.getField(n).setInt(o,v);}

    private Map<UUID,EffectInfo> scanEffects() {
        Map<UUID,EffectInfo> out = new LinkedHashMap<>();
        try {
            AudioEffect.Descriptor[] ds=AudioEffect.queryEffects();
            if(ds!=null) for(AudioEffect.Descriptor d:ds) if(d!=null && d.type!=null) out.putIfAbsent(d.type,new EffectInfo(d.uuid, d.name, d.implementor));
        } catch(Throwable t){Log.w(TAG,"queryEffects failed",t);}
        return out;
    }

    private void cleanup(IEchoCallback cb) {
        for(Integer id:new ArrayList<>(effectIds)) {
            try {
                Object svc=getAudioPolicyService();
                Method remove=findPolicyMethod(svc.getClass(),"removeSourceDefaultEffect",1);
                if(remove==null) throw new NoSuchMethodException("removeSourceDefaultEffect signature not found");
                remove.invoke(svc,id);
                send(cb,"REMOVED","Removed effect id="+id);
            } catch(Throwable t){send(cb,"WARNING","Failed removing effect id="+id+": "+rootMessage(t));}
        }
        effectIds.clear();
        if(modeChanged){try{AudioManager am=context.getSystemService(AudioManager.class);if(am!=null)am.setMode(oldMode);}catch(Throwable t){send(cb,"WARNING","Could not restore audio mode: "+rootMessage(t));}}
        modeChanged=false; running=false; targetPackage=null; activeCallback=null;
        send(cb,"STOPPED","EchoRoute processing is stopped.");
    }

    private void forceStop(String pkg, IEchoCallback cb) {
        try { ProcessBuilder pb=new ProcessBuilder("cmd","activity","force-stop",pkg);pb.redirectErrorStream(true);java.lang.Process p=pb.start();int code=p.waitFor();send(cb,"TARGET", "force-stop " + (code==0?"succeeded":"returned "+code)); }
        catch(Throwable t){send(cb,"WARNING","force-stop failed: "+rootMessage(t));}
    }
    private void launchPackage(String pkg, IEchoCallback cb) {
        try{Intent launch=context.getPackageManager().getLaunchIntentForPackage(pkg);if(launch==null){send(cb,"WARNING","No launcher activity found for target");return;}launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(launch);send(cb,"TARGET","Target relaunched: "+pkg);}catch(Throwable t){send(cb,"WARNING","Target launch failed: "+rootMessage(t));}
    }
    private void send(IEchoCallback cb,String state,String detail){if(cb!=null)try{cb.onState(state,detail);}catch(Throwable ignored){}}
    private String rootMessage(Throwable t){Throwable x=t;while(x.getCause()!=null)x=x.getCause();return x.getClass().getSimpleName()+": "+String.valueOf(x.getMessage());}
    static final class EffectInfo{final UUID implUuid;final String name;final String implementor;EffectInfo(UUID u,String n,String i){implUuid=u;name=n;implementor=i;}}
}
