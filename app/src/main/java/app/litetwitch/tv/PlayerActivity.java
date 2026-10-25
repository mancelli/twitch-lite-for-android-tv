package app.litetwitch.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayerActivity extends Activity {

    private ExoPlayer player;
    private ChatManager chat;
    private ChatAdapter chatAdapter;
    private TextView status;
    private View chatView;
    private LinearLayout menu;
    private TextView progress;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideProgress = () -> progress.setVisibility(View.GONE);
    private String login;
    private String name;
    private long channelId;
    private boolean isVod;
    private String vodId;
    private boolean resumeOnResume;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        login = getIntent().getStringExtra("login");
        name = getIntent().getStringExtra("name");
        channelId = getIntent().getLongExtra("channelId", -1);
        isVod = "vod".equals(getIntent().getStringExtra("type"));
        vodId = getIntent().getStringExtra("vodId");
        status = findViewById(R.id.status);
        chatView = findViewById(R.id.chat);
        menu = findViewById(R.id.menu);
        progress = findViewById(R.id.progress);
        status.setText((isVod ? "Открываю запись: " : "Подключение к ") + name + "…");

        RecyclerView chatList = findViewById(R.id.chat);
        chatList.setLayoutManager(new LinearLayoutManager(this));
        chatList.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        chatAdapter = new ChatAdapter();
        chatList.setAdapter(chatAdapter);

        if (isVod) {
            chatView.setVisibility(View.GONE);
        } else {
            chat = new ChatManager();
            chat.connect(login, (user, text) -> runOnUiThread(() -> chatAdapter.add(user, text)));
            new Thread(() -> {
                Emotes.loadGlobal();
                Emotes.loadChannel(channelId);
                Emotes.preload(getApplicationContext(), 150);
            }).start();
        }

        new Thread(() -> {
            try {
                String master = isVod ? Gql.vodPlaybackUrl(vodId) : Gql.playbackUrl(login);
                runOnUiThread(() -> startPlayback(master));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Ошибка: " + e.getMessage()));
            }
        }).start();
    }

    private void startPlayback(String masterUrl) {
        DefaultHttpDataSource.Factory upstream = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setUserAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
        HlsMediaSource src = new HlsMediaSource.Factory(
                () -> new RewriteDataSource(upstream.createDataSource(), Http.CLIENT))
                .createMediaSource(MediaItem.fromUri(masterUrl));

        player = new ExoPlayer.Builder(this).build();
        ((PlayerView) findViewById(R.id.player)).setPlayer(player);
        player.setMediaSource(src);
        player.setPlayWhenReady(true);
        player.prepare();
        status.setVisibility(View.GONE);
    }

    // ---------- меню (MENU на пульте) ----------

    private static final class MenuItem {
        final String label;
        final Runnable action;

        MenuItem(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    private void toggleMenu() {
        if (menu.getVisibility() == View.VISIBLE) {
            menu.setVisibility(View.GONE);
            menu.removeAllViews();
            return;
        }
        List<MenuItem> items = new ArrayList<>();
        boolean autoActive = player == null || player.getTrackSelectionParameters()
                .overrides.isEmpty();
        items.add(new MenuItem(autoActive ? "Авто качество  ✓" : "Авто качество", () ->
                setQuality(-1)));
        Tracks tracks = player != null ? player.getCurrentTracks() : null;
        androidx.media3.common.Format current = player != null ? player.getVideoFormat() : null;
        if (tracks != null) {
            for (Tracks.Group g : tracks.getGroups()) {
                if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
                for (int i = 0; i < g.length; i++) {
                    final int trackIndex = i;
                    androidx.media3.common.Format f = g.getTrackFormat(i);
                    String label = trackName(f);
                    // текущий = реально играющий формат, а не isTrackSelected
                    // (при адаптивном выборе он true у всей группы)
                    if (current != null && f.height == current.height
                            && Math.round(f.frameRate) == Math.round(current.frameRate)) {
                        label += "  ✓";
                    }
                    items.add(new MenuItem(label, () -> setQuality(trackIndex)));
                }
            }
        }
        if (!isVod) {
            items.add(new MenuItem("Чат вкл/выкл", this::toggleChat));
            items.add(new MenuItem("Канал: " + name, () -> {
                menu.setVisibility(View.GONE);
                Intent ci = new Intent(this, ChannelActivity.class);
                ci.putExtra("login", login);
                ci.putExtra("name", name);
                ci.putExtra("channelId", channelId);
                startActivity(ci);
            }));
        }
        menu.removeAllViews();
        for (MenuItem it : items) {
            TextView tv = new TextView(this);
            tv.setText(it.label);
            tv.setTextSize(18);
            tv.setPadding(28, 18, 28, 18);
            tv.setTextColor(0xFFEFEFF1);
            tv.setFocusable(true);
            tv.setBackgroundResource(R.drawable.tab_bg);
            tv.setOnClickListener(v -> {
                menu.setVisibility(View.GONE);
                it.action.run();
            });
            menu.addView(tv);
        }
        menu.setVisibility(View.VISIBLE);
        if (menu.getChildCount() > 0) menu.getChildAt(0).requestFocus();
    }

    private String trackName(androidx.media3.common.Format f) {
        if (f.height <= 0) return f.label != null ? f.label : "неизвестно";
        String s = f.height + "p";
        if (f.frameRate > 45) s += "60";
        return s;
    }

    /** index=-1 — авто (сброс), иначе индекс трека в первой видео-группе. */
    private void setQuality(int index) {
        if (player == null) return;
        androidx.media3.common.TrackSelectionParameters.Builder b =
                player.getTrackSelectionParameters().buildUpon();
        if (index < 0) {
            b.clearOverridesOfType(C.TRACK_TYPE_VIDEO);
        } else {
            for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
                if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
                b.setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), index));
                break;
            }
        }
        player.setTrackSelectionParameters(b.build());
    }

    private void toggleChat() {
        chatView.setVisibility(chatView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void showProgress() {
        if (player == null) return;
        long pos = player.getCurrentPosition();
        long dur = player.getDuration();
        String text = fmt(pos) + (dur > 0 ? " / " + fmt(dur) : "");
        progress.setText(text);
        progress.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideProgress);
        handler.postDelayed(hideProgress, 2500);
    }

    private static String fmt(long ms) {
        long s = ms / 1000;
        return s >= 3600
                ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
                : String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    // ---------- кнопки пульта ----------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        int kc = event.getKeyCode();
        if (menu.getVisibility() == View.VISIBLE) {
            if (kc == KeyEvent.KEYCODE_BACK || kc == KeyEvent.KEYCODE_MENU) {
                menu.setVisibility(View.GONE);
                return true;
            }
            return super.dispatchKeyEvent(event); // навигация внутри меню — стандартная
        }
        switch (kc) {
            case KeyEvent.KEYCODE_MENU:
                toggleMenu();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (isVod) {
                    if (player != null) player.setPlayWhenReady(!player.getPlayWhenReady());
                } else {
                    toggleChat();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (player != null) player.setPlayWhenReady(!player.getPlayWhenReady());
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (isVod && player != null) {
                    player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
                    showProgress();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (isVod && player != null) {
                    long dur = player.getDuration();
                    long target = player.getCurrentPosition() + 10000;
                    player.seekTo(dur > 0 ? Math.min(target, dur - 2000) : target);
                    showProgress();
                    return true;
                }
                break;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // уход с экрана плеера (меню→Канал и т.п.) — глушим, иначе звук играет под VOD
        if (player != null && player.getPlayWhenReady()) {
            player.setPlayWhenReady(false);
            resumeOnResume = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && resumeOnResume) {
            player.setPlayWhenReady(true);
            resumeOnResume = false;
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (chat != null) chat.close();
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
