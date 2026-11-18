package io.hiddemoens.pluskey;
import android.content.*;
import android.content.pm.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.*;
import java.util.*;

final class ChoicePicker {
    interface Selected{void select(Targets.Choice choice);}
    private final Context context;private final Selected selected;
    ChoicePicker(Context context,Selected selected){this.context=context;this.selected=selected;}
    void show(String title, String unsetLabel){
        ArrayList<String> labels = new ArrayList<>();
        if (unsetLabel != null) labels.add(unsetLabel);
        labels.add("Action");
        labels.add("App");
        new MaterialAlertDialogBuilder(context).setTitle(title).setItems(labels.toArray(new String[0]), (d, index) -> {
            String choice = labels.get(index);
            if (choice.equals("App")) apps();
            else if (choice.equals("Action")) actions();
            else selected.select(new Targets.Choice(unsetLabel, null));
        }).setNegativeButton("Cancel", null).show();
    }
    private void actions(){
        ArrayList<Targets.Choice> choices=new ArrayList<>();
        for(int i=0;i<Targets.ACTIONS.length;i++)if(i!=0||context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH))choices.add(new Targets.Choice(Targets.LABELS[i],Targets.ACTIONS[i]));
        String[] labels=choices.stream().map(c->c.label).toArray(String[]::new);
        new MaterialAlertDialogBuilder(context).setTitle("Choose action").setItems(labels,(d,index)->selected.select(choices.get(index))).setNegativeButton("Cancel",null).show();
    }
    private void apps(){
        List<ResolveInfo> installed=context.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
        installed.removeIf(info->info.activityInfo.packageName.equals(context.getPackageName())||!info.activityInfo.exported);
        installed.sort(new ResolveInfo.DisplayNameComparator(context.getPackageManager()));
        ArrayList<ResolveInfo> filtered=new ArrayList<>(installed);
        LinearLayout body=Ui.column(context);body.setPadding(Ui.dp(context,20),Ui.dp(context,8),Ui.dp(context,20),0);
        TextInputLayout field=new TextInputLayout(context);field.setHint("Search apps");TextInputEditText search=new TextInputEditText(field.getContext());search.setSingleLine(true);field.addView(search);body.addView(field);
        ListView list=new ListView(context);list.setDivider(null);body.addView(list,new LinearLayout.LayoutParams(-1,Ui.dp(context,360)));
        BaseAdapter adapter=new BaseAdapter(){
            public int getCount(){return filtered.size();}public Object getItem(int n){return filtered.get(n);}public long getItemId(int n){return n;}
            public View getView(int n,View recycled,ViewGroup parent){ResolveInfo info=filtered.get(n);LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(context,4),Ui.dp(context,12),0,Ui.dp(context,12));
                ImageView icon=new ImageView(context);icon.setImageDrawable(info.loadIcon(context.getPackageManager()));row.addView(icon,new LinearLayout.LayoutParams(Ui.dp(context,36),Ui.dp(context,36)));
                TextView name=new TextView(context);name.setText(info.loadLabel(context.getPackageManager()));name.setTextSize(17);name.setPadding(Ui.dp(context,14),0,0,0);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));return row;}
        };list.setAdapter(adapter);
        AlertDialog dialog=new MaterialAlertDialogBuilder(context).setTitle("Choose app").setView(body).setNegativeButton("Cancel",null).create();
        list.setOnItemClickListener((parent,view,index,id)->{ResolveInfo app=filtered.get(index);dialog.dismiss();appActions(app);});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int st,int b,int c){String query=s.toString().toLowerCase(Locale.ROOT);filtered.clear();for(ResolveInfo info:installed)if(info.loadLabel(context.getPackageManager()).toString().toLowerCase(Locale.ROOT).contains(query))filtered.add(info);adapter.notifyDataSetChanged();}});
        dialog.show();
    }
    private void appActions(ResolveInfo app){
        String appName=app.loadLabel(context.getPackageManager()).toString();
        Targets.Choice open=new Targets.Choice(appName,new ComponentName(app.activityInfo.packageName,app.activityInfo.name).flattenToString());
        ArrayList<Targets.Choice> choices=Targets.shortcuts(context,app);
        if(choices.isEmpty()){selected.select(open);return;}
        choices.add(0,open);String[] labels=new String[choices.size()];labels[0]="Open app";for(int i=1;i<labels.length;i++)labels[i]=choices.get(i).label;
        new MaterialAlertDialogBuilder(context).setTitle(appName).setItems(labels,(d,index)->selected.select(choices.get(index))).setNegativeButton("Cancel",null).show();
    }
}
