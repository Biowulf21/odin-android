package io.hiddemoens.pluskey;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class ChainEditor {
    private final Context context;
    private final List<String> steps = new ArrayList<>();
    private final boolean clearOnEmpty;
    private final String standalone;
    private final LinearLayout rows;
    private final MaterialButton addAction;
    private final AlertDialog dialog;

    static void show(Context context, String title, String current, String unsetLabel,
            Consumer<String> save, String alternativesLabel, Consumer<Runnable> chooseAlternative,
            Map<String, Runnable> options) {
        new ChainEditor(context, title, current, unsetLabel, save, alternativesLabel, chooseAlternative, options).open();
    }

    private ChainEditor(Context context, String title, String current, String unsetLabel,
            Consumer<String> save, String alternativesLabel, Consumer<Runnable> chooseAlternative,
            Map<String, Runnable> options) {
        this.context = context;
        String[] saved = ActionChain.parse(current);
        if (saved != null) Collections.addAll(steps, saved);
        else if (Options.builtIn(current)) steps.add(current);
        clearOnEmpty = !steps.isEmpty() && unsetLabel != null;
        standalone = steps.isEmpty() && current != null && !current.isEmpty() ? current : null;

        LinearLayout body = Ui.column(context);
        int padding = Ui.dp(context, 24);
        body.setPadding(padding, 0, padding, Ui.dp(context, 8));
        Ui.text(context, body, "Actions run in order. Add one or build a sequence.", 14, true);
        ScrollView scroll = new ScrollView(context) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int limit = Math.round(getResources().getDisplayMetrics().heightPixels * .34f);
                int height = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED ? limit
                    : Math.min(limit, MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
            }
        };
        body.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        rows = Ui.column(context);
        rows.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 4));
        scroll.addView(rows);
        addAction = Ui.button(context, body, "Add action", true, view ->
            new ChoicePicker(context, choice -> {
                steps.add(choice.target);
                render();
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            }, ChoicePicker.Mode.STEP, false).show("Add action", null));
        MaterialButton alternative = new MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        alternative.setText(alternativesLabel);
        alternative.setAllCaps(false);
        alternative.setMinHeight(Ui.dp(context, 48));
        alternative.setOnClickListener(v -> chooseAlternative.accept(this::close));
        body.addView(alternative, new LinearLayout.LayoutParams(-1, -2));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(body)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (window, which) ->
                save.accept(steps.isEmpty() ? null : ActionChain.encode(steps)));
        boolean canClear = unsetLabel != null && current != null && !current.isEmpty();
        if (!options.isEmpty() || canClear) builder.setNeutralButton("More", null);
        dialog = builder.create();
        dialog.setOnShowListener(window -> {
            if (!options.isEmpty() || canClear) {
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(anchor -> {
                    PopupMenu menu = new PopupMenu(context, anchor);
                    options.forEach((label, run) -> menu.getMenu().add(label).setOnMenuItemClickListener(item -> {
                        close();
                        run.run();
                        return true;
                    }));
                    if (canClear) {
                        menu.getMenu().add("Off".equals(unsetLabel) ? "Turn off" : unsetLabel)
                            .setOnMenuItemClickListener(item -> {
                                close();
                                save.accept(null);
                                return true;
                            });
                    }
                    menu.show();
                });
            }
            render();
        });
    }

    private void open() { dialog.show(); }
    private void close() { dialog.dismiss(); }

    private void render() {
        rows.removeAllViews();
        addAction.setEnabled(steps.size() < ActionChain.MAX_STEPS);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(!steps.isEmpty() || clearOnEmpty);
        if (steps.isEmpty()) {
            if (standalone == null) {
                Ui.text(context, rows, "No actions in the sequence", 16, true);
            } else {
                Ui.text(context, rows, "Current mapping", 13, true);
                Ui.text(context, rows, Targets.label(context, standalone), 16, false);
            }
        }
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) Ui.divider(context, rows);
            addStep(i);
        }
    }

    private void addStep(int index) {
        String label = Targets.label(context, steps.get(index));
        LinearLayout row = Ui.actions(context, rows);
        row.setMinimumHeight(Ui.dp(context, 52));
        TextView number = new TextView(context);
        number.setText(String.valueOf(index + 1));
        number.setTextColor(Ui.color(context, androidx.appcompat.R.attr.colorPrimary));
        number.setTextSize(13);
        number.setTypeface(null, Typeface.BOLD);
        number.setGravity(Gravity.CENTER);
        number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(number, new LinearLayout.LayoutParams(Ui.dp(context, 28), -2));
        TextView title = new TextView(context);
        title.setText(label);
        title.setTextSize(16);
        title.setContentDescription("Step " + (index + 1) + ", " + label);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton edit = Ui.navigationButton(context);
        edit.setImageResource(R.drawable.ic_more);
        edit.setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12));
        edit.setContentDescription("Edit step " + (index + 1) + ", " + label);
        edit.setOnClickListener(anchor -> {
            PopupMenu menu = new PopupMenu(context, anchor);
            if (index > 0) menu.getMenu().add("Move earlier").setOnMenuItemClickListener(item -> move(index, index - 1));
            if (index + 1 < steps.size()) menu.getMenu().add("Move later").setOnMenuItemClickListener(item -> move(index, index + 1));
            menu.getMenu().add("Remove").setOnMenuItemClickListener(item -> {
                steps.remove(index);
                render();
                return true;
            });
            menu.show();
        });
        row.addView(edit, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));
    }

    private boolean move(int from, int to) {
        Collections.swap(steps, from, to);
        render();
        return true;
    }
}
