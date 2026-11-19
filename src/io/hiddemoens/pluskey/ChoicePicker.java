package io.hiddemoens.pluskey;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import java.text.Collator;
import java.util.*;

final class ChoicePicker {
    interface Selected { void select(Targets.Choice choice); }

    private static final class Entry {
        final Targets.Choice choice;
        final ResolveInfo app;
        final String searchName;
        Entry(Targets.Choice choice, ResolveInfo app) {
            this.choice = choice;
            this.app = app;
            this.searchName = choice.label.toLowerCase(Locale.ROOT);
        }
    }

    private final Context context;
    private final Selected selected;
    private final List<Entry> visible = new ArrayList<>();
    private final List<Entry> actions = new ArrayList<>();
    private final LruCache<String, Drawable> icons = new LruCache<>(32);
    private List<Entry> apps;
    private Set<String> secureCameras = Collections.emptySet();
    private Map<String, List<Targets.Choice>> lockedApps = Collections.emptyMap();
    private TextView legend;
    private BottomSheetDialog sheet;
    private EditText search;
    private ListView list;
    private TextView empty;
    private String current, gestureTitle, playing;
    private int mediaStep, mainPosition;
    private TextView gesture;
    private TabLayout tabs;
    private ImageButton back;
    private View timingButton;
    private boolean browsingApps, loading;
    private final Rows rows = new Rows();

    ChoicePicker(Context context, Selected selected) {
        this.context = context;
        this.selected = selected;
    }

    void show(String title, String unsetLabel, String current, Runnable timing) {
        this.current = current;
        gestureTitle = title;
        if (unsetLabel != null) actions.add(new Entry(new Targets.Choice(unsetLabel, null), null));
        for (int i = 0; i < Targets.ACTIONS.length; i++) {
            if (Targets.ACTIONS[i].equals("action:pause_torch")) continue;
            if (Targets.ACTIONS[i].equals("action:torch") && !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) continue;
            actions.add(new Entry(new Targets.Choice(Targets.LABELS[i], Targets.ACTIONS[i]), null));
        }
        actions.add(new Entry(new Targets.Choice("If media is playing…", "media:"), null));
        sheet = new BottomSheetDialog(context);
        sheet.setDismissWithAnimation(true);
        LinearLayout body = Ui.column(context);
        body.setPadding(dp(18), dp(12), dp(18), dp(14));
        body.setFocusableInTouchMode(true);
        View handle = new View(context);
        handle.setBackground(rounded(0xffa6a19d, 2));
        LinearLayout.LayoutParams grip = new LinearLayout.LayoutParams(dp(34), dp(4));
        grip.gravity = Gravity.CENTER_HORIZONTAL;
        grip.bottomMargin = dp(12);
        body.addView(handle, grip);
        LinearLayout heading = Ui.actions(context, body);
        gesture = new TextView(context);
        gesture.setText(title);
        gesture.setTextSize(28);
        gesture.setTypeface(null, Typeface.BOLD);
        heading.addView(gesture, new LinearLayout.LayoutParams(0, -2, 1));
        back = Ui.navigationButton(context);
        back.setContentDescription("Back to gestures");
        back.setPadding(dp(12), dp(12), dp(12), dp(12));
        back.setImageResource(R.drawable.ic_back);
        back.setOnClickListener(v -> {
            if (mediaStep == 0) sheet.cancel();
            else showStep(mediaStep - 1);
        });
        sheet.setOnKeyListener((dialog, key, event) -> {
            if (key != android.view.KeyEvent.KEYCODE_BACK || mediaStep == 0) return false;
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) showStep(mediaStep - 1);
            return true;
        });
        heading.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        int accent = Ui.color(context, androidx.appcompat.R.attr.colorPrimary);
        int surface = Ui.color(context, com.google.android.material.R.attr.colorSurfaceContainer);
        tabs = new TabLayout(context);
        tabs.setBackground(rounded(surface, 24));
        tabs.setTabMode(TabLayout.MODE_FIXED);
        tabs.setTabGravity(TabLayout.GRAVITY_FILL);
        tabs.setTabIndicatorFullWidth(true);
        tabs.setTabRippleColor(null);
        tabs.setSelectedTabIndicator(new InsetDrawable(rounded(accent, 22), dp(4)));
        tabs.setSelectedTabIndicatorColor(accent);
        tabs.setSelectedTabIndicatorGravity(TabLayout.INDICATOR_GRAVITY_STRETCH);
        tabs.setTabTextColors(Ui.color(context, com.google.android.material.R.attr.colorOnSurfaceVariant),
            Ui.color(context, com.google.android.material.R.attr.colorOnPrimary));
        tabs.addTab(tabs.newTab().setText("Actions"));
        tabs.addTab(tabs.newTab().setText("Apps"));
        LinearLayout.LayoutParams segment = new LinearLayout.LayoutParams(-1, dp(48));
        segment.topMargin = dp(6);
        segment.bottomMargin = dp(12);
        body.addView(tabs, segment);
        legend = Ui.text(context, body, "Offers an action you can use while locked", 14, false);
        Drawable lock = context.getDrawable(R.drawable.ic_locked_action).mutate();
        lock.setTint(accent);
        legend.setCompoundDrawablesRelativeWithIntrinsicBounds(lock, null, null, null);
        legend.setCompoundDrawablePadding(dp(8));
        search = new EditText(context);
        search.setSingleLine();
        search.setHint("Search apps");
        search.setTextSize(16);
        search.setBackground(rounded(surface, 16));
        search.setPadding(dp(16), 0, dp(16), 0);
        Drawable lens = context.getDrawable(android.R.drawable.ic_menu_search).mutate();
        lens.setTint(Ui.color(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
        lens.setBounds(0, 0, dp(20), dp(20));
        search.setCompoundDrawablesRelative(lens, null, null, null);
        search.setCompoundDrawablePadding(dp(10));
        LinearLayout.LayoutParams field = new LinearLayout.LayoutParams(-1, dp(48));
        field.bottomMargin = dp(10);
        body.addView(search, field);
        list = new ListView(context);
        list.setNestedScrollingEnabled(true);
        sheet.getBehavior().setDraggableOnNestedScroll(false);
        list.setBackground(rounded(surface, 20));
        list.setClipToOutline(true);
        list.setDivider(null);
        list.setDrawSelectorOnTop(true);
        list.setAdapter(rows);
        int height = Math.min(dp(340), context.getResources().getDisplayMetrics().heightPixels / 3);
        LinearLayout.LayoutParams results = new LinearLayout.LayoutParams(-1, height);
        results.weight = 1;
        body.addView(list, results);
        empty = Ui.text(context, body, "", 16, true);
        if (timing != null) timingButton = Ui.button(context, body, "Press timing", false, v -> { sheet.dismiss(); timing.run(); });
        list.setOnItemClickListener((parent, view, position, id) -> pick(visible.get(position)));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void afterTextChanged(Editable value) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
        });
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            public void onTabSelected(TabLayout.Tab tab) {
                browsingApps = tab.getPosition() == 1;
                if (browsingApps && apps == null && !loading) loadApps();
                refresh();
                list.setSelection(0);
            }
            public void onTabUnselected(TabLayout.Tab tab) {}
            public void onTabReselected(TabLayout.Tab tab) {}
        });
        sheet.setContentView(body);
        refresh();
        sheet.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        sheet.show();
    }

    private void loadApps() {
        loading = true;
        new Thread(() -> {
            List<Entry> found = new ArrayList<>();
            PackageManager pm = context.getPackageManager();
            Map<String, List<Targets.Choice>> locked = new HashMap<>();
            Set<String> cameras = new HashSet<>();
            Intent secure = new Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
            for (ResolveInfo handler : pm.queryIntentActivities(secure, PackageManager.MATCH_DEFAULT_ONLY)) {
                if (handler.activityInfo.exported && (handler.activityInfo.permission == null ||
                    context.checkSelfPermission(handler.activityInfo.permission) == PackageManager.PERMISSION_GRANTED))
                    cameras.add(handler.activityInfo.packageName);
            }
            for (ResolveInfo app : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
                if (!app.activityInfo.exported || context.getPackageName().equals(app.activityInfo.packageName)) continue;
                String target = new ComponentName(app.activityInfo.packageName, app.activityInfo.name).flattenToString();
                locked.computeIfAbsent(app.activityInfo.packageName, owner -> Targets.lockedActivities(context, owner, cameras.contains(owner)));
                found.add(new Entry(new Targets.Choice(app.loadLabel(pm).toString(), target), app));
            }
            Set<String> launcherPackages = new HashSet<>();
            for (Entry entry : found) launcherPackages.add(entry.app.activityInfo.packageName);
            addHandlers(found, secure, Options.SECURE_CAMERA, "Secure camera", launcherPackages);
            addHandlers(found, new Intent(Intent.ACTION_CREATE_NOTE), "note:", "Create note", Collections.emptySet());
            addHandlers(found, new Intent(android.speech.RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE),
                "voice:", "Hands-free voice", Collections.emptySet());
            addHandlers(found, new Intent(Intent.ACTION_CREATE_SHORTCUT), "configure:", "Configure shortcut", Collections.emptySet());
            Collator collator = Collator.getInstance();
            found.sort((a, b) -> collator.compare(a.choice.label, b.choice.label));
            context.getMainExecutor().execute(() -> {
                if (!sheet.isShowing()) return;
                locked.values().removeIf(List::isEmpty);
                lockedApps = locked;
                secureCameras = cameras;
                apps = found;
                loading = false;
                refresh();
            });
        }, "PlusKey-app-picker").start();
    }

    private void addHandlers(List<Entry> entries, Intent intent, String prefix, String label, Set<String> skip) {
        PackageManager pm = context.getPackageManager();
        Set<String> added = new HashSet<>();
        for (ResolveInfo handler : pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
            android.content.pm.ActivityInfo info = handler.activityInfo;
            if (!info.exported || skip.contains(info.packageName) || info.permission != null &&
                context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED) continue;
            boolean configure = prefix.equals("configure:");
            String target = prefix + (configure ? new ComponentName(info.packageName, info.name).flattenToString() : info.packageName);
            if (!added.add(target)) continue;
            String name = (configure ? handler.loadLabel(pm) : info.applicationInfo.loadLabel(pm)) + " · " + label;
            entries.add(new Entry(new Targets.Choice(name, target), handler));
        }
    }

    private void refresh() {
        search.setVisibility(browsingApps ? View.VISIBLE : View.GONE);
        legend.setVisibility(browsingApps && (!secureCameras.isEmpty() || !lockedApps.isEmpty()) ? View.VISIBLE : View.GONE);
        visible.clear();
        if (mediaStep != 0) {
            for (Entry entry : actions) if (Options.builtIn(entry.choice.target)) visible.add(entry);
        } else if (!browsingApps) visible.addAll(actions);
        else if (apps != null) {
            String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            for (Entry entry : apps) if (entry.searchName.contains(query)) visible.add(entry);
        }
        empty.setText(loading ? "Loading apps…" : "No matching apps");
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(visible.isEmpty() ? View.GONE : View.VISIBLE);
        rows.notifyDataSetChanged();
    }

    private void pick(Entry entry) {
        if (mediaStep == 1) {
            playing = entry.choice.target;
            showStep(2);
            return;
        }
        if (mediaStep == 2) {
            String target = "media:" + playing + "," + entry.choice.target;
            finish(new Targets.Choice(Targets.label(context, target), target));
            return;
        }
        if ("media:".equals(entry.choice.target)) {
            mainPosition = list.getFirstVisiblePosition();
            String[] saved = Options.mediaBranches(current);
            playing = saved == null ? null : saved[0];
            showStep(1);
            return;
        }
        if (entry.app != null && Options.validComponent(entry.choice.target)) {
            List<Targets.Choice> shortcuts = Targets.shortcuts(context, entry.app);
            shortcuts.addAll(0, lockedApps.getOrDefault(entry.app.activityInfo.packageName, Collections.emptyList()));
            if (secureCameras.contains(entry.app.activityInfo.packageName)) {
                shortcuts.add(0, new Targets.Choice(entry.choice.label + " · Secure camera",
                    Options.SECURE_CAMERA + entry.app.activityInfo.packageName));
            }
            if (!shortcuts.isEmpty()) {
                shortcuts.add(0, entry.choice);
                String[] labels = new String[shortcuts.size()];
                int checked = -1;
                for (int i = 0; i < labels.length; i++) {
                    labels[i] = i == 0 ? "Open app" : shortcuts.get(i).label;
                    if (Options.isSecureCamera(shortcuts.get(i).target)) labels[i] = "Open secure camera while locked";
                    if (Objects.equals(shortcuts.get(i).target, current)) checked = i;
                }
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                    .setTitle(entry.choice.label).setSingleChoiceItems(labels, checked, (dialog, index) -> {
                        dialog.dismiss();
                        finish(shortcuts.get(index));
                    }).setNegativeButton("Back", null).show();
                return;
            }
        }
        finish(entry.choice);
    }

    private void showStep(int step) {
        boolean forward = step > mediaStep;
        mediaStep = step;
        gesture.setText(step == 0 ? gestureTitle : step == 1 ? "If media is playing" : "Otherwise");
        back.setContentDescription(step == 0 ? "Back to gestures" : step == 1 ? "Back to actions" : "Back to playback action");
        tabs.setVisibility(step == 0 ? View.VISIBLE : View.GONE);
        if (timingButton != null) timingButton.setVisibility(step == 0 ? View.VISIBLE : View.GONE);
        refresh();
        int position = step == 0 ? mainPosition : 0;
        String selected = selectedTarget();
        for (int i = 0; step != 0 && i < visible.size(); i++) {
            if (visible.get(i).choice.target.equals(selected)) position = i;
        }
        list.setSelection(position);
        list.animate().cancel();
        list.setTranslationX((forward ? 1 : -1) * list.getWidth());
        list.animate().translationX(0).setDuration(180).start();
        gesture.announceForAccessibility(gesture.getText());
    }

    private String selectedTarget() {
        if (mediaStep == 1) return playing;
        String[] saved = Options.mediaBranches(current);
        if (mediaStep == 2) return saved == null ? null : saved[1];
        return saved == null ? current : "media:";
    }

    private void finish(Targets.Choice choice) {
        sheet.dismiss();
        selected.select(choice);
    }

    static int actionIcon(String target) {
        if (target == null) return android.R.drawable.ic_menu_close_clear_cancel;
        if (target.startsWith("media:")) return R.drawable.ic_smart;
        switch (target) {
            case "action:assistant": return android.R.drawable.ic_btn_speak_now;
            case "action:previous_app": return R.drawable.ic_recent;
            case "action:pause_torch": return R.drawable.ic_pause_torch;
            case "action:media_pause": return R.drawable.ic_pause;
            case "action:hotspot": return R.drawable.ic_hotspot;
            case "action:dnd_hour": return R.drawable.ic_dnd;
            case "action:data_slot1": return R.drawable.ic_sim1;
            case "action:data_slot2": return R.drawable.ic_sim2;
            case "action:wifi_toggle": return R.drawable.ic_wifi;
            case "action:data_toggle": return R.drawable.ic_data;
            case "action:data_sim": return R.drawable.ic_sim;
            case "action:torch": return R.drawable.ic_flashlight;
            case "action:screenshot": return R.drawable.ic_capture;
            case "action:lock": return android.R.drawable.ic_lock_lock;
            case "action:notifications": return android.R.drawable.ic_dialog_info;
            case "action:quick_settings": return android.R.drawable.ic_menu_preferences;
            case "action:media_toggle": return android.R.drawable.ic_media_play;
            case "action:media_next": return android.R.drawable.ic_media_next;
            case "action:media_previous": return android.R.drawable.ic_media_previous;
            case "action:silent": return android.R.drawable.ic_lock_silent_mode;
            default: return android.R.drawable.ic_lock_silent_mode_off;
        }
    }

    private final class Rows extends BaseAdapter {
        public int getCount() { return visible.size(); }
        public Entry getItem(int index) { return visible.get(index); }
        public long getItemId(int index) { return index; }
        public View getView(int index, View recycled, ViewGroup parent) {
            LinearLayout row = (LinearLayout) recycled;
            if (row == null) {
                row = new LinearLayout(context);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(52));
                row.setPadding(dp(16), dp(4), dp(16), dp(4));
                row.addView(new ImageView(context), new LinearLayout.LayoutParams(dp(28), dp(28)));
                TextView label = new TextView(context);
                label.setPadding(dp(14), 0, dp(8), 0);
                label.setTextSize(16);
                row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
                TextView check = new TextView(context);
                check.setText("✓");
                check.setTextSize(21);
                check.setTextColor(Ui.color(context, androidx.appcompat.R.attr.colorPrimary));
                row.addView(check);
                ImageView lock = new ImageView(context);
                lock.setImageResource(R.drawable.ic_locked_action);
                lock.setColorFilter(Ui.color(context, androidx.appcompat.R.attr.colorPrimary));
                row.addView(lock, new LinearLayout.LayoutParams(dp(24), dp(24)));
            }
            Entry entry = getItem(index);
            ImageView icon = (ImageView) row.getChildAt(0);
            if (entry.app == null) {
                icon.setImageResource(actionIcon(entry.choice.target));
                icon.setColorFilter(Ui.color(context, com.google.android.material.R.attr.colorOnSurface));
            } else {
                icon.clearColorFilter();
                Drawable drawable = icons.get(entry.choice.target);
                if (drawable == null) {
                    drawable = entry.app.loadIcon(context.getPackageManager());
                    if (drawable != null) icons.put(entry.choice.target, drawable);
                }
                icon.setImageDrawable(drawable);
            }
            boolean launcher = entry.app != null && Options.validComponent(entry.choice.target);
            boolean supportsLocked = Policy.shouldWakeScreen(entry.choice.target) || launcher && (secureCameras.contains(entry.app.activityInfo.packageName) || lockedApps.containsKey(entry.app.activityInfo.packageName));
            TextView label = (TextView) row.getChildAt(1);
            label.setText(entry.choice.label);
            row.getChildAt(3).setVisibility(supportsLocked ? View.VISIBLE : View.GONE);
            label.setContentDescription(supportsLocked ? entry.choice.label + ", supports locked use" : null);
            boolean checked = Objects.equals(selectedTarget(), entry.choice.target)
                || (launcher && lockedApps.getOrDefault(entry.app.activityInfo.packageName, Collections.emptyList()).stream().anyMatch(option -> option.target.equals(current)))
                || (launcher && (Options.SECURE_CAMERA + entry.app.activityInfo.packageName).equals(current));
            row.setBackgroundColor(checked ? 0x18ff902f : android.graphics.Color.TRANSPARENT);
            row.getChildAt(2).setVisibility(checked ? View.VISIBLE : View.INVISIBLE);
            row.setStateDescription(checked ? "Selected" : null);
            return row;
        }
    }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(radius));
        shape.setColor(color);
        return shape;
    }

    private int dp(int n) { return Ui.dp(context, n); }
}
