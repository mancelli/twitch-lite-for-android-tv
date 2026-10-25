package app.litetwitch.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

/** Страница канала: профиль, стрим (если в эфире) и список записей. */
public final class ChannelActivity extends Activity {

    private StreamAdapter adapter;
    private TextView status;
    private String login, name;
    private long channelId;
    private String avatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel);
        login = getIntent().getStringExtra("login");
        name = getIntent().getStringExtra("name");
        channelId = getIntent().getLongExtra("channelId", -1);
        status = findViewById(R.id.channel_status);
        adapter = new StreamAdapter(this::open);
        RecyclerView grid = findViewById(R.id.vods);
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        grid.setAdapter(adapter);
        load();
    }

    private void load() {
        status.setVisibility(View.VISIBLE);
        status.setText("Загрузка канала " + name + "…");
        new Thread(() -> {
            try {
                JSONObject user = Gql.channelInfo(login);
                avatar = user.optString("profileImageURL");
                JSONObject stream = user.optJSONObject("stream");
                JSONArray vodEdges = user.getJSONObject("videos").getJSONArray("edges");
                JSONArray vods = new JSONArray();
                for (int i = 0; i < vodEdges.length(); i++) {
                    JSONObject v = vodEdges.getJSONObject(i).getJSONObject("node");
                    v.put("vodId", v.getString("id"));
                    v.put("thumb", avatar);
                    vods.put(v);
                }
                runOnUiThread(() -> render(user, stream, vods));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Ошибка: " + e.getMessage()));
            }
        }).start();
    }

    private void render(JSONObject user, JSONObject stream, JSONArray vods) {
        ImageView av = findViewById(R.id.avatar);
        Glide.with(this).load(avatar).circleCrop().into(av);
        ((TextView) findViewById(R.id.ch_name)).setText(user.optString("displayName"));
        JSONObject fol = user.optJSONObject("followers");
        ((TextView) findViewById(R.id.ch_meta)).setText(
                (fol != null ? fol.optLong("totalCount") + " подписчиков" : ""));

        TextView watch = findViewById(R.id.watch_live);
        TextView liveInfo = findViewById(R.id.live_info);
        if (stream != null) {
            JSONObject game = stream.optJSONObject("game");
            liveInfo.setText("В ЭФИРЕ: " + stream.optString("title") + " · "
                    + (game == null ? "" : game.optString("displayName") + " · ")
                    + stream.optLong("viewersCount") + " зрителей");
            watch.setVisibility(View.VISIBLE);
            watch.setOnClickListener(v -> openLive(stream));
        } else {
            liveInfo.setText("Не в эфире");
            watch.setVisibility(View.GONE);
        }

        adapter.setData(vods);
        status.setVisibility(vods.length() == 0 ? View.VISIBLE : View.GONE);
        if (vods.length() == 0) status.setText("Записей нет");
        // Allwinner-глюк: первый биндинг иногда не отрисовывается — перебинд отложенно
        RecyclerView grid = findViewById(R.id.vods);
        grid.postDelayed(() -> {
            adapter.notifyDataSetChanged();
            grid.postInvalidateOnAnimation();
        }, 400);
    }

    private void openLive(JSONObject stream) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("login", login);
        i.putExtra("name", name);
        i.putExtra("title", stream.optString("title"));
        i.putExtra("channelId", channelId);
        startActivity(i);
    }

    private void open(JSONObject node) {
        if (!node.has("vodId")) {
            openLive(node);
            return;
        }
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("type", "vod");
        i.putExtra("vodId", node.optString("vodId"));
        i.putExtra("name", node.optString("title"));
        i.putExtra("login", login);
        startActivity(i);
    }
}
