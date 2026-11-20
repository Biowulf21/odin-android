package io.hiddemoens.pluskey;

import java.nio.file.Files;

public class PatternStorageTest {
    public static void main(String[] args) throws Exception {
        Mailbox box = new Mailbox(Files.createTempDirectory("pluskey-patterns").toFile());
        int first = GesturePatterns.id(".-"), longer = GesturePatterns.id(".-." );
        box.addPattern(first);
        box.addPattern(longer);
        try { box.select(first, "hold:action:torch"); throw new AssertionError("reserved occupied prefix"); }
        catch (IllegalArgumentException expected) { }
        box.removePattern(longer);
        box.select(first, "hold:action:torch");
        try { box.addPattern(longer); throw new AssertionError("extended held prefix"); }
        catch (IllegalArgumentException expected) { }
        if (!box.activeGestures().contains(first)) throw new AssertionError("missing enabled pattern");
        box.removePattern(first);
        if (!box.patterns().isEmpty() || box.selected(first)!=null) throw new AssertionError("stale pattern");
        System.out.println("Pattern storage tests passed");
    }
}
