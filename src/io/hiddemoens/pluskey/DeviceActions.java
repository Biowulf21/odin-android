package io.hiddemoens.pluskey;

import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.camera2.*;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.os.*;
import android.net.wifi.WifiManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import java.util.List;
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
        if (Build.VERSION.SDK_INT < 36) {
            throw new IllegalStateException("Unlock callbacks require Android 16 or later");
        }
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
    static boolean mediaPlaying() throws Exception {
        return context().getSystemService(AudioManager.class).isMusicActive();
    }

    static synchronized void run(String action)throws Exception{
        String code=null;
        switch(action){
            case "action:previous_app":
                Helper.command("/system/bin/input", "keyevent", "KEYCODE_APP_SWITCH", "KEYCODE_APP_SWITCH");
                return;
            case "action:media_pause":code="127";break;
            case "action:assistant":code=String.valueOf(android.view.KeyEvent.KEYCODE_VOICE_ASSIST);break;
            case "action:screenshot":code="120";break;case "action:lock":code="223";break;
            case "action:media_toggle":code="85";break;case "action:media_next":code="87";break;case "action:media_previous":code="88";break;
            case "action:volume_up":code="24";break;case "action:volume_down":code="25";break;
        }
        if(code!=null){Helper.command("/system/bin/input","keyevent",code);return;}
        if(action.equals("action:notifications")||action.equals("action:quick_settings")){Helper.command("/system/bin/cmd","statusbar",action.equals("action:notifications")?"expand-notifications":"expand-settings");return;}
        Context ctx=context();
        switch (action) {
            case "action:hotspot":
                toggleHotspot(ctx);
                return;
            case "action:dnd_hour":
                android.app.NotificationManager notifications = ctx.getSystemService(android.app.NotificationManager.class);
                boolean quiet = notifications.getCurrentInterruptionFilter() != android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
                android.net.Uri until = quiet ? null : android.net.Uri.parse("condition://android/countdown/" +
                    (System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)) + "/alarm/false");
                notifications.getClass().getMethod("setZenMode", int.class, android.net.Uri.class, String.class)
                    .invoke(notifications, quiet ? 0 : 1, until, "PlusKey");
                return;
            case "action:wifi_toggle":
                int state = ctx.getSystemService(WifiManager.class).getWifiState();
                if (state == WifiManager.WIFI_STATE_UNKNOWN) throw new IllegalStateException("Wi-Fi state unavailable");
                boolean enabled = state == WifiManager.WIFI_STATE_ENABLED || state == WifiManager.WIFI_STATE_ENABLING;
                Helper.command("/system/bin/cmd", "wifi", "set-wifi-enabled", enabled ? "disabled" : "enabled");
                return;
            case "action:data_toggle":
            case "action:data_sim":
            case "action:data_slot1":
            case "action:data_slot2":
                mobileData(ctx, action);
                return;
        }
        if(action.equals("action:torch") || action.equals("action:pause_torch")){
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
            if (action.equals("action:pause_torch") && !torch && mediaPlaying()) {
                Helper.command("/system/bin/input", "keyevent", "KEYCODE_MEDIA_PAUSE");
                return;
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
    private static void toggleHotspot(Context ctx) throws Exception {
        WifiManager wifi = ctx.getSystemService(WifiManager.class);
        int state = (int) WifiManager.class.getMethod("getWifiApState").invoke(wifi);
        android.net.TetheringManager manager = ctx.getSystemService(android.net.TetheringManager.class);
        if (state == 13) { // WIFI_AP_STATE_ENABLED
            manager.getClass().getMethod("stopTethering", int.class).invoke(manager, 0);
        } else if (state == 11) { // WIFI_AP_STATE_DISABLED
            android.net.TetheringManager.TetheringRequest request =
                new android.net.TetheringManager.TetheringRequest.Builder(0).build();
            CompletableFuture<Void> started = new CompletableFuture<>();
            manager.startTethering(request, Runnable::run, new android.net.TetheringManager.StartTetheringCallback() {
                public void onTetheringStarted() { started.complete(null); }
                public void onTetheringFailed(int error) {
                    started.completeExceptionally(new IllegalStateException("Hotspot error " + error));
                }
            });
            started.get(10, TimeUnit.SECONDS);
        } else throw new IllegalStateException("Hotspot changing or unavailable");
    }

    private static void mobileData(Context ctx, String action) throws Exception {
        SubscriptionManager subscriptions = ctx.getSystemService(SubscriptionManager.class);
        int current = SubscriptionManager.getDefaultDataSubscriptionId();
        if (subscriptions == null || subscriptions.getActiveSubscriptionInfo(current) == null) {
            throw new IllegalStateException("No active default data SIM");
        }
        TelephonyManager phones = ctx.getSystemService(TelephonyManager.class);
        TelephonyManager source = phones.createForSubscriptionId(current);
        boolean enabled = source.isDataEnabled();
        if (action.equals("action:data_toggle")) {
            source.setDataEnabled(!enabled);
            return;
        }

        int slot = action.equals("action:data_slot1") ? 0 : action.equals("action:data_slot2") ? 1 : -1;
        if (slot >= 0) {
            SubscriptionInfo chosen = subscriptions.getActiveSubscriptionInfoForSimSlotIndex(slot);
            if (chosen == null || chosen.isOpportunistic()) throw new IllegalStateException("Data SIM unavailable");
            if (chosen.getSubscriptionId() != current) selectDataSim(subscriptions, phones, chosen.getSubscriptionId(), enabled);
            return;
        }

        // Android returns active subscriptions in SIM slot order.
        List<SubscriptionInfo> active = subscriptions.getActiveSubscriptionInfoList();
        if (active == null) return;
        for (int i = 0; i < active.size(); i++) {
            if (active.get(i).getSubscriptionId() != current) continue;
            for (int offset = 1; offset < active.size(); offset++) {
                SubscriptionInfo next = active.get((i + offset) % active.size());
                if (next.isOpportunistic()) continue;
                selectDataSim(subscriptions, phones, next.getSubscriptionId(), enabled);
                return;
            }
            return;
        }
    }
    private static void selectDataSim(SubscriptionManager subscriptions, TelephonyManager phones, int id, boolean enabled) throws Exception {
        TelephonyManager destination = phones.createForSubscriptionId(id);
        SubscriptionManager.class.getMethod("setDefaultDataSubscriptionId", int.class).invoke(subscriptions, id);
        if (destination.isDataEnabled() != enabled) destination.setDataEnabled(enabled);
    }
    static void feedback()throws Exception{
        Helper.command("/system/bin/cmd","vibrator_manager","feedback","-d","PlusKey","4");
    }
    static synchronized void shutdown(){if(cameraThread!=null)cameraThread.quitSafely();}
}
