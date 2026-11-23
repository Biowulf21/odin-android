package io.hiddemoens.pluskey;

final class EdgeSwipe {
    static final String[] LABELS = {"Straight inward", "Diagonal up", "Diagonal down"};
    static final String[] KEYS = {"straight", "up", "down"};

    // Use signed inward distance so the same angles apply to either edge.
    static int direction(boolean left, float dx, float dy, float minimum) {
        float inward = left ? dx : -dx;
        if (inward < minimum || Math.abs(dy) > 2.75 * inward) return -1;
        if (Math.abs(dy) < .45 * inward) return 0;
        return dy < 0 ? 1 : 2;
    }
    static final class Stroke {
        private final boolean left;
        private final float minimum;
        private float anchorInward, anchorY;
        private boolean committed;
        private int turn = -1;

        Stroke(boolean left, float minimum) {
            this.left = left;
            this.minimum = minimum;
        }

        void move(float dx, float dy) {
            float inward = left ? dx : -dx;
            if (!committed) {
                if (direction(left, dx, dy, minimum) >= 0) {
                    committed = true;
                    anchorInward = inward;
                    anchorY = dy;
                }
                return;
            }

            // Once the swipe has moved inward, recognize a turn from a recent point on its path.
            float horizontal = Math.abs(inward - anchorInward);
            float vertical = dy - anchorY;
            if (Math.abs(vertical) >= minimum * .75f && Math.abs(vertical) > horizontal) {
                turn = vertical < 0 ? 1 : 2;
            } else if (inward - anchorInward >= minimum && Math.abs(vertical) < .45f * horizontal) {
                turn = -1;
            } else {
                return;
            }
            anchorInward = inward;
            anchorY = dy;
        }

        int finish(float dx, float dy) {
            move(dx, dy);
            if ((left ? dx : -dx) < minimum) return -1;
            return turn >= 0 ? turn : direction(left, dx, dy, minimum);
        }
    }
}
