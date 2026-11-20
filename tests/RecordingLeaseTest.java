package io.hiddemoens.pluskey;

public final class RecordingLeaseTest {
    public static void main(String[] args) throws Exception {
        RecordingLease lease = new RecordingLease();
        check(lease.reserve("hold"));
        check(!lease.reserve("toggle"));
        check(!lease.awaitActive("hold", 1));
        check(lease.release("hold"));
        check(lease.reserve("toggle"));
        check(!lease.activate("hold"));
        check(!lease.release("hold"));
        Thread service = new Thread(() -> lease.activate("toggle"));
        service.start();
        check(lease.awaitActive("toggle", 1000));
        service.join();
        check(!lease.pending("toggle"));
        check(lease.release("toggle"));
        check(!lease.awaitActive("toggle", 1000));
        check(!lease.release(null));
        LightState light = new LightState();
        check(!light.canHold());
        light.observed(true);
        check(!light.canHold());
        light.observed(false);
        check(light.canHold());
        light.requested(true, true);
        check(light.held && !light.canHold());
        light.requested(false, false);
        light.observed(true);
        check(light.changing() && !light.canHold());
        light.observed(false);
        check(light.canHold());
        light.requested(true, true);
        light.observed(true);
        light.observed(null);
        light.observed(true);
        check(!light.held && !light.canHold());
        System.out.println("Recording lease checks passed");
    }

    private static void check(boolean result) {
        if (!result) throw new AssertionError();
    }
}
