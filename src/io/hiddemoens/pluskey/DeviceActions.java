package io.hiddemoens.pluskey;

import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.camera2.*;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.view.Display;
import java.util.concurrent.*;

/** Invoked only by the already-authorized shell helper, using shell attribution. */
final class DeviceActions {
    private static Context context;
    private static CameraManager camera;
    private static String cameraId;
    private static volatile boolean torch;
    private static HandlerThread cameraThread;
    private static synchronized Context context()throws Exception{
        if(context==null){
            if(Looper.myLooper()==null)Looper.prepare();
            Class<?> thread=Class.forName("android.app.ActivityThread");
            Object system=thread.getMethod("systemMain").invoke(null);
            Context base=(Context)thread.getMethod("getSystemContext").invoke(system);
            context=base.createPackageContext("com.android.shell",0);
        }
        return context;
    }
    static String nativeLongPressAction() throws Exception {
        return Settings.System.getString(context().getContentResolver(), Helper.ONEPLUS_ACTION_SETTING);
    }
    static boolean isAwakeAndUnlocked() throws Exception {
        Context ctx = context();
        if (!ctx.getSystemService(PowerManager.class).isInteractive()) return false;
        Display display = ctx.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) throw new IllegalStateException("Default display unavailable");
        return display.getState() == Display.STATE_ON
            && !ctx.getSystemService(KeyguardManager.class).isKeyguardLocked();
    }
    static Runnable watchUnlock(Executor executor, Runnable changed) throws Exception {
        if (Build.VERSION.SDK_INT < 36) return null;
        Context ctx = context();
        DisplayManager displays = ctx.getSystemService(DisplayManager.class);
        KeyguardManager keyguard = ctx.getSystemService(KeyguardManager.class);
        DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
            @Override public void onDisplayAdded(int id) { }
            @Override public void onDisplayRemoved(int id) { }
            @Override public void onDisplayChanged(int id) {
                if (id == Display.DEFAULT_DISPLAY) changed.run();
            }
        };
        KeyguardManager.KeyguardLockedStateListener keyguardListener = locked -> changed.run();
        displays.registerDisplayListener(executor, DisplayManager.EVENT_TYPE_DISPLAY_STATE, displayListener);
        try {
            keyguard.addKeyguardLockedStateListener(executor, keyguardListener);
        } catch (RuntimeException error) {
            displays.unregisterDisplayListener(displayListener);
            throw error;
        }
        return () -> {
            try {
                keyguard.removeKeyguardLockedStateListener(keyguardListener);
            } finally {
                displays.unregisterDisplayListener(displayListener);
            }
        };
    }
    static synchronized void run(String action)throws Exception{
        String code=null;
        switch(action){
            case "action:screenshot":code="120";break;case "action:lock":code="223";break;
            case "action:media_toggle":code="85";break;case "action:media_next":code="87";break;case "action:media_previous":code="88";break;
            case "action:volume_up":code="24";break;case "action:volume_down":code="25";break;
        }
        if(code!=null){Helper.command("/system/bin/input","keyevent",code);return;}
        if(action.equals("action:notifications")||action.equals("action:quick_settings")){Helper.command("/system/bin/cmd","statusbar",action.equals("action:notifications")?"expand-notifications":"expand-settings");return;}
        Context ctx=context();
        if(action.equals("action:torch")){
            if(camera==null){
                camera=(CameraManager)ctx.getSystemService(Context.CAMERA_SERVICE);
                for(String id:camera.getCameraIdList()){
                    CameraCharacteristics info=camera.getCameraCharacteristics(id);
                    if(Boolean.TRUE.equals(info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))){cameraId=id;if(Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(info.get(CameraCharacteristics.LENS_FACING)))break;}
                }
                if(cameraId==null)throw new IllegalStateException("No flashlight available");
                CountDownLatch initial=new CountDownLatch(1);
                cameraThread=new HandlerThread("PlusKey-light");cameraThread.start();
                camera.registerTorchCallback(new CameraManager.TorchCallback(){public void onTorchModeChanged(String id,boolean enabled){if(id.equals(cameraId)){torch=enabled;initial.countDown();}}},new Handler(cameraThread.getLooper()));
                initial.await(1,TimeUnit.SECONDS);
            }
            boolean desired=!torch;camera.setTorchMode(cameraId,desired);torch=desired;return;
        }
        AudioManager audio=(AudioManager)ctx.getSystemService(Context.AUDIO_SERVICE);
        int next=Options.ringerMode(action,audio.getRingerMode());
        if(next<0)throw new IllegalArgumentException("Unknown action");
        // Shell holds MODIFY_AUDIO_ROUTING; system control preserves all three modes.
        audio.getClass().getMethod("setRingerModeInternal",int.class).invoke(audio,next);
        if(audio.getRingerMode()!=next)throw new IllegalStateException("Phone did not apply sound mode");
    }
    static void feedback()throws Exception{
        Helper.command("/system/bin/cmd","vibrator_manager","feedback","-d","PlusKey","4");
    }
    static synchronized void shutdown(){if(cameraThread!=null)cameraThread.quitSafely();}
}
