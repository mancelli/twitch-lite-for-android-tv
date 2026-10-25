package app.litetwitch.tv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Клиент GQL (graphql.twitch.tv/gql) с публичным client-id веб-клиента Twitch.
 * Все вызовы синхронные — вызывать из фоновых потоков.
 */
public final class Gql {
    private static final String ENDPOINT = "https://gql.twitch.tv/gql";
    public static final String CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko";
    // client_id официального Android-клиента для device-code авторизации
    public static final String DEVICE_CLIENT_ID = "ue6666qo983tsx6so1t0vnawi233wa";

    private static String oauthToken;

    private static final OkHttpClient HTTP = Http.CLIENT;

    private static String deviceId;

    private static String deviceId() {
        if (deviceId == null) deviceId = UUID.randomUUID().toString();
        return deviceId;
    }

    /** Подгрузить сохранённый токен (вызывать при старте приложения). */
    public static void init(android.content.Context ctx) {
        oauthToken = Auth.token(ctx);
    }

    /** Токен авторизации (null — не авторизован). */
    public static String oauthToken() {
        return oauthToken;
    }

    public static void setOAuth(String token) {
        oauthToken = token;
    }

    public static String post(String body) throws Exception {
        Request.Builder rb = new Request.Builder()
                .url(ENDPOINT)
                .header("Client-ID", CLIENT_ID)
                .header("X-Device-Id", deviceId());
        if (oauthToken != null) rb.header("Authorization", "OAuth " + oauthToken);
        Request req = rb.post(RequestBody.create(body, MediaType.parse("application/json"))).build();
        try (Response r = HTTP.newCall(req).execute()) {
            String s = r.body() != null ? r.body().string() : "";
            if (!r.isSuccessful()) throw new Exception("GQL HTTP " + r.code() + ": " + s);
            JSONObject obj = new JSONObject(s);
            if (obj.has("errors")) {
                throw new Exception("GQL error: " + obj.getJSONArray("errors").toString());
            }
            return s;
        }
    }

    private static final String STREAM_FIELDS = "id title viewersCount "
            + "previewImageURL(width:320,height:180) game{displayName} "
            + "broadcaster{id login displayName}";

    private static JSONArray edgesOf(String body, String path) throws Exception {
        JSONObject d = new JSONObject(body).getJSONObject("data");
        for (String key : path.split("\\.")) d = d.getJSONObject(key);
        JSONArray edges = d.getJSONArray("edges");
        JSONArray out = new JSONArray();
        for (int i = 0; i < edges.length(); i++) {
            JSONObject node = edges.getJSONObject(i).getJSONObject("node");
            out.put(node);
        }
        return out;
    }

    /** Обёртка GraphQL-запроса в JSON — экранирование берёт на себя JSONObject. */
    private static String wrap(String gql) throws org.json.JSONException {
        return new JSONObject().put("query", gql).toString();
    }

    /** Топ live-стримов. Возвращает JSONArray из "node". GQL ограничивает first<=30. */
    public static JSONArray topStreams(int first) throws Exception {
        first = Math.max(1, Math.min(first, 30));
        String gql = "query{streams(first:" + first + "){edges{node{" + STREAM_FIELDS + "}}}}";
        return edgesOf(post(wrap(gql)), "streams");
    }

    /** Поиск стримов. */
    public static JSONArray searchStreams(String query) throws Exception {
        String gql = "query{searchStreams(first:30,userQuery:\"" + jsonEsc(query)
                + "\"){edges{node{" + STREAM_FIELDS + "}}}}";
        return edgesOf(post(wrap(gql)), "searchStreams");
    }

    /** Подписки пользователя (только live) через Helix — GQL user.follows у Twitch сломан ("service error"). */
    public static JSONArray followedStreams(String userId) throws Exception {
        if (oauthToken == null) throw new Exception("Требуется вход");
        okhttp3.Request req = new okhttp3.Request.Builder()
                .url("https://api.twitch.tv/helix/streams/followed?user_id=" + userId)
                .header("Client-ID", DEVICE_CLIENT_ID)
                .header("Authorization", "Bearer " + oauthToken)
                .build();
        String body;
        try (okhttp3.Response r = Http.CLIENT.newCall(req).execute()) {
            body = r.body() != null ? r.body().string() : "";
            if (!r.isSuccessful()) throw new Exception("Helix HTTP " + r.code() + ": " + body);
        }
        JSONArray arr = new JSONObject(body).getJSONArray("data");
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.getJSONObject(i);
            JSONObject node = new JSONObject();
            node.put("id", s.optString("id"));
            node.put("title", s.optString("title"));
            node.put("viewersCount", s.optLong("viewer_count"));
            node.put("previewImageURL", s.optString("thumbnail_url")
                    .replace("{width}", "320").replace("{height}", "180"));
            node.put("game", new JSONObject().put("displayName", s.optString("game_name")));
            node.put("broadcaster", new JSONObject()
                    .put("id", s.optString("user_id"))
                    .put("login", s.optString("user_login"))
                    .put("displayName", s.optString("user_name")));
            out.put(node);
        }
        return out;
    }

    private static String jsonEsc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    /** URL master-плейлиста (usher) для канала. playerType=embed — плейлист обычно без SSAI-рекламы. */
    public static String playbackUrl(String login) throws Exception {
        try {
            return tokenUrl(login, "embed");
        } catch (Exception embedFail) {
            // фолбэк: playerType=site (может вернуть плейлист с рекламой — её вырежет HlsRewrite)
            return tokenUrl(login, "site");
        }
    }

    private static String tokenUrl(String login, String playerType) throws Exception {
        String q = "query PlaybackAccessToken($login: String!, $playerType: String!) "
                + "{ streamPlaybackAccessToken(channelName: $login, "
                + "params: {platform: \"web\", playerBackend: \"mediaplayer\", playerType: $playerType}) "
                + "{ value signature __typename } }";
        JSONObject vars = new JSONObject()
                .put("login", login)
                .put("playerType", playerType);
        JSONObject body = new JSONObject().put("query", q).put("variables", vars);
        JSONObject ata = new JSONObject(post(body.toString()))
                .getJSONObject("data")
                .getJSONObject("streamPlaybackAccessToken");
        String sig = ata.getString("signature");
        String value = ata.getString("value");
        // usher-URL собирается из signature+value (в самом токене поля url нет)
        return "https://usher.ttvnw.net/api/channel/hls/" + login + ".m3u8"
                + "?sig=" + java.net.URLEncoder.encode(sig, "UTF-8")
                + "&token=" + java.net.URLEncoder.encode(value, "UTF-8")
                + "&allow_source=true&allow_audio_only=true&player=tweb&supported_codecs=h264&fast_bread=true";
    }

    /** URL плейлиста VOD по его id. */
    public static String vodPlaybackUrl(String vodId) throws Exception {
        String q = "query{videoPlaybackAccessToken(id: \"" + jsonEsc(vodId) + "\", "
                + "params: {platform: \"web\", playerBackend: \"mediaplayer\", playerType: \"site\"}) "
                + "{ value signature } }";
        JSONObject ata = new JSONObject(post(wrap(q)))
                .getJSONObject("data")
                .getJSONObject("videoPlaybackAccessToken");
        return "https://usher.ttvnw.net/vod/" + vodId + ".m3u8"
                + "?sig=" + java.net.URLEncoder.encode(ata.getString("signature"), "UTF-8")
                + "&token=" + java.net.URLEncoder.encode(ata.getString("value"), "UTF-8")
                + "&allow_source=true&supported_codecs=h264&player=tweb";
    }

    /** Информация о канале: профиль, стрим (если в эфире) и последние VOD. */
    public static JSONObject channelInfo(String login) throws Exception {
        String q = "query{user(login:\"" + jsonEsc(login) + "\"){displayName "
                + "profileImageURL(width:70) "
                + "followers{totalCount} "
                + "stream{id title viewersCount game{displayName}} "
                + "videos(first:30,sort:TIME){edges{node{id title lengthSeconds viewCount createdAt}}}}}";
        JSONObject data = new JSONObject(post(wrap(q))).getJSONObject("data");
        if (data.isNull("user")) throw new Exception("Канал не найден: " + login);
        return data.getJSONObject("user");
    }
}
