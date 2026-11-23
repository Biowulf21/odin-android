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
            check(EdgeSwipe.direction(left, inward, 0, 24) == 0, "straight");
            check(EdgeSwipe.direction(left, inward, -40, 24) == 1, "up");
            check(EdgeSwipe.direction(left, inward, 40, 24) == 2, "down");
            check(EdgeSwipe.direction(left, inward, 150, 24) == -1, "vertical");
            check(EdgeSwipe.direction(left, -inward, 0, 24) == -1, "outward");
            check(EdgeSwipe.direction(left, inward / 4, 0, 24) == -1, "too short");
            check(EdgeSwipe.direction(left, inward, -24, 24) == 1, "shallow up");
            check(EdgeSwipe.direction(left, inward, 12, 24) == 0, "straight with a small drift");
            EdgeSwipe.Stroke up = new EdgeSwipe.Stroke(left, 24);
            up.move(inward, 0);
            up.move(inward * 2, 0);
            check(up.finish(inward * 2, -28) == 1, "late upward turn");
            EdgeSwipe.Stroke down = new EdgeSwipe.Stroke(left, 24);
            down.move(inward / 2, 0);
            check(down.finish(inward / 2, 150) == 2, "steep downward turn after an inward swipe");
            EdgeSwipe.Stroke vertical = new EdgeSwipe.Stroke(left, 24);
            vertical.move(0, 60);
            check(vertical.finish(inward / 2, 150) == -1, "vertical scroll never committed inward");
            EdgeSwipe.Stroke resumed = new EdgeSwipe.Stroke(left, 24);
            resumed.move(inward, 0);
            resumed.move(inward, -20);
            check(resumed.finish(inward * 2, -20) == 0, "horizontal ending clears an earlier turn");
        }
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
