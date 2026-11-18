package io.hiddemoens.pluskey;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
final class KeyPress {
    private static final Pattern EVENT = Pattern.compile("\\[\\s*([0-9]+(?:\\.[0-9]+)?)\\]\\s+(?:/dev/input/event[0-9]+:\\s+)?EV_KEY\\s+BTN_TRIGGER_HAPPY32\\s+(DOWN|UP|REPEAT)\\s*");
    private static final long HOLD_MS = 500;
    private final ScheduledExecutorService timer;
    private final Runnable onHold;
    private ScheduledFuture<?> pendingHold;
    private long pressId;
    private double eventDown = -1;
    private boolean longFired;

    KeyPress() {
        this(null, null);
    }

    KeyPress(ScheduledExecutorService timer, Runnable onHold) {
        this.timer = timer;
        this.onHold = onHold;
    }

    boolean accept(String line) { return gesture(line) == 1; }

    synchronized int gesture(String line) {
        Matcher match = EVENT.matcher(line);
        if (!match.matches()) return 0;
        String value = match.group(2);
        if (value.equals("DOWN") && eventDown < 0) {
            eventDown = Double.parseDouble(match.group(1));
            longFired = false;
            if (timer != null) {
                long id = ++pressId;
                pendingHold = timer.schedule(() -> fireHold(id), HOLD_MS, TimeUnit.MILLISECONDS);
            }
        }
        if (!value.equals("UP") || eventDown < 0) return 0;
        if (pendingHold != null) {
            pendingHold.cancel(false);
            pendingHold = null;
        }
        double duration = Double.parseDouble(match.group(1)) - eventDown;
        int result = duration < 0 || longFired ? 0 : duration < HOLD_MS / 1000.0 ? 1 : 2;
        eventDown = -1;
        return result;
    }

    private synchronized void fireHold(long id) {
        if (id != pressId || eventDown < 0 || longFired) return;
        pendingHold = null;
        longFired = true;
        onHold.run();
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
