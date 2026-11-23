package io.hiddemoens.pluskey;

import java.util.List;

final class ActionChain {
    static final int MAX_STEPS = 16;
    private static final String PREFIX = "chain:";
    interface Step { void run(String action) throws Exception; }

    static String[] parse(String target) {
        if (target == null || !target.startsWith(PREFIX) || target.length() > 512) return null;
        String[] steps = target.substring(PREFIX.length()).split(",", -1);
        if (steps.length < 2 || steps.length > MAX_STEPS) return null;
        for (String step : steps) if (!Options.builtIn(step)) return null;
        return steps;
    }

    static String encode(List<String> steps) {
        if (steps.size() == 1 && Options.builtIn(steps.get(0))) return steps.get(0);
        String target = PREFIX + String.join(",", steps);
        if (parse(target) == null) throw new IllegalArgumentException("Choose valid built-in actions");
        return target;
    }

    static boolean directNavigation(String step) {
        return "action:back".equals(step) || "action:home".equals(step) || "action:recents".equals(step);
    }

    static void run(String[] steps, boolean helperAvailable, Step stepRunner) throws Exception {
        if (!helperAvailable) {
            for (String step : steps) {
                if (!directNavigation(step)) throw new IllegalStateException("Start the Plus Key helper to run this action");
            }
        }
        for (int i = 0; i < steps.length; i++) {
            try {
                stepRunner.run(steps[i]);
            } catch (Exception error) {
                if (error instanceof InterruptedException) throw error;
                String step = steps[i].substring("action:".length()).replace('_', ' ');
                throw new IllegalStateException("Step " + (i + 1) + " (" + step + "): "
                    + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()), error);
            }
        }
    }
}
