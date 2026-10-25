package app.litetwitch.tv;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Чат: ник + текст, эмоуты подставляются картинками по мере загрузки. */
public final class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {

    private static final int MAX = 60;
    private static final int EMOTE_PX = 30;
    private static final Map<String, Bitmap> CACHE = new ConcurrentHashMap<>();

    private static final class Msg {
        final String user, text;

        Msg(String u, String t) {
            user = u;
            text = t;
        }
    }

    private final List<Msg> msgs = new ArrayList<>();

    public void add(String user, String text) {
        if (msgs.size() >= MAX) msgs.remove(0);
        msgs.add(new Msg(user, text));
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Msg m = msgs.get(pos);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int start = sb.length();
        sb.append(m.user).append(':');
        sb.setSpan(new ForegroundColorSpan(0xFF9147FF), start, sb.length(), 0);
        sb.append(' ');
        for (String word : m.text.split(" ")) {
            if (word.isEmpty()) continue;
            Emote e = Emotes.get(word);
            Bitmap bm = e == null ? null : CACHE.get(e.url);
            if (bm != null) {
                Drawable d = new BitmapDrawable(h.itemView.getResources(), bm);
                d.setBounds(0, 0, EMOTE_PX, EMOTE_PX);
                start = sb.length();
                sb.append(word);
                sb.setSpan(new ImageSpan(d, ImageSpan.ALIGN_BASELINE), start, sb.length(), 0);
                sb.append(' ');
            } else {
                sb.append(word).append(' ');
                if (e != null) loadEmote(h, e.url);
            }
        }
        h.text.setText(sb);
    }

    private void loadEmote(VH h, String url) {
        if (CACHE.containsKey(url)) return;
        // applicationContext: загрузка не должна зависеть от жизненного цикла активити
        Glide.with(h.itemView.getContext().getApplicationContext()).asBitmap().override(64, 64).load(url)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap bm, Transition<? super Bitmap> t) {
                        CACHE.put(url, bm);
                        android.util.Log.d("TTLite", "emote img OK " + bm.getWidth() + "x" + bm.getHeight() + " " + url);
                        // нотификация отложенным post: нельзя дергать adapter во время layout-прохода
                        h.text.post(() -> notifyItemRangeChanged(0, msgs.size()));
                    }

                    @Override
                    public void onLoadFailed(Drawable errorDrawable) {
                        android.util.Log.w("TTLite", "emote img FAIL: " + url);
                    }

                    @Override
                    public void onLoadCleared(Drawable old) {
                    }
                });
    }

    @Override
    public int getItemCount() {
        return msgs.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView text;

        VH(View v) {
            super(v);
            text = (TextView) v;
        }
    }
}
