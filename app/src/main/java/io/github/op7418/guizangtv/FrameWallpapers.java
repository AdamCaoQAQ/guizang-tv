package io.github.op7418.guizangtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bundled wallpapers: bounded decoding, saved choice, foreground-only rotation. */
final class FrameWallpapers {
    private static final class Entry {
        final String id, name, file, thumb;
        Entry(JSONObject json) throws org.json.JSONException {
            id = json.getString("id"); name = json.getString("name");
            file = json.getString("file"); thumb = json.getString("thumb");
        }
    }
    private final Activity activity;
    private final ImageView photo, transition;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Entry> entries = new ArrayList<>();
    private final List<TextView> captions = new ArrayList<>();
    private boolean rotating, active, loading, destroyed, hasImage;
    private int current, minutes, generation;
    private Dialog dialog;
    private AlertDialog intervalDialog;
    private TextView autoButton, fixedButton, intervalButton;
    private final Runnable rotate = () -> load(WallpaperCycle.next(current, entries.size()));

    FrameWallpapers(Activity activity, ImageView photo, ImageView transition) {
        this.activity = activity; this.photo = photo; this.transition = transition;
        prefs = activity.getSharedPreferences("frame-wallpapers", Activity.MODE_PRIVATE);
        rotating = prefs.getBoolean("rotating", true);
        minutes = WallpaperCycle.minutes(prefs.getInt("minutes", WallpaperCycle.DEFAULT_MINUTES));
        try (InputStream stream = openCatalog()) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = stream.read(buffer)) != -1) bytes.write(buffer, 0, n);
            JSONArray json = new JSONArray(bytes.toString("UTF-8"));
            if (json.length() > 15) throw new IllegalArgumentException("At most 15 wallpapers");
            for (int i = 0; i < json.length(); i++) entries.add(new Entry(json.getJSONObject(i)));
        } catch (Exception error) {
            android.util.Log.e("TvHome.Wallpaper", "Catalog unavailable", error);
        }
        String id = prefs.getString("selected", "");
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id.equals(id)) current = i;
        if (entries.isEmpty()) {
            String[] names = {"暮色", "松林", "暖砂", "深海", "紫夜"};
            for (int i = 0; i < names.length; i++) {
                try {
                    entries.add(new Entry(new JSONObject().put("id", "builtin-" + i)
                            .put("name", names[i]).put("file", "builtin:" + i)
                            .put("thumb", "builtin:" + i)));
                } catch (org.json.JSONException impossible) { throw new IllegalStateException(impossible); }
            }
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).id.equals(id)) current = i;
        }
        load(current);
    }

    private InputStream openCatalog() throws java.io.IOException {
        try { return activity.getAssets().open("wallpapers/local/catalog.json"); }
        catch (java.io.FileNotFoundException absent) {
            return activity.getAssets().open("wallpapers/catalog.json");
        }
    }

    private Bitmap decode(String file, boolean thumbnail) throws Exception {
        if (file.startsWith("builtin:")) {
            int[][] palettes = {{0xFF172B3B, 0xFFB78060}, {0xFF152C28, 0xFF6D8B68},
                    {0xFF3C302B, 0xFFC6A376}, {0xFF0B2238, 0xFF397E92}, {0xFF24203A, 0xFF866B8D}};
            int index = Integer.parseInt(file.substring(8));
            Bitmap image = Bitmap.createBitmap(thumbnail ? 320 : 1920, thumbnail ? 180 : 1080,
                    Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(image);
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setShader(new android.graphics.LinearGradient(0, image.getHeight(), image.getWidth(), 0,
                    palettes[index], null, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, image.getWidth(), image.getHeight(), paint);
            return image;
        }
        try (InputStream stream = activity.getAssets().open("wallpapers/" + file)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap image = BitmapFactory.decodeStream(stream, null, options);
            if (image == null) throw new java.io.IOException("Image unavailable");
            return image;
        }
    }

    private void load(int index) {
        if (destroyed || entries.isEmpty()) return;
        handler.removeCallbacks(rotate);
        final int request = ++generation;
        loading = true;
        worker.execute(() -> {
            Bitmap bitmap = null;
            try { bitmap = decode(entries.get(index).file, false); }
            catch (Exception error) { android.util.Log.e("TvHome.Wallpaper", "Decode failed", error); }
            final Bitmap image = bitmap;
            handler.post(() -> {
                if (destroyed || request != generation) {
                    if (image != null) image.recycle();
                    return;
                }
                loading = false;
                if (image == null) {
                    Toast.makeText(activity, "壁纸加载失败，保留当前图片", Toast.LENGTH_SHORT).show();
                    schedule();
                    return;
                }
                transition.animate().cancel();
                if (transition.getDrawable() != null) photo.setImageDrawable(transition.getDrawable());
                transition.setImageDrawable(null);
                if (!hasImage || !active) {
                    photo.setImageBitmap(image);
                } else {
                    transition.setImageBitmap(image);
                    transition.setAlpha(0f);
                    transition.animate().alpha(1f).setDuration(700).withEndAction(() -> {
                        photo.setImageBitmap(image);
                        transition.setImageDrawable(null);
                        transition.setAlpha(0f);
                    }).start();
                }
                hasImage = true;
                current = index;
                prefs.edit().putString("selected", entries.get(current).id).apply();
                refreshLabels();
                android.util.Log.i("TvHome.Wallpaper", "Applied " + entries.get(current).name
                        + " rotating=" + rotating + " minutes=" + minutes);
                schedule();
            });
        });
    }

    private void schedule() {
        handler.removeCallbacks(rotate);
        if (active && !destroyed && !loading && rotating && entries.size() > 1
                && (dialog == null || !dialog.isShowing()))
            handler.postDelayed(rotate, minutes * 60_000L);
    }

    void resume() { active = true; schedule(); }
    void pause() {
        active = false;
        handler.removeCallbacks(rotate);
        if (intervalDialog != null) intervalDialog.dismiss();
        if (dialog != null) dialog.dismiss();
    }
    void destroy() {
        destroyed = true;
        generation++;
        pause();
        transition.animate().cancel();
        worker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
    }

    private void setMode(boolean enabled) {
        rotating = enabled;
        prefs.edit().putBoolean("rotating", enabled).apply();
        refreshLabels();
        schedule();
    }

    void show(View returnFocus) {
        if (entries.isEmpty()) return;
        if (dialog != null && dialog.isShowing()) return;
        handler.removeCallbacks(rotate);
        captions.clear();
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(20), dp(28), dp(18));
        panel.setBackground(surface(0xF51C2025, false));
        TextView title = label("壁纸设置", 24);
        panel.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));
        LinearLayout modes = new LinearLayout(activity);
        autoButton = button("", () -> setMode(true));
        fixedButton = button("", () -> setMode(false));
        intervalButton = button("", this::chooseInterval);
        for (TextView button : new TextView[]{autoButton, fixedButton, intervalButton}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(40), 1);
            p.rightMargin = dp(10);
            modes.addView(button, p);
        }
        panel.addView(modes);
        TextView note = label("选择图片可固定使用；自动轮播仅在桌面显示时计时", 12);
        note.setTextColor(0xB3FFFFFF);
        note.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(note, new LinearLayout.LayoutParams(-1, dp(32)));
        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(5);
        List<View> tiles = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            final int index = i;
            LinearLayout tile = new LinearLayout(activity);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(4), dp(4), dp(4), dp(3));
            tile.setFocusable(true);
            tile.setClickable(true);
            tile.setId(View.generateViewId());
            tile.setBackground(surface(0x18000000, false));
            tile.setContentDescription(entries.get(i).name);
            tile.setOnFocusChangeListener((v, focus) -> tile.setBackground(surface(0x18FFFFFF, focus)));
            tile.setOnClickListener(v -> { setMode(false); load(index); });
            ImageView thumb = new ImageView(activity);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try { thumb.setImageBitmap(decode(entries.get(i).thumb, true)); }
            catch (Exception unavailable) { thumb.setBackgroundColor(0xFF30343A); }
            tile.addView(thumb, new LinearLayout.LayoutParams(-1, dp(70)));
            TextView caption = label("", 12);
            caption.setGravity(Gravity.CENTER);
            tile.addView(caption, new LinearLayout.LayoutParams(-1, dp(22)));
            captions.add(caption);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = dp(148); p.height = dp(102);
            p.rightMargin = dp(8); p.bottomMargin = dp(6);
            grid.addView(tile, p);
            tiles.add(tile);
        }
        for (int i = 0; i < tiles.size(); i++) {
            View tile = tiles.get(i);
            tile.setNextFocusLeftId(tiles.get(i % 5 == 0 ? i : i - 1).getId());
            tile.setNextFocusRightId(tiles.get(i % 5 == 4 || i == tiles.size() - 1 ? i : i + 1).getId());
            tile.setNextFocusUpId(i < 5 ? (i < 2 ? autoButton : i < 4 ? fixedButton : intervalButton).getId()
                    : tiles.get(i - 5).getId());
            tile.setNextFocusDownId(tiles.get(Math.min(i + 5, tiles.size() - 1)).getId());
        }
        autoButton.setNextFocusDownId(tiles.get(0).getId());
        fixedButton.setNextFocusDownId(tiles.get(Math.min(2, tiles.size() - 1)).getId());
        intervalButton.setNextFocusDownId(tiles.get(Math.min(4, tiles.size() - 1)).getId());
        autoButton.setNextFocusUpId(autoButton.getId());
        fixedButton.setNextFocusUpId(fixedButton.getId());
        intervalButton.setNextFocusUpId(intervalButton.getId());
        autoButton.setNextFocusLeftId(autoButton.getId());
        autoButton.setNextFocusRightId(fixedButton.getId());
        fixedButton.setNextFocusLeftId(autoButton.getId());
        fixedButton.setNextFocusRightId(intervalButton.getId());
        intervalButton.setNextFocusLeftId(fixedButton.getId());
        intervalButton.setNextFocusRightId(intervalButton.getId());
        panel.addView(grid);
        dialog.setContentView(panel);
        dialog.setOnDismissListener(d -> {
            captions.clear();
            returnFocus.requestFocus();
            schedule();
        });
        refreshLabels();
        dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(dp(840), -2);
        (rotating ? autoButton : fixedButton).requestFocus();
    }

    private void chooseInterval() {
        String[] labels = {"1 分钟", "5 分钟", "15 分钟", "30 分钟"};
        int selected = 1;
        for (int i = 0; i < WallpaperCycle.MINUTES.length; i++) if (WallpaperCycle.MINUTES[i] == minutes) selected = i;
        intervalDialog = new AlertDialog.Builder(activity).setTitle("轮播间隔")
                .setSingleChoiceItems(labels, selected, (d, which) -> {
                    minutes = WallpaperCycle.MINUTES[which];
                    prefs.edit().putInt("minutes", minutes).apply();
                    refreshLabels();
                    d.dismiss();
                    schedule();
                }).setNegativeButton("返回", null).create();
        intervalDialog.show();
    }

    private void refreshLabels() {
        if (autoButton == null) return;
        autoButton.setText((rotating ? "✓  " : "") + "自动轮播");
        fixedButton.setText((!rotating ? "✓  " : "") + "固定壁纸");
        intervalButton.setText("间隔：" + minutes + " 分钟");
        for (int i = 0; i < captions.size(); i++)
            captions.get(i).setText((i == current ? "✓ " : "") + entries.get(i).name);
    }

    void dump(PrintWriter out) {
        out.println("FrameWallpaper: count=" + entries.size() + " index=" + current
                + " rotating=" + rotating + " minutes=" + minutes + " active=" + active
                + " loading=" + loading + " name=" + (entries.isEmpty() ? "" : entries.get(current).name));
    }

    private TextView button(String text, Runnable action) {
        TextView view = label(text, 15);
        view.setGravity(Gravity.CENTER);
        view.setId(View.generateViewId());
        view.setFocusable(true);
        view.setClickable(true);
        view.setBackground(surface(0x18FFFFFF, false));
        view.setOnFocusChangeListener((v, focus) -> view.setBackground(surface(0x28FFFFFF, focus)));
        view.setOnClickListener(v -> action.run());
        return view;
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(activity);
        view.setText(text); view.setTextSize(size); view.setTextColor(Color.WHITE);
        view.setIncludeFontPadding(false);
        return view;
    }
    private GradientDrawable surface(int color, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(8));
        if (focused) drawable.setStroke(dp(2), Color.WHITE);
        return drawable;
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
