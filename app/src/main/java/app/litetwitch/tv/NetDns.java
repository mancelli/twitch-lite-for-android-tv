package app.litetwitch.tv;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Dns;

/**
 * DNS с закреплёнными IP для доменов эмоутов: провайдерский DNS флакающе отдаёт
 * саункхол 8.47.69.0 для cdn.betterttv.net / cdn.frankerfacez.com (РКН), при этом
 * те же домены с настоящих IP (Cloudflare anycast) отвечают нормально.
 * Остальные домены резолвятся системой.
 */
public final class NetDns implements Dns {

    private static final Map<String, List<InetAddress>> PINNED = new HashMap<>();

    static {
        pin("cdn.betterttv.net", "104.26.0.19", "104.26.1.19", "172.67.68.177");
        pin("api.betterttv.net", "104.26.0.19", "104.26.1.19", "172.67.68.177");
        pin("cdn.frankerfacez.com", "104.26.6.201", "104.26.7.201", "172.67.70.71");
        pin("api.frankerfacez.com", "104.26.6.201", "104.26.7.201", "172.67.70.71");
    }

    private static void pin(String host, String... ips) {
        try {
            InetAddress[] addrs = new InetAddress[ips.length];
            for (int i = 0; i < ips.length; i++) addrs[i] = InetAddress.getByName(ips[i]);
            PINNED.put(host, Arrays.asList(addrs));
        } catch (UnknownHostException ignored) {
        }
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        List<InetAddress> pinned = PINNED.get(hostname);
        if (pinned != null) return pinned;
        return Dns.SYSTEM.lookup(hostname);
    }
}
