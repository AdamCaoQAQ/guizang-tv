package io.github.op7418.guizangtv;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One silent player only while unobscured Home is resumed. Static wallpaper stays as fallback. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class AerialBackground {
    private final Activity activity;
    private final FrameLayout root;
    private final ImageView photo;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView caption;
    private List<AerialCatalog.Film> films = Collections.emptyList();
    private ExoPlayer player;
    private SurfaceView video;
    private boolean active, destroyed, hasFrame;
    private int failures, successfulIndex = -1, savedIndex;
    private long savedPosition;
    private String status = "未播放";
    private final Runnable stalled = () -> failed("网络缓冲超时");
    private final Runnable updateCaption = new Runnable() {
        @Override public void run() {
            if (player == null || !active) return;
            int index = player.getCurrentMediaItemIndex();
            if (caption.getVisibility() == View.VISIBLE && index >= 0 && index < films.size()) {
                String text = films.get(index).caption(player.getCurrentPosition());
                if (!android.text.TextUtils.equals(caption.getText(), text)) caption.setText(text);
            }
            handler.postDelayed(this, 1000);
        }
    };
    AerialBackground(Activity activity, FrameLayout root, ImageView photo) {
        this.activity = activity; this.root = root; this.photo = photo;
        caption = new TextView(activity); caption.setTextColor(0xE6FFFFFF); caption.setTextSize(14);
        caption.setShadowLayer(4, 0, 1, 0xFF000000); caption.setMaxLines(2);
        caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        caption.setVisibility(View.GONE);
        int margin = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        params.leftMargin = margin; params.bottomMargin = margin / 2; params.rightMargin = margin;
        root.addView(caption, params);
    }
    void active(boolean wanted) {
        if (destroyed) return;
        if (active == wanted) {
            if (wanted && hasFrame) photo.setVisibility(View.INVISIBLE);
            return;
        }
        active = wanted;
        if (!wanted) { stop(); return; }
        failures = 0; successfulIndex = -1;
        try {
            if (films.isEmpty()) {
                films = new ArrayList<>(AerialCatalog.load(activity)); Collections.shuffle(films);
            }
            video = new SurfaceView(activity); video.setFocusable(false);
            video.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            root.addView(video, 0, new FrameLayout.LayoutParams(-1, -1));
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(5000).setReadTimeoutMs(8000).setAllowCrossProtocolRedirects(false);
            DefaultLoadControl load = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(10_000, 30_000, 1_000, 2_000)
                    .setTargetBufferBytes(AerialPolicy.BUFFER_BYTES).setPrioritizeTimeOverSizeThresholds(false).build();
            player = new ExoPlayer.Builder(activity).setLoadControl(load)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(http)
                            .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(1))).build();
            player.setVolume(0); player.setRepeatMode(Player.REPEAT_MODE_ALL);
            player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            player.setVideoSurfaceView(video);
            final ExoPlayer owner = player;
            player.addListener(new Player.Listener() {
                @Override public void onRenderedFirstFrame() {
                    if (!active || player != owner) return;
                    int index = player.getCurrentMediaItemIndex();
                    if (index != successfulIndex) { failures = 0; successfulIndex = index; }
                    handler.removeCallbacks(stalled);
                    hasFrame = true; photo.setVisibility(View.INVISIBLE); caption.setVisibility(View.VISIBLE);
                    status = "播放中";
                }
                @Override public void onMediaItemTransition(MediaItem item, int reason) {
                    if (!active || player != owner) return;
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) showFallback();
                    caption.setText(films.get(player.getCurrentMediaItemIndex()).caption(0));
                    watch();
                }
                @Override public void onPlaybackStateChanged(int state) {
                    if (!active || player != owner) return;
                    if (state == Player.STATE_BUFFERING) watch();
                    else if (state == Player.STATE_READY) {
                        if (hasFrame) handler.removeCallbacks(stalled); else watch();
                    }
                }
                @Override public void onPlayerError(PlaybackException error) {
                    if (active && player == owner) failed("视频暂不可用");
                }
            });
            List<MediaItem> items = new ArrayList<>();
            for (AerialCatalog.Film film : films) items.add(new MediaItem.Builder().setUri(film.url).setMediaId(film.id).build());
            player.setMediaItems(items, savedIndex, savedPosition);
            status = "正在加载苹果航拍"; player.prepare(); player.play(); watch();
            handler.post(updateCaption);
        } catch (Exception unavailable) {
            android.util.Log.w("GuiZang.Aerial", "Unable to start aerial background", unavailable);
            stop(); status = "航拍不可用，已显示照片";
        }
    }
    private void watch() {
        handler.removeCallbacks(stalled); handler.postDelayed(stalled, AerialPolicy.STALL_MS);
    }
    private void showFallback() { hasFrame = false; photo.setVisibility(View.VISIBLE); caption.setVisibility(View.GONE); }
    private void failed(String reason) {
        if (!active || player == null) return;
        handler.removeCallbacks(stalled); showFallback();
        if (AerialPolicy.exhausted(++failures)) {
            stop(); status = reason + "，已显示照片；返回首页时重试"; return;
        }
        status = reason + "，尝试下一段";
        player.seekTo((player.getCurrentMediaItemIndex() + 1) % films.size(), 0);
        player.prepare(); player.play(); watch();
    }
    private void stop() {
        status = "已暂停";
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            savedIndex = Math.max(0, player.getCurrentMediaItemIndex()); savedPosition = Math.max(0, player.getCurrentPosition());
            ExoPlayer old = player; player = null; old.release();
        }
        if (video != null) { root.removeView(video); video = null; }
        hasFrame = false; caption.setVisibility(View.GONE);
        photo.setVisibility(View.VISIBLE);
    }
    String status() { return status; }
    void front() { caption.bringToFront(); }
    void destroy() { active = false; destroyed = true; stop(); }
}
