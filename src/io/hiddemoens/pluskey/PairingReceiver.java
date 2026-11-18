package io.hiddemoens.pluskey;
import android.app.RemoteInput;
import android.content.*;
import android.os.Bundle;
public final class PairingReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (!"PAIR".equals(intent.getAction())) return;
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        CharSequence code = results == null ? null : results.getCharSequence("code");
        if (code == null) return;
        context.startForegroundService(new Intent(context, PairingService.class).putExtra("code", code.toString().trim()));
        context.getSystemService(android.app.NotificationManager.class).cancel(10);
    }
}
