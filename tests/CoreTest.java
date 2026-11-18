package io.hiddemoens.pluskey;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class CoreTest {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    static String event(double time, String value) {
        return "[ " + time + "] EV_KEY BTN_TRIGGER_HAPPY32 " + value;
    }
    public static void main(String[] args) {
        KeyPress key = new KeyPress();
        check(!key.accept(event(10, "DOWN")), "down must not launch");
        check(!key.accept(event(10.05, "REPEAT")), "repeat must not launch");
        check(key.accept(event(10.2, "UP")), "short release must launch");
        check(!key.accept(event(10.21, "UP")), "duplicate up must not launch");
        key.accept(event(11, "DOWN"));
        key.accept(event(11.3, "DOWN"));
        check(!key.accept(event(11.6, "UP")), "repeat down must not shorten hold");
        key.accept(event(12, "DOWN"));
        check(!key.accept(event(12.5, "UP")), "500ms hold must not launch");
        check(!key.accept("garbage"), "malformed input ignored");
        check(!key.accept("[ 13.0] EV_KEY KEY_POWER UP"), "other key ignored");
        key.accept(event(15, "DOWN"));
        check(!key.accept(event(14.9, "UP")), "negative duration ignored");
        check(!Policy.mayLaunch("showing=true\nscreenState=SCREEN_STATE_ON\ninteractiveState=INTERACTIVE_STATE_AWAKE"), "locked blocked");
        check(!Policy.mayLaunch("showing=false\nscreenState=SCREEN_STATE_OFF\ninteractiveState=INTERACTIVE_STATE_ASLEEP"), "sleep blocked");
        check(!Policy.mayLaunch(""), "unknown policy blocked");
        check(Policy.mayLaunch("  showing=false\n screenState=SCREEN_STATE_ON\n interactiveState=INTERACTIVE_STATE_AWAKE\n"), "unlocked awake allowed");
        check(Policy.mayRunWhileLocked("action:torch"), "torch can run with screen off");
        check(Policy.mayRunWhileLocked("action:silent_toggle"), "ringer can change while locked");
        check(Policy.mayRunWhileLocked("action:media_next"), "media can run with screen off");
        check(Policy.mayRunWhileLocked("action:volume_down"), "volume can change while locked");
        check(Policy.mayRunWhileLocked("action:lock"), "screen lock can run immediately");
        check(!Policy.mayRunWhileLocked("action:screenshot"), "screenshot requires unlock");
        check(!Policy.mayRunWhileLocked("action:notifications"), "notifications require unlock");
        check(!Policy.mayRunWhileLocked("action:quick_settings"), "quick settings require unlock");
        check(!Policy.mayRunWhileLocked("com.example.app/.Main"), "apps require unlock");
        check(!Policy.mayRunWhileLocked("shortcut:com.example.app:encoded"), "shortcuts require unlock");
        String devices = "add device 1: /dev/input/event5\n name: gpio-keys\n KEY_VOLUMEDOWN\nadd device 2: /dev/input/event9\n name: gpio-keys\n KEY (0001): BTN_TRIGGER_HAPPY32\n";
        check("/dev/input/event9".equals(KeyPress.device(devices)), "discover actual key path");
        check(KeyPress.device("add device 1: /dev/input/event0\nKEY_POWER") == null, "missing key does not pick unrelated device");
        KeyPress gestures = new KeyPress();
        check(gestures.gesture(event(20,"DOWN"))==0,"press waits for release");
        check(gestures.gesture(event(20.7,"UP"))==2,"long hold produces long gesture");
        check(gestures.gesture(event(20.8,"UP"))==0,"long gesture is not duplicated");
        checkHoldScheduling();
        System.out.println("Core behavior tests passed");
    }

    private static void checkHoldScheduling() {
        List<Runnable> callbacks = new ArrayList<>();
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1) {
            @Override public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
                check(unit.toMillis(delay) == 500, "hold waits 500 ms");
                callbacks.add(task);
                return super.schedule(task, 1, TimeUnit.DAYS);
            }
        };
        timer.setRemoveOnCancelPolicy(true);
        try {
            int[] fired = {0};
            KeyPress held = new KeyPress(timer, () -> fired[0]++);
            held.gesture("garbage");
            held.gesture(event(29, "REPEAT"));
            check(callbacks.isEmpty(), "idle and stray repeats never schedule work");
            held.gesture(event(30, "DOWN"));
            held.gesture(event(30.1, "DOWN"));
            held.gesture(event(30.2, "REPEAT"));
            check(callbacks.size() == 1, "one timer per press");
            check(held.gesture(event(30.3, "UP")) == 1, "short action fires on release");
            check(timer.getQueue().isEmpty(), "release cancels timer");
            held.gesture(event(31, "DOWN"));
            callbacks.get(0).run();
            check(fired[0] == 0, "old timer cannot fire for a new press");
            timer.remove(timer.getQueue().peek());
            callbacks.get(1).run();
            callbacks.get(1).run();
            check(fired[0] == 1, "hold fires once while button is down");
            check(held.gesture(event(31.6, "UP")) == 0, "held release does not fire again");
            held.gesture(event(40, "DOWN"));
            check(held.gesture(event(40.7, "UP")) == 2, "queued events retain physical hold duration");
            check(timer.getQueue().isEmpty(), "queued release cancels its timer");
        } finally {
            timer.shutdownNow();
        }
    }
}
