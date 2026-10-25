package app.litetwitch.tv;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import org.json.JSONObject;

/** Экран device-авторизации: показывает код, опрашивает токен. */
public final class AuthActivity extends Activity {

    private volatile boolean running = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        TextView authStatus = findViewById(R.id.auth_status);
        TextView codeView = findViewById(R.id.code);
        authStatus.setText("Получение кода…");

        new Thread(() -> {
            try {
                DeviceAuth.Flow flow = DeviceAuth.begin();
                runOnUiThread(() -> {
                    codeView.setText(flow.userCode);
                    authStatus.setText("Ожидание подтверждения…");
                });
                long deadline = System.currentTimeMillis() + 15L * 60 * 1000;
                while (running && System.currentTimeMillis() < deadline) {
                    Thread.sleep(5000);
                    if (!running) break;
                    try {
                        String token = DeviceAuth.poll(flow.deviceCode);
                        JSONObject v = DeviceAuth.validate(token);
                        String login = v.getString("login");
                        String userId = v.getString("user_id");
                        Auth.save(this, token, login, userId);
                        Gql.setOAuth(token);
                        runOnUiThread(() -> {
                            authStatus.setText("Готово: " + login);
                            setResult(RESULT_OK);
                            finish();
                        });
                        return;
                    } catch (DeviceAuth.Pending pending) {
                        // ещё не подтвердил — ждём дальше
                    }
                }
                if (running) runOnUiThread(() -> authStatus.setText("Время ожидания истекло"));
            } catch (InterruptedException ie) {
                // выход с экрана
            } catch (Exception e) {
                runOnUiThread(() -> authStatus.setText("Ошибка: " + e.getMessage()));
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        running = false;
        super.onDestroy();
    }
}
