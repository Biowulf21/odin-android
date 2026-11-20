package io.hiddemoens.pluskey;

import java.util.concurrent.TimeUnit;

final class RecordingLease {
    private String owner;
    private boolean active;

    synchronized String owner() { return owner; }

    synchronized boolean reserve(String token) {
        if (token == null || owner != null) return false;
        owner = token;
        return true;
    }

    synchronized boolean pending(String token) { return token != null && token.equals(owner) && !active; }

    synchronized boolean activate(String token) {
        if (!pending(token)) return false;
        active = true;
        notifyAll();
        return true;
    }

    synchronized boolean release(String token) {
        if (token == null || !token.equals(owner)) return false;
        owner = null;
        active = false;
        notifyAll();
        return true;
    }

    synchronized boolean awaitActive(String token, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (pending(token)) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            TimeUnit.NANOSECONDS.timedWait(this, remaining);
        }
        return active && token != null && token.equals(owner);
    }
}
