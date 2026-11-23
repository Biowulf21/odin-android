package io.hiddemoens.pluskey;

import android.content.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import androidx.core.widget.NestedScrollView;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.slider.TickVisibilityMode;
import com.google.android.material.tabs.TabLayout;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;

public final class MainActivity extends AppCompatActivity {
    private static final long MIN_TICK_SPACING_MS = 55;
    private static final long STATUS_CHECK_MS = 2_000;
    private static final String LONG_PRESS_DEFAULT_SET = "long_press_default_set";
    private static final String LONG_PRESS_SETUP_HINT = "Set the OnePlus action to Do nothing to let Plus Key handle long press and remove the ‘Press and hold…’ warning.";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Mailbox mailbox;private WirelessController wireless;private SetupSheet setup;
    private final Map<Integer,TextView> pressNames = new LinkedHashMap<>();
    private final Map<Integer,ImageView> pressIcons = new HashMap<>();
    private final Map<Integer,MaterialButton> pressPreviews = new HashMap<>();
    private LinearLayout customRows;
    private androidx.appcompat.app.AlertDialog captureDialog;
    private boolean helperReady;
    private View longPressSetupCard;
    private FrameLayout pageHost;
    private NestedScrollView pageScroll;
    private LinearLayout buttonPage;
    private ImageButton settingsButton;
    private TabLayout destinations;
    private EdgeSettings edges;
    private int activeDestination;
    private final int[] destinationScroll = new int[2];
    private int previousScroll;
    private final androidx.activity.OnBackPressedCallback settingsBack =
        new androidx.activity.OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { returnToMain(); }
        };
    private static final int STATUS_GREEN = 0xff63dc96;
    private static final int STATUS_AMBER = 0xffffb454;
    private static final int STATUS_RED = 0xffff7975;
    private static final int STATUS_GRAY = 0xffa6a6aa;
    private final GradientDrawable statusDot = new GradientDrawable();
    private TextView status,detail;
    private MaterialButton start,stop,finishSetup;private LinearProgressIndicator progress;
    private boolean resumed,autoAttempt,shizukuAttempt;
    private final Shizuku.OnRequestPermissionResultListener shizukuPermission = (code, result) -> {
        if (code != ShizukuConnection.PERMISSION_REQUEST || isDestroyed()) return;
        if (result == PackageManager.PERMISSION_GRANTED) startHelper();
        else toast("Shizuku permission denied");
        setup.refresh();
    };
    private Updates updates;
    private boolean resumePairingAfterPermission;
    private FileObserver statusObserver;
    private int resumeGeneration;
    private final Runnable statusChanged = () -> refresh(false);
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            if (!ShizukuConnection.authorized()) shizukuAttempt = false;
            else if (!shizukuAttempt && !wireless.busy.get() && !wireless.prefs().getBoolean("user_stopped", false)) {
                shizukuAttempt = true;
                wireless.start();
            }
            refresh(true);
            handler.postDelayed(this, STATUS_CHECK_MS);
        }
    };
    private int configuringGesture;
    private String configuringPackage;
    private final androidx.activity.result.ActivityResultLauncher<Intent> shortcutSetup =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
            String owner = configuringPackage;
            configuringPackage = null;
            if (owner == null || result.getResultCode() != RESULT_OK || result.getData() == null) return;
            try {
                Targets.Choice choice = ConfiguredShortcut.save(this, owner, result.getData());
                saveChoice(configuringGesture, choice);
            } catch (RuntimeException error) {
                toast("App did not return a usable shortcut");
            }
        });

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("shortcutOwner", configuringPackage);
        state.putInt("shortcutGesture", configuringGesture);
        state.putInt("destination", activeDestination);
    }
    protected void onCreate(Bundle saved){
        super.onCreate(saved);
        if (saved != null) {
            configuringPackage = saved.getString("shortcutOwner");
            configuringGesture = saved.getInt("shortcutGesture");
        }
        File mailboxDir = getExternalFilesDir(null);
        updates=new Updates(this);mailbox=new Mailbox(mailboxDir);wireless=WirelessController.get(this);setup=new SetupSheet(this,wireless);
        Shizuku.addRequestPermissionResultListener(shizukuPermission);
        try{if(mailbox.selected(KeyPress.SHORT)==null && getPackageManager().getLaunchIntentForPackage("com.google.ar.lens")!=null)mailbox.select(KeyPress.SHORT, Helper.LENS);}catch(Exception ignored){}
        if (mailboxDir != null) initializeLongPressDefault();
        LinearLayout window = Ui.column(this);
        LinearLayout header = Ui.actions(this, window);
        header.setPadding(dp(20), dp(8), dp(12), dp(8));
        TextView title = new TextView(this);
        title.setText("PlusKey");
        title.setTextSize(30);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Ui.color(this, com.google.android.material.R.attr.colorOnSurface));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        settingsButton = Ui.navigationButton(this);
        header.addView(settingsButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        settingsButton.setOnClickListener(v -> {
            if (settingsBack.isEnabled()) returnToMain();
            else showSettings();
        });
        destinations = new TabLayout(this);
        destinations.setTabMode(TabLayout.MODE_FIXED);
        destinations.setTabIndicatorFullWidth(false);
        destinations.setSelectedTabIndicatorColor(Ui.color(this, androidx.appcompat.R.attr.colorPrimary));
        destinations.setTabTextColors(Ui.color(this, com.google.android.material.R.attr.colorOnSurfaceVariant),
            Ui.color(this, androidx.appcompat.R.attr.colorPrimary));
        destinations.addTab(destinations.newTab().setText("Button"));
        destinations.addTab(destinations.newTab().setText("Edge swipes"));
        window.addView(destinations, new LinearLayout.LayoutParams(-1, dp(48)));
        Ui.divider(this, window);

        pageScroll = new NestedScrollView(this);
        pageScroll.setFillViewport(true);
        pageHost = new FrameLayout(this);
        pageScroll.addView(pageHost, new FrameLayout.LayoutParams(-1, -1));
        window.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        buttonPage = buildButtonPage();
        edges = new EdgeSettings(this);
        activeDestination = saved == null ? 0 : saved.getInt("destination", 0);
        destinations.selectTab(destinations.getTabAt(activeDestination));
        pageHost.addView(activeDestination == 0 ? buttonPage : edges.page);
        destinations.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                destinationScroll[activeDestination] = pageScroll.getScrollY();
                activeDestination = tab.getPosition();
                if (activeDestination == 1) edges.refresh();
                showPage(activeDestination == 0 ? buttonPage : edges.page);
                restoreScroll(destinationScroll[activeDestination]);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { pageScroll.smoothScrollTo(0, 0); }
        });
        getOnBackPressedDispatcher().addCallback(this, settingsBack);
        updateNavigation(false);
        window.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        setContentView(window);
        showSelections();
    }

    private LinearLayout buildButtonPage() {
        LinearLayout content = Ui.column(this);
        content.setPadding(dp(20), dp(20), dp(20), dp(24));
        Ui.heading(this, content, "Button mappings");
        Ui.text(this, content, "Tap a press to assign one or more actions.", 14, true);
        LinearLayout mappings = Ui.card(this, content);
        mappings.setPadding(dp(12), dp(4), dp(12), dp(4));
        addPressRow(mappings, KeyPress.SHORT, "Short press", null);
        Ui.divider(this, mappings);
        addPressRow(mappings, KeyPress.DOUBLE, "Double press", "Off");
        Ui.divider(this, mappings);
        addPressRow(mappings, KeyPress.TRIPLE, "Triple press", "Off");
        Ui.divider(this, mappings);
        addPressRow(mappings, KeyPress.LONG, "Long press", "Managed by OnePlus");
        customRows = Ui.column(this);
        mappings.addView(customRows);
        rebuildPatterns();
        Ui.button(this, content, "Record press pattern", false, v -> recordPattern());

        LinearLayout longPressSetup = Ui.card(this, content);
        longPressSetupCard = (View) longPressSetup.getParent();
        longPressSetupCard.setVisibility(View.GONE);
        Ui.text(this, longPressSetup, "OnePlus long press", 18, false);
        Ui.text(this, longPressSetup, LONG_PRESS_SETUP_HINT, 14, true);
        Ui.button(this, longPressSetup, "Open OnePlus settings", false, v -> onePlusSettings());

        LinearLayout connection = Ui.card(this, content);
        status = Ui.text(this, connection, "Checking button connection…", 16, false);
        statusDot.setShape(GradientDrawable.OVAL);
        statusDot.setSize(dp(8), dp(8));
        statusDot.setColor(STATUS_AMBER);
        status.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        status.setCompoundDrawablePadding(dp(8));
        status.setCompoundDrawablesRelativeWithIntrinsicBounds(statusDot, null, null, null);
        detail = Ui.text(this, connection, "", 14, true);
        progress = new LinearProgressIndicator(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        connection.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        LinearLayout controls = Ui.actions(this, connection);
        start = Ui.button(this, controls, "Start", true, v -> startHelper());
        stop = Ui.button(this, controls, "Stop", false, v -> {
            if (wireless.busy.get()) return;
            try {
                wireless.prefs().edit().putBoolean("user_stopped", true).apply();
                mailbox.requestStop();
                setStatus("Stopping…", STATUS_AMBER);
            } catch (IOException error) { toast("Could not stop. Try again."); }
        });
        finishSetup = Ui.button(this, controls, "Set up button", true, v -> setup.show());
        Ui.equal(this, controls);
        return content;
    }

    private void initializeLongPressDefault() {
        SharedPreferences prefs = wireless.prefs();
        if (prefs.getBoolean(LONG_PRESS_DEFAULT_SET, false)) return;
        try {
            PackageManager packages = getPackageManager();
            PackageInfo installed = packages.getPackageInfo(getPackageName(), 0);
            // An absent long-press target on an upgrade can be an intentional OnePlus choice.
            if (installed.firstInstallTime == installed.lastUpdateTime
                && packages.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
                && mailbox.selected(KeyPress.LONG) == null) {
                mailbox.select(KeyPress.LONG, "action:torch");
            }
            prefs.edit().putBoolean(LONG_PRESS_DEFAULT_SET, true).apply();
        } catch (PackageManager.NameNotFoundException | IOException error) {
            android.util.Log.w("PlusKey", "Could not initialize long press", error);
        }
    }
    private void showSettings() {
        showSettingsSheet(true);
    }

    private void showClickInterval() {
        showSettingsSheet(false);
    }

    private void showSettingsSheet(boolean includeConnection) {
        if (settingsBack.isEnabled()) return;
        previousScroll = pageScroll.getScrollY();
        LinearLayout body = Ui.column(this);
        body.setPadding(dp(20), dp(20), dp(20), dp(24));
        TextView title = Ui.text(this, body, includeConnection ? "Settings" : "Press timing", 30, false);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout timing = Ui.card(this, body);
        Ui.text(this, timing, includeConnection ? "Press timing" : "Maximum gap between presses", 19, false)
            .setTypeface(null, Typeface.BOLD);
        if (includeConnection) Ui.text(this, timing, "Maximum gap between presses", 15, true);
        int interval = mailbox.clickInterval();
        TextView value = Ui.text(this, timing, interval + " ms", 36, false);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        value.setTextColor(Ui.color(this, androidx.appcompat.R.attr.colorPrimary));
        Slider slider = new Slider(this);
        slider.setContentDescription("Maximum gap between presses");
        slider.setValueFrom(Mailbox.MIN_CLICK_INTERVAL_MS);
        slider.setValueTo(Mailbox.MAX_CLICK_INTERVAL_MS);
        slider.setStepSize(1);
        slider.setTickVisibilityMode(TickVisibilityMode.TICK_VISIBILITY_HIDDEN);
        slider.setValue(interval);
        slider.setLabelFormatter(ms -> Math.round(ms) + " ms");
        timing.addView(slider, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout limits = Ui.actions(this, timing);
        Ui.text(this, limits, Mailbox.MIN_CLICK_INTERVAL_MS + " ms", 13, true);
        Ui.text(this, limits, Mailbox.MAX_CLICK_INTERVAL_MS + " ms", 13, true).setGravity(Gravity.END);
        Ui.equal(this, limits);
        Ui.text(this, timing, "A longer gap gives you more time between taps and delays the action.", 14, true);
        MaterialButton save = Ui.button(this, timing, "Save timing", true, v -> {
            try {
                mailbox.setClickInterval(Math.round(slider.getValue()));
                v.setEnabled(false);
            } catch (IOException error) {
                toast("Could not save press timing");
            }
        });
        save.setEnabled(false);
        slider.addOnChangeListener(new Slider.OnChangeListener() {
            private long lastTickTime;

            @Override public void onValueChange(Slider view, float ms, boolean fromUser) {
                int millis = Math.round(ms);
                value.setText(millis + " ms");
                if (!fromUser) return;
                save.setEnabled(true);
                // The slider reports each changed 1 ms step. Coalesce only while the motor is busy.
                long now = SystemClock.uptimeMillis();
                if (now - lastTickTime < MIN_TICK_SPACING_MS) return;
                lastTickTime = now;
                view.performHapticFeedback(Build.VERSION.SDK_INT >= 34
                    ? HapticFeedbackConstants.SEGMENT_TICK
                    : HapticFeedbackConstants.CLOCK_TICK);
            }
        });
        if (includeConnection) {
            LinearLayout connection = Ui.card(this, body);
            Ui.text(this, connection, "Button connection", 19, false);
            settingsRow(connection, "Set up button", android.R.drawable.ic_menu_manage, setup::show);
            settingsRow(connection, "Connection settings", android.R.drawable.ic_menu_preferences, setup::advanced);

            LinearLayout updatesCard = Ui.card(this, body);
            Ui.text(this, updatesCard, "App updates", 19, false);
            settingsRow(updatesCard, "Check for updates", android.R.drawable.ic_popup_sync, this::checkUpdates);
            com.google.android.material.materialswitch.MaterialSwitch automatic =
                new com.google.android.material.materialswitch.MaterialSwitch(this);
            automatic.setText("Automatically check for updates");
            automatic.setMinHeight(dp(52));
            automatic.setChecked(automaticUpdates());
            automatic.setOnCheckedChangeListener((button, checked) -> updates.setAutomatic(checked));
            updatesCard.addView(automatic);
        }
        settingsBack.setEnabled(true);
        updateNavigation(true);
        showPage(body);
        pageScroll.scrollTo(0, 0);
        androidx.core.view.ViewCompat.setAccessibilityPaneTitle(body, title.getText());
    }

    private void updateNavigation(boolean settingsOpen) {
        settingsButton.setPadding(dp(11), dp(11), dp(11), dp(11));
        settingsButton.setImageResource(settingsOpen ? R.drawable.ic_back : R.drawable.ic_settings);
        settingsButton.setContentDescription(settingsOpen ? "Back" : "Settings");
        destinations.setVisibility(settingsOpen ? View.GONE : View.VISIBLE);
    }

    private void returnToMain() {
        settingsBack.setEnabled(false);
        updateNavigation(false);
        showPage(activeDestination == 0 ? buttonPage : edges.page);
        restoreScroll(previousScroll);
    }

    private void restoreScroll(int y) {
        pageScroll.post(() -> pageScroll.scrollTo(0, y));
    }

    private void showPage(View next) {
        if (pageHost.getChildAt(0) == next) return;
        pageHost.removeAllViews();
        pageHost.addView(next);
    }

    private void settingsRow(LinearLayout group, String label, int icon, Runnable open) {
        MaterialButton row = new MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        row.setText(label);
        row.setTextSize(16);
        row.setAllCaps(false);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setIconResource(icon);
        row.setIconPadding(dp(16));
        row.setMinHeight(dp(52));
        row.setOnClickListener(v -> open.run());
        group.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addPressRow(LinearLayout group, int gesture, String title, String unsetLabel) {
        LinearLayout row = Ui.actions(this, group);
        ImageView icon = new ImageView(this);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        pressIcons.put(gesture, icon);
        LinearLayout labels = Ui.mapping(this, row, icon, title,
            () -> choose(gesture, title, unsetLabel));
        TextView assignment = Ui.text(this, labels, "", 14, true);
        assignment.setPadding(0, 0, 0, 0);
        assignment.setMaxLines(2);
        assignment.setEllipsize(TextUtils.TruncateAt.END);
        pressNames.put(gesture, assignment);
        MaterialButton preview = new MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        preview.setText("Preview");
        preview.setAllCaps(false);
        preview.setTextSize(13);
        preview.setMinHeight(dp(48));
        preview.setContentDescription("Preview " + title);
        preview.setOnClickListener(v -> preview(gesture));
        row.addView(preview, new LinearLayout.LayoutParams(-2, -2));
        pressPreviews.put(gesture, preview);
    }

    private static String patternName(int gesture) {
        StringJoiner presses = new StringJoiner(" · ");
        for (char press : GesturePatterns.text(gesture).toCharArray()) presses.add(press == '-' ? "Hold" : "Tap");
        return presses.toString();
    }

    private void confirmRemove(int gesture) {
            new MaterialAlertDialogBuilder(this).setTitle("Remove " + patternName(gesture) + "?").setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    try { mailbox.removePattern(gesture); rebuildPatterns(); showSelections(); }
                    catch (IOException error) { toast(error.getMessage()); }
                }).show();
    }

    private void rebuildPatterns() {
        customRows.removeAllViews();
        pressNames.keySet().removeIf(id -> id > KeyPress.TRIPLE);
        pressIcons.keySet().removeIf(id -> id > KeyPress.TRIPLE);
        pressPreviews.keySet().removeIf(id -> id > KeyPress.TRIPLE);
        for (int id : mailbox.patterns()) {
            Ui.divider(this, customRows);
            addPressRow(customRows, id, patternName(id), "Off");
        }
    }

    private void recordPattern() {
        if (!helperReady) { toast("Start Plus Key before recording a pattern"); return; }
        File dir = getExternalFilesDir(null);
        if (dir == null) { toast("Pattern storage unavailable"); return; }
        PatternCapture capture = new PatternCapture(dir);
        final String token;
        try { token = capture.start(); } catch (IOException error) { toast(error.getMessage()); return; }
        LinearLayout body = Ui.column(this);
        body.setPadding(dp(24), dp(6), dp(24), 0);
        Ui.text(this, body,"Press Plus Key up to 8 times. Tap for a dot, hold for a dash. Actions are paused while recording.",16,true);
        TextView sequence = Ui.text(this, body,"Waiting for Plus Key…",24,false);
        sequence.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("Record pattern")
            .setView(body).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        captureDialog = dialog;
        Runnable update = () -> {
            if (!dialog.isShowing()) return;
            try {
                String value = capture.read(token);
                boolean valid = value.matches("[.-]{1,8}");
                sequence.setText(valid ? patternName(GesturePatterns.id(value)) : value.isEmpty() ? "Waiting for Plus Key…" : "Too many presses. Cancel and try again.");
                dialog.getButton(-1).setEnabled(valid);
            } catch (IOException error) { dialog.getButton(-1).setEnabled(false); }
        };
        FileObserver observer = new FileObserver(dir, FileObserver.CLOSE_WRITE) {
            public void onEvent(int event,String path) { if (PatternCapture.filename(token).equals(path)) handler.post(update); }
        };
        Runnable expire = dialog::dismiss;
        dialog.setOnDismissListener(ignored -> { capture.stop(token); observer.stopWatching(); handler.removeCallbacks(expire); captureDialog = null; });
        observer.startWatching(); dialog.show(); update.run(); handler.postDelayed(expire, PatternCapture.TIMEOUT);
        dialog.getButton(-1).setOnClickListener(view -> {
            try {
                String value = capture.read(token);
                if (!value.matches("[.-]{1,8}")) return;
                int id = GesturePatterns.id(value);
                mailbox.addPattern(id); dialog.dismiss(); rebuildPatterns(); showSelections(); choose(id,patternName(id),"Off");
            } catch (IOException | IllegalArgumentException error) { toast(error.getMessage()); }
        });
    }

    private void choose(int gesture, String title, String unsetLabel) {
        try {
            String current = mailbox.selected(gesture);
            Map<String, Runnable> options = new LinkedHashMap<>();
            if (GesturePatterns.text(gesture).length() > 1) options.put("Press timing", this::showClickInterval);
            if (gesture > KeyPress.TRIPLE) options.put("Remove pattern", () -> confirmRemove(gesture));
            ChainEditor.show(this, title, current, unsetLabel,
                target -> saveChoice(gesture, new Targets.Choice(Targets.label(this, target), target)),
                "Apps & special actions", close -> chooseAlternative(gesture, title, current, close), options);
        } catch (IOException error) { toast("Could not read selection"); }
    }

    private void chooseAlternative(int gesture, String title, String current, Runnable closeEditor) {
        boolean canHold = GesturePatterns.text(gesture).endsWith("-");
        new ChoicePicker(this, choice -> {
            if (choice.target.startsWith("configure:")) {
                ComponentName component = ComponentName.unflattenFromString(choice.target.substring("configure:".length()));
                configuringGesture = gesture;
                configuringPackage = component.getPackageName();
                try {
                    shortcutSetup.launch(new Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(component));
                    closeEditor.run();
                } catch (ActivityNotFoundException | SecurityException error) {
                    configuringPackage = null;
                    toast("Shortcut setup unavailable");
                }
            } else if (canHold && ChoicePicker.canHold(this, choice.target)) {
                String normal = choice.target.startsWith("tile:") ? "Click once" : "Toggle";
                new MaterialAlertDialogBuilder(this).setTitle(choice.label)
                    .setMessage("While held starts when the final hold is recognized and stops when you release Plus Key.")
                    .setNeutralButton("Cancel", null)
                    .setNegativeButton(normal, (dialog, which) -> {
                        closeEditor.run();
                        saveChoice(gesture, choice);
                    })
                    .setPositiveButton("While held", (dialog, which) -> {
                        closeEditor.run();
                        saveChoice(gesture, new Targets.Choice(choice.label + " · While held", "hold:" + choice.target));
                    }).show();
            } else {
                closeEditor.run();
                saveChoice(gesture, choice);
            }
        }, ChoicePicker.Mode.BUTTON, canHold).show(title, current != null && current.startsWith("hold:") ? current.substring(5) : current);
    }
    private void saveChoice(int gesture, Targets.Choice choice) {
        try {
            mailbox.select(gesture, choice.target);
            if (choice.target != null) Targets.remember(this, choice);
            showSelections();
            requestRecordingFor(choice.target);
        } catch (IOException | IllegalArgumentException error) { toast(error.getMessage()); }
    }
    void requestRecordingFor(String target) {
        if (target == null) return;
        String[] steps = ActionChain.parse(target);
        if ("action:record".equals(target) || "hold:action:record".equals(target)
                || target.startsWith("media:") && target.contains("action:record")
                || steps != null && java.util.Arrays.asList(steps).contains("action:record")) {
            ArrayList<String> missing = new ArrayList<>();
            for (String permission : RecordingActivity.requiredPermissions())
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
            if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), 5);
        }
    }
    private String systemAction(){try{return Settings.System.getString(getContentResolver(),Helper.ONEPLUS_ACTION_SETTING);}catch(Exception error){return null;}}
    private void showSelections(){
        try {
            for (int gesture : pressNames.keySet()) {
                String target = mailbox.selected(gesture);
                showSelection(gesture, target);
                MaterialButton preview = pressPreviews.get(gesture);
                if (gesture == KeyPress.LONG) {
                    String nativeAction = systemAction();
                    boolean blocked = target != null && !"nothing".equals(nativeAction);
                    boolean showSetup = blocked && nativeAction != null && !nativeAction.isEmpty();
                    boolean openSettings = target == null || (blocked && !showSetup);
                    preview.setText(openSettings ? "OnePlus settings" : "Preview");
                    preview.setContentDescription(openSettings ? "Open OnePlus settings" : "Preview Long press");
                    preview.setEnabled(!showSetup);
                    preview.setOnClickListener(v -> {if (openSettings) onePlusSettings(); else preview(KeyPress.LONG);});
                    longPressSetupCard.setVisibility(showSetup ? View.VISIBLE : View.GONE);
                } else {
                    preview.setEnabled(target != null);
                }
            }
        } catch (IOException error) {toast("Choose an action again");}
    }
    private void showSelection(int gesture, String target){
        TextView name = pressNames.get(gesture);
        ImageView icon = pressIcons.get(gesture);
        name.setText(Targets.summary(this, target, gesture == KeyPress.LONG ? "Managed by OnePlus" : "Off"));
        icon.setImageTintList(null);
        String action = target != null && target.startsWith("hold:") ? target.substring(5) : target;
        if (Options.fanApps(action) != null) {
            icon.setImageResource(R.drawable.ic_app_fan);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, androidx.appcompat.R.attr.colorPrimary)));
            return;
        }
        if (Options.isTile(action)) {
            try {
                ComponentName component = ComponentName.unflattenFromString(action.substring(5));
                ServiceInfo service = getPackageManager().getServiceInfo(component, 0);
                icon.setImageDrawable(service.loadIcon(getPackageManager()).mutate());
                if (service.icon != 0) icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    Ui.color(this, androidx.appcompat.R.attr.colorPrimary)));
                return;
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        try{
            Intent[] intents=target==null?new Intent[0]:Targets.intents(target);
            if(intents.length>0){icon.setImageDrawable(getPackageManager().getActivityIcon(intents[intents.length-1]));return;}
        }catch(Exception ignored){}
        icon.setImageResource(ChoicePicker.actionIcon(target));
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this,androidx.appcompat.R.attr.colorPrimary)));
    }
    private void preview(int gesture){
        ImageView icon = pressIcons.get(gesture);
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            icon.setScaleX(.8f);
            icon.setScaleY(.8f);
            icon.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
        }
        try{String target=mailbox.selected(gesture);if(target==null)return;
            if(target.startsWith("hold:")){toast("Hold the physical Plus Key to test this action");return;}
            if(target.startsWith("tile:") || target.startsWith("action:") || Options.mediaBranches(target) != null || ActionChain.parse(target) != null){String current=mailbox.readStatus(System.currentTimeMillis());if(current!=null&&current.startsWith("Listening|")){mailbox.requestPreview(gesture);return;}toast("Start your button first to preview this action");return;}
            startActivities(Targets.intents(target));
        }catch(Exception error){toast("Action unavailable. Choose another.");}
    }
    void startHelper(){
        wireless.prefs().edit().putBoolean("user_stopped",false).apply();
        if (ShizukuConnection.authorized()) {
            if (!wireless.busy.get()) shizukuAttempt = true;
            wireless.start();refresh(true);return;
        }
        if(!wireless.paired()||!setup.localAllowed()){setup.show();return;}
        if(!setup.wifiDebugging()&&!wireless.automatic()){setup.show();return;}
        wireless.start();refresh(true);
    }
    void authorizeShizuku() {
        try {
            if (ShizukuConnection.authorized()) startHelper();
            else if (!ShizukuConnection.running()) toast("Start Shizuku first");
            else if (Shizuku.shouldShowRequestPermissionRationale()) toast("Allow Plus Key in Shizuku’s authorized apps");
            else Shizuku.requestPermission(ShizukuConnection.PERMISSION_REQUEST);
        } catch (RuntimeException disconnected) {
            toast("Shizuku disconnected. Start it and try again.");
        }
    }
    private void watchStatus() {
        File dir = getExternalFilesDir(null);
        if (dir == null) return;
        statusObserver = new FileObserver(dir, FileObserver.MOVED_TO) {
            @Override public void onEvent(int event, String path) {
                if (!Mailbox.STATUS_FILE.equals(path)) return;
                handler.removeCallbacks(statusChanged);
                handler.post(statusChanged);
            }
        };
        try {
            statusObserver.startWatching();
        } catch (RuntimeException unavailable) {
            android.util.Log.w("PlusKey", "Status callbacks unavailable", unavailable);
        }
    }
    private void refresh(boolean checkLiveness) {
        if (!resumed) return;
        int generation = resumeGeneration;
        worker.execute(() -> {
            String value = null;
            boolean matchingVersion = false;
            try {
                matchingVersion = Helper.VERSION.equals(mailbox.runningVersion());
                long now = System.currentTimeMillis();
                value = checkLiveness && matchingVersion && !wireless.prefs().getBoolean("user_stopped", false)
                    ? mailbox.refreshStatus(now) : mailbox.readStatus(now);
            } catch (IOException ignored) { }
            final String current = value;
            final boolean versionOkay = matchingVersion;
            runOnUiThread(() -> {
                if (!resumed || resumeGeneration != generation || isDestroyed()) return;
                renderStatus(current, versionOkay);
                if (checkLiveness) setup.refresh();
            });
        });
    }
    private void renderStatus(String current, boolean versionOkay) {
        boolean busy = wireless.busy.get();
        boolean ready = current != null && current.startsWith("Listening|") && versionOkay;
        helperReady = ready;
        boolean paired = wireless.paired();
        boolean configured = paired || ShizukuConnection.authorized();
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        start.setVisibility(!ready && !busy && configured ? View.VISIBLE : View.GONE);
        stop.setVisibility(ready && !busy ? View.VISIBLE : View.GONE);
        finishSetup.setVisibility(!ready && !busy ? View.VISIBLE : View.GONE);
        setTextIfChanged(finishSetup, configured ? "Fix setup" : "Set up button");
        if (busy) {
            setStatus("Starting…", STATUS_AMBER);
            setTextIfChanged(detail, wireless.message);
        } else if (ready) {
            String[] fields = current.split("\\|", 3);
            boolean failed = fields.length == 3 && (fields[2].startsWith("Launch failed") || fields[2].startsWith("Long press needs"));
            setStatus(failed ? "Action needs attention" : "Active", failed ? STATUS_RED : STATUS_GREEN);
            setTextIfChanged(detail, failed ? fields[2] : wireless.automatic() && !ShizukuConnection.authorized() ? "Automatic startup on" : "Your actions are ready.");
        } else if (wireless.prefs().getBoolean("user_stopped", false)) {
            setStatus("Stopped", STATUS_GRAY);
            setTextIfChanged(detail, "Tap Start when you’re ready.");
        } else if (current != null && current.startsWith("Error|")) {
            setStatus("Needs attention", STATUS_RED);
            setTextIfChanged(detail, current.split("\\|", 3)[2]);
        } else {
            setStatus(configured ? "Not running" : "Setup needed", configured ? STATUS_GRAY : STATUS_AMBER);
            String message = wireless.message;
            if (!message.startsWith("Setup needed:")) {
                message = configured ? "Tap Start to reconnect your button." : "Connect the button to run these mappings.";
            }
            setTextIfChanged(detail, message);
        }
    }
    private void setStatus(String label, int color) {
        if (statusDot.getColor().getDefaultColor() != color) statusDot.setColor(color);
        setTextIfChanged(status, label);
    }

    private static void setTextIfChanged(TextView view, String value) {
        if (!TextUtils.equals(view.getText(), value)) view.setText(value);
    }
    void requestSetupPermissions(){ArrayList<String> missing=new ArrayList<>();if(!setup.localAllowed())missing.add("android.permission.ACCESS_LOCAL_NETWORK");if(!wireless.paired()&&!setup.notificationsAllowed())missing.add("android.permission.POST_NOTIFICATIONS");if(!missing.isEmpty())requestPermissions(missing.toArray(new String[0]),3);}
    void requestPairingPermissions(){ArrayList<String> missing=new ArrayList<>();if(!setup.localAllowed())missing.add("android.permission.ACCESS_LOCAL_NETWORK");if(!setup.notificationsAllowed())missing.add("android.permission.POST_NOTIFICATIONS");if(!missing.isEmpty()){resumePairingAfterPermission=true;requestPermissions(missing.toArray(new String[0]),3);}}
    public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);setup.refresh();if(resumePairingAfterPermission){resumePairingAfterPermission=false;if(setup.localAllowed()&&setup.notificationsAllowed())setup.pairWithCode();}}
    void checkUpdates(){updates.check(true);}
    boolean automaticUpdates(){return updates.automatic();}
    private void onePlusSettings(){try{startActivity(new Intent("com.oplus.gesture.StartActionButtonSettingsActivity").setPackage("com.oplus.gesture"));}catch(Exception error){
        Intent settings=new Intent(Settings.ACTION_SETTINGS).putExtra(":settings:fragment_args_key","snap_card_pack");
        Bundle args=new Bundle();args.putString(":settings:fragment_args_key","snap_card_pack");settings.putExtra(":settings:show_fragment_args",args);
        startActivity(settings);toast("Tap Snap Key to choose Do nothing");
    }}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private int dp(int value){return Ui.dp(this,value);}
    protected void onResume(){super.onResume();resumed=true;resumeGeneration++;watchStatus();handler.post(poll);if(updates!=null){updates.resumed();updates.check(false);}rebuildPatterns();showSelections();edges.refresh();
        boolean pendingAutomatic=wireless.prefs().getBoolean("automatic_requested",false)&&setup.wifiDebugging();
        if(!ShizukuConnection.authorized()&&(!autoAttempt||pendingAutomatic)&&wireless.canUseWireless()&&!wireless.prefs().getBoolean("user_stopped",false)){
            autoAttempt=true;try{String current=mailbox.readStatus(System.currentTimeMillis());if(current==null||!current.startsWith("Listening|")||!Helper.VERSION.equals(mailbox.runningVersion())||wireless.prefs().getBoolean("automatic_requested",false)||wireless.automatic())wireless.start();}catch(IOException ignored){}
        }
    }
    protected void onPause(){
        if (captureDialog != null) captureDialog.dismiss();
        resumed=false;
        if (statusObserver != null) {
            statusObserver.stopWatching();
            statusObserver = null;
        }
        handler.removeCallbacks(statusChanged);
        handler.removeCallbacks(poll);
        super.onPause();
    }
    protected void onDestroy(){Shizuku.removeRequestPermissionResultListener(shizukuPermission);worker.shutdownNow();if(updates!=null)updates.close();super.onDestroy();}
}
