package io.hiddemoens.pluskey;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

final class SetupSheet {
    private final MainActivity activity;
    private final WirelessController wireless;
    private AlertDialog dialog;private LinearLayout body;private String previous="";
    SetupSheet(MainActivity activity,WirelessController wireless){this.activity=activity;this.wireless=wireless;}
    boolean localAllowed(){return Build.VERSION.SDK_INT<37||activity.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK")==PackageManager.PERMISSION_GRANTED;}
    boolean notificationsAllowed(){return Build.VERSION.SDK_INT<33||activity.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED;}
    boolean developerEnabled(){return wireless.paired()||Settings.Global.getInt(activity.getContentResolver(),Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,0)==1;}
    boolean wifiDebugging(){return Settings.Global.getInt(activity.getContentResolver(),"adb_wifi_enabled",0)==1;}
    void show(){
        if(dialog!=null&&dialog.isShowing()){refresh();return;}
        body=Ui.column(activity);body.setPadding(Ui.dp(activity,24),0,Ui.dp(activity,24),Ui.dp(activity,16));ScrollView page=new ScrollView(activity);page.addView(body);
        dialog=new MaterialAlertDialogBuilder(activity).setTitle("Set up your button").setView(page).setPositiveButton("Done",null).setNeutralButton("More options",(d,w)->advanced()).create();
        previous="";dialog.show();refresh();
    }
    void refresh(){
        if(dialog==null||!dialog.isShowing())return;
        String state=developerEnabled()+"/"+localAllowed()+"/"+notificationsAllowed()+"/"+wireless.paired()+"/"+wifiDebugging()+"/"+wireless.automatic()+"/"+wireless.busy.get();
        if(state.equals(previous))return;previous=state;body.removeAllViews();
        if(!developerEnabled()){
            text("Enable phone setup",22,false);text("Open About device → Version and tap Build number seven times. Android requires this once.",15,true);
            button("Open About device",v->open(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),new Intent(Settings.ACTION_SETTINGS)));return;
        }
        if(!localAllowed()||(!wireless.paired()&&!notificationsAllowed())){
            text("Allow connection setup",22,false);
            if(!localAllowed())text("Local connection · Needed to connect this app to your phone’s button service.",15,true);
            if(!wireless.paired()&&!notificationsAllowed())text("Notifications · Needed to enter pairing code while Android’s pairing window stays open.",15,true);
            button("Allow permissions",v->activity.requestSetupPermissions());
            button("Permission settings",v->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.getPackageName()))));return;
        }
        if(!wireless.paired()){
            text("Pair with your phone",22,false);
            text("This is a one-time connection. Android will show a six-digit code. Keep that window open and enter code in Plus Key notification.",15,true);
            if(!wifiDebugging())text("First enable Wireless debugging on the next screen. Android may ask you to approve your Wi-Fi network.",15,true);
            button("Pair with code",v->pairWithCode());return;
        }
        text("Phone paired",22,false);text("Permissions ready. Your saved connection is available.",15,true);
        if(wireless.message.startsWith("Setup needed:"))text(wireless.message,14,true);
        if(wireless.prefs().getBoolean("permission_monitor_blocked",false)&&!wireless.canControlDebugging()){
            text("OnePlus is blocking automatic setup",20,false);
            text("For automatic startup, turn on "+restrictionLabel()+" in Developer options. This relaxes OnePlus’s debugging restrictions and may increase background power use. Manual startup remains available.",15,true);
            button("Open permission setting",v->permissionMonitoringSettings());
            button("Retry automatic setup",v->automaticConsent());
            if(wifiDebugging())button("Start manually",v->{wireless.disableAutomatic();activity.startHelper();});
            return;
        }
        if(wireless.automatic()){
            text("Automatic startup is on",20,false);text("App enables its connection when needed and turns Wireless debugging off once your button is ready.",15,true);
            if(!wireless.busy.get())button("Start",v->activity.startHelper());
        }else{
            text("Make startup automatic",20,false);text("Let the app turn Wireless debugging on only while starting your button, then turn it off again.",15,true);
            button("Enable automatic startup",v->automaticConsent());
            if(!wifiDebugging())button("Open Wireless debugging",v->wirelessSettings());else if(!wireless.busy.get())button("Start manually",v->activity.startHelper());
        }
    }
    void automaticConsent(){
        if(wireless.busy.get()){Toast.makeText(activity,"Wait for current setup to finish",Toast.LENGTH_SHORT).show();return;}
        new MaterialAlertDialogBuilder(activity).setTitle("Allow automatic connection?")
            .setMessage("One-time setup grants WRITE_SECURE_SETTINGS. Android allows this permission to change protected system settings; Plus Key uses it only to turn Wireless debugging on for startup and off afterward. Your Wi-Fi stays connected. Android may still ask you to approve a new Wi-Fi network.")
            .setNegativeButton("Not now",null).setPositiveButton("Allow",(d,w)->{
                wireless.prefs().edit().putInt("automatic_consent_revision",2).putBoolean("automatic_requested",true).putBoolean("user_stopped",false).apply();
                if(wireless.canControlDebugging()){wireless.prefs().edit().putBoolean("automatic_debugging",true).putBoolean("automatic_requested",false).apply();wireless.start();}
                else if(wireless.paired()&&wifiDebugging())wireless.start();
                else{Toast.makeText(activity,"Enable Wireless debugging once to finish automatic setup",Toast.LENGTH_LONG).show();wirelessSettings();}
                previous="";refresh();
            }).show();
    }
    void advanced(){
        String[] options={"Wireless debugging settings","Pair with code","App permissions","App battery settings",wireless.automatic()?"Turn off automatic startup":"Automatic startup","Check for updates",activity.automaticUpdates()?"Turn off automatic update checks":"Turn on automatic update checks"};
        new MaterialAlertDialogBuilder(activity).setTitle("Connection options").setItems(options,(d,index)->{
            if(index==0)wirelessSettings();else if(index==1)pairWithCode();
            else if(index==2)open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.getPackageName())));
            else if(index==3)open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            else if(index==5)activity.checkUpdates();
            else if(index==6)activity.toggleUpdateChecks();
            else if(wireless.automatic()){wireless.disableAutomatic();previous="";refresh();}else automaticConsent();
        }).setNegativeButton("Close",null).show();
    }
    void pairWithCode(){
        if(!localAllowed()||!notificationsAllowed()){activity.requestPairingPermissions();return;}
        wireless.pairingNotification();wirelessSettings();
    }
    private void permissionMonitoringSettings(){
        String key=modernRestriction()?"safecenter_system_optimization":"permission_monitoring";
        Intent intent=new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);Bundle args=new Bundle();args.putString(":settings:fragment_args_key",key);intent.putExtra(":settings:fragment_args_key",key).putExtra(":settings:show_fragment_args",args);
        open(intent,new Intent(Settings.ACTION_SETTINGS));Toast.makeText(activity,"Find "+restrictionLabel(),Toast.LENGTH_LONG).show();
    }
    private String restrictionLabel(){
        try{android.content.res.Resources resources=activity.getPackageManager().getResourcesForApplication("com.android.settings");
            int name=resources.getIdentifier("safecenter_system_optimization_title","string","com.android.settings");if(name!=0)return resources.getString(name);
        }catch(Exception ignored){}
        return android.os.Build.VERSION.SDK_INT>=36?"Disable system optimisation":"Disable permission monitoring";
    }
    private boolean modernRestriction(){try{return activity.getPackageManager().getResourcesForApplication("com.android.settings").getIdentifier("safecenter_system_optimization_title","string","com.android.settings")!=0;}catch(Exception error){return Build.VERSION.SDK_INT>=36;}}
    void wirelessSettings(){
        Intent fallback=new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);Bundle args=new Bundle();args.putString(":settings:fragment_args_key","toggle_adb_wireless");fallback.putExtra(":settings:fragment_args_key","toggle_adb_wireless").putExtra(":settings:show_fragment_args",args);
        open(new Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"),fallback,new Intent(Settings.ACTION_SETTINGS));
        Toast.makeText(activity,"Open Wireless debugging in Developer options",Toast.LENGTH_LONG).show();
    }
    private void open(Intent... intents){for(Intent intent:intents)try{activity.startActivity(intent);return;}catch(Exception ignored){}Toast.makeText(activity,"Open phone Settings manually",Toast.LENGTH_LONG).show();}
    private void text(String s,int size,boolean secondary){Ui.text(activity,body,s,size,secondary);}
    private void button(String s,android.view.View.OnClickListener action){Ui.button(activity,body,s,true,action);}
}
