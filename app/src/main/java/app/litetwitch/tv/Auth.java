package app.litetwitch.tv;

import android.content.Context;
import android.content.SharedPreferences;

/** Хранилище OAuth-токена Twitch. */
public final class Auth {
    private static final String PREFS = "auth";

    private Auth() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String token(Context ctx) {
        return prefs(ctx).getString("token", null);
    }

    public static String login(Context ctx) {
        return prefs(ctx).getString("login", null);
    }

    public static String userId(Context ctx) {
        return prefs(ctx).getString("user_id", null);
    }

    public static void save(Context ctx, String token, String login, String userId) {
        prefs(ctx).edit()
                .putString("token", token)
                .putString("login", login)
                .putString("user_id", userId)
                .apply();
    }

    public static void clear(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }
}
