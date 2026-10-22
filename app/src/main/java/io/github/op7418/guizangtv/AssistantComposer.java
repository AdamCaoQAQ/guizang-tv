package io.github.op7418.guizangtv;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.widget.FrameLayout;

/** Original native rendering inspired by Border Beam; only the lower rim emits light. */
final class AssistantComposer extends FrameLayout {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shell=new RectF();
    private boolean running=true;
    private Bitmap glowMask;
    private final PorterDuffXfermode maskMode=new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private final float density;
    AssistantComposer(Context context){super(context);density=getResources().getDisplayMetrics().density;setWillNotDraw(false);setClipChildren(false);}
    void active(boolean active){running=active;invalidate();}
    private float dp(float n){return n*density;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        if(w<=0||h<=0)return;
        // Blur the alpha mask once on a software canvas; animation only recolors this cached field.
        glowMask=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas mask=new Canvas(glowMask);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF bounds=new RectF(dp(9),dp(6),w-dp(9),h-dp(14));
        p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(6));
        p.setMaskFilter(new BlurMaskFilter(dp(11),BlurMaskFilter.Blur.NORMAL));mask.drawRoundRect(bounds,dp(30),dp(30),p);
        p.setAlpha(180);p.setStrokeWidth(dp(2));p.setMaskFilter(new BlurMaskFilter(dp(3),BlurMaskFilter.Blur.NORMAL));mask.drawRoundRect(bounds,dp(30),dp(30),p);
        p.setMaskFilter(null);p.setStyle(Paint.Style.FILL);p.setAlpha(255);
        p.setShader(new LinearGradient(0,h*.60f,0,h*.86f,Color.TRANSPARENT,Color.WHITE,Shader.TileMode.CLAMP));
        p.setXfermode(maskMode);mask.drawRect(0,0,w,h,p);
    }
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),radius=dp(30);
        shell.set(dp(9),dp(6),w-dp(9),h-dp(14));
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xF0191D26);c.drawRoundRect(shell,radius,radius,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(.6f));paint.setColor(0x26FFFFFF);c.drawRoundRect(shell,radius,radius,paint);
        float phase=(SystemClock.uptimeMillis()%9000)/9000f;
        float center=w*(.5f+.23f*(float)Math.sin(phase*Math.PI*2));
        paint.setShader(new LinearGradient(center-w*.58f,0,center+w*.58f,0,
            new int[]{0x00227BD0,0xA85CB8F5,0xBA8A7DF1,0xB9E499CC,0xCDE9B380,0x004B8EDD},
            new float[]{0,.19f,.39f,.60f,.79f,1},Shader.TileMode.CLAMP));
        Shader colors=paint.getShader();paint.setAlpha(255);
        c.save();c.clipRect(0,h*.60f,w,h);
        if(glowMask!=null){
            int layer=c.saveLayer(0,h*.60f,w,h,null);
            paint.setStyle(Paint.Style.FILL);c.drawRect(0,h*.60f,w,h,paint);
            paint.setShader(null);paint.setXfermode(maskMode);c.drawBitmap(glowMask,0,0,paint);
            paint.setXfermode(null);c.restoreToCount(layer);
        }
        paint.setShader(colors);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.2f));paint.setAlpha(190);
        c.drawRoundRect(shell,radius,radius,paint);
        c.restore();paint.setShader(null);paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);
        if(running&&isShown())postInvalidateDelayed(33);
    }
    @Override protected void onDetachedFromWindow(){running=false;super.onDetachedFromWindow();}
}
