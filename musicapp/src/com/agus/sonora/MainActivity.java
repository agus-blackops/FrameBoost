package com.agus.sonora;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MainActivity extends Activity implements PlayerEngine.Listener, Library.Listener {

    private static final int REQ_PERMS = 1;

    private Library lib;
    private PlayerEngine player;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private FrameLayout content;
    private final Deque<Screen> stack = new ArrayDeque<>();
    private int tab = 0;
    private final List<View> tabViews = new ArrayList<>();

    // mini player
    private LinearLayout mini;
    private ImageView miniCover;
    private TextView miniTitle;
    private TextView miniArtist;
    private ImageView miniLike;
    private ImageView miniPlay;
    private ProgressBar miniProgress;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    // ================================================================ lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        lib = Library.get(this);
        player = PlayerEngine.get(this);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        root.addView(buildMiniPlayer());
        root.addView(buildBottomNav());
        setContentView(root);

        player.restoreSession(); // paused where the user left off
        lib.addListener(this);
        player.addListener(this);
        selectTab(0);
        onPlayerChanged();
        askPermissions();
        lib.reloadLocal();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(ticker);
        onPlayerChanged();
        lib.loadOnline(); // no-op for shelves already loaded or loading

    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        player.saveSession();
    }

    @Override
    protected void onDestroy() {
        lib.removeListener(this);
        player.removeListener(this);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (stack.size() > 1) {
            stack.pop();
            render();
        } else if (tab != 0) {
            selectTab(0);
        } else {
            // Keep the music going; just go to the home screen like Spotify does.
            moveTaskToBack(true);
        }
    }

    // ================================================================ permissions

    private void askPermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        List<String> need = new ArrayList<>();
        if (!Library.hasAudioPermission(this)) {
            need.add(Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO
                    : Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != 0) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMS);
    }

    private void requestAudioAgain() {
        if (Build.VERSION.SDK_INT < 23) return;
        String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (shouldShowRequestPermissionRationale(p) || !getPreferences(0).getBoolean("asked", false)) {
            getPreferences(0).edit().putBoolean("asked", true).apply();
            requestPermissions(new String[]{p}, REQ_PERMS);
        } else {
            // Permanently denied: send the user to the app's settings page.
            Toast.makeText(this, "Activa el permiso de \"Música y audio\"", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        getPreferences(0).edit().putBoolean("asked", true).apply();
        lib.reloadLocal();
    }

    // ================================================================ listeners

    @Override
    public void onLibraryChanged() {
        Screen s = stack.peek();
        if (s != null) s.onLibraryChanged();
        updateMini();
    }

    @Override
    public void onPlayerChanged() {
        updateMini();
        Screen s = stack.peek();
        if (s != null) s.onPlayerChanged();
    }

    // ================================================================ navigation

    /** A page in the content area. */
    private abstract class Screen {
        View view;

        abstract View create();

        /** Default: rebuild the page. */
        void onLibraryChanged() {
            render();
        }

        void onPlayerChanged() {
        }
    }

    private void push(Screen s) {
        stack.push(s);
        render();
    }

    private void render() {
        Screen s = stack.peek();
        if (s == null) return;
        hideKeyboard();
        content.removeAllViews();
        s.view = s.create();
        content.addView(s.view, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
    }

    private void selectTab(int index) {
        tab = index;
        stack.clear();
        if (index == 0) stack.push(new HomeScreen());
        else if (index == 1) stack.push(new SearchScreen());
        else if (index == 2) stack.push(new PodcastsScreen());
        else stack.push(new LibraryScreen());
        render();
        for (int i = 0; i < tabViews.size(); i++) {
            LinearLayout item = (LinearLayout) tabViews.get(i);
            int color = i == index ? Ui.TEXT : Ui.MUTED;
            ((ImageView) item.getChildAt(0)).setColorFilter(color);
            ((TextView) item.getChildAt(1)).setTextColor(color);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && content != null) imm.hideSoftInputFromWindow(content.getWindowToken(), 0);
    }

    private View buildBottomNav() {
        LinearLayout nav = Ui.row(this);
        nav.setBackgroundColor(0xF0000000);
        nav.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        String[] labels = {"Inicio", "Buscar", "Podcasts", "Tu biblioteca"};
        int[] icons = {R.drawable.ic_home, R.drawable.ic_search, R.drawable.ic_podcast, R.drawable.ic_library};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout item = Ui.column(this);
            item.setGravity(Gravity.CENTER);
            item.addView(Ui.icon(this, icons[i], 26, Ui.MUTED));
            TextView label = Ui.text(this, labels[i], 11, Ui.MUTED, false);
            label.setGravity(Gravity.CENTER);
            item.addView(label, Ui.lp(Ui.MATCH, Ui.WRAP));
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectTab(index);
                }
            });
            nav.addView(item, Ui.weight(1));
            tabViews.add(item);
        }
        return nav;
    }

    // ================================================================ mini player

    private View buildMiniPlayer() {
        mini = Ui.column(this);
        LinearLayout.LayoutParams outer = Ui.lp(Ui.MATCH, Ui.WRAP);
        int m = Ui.dp(this, 8);
        outer.setMargins(m, 0, m, Ui.dp(this, 4));
        mini.setLayoutParams(outer);
        mini.setBackground(Ui.ripple(Ui.rounded(0xFF3A2F3F, Ui.dp(this, 8))));
        mini.setClipToOutline(true);

        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 6));
        miniCover = new ImageView(this);
        miniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(miniCover, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));
        LinearLayout texts = Ui.column(this);
        texts.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 4), 0);
        miniTitle = Ui.text(this, "", 14, Ui.TEXT, true);
        miniArtist = Ui.text(this, "", 12, Ui.SUB, false);
        texts.addView(miniTitle);
        texts.addView(miniArtist);
        row.addView(texts, Ui.weight(1));
        miniLike = Ui.iconButton(this, R.drawable.ic_heart_outline, 44, Ui.TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lib.toggleLike(player.current());
            }
        });
        row.addView(miniLike);
        miniPlay = Ui.iconButton(this, R.drawable.ic_play, 44, Ui.TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.togglePlay();
            }
        });
        row.addView(miniPlay);
        mini.addView(row);

        miniProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        miniProgress.setMax(1000);
        miniProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT));
        miniProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x55FFFFFF));
        LinearLayout.LayoutParams plp = Ui.lp(Ui.MATCH, Ui.dp(this, 2));
        plp.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        mini.addView(miniProgress, plp);

        mini.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, NowPlayingActivity.class));
                overridePendingTransition(R.anim.slide_up, R.anim.stay);
            }
        });
        mini.setVisibility(View.GONE);
        return mini;
    }

    private String miniKey;

    private void updateMini() {
        if (mini == null) return;
        Track t = player.current();
        if (t == null) {
            mini.setVisibility(View.GONE);
            return;
        }
        mini.setVisibility(View.VISIBLE);
        if (!t.key.equals(miniKey)) {
            miniKey = t.key;
            miniTitle.setText(t.title);
            miniArtist.setText(t.artist);
            Covers.load(this, t, miniCover, Ui.dp(this, 40));
            GradientDrawable bg = Ui.rounded(darken(Covers.accent(t)), Ui.dp(this, 8));
            mini.setBackground(Ui.ripple(bg));
        }
        boolean liked = lib.isLiked(t);
        miniLike.setImageResource(liked ? R.drawable.ic_heart : R.drawable.ic_heart_outline);
        miniLike.setColorFilter(liked ? Ui.GREEN : Ui.TEXT);
        miniPlay.setImageResource(player.isActive() ? R.drawable.ic_pause : R.drawable.ic_play);
        miniArtist.setText(player.isBuffering() && player.isActive() ? "Cargando…" : t.artist);
        updateProgress();
    }

    private void updateProgress() {
        if (miniProgress == null) return;
        int d = player.duration();
        miniProgress.setProgress(d > 0 ? (int) (1000L * player.position() / d) : 0);
    }

    static int darken(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        return 0xFF000000 | ((r * 45 / 100) << 16) | ((g * 45 / 100) << 8) | (b * 45 / 100);
    }

    // ================================================================ collections

    private static final int C_LIKED = 0;
    private static final int C_DEVICE = 1;
    private static final int C_ONLINE = 2;
    private static final int C_PLAYLIST = 3;
    private static final int C_ARTIST = 4;
    private static final int C_ALBUM = 5;
    private static final int C_RECENT = 6;
    private static final int C_HISTORY = 7;
    private static final int C_ITEM = 8;
    private static final int C_RESUME = 9;

    /** Something that resolves to a list of tracks: a playlist, an artist, an album… */
    private final class Collection {
        final int kind;
        final String name;
        final long artistId;
        /** C_ITEM: the album / artist / playlist / genre / podcast this page shows. */
        final Library.Item item;

        Collection(Library.Item item) {
            this.kind = C_ITEM;
            this.name = item.title;
            this.artistId = 0;
            this.item = item;
        }

        Collection(int kind, String name) {
            this(kind, name, 0);
        }

        Collection(int kind, String name, long artistId) {
            this.kind = kind;
            this.name = name;
            this.artistId = artistId;
            this.item = null;
        }

        String count(int n) {
            Library.Section sec = section();
            if (sec != null && sec.radio) return n == 1 ? "1 emisora" : n + " emisoras";
            if (kind == C_RESUME || (kind == C_ITEM && item.kind == Library.Item.PODCAST)) {
                return n == 1 ? "1 episodio" : n + " episodios";
            }
            return Ui.songs(n);
        }

        Library.Section section() {
            return kind == C_ONLINE ? lib.section(name) : null;
        }

        String title() {
            switch (kind) {
                case C_LIKED:
                    return "Canciones que te gustan";
                case C_DEVICE:
                    return "Tu música";
                case C_ONLINE:
                    return section() == null ? "En línea" : section().title;
                case C_RECENT:
                    return "Agregadas recientemente";
                case C_HISTORY:
                    return "Escuchado recientemente";
                case C_RESUME:
                    return "Seguir escuchando";
                default:
                    return name;
            }
        }

        String type() {
            switch (kind) {
                case C_ARTIST:
                    return "Artista";
                case C_ALBUM:
                    return "Álbum";
                case C_DEVICE:
                    return "Canciones del dispositivo";
                case C_ONLINE:
                    return section() != null && section().radio ? "Emisoras" : "Playlist";
                case C_RESUME:
                    return "Podcasts";
                case C_ITEM:
                    switch (item.kind) {
                        case Library.Item.ALBUM:
                            return item.subtitle.isEmpty() ? "Álbum" : "Álbum · " + item.subtitle;
                        case Library.Item.ARTIST:
                            return "Artista";
                        case Library.Item.GENRE:
                            return "Género";
                        case Library.Item.PODCAST:
                            return item.subtitle.isEmpty() ? "Podcast" : "Podcast · " + item.subtitle;
                        default:
                            return "Playlist";
                    }
                default:
                    return "Playlist";
            }
        }

        List<Track> tracks() {
            switch (kind) {
                case C_LIKED:
                    return lib.likedTracks();
                case C_DEVICE:
                    return lib.localTracks();
                case C_ONLINE:
                    return section() == null ? new ArrayList<Track>() : section().tracks;
                case C_RECENT:
                    return lib.recentlyAdded(50);
                case C_HISTORY:
                    return lib.recentlyPlayed(50);
                case C_RESUME:
                    return lib.inProgress(30);
                case C_ITEM:
                    return lib.itemTracks(item);
                case C_PLAYLIST:
                    return lib.playlist(name);
                case C_ARTIST: {
                    List<Track> l = lib.group(false).get(name);
                    List<Track> out = l == null ? new ArrayList<Track>() : new ArrayList<>(l);
                    List<Track> top = lib.artistTop(name);
                    if (top != null) for (Track t : top) if (!out.contains(t)) out.add(t);
                    return out;
                }
                case C_ALBUM: {
                    List<Track> l = lib.group(true).get(name);
                    return l == null ? new ArrayList<Track>() : l;
                }
                default:
                    return new ArrayList<>();
            }
        }

        Bitmap cover(int size) {
            if (kind == C_LIKED) return likedCover(size);
            List<Track> t = tracks();
            if ((kind == C_ALBUM || kind == C_ARTIST) && !t.isEmpty()) {
                return Covers.loadSync(MainActivity.this, t.get(0), size);
            }
            return Covers.generated(title(), size, true);
        }
    }

    private static Bitmap likedCover(int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, size, size, 0xFF450AF5, 0xFFC4EFD9, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, size, size, p);
        return b;
    }

    /** Cover image for a collection, with the heart glyph on "liked songs". */
    private View collectionCover(Collection col, int sizeDp) {
        FrameLayout f = new FrameLayout(this);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        String artUrl = null;
        Library.Section sec = col.section();
        if (col.kind == C_ITEM) artUrl = col.item.art;
        else if (sec != null && sec.hasItems() && !sec.items.isEmpty()) artUrl = sec.items.get(0).art;
        boolean remoteArt = col.kind == C_ITEM || artUrl != null;
        List<Track> tracks = !remoteArt && (col.kind == C_ALBUM || col.kind == C_ARTIST
                || (col.kind == C_ONLINE && !col.tracks().isEmpty())) ? col.tracks() : null;
        if (remoteArt) Covers.loadUrl(this, artUrl, col.title(), img, Ui.dp(this, sizeDp));
        else if (tracks != null && !tracks.isEmpty()) Covers.load(this, tracks.get(0), img, Ui.dp(this, sizeDp));
        else img.setImageBitmap(col.cover(Ui.dp(this, sizeDp)));
        f.addView(img, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        if (col.kind == C_LIKED || col.kind == C_DEVICE || col.kind == C_HISTORY
                || (col.kind == C_ONLINE && tracks == null && !remoteArt)) {
            int res = col.kind == C_LIKED ? R.drawable.ic_heart
                    : col.kind == C_HISTORY ? R.drawable.ic_history
                    : col.kind == C_DEVICE ? R.drawable.ic_library : R.drawable.ic_note;
            ImageView glyph = Ui.icon(this, res, Math.max(24, sizeDp / 2), Ui.TEXT);
            if (col.kind != C_LIKED) img.setImageBitmap(Covers.generated(col.title(), Ui.dp(this, sizeDp), false));
            f.addView(glyph, new FrameLayout.LayoutParams(Ui.dp(this, Math.max(24, sizeDp / 2)),
                    Ui.dp(this, Math.max(24, sizeDp / 2)), Gravity.CENTER));
        }
        f.setLayoutParams(Ui.lp(Ui.dp(this, sizeDp), Ui.dp(this, sizeDp)));
        if (col.kind == C_ARTIST || (col.kind == C_ITEM && col.item.kind == Library.Item.ARTIST)) {
            f.setBackground(Ui.oval(Ui.CARD));
            f.setClipToOutline(true);
        }
        return f;
    }

    private void open(Collection c) {
        if (c.kind == C_ARTIST) lib.loadArtistTop(c.name, c.artistId);
        if (c.kind == C_ITEM) lib.loadItemTracks(c.item);
        Library.Section sec = c.section();
        if (sec != null && (sec.state == Library.IDLE || sec.state == Library.FAILED)) lib.loadSection(sec);
        if (sec != null && sec.hasItems()) {
            push(new ItemsScreen(sec));
            return;
        }
        push(new DetailScreen(c));
    }

    // ================================================================ track menu

    private void trackMenu(final Track t, View anchor, final Collection from) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, 1, 0, lib.isLiked(t) ? "Quitar de Me gusta" : "Añadir a Me gusta");
        m.add(0, 2, 0, "Reproducir a continuación");
        m.add(0, 3, 0, "Añadir a playlist…");
        if (!t.isLive() && !t.isEpisode()) m.add(0, 4, 0, "Ir al artista");
        if (!t.remote) m.add(0, 5, 0, "Ir al álbum");

        if (from != null && from.kind == C_PLAYLIST) m.add(0, 6, 0, "Quitar de esta playlist");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        lib.toggleLike(t);
                        break;
                    case 2:
                        player.playNext(t);
                        Toast.makeText(MainActivity.this, "Se reproducirá a continuación", Toast.LENGTH_SHORT).show();
                        break;
                    case 3:
                        Dialogs.addToPlaylist(MainActivity.this, t);
                        break;
                    case 4:
                        open(new Collection(C_ARTIST, t.artist, t.artistId));
                        break;
                    case 5:
                        open(new Collection(C_ALBUM, t.album));
                        break;
                    case 6:
                        lib.removeFromPlaylist(from.name, t);
                        break;
                    default:
                        return false;
                }
                return true;
            }
        });
        pm.show();
    }

    // ================================================================ shared bits

    private TextView sectionTitle(String s) {
        TextView t = Ui.text(this, s, 22, Ui.TEXT, true);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 12));
        return t;
    }

    /** Title + subtitle of a home shelf, with "Ver todo" opening the whole list. */
    private View sectionHeader(final Collection c, String subtitle) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 12));
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, c.title(), 22, Ui.TEXT, true));
        if (subtitle != null) texts.addView(Ui.text(this, subtitle, 13, Ui.SUB, false));
        r.addView(texts, Ui.weight(1));
        TextView all = Ui.text(this, "Ver todo", 13, Ui.SUB, true);
        all.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        all.setBackground(Ui.ripple(null));
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(c);
            }
        });
        r.addView(all);
        return r;
    }

    // ================================================================ items (albums, artists, playlists, genres, podcasts)

    /** A horizontal shelf of cover cards that open an item's page. */
    private View itemRow(List<Library.Item> items) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout r = Ui.row(this);
        r.setGravity(Gravity.TOP);
        r.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        for (final Library.Item it : new ArrayList<>(items)) r.addView(itemCard(it));
        hs.addView(r);
        return hs;
    }

    private View itemCard(final Library.Item it) {
        boolean round = it.kind == Library.Item.ARTIST;
        int size = round ? 120 : 140;
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        if (round) card.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackground(round ? Ui.oval(Ui.CARD) : Ui.rounded(Ui.CARD, Ui.dp(this, 6)));
        img.setClipToOutline(true);
        Covers.loadUrl(this, it.art, it.title, img, Ui.dp(this, size));
        card.addView(img, Ui.lp(Ui.dp(this, size), Ui.dp(this, size)));
        TextView title = Ui.text(this, it.title, 13, Ui.TEXT, true);
        title.setPadding(0, Ui.dp(this, 8), 0, 0);
        if (round) title.setGravity(Gravity.CENTER);
        card.addView(title, Ui.lp(Ui.dp(this, size), Ui.WRAP));
        if (!round) card.addView(Ui.text(this, it.subtitle, 12, Ui.SUB, false), Ui.lp(Ui.dp(this, size), Ui.WRAP));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(new Collection(it));
            }
        });
        return card;
    }

    /** A list row for an item: cover, title and a line of detail. */
    private View itemListRow(final Library.Item it) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        r.setBackground(Ui.ripple(null));
        r.addView(collectionCover(new Collection(it), 60));
        LinearLayout texts = Ui.column(this);
        texts.setPadding(Ui.dp(this, 12), 0, 0, 0);
        texts.addView(Ui.text(this, it.title, 16, Ui.TEXT, false));
        texts.addView(Ui.text(this, it.subtitle, 13, Ui.SUB, false));
        r.addView(texts, Ui.weight(1));
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(new Collection(it));
            }
        });
        return r;
    }

    /** The full list of a shelf of items ("Ver todo"). */
    private final class ItemsScreen extends Screen {
        final Library.Section sec;

        ItemsScreen(Library.Section sec) {
            this.sec = sec;
        }

        @Override
        View create() {
            LinearLayout col = Ui.column(MainActivity.this);
            LinearLayout top = Ui.row(MainActivity.this);
            top.setPadding(Ui.dp(MainActivity.this, 4), Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 4));
            top.addView(Ui.iconButton(MainActivity.this, R.drawable.ic_back, 48, Ui.TEXT, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            }));
            LinearLayout titles = Ui.column(MainActivity.this);
            titles.setPadding(Ui.dp(MainActivity.this, 8), 0, 0, 0);
            titles.addView(Ui.text(MainActivity.this, sec.title, 22, Ui.TEXT, true));
            titles.addView(Ui.text(MainActivity.this, sec.subtitle, 12, Ui.SUB, false));
            top.addView(titles, Ui.weight(1));
            col.addView(top);
            LinearLayout list = Ui.column(MainActivity.this);
            list.setPadding(0, Ui.dp(MainActivity.this, 8), 0, Ui.dp(MainActivity.this, 24));
            if (sec.state == Library.READY) {
                for (Library.Item it : sec.items) list.addView(itemListRow(it));
            } else {
                list.addView(sectionStatus(sec));
            }
            ScrollView sv = new ScrollView(MainActivity.this);
            sv.addView(list);
            col.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
            return col;
        }
    }

    private static final String[] PODCAST_TOPICS = {
            "Noticias", "Comedia", "Deportes", "Tecnología", "Historia", "Negocios", "Salud", "Música",
            "Ciencia", "Crimen real", "Educación", "Cine"};

    /** Podcasts: your shows, what you left half-way, the charts and a search over Apple's directory. */
    private final class PodcastsScreen extends Screen {
        String query = "";
        String resultsFor = "";
        List<Library.Item> results = new ArrayList<>();
        boolean searching;
        boolean failed;
        Runnable pending;
        EditText input;
        LinearLayout resultsBox;
        ScrollView resultsScroll;
        View homeView;

        @Override
        View create() {
            Library.Section pop = lib.section("podcasts");
            if (pop.state == Library.IDLE || pop.state == Library.FAILED) lib.loadSection(pop);

            LinearLayout col = Ui.column(MainActivity.this);
            TextView title = Ui.text(MainActivity.this, "Podcasts", 26, Ui.TEXT, true);
            title.setPadding(Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 28), 0, Ui.dp(MainActivity.this, 14));
            col.addView(title);

            LinearLayout box = Ui.row(MainActivity.this);
            box.setBackground(Ui.rounded(Ui.TEXT, Ui.dp(MainActivity.this, 6)));
            box.setPadding(Ui.dp(MainActivity.this, 10), 0, Ui.dp(MainActivity.this, 10), 0);
            box.addView(Ui.icon(MainActivity.this, R.drawable.ic_search, 28, 0xFF121212));
            input = new EditText(MainActivity.this);
            input.setHint("Buscar podcasts");
            input.setHintTextColor(0xFF6A6A6A);
            input.setTextColor(0xFF121212);
            input.setBackground(null);
            input.setSingleLine(true);
            input.setTextSize(16);
            input.setText(query);
            input.setSelection(query.length());
            box.addView(input, Ui.weight(1));
            final ImageView clear = Ui.iconButton(MainActivity.this, R.drawable.ic_close, 36, 0xFF121212, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    input.setText("");
                }
            });
            box.addView(clear);
            LinearLayout.LayoutParams blp = Ui.lp(Ui.MATCH, Ui.dp(MainActivity.this, 48));
            blp.setMargins(Ui.dp(MainActivity.this, 16), 0, Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 8));
            col.addView(box, blp);

            FrameLayout body = new FrameLayout(MainActivity.this);
            homeView = homeContent();
            body.addView(homeView);
            resultsBox = Ui.column(MainActivity.this);
            resultsBox.setPadding(0, Ui.dp(MainActivity.this, 8), 0, Ui.dp(MainActivity.this, 24));
            resultsScroll = new ScrollView(MainActivity.this);
            resultsScroll.addView(resultsBox);
            body.addView(resultsScroll);
            col.addView(body, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));

            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    query = s.toString();
                    clear.setVisibility(query.isEmpty() ? View.INVISIBLE : View.VISIBLE);
                    schedule();
                    showResults();
                }
            });
            clear.setVisibility(query.isEmpty() ? View.INVISIBLE : View.VISIBLE);
            if (!query.trim().isEmpty() && !query.trim().equals(resultsFor)) schedule();
            showResults();
            return col;
        }

        /** Your shows, what you were listening to, the charts and topic shortcuts. */
        View homeContent() {
            LinearLayout col = Ui.column(MainActivity.this);
            List<Track> resume = lib.inProgress(10);
            if (!resume.isEmpty()) {
                Collection rc = new Collection(C_RESUME, null);
                col.addView(sectionHeader(rc, "Continúa donde lo dejaste"));
                col.addView(trackRow(resume, rc));
            }
            List<Library.Item> mine = lib.followedPodcasts();
            if (!mine.isEmpty()) {
                col.addView(sectionTitle("Tus podcasts"));
                col.addView(itemRow(mine));
            }
            col.addView(sectionTitle("Temas"));
            HorizontalScrollView hs = new HorizontalScrollView(MainActivity.this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = Ui.row(MainActivity.this);
            chips.setPadding(Ui.dp(MainActivity.this, 12), 0, Ui.dp(MainActivity.this, 12), 0);
            for (final String topic : PODCAST_TOPICS) {
                TextView chip = Ui.text(MainActivity.this, topic, 13, Ui.TEXT, false);
                chip.setPadding(Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 8));
                chip.setBackground(Ui.ripple(Ui.rounded(Ui.CARD, Ui.dp(MainActivity.this, 18))));
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        input.setText(topic);
                        input.setSelection(topic.length());
                    }
                });
                LinearLayout.LayoutParams lp = Ui.lp(Ui.WRAP, Ui.WRAP);
                lp.setMargins(Ui.dp(MainActivity.this, 4), 0, Ui.dp(MainActivity.this, 4), 0);
                chips.addView(chip, lp);
            }
            hs.addView(chips);
            col.addView(hs);
            Library.Section pop = lib.section("podcasts");
            col.addView(sectionHeader(new Collection(C_ONLINE, "podcasts"), pop.subtitle));
            if (pop.state == Library.READY) {
                col.addView(itemRow(pop.items.size() > 20 ? pop.items.subList(0, 20) : pop.items));
            } else {
                col.addView(sectionStatus(pop));
            }
            col.setPadding(0, 0, 0, Ui.dp(MainActivity.this, 24));
            ScrollView sv = new ScrollView(MainActivity.this);
            sv.addView(col);
            return sv;
        }

        void schedule() {
            if (pending != null) handler.removeCallbacks(pending);
            final String q = query.trim();
            if (q.isEmpty()) {
                searching = false;
                return;
            }
            searching = true;
            pending = new Runnable() {
                @Override
                public void run() {
                    lib.searchPodcasts(q, new Library.ItemResults() {
                        @Override
                        public void onItems(List<Library.Item> items, boolean fail) {
                            if (!q.equals(query.trim())) return; // stale
                            results = items;
                            resultsFor = q;
                            failed = fail;
                            searching = false;
                            if (stack.peek() == PodcastsScreen.this) showResults();
                        }
                    });
                }
            };
            handler.postDelayed(pending, 450);
        }

        void showResults() {
            String q = query.trim();
            boolean idle = q.isEmpty();
            homeView.setVisibility(idle ? View.VISIBLE : View.GONE);
            resultsScroll.setVisibility(idle ? View.GONE : View.VISIBLE);
            resultsBox.removeAllViews();
            if (idle) return;
            if (searching && !q.equals(resultsFor)) {
                resultsBox.addView(statusLine("Buscando \"" + q + "\"…"));
            } else if (failed) {
                resultsBox.addView(statusLine("No hay conexión a internet."));
            } else if (results.isEmpty()) {
                resultsBox.addView(statusLine("No se encontraron podcasts para \"" + q + "\""));
            } else {
                for (Library.Item it : results) resultsBox.addView(itemListRow(it));
            }
        }

        TextView statusLine(String msg) {
            TextView t = Ui.text(MainActivity.this, msg, 15, Ui.SUB, false);
            t.setSingleLine(false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(MainActivity.this, 32), Ui.dp(MainActivity.this, 48), Ui.dp(MainActivity.this, 32), 0);
            return t;
        }

        @Override
        void onLibraryChanged() {
            if (query.trim().isEmpty()) render(); // typing must not be interrupted
        }
    }

    /** Placeholder for a shelf that is loading or failed to load. */
    private View sectionStatus(final Library.Section sec) {
        LinearLayout box = Ui.row(this);
        box.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        if (sec.state == Library.FAILED) {
            box.addView(Ui.text(this, "Sin conexión", 14, Ui.SUB, false), Ui.weight(1));
            box.addView(pillButton("Reintentar", Ui.CARD, Ui.TEXT, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    lib.loadSection(sec);
                }
            }));
        } else {
            ProgressBar pb = new ProgressBar(this);
            pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
            box.addView(pb, Ui.lp(Ui.dp(this, 24), Ui.dp(this, 24)));
            TextView t = Ui.text(this, "Cargando…", 14, Ui.SUB, false);
            t.setPadding(Ui.dp(this, 12), 0, 0, 0);
            box.addView(t);
        }
        box.setMinimumHeight(Ui.dp(this, 56));
        return box;
    }

    private View permissionCard() {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.rounded(Ui.CARD, Ui.dp(this, 10)));
        int p = Ui.dp(this, 16);
        card.setPadding(p, p, p, p);
        TextView title = Ui.text(this, "Escucha tu propia música", 17, Ui.TEXT, true);
        TextView body = Ui.text(this, "Permite el acceso a \"Música y audio\" para ver las canciones guardadas en tu teléfono.", 14, Ui.SUB, false);
        body.setSingleLine(false);
        body.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 12));
        card.addView(title);
        card.addView(body);
        card.addView(pillButton("Permitir acceso", Ui.GREEN, 0xFF000000, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestAudioAgain();
            }
        }), Ui.lp(Ui.WRAP, Ui.WRAP));
        LinearLayout.LayoutParams lp = Ui.lp(Ui.MATCH, Ui.WRAP);
        lp.setMargins(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), 0);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView pillButton(String label, int bg, int fg, View.OnClickListener l) {
        TextView b = Ui.text(this, label, 14, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Ui.dp(this, 22), Ui.dp(this, 10), Ui.dp(this, 22), Ui.dp(this, 10));
        b.setBackground(Ui.ripple(Ui.rounded(bg, Ui.dp(this, 24))));
        b.setOnClickListener(l);
        return b;
    }

    private ScrollView scroller(View inner) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(inner);
        return sv;
    }

    // ================================================================ Home

    private final class HomeScreen extends Screen {
        int scrollY;
        ScrollView sv;

        @Override
        View create() {
            LinearLayout col = Ui.column(MainActivity.this);
            col.setPadding(0, Ui.dp(MainActivity.this, 12), 0, Ui.dp(MainActivity.this, 24));
            // greeting
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            String greet = h < 5 ? "Buenas noches" : h < 12 ? "Buenos días" : h < 20 ? "Buenas tardes" : "Buenas noches";
            TextView g = Ui.text(MainActivity.this, greet, 26, Ui.TEXT, true);
            g.setPadding(Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 16), 0, Ui.dp(MainActivity.this, 12));
            col.addView(g);

            if (!Library.hasAudioPermission(MainActivity.this)) col.addView(permissionCard());

            // quick access grid
            List<Collection> quick = new ArrayList<>();
            quick.add(new Collection(C_LIKED, null));
            quick.add(new Collection(C_DEVICE, null));
            quick.add(new Collection(C_ONLINE, "top"));
            quick.add(new Collection(C_ONLINE, "radio"));
            if (!lib.localTracks().isEmpty()) quick.add(new Collection(C_RECENT, null));
            for (String name : lib.playlistNames()) {
                if (quick.size() >= 6) break;
                quick.add(new Collection(C_PLAYLIST, name));
            }
            LinearLayout grid = Ui.column(MainActivity.this);
            grid.setPadding(Ui.dp(MainActivity.this, 12), Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 12), 0);
            for (int i = 0; i < quick.size(); i += 2) {
                LinearLayout r = Ui.row(MainActivity.this);
                r.addView(quickTile(quick.get(i)), tileLp());
                if (i + 1 < quick.size()) r.addView(quickTile(quick.get(i + 1)), tileLp());
                else r.addView(new View(MainActivity.this), tileLp());
                grid.addView(r);
            }
            col.addView(grid);

            List<Track> played = lib.recentlyPlayed(15);
            if (!played.isEmpty()) {
                Collection hc = new Collection(C_HISTORY, null);
                col.addView(sectionHeader(hc, null));
                col.addView(trackRow(played, hc));
            }

            List<Track> recent = lib.recentlyAdded(15);
            if (!recent.isEmpty()) {
                col.addView(sectionTitle("Agregadas recientemente"));
                col.addView(trackRow(recent, new Collection(C_RECENT, null)));
            }

            List<Track> resume = lib.inProgress(10);
            if (!resume.isEmpty()) {
                Collection rc = new Collection(C_RESUME, null);
                col.addView(sectionHeader(rc, "Podcasts que dejaste a medias"));
                col.addView(trackRow(resume, rc));
            }
            List<Library.Item> mine = lib.followedPodcasts();
            if (!mine.isEmpty()) {
                col.addView(sectionTitle("Tus podcasts"));
                col.addView(itemRow(mine));
            }

            // real content from the internet: charts, new releases, artists, playlists, radio…
            for (Library.Section sec : lib.sections()) {
                if (!sec.featured) continue;
                Collection c = new Collection(C_ONLINE, sec.id);
                col.addView(sectionHeader(c, sec.subtitle));
                if (sec.state != Library.READY) {
                    col.addView(sectionStatus(sec));
                } else if (sec.hasItems()) {
                    col.addView(itemRow(sec.items.size() > 20 ? sec.items.subList(0, 20) : sec.items));
                } else {
                    col.addView(trackRow(sec.tracks.size() > 20 ? sec.tracks.subList(0, 20) : sec.tracks, c));
                }
            }
            TextView more = Ui.text(MainActivity.this, "Más géneros, radios y podcasts en Buscar", 13, Ui.SUB, false);
            more.setPadding(Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 20), Ui.dp(MainActivity.this, 16), 0);
            col.addView(more);

            LinkedHashMap<String, List<Track>> artists = lib.group(false);
            if (artists.size() > 1) {
                col.addView(sectionTitle("Tus artistas"));
                col.addView(artistRow(artists));
            }

            if (Library.hasAudioPermission(MainActivity.this) && lib.loadedOnce() && lib.localTracks().isEmpty()) {
                TextView hint = Ui.text(MainActivity.this,
                        "No encontramos canciones guardadas en este teléfono. Copia archivos MP3 a la carpeta Música y aparecerán en \"Tu música\".",
                        14, Ui.SUB, false);
                hint.setSingleLine(false);
                hint.setPadding(Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 24), Ui.dp(MainActivity.this, 16), 0);
                col.addView(hint);
            }

            sv = scroller(col);
            final ScrollView s = sv;
            s.post(new Runnable() {
                @Override
                public void run() {
                    s.scrollTo(0, scrollY);
                }
            });
            s.getViewTreeObserver().addOnScrollChangedListener(new android.view.ViewTreeObserver.OnScrollChangedListener() {
                @Override
                public void onScrollChanged() {
                    scrollY = s.getScrollY();
                }
            });
            return sv;
        }
    }

    private LinearLayout.LayoutParams tileLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1);
        int m = Ui.dp(this, 4);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    private View quickTile(final Collection c) {
        LinearLayout t = Ui.row(this);
        t.setBackground(Ui.ripple(Ui.rounded(Ui.CARD, Ui.dp(this, 6))));
        t.setClipToOutline(true);
        t.addView(collectionCover(c, 56));
        TextView label = Ui.text(this, c.title(), 13, Ui.TEXT, true);
        label.setSingleLine(false);
        label.setMaxLines(2);
        label.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 6), 0);
        t.addView(label, Ui.weight(1));
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(c);
            }
        });
        return t;
    }

    private View trackRow(final List<Track> tracks, final Collection source) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout r = Ui.row(this);
        r.setGravity(Gravity.TOP);
        r.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        for (int i = 0; i < tracks.size(); i++) {
            final int index = i;
            Track t = tracks.get(i);
            LinearLayout card = Ui.column(this);
            card.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackground(Ui.rounded(Ui.CARD, Ui.dp(this, 6)));
            img.setClipToOutline(true);
            Covers.load(this, t, img, Ui.dp(this, 140));
            card.addView(img, Ui.lp(Ui.dp(this, 140), Ui.dp(this, 140)));
            TextView title = Ui.text(this, t.title, 13, Ui.TEXT, true);
            title.setPadding(0, Ui.dp(this, 8), 0, 0);
            card.addView(title, Ui.lp(Ui.dp(this, 140), Ui.WRAP));
            card.addView(Ui.text(this, t.artist, 12, Ui.SUB, false), Ui.lp(Ui.dp(this, 140), Ui.WRAP));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<Track> all = source.kind == C_ONLINE || source.kind == C_HISTORY || source.kind == C_RESUME
                            ? source.tracks() : tracks;
                    int i = all.indexOf(tracks.get(index));
                    player.playList(all, Math.max(i, 0), source.title());
                }
            });
            r.addView(card);
        }
        hs.addView(r);
        return hs;
    }

    private View artistRow(LinkedHashMap<String, List<Track>> artists) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout r = Ui.row(this);
        r.setGravity(Gravity.TOP);
        r.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        int n = 0;
        for (final Map.Entry<String, List<Track>> e : artists.entrySet()) {
            if (n++ >= 15) break;
            final Collection c = new Collection(C_ARTIST, e.getKey());
            LinearLayout card = Ui.column(this);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackground(Ui.oval(Ui.CARD));
            img.setClipToOutline(true);
            Covers.load(this, e.getValue().get(0), img, Ui.dp(this, 120));
            card.addView(img, Ui.lp(Ui.dp(this, 120), Ui.dp(this, 120)));
            TextView name = Ui.text(this, e.getKey(), 13, Ui.TEXT, true);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, Ui.dp(this, 8), 0, 0);
            card.addView(name, Ui.lp(Ui.dp(this, 120), Ui.WRAP));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    open(c);
                }
            });
            r.addView(card);
        }
        hs.addView(r);
        return hs;
    }

    // ================================================================ Search

    private final class SearchScreen extends Screen {
        String query = "";
        List<Track> online = new ArrayList<>();
        String onlineFor = "";
        boolean onlineFailed;
        boolean searching;
        Runnable pending;
        FrameLayout body;
        EditText input;
        String browseSig = "";
        TrackAdapter adapter;
        View browse;
        ListView list;
        TextView empty;

        @Override
        View create() {
            LinearLayout col = Ui.column(MainActivity.this);
            TextView title = Ui.text(MainActivity.this, "Buscar", 26, Ui.TEXT, true);
            title.setPadding(Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 28), 0, Ui.dp(MainActivity.this, 14));
            col.addView(title);

            LinearLayout box = Ui.row(MainActivity.this);
            box.setBackground(Ui.rounded(Ui.TEXT, Ui.dp(MainActivity.this, 6)));
            box.setPadding(Ui.dp(MainActivity.this, 10), 0, Ui.dp(MainActivity.this, 10), 0);
            box.addView(Ui.icon(MainActivity.this, R.drawable.ic_search, 28, 0xFF121212));
            input = new EditText(MainActivity.this);
            input.setHint("¿Qué quieres escuchar?");
            input.setHintTextColor(0xFF6A6A6A);
            input.setTextColor(0xFF121212);
            input.setBackground(null);
            input.setSingleLine(true);
            input.setTextSize(16);
            input.setText(query);
            input.setSelection(query.length());
            box.addView(input, Ui.weight(1));
            final ImageView clear = Ui.iconButton(MainActivity.this, R.drawable.ic_close, 36, 0xFF121212, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    input.setText("");
                }
            });
            box.addView(clear);
            LinearLayout.LayoutParams blp = Ui.lp(Ui.MATCH, Ui.dp(MainActivity.this, 48));
            blp.setMargins(Ui.dp(MainActivity.this, 16), 0, Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 8));
            col.addView(box, blp);

            body = new FrameLayout(MainActivity.this);
            list = new ListView(MainActivity.this);
            list.setDivider(null);
            list.setSelector(Ui.ripple(null));
            adapter = new TrackAdapter(MainActivity.this, new TrackAdapter.MoreHandler() {
                @Override
                public void onMore(Track t, View anchor) {
                    trackMenu(t, anchor, null);
                }
            });
            list.setAdapter(adapter);
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    hideKeyboard();
                    lib.addSearch(query);
                    player.playList(adapter.tracks(), position, "Búsqueda: " + query);
                }
            });
            list.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int state) {
                    if (state != SCROLL_STATE_IDLE) hideKeyboard();
                }

                @Override
                public void onScroll(AbsListView v, int a, int b, int c) {
                }
            });
            body.addView(list);
            empty = Ui.text(MainActivity.this, "", 15, Ui.SUB, false);
            empty.setSingleLine(false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Ui.dp(MainActivity.this, 32), Ui.dp(MainActivity.this, 48), Ui.dp(MainActivity.this, 32), 0);
            body.addView(empty, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
            browseSig = lib.searchHistory().toString();
            browse = browseGrid(input);
            body.addView(browse);
            col.addView(body, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));

            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    query = s.toString();
                    clear.setVisibility(query.isEmpty() ? View.INVISIBLE : View.VISIBLE);
                    scheduleOnline();
                    refresh();
                }
            });
            clear.setVisibility(query.isEmpty() ? View.INVISIBLE : View.VISIBLE);
            if (!query.trim().equals(onlineFor)) scheduleOnline();
            refresh();
            return col;
        }

        /** Asks the internet once the user stops typing for a moment. */
        void scheduleOnline() {
            if (pending != null) handler.removeCallbacks(pending);
            final String q = query.trim();
            if (q.isEmpty()) {
                searching = false;
                return;
            }
            searching = true;
            pending = new Runnable() {
                @Override
                public void run() {
                    lib.searchOnline(q, new Library.Results() {
                        @Override
                        public void onResults(List<Track> tracks, boolean failed) {
                            if (!q.equals(query.trim())) return; // stale answer
                            online = tracks;
                            onlineFor = q;
                            onlineFailed = failed;
                            searching = false;
                            if (adapter != null && stack.peek() == SearchScreen.this) refresh();
                        }
                    });
                }
            };
            handler.postDelayed(pending, 450);
        }

        void refresh() {
            String q = query.trim();
            boolean idle = q.isEmpty();
            String sig = lib.searchHistory().toString();
            if (idle && !sig.equals(browseSig)) { // recent searches changed: redraw the browse page
                body.removeView(browse);
                browse = browseGrid(input);
                body.addView(browse);
                browseSig = sig;
            }
            browse.setVisibility(idle ? View.VISIBLE : View.GONE);
            List<Track> res = idle ? new ArrayList<Track>() : lib.search(query);
            if (!idle && q.equals(onlineFor)) {
                for (Track t : online) if (!res.contains(t)) res.add(t);
            }
            adapter.setTracks(res);
            list.setVisibility(idle ? View.GONE : View.VISIBLE);
            empty.setVisibility(!idle && res.isEmpty() ? View.VISIBLE : View.GONE);
            if (searching) empty.setText("Buscando \"" + q + "\"…");
            else if (onlineFailed && q.equals(onlineFor)) empty.setText("No hay conexión a internet. Solo se buscó en tu música.");
            else empty.setText("No se encontró nada para \"" + q + "\"");
        }

        @Override
        void onLibraryChanged() {
            if (adapter != null) refresh();
        }

        @Override
        void onPlayerChanged() {
            if (adapter != null) adapter.notifyDataSetChanged();
        }
    }

    private View browseGrid(final EditText input) {
        List<Collection> cats = new ArrayList<>();
        for (Library.Section sec : lib.sections()) cats.add(new Collection(C_ONLINE, sec.id));
        cats.add(new Collection(C_DEVICE, null));
        cats.add(new Collection(C_LIKED, null));
        if (!lib.localTracks().isEmpty()) cats.add(new Collection(C_RECENT, null));
        int n = 0;
        for (String album : lib.group(true).keySet()) {
            if (n++ >= 12) break;
            cats.add(new Collection(C_ALBUM, album));
        }
        LinearLayout col = Ui.column(this);
        List<String> recents = lib.searchHistory();
        if (!recents.isEmpty()) {
            LinearLayout head = Ui.row(this);
            head.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 4));
            head.addView(Ui.text(this, "Búsquedas recientes", 17, Ui.TEXT, true), Ui.weight(1));
            TextView clear = Ui.text(this, "Borrar", 13, Ui.SUB, true);
            clear.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
            clear.setBackground(Ui.ripple(null));
            clear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    lib.clearSearchHistory();
                }
            });
            head.addView(clear);
            col.addView(head);
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = Ui.row(this);
            chips.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 8));
            for (final String q : recents) {
                TextView chip = Ui.text(this, q, 13, Ui.TEXT, false);
                chip.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
                chip.setBackground(Ui.ripple(Ui.rounded(Ui.CARD, Ui.dp(this, 18))));
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        input.setText(q);
                        input.setSelection(q.length());
                    }
                });
                LinearLayout.LayoutParams lp = Ui.lp(Ui.WRAP, Ui.WRAP);
                lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
                chips.addView(chip, lp);
            }
            hs.addView(chips);
            col.addView(hs);
        }
        TextView t = Ui.text(this, "Explorar todo", 17, Ui.TEXT, true);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), 0, Ui.dp(this, 8));
        col.addView(t);
        LinearLayout grid = Ui.column(this);
        grid.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 24));
        for (int i = 0; i < cats.size(); i += 2) {
            LinearLayout r = Ui.row(this);
            r.addView(categoryTile(cats.get(i)), catLp());
            if (i + 1 < cats.size()) r.addView(categoryTile(cats.get(i + 1)), catLp());
            else r.addView(new View(this), catLp());
            grid.addView(r);
        }
        col.addView(grid);
        return scroller(col);
    }

    private LinearLayout.LayoutParams catLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 96), 1);
        int m = Ui.dp(this, 5);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    private View categoryTile(final Collection c) {
        FrameLayout f = new FrameLayout(this);
        int color = Covers.accent(new Track("x", c.title(), c.title(), c.title(), 0, 0, true, 0));
        f.setBackground(Ui.ripple(Ui.rounded(color, Ui.dp(this, 8))));
        f.setClipToOutline(true);
        TextView label = Ui.text(this, c.title(), 16, Ui.TEXT, true);
        label.setSingleLine(false);
        label.setMaxLines(2);
        label.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 40), 0);
        f.addView(label);
        View cover = collectionCover(c, 64);
        cover.setRotation(25);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64), Gravity.BOTTOM | Gravity.END);
        clp.setMargins(0, 0, -Ui.dp(this, 12), -Ui.dp(this, 4));
        f.addView(cover, clp);
        f.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(c);
            }
        });
        return f;
    }

    // ================================================================ Library

    private final class LibraryScreen extends Screen {
        int filter; // 0 playlists, 1 artists, 2 albums

        @Override
        View create() {
            LinearLayout col = Ui.column(MainActivity.this);
            LinearLayout header = Ui.row(MainActivity.this);
            header.setPadding(Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 24), Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 8));
            header.addView(Ui.text(MainActivity.this, "Tu biblioteca", 24, Ui.TEXT, true), Ui.weight(1));
            header.addView(Ui.iconButton(MainActivity.this, R.drawable.ic_add, 44, Ui.TEXT, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Dialogs.newPlaylist(MainActivity.this, new Dialogs.OnName() {
                        @Override
                        public void onName(String name) {
                            open(new Collection(C_PLAYLIST, name));
                        }
                    });
                }
            }));
            col.addView(header);

            LinearLayout chips = Ui.row(MainActivity.this);
            chips.setPadding(Ui.dp(MainActivity.this, 12), 0, Ui.dp(MainActivity.this, 12), Ui.dp(MainActivity.this, 8));
            String[] names = {"Playlists", "Artistas", "Álbumes", "Podcasts"};
            for (int i = 0; i < names.length; i++) {
                final int index = i;
                boolean on = i == filter;
                TextView chip = Ui.text(MainActivity.this, names[i], 13, on ? 0xFF000000 : Ui.TEXT, false);
                chip.setPadding(Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 7), Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 7));
                chip.setBackground(Ui.ripple(Ui.rounded(on ? Ui.GREEN : Ui.CARD, Ui.dp(MainActivity.this, 16))));
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        filter = index;
                        render();
                    }
                });
                LinearLayout.LayoutParams lp = Ui.lp(Ui.WRAP, Ui.WRAP);
                lp.setMargins(Ui.dp(MainActivity.this, 4), 0, Ui.dp(MainActivity.this, 4), 0);
                chips.addView(chip, lp);
            }
            col.addView(chips);

            if (!Library.hasAudioPermission(MainActivity.this)) col.addView(permissionCard());

            final List<Collection> items = new ArrayList<>();
            if (filter == 0) {
                items.add(new Collection(C_LIKED, null));
                items.add(new Collection(C_DEVICE, null));
                if (!lib.recentlyPlayed(1).isEmpty()) items.add(new Collection(C_HISTORY, null));
                items.add(new Collection(C_ONLINE, "top"));
                items.add(new Collection(C_ONLINE, "radio"));
                for (String n : lib.playlistNames()) items.add(new Collection(C_PLAYLIST, n));
            } else if (filter == 3) {
                for (Library.Item it : lib.followedPodcasts()) items.add(new Collection(it));
            } else {
                for (String n : lib.group(filter == 2).keySet()) {
                    items.add(new Collection(filter == 2 ? C_ALBUM : C_ARTIST, n));
                }
            }
            ListView list = new ListView(MainActivity.this);
            list.setDivider(null);
            list.setSelector(Ui.ripple(null));
            list.setAdapter(new BaseAdapter() {
                @Override
                public int getCount() {
                    return items.size();
                }

                @Override
                public Object getItem(int p) {
                    return items.get(p);
                }

                @Override
                public long getItemId(int p) {
                    return p;
                }

                @Override
                public View getView(int p, View convertView, ViewGroup parent) {
                    Collection c = items.get(p);
                    LinearLayout r = Ui.row(MainActivity.this);
                    r.setPadding(Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 8));
                    r.setLayoutParams(new AbsListView.LayoutParams(Ui.MATCH, Ui.WRAP));
                    r.addView(collectionCover(c, 60));
                    LinearLayout texts = Ui.column(MainActivity.this);
                    texts.setPadding(Ui.dp(MainActivity.this, 12), 0, 0, 0);
                    texts.addView(Ui.text(MainActivity.this, c.title(), 16, Ui.TEXT, false));
                    int count = c.tracks().size();
                    String sub = c.kind == C_ARTIST ? "Artista · " + Ui.songs(count)
                            : c.kind == C_ITEM ? c.type() : c.type() + " · " + c.count(count);
                    texts.addView(Ui.text(MainActivity.this, sub, 13, Ui.SUB, false));
                    r.addView(texts, Ui.weight(1));
                    return r;
                }
            });
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    open(items.get(position));
                }
            });
            list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    final Collection c = items.get(position);
                    if (c.kind != C_PLAYLIST) return false;
                    confirmDelete(c.name);
                    return true;
                }
            });
            col.addView(list, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
            return col;
        }
    }

    private void playlistMenu(View anchor, final String playlist) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "Cambiar nombre");
        pm.getMenu().add(0, 2, 0, "Eliminar playlist");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == 1) renamePlaylist(playlist);
                else confirmDelete(playlist);
                return true;
            }
        });
        pm.show();
    }

    private void renamePlaylist(final String playlist) {
        Dialogs.rename(this, playlist, new Dialogs.OnName() {
            @Override
            public void onName(String name) {
                // swap the open page for the renamed one
                if (stack.size() > 1) stack.pop();
                push(new DetailScreen(new Collection(C_PLAYLIST, name)));
            }
        });
    }

    private void confirmDelete(final String playlist) {
        Dialogs.builder(this)
                .setTitle("¿Eliminar \"" + playlist + "\"?")
                .setMessage("La playlist se borrará de tu biblioteca. Las canciones no se eliminan.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        lib.deletePlaylist(playlist);
                        Screen top = stack.peek();
                        if (top instanceof DetailScreen && stack.size() > 1) {
                            stack.pop();
                            render();
                        }
                    }
                })
                .show();
    }

    // ================================================================ Detail (playlist / artist / album)

    private final class DetailScreen extends Screen {
        final Collection col;
        TrackAdapter adapter;
        TextView subtitle;
        View emptyView;
        ImageView playBtn;
        ImageView shuffleBtn;

        boolean shownEmpty;
        TextView followBtn;

        void updateFollow() {
            if (followBtn == null) return;
            boolean on = lib.isFollowing(col.item);
            followBtn.setText(on ? "Siguiendo" : "Seguir");
            followBtn.setTextColor(on ? Ui.TEXT : 0xFF000000);
            followBtn.setBackground(Ui.ripple(on ? Ui.rounded(Ui.CARD, Ui.dp(MainActivity.this, 24))
                    : Ui.rounded(Ui.TEXT, Ui.dp(MainActivity.this, 24))));
        }

        DetailScreen(Collection col) {
            this.col = col;
        }

        @Override
        View create() {
            final ListView list = new ListView(MainActivity.this);
            list.setDivider(null);
            list.setSelector(Ui.ripple(null));

            LinearLayout header = Ui.column(MainActivity.this);
            header.setLayoutParams(new AbsListView.LayoutParams(Ui.MATCH, Ui.WRAP));
            int accent = col.kind == C_LIKED ? 0xFF450AF5
                    : Covers.accent(new Track("x", col.title(), col.title(), col.title(), 0, 0, true, 0));
            if ((col.kind == C_ALBUM || col.kind == C_ARTIST) && !col.tracks().isEmpty()) {
                accent = Covers.accent(col.tracks().get(0));
            }
            if (col.kind == C_ITEM) accent = Covers.accent(new Track("x", col.title(), col.title(), col.title(), 0, 0, true, 0));
            header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{darken(accent), Ui.BG}));

            LinearLayout top = Ui.row(MainActivity.this);
            top.setPadding(Ui.dp(MainActivity.this, 4), Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 4), 0);
            top.addView(Ui.iconButton(MainActivity.this, R.drawable.ic_back, 48, Ui.TEXT, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            }));
            top.addView(new View(MainActivity.this), new LinearLayout.LayoutParams(0, 1, 1));
            if (col.kind == C_ITEM && col.item.kind == Library.Item.PODCAST) {
                followBtn = pillButton("Seguir", Ui.TEXT, 0xFF000000, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        lib.toggleFollow(col.item);
                    }
                });
                LinearLayout.LayoutParams flp = Ui.lp(Ui.WRAP, Ui.WRAP);
                flp.setMargins(0, 0, Ui.dp(MainActivity.this, 12), 0);
                top.addView(followBtn, flp);
                updateFollow();
            }
            if (col.kind == C_PLAYLIST) {
                top.addView(Ui.iconButton(MainActivity.this, R.drawable.ic_more, 48, Ui.TEXT, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        playlistMenu(v, col.name);
                    }
                }));
            }
            header.addView(top);

            View cover = collectionCover(col, 200);
            LinearLayout.LayoutParams clp = Ui.lp(Ui.dp(MainActivity.this, 200), Ui.dp(MainActivity.this, 200));
            clp.gravity = Gravity.CENTER_HORIZONTAL;
            clp.setMargins(0, Ui.dp(MainActivity.this, 8), 0, Ui.dp(MainActivity.this, 16));
            cover.setElevation(Ui.dp(MainActivity.this, 12));
            header.addView(cover, clp);

            TextView title = Ui.text(MainActivity.this, col.title(), 24, Ui.TEXT, true);
            title.setPadding(Ui.dp(MainActivity.this, 16), 0, Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 4));
            header.addView(title);
            subtitle = Ui.text(MainActivity.this, "", 13, Ui.SUB, false);
            subtitle.setPadding(Ui.dp(MainActivity.this, 16), 0, Ui.dp(MainActivity.this, 16), 0);
            header.addView(subtitle);

            LinearLayout actions = Ui.row(MainActivity.this);
            actions.setPadding(Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 8));
            actions.addView(new View(MainActivity.this), new LinearLayout.LayoutParams(0, 1, 1));
            shuffleBtn = Ui.iconButton(MainActivity.this, R.drawable.ic_shuffle, 48, Ui.SUB, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    player.shufflePlay(adapter.tracks(), col.title());
                }
            });
            actions.addView(shuffleBtn);
            playBtn = Ui.icon(MainActivity.this, R.drawable.ic_play, 56, 0xFF000000);
            playBtn.setBackground(Ui.ripple(Ui.oval(Ui.GREEN)));
            playBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isThisPlaying()) {
                        player.togglePlay();
                    } else if (!adapter.tracks().isEmpty()) {
                        if (player.isShuffle()) player.shufflePlay(adapter.tracks(), col.title());
                        else player.playList(adapter.tracks(), 0, col.title());
                    }
                }
            });
            LinearLayout.LayoutParams plp = Ui.lp(Ui.dp(MainActivity.this, 56), Ui.dp(MainActivity.this, 56));
            plp.setMargins(Ui.dp(MainActivity.this, 8), 0, 0, 0);
            actions.addView(playBtn, plp);
            header.addView(actions);

            if (col.kind == C_DEVICE && !Library.hasAudioPermission(MainActivity.this)) {
                header.addView(permissionCard());
            }
            TextView empty = Ui.text(MainActivity.this, emptyText(), 15, Ui.SUB, false);
            empty.setSingleLine(false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Ui.dp(MainActivity.this, 32), Ui.dp(MainActivity.this, 32), Ui.dp(MainActivity.this, 32), Ui.dp(MainActivity.this, 32));
            emptyView = empty;
            empty.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Library.Section sec = col.section();
                    if (sec != null && sec.state == Library.FAILED) lib.loadSection(sec);
                    if (col.kind == C_ITEM) lib.retryItem(col.item);
                }
            });
            header.addView(empty);

            shownEmpty = col.tracks().isEmpty();
            list.addHeaderView(header, null, false);
            adapter = new TrackAdapter(MainActivity.this, new TrackAdapter.MoreHandler() {
                @Override
                public void onMore(Track t, View anchor) {
                    trackMenu(t, anchor, col);
                }
            });
            list.setAdapter(adapter);
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    int index = position - list.getHeaderViewsCount();
                    if (index >= 0) player.playList(adapter.tracks(), index, col.title());
                }
            });
            View spacer = new View(MainActivity.this);
            spacer.setLayoutParams(new AbsListView.LayoutParams(Ui.MATCH, Ui.dp(MainActivity.this, 24)));
            list.addFooterView(spacer, null, false);
            onLibraryChanged();
            return list;
        }

        String emptyText() {
            switch (col.kind) {
                case C_LIKED:
                    return "Las canciones que marques con ♥ aparecerán aquí.";
                case C_PLAYLIST:
                    return "Esta playlist está vacía. Usa ⋮ › \"Añadir a playlist…\" en cualquier canción.";
                case C_DEVICE:
                    return lib.isLoading() ? "Buscando canciones…" : "No hay canciones en este dispositivo.";
                case C_ONLINE: {
                    Library.Section sec = col.section();
                    if (sec != null && sec.state == Library.FAILED) return "Sin conexión a internet.\nToca aquí para reintentar.";
                    return "Cargando…";
                }
                case C_ARTIST:
                    return col.artistId > 0 ? "Cargando canciones…" : "Nada por aquí todavía.";
                case C_ITEM:
                    if (lib.itemState(col.item) == Library.FAILED) {
                        return "No se pudo cargar. Revisa tu conexión.\nToca aquí para reintentar.";
                    }
                    return "Cargando…";
                default:
                    return "Nada por aquí todavía.";
            }
        }

        boolean isThisPlaying() {
            return col.title().equals(player.queueName()) && player.current() != null;
        }

        @Override
        void onLibraryChanged() {
            if (adapter == null) return;
            List<Track> tracks = col.tracks();
            if (shownEmpty && !tracks.isEmpty() && (col.kind == C_ONLINE || col.kind == C_ARTIST || col.kind == C_ITEM)) {
                shownEmpty = false;
                render(); // first results arrived: rebuild so the header shows real cover art
                return;
            }
            updateFollow();
            adapter.setTracks(tracks);
            String len = col.kind == C_ITEM && col.item.kind == Library.Item.PODCAST ? "" : Ui.totalLength(tracks);
            subtitle.setText(col.type() + " · " + col.count(tracks.size()) + (len.isEmpty() ? "" : " · " + len));
            emptyView.setVisibility(tracks.isEmpty() ? View.VISIBLE : View.GONE);
            ((TextView) emptyView).setText(emptyText());
            onPlayerChanged();
        }

        @Override
        void onPlayerChanged() {
            if (adapter == null) return;
            adapter.notifyDataSetChanged();
            playBtn.setImageResource(isThisPlaying() && player.isActive() ? R.drawable.ic_pause : R.drawable.ic_play);
            shuffleBtn.setColorFilter(player.isShuffle() ? Ui.GREEN : Ui.SUB);
        }
    }
}
