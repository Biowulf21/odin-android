package io.hiddemoens.pluskey;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.util.concurrent.FutureTask;

public final class RecordingProvider extends ContentProvider {
    static final String AUTHORITY = "io.hiddemoens.pluskey.recording";
    static final RecordingLease lease = new RecordingLease();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static IBinder helper;
    private static IBinder.DeathRecipient death;
    private static Runnable expiry;

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String token, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (uid != 0 && uid != 2000) throw new SecurityException("Helper only");
        if (token == null || token.isEmpty()) throw new IllegalArgumentException("Missing session");
        try {
            Bundle result = new Bundle();
            if ("await".equals(method)) {
                result.putBoolean("started", lease.awaitActive(token, 3000));
            } else {
                FutureTask<String> task = new FutureTask<>(() -> command(method, token, extras));
                main.post(task);
                result.putString("status", task.get());
            }
            return result;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Recording request interrupted", error);
        } catch (java.util.concurrent.ExecutionException error) {
            throw new IllegalStateException(error.getCause().getMessage());
        }
    }

    private String command(String method, String token, Bundle extras) throws RemoteException {
        if ("stop".equals(method)) { stop(token); return "stopped"; }
        if (!"hold".equals(method) && !"toggle".equals(method)) throw new IllegalArgumentException("Unknown recording command");
        String previous = lease.owner();
        if (previous != null) {
            if ("hold".equals(method)) return "busy";
            stop(previous);
            return "stopped";
        }
        RecordingActivity.checkReady(getContext());
        IBinder client = extras == null ? null : extras.getBinder("helper");
        if (client == null) throw new IllegalArgumentException("Missing helper");
        IBinder.DeathRecipient recipient = () -> main.post(() -> stop(token));
        client.linkToDeath(recipient, 0);
        helper = client;
        death = recipient;
        lease.reserve(token);
        expiry = () -> { if (lease.pending(token)) stop(token); };
        main.postDelayed(expiry, 5000);
        return "reserved";
    }

    static void stop(String token) {
        if (!lease.release(token)) return;
        main.removeCallbacks(expiry);
        helper.unlinkToDeath(death, 0);
        helper = null;
        death = null;
        expiry = null;
        RecordingActivity.close(token);
        RecordingService.stopOwned(token);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
