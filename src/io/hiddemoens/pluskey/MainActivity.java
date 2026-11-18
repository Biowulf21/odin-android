package io.hiddemoens.pluskey;

import android.content.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
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
        DynamicColors.applyToActivityIfAvailable(this);super.onCreate(saved);
        File mailboxDir = getExternalFilesDir(null);
        updates=new Updates(this);mailbox=new Mailbox(mailboxDir);wireless=WirelessController.get(this);setup=new SetupSheet(this,wireless);
        Shizuku.addRequestPermissionResultListener(shizukuPermission);
        try{if(mailbox.selected(KeyPress.SHORT)==null && getPackageManager().getLaunchIntentForPackage("com.google.ar.lens")!=null)mailbox.select(KeyPress.SHORT, Helper.LENS);}catch(Exception ignored){}
        if (mailboxDir != null) initializeLongPressDefault();
        LinearLayout root=Ui.column(this);root.setPadding(dp(20),dp(12),dp(20),dp(28));
        LinearLayout top=Ui.actions(this,root);
        TextView title=new TextView(this);title.setText("Plus Key");title.setTextSize(36);title.setTypeface(null,Typeface.BOLD);title.setTextColor(Ui.color(this,com.google.android.material.R.attr.colorOnSurface));top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        MaterialButton settings=new MaterialButton(this,null,com.google.android.material.R.attr.materialIconButtonStyle);settings.setIconResource(android.R.drawable.ic_menu_preferences);settings.setContentDescription("Settings");settings.setOnClickListener(v->setup.show());top.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));
        Ui.text(this,root,"Your button. Your actions.",16,true);
        addPressCard(root, KeyPress.SHORT, "Short press", null);
        addPressCard(root, KeyPress.DOUBLE, "Double press", "Off");
        addPressCard(root, KeyPress.TRIPLE, "Triple press", "Off");
        Ui.button(this, root, clickIntervalLabel(), false, v -> showClickInterval((MaterialButton) v));
        addPressCard(root, KeyPress.LONG, "Long press", "Managed by OnePlus");
        LinearLayout longPressSetup = Ui.card(this, root);
        longPressSetupCard = (View) longPressSetup.getParent();
        longPressSetupCard.setVisibility(View.GONE);
        Ui.text(this, longPressSetup, "Turn off OnePlus action", 22, false);
        Ui.text(this, longPressSetup, LONG_PRESS_SETUP_HINT, 14, true);
        Ui.button(this, longPressSetup, "Open OnePlus settings", true, v -> onePlusSettings());
        LinearLayout connection=Ui.card(this,root);status=Ui.text(this,connection,"Checking…",24,false);detail=Ui.text(this,connection,"",14,true);
        progress=new LinearProgressIndicator(this);progress.setIndeterminate(true);progress.setVisibility(View.GONE);connection.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));
        start=Ui.button(this,connection,"Start",true,v->startHelper());
        stop=Ui.button(this,connection,"Stop",false,v->{if(wireless.busy.get())return;try{wireless.prefs().edit().putBoolean("user_stopped",true).apply();mailbox.requestStop();status.setText("Stopping…");}catch(IOException error){toast("Could not stop. Try again.");}});
        finishSetup=Ui.button(this,root,"Finish setup",true,v->setup.show());
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.color(this,com.google.android.material.R.attr.colorSurface));scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());view.setPadding(0,bars.top,0,bars.bottom);return insets;});
        setContentView(scroll);showSelections();
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
    private String clickIntervalLabel() {
        return "Click interval · " + mailbox.clickInterval() + " ms";
    }

    private void showClickInterval(MaterialButton button) {
        LinearLayout body = Ui.column(this);
        body.setPadding(dp(24), dp(8), dp(24), dp(12));
        int interval = mailbox.clickInterval();
        TextView value = Ui.text(this, body, interval + " ms", 18, false);
        Slider slider = new Slider(this);
        slider.setContentDescription("Click interval");
        slider.setValueFrom(Mailbox.MIN_CLICK_INTERVAL_MS);
        slider.setValueTo(Mailbox.MAX_CLICK_INTERVAL_MS);
        slider.setStepSize(1);
        slider.setTickVisibilityMode(TickVisibilityMode.TICK_VISIBILITY_HIDDEN);
        slider.setValue(interval);
        slider.setLabelFormatter(ms -> Math.round(ms) + " ms");
        slider.addOnChangeListener((view, ms, fromUser) -> value.setText(Math.round(ms) + " ms"));
        body.addView(slider, new LinearLayout.LayoutParams(-1, -2));
        Ui.text(this, body, "Longer intervals delay single- and double-press actions.", 14, true);
        new MaterialAlertDialogBuilder(this).setTitle("Click interval")
            .setView(body)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (dialog, which) -> {
                try {
                    mailbox.setClickInterval(Math.round(slider.getValue()));
                    button.setText(clickIntervalLabel());
                } catch (IOException error) {
                    toast("Could not save click interval");
                }
            }).show();
    }

    private void addPressCard(LinearLayout root, int gesture, String title, String unsetLabel) {
        LinearLayout card = Ui.card(this, root);
        Ui.text(this, card, title.toUpperCase(Locale.ROOT), 12, true);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(6),0,dp(10));
        ImageView icon=new ImageView(this);row.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView label=new TextView(this);label.setTextSize(23);label.setTextColor(Ui.color(this,com.google.android.material.R.attr.colorOnSurface));label.setPadding(dp(14),0,0,0);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);
        pressNames[gesture] = label;
        pressIcons[gesture] = icon;
        LinearLayout actions = Ui.actions(this, card);
        Ui.button(this, actions, "Choose", true, v -> choose(gesture, title, unsetLabel));
        pressPreviews[gesture] = Ui.button(this, actions, "Preview", false, v -> preview(gesture));
        Ui.equal(this, actions);
    }
    private void choose(int gesture, String title, String unsetLabel){new ChoicePicker(this,choice->{
        try{
            mailbox.select(gesture, choice.target);
            if(choice.target!=null)Targets.remember(this,choice);showSelections();
        }catch(Exception error){toast("Could not save. Choose another action.");}
    }).show(title, unsetLabel);}
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
        icon.setImageResource(target!=null&&target.equals("action:torch")?android.R.drawable.ic_menu_camera:target!=null&&target.startsWith("action:")?android.R.drawable.ic_lock_silent_mode:android.R.drawable.ic_menu_preferences);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this,androidx.appcompat.R.attr.colorPrimary)));
    }
    private void preview(int gesture){
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
            setTextIfChanged(status, "Starting…");
            setTextIfChanged(detail, wireless.message);
        } else if (ready) {
            String[] fields = current.split("\\|", 3);
            boolean failed = fields.length == 3 && (fields[2].startsWith("Launch failed") || fields[2].startsWith("Long press needs"));
            setTextIfChanged(status, failed ? "Action needs attention" : "Ready");
            setTextIfChanged(detail, failed ? fields[2] : wireless.automatic() && !ShizukuConnection.authorized() ? "Automatic startup on" : "Your actions are ready.");
        } else if (wireless.prefs().getBoolean("user_stopped", false)) {
            setTextIfChanged(status, "Stopped");
            setTextIfChanged(detail, "Tap Start when you’re ready.");
        } else if (current != null && current.startsWith("Error|")) {
            setTextIfChanged(status, "Needs attention");
            setTextIfChanged(detail, current.split("\\|", 3)[2]);
        } else {
            setTextIfChanged(status, configured ? "Not running" : "Setup needed");
            String message = wireless.message;
            if (!message.startsWith("Setup needed:")) {
                message = configured ? "Tap Start to reconnect your button." : "Finish setup to use your button.";
            }
            setTextIfChanged(detail, message);
        }
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
