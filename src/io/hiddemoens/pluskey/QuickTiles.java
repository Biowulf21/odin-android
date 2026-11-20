package io.hiddemoens.pluskey;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.service.quicksettings.TileService;
import java.util.ArrayList;
import java.util.List;

final class QuickTiles {
    static java.util.Set<String> pinned(Context context) {
        String specs = null;
        try { specs = android.provider.Settings.Secure.getString(context.getContentResolver(), "sysui_qs_tiles"); }
        catch (SecurityException ignored) { }
        if (specs == null) {
            java.io.File dir = context.getExternalFilesDir(null);
            if (dir != null) try {
                String token = java.util.UUID.randomUUID().toString();
                java.io.File request = new java.io.File(dir, "tile-query");
                java.io.File reply = new java.io.File(dir, "tile-reply");
                java.nio.file.Files.write(request.toPath(), java.util.Collections.singletonList(token));
                for (int retry = 0; retry < 30; retry++) {
                    if (reply.isFile()) {
                        List<String> lines = java.nio.file.Files.readAllLines(reply.toPath());
                        if (lines.size() == 2 && token.equals(lines.get(0))) { specs = lines.get(1); break; }
                    }
                    Thread.sleep(50);
                }
            } catch (java.io.IOException ignored) { }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
        java.util.Set<String> result = new java.util.HashSet<>();
        if (specs != null) for (String part : specs.split(",")) {
            String spec = part.trim();
            if (!spec.startsWith("custom(") || !spec.endsWith(")")) continue;
            ComponentName component = ComponentName.unflattenFromString(spec.substring(7,spec.length()-1));
            if (component != null) result.add(component.flattenToString());
        }
        return result;
    }
    static List<ResolveInfo> discover(Context context, String owner) {
        Intent intent = new Intent(TileService.ACTION_QS_TILE).setPackage(owner);
        List<ResolveInfo> tiles = new ArrayList<>();
        for (ResolveInfo result : context.getPackageManager().queryIntentServices(intent, PackageManager.GET_META_DATA)) {
            ServiceInfo info = result.serviceInfo;
            if (info != null && info.exported && !context.getPackageName().equals(info.packageName)
                    && Manifest.permission.BIND_QUICK_SETTINGS_TILE.equals(info.permission)) tiles.add(result);
        }
        return tiles;
    }

    static boolean toggleable(Context context, ComponentName component) {
        for (ResolveInfo result : discover(context, component.getPackageName())) {
            ServiceInfo info = result.serviceInfo;
            if (component.getClassName().equals(info.name) && info.metaData != null
                    && info.metaData.getBoolean(TileService.META_DATA_TOGGLEABLE_TILE, false)) return true;
        }
        return false;
    }
}
