package io.github.op7418.guizangtv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** The same native home layout is used for the live home and Settings preview. */
final class HomeSurface extends FrameLayout {
    interface Actions { void open(String id); void control(int index); void selected(int index); }
    final List<View> apps = new ArrayList<>(), controls = new ArrayList<>();
    final ImageView previewPhoto;
    final TextView clock;
    private final HomeAppearance style;
    private final boolean preview;
    private final Actions actions;
    private int selected;
    private android.graphics.Bitmap material;
    private int materialRequest;
    HomeSurface(Context c, HomeAppearance style, HomeItems items, Drawable wallpaper, int selected,
                boolean preview, Actions actions) {
        super(c); this.style=style;this.preview=preview;this.actions=actions;
        this.selected=Math.max(0,Math.min(items.order.size()-1,selected));
        previewPhoto = new ImageView(c);
        previewPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if(preview) {
            setBackgroundColor(style.light()?0xFFF0EFE9:0xFF11151B);
            if(style.get("background")==0){previewPhoto.setImageDrawable(wallpaper);addView(previewPhoto,new LayoutParams(-1,-1));}
        }
        if(style.get("background")==0) {
            View shade=new View(c);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0x990C0C0C,0x360C0C0C,0x700C0C0C}));
            addView(shade,new LayoutParams(-1,-1));
        }
        boolean dock=style.get("layout")==1;
        LinearLayout list=new LinearLayout(c);
        list.setOrientation(dock?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        list.setClipChildren(false);list.setClipToPadding(false);
        if(dock){
            HorizontalScrollView scroll=new HorizontalScrollView(c);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);
            scroll.addView(list,new HorizontalScrollView.LayoutParams(-2,-1));
            LayoutParams p=new LayoutParams(dp(840),dp(114),Gravity.BOTTOM|Gravity.START);p.leftMargin=dp(60);p.bottomMargin=dp(54);addView(scroll,p);
        }else{
            ScrollView scroll=new ScrollView(c);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);
            scroll.addView(list,new ScrollView.LayoutParams(-1,-2));
            LayoutParams p=new LayoutParams(dp(620),dp(400),Gravity.CENTER_VERTICAL|Gravity.START);p.leftMargin=dp(56);addView(scroll,p);
        }
        for(int i=0;i<items.order.size();i++){
            String id=items.order.get(i);final int index=i;
            String suffix=id.startsWith("hdmi:")?"HDMI "+id.substring(5):"";
            LinearLayout tile=new LinearLayout(c);tile.setOrientation(dock?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
            tile.setGravity(dock?Gravity.CENTER:Gravity.CENTER_VERTICAL);tile.setPadding(dp(14),dp(4),dp(18),dp(4));
            TextView label=text(items.name(id),dock?13:style.size());label.setTypeface(style.typeface());
            label.setMaxWidth(dp(dock?106:style.get("apps")==0?510:450));
            label.setMaxLines(1);label.setEllipsize(TextUtils.TruncateAt.END);
            ImageView icon=new ImageView(c);icon.setImageDrawable(HomeIcons.get(c,id,style.ink()));
            int mode=style.get("apps");
            if(mode!=0){LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(dock?38:32),dp(dock?38:32));if(!dock)ip.rightMargin=dp(16);tile.addView(icon,ip);}
            // Icon-only mode still reveals the focused app name and keeps its accessible label.
            tile.addView(label,new LinearLayout.LayoutParams(dock?-1:-2,dock?dp(30):-2));
            label.setGravity(dock?Gravity.CENTER:Gravity.CENTER_VERTICAL);
            TextView tag=text(suffix,11);tag.setAlpha(.7f);
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);if(!dock)tp.leftMargin=dp(16);tile.addView(tag,tp);
            LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(dock?dp(112):-2,dock?dp(108):dp(65));
            if(dock)row.rightMargin=dp(12);list.addView(tile,row);apps.add(tile);
            wire(tile,()->actions.open(id));tile.setContentDescription(items.name(id)+(suffix.isEmpty()?"":"，"+suffix));
            boolean chosen=index==this.selected;
            paint(tile,label,chosen,mode==1);
            tile.setOnFocusChangeListener((v,focus)->{
                paint(tile,label,focus,mode==1);
                if(focus){this.selected=index;actions.selected(index);for(int cIndex=0;cIndex<controls.size();cIndex++){View control=controls.get(cIndex);if(dock){control.setNextFocusDownId(tile.getId());if(cIndex==0)control.setNextFocusLeftId(tile.getId());}else control.setNextFocusLeftId(tile.getId());}}
            });
        }
        clock=text("",26);clock.setTypeface(style.typeface());clock.setGravity(Gravity.END);
        LayoutParams cp=new LayoutParams(dp(160),dp(45),Gravity.TOP|(dock?Gravity.START:Gravity.END));cp.topMargin=dp(45);cp.rightMargin=dp(64);if(dock){cp.leftMargin=dp(60);clock.setGravity(Gravity.START);}addView(clock,cp);
        LinearLayout side=new LinearLayout(c);side.setOrientation(dock?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);side.setGravity(Gravity.END);
        LayoutParams sp=new LayoutParams(dock?-2:dp(180),-2,(dock?Gravity.TOP:Gravity.BOTTOM)|Gravity.END);sp.rightMargin=dp(64);sp.bottomMargin=dp(46);if(dock)sp.topMargin=dp(45);addView(side,sp);
        String[] names={"全部应用","其他信号源","设置"};String[] ids={"apps","inputs","settings"};
        for(int i=0;i<names.length;i++){
            final int index=i;LinearLayout line=new LinearLayout(c);line.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            TextView label=text(names[i],16);label.setTypeface(style.typeface());int mode=style.get("controls");
            line.addView(label,new LinearLayout.LayoutParams(-2,-2));
            if(mode!=0){ImageView icon=new ImageView(c);icon.setImageDrawable(HomeIcons.get(c,"control:"+ids[i],style.ink()));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(23),dp(23));ip.leftMargin=dp(12);line.addView(icon,ip);}
            line.setPadding(dp(12),0,dp(12),0);side.addView(line,new LinearLayout.LayoutParams(-2,dp(40)));controls.add(line);
            wire(line,()->actions.control(index));line.setContentDescription(names[i]);
            paint(line,label,false,mode==1);line.setOnFocusChangeListener((v,focus)->paint(line,label,focus,mode==1));
        }
        for(int i=0;i<apps.size();i++){
            View v=apps.get(i),prev=apps.get(Math.max(0,i-1)),next=apps.get(Math.min(apps.size()-1,i+1));
            v.setNextFocusUpId(dock?controls.get(0).getId():prev.getId());v.setNextFocusDownId(dock?v.getId():next.getId());
            v.setNextFocusLeftId(dock?prev.getId():v.getId());v.setNextFocusRightId(dock&&i<apps.size()-1?next.getId():controls.get(0).getId());
        }
        for(int i=0;i<controls.size();i++){
            View v=controls.get(i);
            if(dock){
                v.setNextFocusUpId(v.getId());v.setNextFocusDownId(apps.get(this.selected).getId());
                v.setNextFocusLeftId(i==0?apps.get(this.selected).getId():controls.get(i-1).getId());
                v.setNextFocusRightId(controls.get(Math.min(controls.size()-1,i+1)).getId());
            }else{
                v.setNextFocusUpId(controls.get(Math.max(0,i-1)).getId());v.setNextFocusDownId(controls.get(Math.min(controls.size()-1,i+1)).getId());
                v.setNextFocusRightId(v.getId());v.setNextFocusLeftId(apps.get(this.selected).getId());
            }
        }
        if(preview)setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        wallpaper(wallpaper);
    }
    void focusApp(){apps.get(selected).requestFocus();}
    private void paint(View v,TextView label,boolean focused,boolean iconsOnly){
        v.setAlpha(focused?1f:style.faded());label.setVisibility(iconsOnly&&!focused&&style.get("layout")==0?GONE:VISIBLE);
        if(iconsOnly&&style.get("layout")==1)label.setAlpha(focused?1:0);
        if(style.get("focus")==1&&focused){
            int veil=style.light()?0x2AFFFFFF:new int[]{0x60101418,0x48101418,0x30101418}[style.get("material")];
            v.setBackground(new WallpaperMaterial.Glass(this,v,material,dp(12),veil));
        }else v.setBackgroundColor(Color.TRANSPARENT);
    }
    void wallpaper(Drawable drawable){
        int request=++materialRequest;
        if(style.get("focus")!=1||style.get("background")!=0)return;
        WallpaperMaterial.request(drawable,new int[]{14,42,2}[style.get("material")],field->{
            if(request!=materialRequest)return;material=field;
            for(View v:apps)if(v.isFocused()||(preview&&apps.indexOf(v)==selected))
                v.setBackground(new WallpaperMaterial.Glass(this,v,material,dp(12),new int[]{0x60101418,0x48101418,0x30101418}[style.get("material")]));
            for(View v:controls)if(v.isFocused())v.setBackground(new WallpaperMaterial.Glass(this,v,material,dp(12),0x50101418));
        });
    }
    private void wire(View v,Runnable run){
        v.setId(View.generateViewId());v.setFocusable(!preview);v.setFocusableInTouchMode(!preview);v.setClickable(!preview);
        if(!preview){v.setOnClickListener(w->run.run());v.setOnKeyListener((w,key,e)->(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)&&e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()>0);}
    }
    private TextView text(String s,float size){TextView v=new TextView(getContext());v.setText(s);v.setTextSize(size);v.setTextColor(style.ink());v.setIncludeFontPadding(false);return v;}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
