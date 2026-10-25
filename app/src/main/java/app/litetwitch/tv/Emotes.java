package app.litetwitch.tv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Хранилище эмоутов BTTV / FFZ / 7TV.
 * Эндпоинты и форматы — точный порт bttv-android (MIT), см. bttv Network.java / Emote.java.
 */
public final class Emotes {

    public enum Source { BTTV, FFZ, STV }

    private static final String BTTV_API = "https://api.betterttv.net";
    private static final String STV_API = "https://7tv.io/v3";

    private static final OkHttpClient HTTP = Http.CLIENT;

    private static final Map<String, Emote> GLOBAL = new ConcurrentHashMap<>();
    private static final Map<Long, Map<String, Emote>> CHANNELS = new ConcurrentHashMap<>();
    private static volatile long currentChannel = -1;
    private static volatile boolean globalLoaded = false;

    private static String fetch(String url) throws Exception {
        Request req = new Request.Builder().url(url)
                .header("User-Agent", "bttv-android (TwitchTVLite port)")
                .build();
        try (Response r = HTTP.newCall(req).execute()) {
            String s = r.body() != null ? r.body().string() : "";
            if (!r.isSuccessful()) throw new Exception("HTTP " + r.code() + " " + url);
            return s;
        }
    }

    /** Загрузить глобальные эмоуты всех трёх сервисов. Потокобезопасно, вызывать в фоне. */
    public static synchronized void loadGlobal() {
        if (globalLoaded) return;
        // параллельно: один заблокированный хост не должен тормозить остальные
        Thread bttv = new Thread(() -> {
            loadSource(BTTV_API + "/3/cached/emotes/global", Source.BTTV, GLOBAL);
            loadSource(BTTV_API + "/3/cached/frankerfacez/emotes/global", Source.FFZ, GLOBAL);
        });
        Thread stv = new Thread(() -> loadSource(STV_API + "/emote-sets/global", Source.STV, GLOBAL));
        bttv.start();
        stv.start();
        try {
            bttv.join(20000);
            stv.join(20000);
        } catch (InterruptedException ignored) {
        }
        globalLoaded = true;
    }

    /** Загрузить эмоуты канала (числовой id Twitch). Потокобезопасно, вызывать в фоне. */
    public static synchronized void loadChannel(long channelId) {
        currentChannel = channelId;
        if (CHANNELS.containsKey(channelId)) return;
        Map<String, Emote> map = new ConcurrentHashMap<>();
        Thread bttv = new Thread(() -> {
            loadSource(BTTV_API + "/3/cached/users/twitch/" + channelId, Source.BTTV, map);
            loadSource(BTTV_API + "/3/cached/frankerfacez/users/twitch/" + channelId, Source.FFZ, map);
        });
        Thread stv = new Thread(() -> loadSource(STV_API + "/users/twitch/" + channelId, Source.STV, map));
        bttv.start();
        stv.start();
        try {
            bttv.join(20000);
            stv.join(20000);
        } catch (InterruptedException ignored) {
        }
        CHANNELS.put(channelId, map);
    }

    private static void loadSource(String url, Source source, Map<String, Emote> into) {
        try {
            JSONArray arr = Emote.extractArray(fetch(url), source);
            int n = 0;
            for (int i = 0; i < arr.length(); i++) {
                try {
                    Emote e = Emote.fromJson(arr.getJSONObject(i), source);
                    if (e.code != null && !e.code.isEmpty()) {
                        into.put(e.code, e);
                        n++;
                    }
                } catch (Exception ignored) {
                    // битый элемент не должен валить весь список
                }
            }
            android.util.Log.d("TTLite", "emotes " + source + ": +" + n + " from " + url);
        } catch (Exception e) {
            android.util.Log.w("TTLite", "emotes " + source + " FAIL " + url + ": " + e);
        }
    }

    /** Найти эмоут по коду: сначала эмоуты текущего канала, затем глобальные. */
    public static Emote get(String code) {
        Map<String, Emote> ch = CHANNELS.get(currentChannel);
        if (ch != null) {
            Emote e = ch.get(code);
            if (e != null) return e;
        }
        return GLOBAL.get(code);
    }

    /** Прогреть кэш Glide для эмоутов канала (чтобы чат не мигал). Вызывать в фоне. */
    public static void preload(android.content.Context ctx, int limit) {
        List<Emote> all = new ArrayList<>(GLOBAL.values());
        Map<String, Emote> ch = CHANNELS.get(currentChannel);
        if (ch != null) all.addAll(ch.values());
        int n = Math.min(limit, all.size());
        for (int i = 0; i < n; i++) {
            try {
                com.bumptech.glide.Glide.with(ctx.getApplicationContext())
                        .asBitmap()
                        .load(all.get(i).url)
                        .override(64, 64)
                        .submit()
                        .get();
            } catch (Exception ignored) {
            }
        }
    }
}
