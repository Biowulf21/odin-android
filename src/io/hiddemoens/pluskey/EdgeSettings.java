package io.hiddemoens.pluskey;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import java.util.Collections;
import java.util.function.Consumer;

final class EdgeSettings {
    static final String PREFS = "edges";
    static final int WIDTH = 20;
    static final int HEIGHT = 96;
    static final int POSITION = 50;

    final LinearLayout page;
    private final MainActivity activity;
    private final SharedPreferences prefs;
    private final View setupCard;
    private final LinearLayout sidePage;

    EdgeSettings(MainActivity activity) {
        this.activity = activity;
        prefs = activity.getSharedPreferences(PREFS, 0);
        page = Ui.column(activity);
        int padding = Ui.dp(activity, 20);
        page.setPadding(padding, padding, padding, Ui.dp(activity, 24));
        Ui.heading(activity, page, "Edge swipe mappings");
        Ui.text(activity, page, "Assign an action or sequence to each direction. The handles are invisible during use.", 14, true);

        LinearLayout setup = Ui.card(activity, page);
        setupCard = (View) setup.getParent();
        Ui.text(activity, setup, "Enable the edge swipe service", 16, false);
        Ui.text(activity, setup, "In Accessibility, turn on PlusKey edge swipes.", 14, true);
        Ui.button(activity, setup, "Open Accessibility settings", true, view ->
            activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        MaterialButtonToggleGroup sides = new MaterialButtonToggleGroup(activity);
        sides.setSingleSelection(true);
        sides.setSelectionRequired(true);
        MaterialButton left = addSideButton(sides, "Left edge");
        MaterialButton right = addSideButton(sides, "Right edge");
        LinearLayout.LayoutParams tabs = new LinearLayout.LayoutParams(-1, -2);
        tabs.topMargin = Ui.dp(activity, 16);
        page.addView(sides, tabs);
        sidePage = Ui.column(activity);
        page.addView(sidePage);
        boolean showRight = prefs.getBoolean("editing_right", false);
        sides.check(showRight ? right.getId() : left.getId());
        showSide(showRight ? "right" : "left");
        sides.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            boolean isRight = id == right.getId();
            prefs.edit().putBoolean("editing_right", isRight).apply();
            showSide(isRight ? "right" : "left");
        });
        refresh();
    }

    private MaterialButton addSideButton(MaterialButtonToggleGroup group, String title) {
        MaterialButton button = new MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setId(View.generateViewId());
        button.setText(title);
        button.setAllCaps(false);
        button.setMinHeight(Ui.dp(activity, 48));
        group.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        return button;
    }

    void refresh() {
        AccessibilityManager accessibility = activity.getSystemService(AccessibilityManager.class);
        ComponentName service = new ComponentName(activity, EdgeService.class);
        boolean enabled = accessibility.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .stream().anyMatch(info -> service.equals(ComponentName.unflattenFromString(info.getId())));
        setupCard.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    private void showSide(String side) {
        sidePage.removeAllViews();
        boolean left = side.equals("left");
        MaterialSwitch enabled = new MaterialSwitch(activity);
        enabled.setText(left ? "Use left handle" : "Use right handle");
        enabled.setMinHeight(Ui.dp(activity, 52));
        enabled.setChecked(prefs.getBoolean(side + ".enabled", false));
        enabled.setOnCheckedChangeListener((view, checked) -> prefs.edit().putBoolean(side + ".enabled", checked).apply());
        sidePage.addView(enabled, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout mappings = Ui.card(activity, sidePage);
        int padding = Ui.dp(activity, 12);
        mappings.setPadding(padding, Ui.dp(activity, 4), padding, Ui.dp(activity, 4));
        int[] icons = {R.drawable.ic_edge_straight, R.drawable.ic_edge_up, R.drawable.ic_edge_down};
        for (int direction = 0; direction < EdgeSwipe.KEYS.length; direction++) {
            if (direction > 0) Ui.divider(activity, mappings);
            String key = side + "." + EdgeSwipe.KEYS[direction];
            String title = EdgeSwipe.LABELS[direction];
            String fallback = direction == 0 ? "action:back" : "";
            LinearLayout row = Ui.actions(activity, mappings);
            ImageView icon = new ImageView(activity);
            icon.setImageResource(icons[direction]);
            icon.setColorFilter(Ui.color(activity, androidx.appcompat.R.attr.colorPrimary));
            icon.setScaleX(left ? 1f : -1f);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout labels = Ui.mapping(activity, row, icon, title, rowClick(key, title, fallback, row));
            TextView assignment = Ui.text(activity, labels, summary(key, fallback), 14, true);
            assignment.setPadding(0, 0, 0, 0);
            assignment.setMaxLines(2);
            assignment.setEllipsize(TextUtils.TruncateAt.END);
            row.setTag(assignment);
        }
        Ui.button(activity, sidePage, "Handle size & position", false, view -> showGeometry(side));
    }

    private Runnable rowClick(String key, String title, String fallback, LinearLayout row) {
        return () -> {
            String current = prefs.getString(key, fallback);
            Consumer<String> save = target -> {
                prefs.edit().putString(key, target == null ? "" : target).apply();
                ((TextView) row.getTag()).setText(summary(key, fallback));
                activity.requestRecordingFor(target);
            };
            ChainEditor.show(activity, title, current, "Off", save, "Choose an app", close ->
                new ChoicePicker(activity, choice -> {
                    close.run();
                    save.accept(choice.target);
                }, ChoicePicker.Mode.EDGE, false).show("Choose an app", current), Collections.emptyMap());
        };
    }

    private String summary(String key, String fallback) {
        return Targets.summary(activity, prefs.getString(key, fallback), "Off");
    }

    private void showGeometry(String side) {
        LinearLayout body = Ui.column(activity);
        int padding = Ui.dp(activity, 24);
        body.setPadding(padding, Ui.dp(activity, 4), padding, padding);
        Ui.text(activity, body, "Drag the preview to move the handle.", 14, true);
        HandlePreview preview = new HandlePreview(side);
        body.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 154)));
        slider(body, side + ".width", "Width", " dp", 12, 36, WIDTH, preview);
        slider(body, side + ".height", "Height", " dp", 48, 192, HEIGHT, preview);
        preview.position = slider(body, side + ".position", "Vertical position", "%", 0, 100, POSITION, preview);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);
        new MaterialAlertDialogBuilder(activity)
            .setTitle(side.equals("left") ? "Left handle" : "Right handle")
            .setView(scroll).setPositiveButton("Done", null).show();
    }

    private Slider slider(LinearLayout body, String key, String label, String unit,
            int low, int high, int defaultValue, HandlePreview preview) {
        int value = prefs.getInt(key, defaultValue);
        TextView heading = Ui.text(activity, body, label + " · " + value + unit, 14, false);
        Slider slider = new Slider(activity);
        slider.setValueFrom(low);
        slider.setValueTo(high);
        slider.setStepSize(1);
        slider.setTickVisibilityMode(com.google.android.material.slider.TickVisibilityMode.TICK_VISIBILITY_HIDDEN);
        slider.setValue(value);
        slider.setContentDescription(label);
        slider.setLabelFormatter(amount -> Math.round(amount) + unit);
        slider.addOnChangeListener((view, position, fromUser) -> {
            int chosen = Math.round(position);
            heading.setText(label + " · " + chosen + unit);
            prefs.edit().putInt(key, chosen).apply();
            preview.invalidate();
        });
        body.addView(slider, new LinearLayout.LayoutParams(-1, -2));
        return slider;
    }

    private final class HandlePreview extends View {
        private final String side;
        private final RectF display = new RectF();
        private final RectF handle = new RectF();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Slider position;
        private float grabOffset;

        HandlePreview(String side) {
            super(activity);
            this.side = side;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            float margin = Ui.dp(activity, 8);
            android.graphics.Rect screen = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
            float height = getHeight() - 2 * margin;
            float width = Math.min(getWidth() - 2 * margin, height * screen.width() / Math.max(1, screen.height()));
            float left = (getWidth() - width) / 2;
            display.set(left, margin, left + width, margin + height);
            paint.setColor(Ui.color(activity, com.google.android.material.R.attr.colorSurface));
            canvas.drawRoundRect(display, margin, margin, paint);
            paint.setColor(Ui.color(activity, com.google.android.material.R.attr.colorOutlineVariant));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.dp(activity, 1));
            canvas.drawRoundRect(display, margin, margin, paint);
            paint.setStyle(Paint.Style.FILL);

            float scale = height / Math.max(1, screen.height());
            float handleWidth = Math.max(1f, Ui.dp(activity, prefs.getInt(side + ".width", WIDTH)) * scale);
            float handleHeight = Math.min(height, Ui.dp(activity, prefs.getInt(side + ".height", HEIGHT)) * scale);
            float top = margin + (height - handleHeight) * prefs.getInt(side + ".position", POSITION) / 100f;
            float x = side.equals("left") ? display.left : display.right - handleWidth;
            handle.set(x, top, x + handleWidth, top + handleHeight);
            paint.setColor(Ui.color(activity, androidx.appcompat.R.attr.colorPrimary));
            canvas.drawRoundRect(handle, handleWidth / 2, handleWidth / 2, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    float margin = Ui.dp(activity, 12);
                    if (event.getX() < display.left - margin || event.getX() > display.right + margin) return false;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    grabOffset = event.getY() >= handle.top && event.getY() <= handle.bottom
                        ? event.getY() - handle.top : handle.height() / 2;
                    setPosition(event.getY());
                    return true;
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    setPosition(event.getY());
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return super.onTouchEvent(event);
            }
        }

        private void setPosition(float y) {
            float travel = display.height() - handle.height();
            if (travel <= 0) return;
            int percent = Math.round(100 * (y - grabOffset - display.top) / travel);
            position.setValue(Math.max(0, Math.min(100, percent)));
        }
    }
}
