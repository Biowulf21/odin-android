package io.hiddemoens.pluskey;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

final class Ui {
    static int dp(Context c,int n){return(int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    static int color(Context c,int attr){return MaterialColors.getColor(c,attr,0xff202020);}
    static ImageButton navigationButton(Context context) {
        ImageButton button = new ImageButton(context);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        int highlight = color(context, androidx.appcompat.R.attr.colorControlHighlight);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(highlight), null, mask));
        button.setImageTintList(ColorStateList.valueOf(color(context, androidx.appcompat.R.attr.colorPrimary)));
        return button;
    }
    static LinearLayout column(Context c){LinearLayout layout=new LinearLayout(c);layout.setOrientation(1);return layout;}
    static TextView text(Context c,LinearLayout root,String value,int size,boolean secondary){
        TextView text=new TextView(c);text.setText(value);text.setTextSize(size);text.setTextColor(color(c,secondary?com.google.android.material.R.attr.colorOnSurfaceVariant:com.google.android.material.R.attr.colorOnSurface));
        text.setPadding(0,dp(c,5),0,dp(c,5));root.addView(text,new LinearLayout.LayoutParams(-1,-2));return text;
    }
    static LinearLayout card(Context c,LinearLayout root){
        MaterialCardView card=new MaterialCardView(c);card.setCardElevation(0);card.setRadius(dp(c,28));card.setStrokeWidth(0);
        card.setCardBackgroundColor(color(c,com.google.android.material.R.attr.colorSurfaceContainer));
        LinearLayout content=column(c);content.setPadding(dp(c,20),dp(c,16),dp(c,20),dp(c,16));card.addView(content);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(c,14);root.addView(card,params);return content;
    }
    static MaterialButton button(Context c,LinearLayout root,String label,boolean primary,View.OnClickListener action){
        MaterialButton button=new MaterialButton(c,null,primary?com.google.android.material.R.attr.materialButtonStyle:com.google.android.material.R.attr.materialButtonTonalStyle);
        button.setText(label);button.setAllCaps(false);button.setTextSize(15);button.setMinHeight(dp(c,48));button.setOnClickListener(action);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(c,8);root.addView(button,params);return button;
    }
    static LinearLayout actions(Context c,LinearLayout root){LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);root.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;}
    static void equal(Context c,LinearLayout row){for(int i=0;i<row.getChildCount();i++){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)row.getChildAt(i).getLayoutParams();p.width=0;p.weight=1;if(i>0)p.leftMargin=dp(c,8);row.getChildAt(i).setLayoutParams(p);}}
}
