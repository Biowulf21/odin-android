package io.hiddemoens.pluskey;

final class EdgeSwipe {
    static final String[] LABELS = {"Straight inward", "Diagonal up", "Diagonal down"};
    static final String[] KEYS = {"straight", "up", "down"};

    // Use signed inward distance so the same angles apply to either edge.
    static int direction(boolean left, float dx, float dy, float minimum) {
        float inward = left ? dx : -dx;
        if (inward < minimum || Math.abs(dy) > 2.75 * inward) return -1;
        if (Math.abs(dy) < .58 * inward) return 0;
        return dy < 0 ? 1 : 2;
    }
}
