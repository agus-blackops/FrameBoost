package com.agus.sonora;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Song rows: cover, title (green when playing), artist, liked mark and an overflow button. */
final class TrackAdapter extends BaseAdapter {

    interface MoreHandler {
        void onMore(Track t, View anchor);
    }

    private final Context ctx;
    private final MoreHandler more;
    private List<Track> tracks = new ArrayList<>();

    TrackAdapter(Context ctx, MoreHandler more) {
        this.ctx = ctx;
        this.more = more;
    }

    void setTracks(List<Track> tracks) {
        this.tracks = new ArrayList<>(tracks);
        notifyDataSetChanged();
    }

    List<Track> tracks() {
        return tracks;
    }

    @Override
    public int getCount() {
        return tracks.size();
    }

    @Override
    public Track getItem(int position) {
        return tracks.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private static final class Holder {
        ImageView cover;
        TextView title;
        TextView sub;
        ImageView liked;
        ImageView more;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            LinearLayout row = Ui.row(ctx);
            row.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 4), Ui.dp(ctx, 8));
            row.setLayoutParams(new AbsListView.LayoutParams(Ui.MATCH, Ui.WRAP));
            h = new Holder();
            h.cover = new ImageView(ctx);
            h.cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(h.cover, Ui.lp(Ui.dp(ctx, 48), Ui.dp(ctx, 48)));

            LinearLayout texts = Ui.column(ctx);
            texts.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 8), 0);
            h.title = Ui.text(ctx, "", 16, Ui.TEXT, false);
            h.sub = Ui.text(ctx, "", 13, Ui.SUB, false);
            texts.addView(h.title);
            texts.addView(h.sub);
            row.addView(texts, Ui.weight(1));

            h.liked = Ui.icon(ctx, R.drawable.ic_heart, 20, Ui.GREEN);
            row.addView(h.liked);
            h.more = Ui.iconButton(ctx, R.drawable.ic_more, 44, Ui.SUB, null);
            row.addView(h.more);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setTag(h);
            convertView = row;
        } else {
            h = (Holder) convertView.getTag();
        }
        final Track t = tracks.get(position);
        Track now = PlayerEngine.get(ctx).current();
        h.title.setText(t.title);
        h.title.setTextColor(now != null && now.key.equals(t.key) ? Ui.GREEN : Ui.TEXT);
        h.sub.setText(t.isLive() ? "Radio en vivo · " + t.artist
                : t.isEpisode() ? Ui.episodeInfo(ctx, t) : t.artist);
        h.liked.setVisibility(Library.get(ctx).isLiked(t) ? View.VISIBLE : View.GONE);
        Covers.load(ctx, t, h.cover, Ui.dp(ctx, 48));
        h.more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                more.onMore(t, v);
            }
        });
        return convertView;
    }
}
