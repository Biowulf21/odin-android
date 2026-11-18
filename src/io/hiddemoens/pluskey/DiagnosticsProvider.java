package io.hiddemoens.pluskey;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import java.util.Map;

/** Read-only diagnostics, accessible to ADB shell/platform diagnostics only. */
public final class DiagnosticsProvider extends ContentProvider {
    public boolean onCreate() { return true; }
    public Bundle call(String method,String arg,Bundle extras) {
        if (!"status".equals(method)) throw new IllegalArgumentException("Unknown diagnostic");
        WirelessController controller=WirelessController.get(getContext());
        Bundle result=new Bundle();
        result.putBoolean("busy",controller.busy.get());result.putBoolean("paired",controller.paired());
        result.putString("stage",controller.message);
        result.putBoolean("automatic",controller.automatic());
        result.putBoolean("setting_permission",controller.canControlDebugging());
        result.putString("permission_result",controller.permissionResult);
        result.putInt("pairing_port",controller.discovery.pairingPort);result.putInt("connect_port",controller.discovery.connectPort);
        StringBuilder stacks=new StringBuilder();
        for(Map.Entry<Thread,StackTraceElement[]> entry:Thread.getAllStackTraces().entrySet()){
            boolean own=false;
            for(StackTraceElement frame:entry.getValue())if(frame.getClassName().startsWith("io.hiddemoens.pluskey.WirelessController")||frame.getClassName().startsWith("io.github.muntashirakon.adb."))own=true;
            if(!own)continue;
            stacks.append(entry.getKey().getName()).append(" ").append(entry.getKey().getState()).append("\n");
            for(StackTraceElement frame:entry.getValue())stacks.append(frame).append("\n");
        }
        result.putString("threads",stacks.toString());return result;
    }
    public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    public String getType(Uri u){return null;}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
