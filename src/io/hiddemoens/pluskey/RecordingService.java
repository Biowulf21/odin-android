package io.hiddemoens.pluskey;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Log;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class RecordingService extends Service {
    private static RecordingService current;
    private String session;
    private MediaRecorder recorder;
    private Uri output;
    private boolean started;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String token = intent == null ? null : intent.getStringExtra("session");
        if (intent != null && "stop".equals(intent.getAction())) {
            RecordingProvider.stop(token);
        } else if (RecordingProvider.lease.pending(token) && session == null) {
            current = this;
            session = token;
            try {
                RecordingActivity.checkReady(this);
                if (!RecordingActivity.ready(token)) throw new IllegalStateException("Recording window no longer visible");
                startForeground(32, notification(token), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                prepare(token);
                RecordingActivity.checkReady(this);
                recorder.start();
                started = true;
                RecordingProvider.lease.activate(token);
                RecordingActivity.close(token);
            } catch (Exception error) {
                Log.w("PlusKey", "Recording start failed", error);
                RecordingProvider.stop(token);
            }
        }
        if (session == null) stopSelf(startId);
        return START_NOT_STICKY;
    }

    private Notification notification(String token) {
        Intent stop = new Intent(this, RecordingService.class).setAction("stop")
            .setData(Uri.fromParts("pluskey-recording", token, null)).putExtra("session", token);
        PendingIntent action = PendingIntent.getService(this, 0, stop, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, RecordingActivity.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Recording audio")
            .setContentText("Saving to Music/Plus Key").setUsesChronometer(true).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Stop and save", action).build());
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return builder.build();
    }

    private void prepare(String token) throws IOException {
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss-SSS", Locale.ROOT).format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, "Plus Key " + stamp + ".m4a");
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Plus Key/");
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        output = getContentResolver().insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
        if (output == null) throw new IOException("Cannot create recording");
        recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
        recorder.setOnErrorListener((source, what, extra) -> {
            if (source == recorder) { started = false; RecordingProvider.stop(token); }
        });
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        try (ParcelFileDescriptor file = getContentResolver().openFileDescriptor(output, "rw")) {
            if (file == null) throw new IOException("Cannot open recording");
            recorder.setOutputFile(file.getFileDescriptor());
            recorder.prepare();
        }
    }

    static void stopOwned(String token) {
        if (current == null || !token.equals(current.session)) return;
        RecordingService service = current;
        current = null;
        service.session = null;
        service.finishRecording();
        service.stopForeground(STOP_FOREGROUND_REMOVE);
        service.stopSelf();
    }

    private void finishRecording() {
        boolean saved = false;
        try {
            if (started) { recorder.stop(); saved = true; }
        } catch (RuntimeException error) { Log.w("PlusKey", "Discarding incomplete recording", error); }
        finally {
            started = false;
            if (recorder != null) {
                try { recorder.release(); } catch (RuntimeException error) { Log.w("PlusKey", "Recorder release failed", error); }
                recorder = null;
            }
        }
        if (output == null) return;
        try {
            if (saved) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Media.IS_PENDING, 0);
                getContentResolver().update(output, values, null, null);
            } else getContentResolver().delete(output, null, null);
        } catch (RuntimeException error) { Log.w("PlusKey", "Cannot finalize recording", error); }
        finally { output = null; }
    }

    @Override public void onDestroy() {
        if (current == this) RecordingProvider.stop(session);
        super.onDestroy();
    }
}
