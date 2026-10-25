package app.litetwitch.tv;

/**
 * Разбор и чистка HLS-плейлистов Twitch.
 * Вырезание рекламы по методу Streamlink: сегменты между DATERANGE-маркерами
 * Class="stitched-ad" (и до следующего DATERANGE/-END) удаляются из плейлиста.
 */
public final class HlsRewrite {

    private HlsRewrite() {}

    /** Выбрать вариант с максимальным BANDWIDTH из master-плейлиста; URL абсолютный. */
    public static String pickBestVariant(String master, String baseUrl) {
        String[] lines = master.split("\\r?\\n");
        long bestBw = -1;
        String best = null;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].trim();
            if (!l.startsWith("#EXT-X-STREAM-INF")) continue;
            long bw = attr(l, "BANDWIDTH", -1);
            String url = null;
            for (int j = i + 1; j < lines.length; j++) {
                String s = lines[j].trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                url = s;
                break;
            }
            if (url != null && bw > bestBw) {
                bestBw = bw;
                best = url;
            }
        }
        if (best == null) {
            // уже медиа-плейлист?
            if (master.contains("#EXTINF")) return baseUrl;
            return null;
        }
        if (best.startsWith("http://") || best.startsWith("https://")) return best;
        return resolve(baseUrl, best);
    }

    /**
     * Убрать рекламу из медиа-плейлиста:
     *  - DATERANGE с Class="stitched-ad"/"trigger-ad" — маркеры рекламных вставок;
     *  - сегменты от старта ad-блока до следующего DATERANGE отбрасываются.
     */
    public static String rewrite(String playlist) {
        StringBuilder out = new StringBuilder(playlist.length() + 16);
        boolean inAd = false;
        boolean seenContent = false;
        for (String raw : playlist.split("\\r?\\n", -1)) {
            String l = raw.trim();
            if (l.startsWith("#EXT-X-DATERANGE")) {
                boolean isAd = l.contains("stitched-ad") || l.contains("trigger-ad");
                boolean isEnd = l.contains("-END") || l.contains("END-DATE=");
                if (isAd && !isEnd && !l.contains("END-DATE")) {
                    inAd = true;
                } else {
                    inAd = false;
                }
                continue; // сами маркеры из плейлиста убираем
            }
            if (l.startsWith("#EXT-X-TWITCH-AD")) continue;
            if (inAd && !l.isEmpty() && !l.startsWith("#")) continue; // рекламный сегмент
            if (inAd && (l.startsWith("#EXTINF") || l.startsWith("#EXT-X-BYTERANGE"))) continue;
            if (!l.isEmpty() && !l.startsWith("#")) seenContent = true;
            out.append(raw).append('\n');
        }
        if (!seenContent) return playlist; // перестарались — отдать как было
        return out.toString();
    }

    private static long attr(String line, String name, long def) {
        int i = line.indexOf(name + "=");
        if (i < 0) return def;
        int s = line.indexOf('=', i) + 1;
        int e = s;
        while (e < line.length() && (Character.isDigit(line.charAt(e)))) e++;
        try {
            return Long.parseLong(line.substring(s, e));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static String resolve(String baseUrl, String rel) {
        if (rel.startsWith("/")) {
            int idx = baseUrl.indexOf('/', baseUrl.indexOf("//") + 2);
            return idx < 0 ? baseUrl + rel : baseUrl.substring(0, idx) + rel;
        }
        int slash = baseUrl.lastIndexOf('/');
        return (slash < 0 ? baseUrl : baseUrl.substring(0, slash + 1)) + rel;
    }
}
