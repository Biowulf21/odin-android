package io.hiddemoens.pluskey;

import android.content.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.widget.NestedScrollView;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDragHandleView;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.slider.TickVisibilityMode;
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
    private final TextView[] pressNames = new TextView[KeyPress.TRIPLE + 1];
    private final ImageView[] pressIcons = new ImageView[KeyPress.TRIPLE + 1];
    private final MaterialButton[] pressPreviews = new MaterialButton[KeyPress.TRIPLE + 1];
    private View longPressSetupCard;
    private FrameLayout shelfPages;
    private NestedScrollView shelfScroll;
    private LinearLayout gesturesPage;
    private ImageButton shelfNav;
    private int previousScroll;
    private final androidx.activity.OnBackPressedCallback settingsBack =
        new androidx.activity.OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { returnToGestures(); }
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
    protected void onCreate(Bundle saved){
        super.onCreate(saved);
        File mailboxDir = getExternalFilesDir(null);
        updates=new Updates(this);mailbox=new Mailbox(mailboxDir);wireless=WirelessController.get(this);setup=new SetupSheet(this,wireless);
        Shizuku.addRequestPermissionResultListener(shizukuPermission);
        try{if(mailbox.selected(KeyPress.SHORT)==null && getPackageManager().getLaunchIntentForPackage("com.google.ar.lens")!=null)mailbox.select(KeyPress.SHORT, Helper.LENS);}catch(Exception ignored){}
        if (mailboxDir != null) initializeLongPressDefault();
        CoordinatorLayout page = new CoordinatorLayout(this);
        FrameLayout header = new FrameLayout(this);
        page.addView(header, new CoordinatorLayout.LayoutParams(-1, 1));
        FrameLayout photoLayer = new FrameLayout(this);
        header.addView(photoLayer, new FrameLayout.LayoutParams(-1, -1));
        ImageView handset = new ImageView(this);
        handset.setScaleType(ImageView.ScaleType.CENTER_CROP);
        handset.setImageResource(R.drawable.plus_key_header);
        photoLayer.addView(handset, new FrameLayout.LayoutParams(-1, -1));
        ImageView softPhoto = new ImageView(this);
        softPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        softPhoto.setImageResource(R.drawable.plus_key_header_soft);
        softPhoto.setAlpha(0f);
        softPhoto.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        photoLayer.addView(softPhoto, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout heading = Ui.column(this);
        heading.setPadding(dp(20), dp(12), dp(20), dp(16));
        GradientDrawable titleShade = new GradientDrawable();
        titleShade.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        titleShade.setGradientCenter(0f, 0f);
        titleShade.setGradientRadius(dp(240));
        titleShade.setColors(new int[] {0x86000000, 0x00000000});
        View scrim = new View(this);
        scrim.setBackground(titleShade);
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        header.addView(heading, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout top = Ui.actions(this, heading);
        TextView title=new TextView(this);title.setText("Plus Key");title.setTextSize(36);title.setTypeface(null,Typeface.BOLD);title.setTextColor(Ui.color(this,com.google.android.material.R.attr.colorOnSurface));top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        status = Ui.text(this, heading, "Checking…", 16, false);
        statusDot.setShape(GradientDrawable.OVAL);
        statusDot.setSize(dp(8), dp(8));
        statusDot.setColor(STATUS_AMBER);
        status.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        status.setCompoundDrawablePadding(dp(8));
        status.setCompoundDrawablesRelativeWithIntrinsicBounds(statusDot, null, null, null);
        LinearLayout root = Ui.column(this);
        root.setPadding(dp(16), 0, dp(16), dp(28));
        root.setClipChildren(false);
        root.setClipToPadding(false);

        LinearLayout first = gestureRow(root);
        addPressCard(first, KeyPress.SHORT, "Short press", null, "●");
        addPressCard(first, KeyPress.DOUBLE, "Double press", "Off", "● ●");
        Ui.equal(this, first);
        LinearLayout second = gestureRow(root);
        addPressCard(second, KeyPress.TRIPLE, "Triple press", "Off", "● ● ●");
        addPressCard(second, KeyPress.LONG, "Long press", "Managed by OnePlus", "");
        Ui.equal(this, second);
        LinearLayout longPressSetup = Ui.card(this, root);
        longPressSetupCard = (View) longPressSetup.getParent();
        longPressSetupCard.setVisibility(View.GONE);
        Ui.text(this, longPressSetup, "Turn off OnePlus action", 22, false);
        Ui.text(this, longPressSetup, LONG_PRESS_SETUP_HINT, 14, true);
        Ui.button(this, longPressSetup, "Open OnePlus settings", true, v -> onePlusSettings());
        LinearLayout connection=Ui.card(this,root);detail=Ui.text(this,connection,"",14,true);
        progress=new LinearProgressIndicator(this);progress.setIndeterminate(true);progress.setVisibility(View.GONE);connection.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));
        start=Ui.button(this,connection,"Start",true,v->startHelper());
        stop=Ui.button(this,connection,"Stop",false,v->{if(wireless.busy.get())return;try{wireless.prefs().edit().putBoolean("user_stopped",true).apply();mailbox.requestStop();setStatus("Stopping…", STATUS_AMBER);}catch(IOException error){toast("Could not stop. Try again.");}});
        finishSetup=Ui.button(this,root,"Finish setup",true,v->setup.show());
        attachGestureShelf(page, header, photoLayer, softPhoto, root, second);
        FrameLayout window = new FrameLayout(this);
        window.addView(page, new FrameLayout.LayoutParams(-1, -1));
        window.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        setContentView(window);
        showSelections();
    }

    private void attachGestureShelf(CoordinatorLayout page, View header, View photoLayer, ImageView softPhoto, LinearLayout content, View lastRow) {
        LinearLayout shelf = Ui.column(this);
        GradientDrawable surface = new GradientDrawable();
        float radius = dp(30);
        surface.setCornerRadii(new float[] {radius, radius, radius, radius, 0, 0, 0, 0});
        surface.setColor(Ui.color(this, com.google.android.material.R.attr.colorSurface));
        shelf.setBackground(surface);
        shelf.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + (int) radius, radius);
            }
        });
        shelf.setElevation(dp(3));
        BottomSheetDragHandleView handle = new BottomSheetDragHandleView(this);
        FrameLayout toolbar = new FrameLayout(this);
        toolbar.addView(handle, new FrameLayout.LayoutParams(dp(64), -1, Gravity.CENTER));
        shelfNav = Ui.navigationButton(this);
        updateShelfNavigation(false);
        shelfNav.setOnClickListener(v -> {
            if (settingsBack.isEnabled()) returnToGestures();
            else showSettings();
        });
        FrameLayout.LayoutParams nav = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END);
        nav.setMarginEnd(dp(12));
        toolbar.addView(shelfNav, nav);
        shelf.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(48)));
        shelfScroll = new NestedScrollView(this);
        shelfPages = new FrameLayout(this);
        gesturesPage = content;
        shelfPages.addView(content);
        shelfScroll.addView(shelfPages);
        shelf.addView(shelfScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        getOnBackPressedDispatcher().addCallback(this, settingsBack);
        BottomSheetBehavior<LinearLayout> behavior = new BottomSheetBehavior<>();
        addShelfMotion(shelf, behavior);
        behavior.setHideable(false);
        CoordinatorLayout.LayoutParams layout = new CoordinatorLayout.LayoutParams(-1, -1);
        layout.setBehavior(behavior);
        page.addView(shelf, layout);
        // Draw into the transparent margin so the shadow follows the rounded edge
        // during both a drag and its spring release.
        android.graphics.Paint shadow = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(Ui.color(this, com.google.android.material.R.attr.colorSurface));
        int shadowMargin = dp(84);
        shadow.setShadowLayer(dp(36), 0, -dp(8), 0x87000000);
        View castShadow = new View(this) {
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                float top = shadowMargin;
                canvas.drawRoundRect(dp(1), top, getWidth() - dp(1), getHeight() + radius,
                    radius, radius, shadow);
            }
        };
        castShadow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        castShadow.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        page.addView(castShadow, 1, new CoordinatorLayout.LayoutParams(-1, shadowMargin + dp(32)));
        shelf.getViewTreeObserver().addOnPreDrawListener(() -> {
            castShadow.setTranslationY(shelf.getY() - shadowMargin);
            int travel = shelf.getHeight() - behavior.getPeekHeight();
            if (travel > 0) {
                float distance = page.getHeight() - behavior.getPeekHeight() - shelf.getY();
                float fraction = Math.max(0f, Math.min(1f, distance / travel));
                float progress = fraction * fraction * (3f - 2f * fraction);
                softPhoto.setAlpha(progress);
                float scale = 1f + .06f * (1f - progress);
                photoLayer.setScaleX(scale);
                photoLayer.setScaleY(scale);
            }
            return true;
        });
        Runnable sizeShelf = () -> {
            int height = page.getHeight();
            if (height == 0 || lastRow.getHeight() == 0) return;
            int expandedHeight = height - Math.min(dp(114), height / 6);
            int restingHeight = Math.min(expandedHeight, toolbar.getHeight() + lastRow.getBottom() + dp(8));
            int photoHeight = height - restingHeight + dp(30);
            ViewGroup.LayoutParams photo = header.getLayoutParams();
            if (photo.height != photoHeight) {
                photo.height = photoHeight;
                header.setLayoutParams(photo);
            }
            if (layout.height != expandedHeight) {
                layout.height = expandedHeight;
                shelf.setLayoutParams(layout);
            }
            if (behavior.getPeekHeight() != restingHeight) behavior.setPeekHeight(restingHeight);
        };
        View.OnLayoutChangeListener resize = (v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            page.removeCallbacks(sizeShelf);
            page.post(sizeShelf);
        };
        page.addOnLayoutChangeListener(resize);
        lastRow.addOnLayoutChangeListener(resize);
    }

    private void addShelfMotion(View shelf, BottomSheetBehavior<LinearLayout> behavior) {
        SpringForce force = new SpringForce(0f)
            .setStiffness(getResources().getFloat(com.google.android.material.R.dimen.m3_sys_motion_expressive_spring_default_spatial_stiffness))
            .setDampingRatio(getResources().getFloat(com.google.android.material.R.dimen.m3_sys_motion_expressive_spring_default_spatial_damping));
        SpringAnimation recoil = new SpringAnimation(shelf, SpringAnimation.TRANSLATION_Y).setSpring(force);
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            private long lastMove;
            private int lastTop;
            private float velocity;

            @Override public void onSlide(View view, float offset) {
                if (behavior.getState() != BottomSheetBehavior.STATE_DRAGGING) return;
                long now = SystemClock.uptimeMillis();
                long elapsed = now - lastMove;
                if (elapsed > 0) {
                    velocity = (view.getTop() - lastTop) * 1000f / elapsed;
                    lastTop = view.getTop();
                    lastMove = now;
                }
            }

            @Override public void onStateChanged(View view, int state) {
                if (state == BottomSheetBehavior.STATE_DRAGGING) {
                    recoil.cancel();
                    view.setTranslationY(0f);
                    lastTop = view.getTop();
                    lastMove = SystemClock.uptimeMillis();
                    velocity = 0f;
                } else if (state == BottomSheetBehavior.STATE_SETTLING && lastMove != 0) {
                    if (android.animation.ValueAnimator.areAnimatorsEnabled() && SystemClock.uptimeMillis() - lastMove < 100) {
                        float limit = dp(480);
                        recoil.setStartVelocity(Math.max(-limit, Math.min(limit, velocity * .45f)));
                        recoil.start();
                    }
                    lastMove = 0;
                } else if (state != BottomSheetBehavior.STATE_SETTLING) {
                    lastMove = 0;
                }
            }
        });
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
        previousScroll = shelfScroll.getScrollY();
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
            settingsRow(connection, "Button setup", android.R.drawable.ic_menu_manage, () -> {
                setup.show();
            });
            settingsRow(connection, "Connection options", android.R.drawable.ic_menu_preferences, () -> {
                setup.advanced();
            });
        }
        settingsBack.setEnabled(true);
        updateShelfNavigation(true);
        switchShelfPage(body, true);
        shelfScroll.scrollTo(0, 0);
        androidx.core.view.ViewCompat.setAccessibilityPaneTitle(body, title.getText());
    }

    private void updateShelfNavigation(boolean settingsOpen) {
        int inset = dp(settingsOpen ? 12 : 8);
        shelfNav.setPadding(inset, inset, inset, inset);
        shelfNav.setImageResource(settingsOpen ? R.drawable.ic_back : R.drawable.ic_settings);
        shelfNav.setContentDescription(settingsOpen ? "Back to gestures" : "Settings");
    }

    private void returnToGestures() {
        settingsBack.setEnabled(false);
        updateShelfNavigation(false);
        switchShelfPage(gesturesPage, false);
        shelfScroll.post(() -> shelfScroll.scrollTo(0, previousScroll));
    }

    private void switchShelfPage(View next, boolean forward) {
        View previous = shelfPages.getChildAt(0);
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            android.transition.TransitionManager.endTransitions(shelfPages);
            com.google.android.material.transition.platform.MaterialSharedAxis motion =
                new com.google.android.material.transition.platform.MaterialSharedAxis(
                    com.google.android.material.transition.platform.MaterialSharedAxis.X, forward);
            motion.addTarget(previous);
            motion.addTarget(next);
            android.transition.TransitionManager.beginDelayedTransition(shelfPages, motion);
        }
        shelfPages.removeView(previous);
        shelfPages.addView(next);
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

    private LinearLayout gestureRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        row.setBaselineAligned(false);
        row.setClipChildren(false);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.topMargin = dp(14);
        parent.addView(row, size);
        return row;
    }

    private void addPressCard(LinearLayout row, int gesture, String title, String unsetLabel, String rhythm) {
        LinearLayout card = Ui.card(this, row);
        card.setPadding(dp(12), dp(14), dp(12), dp(10));
        MaterialCardView tile = (MaterialCardView) card.getParent();
        LinearLayout.LayoutParams tileSize = (LinearLayout.LayoutParams) tile.getLayoutParams();
        tileSize.topMargin = 0;
        tileSize.height = LinearLayout.LayoutParams.MATCH_PARENT;
        tile.setLayoutParams(tileSize);
        tile.setRadius(dp(18));
        tile.setCardElevation(dp(4));
        tile.setOutlineAmbientShadowColor(android.graphics.Color.BLACK);
        tile.setOutlineSpotShadowColor(android.graphics.Color.BLACK);
        tile.setStrokeColor(0x16FFFFFF);
        tile.setStrokeWidth(dp(1));
        tile.setFocusable(true);
        tile.setOnClickListener(v -> choose(gesture, title, unsetLabel));
        LinearLayout symbols = Ui.actions(this, card);
        TextView pattern = new TextView(this);
        if (gesture == KeyPress.LONG) {
            pattern.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_hold, 0, 0, 0);
            pattern.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(
                Ui.color(this, androidx.appcompat.R.attr.colorPrimary)));
        } else {
            pattern.setText(rhythm);
        }
        pattern.setTextColor(Ui.color(this, androidx.appcompat.R.attr.colorPrimary));
        pattern.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        symbols.addView(pattern, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView icon = new ImageView(this);
        symbols.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        pressIcons[gesture] = icon;
        TextView gestureTitle = Ui.text(this, card, title, 21, false);
        gestureTitle.setTypeface(null, Typeface.BOLD);
        gestureTitle.setPadding(0, dp(12), 0, 0);
        pressNames[gesture] = Ui.text(this, card, "", 17, true);
        pressPreviews[gesture] = Ui.button(this, card, "Preview", false, v -> preview(gesture));
    }

    private void choose(int gesture, String title, String unsetLabel) {
        try {
            String current = mailbox.selected(gesture);
            Runnable timing = gesture == KeyPress.DOUBLE || gesture == KeyPress.TRIPLE ? this::showClickInterval : null;
            new ChoicePicker(this, choice -> {
                try {
                    mailbox.select(gesture, choice.target);
                    if (choice.target != null) Targets.remember(this, choice);
                    showSelections();
                } catch (IOException error) { toast("Could not save selection"); }
            }).show(title, unsetLabel, current, timing);
        } catch (IOException error) { toast("Could not read selection"); }
    }
    private String systemAction(){try{return Settings.System.getString(getContentResolver(),Helper.ONEPLUS_ACTION_SETTING);}catch(Exception error){return null;}}
    private void showSelections(){
        try {
            for (int gesture = KeyPress.SHORT; gesture <= KeyPress.TRIPLE; gesture++) {
                String target = mailbox.selected(gesture);
                showSelection(gesture, target);
                MaterialButton preview = pressPreviews[gesture];
                if (gesture == KeyPress.LONG) {
                    String nativeAction = systemAction();
                    boolean blocked = target != null && !"nothing".equals(nativeAction);
                    boolean showSetup = blocked && nativeAction != null && !nativeAction.isEmpty();
                    boolean openSettings = target == null || (blocked && !showSetup);
                    preview.setText(openSettings ? "Plus Key settings" : "Preview");
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
        TextView name = pressNames[gesture];
        ImageView icon = pressIcons[gesture];
        name.setText(target == null && gesture != KeyPress.LONG ? "Off" : Targets.label(this, target));
        icon.setImageTintList(null);
        try{
            Intent[] intents=target==null?new Intent[0]:Targets.intents(target);
            if(intents.length>0){icon.setImageDrawable(getPackageManager().getActivityIcon(intents[intents.length-1]));return;}
        }catch(Exception ignored){}
        icon.setImageResource(ChoicePicker.actionIcon(target));
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this,androidx.appcompat.R.attr.colorPrimary)));
    }
    private void preview(int gesture){
        ImageView icon = pressIcons[gesture];
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            icon.setScaleX(.8f);
            icon.setScaleY(.8f);
            icon.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
        }
        try{String target=mailbox.selected(gesture);if(target==null)return;
            if(target.startsWith("action:")){String current=mailbox.readStatus(System.currentTimeMillis());if(current!=null&&current.startsWith("Listening|")){mailbox.requestPreview(gesture);return;}toast("Start your button first to preview this action");return;}
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
        boolean paired = wireless.paired();
        boolean configured = paired || ShizukuConnection.authorized();
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        start.setVisibility(!ready && !busy && configured ? View.VISIBLE : View.GONE);
        stop.setVisibility(ready && !busy ? View.VISIBLE : View.GONE);
        finishSetup.setVisibility(!ready && !busy ? View.VISIBLE : View.GONE);
        setTextIfChanged(finishSetup, configured ? "Fix setup" : "Finish setup");
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
                message = configured ? "Tap Start to reconnect your button." : "Finish setup to use your button.";
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
    void toggleUpdateChecks(){updates.toggleAutomatic();}
    private void onePlusSettings(){try{startActivity(new Intent("com.oplus.gesture.StartActionButtonSettingsActivity").setPackage("com.oplus.gesture"));}catch(Exception error){
        Intent settings=new Intent(Settings.ACTION_SETTINGS).putExtra(":settings:fragment_args_key","snap_card_pack");
        Bundle args=new Bundle();args.putString(":settings:fragment_args_key","snap_card_pack");settings.putExtra(":settings:show_fragment_args",args);
        startActivity(settings);toast("Tap Snap Key to choose Do nothing");
    }}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private int dp(int value){return Ui.dp(this,value);}
    protected void onResume(){super.onResume();resumed=true;resumeGeneration++;watchStatus();handler.post(poll);if(updates!=null){updates.resumed();updates.check(false);}showSelections();
        boolean pendingAutomatic=wireless.prefs().getBoolean("automatic_requested",false)&&setup.wifiDebugging();
        if(!ShizukuConnection.authorized()&&(!autoAttempt||pendingAutomatic)&&wireless.canUseWireless()&&!wireless.prefs().getBoolean("user_stopped",false)){
            autoAttempt=true;try{String current=mailbox.readStatus(System.currentTimeMillis());if(current==null||!current.startsWith("Listening|")||!Helper.VERSION.equals(mailbox.runningVersion())||wireless.prefs().getBoolean("automatic_requested",false)||wireless.automatic())wireless.start();}catch(IOException ignored){}
        }
    }
    protected void onPause(){
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
