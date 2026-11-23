package io.hiddemoens.pluskey;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.Toast;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EdgeService extends AccessibilityService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService actions = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final View[] handles = new View[2];
    private final SharedPreferences.OnSharedPreferenceChangeListener onChange = (prefs, key) -> {
        if (key == null || key.endsWith(".enabled") || key.endsWith(".width")
                || key.endsWith(".height") || key.endsWith(".position")) rebuildHandles();
    };
    private SharedPreferences prefs;
    private WindowManager windows;
    private Mailbox mailbox;
    private ImageView swipeArrow;
    private WindowManager.LayoutParams arrowLayout;
    private volatile boolean connected;

    @Override protected void onServiceConnected() {
        windows = getSystemService(WindowManager.class);
        prefs = getSharedPreferences(EdgeSettings.PREFS, 0);
        mailbox = new Mailbox(getExternalFilesDir(null));
        prefs.registerOnSharedPreferenceChangeListener(onChange);
        connected = true;
        rebuildHandles();
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (connected) rebuildHandles();
    }

    @Override public boolean onUnbind(Intent intent) {
        disconnect();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        disconnect();
        actions.shutdownNow();
        super.onDestroy();
    }

    private void disconnect() {
        connected = false;
        hideSwipeArrow();
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(onChange);
        for (int i = 0; i < handles.length; i++) removeHandle(i);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { hideSwipeArrow(); }

    private void removeHandle(int index) {
        if (handles[index] == null) return;
        windows.removeViewImmediate(handles[index]);
        handles[index] = null;
    }

    private void rebuildHandles() {
        hideSwipeArrow();
        WindowMetrics metrics = windows.getCurrentWindowMetrics();
        Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.mandatorySystemGestures());
        int room = Math.max(1, metrics.getBounds().height() - insets.top - insets.bottom);
        for (int i = 0; i < handles.length; i++) {
            boolean left = i == 0;
            String side = left ? "left" : "right";
            if (!prefs.getBoolean(side + ".enabled", false)) {
                removeHandle(i);
                continue;
            }
            int width = Ui.dp(this, prefs.getInt(side + ".width", EdgeSettings.WIDTH));
            int height = Math.min(room, Ui.dp(this, prefs.getInt(side + ".height", EdgeSettings.HEIGHT)));
            int position = prefs.getInt(side + ".position", EdgeSettings.POSITION);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | (left ? Gravity.LEFT : Gravity.RIGHT);
            params.x = left ? insets.left : insets.right;
            params.y = insets.top + Math.round((room - height) * position / 100f);
            params.setTitle(left ? "PlusKey left handle" : "PlusKey right handle");
            View handle = handles[i];
            if (handle == null) {
                handle = new View(this);
                handle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                handle.setOnTouchListener(new SwipeListener(left, side));
                windows.addView(handle, params);
                handles[i] = handle;
            } else {
                windows.updateViewLayout(handle, params);
            }
            handle.setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, width, height)));
        }
    }

    private boolean showSwipeArrow(boolean left, float fingerX, float fingerY, Rect display) {
        int size = Ui.dp(this, 36);
        int gap = Ui.dp(this, 10);
        int x = Math.round(fingerX) + (left ? -gap - size : gap) - display.left;
        int y = Math.round(fingerY) - size / 2 - display.top;
        x = Math.max(0, Math.min(display.width() - size, x));
        y = Math.max(0, Math.min(display.height() - size, y));
        if (swipeArrow != null) {
            if (arrowLayout.x != x || arrowLayout.y != y) {
                arrowLayout.x = x;
                arrowLayout.y = y;
                windows.updateViewLayout(swipeArrow, arrowLayout);
            }
            return true;
        }

        ImageView arrow = new ImageView(this);
        arrow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        arrow.setImageResource(R.drawable.ic_back);
        arrow.setScaleX(left ? -1 : 1);
        int padding = Ui.dp(this, 8);
        arrow.setPadding(padding, padding, padding, padding);
        arrow.setColorFilter(0xffff902f);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(0xe6292726);
        arrow.setBackground(background);
        WindowManager.LayoutParams layout = new WindowManager.LayoutParams(size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        layout.gravity = Gravity.TOP | Gravity.LEFT;
        layout.setFitInsetsTypes(0);
        layout.x = x;
        layout.y = y;
        layout.setTitle("PlusKey swipe arrow");
        try {
            windows.addView(arrow, layout);
        } catch (WindowManager.BadTokenException unavailable) {
            return false;
        }
        swipeArrow = arrow;
        arrowLayout = layout;
        return true;
    }

    private void hideSwipeArrow() {
        if (swipeArrow == null) return;
        windows.removeViewImmediate(swipeArrow);
        swipeArrow = null;
        arrowLayout = null;
    }

    private final class SwipeListener implements View.OnTouchListener {
        private final boolean left;
        private final String side;
        private float startX, startY;
        private EdgeSwipe.Stroke stroke;
        private Rect display;
        private boolean arrowUnavailable;

        SwipeListener(boolean left, String side) {
            this.left = left;
            this.side = side;
        }

        @Override public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!unlocked()) return false;
                    stroke = new EdgeSwipe.Stroke(left, Ui.dp(EdgeService.this, 24));
                    display = windows.getCurrentWindowMetrics().getBounds();
                    arrowUnavailable = false;
                    startX = event.getX();
                    startY = event.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (stroke != null) {
                        for (int i = 0; i < event.getHistorySize(); i++) {
                            stroke.move(event.getHistoricalX(i) - startX, event.getHistoricalY(i) - startY);
                        }
                        float dx = event.getX() - startX;
                        stroke.move(dx, event.getY() - startY);
                        if (!arrowUnavailable && (swipeArrow != null || (left ? dx : -dx) >= Ui.dp(EdgeService.this, 8))) {
                            arrowUnavailable = !showSwipeArrow(left, event.getRawX(), event.getRawY(), display);
                        }
                    }
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_CANCEL:
                    stroke = null;
                    hideSwipeArrow();
                    break;
                case MotionEvent.ACTION_UP:
                    hideSwipeArrow();
                    if (stroke != null) {
                        int direction = stroke.finish(event.getX() - startX, event.getY() - startY);
                        if (direction >= 0) trigger(prefs.getString(side + "." + EdgeSwipe.KEYS[direction],
                            direction == 0 ? "action:back" : ""));
                    }
                    stroke = null;
                    break;
            }
            return true;
        }
    }

    private boolean unlocked() {
        return !getSystemService(KeyguardManager.class).isKeyguardLocked()
            && getSystemService(PowerManager.class).isInteractive();
    }

    private void trigger(String target) {
        if (!connected || !unlocked() || target.isEmpty()) return;
        if (!busy.compareAndSet(false, true)) {
            message("An edge action is already running");
            return;
        }
        actions.execute(() -> {
            try {
                if (Options.validComponent(target)) {
                    Intent[] intents = Targets.intents(target);
                    if (intents.length != 1) throw new IllegalStateException("App unavailable");
                    intents[0].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intents[0]);
                    return;
                }
                String[] steps = ActionChain.parse(target);
                if (steps == null) {
                    if (!Options.builtIn(target)) throw new IllegalStateException("Choose the action again");
                    steps = new String[]{target};
                }
                boolean needsHelper = false;
                for (String step : steps) needsHelper |= !ActionChain.directNavigation(step);
                ActionChain.run(steps, !needsHelper || helperAvailable(), this::runStep);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                message(error.getMessage() == null ? "Edge action unavailable" : error.getMessage());
            } finally {
                busy.set(false);
            }
        });
    }

    private boolean helperAvailable() throws IOException, InterruptedException {
        if (getExternalFilesDir(null) == null || !Helper.VERSION.equals(mailbox.runningVersion())) return false;
        long requestTime = System.currentTimeMillis();
        mailbox.requestStatus();
        long deadline = SystemClock.elapsedRealtime() + 1500;
        do {
            String status = mailbox.readStatusAfter(requestTime);
            if (status != null) return status.startsWith("Listening|");
            Thread.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        return false;
    }

    private void runStep(String step) throws Exception {
        if (!connected || !unlocked()) throw new IllegalStateException("Unlock your phone first");
        int navigation;
        switch (step) {
            case "action:back": navigation = GLOBAL_ACTION_BACK; break;
            case "action:home": navigation = GLOBAL_ACTION_HOME; break;
            case "action:recents": navigation = GLOBAL_ACTION_RECENTS; break;
            default: runHelperStep(step); return;
        }
        if (!performGlobalAction(navigation)) throw new IllegalStateException("Navigation action unavailable");
    }

    private void runHelperStep(String step) throws Exception {
        String token = UUID.randomUUID().toString();
        long deadline = SystemClock.elapsedRealtime() + Mailbox.ACTION_TIMEOUT_MS;
        mailbox.requestAction(token, deadline, step);
        do {
            String reply = mailbox.readActionReply(token);
            if (reply != null) {
                if (!reply.isEmpty()) throw new IllegalStateException(reply);
                return;
            }
            Thread.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new IllegalStateException("The Plus Key helper did not respond");
    }

    private void message(String text) {
        main.post(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
    }
}
