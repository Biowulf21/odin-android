package io.hiddemoens.pluskey;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.TextView;

public final class RecordingActivity extends Activity {
    static final String CHANNEL = "audio_recording";
    private static RecordingActivity visible;
    private String token;
    private boolean requested;

    public static String[] requiredPermissions() {
        return Build.VERSION.SDK_INT >= 33
            ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}
            : new String[]{Manifest.permission.RECORD_AUDIO};
    }

    static void checkReady(Context context) {
        for (String permission : requiredPermissions()) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
                throw new IllegalStateException("Allow microphone and notifications in Plus Key first");
        }
        if (!context.getSystemService(PowerManager.class).isInteractive() || context.getSystemService(KeyguardManager.class).isKeyguardLocked())
            throw new IllegalStateException("Unlock phone to record");
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Audio recording", NotificationManager.IMPORTANCE_LOW));
        if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL).getImportance() == NotificationManager.IMPORTANCE_NONE)
            throw new IllegalStateException("Enable recording notifications first");
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        token = getIntent().getStringExtra("session");
        if (!RecordingProvider.lease.pending(token)) { finish(); return; }
        setTitle("Plus Key");
        TextView text = new TextView(this);
        text.setText("Starting audio recording…");
        text.setPadding(32, 24, 32, 24);
        setContentView(text);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!RecordingProvider.lease.pending(token)) finish();
        else if (!isFinishing()) visible = this;
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused || visible != this || requested || isFinishing()) return;
        if (!RecordingProvider.lease.pending(token)) { finish(); return; }
        requested = true;
        try {
            checkReady(this);
            startService(new Intent(this, RecordingService.class).putExtra("session", token));
        } catch (RuntimeException error) { RecordingProvider.stop(token); }
    }

    @Override protected void onPause() {
        if (visible == this) visible = null;
        if (RecordingProvider.lease.pending(token)) { RecordingProvider.stop(token); finish(); }
        super.onPause();
    }

    static boolean ready(String token) {
        return visible != null && !visible.isFinishing() && visible.hasWindowFocus() && token.equals(visible.token);
    }

    static void close(String token) { if (visible != null && token.equals(visible.token)) visible.finish(); }
}
