package io.hiddemoens.pluskey;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import org.xmlpull.v1.XmlPullParser;
import android.os.Bundle;
import android.view.Surface;
import android.view.WindowInsets;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

/** Short-lived launcher shown over the current task. */
public final class AppFanActivity extends Activity {
    private FrameLayout stage;
    private boolean placed, closing;
    private KeyguardManager.KeyguardDismissCallback unlockRequest;
    private static final android.util.LruCache<String, Integer> ROUND_ICON_IDS = new android.util.LruCache<>(48);
    private float keyX, keyY;

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (java.util.Objects.equals(getIntent().getStringExtra("target"), intent.getStringExtra("target")) && !closing) {
            collapse();
            return;
        }
        setIntent(intent);
        showFan(intent);
    }

    @Override protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations() && unlockRequest == null) finish();
    }

    private void collapse() {
        if (closing) return;
        unlockRequest = null;
        closing = true;
        if (!placed || !ValueAnimator.areAnimatorsEnabled()) { finish(); return; }
        for (int i = 0; i < stage.getChildCount(); i++) {
            ImageButton button = (ImageButton) stage.getChildAt(i);
            button.setEnabled(false);
            button.animate().cancel();
            android.view.ViewPropertyAnimator motion = button.animate()
                .x(keyX - button.getWidth() / 2f).y(keyY - button.getHeight() / 2f)
                .scaleX(.25f).scaleY(.25f).alpha(0).setStartDelay(0).setDuration(170)
                .setInterpolator(new android.view.animation.AccelerateInterpolator());
            if (i == 0) motion.withEndAction(this::finish);
            motion.start();
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        stage = new FrameLayout(this);
        stage.setOnClickListener(v -> collapse());
        setContentView(stage);
        showFan(getIntent());
    }

    private void showFan(Intent intent) {
        String[] apps = Options.fanApps(intent.getStringExtra("target"));
        if (apps == null) { finish(); return; }
        for (int i = 0; i < stage.getChildCount(); i++) stage.getChildAt(i).animate().cancel();
        stage.removeAllViews();
        unlockRequest = null;
        boolean locked = getSystemService(KeyguardManager.class).isKeyguardLocked();
        if (locked) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        stage.setBackgroundColor(locked ? android.graphics.Color.TRANSPARENT : 0x66000000);
        placed = false;
        closing = false;
        int size = Ui.dp(this, 52);
        for (String app : apps) {
            ComponentName component = ComponentName.unflattenFromString(app);
            try {
                ActivityInfo info = getPackageManager().getActivityInfo(component, 0);
                if (!info.exported || !info.enabled || !info.applicationInfo.enabled) continue;
                ImageButton button = new ImageButton(this);
                button.setBackground(null);
                button.setPadding(0, 0, 0, 0);
                button.setOutlineProvider(ROUND_OUTLINE);
                button.setClipToOutline(true);
                button.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                button.setImageDrawable(launcherIcon(info));
                button.setContentDescription(info.loadLabel(getPackageManager()));
                button.setOnClickListener(v -> selectApp(component));
                button.setAlpha(0f);
                stage.addView(button, size, size);
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        if (stage.getChildCount() == 0) {
            Toast.makeText(this, "Selected apps are unavailable", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        stage.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                stage.getViewTreeObserver().removeOnPreDrawListener(this);
                if (!placed && hasWindowFocus()) placeIcons(size);
                return true;
            }
        });
    }

    private void selectApp(ComponentName component) {
        if (closing || unlockRequest != null) return;
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (!keyguard.isKeyguardLocked()) {
            launchApp(component);
            return;
        }
        unlockRequest = new KeyguardManager.KeyguardDismissCallback() {
            @Override public void onDismissSucceeded() {
                if (unlockRequest != this) return;
                unlockRequest = null;
                if (!isFinishing() && !isDestroyed() && !keyguard.isDeviceLocked()) launchApp(component);
            }

            @Override public void onDismissCancelled() { clearRequest(); }
            @Override public void onDismissError() { clearRequest(); }

            private void clearRequest() {
                if (unlockRequest == this) unlockRequest = null;
            }
        };
        keyguard.requestDismissKeyguard(this, unlockRequest);
    }

    private void launchApp(ComponentName component) {
        try {
            startActivity(Intent.makeMainActivity(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "App unavailable", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private static final ViewOutlineProvider ROUND_OUTLINE = new ViewOutlineProvider() {
        @Override public void getOutline(View view, Outline outline) {
            outline.setOval(0, 0, view.getWidth(), view.getHeight());
        }
    };

    private Drawable launcherIcon(ActivityInfo info) {
        // PackageManager can choose the regular icon when the OEM launcher uses a squircle.
        // Read the publisher's round variant, respecting activity and alias overrides.
        final String androidNs = "http://schemas.android.com/apk/res/android";
        PackageManager pm = getPackageManager();
        try {
            Resources resources = pm.getResourcesForApplication(info.applicationInfo);
            String cacheKey = info.applicationInfo.sourceDir + ":" + info.name;
            Integer cached = ROUND_ICON_IDS.get(cacheKey);
            if (cached != null) return cached == 0 ? info.loadIcon(pm) : resources.getDrawable(cached, null);
            int round = 0;
            try (XmlResourceParser xml = resources.getAssets().openXmlResourceParser("AndroidManifest.xml")) {
                while (xml.next() != XmlPullParser.END_DOCUMENT) {
                    if (xml.getEventType() != XmlPullParser.START_TAG) continue;
                    String tag = xml.getName();
                    if ("application".equals(tag)) {
                        round = xml.getAttributeResourceValue(androidNs, "roundIcon", 0);
                    } else if ("activity".equals(tag) || "activity-alias".equals(tag)) {
                        String name = xml.getAttributeValue(androidNs, "name");
                        if (name == null) continue;
                        if (name.startsWith(".")) name = info.packageName + name;
                        else if (name.indexOf('.') < 0) name = info.packageName + "." + name;
                        if (!info.name.equals(name)) continue;
                        if (xml.getAttributeResourceValue(androidNs, "icon", 0) != 0) round = 0;
                        round = xml.getAttributeResourceValue(androidNs, "roundIcon", round);
                        break;
                    }
                }
            }
            ROUND_ICON_IDS.put(cacheKey, round);
            if (round != 0) return resources.getDrawable(round, null);
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException |
                 java.io.IOException | org.xmlpull.v1.XmlPullParserException ignored) { }
        return info.loadIcon(pm);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && !placed && stage != null && stage.isLaidOut() && !stage.isLayoutRequested()) placeIcons(Ui.dp(this, 52));
    }

    private void placeIcons(int size) {
        if (closing) return;
        WindowInsets windowInsets = stage.getRootWindowInsets();
        Insets safe = windowInsets == null ? Insets.NONE : windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        float width = stage.getWidth() - safe.left - safe.right;
        float height = stage.getHeight() - safe.top - safe.bottom;
        int rotation = getDisplay().getRotation();
        boolean sideways = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
        float along = sideways ? width : height;
        float depth = sideways ? height : width;
        float half = size / 2f;
        int count = stage.getChildCount();
        double step = Math.toRadians(32);
        float desiredRadius = (float)((size + Ui.dp(this, 6)) / (2 * Math.sin(step / 2)));
        float radius = Math.max(0, Math.min(desiredRadius, Math.min(along / 2 - half, depth - size)));
        // OnePlus 15 Quick Guide front view: key center sits 19% down the active display.
        float fraction = android.os.Build.DEVICE.equalsIgnoreCase("OP611FL1") ? .19f : .35f;
        float anchor = (sideways ? stage.getWidth() : stage.getHeight()) * fraction;
        boolean reversed = rotation == Surface.ROTATION_180 || rotation == Surface.ROTATION_270;
        anchor -= sideways ? (reversed ? safe.right : safe.left) : (reversed ? safe.bottom : safe.top);
        double endAngle = step * (count - 1) / 2;
        float endDepth = radius * (float)Math.cos(endAngle);
        float extent = half + radius * (float)Math.sin(endAngle);
        anchor = Math.max(extent, Math.min(anchor, along - extent));
        boolean animate = !placed && ValueAnimator.areAnimatorsEnabled();
        placed = true;
        for (int i = 0; i < count; i++) {
            double angle = step * (i - (count - 1) / 2.0);
            float tangent = anchor + radius * (float)Math.sin(angle);
            float inward = half + Ui.dp(this, 6) + radius * (float)Math.cos(angle) - endDepth;
            float x = inward, y = tangent, originX = 0, originY = anchor;
            if (rotation == Surface.ROTATION_90) {
                x = tangent; y = height - inward; originX = anchor; originY = height;
            } else if (rotation == Surface.ROTATION_180) {
                x = width - inward; y = height - tangent; originX = width; originY = height - anchor;
            } else if (rotation == Surface.ROTATION_270) {
                x = width - tangent; y = inward; originX = width - anchor; originY = 0;
            }
            keyX = originX + safe.left;
            keyY = originY + safe.top;
            ImageButton button = (ImageButton) stage.getChildAt(i);
            button.animate().cancel();
            button.setX(safe.left + (animate ? originX : x) - half);
            button.setY(safe.top + (animate ? originY : y) - half);
            button.setRotation(animate && i == 0 ? -18f : 0f);
            button.setScaleX(animate ? .18f : 1);
            button.setScaleY(animate ? .18f : 1);
            button.setAlpha(animate ? 0f : 1f);
            if (animate) button.animate().x(safe.left + x - half).y(safe.top + y - half)
                .rotation(0).scaleX(1).scaleY(1).alpha(1).setStartDelay(i * 28L).setDuration(i == 0 ? 480 : 390)
                .setInterpolator(new OvershootInterpolator(i == 0 ? 2.6f : 1.1f)).start();
        }
    }
}
