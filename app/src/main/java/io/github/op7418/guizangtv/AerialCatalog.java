package io.github.op7418.guizangtv;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Aerial TV's Apple catalogue; the APK contains URLs and captions, never the films. */
final class AerialCatalog {
    static final class Film {
        final String id, label, url;
        final int[] seconds;
        final String[] captions;
        Film(JSONObject item) throws Exception {
            id = item.getString("id"); label = item.getString("label");
            url = item.getString("url");
            if (!AerialPolicy.allowedUrl(url)) throw new IllegalArgumentException("Untrusted aerial URL");
            JSONArray values = item.getJSONArray("captions");
            seconds = new int[values.length()]; captions = new String[values.length()];
            for (int i = 0; i < values.length(); i++) {
                JSONArray value = values.getJSONArray(i);
                seconds[i] = value.getInt(0); captions[i] = value.getString(1).replace('\u00a0', ' ');
            }
        }
        String caption(long positionMs) {
            String result = label;
            for (int i = 0; i < seconds.length; i++) {
                if (seconds[i] > positionMs / 1000) break;
                result = captions[i];
            }
            return result;
        }
    }
    static List<Film> load(Context context) throws Exception {
        try (InputStream input = context.getAssets().open("aerials.json")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] block = new byte[8192]; int count;
            while ((count = input.read(block)) != -1) bytes.write(block, 0, count);
            JSONArray values = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())).getJSONArray("videos");
            List<Film> result = new ArrayList<>();
            for (int i = 0; i < values.length(); i++) result.add(new Film(values.getJSONObject(i)));
            if (result.isEmpty()) throw new IllegalArgumentException("Empty aerial catalogue");
            return result;
        }
    }
}
