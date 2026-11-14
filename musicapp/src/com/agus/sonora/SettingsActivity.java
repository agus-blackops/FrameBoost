package com.agus.sonora;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/** Ajustes: playback, downloads, podcasts, appearance and your data, in one place. */
public final class SettingsActivity extends Activity implements Downloads.Listener {

    private Library lib;
    private PlayerEngine player;
    private Downloads downloads;
    private LinearLayout body;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        lib = Library.get(this);
        player = PlayerEngine.get(this);
        downloads = Downloads.get(this);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        LinearLayout top = Ui.row(this);
        top.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 4));
        top.addView(Ui.iconButton(this, R.drawable.ic_back, 48, Ui.TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }));
        TextView title = Ui.text(this, "Ajustes", 20, Ui.TEXT, true);
        title.setPadding(Ui.dp(this, 8), 0, 0, 0);
        top.addView(title, Ui.weight(1));
        root.addView(top);
        scroll = new ScrollView(this);
        body = Ui.column(this);
        body.setPadding(0, 0, 0, Ui.dp(this, 32));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        setContentView(root);
        downloads.addListener(this);
        render();
    }

    @Override
    protected void onDestroy() {
        downloads.removeListener(this);
        super.onDestroy();
    }

    @Override
    public void onDownloadsChanged() {
        render();
    }

    /** Rebuilds the list, keeping the scroll position. */
    void render() {
        final int y = scroll.getScrollY();
        body.removeAllViews();

        section("Reproducción");
        toggle("Solo canciones completas", "Oculta las vistas previas de 30 s de Deezer en toda la app",
                lib.fullOnly(), new Toggle() {
                    @Override
                    public void set(boolean on) {
                        lib.setFullOnly(on);
                        if (on) lib.loadOnline();
                    }
                });

        String[] fades = new String[Crossfade.CHOICES_S.length];
        int sel = 0;
        for (int i = 0; i < fades.length; i++) {
            fades[i] = Crossfade.CHOICES_S[i] == 0 ? "Sin fundido (las canciones se siguen sin pausa)"
                    : Crossfade.CHOICES_S[i] + " segundos";
            if (Crossfade.CHOICES_S[i] * 1000 == player.crossfadeMs()) sel = i;
        }
        choice("Fundido entre canciones", fades, sel, null, new Choice() {
            @Override
            public void chose(int index) {
                player.setCrossfade(Crossfade.CHOICES_S[index] * 1000);
            }
        });
        info(null, "No se aplica entre canciones del mismo álbum o concierto, para no cortar transiciones pensadas así.");

        section("Podcasts");
        toggle("Saltar silencios", "En los episodios, pasa de largo los tramos sin sonido",
                player.skipSilence(), new Toggle() {
                    @Override
                    public void set(boolean on) {
                        player.setSkipSilence(on);
                    }
                });
        toggle("Avisos de episodios nuevos", "Revisa los podcasts que sigues cada pocas horas y te avisa",
                PodcastCheckJob.enabled(this), new Toggle() {
                    @Override
                    public void set(boolean on) {
                        PodcastCheckJob.setEnabled(SettingsActivity.this, on);
                    }
                });

        section("Descargas");
        int n = downloads.tracks().size();
        int waiting = downloads.pending().size();
        String used = n == 0 ? "No hay canciones ni episodios descargados"
                : Ui.songs(n) + " · " + size(downloads.bytesUsed()) + " en este teléfono";
        if (waiting > 0) used += " · " + waiting + " pendiente" + (waiting == 1 ? "" : "s");
        info("Espacio usado", used);
        toggle("Descargar solo con Wi-Fi", "Con datos móviles, las descargas esperan a una red Wi-Fi",
                downloads.wifiOnly(), new Toggle() {
                    @Override
                    public void set(boolean on) {
                        downloads.setWifiOnly(on);
                    }
                });
        if (n + waiting > 0) {
            action("Borrar todas las descargas", "Libera el espacio; podrás volver a descargarlas", new Runnable() {
                @Override
                public void run() {
                    new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle("¿Borrar todas las descargas?")
                            .setMessage("Se borrarán " + Ui.songs(n + waiting) + " de este teléfono.")
                            .setPositiveButton("Borrar", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    downloads.removeAll();
                                }
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                }
            });
        }

        section("Tus datos");
        action("Exportar copia de seguridad", "Me gusta, playlists, podcasts, progreso, historial y ajustes en un archivo",
                new Runnable() {
                    @Override
                    public void run() {
                        create(REQ_EXPORT, "application/json", Backup.fileName());
                    }
                });
        action("Restaurar copia de seguridad", "Suma lo de la copia a lo que ya tienes; no borra nada", new Runnable() {
            @Override
            public void run() {
                pick(REQ_RESTORE, "application/json", "text/plain", "application/octet-stream");
            }
        });
        action("Importar playlist (M3U)", "De otra app o de la computadora: busca cada canción en tu música", new Runnable() {
            @Override
            public void run() {
                pick(REQ_M3U, "audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl",
                        "application/x-mpegurl", "text/plain", "application/octet-stream");
            }
        });
        action("Importar podcasts (OPML)", "Tus suscripciones desde otra app de podcasts", new Runnable() {
            @Override
            public void run() {
                pick(REQ_OPML_IN, "text/x-opml", "text/xml", "application/xml", "text/plain", "application/octet-stream");
            }
        });
        action("Exportar podcasts (OPML)", "Para llevar tus suscripciones a otra app", new Runnable() {
            @Override
            public void run() {
                create(REQ_OPML_OUT, "text/x-opml", "podcasts-sonora.opml");
            }
        });

        section("Acerca de");
        info("Sonora " + version(), "Sin cuentas, sin anuncios y sin suscripción. Tus datos se guardan solo en este teléfono.");
        info("Licencias", "Reproductor Media3 (ExoPlayer) y Guava · Apache 2.0");

        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.scrollTo(0, y);
            }
        });
    }

    // ---------------------------------------------------------------- files (no permissions needed)

    static final int REQ_EXPORT = 1;
    static final int REQ_RESTORE = 2;
    static final int REQ_M3U = 3;
    static final int REQ_OPML_IN = 4;
    static final int REQ_OPML_OUT = 5;

    void create(int req, String mime, String name) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mime).putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, req);
    }

    void pick(int req, String... mimes) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, mimes);
        startActivityForResult(i, req);
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        handleFile(req, data.getData());
    }

    /** Does what was asked with the file the user picked or created. */
    void handleFile(int req, Uri uri) {
        Library lib = Library.get(this);
        try {
            switch (req) {
                case REQ_EXPORT:
                    write(uri, Backup.export(this));
                    toast("Copia de seguridad guardada");
                    break;
                case REQ_RESTORE:
                    report("Copia restaurada", Backup.restore(this, read(uri)).summary());
                    render();
                    break;
                case REQ_M3U: {
                    String name = Backup.playlistName(displayName(uri));
                    Library.ImportResult r = lib.importM3u(name, Backup.parseM3u(read(uri)));
                    String msg = r.playlists == 0 ? "No se encontró ninguna de sus canciones en tu música."
                            : "Playlist creada con " + Ui.songs(r.playlistSongs) + "."
                            + (r.missing > 0 ? " " + (r.missing == 1 ? "1 canción no se encontró." : r.missing + " no se encontraron.") : "");
                    report("Importar playlist", msg);
                    break;
                }
                case REQ_OPML_IN: {
                    Library.ImportResult r = lib.followAll(Backup.parseOpml(read(uri)));
                    report("Importar podcasts", r.podcasts == 0 ? "Ya seguías todos los podcasts del archivo."
                            : "Ahora sigues " + (r.podcasts == 1 ? "1 podcast nuevo." : r.podcasts + " podcasts nuevos."));
                    break;
                }
                case REQ_OPML_OUT: {
                    int n = 0;
                    for (Library.Item it : lib.followedPodcasts()) if (it.feed != null) n++;
                    write(uri, Backup.exportOpml(lib.followedPodcasts()));
                    toast(n == 1 ? "1 podcast exportado" : n + " podcasts exportados");
                    break;
                }
                default:
                    break;
            }
        } catch (Exception e) {
            report("No se pudo", e.getMessage() == null ? "El archivo no se pudo leer." : e.getMessage());
        }
    }

    String read(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("El archivo no se pudo abrir.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 20 * 1024 * 1024) throw new Exception("El archivo es demasiado grande.");
            }
            return new String(out.toByteArray(), "UTF-8");
        }
    }

    void write(Uri uri, String text) throws Exception {
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new Exception("El archivo no se pudo crear.");
            out.write(text.getBytes("UTF-8"));
        }
    }

    String displayName(Uri uri) {
        if ("file".equals(uri.getScheme())) return uri.getLastPathSegment();
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return uri.getLastPathSegment();
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    void report(String title, String msg) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("Aceptar", null).show();
    }

    String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    static String size(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
        if (bytes >= 1024L * 1024) return String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024));
        return Math.max(1, bytes / 1024) + " KB";
    }

    // ---------------------------------------------------------------- row builders

    interface Toggle {
        void set(boolean on);
    }

    void section(String name) {
        TextView t = Ui.text(this, name, 14, Ui.GREEN, true);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 4));
        body.addView(t);
    }

    private LinearLayout texts(String title, String sub) {
        LinearLayout col = Ui.column(this);
        if (title != null) col.addView(Ui.text(this, title, 16, Ui.TEXT, false));
        if (sub != null) {
            TextView s = Ui.text(this, sub, 13, Ui.SUB, false);
            s.setSingleLine(false);
            col.addView(s);
        }
        return col;
    }

    private LinearLayout rowBox() {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        r.setMinimumHeight(Ui.dp(this, 56));
        return r;
    }

    void info(String title, String sub) {
        LinearLayout r = rowBox();
        r.addView(texts(title, sub), Ui.weight(1));
        body.addView(r);
    }

    void toggle(String title, String sub, boolean on, final Toggle t) {
        LinearLayout r = rowBox();
        r.addView(texts(title, sub), Ui.weight(1));
        final Switch sw = new Switch(this);
        sw.setChecked(on);
        Ui.tintSwitch(sw);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                t.set(checked);
            }
        });
        r.addView(sw);
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sw.toggle();
            }
        });
        r.setBackground(Ui.ripple(null));
        body.addView(r);
    }

    void action(String title, String sub, final Runnable run) {
        LinearLayout r = rowBox();
        r.addView(texts(title, sub), Ui.weight(1));
        r.setBackground(Ui.ripple(null));
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                run.run();
            }
        });
        body.addView(r);
    }

    /** A setting with a few fixed options, shown as "title / current choice". */
    void choice(String title, final String[] options, int selected, final Toggle unused, final Choice c) {
        LinearLayout r = rowBox();
        r.addView(texts(title, options[selected]), Ui.weight(1));
        r.setBackground(Ui.ripple(null));
        final int sel = selected;
        final String t = title;
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle(t)
                        .setSingleChoiceItems(options, sel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                d.dismiss();
                                c.chose(which);
                                render();
                            }
                        })
                        .show();
            }
        });
        body.addView(r);
    }

    interface Choice {
        void chose(int index);
    }
}
