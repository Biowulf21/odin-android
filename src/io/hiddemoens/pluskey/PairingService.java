package io.hiddemoens.pluskey;
import android.app.*;
import android.content.Intent;
import android.os.IBinder;

/** Temporary foreground work only while user pairs; stops when setup finishes. */
public final class PairingService extends Service {
    public IBinder onBind(Intent intent) { return null; }
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("wireless_setup", "Helper setup", NotificationManager.IMPORTANCE_HIGH));
        startForeground(11, new Notification.Builder(this,"wireless_setup").setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Pairing Plus Key").setContentText("Connecting to this phone. Keep Android pairing window open.").build());
        String code=intent==null?null:intent.getStringExtra("code");
        int port=intent==null?0:intent.getIntExtra("port",0);
        WirelessController controller=WirelessController.get(this);
        controller.prefs().edit().putBoolean("user_stopped",false).apply();
        controller.pair(code,port,()->{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});
        return START_NOT_STICKY;
    }
}
