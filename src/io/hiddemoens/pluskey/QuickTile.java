package io.hiddemoens.pluskey;

import android.content.ComponentName;
import java.io.IOException;
import java.util.regex.*;

final class QuickTile {
    private static String spec(String target) {
        ComponentName component = ComponentName.unflattenFromString(target.substring(5));
        if (component == null) throw new IllegalArgumentException("Invalid tile component");
        return "custom(" + component.flattenToShortString() + ")";
    }
    static void click(String target) throws Exception {
        String tile = spec(target);
        String pinned = Helper.command("/system/bin/settings", "get", "secure", "sysui_qs_tiles").trim();
        if (!pinned.equals("null") && !pinned.isEmpty() && !java.util.Arrays.asList(pinned.split(",")).contains(tile)
                && !java.util.Arrays.asList(pinned.split(",")).contains("custom(" + target.substring(5) + ")"))
            throw new IOException("Open Quick Settings → Edit and add this app’s tile first");
        String reply = Helper.command("/system/bin/cmd", "statusbar", "click-tile", target.substring(5));
        if (!reply.trim().isEmpty()) throw new IOException("SystemUI could not click this tile");
    }
    private static int state(String target) throws Exception {
        String dump = Helper.command("/system/bin/dumpsys", "activity", "service", "com.android.systemui/.SystemUIService", "QSTileHost");
        for (String line : dump.split("\n")) {
            if (!line.contains("[spec=" + spec(target) + ",") || !line.contains("expandedAccessibilityClassName=android.widget.Switch,")) continue;
            Matcher match = Pattern.compile(",state=([012])(?:,|\\])").matcher(line);
            if (match.find()) return Integer.parseInt(match.group(1));
        }
        return -1;
    }
    static boolean begin(String target) throws Exception {
        int state = state(target);
        if (state == 2) return false;
        if (state != 1) throw new IOException("Tile state unavailable; use normal activation");
        click(target);
        return true;
    }
    static void end(String target) throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            int state = state(target);
            if (state == 2) { click(target); return; }
            if (state != 1) break;
            if (attempt != 3) Thread.sleep(150);
        }
        throw new IOException("Could not confirm tile switched on; check Quick Settings");
    }
}
