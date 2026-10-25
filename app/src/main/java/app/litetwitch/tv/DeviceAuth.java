package app.litetwitch.tv;

import org.json.JSONObject;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Device Authorization Flow Twitch (https://dev.twitch.tv/docs/authentication/getting-tokens-oauth/).
 * Пользователь вводит код на twitch.tv/activate с телефона/ПК, приложение опрашивает токен.
 */
public final class DeviceAuth {

    /** Исключение «ещё не подтверждено» — опрашивать дальше. */
    public static final class Pending extends Exception {}

    public static final class Flow {
        public final String deviceCode;
        public final String userCode;
        public final String verifyUri;

        Flow(String d, String u, String v) {
            deviceCode = d;
            userCode = u;
            verifyUri = v;
        }
    }

    private static final OkHttpClient HTTP = new OkHttpClient();

    private DeviceAuth() {}

    public static Flow begin() throws Exception {
        RequestBody body = new FormBody.Builder()
                .add("client_id", Gql.DEVICE_CLIENT_ID)
                .add("scopes", "user:read:follows")
                .build();
        Request req = new Request.Builder().url("https://id.twitch.tv/oauth2/device").post(body).build();
        try (Response r = HTTP.newCall(req).execute()) {
            String s = r.body() != null ? r.body().string() : "";
            if (!r.isSuccessful()) throw new Exception("device: HTTP " + r.code() + " " + s);
            JSONObject o = new JSONObject(s);
            return new Flow(o.getString("device_code"), o.getString("user_code"),
                    o.optString("verification_uri", "https://www.twitch.tv/activate"));
        }
    }

    /** @return access_token; бросает Pending, пока пользователь не подтвердил. */
    public static String poll(String deviceCode) throws Exception {
        RequestBody body = new FormBody.Builder()
                .add("client_id", Gql.DEVICE_CLIENT_ID)
                .add("device_code", deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build();
        Request req = new Request.Builder().url("https://id.twitch.tv/oauth2/token").post(body).build();
        try (Response r = HTTP.newCall(req).execute()) {
            String s = r.body() != null ? r.body().string() : "";
            JSONObject o = new JSONObject(s);
            if (r.isSuccessful() && o.has("access_token")) return o.getString("access_token");
            // Twitch шлёт статус и в "error", и в "message" — проверяем оба
            String err = o.optString("error", "") + "|" + o.optString("message", "");
            if (err.contains("authorization_pending") || err.contains("slow_down")) throw new Pending();
            throw new Exception("token: " + s);
        }
    }

    /** Проверка токена: возвращает {login, user_id}. */
    public static JSONObject validate(String token) throws Exception {
        Request req = new Request.Builder().url("https://id.twitch.tv/oauth2/validate")
                .header("Authorization", "OAuth " + token).build();
        try (Response r = HTTP.newCall(req).execute()) {
            String s = r.body() != null ? r.body().string() : "";
            if (!r.isSuccessful()) throw new Exception("validate: HTTP " + r.code());
            return new JSONObject(s);
        }
    }
}
