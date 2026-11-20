package io.hiddemoens.pluskey;

final class LightState {
    Boolean enabled;
    private Boolean expected;
    boolean held;

    boolean changing() { return expected != null; }
    boolean canHold() { return Boolean.FALSE.equals(enabled) && !changing() && !held; }
    void requested(boolean desired, boolean hold) {
        expected = desired;
        held = hold;
    }
    void observed(Boolean state) {
        enabled = state;
        if (state == null || state.equals(expected)) expected = null;
        if (!Boolean.TRUE.equals(state)) held = false;
    }
}
