package io.hiddemoens.pluskey;
final class Policy {
    static boolean mayLaunch(String text) {
        return text.matches("(?s).*\\n?\\s*showing=false\\s*\\n.*")
            && text.contains("screenState=SCREEN_STATE_ON")
            && text.contains("interactiveState=INTERACTIVE_STATE_AWAKE")
            && !text.matches("(?s).*\\n?\\s*showing=true\\s*\\n.*");
    }
}
