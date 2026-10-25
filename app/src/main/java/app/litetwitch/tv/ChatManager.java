package app.litetwitch.tv;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Анонимный чат Twitch через IRC-WebSocket (justinfan-ник, только чтение).
 */
public final class ChatManager {

    public interface Listener {
        void onMessage(String displayName, String text);
    }

    private WebSocket ws;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build();

    public void connect(String channelLogin, final Listener listener) {
        close();
        Request req = new Request.Builder().url("wss://irc-ws.chat.twitch.tv:443").build();
        ws = http.newWebSocket(req, new WebSocketListener() {

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                webSocket.send("CAP REQ :twitch.tv/tags");
                webSocket.send("NICK justinfan" + (10000 + new Random().nextInt(89999)));
                webSocket.send("JOIN #" + channelLogin);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                for (String line : text.split("\\r?\\n")) {
                    if (line.isEmpty()) continue;
                    if (line.startsWith("PING")) {
                        webSocket.send("PONG :tmi.twitch.tv");
                        continue;
                    }
                    if (!line.contains(" PRIVMSG ")) continue;
                    String tags = null;
                    String rest = line;
                    if (line.startsWith("@")) {
                        int sp = line.indexOf(' ');
                        if (sp < 0) continue;
                        tags = line.substring(1, sp);
                        rest = line.substring(sp + 1);
                    }
                    // rest: ":nick!nick@nick.tmi.twitch.tv PRIVMSG #chan :message"
                    int msgStart = rest.indexOf(" :", rest.indexOf("PRIVMSG"));
                    if (msgStart < 0) continue;
                    String message = rest.substring(msgStart + 2);
                    // \u0001ACTION ...\u0001 — /me
                    if (message.startsWith("ACTION ") && message.endsWith("")) {
                        message = "*" + message.substring(7, message.length() - 1) + "*";
                    }
                    String name = null;
                    if (tags != null) {
                        for (String t : tags.split(";")) {
                            if (t.startsWith("display-name=")) {
                                name = t.substring("display-name=".length());
                                break;
                            }
                        }
                    }
                    if (name == null || name.isEmpty()) {
                        int colon = rest.indexOf(':');
                        int space = rest.indexOf(' ');
                        if (colon >= 0 && space > colon) name = rest.substring(colon + 1, space);
                    }
                    if (name == null || message.isEmpty()) continue;
                    listener.onMessage(name, message);
                }
            }
        });
    }

    public void close() {
        if (ws != null) {
            try {
                ws.close(1000, "bye");
            } catch (Exception ignored) {
            }
            ws = null;
        }
    }
}
