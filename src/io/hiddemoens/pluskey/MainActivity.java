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
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends AppCompatActivity {
    private static final long STATUS_CHECK_MS = 2_000;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Mailbox mailbox;private WirelessController wireless;private SetupSheet setup;
    private TextView shortName,longName,longNote,status,detail;private ImageView shortIcon,longIcon;
    private MaterialButton start,stop,finishSetup,longSecondary;private LinearProgressIndicator progress;
    private boolean resumed,autoAttempt;
    private Updates updates;
    private boolean resumePairingAfterPermission;
    private FileObserver statusObserver;
    private int resumeGeneration;
    private final Runnable statusChanged = () -> refresh(false);
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            refresh(true);
            handler.postDelayed(this, STATUS_CHECK_MS);
        }
    };
    protected void onCreate(Bundle saved){
        DynamicColors.applyToActivityIfAvailable(this);super.onCreate(saved);
        updates=new Updates(this);mailbox=new Mailbox(getExternalFilesDir(null));wireless=WirelessController.get(this);setup=new SetupSheet(this,wireless);
        try{if(mailbox.selected()==null && getPackageManager().getLaunchIntentForPackage("com.google.ar.lens")!=null)mailbox.select(Helper.LENS);}catch(Exception ignored){}
        LinearLayout root=Ui.column(this);root.setPadding(dp(20),dp(12),dp(20),dp(28));
        LinearLayout top=Ui.actions(this,root);
        TextView title=new TextView(this);title.setText("Plus Key");title.setTextSize(36);title.setTypeface(null,Typeface.BOLD);title.setTextColor(Ui.color(this,com.google.android.material.R.attr.colorOnSurface));top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        MaterialButton settings=new MaterialButton(this,null,com.google.android.material.R.attr.materialIconButtonStyle);settings.setIconResource(android.R.drawable.ic_menu_preferences);settings.setContentDescription("Settings");settings.setOnClickListener(v->setup.show());top.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));
        Ui.text(this,root,"Your button. Your actions.",16,true);
        LinearLayout shortCard=Ui.card(this,root);Ui.text(this,shortCard,"SHORT PRESS",12,true);
        shortName=selectionRow(shortCard,false);
        LinearLayout shortActions=Ui.actions(this,shortCard);Ui.button(this,shortActions,"Choose",true,v->choose(false));Ui.button(this,shortActions,"Preview",false,v->preview(false));Ui.equal(this,shortActions);
        LinearLayout longCard=Ui.card(this,root);Ui.text(this,longCard,"LONG PRESS",12,true);
        longName=selectionRow(longCard,true);longNote=Ui.text(this,longCard,"",14,true);
        LinearLayout longActions=Ui.actions(this,longCard);Ui.button(this,longActions,"Choose",true,v->choose(true));longSecondary=Ui.button(this,longActions,"Plus Key settings",false,v->onePlusSettings());Ui.equal(this,longActions);
        LinearLayout connection=Ui.card(this,root);status=Ui.text(this,connection,"Checking…",24,false);detail=Ui.text(this,connection,"",14,true);
        progress=new LinearProgressIndicator(this);progress.setIndeterminate(true);progress.setVisibility(View.GONE);connection.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));
        start=Ui.button(this,connection,"Start",true,v->startHelper());
        stop=Ui.button(this,connection,"Stop",false,v->{if(wireless.busy.get())return;try{wireless.prefs().edit().putBoolean("user_stopped",true).apply();mailbox.requestStop();status.setText("Stopping…");}catch(IOException error){toast("Could not stop. Try again.");}});
        finishSetup=Ui.button(this,root,"Finish setup",true,v->setup.show());
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.color(this,com.google.android.material.R.attr.colorSurface));scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());view.setPadding(0,bars.top,0,bars.bottom);return insets;});
        setContentView(scroll);showSelections();
    }
    private TextView selectionRow(LinearLayout card,boolean longer){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(6),0,dp(10));
        ImageView icon=new ImageView(this);row.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView label=new TextView(this);label.setTextSize(23);label.setTextColor(Ui.color(this,com.google.android.material.R.attr.colorOnSurface));label.setPadding(dp(14),0,0,0);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);
        if(longer)longIcon=icon;else shortIcon=icon;return label;
    }
    private void choose(boolean longPress){new ChoicePicker(this,choice->{
        try{
            if(longPress)mailbox.selectLong(choice.target);else mailbox.select(choice.target);
            if(choice.target!=null)Targets.remember(this,choice);showSelections();
            if(longPress && choice.target!=null && !"nothing".equals(systemAction()))new MaterialAlertDialogBuilder(this).setTitle("Turn off OnePlus action")
                .setMessage("Set OnePlus button action to Do nothing so your chosen long-press action can run.").setPositiveButton("Open settings",(d,w)->onePlusSettings()).setNegativeButton("Later",null).show();
        }catch(Exception error){toast("Could not save. Choose another action.");}
    }).show(longPress);}
    private String systemAction(){try{return Settings.System.getString(getContentResolver(),Helper.ONEPLUS_ACTION_SETTING);}catch(Exception error){return null;}}
    private void showSelections(){
        try{showSelection(mailbox.selected(),shortName,shortIcon);String selected=mailbox.selectedLong();showSelection(selected,longName,longIcon);
            boolean blocked=selected!=null&&!"nothing".equals(systemAction());
            boolean customReady=selected!=null&&!blocked;
            longSecondary.setText(customReady?"Preview":"Plus Key settings");
            longSecondary.setOnClickListener(v->{if(customReady)preview(true);else onePlusSettings();});longNote.setVisibility(blocked?View.VISIBLE:View.GONE);longNote.setText("Set OnePlus action to Do nothing to use this.");
        }catch(IOException error){toast("Choose an action again");}
    }
    private void showSelection(String target,TextView name,ImageView icon){
        name.setText(Targets.label(this,target));icon.setImageTintList(null);
        try{
            Intent[] intents=target==null?new Intent[0]:Targets.intents(target);
            if(intents.length>0){icon.setImageDrawable(getPackageManager().getActivityIcon(intents[intents.length-1]));return;}
        }catch(Exception ignored){}
        icon.setImageResource(target!=null&&target.equals("action:torch")?android.R.drawable.ic_menu_camera:target!=null&&target.startsWith("action:")?android.R.drawable.ic_lock_silent_mode:android.R.drawable.ic_menu_preferences);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this,androidx.appcompat.R.attr.colorPrimary)));
    }
    private void preview(boolean longer){
        try{String target=longer?mailbox.selectedLong():mailbox.selected();if(target==null){onePlusSettings();return;}
            if(target.startsWith("action:")){String current=mailbox.readStatus(System.currentTimeMillis());if(current!=null&&current.startsWith("Listening|")){mailbox.requestPreview(longer);return;}toast("Start your button first to preview this action");return;}
            startActivities(Targets.intents(target));
        }catch(Exception error){toast("Action unavailable. Choose another.");}
    }
    void startHelper(){
        wireless.prefs().edit().putBoolean("user_stopped",false).apply();
        if(!wireless.paired()||!setup.localAllowed()){setup.show();return;}
        if(!setup.wifiDebugging()&&!wireless.automatic()){setup.show();return;}
        wireless.start();refresh(true);
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
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        start.setVisibility(!ready && !busy && paired ? View.VISIBLE : View.GONE);
        stop.setVisibility(ready && !busy ? View.VISIBLE : View.GONE);
        finishSetup.setVisibility(!ready && !busy ? View.VISIBLE : View.GONE);
        setTextIfChanged(finishSetup, paired ? "Fix setup" : "Finish setup");
        if (busy) {
            setTextIfChanged(status, "Starting…");
            setTextIfChanged(detail, wireless.message);
        } else if (ready) {
            String[] fields = current.split("\\|", 3);
            boolean failed = fields.length == 3 && (fields[2].startsWith("Launch failed") || fields[2].startsWith("Long press needs"));
            setTextIfChanged(status, failed ? "Action needs attention" : "Ready");
            setTextIfChanged(detail, failed ? fields[2] : wireless.automatic() ? "Automatic startup on" : "Your actions are ready.");
        } else if (wireless.prefs().getBoolean("user_stopped", false)) {
            setTextIfChanged(status, "Stopped");
            setTextIfChanged(detail, "Tap Start when you’re ready.");
        } else if (current != null && current.startsWith("Error|")) {
            setTextIfChanged(status, "Needs attention");
            setTextIfChanged(detail, current.split("\\|", 3)[2]);
        } else {
            setTextIfChanged(status, paired ? "Not running" : "Setup needed");
            setTextIfChanged(detail, wireless.message.isEmpty() ? "Finish setup to use your button." : wireless.message);
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
        if((!autoAttempt||pendingAutomatic)&&wireless.paired()&&!wireless.prefs().getBoolean("user_stopped",false)&&setup.localAllowed()){
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
    protected void onDestroy(){worker.shutdownNow();if(updates!=null)updates.close();super.onDestroy();}
}
