package io.github.op7418.guizangtv;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Remote-first grouped settings. Focus browses; OK commits; Back returns one level. */
final class HomeSettings {
    private final FrameHomeActivity a;
    private Dialog dialog;
    private String page="overview";
    private final Map<String,Integer> remembered=new HashMap<>();
    private final List<View> choices=new ArrayList<>();
    private final Deque<List<String>> undo=new ArrayDeque<>();
    private LinearLayout options;
    private HomeSurface preview;
    private FrameLayout previewHost;
    private TextView status;
    private boolean rendering;
    HomeSettings(FrameHomeActivity activity){a=activity;}
    boolean showing(){return dialog!=null&&dialog.isShowing();}
    String page(){return showing()?page:"closed";}
    void time(String time){if(preview!=null)preview.clock.setText(time);}
    void show(){
        if(showing())return;
        page="overview";undo.clear();
        dialog=new Dialog(a){@Override public void onBackPressed(){back();}};
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setOnDismissListener(d->{a.wallpapers.cancelPreview();a.wallpapers.cancelSelection();undo.clear();a.updateRotation();a.surface.controls.get(2).requestFocus();});
        dialog.show();dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(-1,-1);
        dialog.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        render();a.updateRotation();
    }
    void close(){if(dialog!=null)dialog.dismiss();}
    private void back(){
        a.wallpapers.cancelPreview();a.wallpapers.cancelSelection();
        if(page.equals("overview")){close();return;}
        go(page.equals("wallpaper")||page.equals("rotation")?"background":page.startsWith("item:")||page.equals("add")?"home":"overview");
    }
    private void go(String next){a.wallpapers.cancelPreview();a.wallpapers.cancelSelection();page=next;render();}
    private String title(){
        switch(page){case "overview":return "设置";case "background":return "背景";case "wallpaper":return "背景 / 壁纸";case "rotation":return "背景 / 轮播";
            case "layout":return "布局";case "type":return "字体";case "focus":return "选中样式";case "home":return "首页内容";case "add":return "首页内容 / 添加";case "tv":return "电视与启动";
            default:return "首页内容 / "+a.items.name(page.substring(5));}
    }
    private void render(){
        rendering=true;choices.clear();
        LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(a.dp(36),a.dp(25),a.dp(36),a.dp(22));panel.setBackgroundColor(0xFA10151D);
        TextView title=label(title(),26);panel.addView(title,new LinearLayout.LayoutParams(-1,a.dp(48)));
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.HORIZONTAL);panel.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView scroll=new ScrollView(a);scroll.setVerticalScrollBarEnabled(false);
        options=new LinearLayout(a);options.setOrientation(LinearLayout.VERTICAL);options.setPadding(0,0,a.dp(6),a.dp(10));scroll.addView(options,new ScrollView.LayoutParams(-1,-2));
        body.addView(scroll,new LinearLayout.LayoutParams(a.dp(350),-1));
        LinearLayout right=new LinearLayout(a);right.setOrientation(LinearLayout.VERTICAL);right.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);rp.leftMargin=a.dp(36);body.addView(right,rp);
        TextView eyebrow=label("首页预览",13);eyebrow.setTextColor(0xFF99A9BA);right.addView(eyebrow,new LinearLayout.LayoutParams(-1,a.dp(28)));
        previewHost=new FrameLayout(a){@Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);scalePreview(w,h);}};
        right.addView(previewHost,new LinearLayout.LayoutParams(-1,a.dp(270)));
        status=label("✓ 表示已应用 · 高亮表示遥控器焦点",13);status.setTextColor(0xFFB7C3CE);status.setPadding(0,a.dp(16),0,0);right.addView(status,new LinearLayout.LayoutParams(-1,a.dp(70)));
        TextView footer=label("方向键浏览    确定应用    返回上一级",12);footer.setTextColor(0xFF8D9CAC);panel.addView(footer,new LinearLayout.LayoutParams(-1,a.dp(22)));
        switch(page){
            case "overview":
                entry("背景", "壁纸、轮播与纯色",()->go("background"));entry("布局","列表、Dock、图标与文字",()->go("layout"));
                entry("字体","字形与字号",()->go("type"));entry("选中样式","文字高亮与半透明底板",()->go("focus"));
                entry("首页内容","添加、排序、隐藏",()->go("home"));entry("电视与启动","自动进入与小米系统",()->go("tv"));break;
            case "background":
                row("背景","background",new String[]{"照片","浅色","深色"});
                if(a.appearance.get("background")==0){entry("壁纸",a.wallpapers.name(a.wallpapers.selected()),()->go("wallpaper"));entry("轮播",a.wallpapers.rotating()?"每 "+a.wallpapers.minutes()+" 分钟":"固定壁纸",()->go("rotation"));}
                else note("切换回照片背景后，可选择壁纸和轮播。原来的照片与轮播偏好会保留。");break;
            case "layout":
                row("首页布局","layout",new String[]{"纵向列表","横向 Dock"});row("应用显示","apps",new String[]{"文字","图标","图标＋文字"});row("右侧操作","controls",new String[]{"文字","图标","图标＋文字"});
                note("仅显示图标时，聚焦后会显示名称。");break;
            case "type":
                row("字体风格","font",new String[]{"轻盈","标准","衬线"});row("首页字号","size",new String[]{"小","标准","大"});note("使用电视本机字体，中文由系统回退显示。字体应用于首页名称和时钟；设置页保持清晰易读。Dock 使用适合卡片的字号。");break;
            case "focus":
                row("焦点表现","focus",new String[]{"文字高亮","半透明底板"});
                if(a.appearance.get("focus")==1)row("底板质感","material",new String[]{"柔和","浓雾","清透"});else note("启用半透明底板后可调整质感。");
                row("未选中项淡化","dim",new String[]{"轻","标准","明显"});note("底板采用透明度层次，不运行实时模糊。");break;
            case "rotation":
                option("固定壁纸",!a.wallpapers.rotating(),()->{if(a.wallpapers.rotation(false,a.wallpapers.minutes()))render();else failed();});
                for(int m:WallpaperCycle.MINUTES){final int value=m;option("每 "+m+" 分钟",a.wallpapers.rotating()&&a.wallpapers.minutes()==m,()->{
                    if(a.wallpapers.rotation(true,value))render();else failed();});}
                note("只在照片首页显示时计时；设置页、其他应用和纯色背景会暂停轮播。选择壁纸不会改变轮播模式。");break;
            case "wallpaper":gallery();break;
            case "home":
                note("保留 1–9 个入口。选择一项可调整位置或从首页隐藏。");
                for(int i=0;i<a.items.order.size();i++){String id=a.items.order.get(i);entry((i+1)+"  "+a.items.name(id),id.startsWith("hdmi:")?"HDMI "+id.substring(5):"应用",()->go("item:"+id));}
                entry("添加入口","应用或 HDMI 信号源",()->go("add"));if(!undo.isEmpty())entry("撤销上一步","恢复本次编辑前的顺序",this::undo);break;
            case "add":
                a.items.discover();for(String id:a.items.names.keySet())if(!a.items.order.contains(id))entry(a.items.name(id),"添加到首页末尾",()->{
                    if(a.items.order.size()>=9){a.message("首页最多保留 9 个入口");return;}List<String> next=new ArrayList<>(a.items.order);next.add(id);if(save(next))go("home");});break;
            case "tv":
                entry("开机自动进入",BootStart.enabled(a)?"已开启":"已关闭",()->BootStart.show(a));entry("电视系统设置","网络、声音、画面等",a::systemSettings);entry("小米原桌面","打开原来的电视首页",a::stockHome);break;
            default:
                String id=page.substring(5);int pos=a.items.order.indexOf(id);
                if(pos>0)entry("上移","与上一项交换位置",()->move(id,-1));if(pos>=0&&pos<a.items.order.size()-1)entry("下移","与下一项交换位置",()->move(id,1));
                if(a.items.order.size()>1)entry("从首页隐藏","应用仍保留在电视上",()->{List<String> next=new ArrayList<>(a.items.order);next.remove(id);if(save(next))go("home");});
                else note("至少保留一个首页入口。");break;
        }
        dialog.setContentView(panel);refreshPreview();rendering=false;
        if(!choices.isEmpty())choices.get(Math.min(remembered.getOrDefault(page,0),choices.size()-1)).requestFocus();
    }
    private void gallery(){
        for(int i=0;i<a.wallpapers.count();i++){
            final int index=i;
            TextView v=button((i==a.wallpapers.selected()?"✓  ":"    ")+a.wallpapers.name(i),()->{
                if(index==a.wallpapers.selected()){status.setText("已应用这张壁纸");return;}
                status.setText("正在应用壁纸…");String origin=page;
                a.wallpapers.select(index,()->{if(showing()&&page.equals(origin)){render();status.setText("已应用 · "+a.wallpapers.name(index));}});
            });
            android.graphics.drawable.Drawable thumb=a.wallpapers.thumbnail(i);
            if(thumb!=null){thumb.setBounds(0,0,a.dp(64),a.dp(36));v.setCompoundDrawables(thumb,null,null,null);v.setCompoundDrawablePadding(a.dp(10));}
            v.setOnFocusChangeListener((view,focus)->{
                decorate(view,focus);if(!focus)return;remembered.put(page,choices.indexOf(view));
                a.wallpapers.cancelSelection();
                status.setText(index==a.wallpapers.selected()?"已应用 · "+a.wallpapers.name(index):"预览 · 按确定应用 "+a.wallpapers.name(index));
                if(preview!=null)a.wallpapers.preview(index,preview.previewPhoto,()->{});
            });
            options.addView(v,new LinearLayout.LayoutParams(-1,a.dp(46)));
        }
        note("浏览只改变右侧预览。按确定应用，返回保留最后一次已应用的壁纸。");
    }
    private void row(String title,String key,String[] labels){
        heading(title);LinearLayout row=new LinearLayout(a);options.addView(row,new LinearLayout.LayoutParams(-1,a.dp(46)));
        for(int i=0;i<labels.length;i++){
            final int value=i;TextView v=button((a.appearance.get(key)==i?"✓ ":"")+labels[i],()->{
                if(a.appearance.get(key)==value)return;
                if(a.appearance.set(key,value)){a.refreshHome();render();}else failed();
            });v.setTextSize(13);v.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.rightMargin=a.dp(5);row.addView(v,p);
        }
    }
    private void option(String name,boolean selected,Runnable run){entry((selected?"✓  ":"    ")+name,"",run);}
    private void heading(String s){TextView v=label(s,13);v.setTextColor(0xFF9CABBA);v.setPadding(0,a.dp(14),0,a.dp(6));options.addView(v);}
    private void note(String s){TextView v=label(s,13);v.setTextColor(0xFF9CABBA);v.setPadding(0,a.dp(12),a.dp(4),a.dp(12));options.addView(v);}
    private void entry(String name,String subtitle,Runnable run){TextView v=button(name+(subtitle.isEmpty()?"":"\n"+subtitle),run);v.setTextSize(16);options.addView(v,new LinearLayout.LayoutParams(-1,a.dp(subtitle.isEmpty()?44:62)));}
    private TextView button(String text,Runnable run){
        TextView v=label(text,16);v.setPadding(a.dp(12),a.dp(6),a.dp(10),a.dp(6));v.setGravity(Gravity.CENTER_VERTICAL);v.setId(View.generateViewId());v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);choices.add(v);
        v.setOnFocusChangeListener((view,focus)->{decorate(view,focus);if(focus&&!rendering)remembered.put(page,choices.indexOf(view));});
        v.setOnClickListener(view->run.run());v.setOnKeyListener((view,key,e)->(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)&&e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()>0);return v;
    }
    private void decorate(View v,boolean focus){GradientDrawable d=new GradientDrawable();d.setCornerRadius(a.dp(8));d.setColor(focus?0xFF304456:0x00000000);if(focus)d.setStroke(a.dp(1),0xFF91B8D1);v.setBackground(d);}
    private TextView label(String s,int size){TextView v=new TextView(a);v.setText(s);v.setTextSize(size);v.setTextColor(Color.WHITE);v.setIncludeFontPadding(false);return v;}
    private void refreshPreview(){
        preview=a.preview();previewHost.removeAllViews();previewHost.addView(preview,new FrameLayout.LayoutParams(a.dp(960),a.dp(540)));
        preview.setPivotX(0);preview.setPivotY(0);scalePreview(previewHost.getWidth(),previewHost.getHeight());
        preview.clock.setText(new java.text.SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date()));
    }
    private void scalePreview(int w,int h){if(preview!=null){float scale=Math.min(w/(float)a.dp(960),h/(float)a.dp(540));preview.setScaleX(scale);preview.setScaleY(scale);}}
    private boolean save(List<String> next){List<String> old=new ArrayList<>(a.items.order);if(!a.items.save(next)){failed();return false;}undo.push(old);a.refreshHome();return true;}
    private void move(String id,int direction){List<String> next=new ArrayList<>(a.items.order);int index=next.indexOf(id);Collections.swap(next,index,index+direction);if(save(next))render();}
    private void undo(){if(undo.isEmpty())return;if(a.items.save(undo.peek())){undo.pop();a.refreshHome();render();}else failed();}
    private void failed(){a.message("保存失败，保留原设置，请重试");}
}
