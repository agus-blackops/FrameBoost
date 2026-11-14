package com.agus.sonora;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

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
        col.addView(Ui.text(this, title, 16, Ui.TEXT, false));
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
