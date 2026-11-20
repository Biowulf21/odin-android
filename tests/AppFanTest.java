package io.hiddemoens.pluskey;

import java.nio.file.Files;
import java.util.Arrays;

public final class AppFanTest {
    public static void main(String[] args) throws Exception {
        String a = "com.example.camera/.Camera";
        String b = "com.example.maps/.Maps";
        String fan = "fan:" + b + "," + a;
        Mailbox box = new Mailbox(Files.createTempDirectory("pluskey-fan").toFile());
        box.select(KeyPress.SHORT, fan);
        if (!Arrays.equals(Options.fanApps(box.selected(KeyPress.SHORT)), new String[]{b, a}))
            throw new AssertionError("Assignment must preserve tap order");
        for (String invalid : new String[]{"fan:", "fan:" + a, fan + ",", fan + "," + a,
                fan + ",action:torch", fan + ",bad", fan + ",a.b/.C,c.d/.E,e.f/.G,g.h/.I,i.j/.K"}) {
            if (Options.validTarget(invalid)) throw new AssertionError(invalid);
        }
        if (!Policy.mayRunWhileLocked(fan) || !Policy.shouldWakeScreen(fan))
            throw new AssertionError("Fan must wake and display while locked");
        if (Policy.mayRunWhileLocked(a) || Policy.shouldWakeScreen("fan:" + a))
            throw new AssertionError("Only valid fans gain lock-screen access");
        System.out.println("App fan checks passed");
    }
}
