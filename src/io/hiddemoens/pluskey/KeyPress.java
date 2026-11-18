package io.hiddemoens.pluskey;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
final class KeyPress {
    private static final Pattern EVENT = Pattern.compile("\\[\\s*([0-9]+(?:\\.[0-9]+)?)\\]\\s+(?:/dev/input/event[0-9]+:\\s+)?EV_KEY\\s+BTN_TRIGGER_HAPPY32\\s+(DOWN|UP|REPEAT)\\s*");
    private double down = -1, eventDown = -1;
    private boolean longFired;
    boolean accept(String line) { return gesture(line)==1; }
    synchronized int gesture(String line) {
        Matcher match=EVENT.matcher(line);
        return match.matches()?gesture(line,Double.parseDouble(match.group(1))):0;
    }
    synchronized int gesture(String line,double now) {
        Matcher match=EVENT.matcher(line);
        if(!match.matches())return 0;
        String value=match.group(2);
        if(value.equals("DOWN")&&down<0){down=now;eventDown=Double.parseDouble(match.group(1));longFired=false;}
        if(!value.equals("UP"))return 0;
        double duration=Double.parseDouble(match.group(1))-eventDown;
        int result=down<0||duration<0||longFired?0:duration<0.5?1:2;
        down=-1;
        return result;
    }
    synchronized int tick(double now) {
        if(down<0||longFired||now-down<0.5)return 0;
        longFired=true;
        return 2;
    }
    static String device(String text) {
        String path = null;
        for (String line : text.split("\\n")) {
            if (line.startsWith("add device ")) {
                String candidate = line.substring(line.indexOf(":") + 1).trim();
                path = candidate.matches("/dev/input/event[0-9]+") ? candidate : null;
            }
            if (line.contains("BTN_TRIGGER_HAPPY32") && path != null) return path;
        }
        return null;
    }
}
