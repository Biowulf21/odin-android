package io.hiddemoens.pluskey;
final class Policy {
    static boolean shouldWakeScreen(String target) {
        return Options.fanApps(target) != null || "action:assistant".equals(target) || Options.isLockedAppAction(target) || Options.isSecureCamera(target) || Options.isLockedActivity(target);
    }

    static boolean mayRunWhileLocked(String target) {
        if (shouldWakeScreen(target)) return true;
        switch (target) {
            case "action:pause_torch":
            case "action:media_pause":
            case "action:hotspot":
            case "action:dnd_hour":
            case "action:data_slot1":
            case "action:data_slot2":
            case "action:wifi_toggle":
            case "action:data_toggle":
            case "action:data_sim":
            case "action:torch":
            case "action:lock":
            case "action:sound_cycle":
            case "action:vibrate_toggle":
            case "action:silent_toggle":
            case "action:silent":
            case "action:vibrate":
            case "action:ring":
            case "action:media_toggle":
            case "action:media_next":
            case "action:media_previous":
            case "action:volume_up":
            case "action:volume_down":
                return true;
            default:
                return false;
        }
    }

    static boolean mayLaunch(String text) {
        return text.matches("(?s).*\\n?\\s*showing=false\\s*\\n.*")
            && text.contains("screenState=SCREEN_STATE_ON")
            && text.contains("interactiveState=INTERACTIVE_STATE_AWAKE")
            && !text.matches("(?s).*\\n?\\s*showing=true\\s*\\n.*");
    }
}
