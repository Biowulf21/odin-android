package io.hiddemoens.pluskey;

import android.content.*;
import android.content.pm.*;
import android.content.res.*;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Only app-published, enabled static shortcuts that resolve to that app. */
final class Targets {
    static final String[] ACTIONS=Options.ACTIONS;
    static final String[] LABELS={"Toggle flashlight","Sound → Vibrate → Silent","Sound ↔ Vibrate","Sound ↔ Silent","Silent","Vibrate","Sound on","Screenshot","Lock screen","Open notifications","Open quick settings","Play / pause","Next track","Previous track","Volume up","Volume down"};
    static final class Choice {final String label,target;Choice(String label,String target){this.label=label;this.target=target;}}
    static String label(Context context,String target){
        if(target==null)return "Managed by OnePlus";
        for(int i=0;i<ACTIONS.length;i++)if(ACTIONS[i].equals(target))return LABELS[i];
        String saved=context.getSharedPreferences("choices",0).getString(target,"");
        if(!saved.isEmpty())return saved;
        try{return context.getPackageManager().getActivityInfo(ComponentName.unflattenFromString(target),0).loadLabel(context.getPackageManager()).toString();}
        catch(Exception error){return "Choose an action";}
    }
    static void remember(Context context,Choice choice){context.getSharedPreferences("choices",0).edit().putString(choice.target,choice.label).apply();}
    static ArrayList<Choice> shortcuts(Context context,ResolveInfo app){
        ArrayList<Choice> found=new ArrayList<>();String owner=app.activityInfo.packageName;
        try{
            ActivityInfo info=context.getPackageManager().getActivityInfo(new ComponentName(owner,app.activityInfo.name),PackageManager.GET_META_DATA);
            int resource=info.metaData==null?0:info.metaData.getInt("android.app.shortcuts",0);
            if(resource==0)return found;
            Resources resources=context.getPackageManager().getResourcesForApplication(owner);
            try(XmlResourceParser parser=resources.getXml(resource)){
                String label=null;boolean enabled=false;ArrayList<Intent> intents=new ArrayList<>();
                int event;
                while((event=parser.next())!=XmlPullParser.END_DOCUMENT){
                    if(event==XmlPullParser.START_TAG && parser.getName().equals("shortcut")){
                        enabled=parser.getAttributeBooleanValue("http://schemas.android.com/apk/res/android","enabled",true);
                        int name=parser.getAttributeResourceValue("http://schemas.android.com/apk/res/android","shortcutShortLabel",0);
                        label=name==0?parser.getAttributeValue("http://schemas.android.com/apk/res/android","shortcutShortLabel"):resources.getString(name);intents.clear();
                    }else if(event==XmlPullParser.START_TAG && parser.getName().equals("intent") && enabled){
                        Intent intent=Intent.parseIntent(resources,parser,Xml.asAttributeSet(parser));
                        intent.setPackage(owner);
                        ResolveInfo resolved=context.getPackageManager().resolveActivity(intent,0);
                        if(resolved==null||!owner.equals(resolved.activityInfo.packageName)||!resolved.activityInfo.exported||
                            (resolved.activityInfo.permission!=null && context.checkSelfPermission(resolved.activityInfo.permission)!=PackageManager.PERMISSION_GRANTED)){enabled=false;continue;}
                        intent.setComponent(new ComponentName(owner,resolved.activityInfo.name));
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intents.add(intent);
                    }else if(event==XmlPullParser.END_TAG && parser.getName().equals("shortcut") && enabled && label!=null && !intents.isEmpty()){
                        StringBuilder encoded=new StringBuilder();
                        for(Intent intent:intents){if(encoded.length()>0)encoded.append('\0');encoded.append(intent.toUri(Intent.URI_INTENT_SCHEME));}
                        String target="shortcut:"+owner+":"+Base64.getUrlEncoder().withoutPadding().encodeToString(encoded.toString().getBytes(StandardCharsets.UTF_8));
                        if(Options.validTarget(target))found.add(new Choice(label,target));
                    }
                }
            }
        }catch(Exception ignored){}
        return found;
    }
    static Intent[] intents(String target)throws Exception{
        if(!Options.validTarget(target))throw new IllegalArgumentException("Invalid action");
        if(Options.validComponent(target))return new Intent[]{new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName.unflattenFromString(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)};
        if(!target.startsWith("shortcut:"))return new Intent[0];
        int split=target.indexOf(':',9);String owner=target.substring(9,split);
        String decoded=new String(Base64.getUrlDecoder().decode(target.substring(split+1)),StandardCharsets.UTF_8);
        ArrayList<Intent> intents=new ArrayList<>();
        for(String uri:decoded.split("\u0000",-1)){
            Intent intent=Intent.parseUri(uri,Intent.URI_INTENT_SCHEME);
            if(intent.getComponent()==null||!owner.equals(intent.getComponent().getPackageName()))throw new IllegalArgumentException("Shortcut belongs to another app");
            intent.setPackage(owner);intent.setSelector(null);intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);intents.add(intent);
        }
        return intents.toArray(new Intent[0]);
    }
}
