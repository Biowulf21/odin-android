package io.hiddemoens.pluskey;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

final class ChainEditor {
    static void show(Context context, String current, Consumer<String> save) {
        List<String> steps = new ArrayList<>();
        String[] saved = ActionChain.parse(current);
        if (saved != null) Collections.addAll(steps, saved);
        else if (Options.builtIn(current)) steps.add(current);
        LinearLayout body = Ui.column(context);
        int padding = Ui.dp(context, 20);
        body.setPadding(padding, 0, padding, padding);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(body);
        LinearLayout rows = Ui.column(context);
        body.addView(rows);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle("Action chain")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (window, which) -> save.accept(ActionChain.encode(steps)))
            .create();
        MaterialButton add = Ui.button(context, body, "Add action", false, view ->
            new ChoicePicker(context, choice -> {
                steps.add(choice.target);
                render(context, rows, steps, dialog);
            }, ChoicePicker.Mode.STEP).show("Add action", null, null, null, null));
        add.setTag("add");
        dialog.show();
        render(context, rows, steps, dialog);
    }

    private static void render(Context context, LinearLayout rows, List<String> steps,
            androidx.appcompat.app.AlertDialog dialog) {
        rows.removeAllViews();
        ((LinearLayout) rows.getParent()).findViewWithTag("add").setEnabled(steps.size() < ActionChain.MAX_STEPS);
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setEnabled(!steps.isEmpty());
        for (int i = 0; i < steps.size(); i++) {
            final int index = i;
            String label = Targets.label(context, steps.get(i));
            LinearLayout card = Ui.card(context, rows);
            Ui.text(context, card, (i + 1) + ". " + label, 16, false);
            LinearLayout controls = Ui.actions(context, card);
            MaterialButton up = Ui.button(context, controls, "↑", false, view -> {
                Collections.swap(steps, index, index - 1);
                render(context, rows, steps, dialog);
            });
            up.setEnabled(i > 0);
            up.setContentDescription("Move " + label + " up");
            MaterialButton down = Ui.button(context, controls, "↓", false, view -> {
                Collections.swap(steps, index, index + 1);
                render(context, rows, steps, dialog);
            });
            down.setEnabled(i + 1 < steps.size());
            down.setContentDescription("Move " + label + " down");
            Ui.button(context, controls, "Remove", false, view -> {
                steps.remove(index);
                render(context, rows, steps, dialog);
            }).setContentDescription("Remove " + label);
            Ui.equal(context, controls);
        }
    }
}
