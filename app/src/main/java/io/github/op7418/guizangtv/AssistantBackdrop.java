package io.github.op7418.guizangtv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.graphics.drawable.Drawable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Two cached Gaussian fields, masked progressively over the actual home snapshot. */
final class AssistantBackdrop extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private Bitmap soft,strong,frosted;
    private final ArrayList<WeakReference<View>> materialHosts=new ArrayList<>();
    private final Bitmap snapshot;
    private final int sourceWidth,sourceHeight;
    AssistantBackdrop(Context context,Bitmap snapshot){
        super(context);this.snapshot=snapshot;sourceWidth=snapshot.getWidth();sourceHeight=snapshot.getHeight();
        BitmapDrawable source=new BitmapDrawable(getResources(),snapshot);
        WallpaperMaterial.request(source,22,b->{soft=b;invalidate();});
        WallpaperMaterial.request(source,64,b->{strong=b;invalidate();});
        WallpaperMaterial.request(source,90,b->{
            frosted=b;invalidateMaterials();
        });
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    Drawable glass(View host,boolean light,float radius){
        materialHosts.add(new WeakReference<>(host));return new AssistantGlass(this,host,light,radius);
    }
    void invalidateMaterials(){
        for(int i=materialHosts.size()-1;i>=0;i--){
            View host=materialHosts.get(i).get();if(host==null)materialHosts.remove(i);else host.invalidate();
        }
    }
    Bitmap frostedField(){return frosted;}
    float desktopHeight(){return getWidth()*sourceHeight/(float)sourceWidth;}
    @Override protected void onDraw(Canvas canvas){
        float h=getHeight(),w=getWidth();
        // Keep the desktop registered to the screen even when the IME shrinks this window.
        RectF desktop=new RectF(0,0,w,w*sourceHeight/sourceWidth);
        // Freeze the sharp home too: Android may resize the activity behind the dialog for the IME.
        canvas.drawBitmap(snapshot,null,desktop,paint);
        field(canvas,soft,desktop,h*.16f,h*.66f);
        field(canvas,strong,desktop,h*.38f,h*.92f);
        paint.setShader(new LinearGradient(0,h*.22f,0,h,new int[]{0x00080B12,0x14080B12,0xA6080B12},new float[]{0,.45f,1},Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
    }
    private void field(Canvas canvas,Bitmap bitmap,RectF desktop,float start,float end){
        if(bitmap==null)return;
        int layer=canvas.saveLayer(0,0,getWidth(),getHeight(),null);
        paint.setShader(null);canvas.drawBitmap(bitmap,null,desktop,paint);
        paint.setShader(new LinearGradient(0,start,0,end,0x00FFFFFF,0xFFFFFFFF,Shader.TileMode.CLAMP));
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawRect(0,0,getWidth(),getHeight(),paint);
        paint.setXfermode(null);paint.setShader(null);canvas.restoreToCount(layer);
    }
}
