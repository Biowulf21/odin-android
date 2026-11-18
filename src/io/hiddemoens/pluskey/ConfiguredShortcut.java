package io.hiddemoens.pluskey;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.ComponentName;
import android.os.Bundle;
import android.widget.Toast;
import java.util.UUID;

/** Starts a user-configured shortcut with PlusKey's ordinary app permissions. */
public final class ConfiguredShortcut extends Activity {
    static Targets.Choice save(Context context, String owner, Intent result) {
        Intent returned = result.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        if (returned == null || returned.getSelector() != null || returned.getClipData() != null)
            throw new IllegalArgumentException("Unsupported shortcut");
        Intent launch = new Intent(returned).setPackage(owner);
        ResolveInfo resolved = context.getPackageManager().resolveActivity(launch, 0);
        if (resolved == null) throw new IllegalArgumentException("Missing activity");
        ActivityInfo info = resolved.activityInfo;
        if (!owner.equals(info.packageName) || !info.exported || (info.permission != null &&
            context.checkSelfPermission(info.permission) != android.content.pm.PackageManager.PERMISSION_GRANTED))
            throw new IllegalArgumentException("Inaccessible shortcut");
        launch.setComponent(new ComponentName(owner, info.name)).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String uri = launch.toUri(Intent.URI_INTENT_SCHEME);
        String key = "configured:" + UUID.randomUUID();
        String name = result.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        if (name == null || name.trim().isEmpty()) name = info.loadLabel(context.getPackageManager()).toString();
        context.getSharedPreferences("configuredShortcuts", MODE_PRIVATE).edit().putString(key, uri).apply();
        return new Targets.Choice(name, key);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            String key = getIntent().getStringExtra("target");
            if (Options.isConfigured(key) && !getSystemService(KeyguardManager.class).isKeyguardLocked()) {
                String uri = getSharedPreferences("configuredShortcuts", MODE_PRIVATE).getString(key, null);
                if (uri != null) startActivity(Intent.parseUri(uri, Intent.URI_INTENT_SCHEME));
            }
        } catch (Exception error) {
            Toast.makeText(this, "Shortcut unavailable", Toast.LENGTH_SHORT).show();
        } finally { finish(); }
    }
}
