package app.litetwitch.tv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

public final class StreamAdapter extends RecyclerView.Adapter<StreamAdapter.VH> {

    public interface OnOpen {
        void open(JSONObject node);
    }

    private final OnOpen listener;
    private JSONArray items = new JSONArray();

    public StreamAdapter(OnOpen listener) {
        this.listener = listener;
    }

    public void setData(JSONArray arr) {
        items = arr;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.length();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stream, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        JSONObject n = items.optJSONObject(pos);
        if (n == null) return;
        h.itemView.setOnFocusChangeListener((v, focused) -> v.animate()
                .scaleX(focused ? 1.05f : 1f)
                .scaleY(focused ? 1.05f : 1f)
                .setDuration(120)
                .start());
        h.itemView.setOnClickListener(v -> listener.open(n));
        if (n.has("vodId")) {
            // карточка записи: аватар канала + длительность + просмотры
            Glide.with(h.thumb).load(n.optString("thumb")).centerCrop().into(h.thumb);
            h.title.setText(n.optString("title"));
            h.meta.setText(fmtDur(n.optLong("lengthSeconds")) + " · "
                    + n.optLong("viewCount") + " просмотров");
            return;
        }
        Glide.with(h.thumb).load(n.optString("previewImageURL")).centerCrop().into(h.thumb);
        h.title.setText(n.optString("title"));
        JSONObject b = n.optJSONObject("broadcaster");
        JSONObject g = n.optJSONObject("game");
        String name = b == null ? "?" : b.optString("displayName");
        String game = g == null ? "" : g.optString("displayName");
        long viewers = n.optLong("viewersCount");
        h.meta.setText(name + " · " + game + " · " + viewers);
    }

    private static String fmtDur(long s) {
        return s >= 3600 ? String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
                : String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60);
    }

    static final class VH extends RecyclerView.ViewHolder {
        final AspectImageView thumb;
        final TextView title;
        final TextView meta;

        VH(View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            title = v.findViewById(R.id.title);
            meta = v.findViewById(R.id.meta);
        }
    }
}
