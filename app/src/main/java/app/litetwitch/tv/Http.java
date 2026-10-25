package app.litetwitch.tv;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/** Общий HTTP-клиент приложения с DNS-пинами для заблокированных CDN эмоутов. */
public final class Http {
    public static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .dns(new NetDns())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private Http() {}
}
