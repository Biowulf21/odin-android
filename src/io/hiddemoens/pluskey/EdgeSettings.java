package io.hiddemoens.pluskey;

import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

final class EdgeSettings {
    static final String PREFS = "edges";
    static final int WIDTH = 20;
    static final int HEIGHT = 96;
    static final int POSITION = 50;

    static void show(MainActivity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, 0);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout body = Ui.column(activity);
        int padding = Ui.dp(activity, 16);
        body.setPadding(padding, 0, padding, padding);
        scroll.addView(body);
        Ui.button(activity, body, "Open Accessibility settings", true, view ->
            activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addSide(activity, body, prefs, "left");
        addSide(activity, body, prefs, "right");
        new MaterialAlertDialogBuilder(activity).setTitle("Edge swipes")
            .setView(scroll).setPositiveButton("Done", null).show();
    }

    private static void addSide(MainActivity activity, LinearLayout body, SharedPreferences prefs, String side) {
        LinearLayout card = Ui.card(activity, body);
        String name = side.equals("left") ? "Left edge" : "Right edge";
        MaterialSwitch enabled = new MaterialSwitch(activity);
        enabled.setText(name);
        enabled.setMinHeight(Ui.dp(activity, 48));
        enabled.setChecked(prefs.getBoolean(side + ".enabled", false));
        enabled.setOnCheckedChangeListener((view, checked) -> prefs.edit().putBoolean(side + ".enabled", checked).apply());
        card.addView(enabled);
        slider(activity, card, prefs, side + ".width", "Width", " dp", 12, 36, WIDTH);
        slider(activity, card, prefs, side + ".height", "Height", " dp", 48, 192, HEIGHT);
        slider(activity, card, prefs, side + ".position", "Vertical position", "%", 0, 100, POSITION);
        for (int direction = 0; direction < EdgeSwipe.KEYS.length; direction++) {
            String key = side + "." + EdgeSwipe.KEYS[direction];
            String title = EdgeSwipe.LABELS[direction];
            String fallback = direction == 0 ? "action:back" : "";
            MaterialButton button = Ui.button(activity, card, mappingLabel(activity, prefs.getString(key, fallback), title),
                false, view -> {
                    String current = prefs.getString(key, fallback);
                    MaterialButton mapping = (MaterialButton) view;
                    java.util.function.Consumer<String> save = target -> {
                        String stored = target == null ? "" : target;
                        prefs.edit().putString(key, stored).apply();
                        mapping.setText(mappingLabel(activity, stored, title));
                        activity.requestRecordingFor(target);
                    };
                    new ChoicePicker(activity, choice -> {
                        if ("chain:".equals(choice.target)) ChainEditor.show(activity, current, save);
                        else save.accept(choice.target);
                    }, ChoicePicker.Mode.EDGE).show(title, "Off", current, null, null);
                });
            button.setMaxLines(2);
        }
    }

    private static String mappingLabel(MainActivity activity, String target, String title) {
        return title + " · " + (target.isEmpty() ? "Off" : Targets.label(activity, target));
    }

    private static void slider(MainActivity activity, LinearLayout body, SharedPreferences prefs,
            String key, String label, String unit, int low, int high, int defaultValue) {
        int value = prefs.getInt(key, defaultValue);
        TextView heading = Ui.text(activity, body, label + " · " + value + unit, 14, true);
        Slider slider = new Slider(activity);
        slider.setValueFrom(low);
        slider.setValueTo(high);
        slider.setStepSize(1);
        slider.setValue(value);
        slider.setContentDescription(label);
        slider.addOnChangeListener((view, position, fromUser) -> {
            int chosen = Math.round(position);
            heading.setText(label + " · " + chosen + unit);
            if (fromUser) prefs.edit().putInt(key, chosen).apply();
        });
        body.addView(slider);
    }
}
