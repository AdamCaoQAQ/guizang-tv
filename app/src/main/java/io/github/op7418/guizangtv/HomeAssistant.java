package io.github.op7418.guizangtv;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

/** Optional paired chat panel. The TV never contains an OpenAI account token. */
final class HomeAssistant {
    private final FrameHomeActivity a;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Dialog dialog;
    private FrameLayout stage;
    private LinearLayout body,actions;
    private TextView status,transcript,send,voice;
    private EditText input;
    private ScrollView historyScroll;
    private boolean busy,listening,inFlight,pairing;
    private boolean active=true;
    private int generation;
    private String lastDraft="",lastTranscript="";
    private final Runnable poll=()->request("/state",null);
    HomeAssistant(FrameHomeActivity activity){a=activity;prefs=a.getSharedPreferences("codex-companion",0);}
    boolean showing(){return dialog!=null&&dialog.isShowing();}
    void show(){
        if(showing())return;
        dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setOnDismissListener(d->{generation++;main.removeCallbacks(poll);a.updateRotation();a.surface.controls.get(3).requestFocus();});
        stage=new FrameLayout(a);stage.setBackgroundColor(0xFF11151A);
        ImageView backdrop=new ImageView(a);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        stage.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        WallpaperMaterial.request(a.wallpaperDrawable(),42,b->backdrop.setImageBitmap(b));
        View shade=new View(a);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xD5080C12,0xB0080C12}));stage.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        dialog.setContentView(stage);dialog.show();dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(-1,-1);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.getWindow().getDecorView().setSystemUiVisibility(5894);
        if(prefs.contains("connection"))chat();else pair();a.updateRotation();
    }
    void active(boolean value){active=value;main.removeCallbacks(poll);if(value&&showing()&&!pairing)main.post(poll);}
    void close(){if(dialog!=null)dialog.dismiss();}
    void destroy(){close();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
    private LinearLayout layout(){
        if(body!=null)stage.removeView(body);
        body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(a.dp(48),a.dp(32),a.dp(48),a.dp(28));
        stage.addView(body,new FrameLayout.LayoutParams(-1,-1));return body;
    }
    private void pair(){
        pairing=true;generation++;main.removeCallbacks(poll);layout();
        body.addView(label("连接 Codex",30));
        TextView help=label("在电脑上启动归藏 TV 连接服务，将配对码填入这里。\n电脑需要与电视连接同一网络，并保持开机。",17);
        help.setPadding(0,a.dp(22),0,a.dp(24));body.addView(help);
        EditText code=editor("粘贴电脑生成的配对码");code.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(code,new LinearLayout.LayoutParams(-1,a.dp(62)));
        TextView message=label("配对码只保存在这台电视。请勿分享给他人。",13);message.setPadding(0,a.dp(18),0,a.dp(22));body.addView(message);
        LinearLayout row=new LinearLayout(a);body.addView(row);
        button(row,"连接",()->{
            try{String value=code.getText().toString().trim();parse(value);prefs.edit().putString("connection",value).apply();hideKeyboard(code);chat();}
            catch(Exception e){message.setText("配对码无效，请使用电脑生成的完整 gtv1 配对码。");}
        });
        button(row,"返回",()->{if(prefs.contains("connection"))chat();else close();});code.requestFocus();
    }
    private void chat(){
        pairing=false;generation++;main.removeCallbacks(poll);lastDraft="";lastTranscript="";layout();
        LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);body.addView(header,new LinearLayout.LayoutParams(-1,a.dp(48)));
        TextView title=label("Codex",30);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        button(header,"新对话",()->request("/reset",new JSONObject()));button(header,"连接设置",this::pair);button(header,"关闭",this::close);
        status=label("正在连接电脑…",13);status.setTextColor(0xAAFFFFFF);status.setPadding(0,a.dp(8),0,a.dp(12));body.addView(status);
        ScrollView scroll=new ScrollView(a);historyScroll=scroll;scroll.setFillViewport(true);scroll.setPadding(a.dp(24),a.dp(20),a.dp(24),a.dp(20));
        scroll.setBackground(WallpaperMaterial.rounded(0x30232A34,a.dp(20)));scroll.setFocusable(true);
        transcript=label("有什么想聊的？\n\n可以请我解释一个概念、帮你比较选择，或者一起整理想法。",19);transcript.setLineSpacing(a.dp(7),1);
        scroll.addView(transcript,new ScrollView.LayoutParams(-1,-2));body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout compose=new LinearLayout(a);compose.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,a.dp(66));cp.topMargin=a.dp(14);body.addView(compose,cp);
        input=editor("输入问题…");input.setSingleLine(true);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        compose.addView(input,new LinearLayout.LayoutParams(0,a.dp(54),1));
        send=button(compose,"发送",()->{if(busy){request("/cancel",new JSONObject());return;}submit();});
        // An IME may emit SEND even when DONE was requested. Only the explicit button submits.
        input.setOnEditorActionListener((v,action,event)->{
            hideKeyboard(input);send.requestFocus();return true;
        });
        actions=new LinearLayout(a);actions.setGravity(Gravity.CENTER_VERTICAL);body.addView(actions,new LinearLayout.LayoutParams(-1,a.dp(38)));
        button(actions,"键盘 / 语音",this::showKeyboard);
        voice=button(actions,"本机语音 · 实验",()->request("/voice",new JSONObject()));voice.setVisibility(View.GONE);
        TextView foot=label("先打开键盘，再按语音键 · 需输入法支持 · 返回后确认发送",12);foot.setTextColor(0x88FFFFFF);actions.addView(foot);
        input.requestFocus();request("/state",null);
    }
    private void submit(){
        if(busy||listening)return;
        String text=input.getText().toString().trim();if(text.isEmpty())return;
        JSONObject data=new JSONObject();try{data.put("text",text);}catch(JSONException ignored){}
        hideKeyboard(input);request("/message",data);
    }
    private void showKeyboard(){
        input.requestFocus();
        input.post(()->{
            if(showing()&&!pairing&&active)
                ((InputMethodManager)a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                    .showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);
        });
    }
    private void hideKeyboard(View view){((InputMethodManager)a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(),0);}
    private void request(String path,JSONObject body){
        if(!showing()||pairing||!active)return;
        if(inFlight){if(path.equals("/state"))main.postDelayed(poll,700);else {int pending=generation;main.postDelayed(()->{if(pending==generation)request(path,body);},200);}return;}
        inFlight=true;main.removeCallbacks(poll);int request=generation;
        String connection=prefs.getString("connection","");
        worker.execute(()->{
            JSONObject result=null;String error=null;
            try{result=call(connection,path,body);}catch(Exception e){error="未连接到电脑，请确认电脑开机、连接服务正在运行，或检查配对设置。";}
            JSONObject data=result;String failure=error;
            main.post(()->{
                inFlight=false;
                if(!showing()||pairing||!active||request!=generation){if(showing()&&!pairing&&active)main.postDelayed(poll,100);return;}
                if(failure!=null){status.setText(failure);busy=false;send.setText("发送");}
                else if(data.has("error")&&!data.has("messages"))status.setText(data.optString("error"));
                else {render(data,path.equals("/message"));if(path.equals("/message"))input.setText("");}
                main.postDelayed(poll,failure==null?1000:5000);
            });
        });
    }
    private void render(JSONObject data,boolean sent){
        busy=data.optBoolean("busy");listening=data.optBoolean("listening");send.setText(busy?"停止":"发送");
        voice.setVisibility(data.optBoolean("voiceAvailable")?View.VISIBLE:View.GONE);
        String error=data.optString("error");
        status.setText(!error.isEmpty()?error:listening?"请按住遥控器语音键说话，返回后确认发送。小爱仍可能回答或执行口令。":busy?"Codex 正在回答…":"已连接电脑 · Codex");
        String draft=data.optString("draft");if(!draft.isEmpty()&&!draft.equals(lastDraft)){lastDraft=draft;input.setText(draft);send.requestFocus();}
        StringBuilder text=new StringBuilder();JSONArray history=data.optJSONArray("messages");
        if(history!=null)for(int i=0;i<history.length();i++){JSONObject m=history.optJSONObject(i);if(m==null)continue;text.append(m.optString("role").equals("user")?"你\n":"Codex\n").append(m.optString("text")).append("\n\n");}
        String delta=data.optString("reply");if(!delta.isEmpty())text.append("Codex\n").append(delta);
        String value=text.length()==0?"有什么想聊的？\n\n可以请我解释一个概念、帮你比较选择，或者一起整理想法。":text.toString().trim();
        if(!value.equals(lastTranscript)){
            // Follow a new conversation and our own sends; respect scrolling back through history.
            boolean follow=lastTranscript.isEmpty()||sent||!historyScroll.canScrollVertically(1);
            lastTranscript=value;transcript.setText(value);
            if(follow){
                ScrollView target=historyScroll;TextView content=transcript;int page=generation;
                target.post(()->{
                    if(showing()&&!pairing&&page==generation&&target==historyScroll)
                        target.scrollTo(0,content.getBottom()); // Clamp to bottom without stealing remote focus.
                });
            }
        }
    }
    private EditText editor(String hint){EditText v=new EditText(a);v.setTextColor(0xFFFFFFFF);v.setHintTextColor(0x88FFFFFF);v.setTextSize(17);v.setHint(hint);v.setPadding(a.dp(18),a.dp(10),a.dp(18),a.dp(10));v.setBackground(WallpaperMaterial.rounded(0x24FFFFFF,a.dp(14)));return v;}
    private TextView label(String value,int size){TextView v=new TextView(a);v.setText(value);v.setTextSize(size);v.setTextColor(0xFFF5F6F8);v.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));return v;}
    private TextView button(LinearLayout row,String value,Runnable run){
        TextView v=label(value,15);v.setGravity(Gravity.CENTER);v.setPadding(a.dp(16),a.dp(8),a.dp(16),a.dp(8));v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);
        v.setBackground(WallpaperMaterial.rounded(0x10FFFFFF,a.dp(12)));v.setOnFocusChangeListener((w,f)->w.setBackground(WallpaperMaterial.rounded(f?0x42FFFFFF:0x10FFFFFF,a.dp(12))));v.setOnClickListener(w->run.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=a.dp(8);row.addView(v,lp);return v;
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
