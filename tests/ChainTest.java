package io.hiddemoens.pluskey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ChainTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        for (boolean left : new boolean[]{true, false}) {
            float inward = left ? 48 : -48;
            EdgeSwipe.Stroke stroke = new EdgeSwipe.Stroke(left, 24, 18);
            check(stroke.direction(0) == 0, "middle at initial height");
            check(stroke.direction(-40) == 1, "up relative to the starting point");
            check(stroke.direction(-12) == 0, "returns to middle");
            check(stroke.direction(40) == 2, "down on other side of starting point");
            check(stroke.direction(150) == 2, "steep diagonal after moving inward");
            check(stroke.direction(-20) == 1, "diagonal starts outside the default buffer");
            check(!stroke.canRelease(0) && !stroke.canRelease(-inward), "vertical or outward swipes cannot run");
            check(!stroke.canRelease(inward / 4) && stroke.canRelease(inward / 2), "exact configured threshold");
            check(!stroke.feedback(false), "no tick below threshold or when unmapped");
            check(stroke.feedback(stroke.canRelease(inward)), "tick once when a mapped action can run");
            check(!stroke.feedback(true) && !stroke.feedback(false) && !stroke.feedback(true),
                "no second tick when crossing a direction or distance again");
            check(!new EdgeSwipe.Stroke(left, 64, 18).canRelease(inward), "longer threshold takes effect");
            EdgeSwipe.Stroke wide = new EdgeSwipe.Stroke(left, 24, 32);
            check(wide.direction(-32) == 0 && wide.direction(-33) == 1 && wide.direction(33) == 2, "wide buffer");
            check(new EdgeSwipe.Stroke(left, 24, 6).direction(7) == 2, "narrow buffer");
        }
        check(EdgeSwipe.indicatorRotation(true, 0) == 180 && EdgeSwipe.indicatorRotation(false, 0) == 0, "straight chevrons");
        check(EdgeSwipe.indicatorRotation(true, 1) == 135 && EdgeSwipe.indicatorRotation(false, 1) == 45, "up chevrons");
        check(EdgeSwipe.indicatorRotation(true, 2) == 225 && EdgeSwipe.indicatorRotation(false, 2) == -45, "down chevrons");
        check(EdgeSwipe.indicatorX(true, 0, 400, 24, 8, 72) == 0, "left indicator starts at edge");
        check(EdgeSwipe.indicatorX(true, 60, 400, 24, 8, 72) == 28, "indicator follows nearby movement");
        check(EdgeSwipe.indicatorX(true, 380, 400, 24, 8, 72) == 48, "left indicator stays in outer band");
        check(EdgeSwipe.indicatorX(false, 20, 400, 24, 8, 72) == 328, "right indicator mirrors left");
        check(EdgeSwipe.indicatorX(true, 100, 180, 48, 8, 72) == 0, "large indicator stays at edge on narrow screen");
        List<String> actions = Arrays.asList("action:back", "action:data_on", "action:hotspot_on");
        String target = ActionChain.encode(actions);
        check(Options.validTarget(target), "valid chain");
        check(!Policy.mayRunWhileLocked(target), "navigation chain requires unlock");
        check(Policy.mayRunWhileLocked("chain:action:wifi_off,action:data_on,action:hotspot_on"),
            "network-only chain can run while locked");
        check(Options.validTarget("action:data_toggle"), "legacy action remains valid");
        check(Options.validTarget("hold:action:torch"), "legacy held action remains valid");
        check(Options.validTarget("media:action:torch,action:media_pause"), "legacy media assignment remains valid");
        check(!Options.validTarget("chain:action:back,unknown"), "reject unrecognized step");
        check(!Options.validTarget("chain:action:back,hold:action:torch"), "reject held step");
        check(ActionChain.encode(Arrays.asList("action:home")).equals("action:home"), "single action encoding");
        List<String> ran = new ArrayList<>();
        ActionChain.run(ActionChain.parse(target), true, ran::add);
        check(ran.equals(actions), "order");
        ran.clear();
        try {
            ActionChain.run(ActionChain.parse(target), false, ran::add);
            throw new AssertionError("helper required");
        } catch (IllegalStateException error) {
            check(ran.isEmpty(), "preflight happens before navigation");
        }
        ActionChain.run(new String[]{"action:back", "action:home"}, false, ran::add);
        check(ran.size() == 2, "navigation works alone");
        ran.clear();
        try {
            ActionChain.run(ActionChain.parse(target), true, step -> {
                if (step.equals("action:data_on")) throw new IllegalStateException("No SIM");
                ran.add(step);
            });
            throw new AssertionError("failure should stop the chain");
        } catch (IllegalStateException error) {
            check(error.getMessage().contains("Step 2") && error.getMessage().contains("No SIM"), "failure identifies step");
            check(ran.equals(Arrays.asList("action:back")), "later step skipped");
        }

        Path directory = Files.createTempDirectory("pluskey-chain");
        try {
            Mailbox mailbox = new Mailbox(directory.toFile());
            mailbox.select(KeyPress.SHORT, target);
            check(target.equals(mailbox.selected(KeyPress.SHORT)), "stored chain round trip");
            String token = java.util.UUID.randomUUID().toString();
            mailbox.requestAction(token, 5000, "action:wifi_off");
            check(Arrays.equals(mailbox.consumeAction(), new String[]{token, "5000", "action:wifi_off"}), "private action request");
            check(mailbox.consumeAction() == null, "request claimed only once");
            mailbox.replyAction(token, "");
            check("".equals(mailbox.readActionReply(token)), "correlated success");
            check(mailbox.readActionReply("different") == null, "ignore stale reply");
            mailbox.requestAction(token, 5000, "action:wifi_on");
            mailbox.clearActionRequest();
            check(mailbox.consumeAction() == null, "helper startup drops stale requests");
        } finally {
            try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
                for (Path file : files) Files.delete(file);
            }
            Files.delete(directory);
        }
        System.out.println("Action chain and edge swipe tests passed");
    }
}
