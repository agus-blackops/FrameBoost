package com.agus.sonora;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

/** Full-screen player: big cover, seek bar and transport controls. */
public final class NowPlayingActivity extends Activity implements PlayerEngine.Listener, Library.Listener {

    private PlayerEngine player;
    private Library lib;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private TextView source;
    private ImageView cover;
    private TextView title;
    private TextView artist;
    private TextView badge;
    private ImageView like;
    private SeekBar seek;
    private TextView elapsed;
    private TextView remaining;
    private ImageView shuffle;
    private ImageView play;
    private ProgressBar buffering;
    private ImageView repeat;
    private boolean dragging;
    private String shownKey;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        player = PlayerEngine.get(this);
        lib = Library.get(this);

        root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        int side = Ui.dp(this, 24);
        root.setPadding(side, Ui.dp(this, 8), side, Ui.dp(this, 24));

        // top bar
        LinearLayout top = Ui.row(this);
        top.addView(Ui.iconButton(this, R.drawable.ic_down, 44, Ui.TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        }));
        LinearLayout mid = Ui.column(this);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView from = Ui.text(this, "REPRODUCIENDO DESDE", 10, Ui.SUB, false);
        from.setLetterSpacing(0.1f);
        mid.addView(from);
        source = Ui.text(this, "", 13, Ui.TEXT, true);
        mid.addView(source);
        top.addView(mid, Ui.weight(1));
        top.addView(Ui.iconButton(this, R.drawable.ic_more, 44, Ui.TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                menu(v);
            }
        }));
        root.addView(top);

        // cover: square, as wide as the screen allows but leaving room for the controls
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int coverSize = Math.min(dm.widthPixels - 2 * side, (int) (dm.heightPixels * 0.48f));
        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(Ui.rounded(Ui.CARD, Ui.dp(this, 6)));
        cover.setClipToOutline(true);
        cover.setElevation(Ui.dp(this, 16));
        root.addView(new View(this), new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        LinearLayout.LayoutParams clp = Ui.lp(coverSize, coverSize);
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(cover, clp);
        root.addView(new View(this), new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));

        // title + like
        LinearLayout info = Ui.row(this);
        LinearLayout texts = Ui.column(this);
        title = Ui.text(this, "", 22, Ui.TEXT, true);
        title.setSelected(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        artist = Ui.text(this, "", 15, Ui.SUB, false);
        texts.addView(title);
        texts.addView(artist);
        badge = Ui.text(this, "", 11, Ui.GREEN, true);
        badge.setLetterSpacing(0.08f);
        badge.setPadding(0, Ui.dp(this, 4), 0, 0);
        texts.addView(badge);
        info.addView(texts, Ui.weight(1));
        like = Ui.iconButton(this, R.drawable.ic_heart_outline, 48, Ui.TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lib.toggleLike(player.current());
            }
        });
        info.addView(like);
        root.addView(info);

        // seek bar
        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setProgressTintList(ColorStateList.valueOf(Ui.TEXT));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(0x4DFFFFFF));
        seek.setThumbTintList(ColorStateList.valueOf(Ui.TEXT));
        seek.setPadding(Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 4));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) elapsed.setText(Ui.time((long) player.duration() * progress / 1000));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                player.seekTo((int) ((long) player.duration() * s.getProgress() / 1000));
            }
        });
        LinearLayout.LayoutParams slp = Ui.lp(Ui.MATCH, Ui.WRAP);
        slp.setMargins(-Ui.dp(this, 6), Ui.dp(this, 8), -Ui.dp(this, 6), 0);
        root.addView(seek, slp);
        LinearLayout times = Ui.row(this);
        elapsed = Ui.text(this, "0:00", 12, Ui.SUB, false);
        remaining = Ui.text(this, "0:00", 12, Ui.SUB, false);
        remaining.setGravity(Gravity.END);
        times.addView(elapsed, Ui.weight(1));
        times.addView(remaining, Ui.weight(1));
        root.addView(times);

        // transport
        LinearLayout controls = Ui.row(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, Ui.dp(this, 12), 0, 0);
        shuffle = Ui.iconButton(this, R.drawable.ic_shuffle, 48, Ui.TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.toggleShuffle();
            }
        });
        controls.addView(shuffle);
        controls.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        controls.addView(Ui.iconButton(this, R.drawable.ic_prev, 56, Ui.TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.previous();
            }
        }));
        controls.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));

        FrameLayout playBox = new FrameLayout(this);
        play = new ImageView(this);
        play.setImageResource(R.drawable.ic_play);
        play.setColorFilter(0xFF000000);
        play.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pp = Ui.dp(this, 18);
        play.setPadding(pp, pp, pp, pp);
        play.setBackground(Ui.ripple(Ui.oval(Ui.TEXT)));
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.togglePlay();
            }
        });
        playBox.addView(play, new FrameLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 68)));
        buffering = new ProgressBar(this);
        buffering.setIndeterminateTintList(ColorStateList.valueOf(Ui.GREEN));
        playBox.addView(buffering, new FrameLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 68)));
        controls.addView(playBox, Ui.lp(Ui.dp(this, 68), Ui.dp(this, 68)));

        controls.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        controls.addView(Ui.iconButton(this, R.drawable.ic_next, 56, Ui.TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.next();
            }
        }));
        controls.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        repeat = Ui.iconButton(this, R.drawable.ic_repeat, 48, Ui.TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.cycleRepeat();
            }
        });
        controls.addView(repeat);
        root.addView(controls);

        setContentView(root);
        player.addListener(this);
        lib.addListener(this);
        onPlayerChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override
    protected void onDestroy() {
        player.removeListener(this);
        lib.removeListener(this);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        close();
    }

    private void close() {
        finish();
        overridePendingTransition(R.anim.stay, R.anim.slide_down);
    }

    private void menu(View anchor) {
        final Track t = player.current();
        if (t == null) return;
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, 1, 0, "Añadir a playlist…");
        m.add(0, 2, 0, "Cola de reproducción");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == 1) Dialogs.addToPlaylist(NowPlayingActivity.this, t);
                else if (item.getItemId() == 2) Dialogs.showQueue(NowPlayingActivity.this);
                return true;
            }
        });
        pm.show();
    }

    @Override
    public void onLibraryChanged() {
        onPlayerChanged();
    }

    @Override
    public void onPlayerChanged() {
        Track t = player.current();
        if (t == null) {
            close();
            return;
        }
        if (!t.key.equals(shownKey)) {
            shownKey = t.key;
            title.setText(t.title);
            artist.setText(t.artist);
            Covers.load(this, t, cover, Math.min(1024, getResources().getDisplayMetrics().widthPixels));
            root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{MainActivity.darken(Covers.accent(t)), Ui.BG, Ui.BG}));
        }
        source.setText(player.queueName().isEmpty() ? t.album : player.queueName());
        badge.setVisibility(t.remote ? View.VISIBLE : View.GONE);
        badge.setText(t.isLive() ? "● EN VIVO" : "VISTA PREVIA · 30 s");
        seek.setEnabled(!t.isLive());
        boolean liked = lib.isLiked(t);
        like.setImageResource(liked ? R.drawable.ic_heart : R.drawable.ic_heart_outline);
        like.setColorFilter(liked ? Ui.GREEN : Ui.TEXT);
        boolean loading = player.isBuffering() && player.isActive();
        buffering.setVisibility(loading ? View.VISIBLE : View.GONE);
        play.setImageResource(player.isActive() ? R.drawable.ic_pause : R.drawable.ic_play);
        shuffle.setColorFilter(player.isShuffle() ? Ui.GREEN : Ui.TEXT);
        int mode = player.repeatMode();
        repeat.setImageResource(mode == PlayerEngine.REPEAT_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        repeat.setColorFilter(mode == PlayerEngine.REPEAT_OFF ? Ui.TEXT : Ui.GREEN);
        updateProgress();
    }

    private void updateProgress() {
        if (dragging) return;
        Track t = player.current();
        int p = player.position();
        elapsed.setText(Ui.time(p));
        if (t != null && t.isLive()) {
            seek.setProgress(1000);
            remaining.setText("EN VIVO");
            return;
        }
        int d = player.duration();
        seek.setProgress(d > 0 ? (int) (1000L * p / d) : 0);
        remaining.setText(d > 0 ? "-" + Ui.time(d - p) : "--:--");
    }
}
