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
import android.widget.ScrollView;
import android.widget.Toast;
import android.content.DialogInterface;
import android.content.Intent;
import java.util.ArrayList;
import java.util.List;
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
    private FrameLayout stage;
    private View coverPane;
    private ScrollView lyricsScroll;
    private LinearLayout lyricsBox;
    private boolean lyricsMode;
    private String lyricsKey;
    private Lyrics.Result lyricsResult;
    private final List<TextView> lyricLines = new ArrayList<>();
    private int litLine = -2;
    private TextView timerLabel;
    private TextView speedLabel;
    private ImageView timerIcon;
    private ImageView lyricsIcon;
    private TextView speedValue;
    private TextView skipBack;
    private TextView skipFwd;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 300);
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
        stage = new FrameLayout(this);
        LinearLayout coverHolder = Ui.column(this);
        coverHolder.addView(new View(this), new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        LinearLayout.LayoutParams clp = Ui.lp(coverSize, coverSize);
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        coverHolder.addView(cover, clp);
        coverHolder.addView(new View(this), new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        coverPane = coverHolder;
        stage.addView(coverHolder, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        lyricsScroll = new ScrollView(this);
        lyricsScroll.setVerticalScrollBarEnabled(false);
        lyricsScroll.setVisibility(View.GONE);
        lyricsBox = Ui.column(this);
        lyricsScroll.addView(lyricsBox);
        stage.addView(lyricsScroll, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        root.addView(stage, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));

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
        FrameLayout shuffleBox = new FrameLayout(this);
        shuffleBox.addView(shuffle, new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        skipBack = skipButton("−15", -15000);
        shuffleBox.addView(skipBack, new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        controls.addView(shuffleBox, Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)));
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
        FrameLayout repeatBox = new FrameLayout(this);
        repeatBox.addView(repeat, new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        skipFwd = skipButton("+30", 30000);
        repeatBox.addView(skipFwd, new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        controls.addView(repeatBox, Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)));
        root.addView(controls);

        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.dp(this, 14), 0, 0);
        View timerCell = actionCell(R.drawable.ic_timer, "Temporizador", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sleepDialog();
            }
        });
        timerIcon = (ImageView) ((LinearLayout) timerCell).getChildAt(0);
        timerLabel = (TextView) ((LinearLayout) timerCell).getChildAt(1);
        actions.addView(timerCell, Ui.weight(1));
        View speedCell = actionCell(R.drawable.ic_timer, "Velocidad", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                speedDialog();
            }
        });
        ((LinearLayout) speedCell).removeViewAt(0);
        speedValue = Ui.text(this, "1×", 15, Ui.TEXT, true);
        speedValue.setGravity(Gravity.CENTER);
        ((LinearLayout) speedCell).addView(speedValue, 0, Ui.lp(Ui.MATCH, Ui.dp(this, 32)));
        speedLabel = (TextView) ((LinearLayout) speedCell).getChildAt(1);
        actions.addView(speedCell, Ui.weight(1));
        View lyricsCell = actionCell(R.drawable.ic_lyrics, "Letra", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLyricsMode(!lyricsMode);
            }
        });
        lyricsIcon = (ImageView) ((LinearLayout) lyricsCell).getChildAt(0);
        actions.addView(lyricsCell, Ui.weight(1));
        actions.addView(actionCell(R.drawable.ic_queue, "Cola", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(NowPlayingActivity.this, QueueActivity.class));
            }
        }), Ui.weight(1));
        actions.addView(actionCell(R.drawable.ic_equalizer, "Ecualizador", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(NowPlayingActivity.this, EqualizerActivity.class));
            }
        }), Ui.weight(1));
        root.addView(actions);

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
        player.saveSession();
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

    /** Podcast skip button (replaces shuffle / repeat while an episode plays). */
    private TextView skipButton(String label, final int deltaMs) {
        TextView t = Ui.text(this, label, 15, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.ripple(Ui.oval(0x22FFFFFF)));
        t.setVisibility(View.GONE);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.skipBy(deltaMs);
            }
        });
        return t;
    }

    /** Small icon with a caption underneath, used for the secondary actions row. */
    private View actionCell(int icon, String label, View.OnClickListener l) {
        LinearLayout cell = Ui.column(this);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setBackground(Ui.ripple(null));
        cell.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        cell.addView(Ui.icon(this, icon, 32, Ui.TEXT));
        TextView t = Ui.text(this, label, 10, Ui.SUB, false);
        t.setGravity(Gravity.CENTER);
        cell.addView(t, Ui.lp(Ui.MATCH, Ui.WRAP));
        cell.setOnClickListener(l);
        return cell;
    }

    private void sleepDialog() {
        final int[] minutes = {0, 5, 10, 15, 30, 45, 60, -1};
        String[] items = {"Desactivado", "5 minutos", "10 minutos", "15 minutos", "30 minutos",
                "45 minutos", "1 hora", "Al terminar esta canción"};
        int mode = player.sleepMode();
        int checked = mode == PlayerEngine.SLEEP_OFF ? 0
                : mode == PlayerEngine.SLEEP_END_OF_TRACK ? 7 : -1;
        Dialogs.builder(this)
                .setTitle("Temporizador de apagado")
                .setSingleChoiceItems(items, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (minutes[which] == 0) player.cancelSleep();
                        else if (minutes[which] < 0) player.sleepAtEndOfTrack();
                        else player.setSleepTimer(minutes[which]);
                        d.dismiss();
                        if (minutes[which] != 0) {
                            Toast.makeText(NowPlayingActivity.this, minutes[which] < 0
                                    ? "La música se detendrá al terminar esta canción"
                                    : "La música se detendrá en " + minutes[which] + " min", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};

    static String speedText(float s) {
        return (s == (int) s ? String.valueOf((int) s) : String.valueOf(s)) + "×";
    }

    private void speedDialog() {
        Track t = player.current();
        if (t != null && t.isLive()) {
            Toast.makeText(this, "La radio en vivo no admite cambiar la velocidad", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[SPEEDS.length];
        int checked = 2;
        for (int i = 0; i < SPEEDS.length; i++) {
            items[i] = speedText(SPEEDS[i]) + (SPEEDS[i] == 1f ? "  (normal)" : "");
            if (SPEEDS[i] == player.speed()) checked = i;
        }
        Dialogs.builder(this)
                .setTitle("Velocidad de reproducción")
                .setSingleChoiceItems(items, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        player.setSpeed(SPEEDS[which]);
                        d.dismiss();
                    }
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    // ---------------------------------------------------------------- lyrics

    private void setLyricsMode(boolean on) {
        lyricsMode = on;
        lyricsScroll.setVisibility(on ? View.VISIBLE : View.GONE);
        coverPane.setVisibility(on ? View.GONE : View.VISIBLE);
        lyricsIcon.setColorFilter(on ? Ui.GREEN : Ui.TEXT);
        if (on) loadLyrics(false);
    }

    private void loadLyrics(boolean force) {
        final Track t = player.current();
        if (t == null) return;
        if (!force && t.key.equals(lyricsKey) && lyricsResult != null) return;
        lyricsKey = t.key;
        lyricsResult = null;
        showLyricsMessage(t.isLive() ? "La radio en vivo no tiene letra"
                : t.isEpisode() ? "Los podcasts no tienen letra" : "Buscando letra…", false);
        if (t.isLive() || t.isEpisode()) return;
        Lyrics.load(this, t, new Lyrics.Callback() {
            @Override
            public void onLyrics(Track track, Lyrics.Result r) {
                if (!track.key.equals(lyricsKey)) return; // song changed meanwhile
                if (r == null) {
                    showLyricsMessage("No se pudo cargar la letra. Revisa tu conexión.", true);
                    return;
                }
                lyricsResult = r;
                showLyrics(r);
            }
        });
    }

    private void showLyricsMessage(String msg, final boolean retry) {
        lyricsBox.removeAllViews();
        lyricLines.clear();
        litLine = -2;
        TextView m = Ui.text(this, msg, 16, Ui.SUB, false);
        m.setSingleLine(false);
        m.setGravity(Gravity.CENTER);
        m.setPadding(Ui.dp(this, 16), Ui.dp(this, 80), Ui.dp(this, 16), 0);
        if (retry) {
            m.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadLyrics(true);
                }
            });
            m.setText(msg + "\nToca para reintentar");
        }
        lyricsBox.addView(m);
    }

    private void showLyrics(final Lyrics.Result r) {
        lyricsBox.removeAllViews();
        lyricLines.clear();
        litLine = -2;
        if (r.instrumental && r.synced.isEmpty() && r.plain.isEmpty()) {
            showLyricsMessage("Instrumental ♪", false);
            return;
        }
        if (!r.found()) {
            showLyricsMessage("No encontramos la letra de esta canción", false);
            return;
        }
        int pad = Ui.dp(this, 8);
        lyricsBox.addView(new View(this), Ui.lp(Ui.MATCH, Ui.dp(this, 40)));
        if (!r.synced.isEmpty()) {
            for (int i = 0; i < r.synced.size(); i++) {
                final Lyrics.Line line = r.synced.get(i);
                TextView tv = Ui.text(this, line.text.isEmpty() ? "♪" : line.text, 22, Ui.SUB, true);
                tv.setSingleLine(false);
                tv.setPadding(0, pad, 0, pad);
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        player.seekTo((int) line.ms);
                    }
                });
                lyricsBox.addView(tv);
                lyricLines.add(tv);
            }
        } else {
            TextView tv = Ui.text(this, r.plain, 18, Ui.TEXT, false);
            tv.setSingleLine(false);
            tv.setLineSpacing(0, 1.25f);
            lyricsBox.addView(tv);
        }
        lyricsBox.addView(new View(this), Ui.lp(Ui.MATCH, Ui.dp(this, 120)));
        updateLyricsHighlight();
    }

    /** Lights up the line being sung and keeps it in the middle of the view. */
    private void updateLyricsHighlight() {
        if (!lyricsMode || lyricsResult == null || lyricLines.isEmpty()) return;
        int now = Lyrics.currentLine(lyricsResult.synced, player.position());
        if (now == litLine) return;
        litLine = now;
        for (int i = 0; i < lyricLines.size(); i++) {
            TextView tv = lyricLines.get(i);
            boolean on = i == now;
            tv.setTextColor(on ? Ui.TEXT : i < now ? 0x66FFFFFF : 0x99B3B3B3);
        }
        if (now >= 0) {
            final TextView cur = lyricLines.get(now);
            lyricsScroll.post(new Runnable() {
                @Override
                public void run() {
                    int target = cur.getTop() - (lyricsScroll.getHeight() - cur.getHeight()) / 2;
                    lyricsScroll.smoothScrollTo(0, Math.max(0, target));
                }
            });
        }
    }

    private void menu(View anchor) {
        final Track t = player.current();
        if (t == null) return;
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, 1, 0, "Añadir a playlist…");
        m.add(0, 2, 0, "Cola de reproducción");
        m.add(0, 3, 0, "Compartir");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == 1) Dialogs.addToPlaylist(NowPlayingActivity.this, t);
                else if (item.getItemId() == 2) startActivity(new Intent(NowPlayingActivity.this, QueueActivity.class));
                else if (item.getItemId() == 3) {
                    Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "Estoy escuchando \"" + t.title + "\" de " + t.artist + " en Sonora");
                    startActivity(Intent.createChooser(send, "Compartir canción"));
                }
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
        badge.setText(t.isLive() ? "● EN VIVO" : t.isEpisode() ? "PODCAST · EPISODIO COMPLETO" : "VISTA PREVIA · 30 s");
        boolean episode = t.isEpisode();
        skipBack.setVisibility(episode ? View.VISIBLE : View.GONE);
        skipFwd.setVisibility(episode ? View.VISIBLE : View.GONE);
        shuffle.setVisibility(episode ? View.GONE : View.VISIBLE);
        repeat.setVisibility(episode ? View.GONE : View.VISIBLE);
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
        speedValue.setText(speedText(player.speed()));
        speedValue.setTextColor(player.speed() == 1f ? Ui.TEXT : Ui.GREEN);
        int sleep = player.sleepMode();
        timerIcon.setColorFilter(sleep == PlayerEngine.SLEEP_OFF ? Ui.TEXT : Ui.GREEN);
        if (lyricsMode && !t.key.equals(lyricsKey)) loadLyrics(false);
        updateProgress();
    }

    private void updateProgress() {
        if (dragging) return;
        Track t = player.current();
        int p = player.position();
        elapsed.setText(Ui.time(p));
        updateLyricsHighlight();
        int sleep = player.sleepMode();
        timerLabel.setText(sleep == PlayerEngine.SLEEP_TIMED ? Ui.time(player.sleepRemainingMs())
                : sleep == PlayerEngine.SLEEP_END_OF_TRACK ? "Fin canción" : "Temporizador");
        timerLabel.setTextColor(sleep == PlayerEngine.SLEEP_OFF ? Ui.SUB : Ui.GREEN);
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
