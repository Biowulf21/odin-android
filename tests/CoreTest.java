package io.hiddemoens.pluskey;

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
        String devices = "add device 1: /dev/input/event5\n name: gpio-keys\n KEY_VOLUMEDOWN\nadd device 2: /dev/input/event9\n name: gpio-keys\n KEY (0001): BTN_TRIGGER_HAPPY32\n";
        check("/dev/input/event9".equals(KeyPress.device(devices)), "discover actual key path");
        check(KeyPress.device("add device 1: /dev/input/event0\nKEY_POWER") == null, "missing key does not pick unrelated device");
        KeyPress gestures = new KeyPress();
        check(gestures.gesture(event(20,"DOWN"))==0,"press waits for release");
        check(gestures.gesture(event(20.7,"UP"))==2,"long hold produces long gesture");
        check(gestures.gesture(event(20.8,"UP"))==0,"long gesture is not duplicated");
        KeyPress held = new KeyPress();
        held.gesture(event(30,"DOWN"));
        check(held.tick(30.499)==0,"hold waits until threshold");
        check(held.tick(30.5)==2,"long fires while button still held");
        check(held.tick(31)==0,"held button triggers only once");
        check(held.gesture(event(31.2,"UP"))==0,"long release never triggers another action");
        held.gesture(event(32,"DOWN"));
        check(held.gesture(event(32.1,"UP"))==1,"new short press still works");
        check(held.tick(33)==0,"released short has no pending long action");
        KeyPress delayed=new KeyPress();
        delayed.gesture(event(40,"DOWN"),50);
        check(delayed.gesture(event(40.7,"UP"),50.001)==2,"queued events retain physical hold duration");
        System.out.println("Core behavior tests passed");
    }
}
