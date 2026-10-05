package com.agus.sonora;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** The play queue: tap a song to jump to it, ✕ to remove it, or clear everything that follows. */
public final class QueueActivity extends Activity implements PlayerEngine.Listener {

    private PlayerEngine player;
    private ListView list;
    private TextView empty;
    private TextView subtitle;
    private QueueAdapter adapter;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        player = PlayerEngine.get(this);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout top = Ui.row(this);
        top.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 4));
        top.addView(Ui.iconButton(this, R.drawable.ic_back, 48, Ui.TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }));
        LinearLayout titles = Ui.column(this);
        titles.setPadding(Ui.dp(this, 8), 0, 0, 0);
        titles.addView(Ui.text(this, "Cola de reproducción", 20, Ui.TEXT, true));
        subtitle = Ui.text(this, "", 12, Ui.SUB, false);
        titles.addView(subtitle);
        top.addView(titles, Ui.weight(1));
        TextView clear = Ui.text(this, "Limpiar", 14, Ui.GREEN, true);
        clear.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        clear.setBackground(Ui.ripple(null));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.clearUpcoming();
            }
        });
        top.addView(clear);
        root.addView(top);

        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(Ui.ripple(null));
        adapter = new QueueAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Row r = adapter.rows.get(position);
                if (r.track != null) player.jumpTo(r.index);
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        empty = Ui.text(this, "No hay nada en la cola.\nElige una canción para empezar.", 15, Ui.SUB, false);
        empty.setSingleLine(false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(this, 32), Ui.dp(this, 64), Ui.dp(this, 32), 0);
        root.addView(empty, Ui.lp(Ui.MATCH, Ui.WRAP));
        setContentView(root);
        player.addListener(this);
        onPlayerChanged();
    }

    @Override
    protected void onDestroy() {
        player.removeListener(this);
        super.onDestroy();
    }

    @Override
    public void onPlayerChanged() {
        adapter.reload();
        boolean none = player.current() == null;
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        list.setVisibility(none ? View.GONE : View.VISIBLE);
        int upcoming = Math.max(0, player.queueTracks().size() - player.queueIndex() - 1);
        subtitle.setText(none ? "" : (player.queueName().isEmpty() ? "" : player.queueName() + " · ")
                + (upcoming == 1 ? "1 canción a continuación" : upcoming + " canciones a continuación"));
    }

    /** Either a section header (track == null) or a song. */
    private static final class Row {
        final String header;
        final Track track;
        final int index; // index in the engine's queue

        Row(String header, Track track, int index) {
            this.header = header;
            this.track = track;
            this.index = index;
        }
    }

    private final class QueueAdapter extends BaseAdapter {
        final List<Row> rows = new ArrayList<>();

        void reload() {
            rows.clear();
            List<Track> all = player.queueTracks();
            int now = player.queueIndex();
            if (now >= 0 && now < all.size()) {
                rows.add(new Row("Reproduciendo ahora", null, -1));
                rows.add(new Row(null, all.get(now), now));
                if (now + 1 < all.size()) rows.add(new Row("A continuación", null, -1));
                for (int i = now + 1; i < all.size(); i++) rows.add(new Row(null, all.get(i), i));
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int p) {
            return rows.get(p);
        }

        @Override
        public long getItemId(int p) {
            return p;
        }

        @Override
        public boolean isEnabled(int p) {
            return rows.get(p).track != null;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int p) {
            return rows.get(p).track == null ? 0 : 1;
        }

        @Override
        public View getView(int p, View convert, ViewGroup parent) {
            final Row r = rows.get(p);
            if (r.track == null) {
                TextView h = convert instanceof TextView ? (TextView) convert
                        : Ui.text(QueueActivity.this, "", 14, Ui.SUB, true);
                h.setText(r.header);
                h.setPadding(Ui.dp(QueueActivity.this, 16), Ui.dp(QueueActivity.this, 20), 0, Ui.dp(QueueActivity.this, 8));
                h.setLayoutParams(new AbsListView.LayoutParams(Ui.MATCH, Ui.WRAP));
                return h;
            }
            LinearLayout row = Ui.row(QueueActivity.this);
            row.setPadding(Ui.dp(QueueActivity.this, 16), Ui.dp(QueueActivity.this, 6), Ui.dp(QueueActivity.this, 4), Ui.dp(QueueActivity.this, 6));
            row.setLayoutParams(new AbsListView.LayoutParams(Ui.MATCH, Ui.WRAP));
            ImageView cover = new ImageView(QueueActivity.this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Covers.load(QueueActivity.this, r.track, cover, Ui.dp(QueueActivity.this, 44));
            row.addView(cover, Ui.lp(Ui.dp(QueueActivity.this, 44), Ui.dp(QueueActivity.this, 44)));
            LinearLayout texts = Ui.column(QueueActivity.this);
            texts.setPadding(Ui.dp(QueueActivity.this, 12), 0, Ui.dp(QueueActivity.this, 8), 0);
            boolean current = r.index == player.queueIndex();
            texts.addView(Ui.text(QueueActivity.this, r.track.title, 15, current ? Ui.GREEN : Ui.TEXT, false));
            texts.addView(Ui.text(QueueActivity.this, r.track.artist, 12, Ui.SUB, false));
            row.addView(texts, Ui.weight(1));
            row.addView(Ui.iconButton(QueueActivity.this, R.drawable.ic_close, 44, Ui.SUB, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    player.removeAt(r.index);
                }
            }));
            return row;
        }
    }
}
