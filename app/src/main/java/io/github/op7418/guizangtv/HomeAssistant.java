package io.github.op7418.guizangtv;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import javax.net.ssl.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.*;
import java.io.*;
import java.util.concurrent.*;

/** Opt-in chat, presented as a transparent input overlay on the existing home. */
final class HomeAssistant {
    private final FrameHomeActivity a;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Dialog dialog;
    private FrameLayout stage;
    private LinearLayout body,messages;
    private TextView status;
    private ImageView send;
    private EditText input;
    private ScrollView historyScroll;
    private AssistantComposer composer;
    private boolean busy,listening,inFlight,pairing;
    private boolean active=true;
    private int generation;
    private String lastDraft="",lastTranscript="";
    private final Runnable poll=()->request("/state",null);
    HomeAssistant(FrameHomeActivity activity){a=activity;prefs=a.getSharedPreferences("codex-companion",0);}
    static boolean enabled(android.content.Context context){return context.getSharedPreferences("codex-companion",0).getBoolean("enabled",false);}
    boolean enabled(boolean value){
        if(!prefs.edit().putBoolean("enabled",value).commit())return false;
        if(!value){main.removeCallbacks(poll);close();}return true;
    }
    boolean showing(){return dialog!=null&&dialog.isShowing();}
    void show(){
        if(!enabled(a)||showing())return;
        View home=a.getWindow().getDecorView();
        int w=Math.max(1,home.getWidth()),h=Math.max(1,home.getHeight());
        Bitmap snapshot=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(snapshot);canvas.scale(snapshot.getWidth()/(float)w,snapshot.getHeight()/(float)h);home.draw(canvas);
        dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setOnDismissListener(d->{
            generation++;main.removeCallbacks(poll);if(composer!=null)composer.active(false);
            a.updateRotation();a.surface.controls.get(Math.min(3,a.surface.controls.size()-1)).requestFocus();
        });
        stage=new FrameLayout(a);
        stage.addView(new AssistantBackdrop(a,snapshot),new FrameLayout.LayoutParams(-1,-1));
        stage.setOnClickListener(v->close());
        stage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->sizeBody());
        dialog.setContentView(stage);dialog.show();
        Window window=dialog.getWindow();window.setBackgroundDrawableResource(android.R.color.transparent);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND|WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setDimAmount(0);window.setLayout(-1,-1);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        if(prefs.contains("connection"))chat();else pair();a.updateRotation();
        body.setAlpha(0);body.setTranslationY(a.dp(18));body.animate().alpha(1).translationY(0).setDuration(220).start();
    }
    void active(boolean value){
        active=value;if(composer!=null)composer.active(value&&showing());
        main.removeCallbacks(poll);if(value&&enabled(a)&&showing()&&!pairing)main.post(poll);
    }
    void close(){if(dialog!=null){if(input!=null)hideKeyboard(input);dialog.dismiss();}}
    void destroy(){close();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
    private void layout(){
        if(composer!=null)composer.active(false);
        if(body!=null&&body.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)body.getParent()).removeView(body);
        body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(a.dp(12),0,a.dp(12),0);
        body.setClickable(true);stage.addView(body,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL));
        stage.post(this::sizeBody);
    }
    private void sizeBody(){
        if(body==null||stage.getWidth()==0||stage.getHeight()==0)return;
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)body.getLayoutParams();
        int width=Math.min(a.dp(800),stage.getWidth()-a.dp(56));
        int height=pairing?Math.min(a.dp(300),stage.getHeight()-a.dp(32)):Math.min(a.dp(330),stage.getHeight()-a.dp(24));
        int bottom=stage.getHeight()<a.dp(420)?a.dp(8):a.dp(26);
        if(p.width!=width||p.height!=height||p.bottomMargin!=bottom){p.width=width;p.height=height;p.bottomMargin=bottom;body.setLayoutParams(p);}
    }
    private void pair(){
        pairing=true;generation++;main.removeCallbacks(poll);layout();
        body.setPadding(a.dp(28),a.dp(14),a.dp(28),a.dp(10));
        body.addView(label("连接电脑上的 Codex",24));
        TextView help=label("启动电脑连接服务，将配对码填入这里。",15);help.setPadding(0,a.dp(12),0,a.dp(16));body.addView(help);
        EditText code=new EditText(a);code.setTextColor(0xFFF4F6FA);code.setTextSize(17);code.setHintTextColor(0x88FFFFFF);code.setHint("粘贴 gtv1 配对码");
        code.setSingleLine(true);code.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        code.setPadding(a.dp(18),0,a.dp(18),0);code.setBackground(WallpaperMaterial.rounded(0xEF1D2633,a.dp(26)));body.addView(code,new LinearLayout.LayoutParams(-1,a.dp(54)));
        TextView message=label("配对码仅保存在此设备，请勿分享。",12);message.setPadding(0,a.dp(12),0,a.dp(12));body.addView(message);
        LinearLayout row=new LinearLayout(a);body.addView(row);
        button(row,"连接",()->{
            try{String value=code.getText().toString().trim();parse(value);prefs.edit().putString("connection",value).apply();hideKeyboard(code);chat();}
            catch(Exception e){message.setText("配对码无效，请填入完整 gtv1 配对码。");}
        });
        button(row,"返回",()->{hideKeyboard(code);if(prefs.contains("connection"))chat();else close();});code.requestFocus();
    }
    private void chat(){
        pairing=false;generation++;main.removeCallbacks(poll);lastDraft="";lastTranscript="";layout();
        historyScroll=new ScrollView(a);historyScroll.setFillViewport(true);historyScroll.setClipToPadding(false);
        historyScroll.setVerticalFadingEdgeEnabled(true);historyScroll.setFadingEdgeLength(a.dp(24));
        historyScroll.setVerticalScrollBarEnabled(false);historyScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        historyScroll.setFocusable(true);historyScroll.setContentDescription("对话记录，上下滚动");
        messages=new LinearLayout(a);messages.setOrientation(LinearLayout.VERTICAL);messages.setGravity(Gravity.BOTTOM|Gravity.LEFT);
        messages.setPadding(a.dp(10),a.dp(12),a.dp(10),a.dp(10));
        historyScroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if(b-t!=ob-ot && messages.getHeight()-historyScroll.getScrollY()-(ob-ot)<=a.dp(24)){
                ScrollView target=historyScroll;LinearLayout content=messages;int page=generation;
                target.post(()->{if(page==generation&&target==historyScroll)target.scrollTo(0,content.getBottom());});
            }
        });
        historyScroll.addView(messages,new ScrollView.LayoutParams(-1,-2));body.addView(historyScroll,new LinearLayout.LayoutParams(-1,0,1));
        composer=new AssistantComposer(a);body.addView(composer,new LinearLayout.LayoutParams(-1,a.dp(82)));
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(a.dp(18),0,a.dp(18),0);
        FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(-1,a.dp(58),Gravity.TOP);rp.topMargin=a.dp(7);composer.addView(row,rp);
        ImageView mic=icon(row,R.drawable.mark_microphone,"键盘 / 语音",this::showKeyboard,false);
        input=new EditText(a);input.setTextColor(0xFFF4F6FA);input.setHintTextColor(0x92DDE5F4);input.setTextSize(18);
        input.setHint("按住语音键说话，或输入问题…");input.setSingleLine(true);input.setBackgroundColor(Color.TRANSPARENT);input.setPadding(a.dp(12),0,a.dp(12),0);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        row.addView(input,new LinearLayout.LayoutParams(0,-1,1));
        send=icon(row,R.drawable.mark_arrow_up,"发送",()->{if(busy)request("/cancel",new JSONObject());else submit();},true);
        input.setOnEditorActionListener((v,action,event)->{hideKeyboard(input);send.requestFocus();return true;});
        LinearLayout footer=new LinearLayout(a);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(a.dp(12),0,a.dp(12),0);
        body.addView(footer,new LinearLayout.LayoutParams(-1,a.dp(32)));
        status=label("正在连接电脑…",12);status.setTextColor(0xB3E6EAF3);status.setMaxLines(2);footer.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        button(footer,"新对话",()->request("/reset",new JSONObject()));button(footer,"连接",this::pair);button(footer,"收起",this::close);
        input.requestFocus();request("/state",null);
        int page=generation;input.postDelayed(()->{if(page==generation&&showing()&&!pairing&&active)showKeyboard();},260);
    }
    private ImageView icon(LinearLayout parent,int resource,String name,Runnable action,boolean primary){
        ImageView v=new ImageView(a);v.setImageResource(resource);v.setColorFilter(primary?0xFF172033:0xCCDCE7F8);v.setContentDescription(name);
        v.setPadding(a.dp(11),a.dp(11),a.dp(11),a.dp(11));v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);
        v.setBackground(WallpaperMaterial.rounded(primary?0xE6E9EEF8:0x00FFFFFF,a.dp(23)));
        v.setOnFocusChangeListener((view,focus)->v.setBackground(WallpaperMaterial.rounded(focus?0xFFB8D7FE:primary?0xE6E9EEF8:0x00FFFFFF,a.dp(23))));
        v.setOnClickListener(view->action.run());parent.addView(v,new LinearLayout.LayoutParams(a.dp(44),a.dp(44)));return v;
    }
    private void submit(){
        if(busy||listening)return;
        String text=input.getText().toString().trim();if(text.isEmpty())return;
        JSONObject data=new JSONObject();try{data.put("text",text);}catch(JSONException ignored){}
        hideKeyboard(input);request("/message",data);
    }
    private void showKeyboard(){
        if(input==null)return;EditText target=input;int page=generation;target.requestFocus();
        target.post(()->{if(showing()&&!pairing&&active&&page==generation)
            ((InputMethodManager)a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(target,InputMethodManager.SHOW_IMPLICIT);});
    }
    private void hideKeyboard(View view){((InputMethodManager)a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(),0);}
    private void request(String path,JSONObject body){
        if(!enabled(a)||!showing()||pairing||!active)return;
        if(inFlight){if(path.equals("/state"))main.postDelayed(poll,700);else {int pending=generation;main.postDelayed(()->{if(pending==generation)request(path,body);},200);}return;}
        inFlight=true;main.removeCallbacks(poll);int request=generation;String connection=prefs.getString("connection","");
        worker.execute(()->{
            JSONObject result=null;String error=null;
            try{result=call(connection,path,body);}catch(Exception e){error="未连接电脑 · 请检查连接服务或配对";}
            JSONObject data=result;String failure=error;
            main.post(()->{
                inFlight=false;
                if(!enabled(a)||!showing()||pairing||!active||request!=generation){if(enabled(a)&&showing()&&!pairing&&active)main.postDelayed(poll,100);return;}
                if(failure!=null){status.setText(failure);busy=false;send.setImageResource(R.drawable.mark_arrow_up);send.setContentDescription("发送");}
                else if(data.has("error")&&!data.has("messages"))status.setText(data.optString("error"));
                else {render(data,path.equals("/message"));if(path.equals("/message"))input.setText("");}
                main.postDelayed(poll,failure==null?1000:5000);
            });
        });
    }
    private void render(JSONObject data,boolean sent){
        busy=data.optBoolean("busy");listening=data.optBoolean("listening");
        send.setImageResource(busy?R.drawable.mark_stop:R.drawable.mark_arrow_up);send.setContentDescription(busy?"停止":"发送");
        String error=data.optString("error");status.setText(!error.isEmpty()?error:listening?"正在输入…":busy?"正在回答…":"已连接");
        String draft=data.optString("draft");if(!draft.isEmpty()&&!draft.equals(lastDraft)){lastDraft=draft;input.setText(draft);send.requestFocus();}
        JSONArray history=data.optJSONArray("messages");String delta=data.optString("reply");
        String value=String.valueOf(history)+"\n"+delta;
        if(!value.equals(lastTranscript)){
            boolean follow=lastTranscript.isEmpty()||sent||!historyScroll.canScrollVertically(1);lastTranscript=value;
            int count=0;
            if(history!=null)for(int i=0;i<history.length();i++){
                JSONObject m=history.optJSONObject(i);if(m!=null){bubble(count++,m.optString("text"),m.optString("role").equals("user"));}
            }
            if(!delta.isEmpty())bubble(count++,delta,false);
            while(messages.getChildCount()>count)messages.removeViewAt(messages.getChildCount()-1);
            if(follow){ScrollView target=historyScroll;LinearLayout content=messages;int page=generation;
                target.post(()->{if(showing()&&!pairing&&page==generation&&target==historyScroll)target.scrollTo(0,content.getBottom());});}
        }
    }
    private void bubble(int index,String value,boolean own){
        TextView v;
        if(index<messages.getChildCount())v=(TextView)messages.getChildAt(index);
        else{
            v=label("",18);v.setLineSpacing(a.dp(3),1);v.setPadding(a.dp(18),a.dp(12),a.dp(18),a.dp(12));
            v.setMaxWidth(a.dp(660));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.gravity=Gravity.LEFT;p.bottomMargin=a.dp(10);messages.addView(v,p);
        }
        if(!v.getText().toString().equals(value))v.setText(value);
        v.setTextColor(own?0xFFF0F5FC:0xFF242A34);v.setBackground(WallpaperMaterial.rounded(own?0xEC203D59:0xEEF0F2F7,a.dp(24)));
        v.setContentDescription((own?"你的消息：":"助手回复：")+value);
    }
    private TextView label(String value,int size){TextView v=new TextView(a);v.setText(value);v.setTextSize(size);v.setTextColor(0xFFF5F6F8);v.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));return v;}
    private TextView button(LinearLayout row,String value,Runnable run){
        TextView v=label(value,12);v.setGravity(Gravity.CENTER);v.setPadding(a.dp(12),a.dp(5),a.dp(12),a.dp(5));v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);
        v.setBackground(WallpaperMaterial.rounded(0x00FFFFFF,a.dp(16)));v.setOnFocusChangeListener((w,f)->w.setBackground(WallpaperMaterial.rounded(f?0x36FFFFFF:0x00FFFFFF,a.dp(16))));v.setOnClickListener(w->run.run());
        row.addView(v,new LinearLayout.LayoutParams(-2,-2));return v;
    }
    static JSONObject parse(String value)throws Exception{
        if(!value.startsWith("gtv1:")||value.length()>4096)throw new IOException();
        JSONObject result=new JSONObject(new String(Base64.decode(value.substring(5),Base64.URL_SAFE),StandardCharsets.UTF_8));
        URL url=new URL(result.getString("url"));String host=url.getHost();
        if(!url.getProtocol().equals("https")||url.getUserInfo()!=null||!url.getPath().isEmpty()||url.getQuery()!=null||url.getRef()!=null)throw new IOException();
        if(!host.matches("[0-9.]+"))throw new IOException();
        java.net.InetAddress ip=java.net.InetAddress.getByName(host);if(!ip.isSiteLocalAddress()&&!ip.isLoopbackAddress())throw new IOException();
        if(!result.getString("pin").matches("[a-f0-9]{64}")||!result.getString("token").matches("[A-Za-z0-9_-]{32,128}"))throw new IOException();
        return result;
    }
    static JSONObject call(String value,String path,JSONObject body)throws Exception{
        JSONObject conf=parse(value);String pin=conf.getString("pin");
        X509TrustManager trust=new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] c,String type)throws CertificateException{throw new CertificateException();}
            public void checkServerTrusted(X509Certificate[] chain,String type)throws CertificateException{
                try{if(chain.length==0)throw new CertificateException();chain[0].checkValidity();byte[] hash=MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());StringBuilder hex=new StringBuilder();for(byte b:hash)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!MessageDigest.isEqual(hex.toString().getBytes(StandardCharsets.US_ASCII),pin.getBytes(StandardCharsets.US_ASCII)))throw new CertificateException();}
                catch(Exception e){throw new CertificateException("配对证书不匹配",e);}
            }
        };
        SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(null,new TrustManager[]{trust},null);
        HttpsURLConnection c=(HttpsURLConnection)new URL(conf.getString("url")+path).openConnection();
        c.setSSLSocketFactory(ssl.getSocketFactory());c.setInstanceFollowRedirects(false);c.setConnectTimeout(6000);c.setReadTimeout(10000);c.setRequestProperty("Authorization","Bearer "+conf.getString("token"));
        try{
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream o=c.getOutputStream()){o.write(bytes);}}
            int status=c.getResponseCode();InputStream stream=status>=400?c.getErrorStream():c.getInputStream();if(stream==null)throw new IOException();
            try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>256000)throw new IOException("响应过大");out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}
        }finally{c.disconnect();}
    }
}
