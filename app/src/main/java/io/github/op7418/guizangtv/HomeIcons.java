package io.github.op7418.guizangtv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;

/** Installed app icons plus original line drawings for sources and controls. */
final class HomeIcons {
    static Drawable get(Context context, String id, int color) {
        if (!id.startsWith("hdmi:") && !id.startsWith("control:")) {
            try { return context.getPackageManager().getApplicationIcon(id); }
            catch (Exception ignored) { }
        }
        return new Mark(id, color);
    }
    private static final class Mark extends Drawable {
        final String id; final Paint p=new Paint(3);
        Mark(String value,int color){id=value;p.setColor(color);p.setStrokeWidth(2);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
        @Override public void draw(Canvas c){
            c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/32f,getBounds().height()/32f);
            if(id.equals("hdmi:2")){
                c.drawRoundRect(3,9,29,24,6,6,p);c.drawLine(8,16,14,16,p);c.drawLine(11,13,11,19,p);
                c.drawCircle(22,14,1,p);c.drawCircle(25,18,1,p);
            } else if(id.startsWith("hdmi:") || id.equals("control:inputs")){
                c.drawRoundRect(3,5,29,23,3,3,p);c.drawLine(16,23,16,28,p);c.drawLine(10,28,22,28,p);
                if(id.startsWith("hdmi:")){p.setStyle(Paint.Style.FILL);p.setTextSize(10);p.setTextAlign(Paint.Align.CENTER);c.drawText("TV",16,18,p);p.setStyle(Paint.Style.STROKE);}
            } else if(id.equals("control:settings")) {
                c.drawCircle(16,16,8,p);c.drawCircle(16,16,3,p);
                for(int i=0;i<8;i++){c.save();c.rotate(i*45,16,16);c.drawLine(16,3,16,7,p);c.restore();}
            } else {
                for(int x=0;x<2;x++)for(int y=0;y<2;y++)c.drawRoundRect(5+x*13,5+y*13,14+x*13,14+y*13,2,2,p);
            }
            c.restore();
        }
        @Override public void setAlpha(int a){p.setAlpha(a);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
