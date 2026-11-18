package io.hiddemoens.pluskey;

import android.content.Context;
import android.hardware.camera2.*;
import android.media.AudioManager;
import android.os.*;
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
