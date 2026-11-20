package io.hiddemoens.pluskey;
final class Options {
    static int ringerMode(String action,int current){
        switch(action){
            case "action:sound_cycle":return current==2?1:current==1?0:2;
            case "action:vibrate_toggle":return current==1?2:1;
            case "action:silent_toggle":return current==0?2:0;
            case "action:silent":return 0;case "action:vibrate":return 1;case "action:ring":return 2;
            default:return -1;
        }
    }
    static final String[] ACTIONS={"action:torch","action:sound_cycle","action:vibrate_toggle","action:silent_toggle","action:silent","action:vibrate","action:ring","action:screenshot","action:lock","action:notifications","action:quick_settings","action:media_toggle","action:media_next","action:media_previous","action:volume_up","action:volume_down","action:assistant","action:wifi_toggle","action:data_toggle","action:data_sim",
        "action:previous_app","action:pause_torch","action:media_pause","action:hotspot","action:dnd_hour","action:data_slot1","action:data_slot2","action:record"};
    static boolean builtIn(String target) {
        for (String action : ACTIONS) if (action.equals(target)) return true;
        return false;
    }
    static String[] mediaBranches(String target) {
        if (target == null || !target.startsWith("media:")) return null;
        String[] branches = target.substring(6).split(",", -1);
        return branches.length == 2 && builtIn(branches[0]) && builtIn(branches[1]) ? branches : null;
    }
    static final String LOCKED_ACTIVITY = "locked-activity:";
    static boolean offerLockedLabel(boolean hasSecureCamera, String appLabel, String activityLabel, java.util.Set<String> seen) {
        String label = activityLabel.trim();
        if (hasSecureCamera && label.equalsIgnoreCase(appLabel.trim())) return false;
        return seen.add(label.toLowerCase(java.util.Locale.ROOT));
    }
    static boolean isLockedActivity(String target) {
        return target != null && target.startsWith(LOCKED_ACTIVITY)
            && validComponent(target.substring(LOCKED_ACTIVITY.length()));
    }
    static final String SECURE_CAMERA = "secure-camera:";
    static boolean isSecureCamera(String target) {
        return target != null && target.startsWith(SECURE_CAMERA)
            && validComponent(target.substring(SECURE_CAMERA.length()) + "/.Camera");
    }
    static boolean isLockedAppAction(String value) {
        if (value == null || !(value.startsWith("note:") || value.startsWith("voice:"))) return false;
        return validComponent(value.substring(value.indexOf(':') + 1) + "/.Handler");
    }
    static boolean isConfigured(String value) {
        return value != null && value.matches("configured:[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}");
    }
    static final int MAX_FAN_APPS = 6;

    static String[] fanApps(String target) {
        if (target == null || !target.startsWith("fan:") || target.length() > 4 + MAX_FAN_APPS * 501) return null;
        String[] apps = target.substring(4).split(",", -1);
        if (apps.length < 2 || apps.length > MAX_FAN_APPS) return null;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String app : apps) if (!validComponent(app) || !seen.add(app)) return null;
        return apps;
    }

    static boolean validTarget(String value) {
        if (fanApps(value) != null || isTile(value)) return true;
        if (value != null && value.startsWith("hold:")) return canHold(value.substring(5));
        if(isConfigured(value) || isLockedAppAction(value) || validComponent(value) || isSecureCamera(value) || isLockedActivity(value))return true;
        if(value==null)return false;
        if (builtIn(value) || mediaBranches(value) != null) return true;
        if(!value.startsWith("shortcut:")||value.length()>32768)return false;
        int split=value.indexOf(':',9);
        if(split<0||!validComponent(value.substring(9,split)+"/.Shortcut"))return false;
        try{
            String decoded=new String(java.util.Base64.getUrlDecoder().decode(value.substring(split+1)),java.nio.charset.StandardCharsets.UTF_8);
            String[] intents=decoded.split("\u0000",-1);
            if(intents.length==0||intents.length>8)return false;
            for(String intent:intents)if(!(intent.startsWith("intent:")||intent.startsWith("#Intent;"))||!intent.contains("#Intent;")||!intent.endsWith(";end"))return false;
            return true;
        }catch(IllegalArgumentException error){return false;}
    }
    private static final java.util.regex.Pattern COMPONENT = java.util.regex.Pattern.compile(
        "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)*/\\.?[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    static boolean validComponent(String value) {
        return value != null && value.length() <= 500 && value.indexOf('.') >= 0 && value.indexOf('.') < value.indexOf('/') && COMPONENT.matcher(value).matches();
    }
    static boolean isTile(String value) {
        return value != null && value.startsWith("tile:") && value.length() <= 505 && COMPONENT.matcher(value).region(5, value.length()).matches();
    }
    static boolean canHold(String target) {
        return "action:torch".equals(target) || "action:record".equals(target) || isTile(target);
    }
    static int port(String value) {
        if (value == null || !value.matches("[0-9]{1,5}")) return -1;
        int port = Integer.parseInt(value);
        return port > 0 && port <= 65535 ? port : -1;
    }
    static boolean validCode(String value) { return value != null && value.matches("[0-9]{6}"); }
}
