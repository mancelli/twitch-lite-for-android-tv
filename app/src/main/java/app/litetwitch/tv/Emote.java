package app.litetwitch.tv;

import org.json.JSONArray;
import org.json.JSONObject;

/** Эмоут стороннего сервиса. Порт bttv-android (MIT), app.litetwitch.tv. */
public final class Emote {
    public final String id;
    public final String code;
    public final String url;

    public Emote(String id, String code, String url) {
        this.id = id;
        this.code = code;
        this.url = url;
    }

    /** Разбор объекта эмоута по формату конкретного сервиса (логика bttv Emote.fromJson). */
    public static Emote fromJson(JSONObject o, Emotes.Source source) throws Exception {
        String id = o.getString("id");
        switch (source) {
            case BTTV: {
                String code = o.getString("code");
                String url = "https://cdn.betterttv.net/emote/" + id + "/2x";
                return new Emote(id, code, url);
            }
            case FFZ: {
                String code = o.getString("code");
                JSONObject images = o.getJSONObject("images");
                String url = images.has("2x") && !images.isNull("2x")
                        ? images.getString("2x") : images.getString("1x");
                return new Emote(id, code, url);
            }
            case STV: {
                // "name" из набора может отличаться от имени самого эмоута (так в bttv)
                String code = o.getString("name");
                JSONObject data = o.getJSONObject("data");
                JSONObject host = data.getJSONObject("host");
                StringBuilder url = new StringBuilder("https:"); // у 7tv url хоста без схемы
                url.append(host.getString("url")).append('/');
                JSONArray files = host.getJSONArray("files");
                String small = null, big = null;
                for (int i = 0; i < files.length(); i++) {
                    JSONObject f = files.getJSONObject(i);
                    if (!"WEBP".equals(f.getString("format"))) continue;
                    String name = f.getString("name");
                    if (f.getInt("height") >= 64 && big == null) big = name;
                    else if (small == null) small = name;
                }
                String selected = big != null ? big : small;
                if (selected == null) throw new Exception("7TV: нет webp-файла для " + id);
                url.append(selected);
                return new Emote(id, code, url.toString());
            }
            default:
                throw new Exception("Неизвестный источник эмоутов");
        }
    }

    /** Глобальные списки приходят массивом; BTTV-канал — объектом channelEmotes/sharedEmotes; 7TV — emote_set.emotes. */
    public static JSONArray extractArray(String json, Emotes.Source source) throws Exception {
        String trimmed = json.trim();
        if (source == Emotes.Source.BTTV && trimmed.startsWith("{")) {
            // эмоуты канала: {"channelEmotes":[...], "sharedEmotes":[...]} (ChannelEmoteData из bttv-android)
            JSONObject o = new JSONObject(trimmed);
            JSONArray combined = new JSONArray();
            JSONArray ch = o.optJSONArray("channelEmotes");
            JSONArray sh = o.optJSONArray("sharedEmotes");
            if (ch != null) for (int i = 0; i < ch.length(); i++) combined.put(ch.get(i));
            if (sh != null) for (int i = 0; i < sh.length(); i++) combined.put(sh.get(i));
            return combined;
        }
        if (source == Emotes.Source.STV) {
            JSONObject resp = new JSONObject(trimmed);
            JSONObject set = resp.has("emote_set") ? resp.getJSONObject("emote_set") : resp;
            return set.getJSONArray("emotes");
        }
        return new JSONArray(trimmed);
    }
}
