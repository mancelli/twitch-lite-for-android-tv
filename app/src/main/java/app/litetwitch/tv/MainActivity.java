package app.litetwitch.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {

    private static final int REQ_AUTH = 1;

    private StreamAdapter adapter;
    private TextView status;
    private TextView account;
    private TextView tabFollows, tabTop;
    private String currentTab = "top";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Gql.init(this);
        status = findViewById(R.id.status);
        account = findViewById(R.id.account);
        tabFollows = findViewById(R.id.tab_follows);
        tabTop = findViewById(R.id.tab_top);
        RecyclerView grid = findViewById(R.id.grid);
        adapter = new StreamAdapter(this::open);
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        grid.setAdapter(adapter);

        tabTop.setOnClickListener(v -> switchTab("top"));
        tabFollows.setOnClickListener(v -> switchTab("follows"));
        findViewById(R.id.tab_search).setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class)));
        account.setOnClickListener(v -> {
            if (Auth.token(this) != null) {
                Auth.clear(this);
                Gql.setOAuth(null);
                refreshAccount();
                switchTab("top");
            } else {
                startActivityForResult(new Intent(this, AuthActivity.class), REQ_AUTH);
            }
        });

        tabFollows.setOnFocusChangeListener((v, f) -> applyTabColors());
        tabTop.setOnFocusChangeListener((v, f) -> applyTabColors());

        refreshAccount();
        loadTop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccount();
        // на Allwinner после возврата из другой активити RecyclerView не перерисовывает
        // текст ячеек — перебинд + отложенная перерисовка на следующий кадр
        final androidx.recyclerview.widget.RecyclerView grid = findViewById(R.id.grid);
        if (adapter.getItemCount() > 0) {
            adapter.notifyDataSetChanged();
            grid.post(grid::postInvalidateOnAnimation);
            grid.postDelayed(grid::postInvalidateOnAnimation, 300);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_AUTH && resultCode == RESULT_OK) {
            refreshAccount();
            switchTab("follows");
        }
    }

    private void refreshAccount() {
        String login = Auth.login(this);
        account.setText(login == null ? "Войти" : "@" + login + "  (OK — выйти)");
    }

    private void switchTab(String tab) {
        currentTab = tab;
        if ("follows".equals(tab)) {
            String token = Auth.token(this);
            if (token == null) {
                startActivityForResult(new Intent(this, AuthActivity.class), REQ_AUTH);
                return;
            }
            Gql.setOAuth(token);
            loadFollows();
        } else {
            loadTop();
        }
    }

    /** MENU на пульте — обновить список. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            switchTab(currentTab);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean followsActive;

    /** Цвета текста вкладок: в фокусе — всегда белый (фон селектора фиолетовый),
     *  без фокуса — фиолетовый у активной, серый у неактивной. */
    private void markTab(boolean follows) {
        followsActive = follows;
        applyTabColors();
    }

    private void applyTabColors() {
        tabFollows.setTextColor(tabFollows.hasFocus() ? 0xFFFFFFFF
                : (followsActive ? 0xFF9147FF : 0xFFADADB8));
        tabTop.setTextColor(tabTop.hasFocus() ? 0xFFFFFFFF
                : (followsActive ? 0xFFADADB8 : 0xFF9147FF));
    }

    private void loadTop() {
        markTab(false);
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.loading);
        new Thread(() -> {
            try {
                JSONArray streams = Gql.topStreams(30);
                runOnUiThread(() -> {
                    adapter.setData(streams);
                    status.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                android.util.Log.e("TTLite", "top failed", e);
                runOnUiThread(() -> status.setText("Ошибка: " + e.getMessage() + "\nMENU — повтор"));
            }
        }).start();
    }

    private void loadFollows() {
        markTab(true);
        String userId = Auth.userId(this);
        status.setVisibility(View.VISIBLE);
        status.setText("Подписки…");
        new Thread(() -> {
            try {
                JSONArray streams = Gql.followedStreams(userId);
                runOnUiThread(() -> {
                    adapter.setData(streams);
                    status.setVisibility(streams.length() == 0 ? View.VISIBLE : View.GONE);
                    if (streams.length() == 0) status.setText("Никто из подписок сейчас в эфире");
                });
            } catch (Exception e) {
                android.util.Log.e("TTLite", "follows failed", e);
                runOnUiThread(() -> status.setText("Ошибка: " + e.getMessage() + "\nMENU — повтор"));
            }
        }).start();
    }

    private void open(JSONObject node) {
        JSONObject b = node.optJSONObject("broadcaster");
        if (b == null) return;
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("login", b.optString("login"));
        i.putExtra("name", b.optString("displayName"));
        i.putExtra("title", node.optString("title"));
        i.putExtra("channelId", b.optLong("id"));
        startActivity(i);
    }
}
