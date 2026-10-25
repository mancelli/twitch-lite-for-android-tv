package app.litetwitch.tv;

import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * DataSource для ExoPlayer: перехватывает .m3u8 (master и media),
 * выбирает лучший вариант и вырезает рекламные сегменты.
 * Сегменты видео отдаются без изменений через upstream.
 */
public final class RewriteDataSource extends BaseDataSource {

    private static final String UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/90 Mobile Safari/537.36";

    private final DataSource upstream;
    private final OkHttpClient http;

    private UriOpen open;
    private InputStream playlistStream;
    private long remaining;

    private static final class UriOpen {
        final android.net.Uri uri;
        UriOpen(android.net.Uri uri) { this.uri = uri; }
    }

    public RewriteDataSource(DataSource upstream, OkHttpClient http) {
        super(true);
        this.upstream = upstream;
        this.http = http;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        transferInitializing(dataSpec);
        android.net.Uri uri = dataSpec.uri;
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.endsWith(".m3u8")) {
            Request req = new Request.Builder().url(uri.toString()).header("User-Agent", UA).build();
            try (Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code() + " для " + uri);
                String body = resp.body() != null ? resp.body().string() : "";
                if (!body.startsWith("#EXTM3U")) {
                    String head = body.length() > 160 ? body.substring(0, 160) : body;
                    throw new IOException("Не-HLS ответ от " + uri + ": " + head.replace('\n', ' '));
                }
                android.util.Log.d("TTLite", "m3u8 " + uri.getLastPathSegment()
                        + " len=" + body.length());
                // master пробрасываем как есть — ExoPlayer сам делает ABR и выбор качества,
                // каждый медиа-плейлист ниже всё равно проходит чистку от рекламы
                String out = body.contains("#EXT-X-STREAM-INF") ? body : HlsRewrite.rewrite(body);
                byte[] bytes = out.getBytes("UTF-8");
                long pos = Math.max(0L, dataSpec.position);
                long len = bytes.length - pos;
                if (len <= 0) throw new EOFException();
                if (dataSpec.length != C.LENGTH_UNSET) len = Math.min(len, dataSpec.length);
                playlistStream = new ByteArrayInputStream(bytes, (int) pos, (int) len);
                remaining = len;
                this.open = new UriOpen(uri);
                transferStarted(dataSpec);
                return len;
            }
        }
        long len = upstream.open(dataSpec);
        this.open = new UriOpen(uri);
        transferStarted(dataSpec);
        return len;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (playlistStream != null) {
            if (remaining <= 0) return C.RESULT_END_OF_INPUT;
            int n = playlistStream.read(buffer, offset, (int) Math.min(readLength, remaining));
            if (n == -1) return C.RESULT_END_OF_INPUT;
            remaining -= n;
            bytesTransferred(n);
            return n;
        }
        return upstream.read(buffer, offset, readLength);
    }

    @Override
    public android.net.Uri getUri() {
        return open != null ? open.uri : null;
    }

    @Override
    public void close() throws IOException {
        open = null;
        playlistStream = null;
        remaining = 0;
        upstream.close();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return Collections.emptyMap();
    }
}
