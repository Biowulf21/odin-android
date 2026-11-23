package io.hiddemoens.pluskey;

final class EdgeSwipe {
    static final String[] LABELS = {"Straight inward", "Diagonal up", "Diagonal down"};
    static final String[] KEYS = {"straight", "up", "down"};

    static final String INDICATOR_SIZE_KEY = "indicator_size";
    static final String INDICATOR_COLOR_KEY = "indicator_color";
    static final int INDICATOR_SIZE = 24;
    static final int INDICATOR_COLOR = 0xffedf2f8;

    static int indicatorX(boolean left, int fingerX, int screenWidth, int size, int gap, int maxBand) {
        int fromEdge = left ? fingerX : screenWidth - fingerX;
        int band = Math.min(maxBand, screenWidth / 5);
        int offset = Math.max(0, Math.min(Math.max(0, band - size), fromEdge - gap - size));
        return left ? offset : Math.max(0, screenWidth - size - offset);
    }

    static final String ACTIVATION_DISTANCE_KEY = "activation_distance";
    static final int ACTIVATION_DISTANCE = 24;
    static final String DIRECTION_BUFFER_KEY = "direction_buffer";
    static final int DIRECTION_BUFFER = 18;
    static final int MIN_DIRECTION_BUFFER = 6;
    static final int MAX_DIRECTION_BUFFER = 48;

    static int indicatorRotation(boolean left, int direction) {
        int tilt = direction == 1 ? -45 : direction == 2 ? 45 : 0;
        return left ? 180 + tilt : -tilt;
    }

    static final class Stroke {
        private final boolean left;
        private final float minimum;
        private final float directionBuffer;
        private boolean feedbackSent;

        Stroke(boolean left, float minimum, float directionBuffer) {
            this.left = left;
            this.minimum = minimum;
            this.directionBuffer = directionBuffer;
        }

        float inward(float dx) {
            return left ? dx : -dx;
        }

        int direction(float dy) {
            if (Math.abs(dy) <= directionBuffer) return 0;
            return dy < 0 ? 1 : 2;
        }

        boolean canRelease(float dx) {
            return inward(dx) >= minimum;
        }

        boolean feedback(boolean ready) {
            if (!ready || feedbackSent) return false;
            feedbackSent = true;
            return true;
        }
    }
}
