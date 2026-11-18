package io.hiddemoens.pluskey;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
final class KeyPress {
    static final int SHORT = 1, LONG = 2, DOUBLE = 3, TRIPLE = 4;
    private static final Pattern EVENT = Pattern.compile("\\[\\s*([0-9]+(?:\\.[0-9]+)?)\\]\\s+(?:/dev/input/event[0-9]+:\\s+)?EV_KEY\\s+BTN_TRIGGER_HAPPY32\\s+(DOWN|UP|REPEAT)\\s*");
    private static final long HOLD_MS = 500;
    private final ScheduledExecutorService timer;
    private final IntConsumer dispatch;
    private final IntSupplier activeClickInterval;
    private ScheduledFuture<?> pendingHold;
    private ScheduledFuture<?> pendingClicks;
    private long pressId;
    private long clickTimerId;
    private int clickCount;
    private int clickIntervalMs;
    private long lastReleaseUs;
    private long eventDownUs = -1;
    private boolean longFired;

    KeyPress() {
        this(null, null, () -> 0);
    }

    KeyPress(ScheduledExecutorService timer, IntConsumer dispatch, IntSupplier activeClickInterval) {
        this.timer = timer;
        this.dispatch = dispatch;
        this.activeClickInterval = activeClickInterval;
    }

    boolean accept(String line) { return gesture(line) == SHORT; }

    synchronized int gesture(String line) {
        Matcher match = EVENT.matcher(line);
        if (!match.matches()) return 0;
        String value = match.group(2);
        long eventUs = Math.round(Double.parseDouble(match.group(1)) * 1_000_000);
        if (value.equals("DOWN") && eventDownUs < 0) {
            if (clickCount != 0) {
                long gapUs = eventUs - lastReleaseUs;
                if (gapUs >= clickIntervalMs * 1000 || gapUs < 0) flushClicks();
                else cancelClickTimer();
            }
            eventDownUs = eventUs;
            longFired = false;
            if (timer != null) {
                long id = ++pressId;
                pendingHold = timer.schedule(() -> fireHold(id), HOLD_MS, TimeUnit.MILLISECONDS);
            }
        }
        if (!value.equals("UP") || eventDownUs < 0) return 0;
        if (pendingHold != null) {
            pendingHold.cancel(false);
            pendingHold = null;
        }
        long durationUs = eventUs - eventDownUs;
        eventDownUs = -1;
        if (longFired) return 0;
        if (durationUs < 0 || durationUs >= HOLD_MS * 1000) {
            flushClicks();
            return durationUs < 0 ? 0 : LONG;
        }
        if (clickCount == 0) {
            clickIntervalMs = activeClickInterval.getAsInt();
            if (clickIntervalMs == 0) return SHORT;
        }
        if (++clickCount == 3) {
            clickCount = 0;
            return TRIPLE;
        }
        lastReleaseUs = eventUs;
        long id = ++clickTimerId;
        pendingClicks = timer.schedule(() -> fireClicks(id), clickIntervalMs, TimeUnit.MILLISECONDS);
        return 0;
    }

    private synchronized void fireHold(long id) {
        if (id != pressId || eventDownUs < 0 || longFired) return;
        pendingHold = null;
        longFired = true;
        flushClicks();
        dispatch.accept(LONG);
    }

    private synchronized void fireClicks(long id) {
        if (id == clickTimerId) flushClicks();
    }

    private void flushClicks() {
        if (clickCount == 0) return;
        int gesture = clickCount == 1 ? SHORT : DOUBLE;
        clickCount = 0;
        cancelClickTimer();
        dispatch.accept(gesture);
    }

    private void cancelClickTimer() {
        clickTimerId++;
        if (pendingClicks != null) {
            pendingClicks.cancel(false);
            pendingClicks = null;
        }
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
