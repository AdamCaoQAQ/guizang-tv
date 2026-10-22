package io.github.op7418.guizangtv;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.util.Random;

/** Native backdrop blur + tint + inset hairline, adapted from Libraries.dev's frosted controls.
 * See assets/licenses/LIBRARIES-DEV-MIT.txt and docs/BEAM-ANDROID.md. */
final class AssistantGlass extends Drawable {
    private final AssistantBackdrop backdrop;
    private final View host;
    private final boolean light;
    private final float radius,stroke;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Path clip=new Path();
    private final RectF shape=new RectF(),desktop=new RectF(),edge=new RectF();
    private final ColorMatrixColorFilter muted;
    private final BitmapShader grain;
    private Shader fill,hairline;
    private int alpha=255;
    private static final Bitmap GRAIN=grainTile();
    AssistantGlass(AssistantBackdrop backdrop,View host,boolean light,float radius){
        this.backdrop=backdrop;this.host=host;this.light=light;this.radius=radius;
        stroke=host.getResources().getDisplayMetrics().density*.75f;
        ColorMatrix color=new ColorMatrix();color.setSaturation(.28f);muted=new ColorMatrixColorFilter(color);
        grain=new BitmapShader(GRAIN,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT);
    }
    private static Bitmap grainTile(){
        Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);int[] pixels=new int[64*64];Random random=new Random(7418);
        for(int i=0;i<pixels.length;i++)pixels[i]=(random.nextInt(7)<<24)|(random.nextBoolean()?0xFFFFFF:0);
        b.setPixels(pixels,0,64,0,0,64,64);return b;
    }
    @Override protected void onBoundsChange(Rect bounds){
        shape.set(bounds);clip.reset();clip.addRoundRect(shape,radius,radius,Path.Direction.CW);
        edge.set(shape);edge.inset(stroke/2,stroke/2);
        fill=new LinearGradient(0,shape.top,0,Math.max(shape.top+1,shape.bottom),
            light?0xDFF9FAFC:0xD51B1D22,light?0xD3ECEEF2:0xE0080A0D,Shader.TileMode.CLAMP);
        hairline=new LinearGradient(0,shape.top,0,Math.max(shape.top+1,shape.bottom),
            light?new int[]{0xC4FFFFFF,0x52FFFFFF,0x24000000}:new int[]{0x66FFFFFF,0x28FFFFFF,0x16FFFFFF},
            new float[]{0,.48f,1},Shader.TileMode.CLAMP);
    }
    @Override public void draw(Canvas c){
        int layer=alpha<255?c.saveLayerAlpha(shape,alpha):c.save();c.clipPath(clip);
        Bitmap field=backdrop.frostedField();
        paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setAlpha(255);
        if(field!=null){
            float x=0,y=0;View child=host;
            while(child.getParent() instanceof View){
                View parent=(View)child.getParent();x+=child.getLeft()+child.getTranslationX()-parent.getScrollX();y+=child.getTop()+child.getTranslationY()-parent.getScrollY();
                if(parent==backdrop.getParent())break;child=parent;
            }
            desktop.set(-x,-y,backdrop.getWidth()-x,backdrop.desktopHeight()-y);
            paint.setColorFilter(muted);c.drawBitmap(field,null,desktop,paint);paint.setColorFilter(null);
        }else{paint.setColor(light?0xFFF2F3F6:0xFF16181D);c.drawRect(shape,paint);}
        paint.setShader(fill);c.drawRect(shape,paint);
        paint.setShader(grain);c.drawRect(shape,paint);
        paint.setShader(hairline);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);
        c.drawRoundRect(edge,Math.max(0,radius-stroke/2),Math.max(0,radius-stroke/2),paint);
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);c.restoreToCount(layer);
    }
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
