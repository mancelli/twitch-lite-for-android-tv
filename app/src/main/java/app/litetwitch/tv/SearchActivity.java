package app.litetwitch.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

public final class SearchActivity extends Activity {

    private StreamAdapter adapter;
    private TextView status;

    /** TV-навигация: из поля DOWN — к кнопке/результатам, ENTER — искать. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            EditText query = findViewById(R.id.query);
            View focused = getCurrentFocus();
            if (focused == query) {
                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        findViewById(R.id.go).requestFocus();
                        return true;
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                        search(query.getText().toString());
                        return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        EditText query = findViewById(R.id.query);
        TextView go = findViewById(R.id.go);
        status = findViewById(R.id.search_status);
        RecyclerView results = findViewById(R.id.results);
        adapter = new StreamAdapter(this::open);
        results.setLayoutManager(new GridLayoutManager(this, 3));
        results.setAdapter(adapter);

        query.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                search(query.getText().toString());
                return true;
            }
            return false;
        });
        go.setOnClickListener(v -> {
            query.clearFocus();
            search(query.getText().toString());
        });
        go.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start());
    }

    private void search(String q) {
        if (q == null || q.trim().isEmpty()) return;
        android.util.Log.d("TTLite", "search start: " + q);
        status.setVisibility(View.VISIBLE);
        status.setText("Поиск: " + q.trim() + "…");
        new Thread(() -> {
            try {
                JSONArray arr = Gql.searchStreams(q.trim());
                android.util.Log.d("TTLite", "search done: " + arr.length());
                runOnUiThread(() -> {
                    adapter.setData(arr);
                    status.setVisibility(arr.length() == 0 ? View.VISIBLE : View.GONE);
                    if (arr.length() == 0) status.setText("Ничего не найдено");
                });
            } catch (Throwable e) {
                android.util.Log.e("TTLite", "search failed", e);
                runOnUiThread(() -> status.setText("Ошибка: " + e));
            }
        }).start();
    }

    private void open(JSONObject node) {
        JSONObject b = node.optJSONObject("broadcaster");
        if (b == null) return;
        android.content.Intent i = new android.content.Intent(this, PlayerActivity.class);
        i.putExtra("login", b.optString("login"));
        i.putExtra("name", b.optString("displayName"));
        i.putExtra("title", node.optString("title"));
        i.putExtra("channelId", b.optLong("id"));
        startActivity(i);
    }
}
