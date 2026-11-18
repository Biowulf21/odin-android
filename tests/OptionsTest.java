package io.hiddemoens.pluskey;
import java.io.File;
import java.nio.file.Files;
public final class OptionsTest {
    public static void main(String[] args) throws Exception {
        CoreTest.check(Options.validComponent("com.google.ar.lens/com.google.vr.apps.ornament.app.lens.LensLauncherActivity"), "Lens component accepted");
        CoreTest.check(Options.validComponent("com.example.app/.MainActivity"), "relative class accepted");
        for (String bad : new String[]{"", "com.example", "com.example/.Main;reboot", "com.example/.Main\n", "com.example/--user", "a/b/c"})
            CoreTest.check(!Options.validComponent(bad), "invalid component rejected");
        CoreTest.check(Options.port("37123") == 37123, "port accepted");
        CoreTest.check(Options.port("0") == -1 && Options.port("65536") == -1 && Options.port("x") == -1, "invalid ports rejected");
        CoreTest.check(Options.validCode("012345") && !Options.validCode("12345") && !Options.validCode("1234567"), "six digit code including leading zero");
        File dir=Files.createTempDirectory("pluslens-choice-").toFile();
        Mailbox box=new Mailbox(dir);
        box.select("com.example.app/.MainActivity");
        CoreTest.check("com.example.app/.MainActivity".equals(new Mailbox(dir).selected()), "choice persists across helper restart");
        box.select("com.other.app/com.other.app.Home");
        CoreTest.check("com.other.app/com.other.app.Home".equals(box.selected()), "changed choice loaded without restart");
        CoreTest.check(box.selectedLong()==null,"long press defaults to OnePlus");
        box.selectLong("action:torch");
        CoreTest.check("action:torch".equals(new Mailbox(dir).selectedLong()),"long action persists");
        box.selectLong(null);CoreTest.check(box.selectedLong()==null,"OnePlus choice clears custom long action");
        CoreTest.check(Options.validTarget("action:silent")&&!Options.validTarget("action:reboot"),"only supported built-in actions accepted");
        int ringer=2;ringer=Options.ringerMode("action:sound_cycle",ringer);CoreTest.check(ringer==1,"sound cycles to vibrate");
        ringer=Options.ringerMode("action:sound_cycle",ringer);CoreTest.check(ringer==0,"vibrate cycles to silent");
        ringer=Options.ringerMode("action:sound_cycle",ringer);CoreTest.check(ringer==2,"silent cycles to sound");
        CoreTest.check(Options.ringerMode("action:silent_toggle",2)==0&&Options.ringerMode("action:silent_toggle",0)==2,"silent toggle returns to sound");
        CoreTest.check(Options.ringerMode("action:vibrate_toggle",2)==1&&Options.ringerMode("action:vibrate_toggle",1)==2,"vibrate toggle returns to sound");
        String shortcut="shortcut:com.example.app:"+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("#Intent;action=com.example.NEW;component=com.example.app/.Main;end".getBytes("UTF-8"));
        CoreTest.check(Options.validTarget(shortcut),"published shortcut encoding accepted");
        CoreTest.check(!Options.validTarget("shortcut:com.example.app:"+java.util.Base64.getUrlEncoder().encodeToString("--user 10".getBytes("UTF-8"))),"shortcut command injection rejected");
        try { box.select("com.test/.Main;reboot"); throw new AssertionError("unsafe choice accepted"); }
        catch (IllegalArgumentException expected) { }
        for(File f:dir.listFiles())f.delete(); dir.delete();
        System.out.println("App choice and pairing input tests passed");
    }
}
