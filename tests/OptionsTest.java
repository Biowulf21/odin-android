package io.hiddemoens.pluskey;
import java.io.File;
import java.nio.file.Files;
public final class OptionsTest {
    public static void main(String[] args) throws Exception {
        for (String target : new String[]{"action:torch", "action:record", "tile:com.example.app/.Toggle",
                "tile:com.example.app/com.example.app.Tiles$Network", "tile:oplus/.Tile"}) {
            CoreTest.check(Options.validTarget(target), "accept target: " + target);
            CoreTest.check(Options.canHold(target) && Options.validTarget("hold:" + target), "accept hold: " + target);
        }
        for (String target : new String[]{null, "action:silent", "com.example.app/.Main", "hold:action:torch",
                "media:action:torch,action:record", "tile:", "tile:oplus", "tile:/Tile", "tile:oplus/",
                "tile:oplus/--user", "tile:com.example/.Tile;reboot", "tile:oplus/.Tile\n", "tile:oplus/a/b"}) {
            CoreTest.check(!Options.canHold(target) && !Options.validTarget("hold:" + target), "reject hold: " + target);
            if (target != null && target.startsWith("tile:")) CoreTest.check(!Options.validTarget(target), "reject tile: " + target);
        }
        CoreTest.check(!Options.validComponent("oplus/.Tile"), "single-segment exception limited to tiles");
        CoreTest.check(Options.validTarget("media:action:media_next,action:previous_app"), "media branches persist");
        CoreTest.check(!Options.validTarget("media:action:media_pause,action:unknown"), "reject unknown fallback");
        CoreTest.check(!Options.validTarget("media:action:torch,action:torch,action:lock"), "require two branches");
        CoreTest.check(!Policy.mayRunWhileLocked("action:previous_app"), "previous app requires unlock");
        CoreTest.check(Options.validComponent("com.google.ar.lens/com.google.vr.apps.ornament.app.lens.LensLauncherActivity"), "Lens component accepted");
        CoreTest.check(Options.validComponent("com.example.app/.MainActivity"), "relative class accepted");
        for (String bad : new String[]{"", "com.example", "com.example/.Main;reboot", "com.example/.Main\n", "com.example/--user", "a/b/c"})
            CoreTest.check(!Options.validComponent(bad), "invalid component rejected");
        CoreTest.check(Options.port("37123") == 37123, "port accepted");
        CoreTest.check(Options.port("0") == -1 && Options.port("65536") == -1 && Options.port("x") == -1, "invalid ports rejected");
        CoreTest.check(Options.validCode("012345") && !Options.validCode("12345") && !Options.validCode("1234567"), "six digit code including leading zero");
        CoreTest.check(Policy.shouldWakeScreen("locked-activity:com.example.app/.Main"), "locked app wakes display");
        CoreTest.check(!Policy.shouldWakeScreen("action:torch") && !Policy.shouldWakeScreen("com.example.app/.Main"), "other gestures preserve display state");
        for (String target : new String[]{"note:com.example.notes", "voice:com.example.voice", "action:assistant"}) {
            CoreTest.check(Options.validTarget(target) && Policy.shouldWakeScreen(target), "lock-screen assignment: " + target);
        }
        CoreTest.check(!Options.validTarget("voice:com.example/.Main"), "voice requires package");
        CoreTest.check(!Options.validTarget("configure:com.example/.Main"), "setup entry cannot be assigned");
        String configured = "configured:28cb4b53-cb15-4c0b-97fb-7b00776fd841";
        CoreTest.check(Options.validTarget(configured) && !Policy.mayRunWhileLocked(configured), "configured shortcuts wait for unlock");
        java.util.Set<String> seen = new java.util.HashSet<>();
        CoreTest.check(!Options.offerLockedLabel(true, "Camera", "Camera", seen), "secure camera replaces generic camera choice");
        CoreTest.check(Options.offerLockedLabel(true, "Camera", "Scanner", seen), "separate camera feature remains");
        CoreTest.check(!Options.offerLockedLabel(true, "Camera", " Scanner ", seen), "duplicate locked label hidden");
        CoreTest.check(Options.offerLockedLabel(false, "Photos", "Photos", seen), "generic locked choice remains without secure camera");
        String secureCamera = Options.SECURE_CAMERA + "com.example.camera";
        CoreTest.check(Policy.shouldWakeScreen(secureCamera), "secure camera wakes display");
        CoreTest.check(Options.validTarget(secureCamera) && Policy.mayRunWhileLocked(secureCamera), "secure camera allowed while locked");
        CoreTest.check(!Policy.mayRunWhileLocked("com.example.camera/.Main"), "normal camera launch waits for unlock");
        CoreTest.check(!Options.validTarget(secureCamera + "/.Main"), "secure camera accepts only package names");
        File dir=Files.createTempDirectory("pluslens-choice-").toFile();
        Mailbox box=new Mailbox(dir);
        box.select(KeyPress.SHORT, "com.example.app/.MainActivity");
        CoreTest.check("com.example.app/.MainActivity".equals(new Mailbox(dir).selected(KeyPress.SHORT)), "choice persists across helper restart");
        box.select(KeyPress.SHORT, "com.other.app/com.other.app.Home");
        CoreTest.check("com.other.app/com.other.app.Home".equals(box.selected(KeyPress.SHORT)), "changed choice loaded without restart");
        CoreTest.check(box.selected(KeyPress.LONG)==null,"long press defaults to OnePlus");
        box.select(KeyPress.LONG, "action:torch");
        CoreTest.check("action:torch".equals(new Mailbox(dir).selected(KeyPress.LONG)),"long action persists");
        box.select(KeyPress.LONG, null);CoreTest.check(box.selected(KeyPress.LONG)==null,"OnePlus choice clears custom long action");
        CoreTest.check(!box.multiPressEnabled(), "multi-press defaults to off");
        box.select(KeyPress.DOUBLE, "action:torch");
        box.select(KeyPress.TRIPLE, "action:silent");
        Mailbox reloaded = new Mailbox(dir);
        CoreTest.check("action:torch".equals(reloaded.selected(KeyPress.DOUBLE)), "double action persists");
        CoreTest.check("action:silent".equals(reloaded.selected(KeyPress.TRIPLE)), "triple action persists");
        box.select(KeyPress.DOUBLE, null);
        CoreTest.check(reloaded.selected(KeyPress.DOUBLE) == null && reloaded.multiPressEnabled(),
            "clearing double keeps triple enabled");
        box.select(KeyPress.TRIPLE, null);
        CoreTest.check(!reloaded.multiPressEnabled(), "clearing both disables multi-press");
        CoreTest.check(Options.validTarget("action:silent")&&!Options.validTarget("action:reboot"),"only supported built-in actions accepted");
        int ringer=2;ringer=Options.ringerMode("action:sound_cycle",ringer);CoreTest.check(ringer==1,"sound cycles to vibrate");
        ringer=Options.ringerMode("action:sound_cycle",ringer);CoreTest.check(ringer==0,"vibrate cycles to silent");
        ringer=Options.ringerMode("action:sound_cycle",ringer);CoreTest.check(ringer==2,"silent cycles to sound");
        CoreTest.check(Options.ringerMode("action:silent_toggle",2)==0&&Options.ringerMode("action:silent_toggle",0)==2,"silent toggle returns to sound");
        CoreTest.check(Options.ringerMode("action:vibrate_toggle",2)==1&&Options.ringerMode("action:vibrate_toggle",1)==2,"vibrate toggle returns to sound");
        String shortcut="shortcut:com.example.app:"+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("#Intent;action=com.example.NEW;component=com.example.app/.Main;end".getBytes("UTF-8"));
        CoreTest.check(Options.validTarget(shortcut),"published shortcut encoding accepted");
        CoreTest.check(!Options.validTarget("shortcut:com.example.app:"+java.util.Base64.getUrlEncoder().encodeToString("--user 10".getBytes("UTF-8"))),"shortcut command injection rejected");
        try { box.select(KeyPress.SHORT, "com.test/.Main;reboot"); throw new AssertionError("unsafe choice accepted"); }
        catch (IllegalArgumentException expected) { }
        for(File f:dir.listFiles())f.delete(); dir.delete();
        System.out.println("App choice and pairing input tests passed");
    }
}
