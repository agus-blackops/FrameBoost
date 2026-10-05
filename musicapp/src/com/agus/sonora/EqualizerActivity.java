package com.agus.sonora;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Equalizer with presets and per-band sliders, plus bass boost and surround. */
public final class EqualizerActivity extends Activity {

    private Effects fx;
    private Effects.Info info;
    private final List<TextView> chips = new ArrayList<>();
    private final List<SeekBar> bandBars = new ArrayList<>();
    private final List<TextView> bandValues = new ArrayList<>();
    private boolean updating;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        fx = Effects.get(this);
        info = Effects.info();

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
        TextView title = Ui.text(this, "Ecualizador", 20, Ui.TEXT, true);
        title.setPadding(Ui.dp(this, 8), 0, 0, 0);
        top.addView(title, Ui.weight(1));
        final Switch on = new Switch(this);
        on.setChecked(fx.isEnabled());
        on.setThumbTintList(ColorStateList.valueOf(Ui.GREEN));
        on.setTrackTintList(ColorStateList.valueOf(0x661DB954));
        on.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                fx.setEnabled(checked);
                refresh();
            }
        });
        top.addView(on);
        root.addView(top);

        LinearLayout body = Ui.column(this);
        body.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 32));

        if (info == null) {
            TextView msg = Ui.text(this, "Este dispositivo no permite ajustar el ecualizador.", 15, Ui.SUB, false);
            msg.setSingleLine(false);
            msg.setPadding(0, Ui.dp(this, 32), 0, 0);
            body.addView(msg);
        } else {
            body.addView(heading("Ajustes preestablecidos"));
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout chipRow = Ui.row(this);
            for (int i = 0; i < Effects.PRESET_NAMES.length; i++) {
                final int index = i;
                TextView chip = Ui.text(this, Effects.PRESET_NAMES[i], 13, Ui.TEXT, false);
                chip.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!fx.isEnabled()) on.setChecked(true);
                        fx.setPreset(index);
                        refresh();
                    }
                });
                LinearLayout.LayoutParams lp = Ui.lp(Ui.WRAP, Ui.WRAP);
                lp.setMargins(0, 0, Ui.dp(this, 8), 0);
                chipRow.addView(chip, lp);
                chips.add(chip);
            }
            hs.addView(chipRow);
            body.addView(hs);

            body.addView(heading("Bandas"));
            for (int b = 0; b < info.bands; b++) {
                final int band = b;
                LinearLayout row = Ui.row(this);
                row.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
                TextView freq = Ui.text(this, hz(info.centerHz[b]), 13, Ui.SUB, false);
                row.addView(freq, Ui.lp(Ui.dp(this, 64), Ui.WRAP));
                SeekBar bar = new SeekBar(this);
                bar.setMax(Math.round((info.maxDb - info.minDb) * 2)); // half-dB steps
                bar.setProgressTintList(ColorStateList.valueOf(Ui.GREEN));
                bar.setThumbTintList(ColorStateList.valueOf(Ui.GREEN));
                bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x44FFFFFF));
                bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                        if (!fromUser || updating) return;
                        if (!fx.isEnabled()) on.setChecked(true);
                        fx.setBand(band, info.minDb + progress / 2f);
                        bandValues.get(band).setText(db(info.minDb + progress / 2f));
                        selectChip();
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar s) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar s) {
                    }
                });
                row.addView(bar, Ui.weight(1));
                TextView value = Ui.text(this, "0 dB", 13, Ui.TEXT, false);
                value.setGravity(Gravity.END);
                row.addView(value, Ui.lp(Ui.dp(this, 64), Ui.WRAP));
                body.addView(row);
                bandBars.add(bar);
                bandValues.add(value);
            }
        }

        body.addView(heading("Refuerzo de graves"));
        body.addView(strengthRow(fx.bass(), new Setter() {
            @Override
            public void set(int v) {
                fx.setBass(v);
            }
        }));
        body.addView(heading("Sonido envolvente"));
        body.addView(strengthRow(fx.surround(), new Setter() {
            @Override
            public void set(int v) {
                fx.setSurround(v);
            }
        }));
        TextView note = Ui.text(this, "Los efectos se aplican a la canción que está sonando y a las siguientes.", 12, Ui.MUTED, false);
        note.setSingleLine(false);
        note.setPadding(0, Ui.dp(this, 24), 0, 0);
        body.addView(note);

        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        setContentView(root);
        refresh();
    }

    private interface Setter {
        void set(int v);
    }

    private TextView heading(String s) {
        TextView t = Ui.text(this, s, 15, Ui.TEXT, true);
        t.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 10));
        return t;
    }

    private View strengthRow(int initial, final Setter setter) {
        LinearLayout row = Ui.row(this);
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(initial / 10);
        bar.setProgressTintList(ColorStateList.valueOf(Ui.GREEN));
        bar.setThumbTintList(ColorStateList.valueOf(Ui.GREEN));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x44FFFFFF));
        final TextView value = Ui.text(this, initial / 10 + "%", 13, Ui.TEXT, false);
        value.setGravity(Gravity.END);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                value.setText(progress + "%");
                if (fromUser) setter.set(progress * 10);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        row.addView(bar, Ui.weight(1));
        row.addView(value, Ui.lp(Ui.dp(this, 48), Ui.WRAP));
        return row;
    }

    static String hz(int hz) {
        return hz >= 1000 ? String.format(Locale.ROOT, "%.1f kHz", hz / 1000f).replace(".0 ", " ") : hz + " Hz";
    }

    static String db(float v) {
        return String.format(Locale.ROOT, "%+.1f dB", v).replace("+0.0", "0").replace("-0.0", "0");
    }

    /** Syncs sliders and chips with the stored settings. */
    private void refresh() {
        updating = true;
        if (info != null) {
            float[] levels = fx.levelsDb();
            for (int b = 0; b < bandBars.size() && b < levels.length; b++) {
                bandBars.get(b).setProgress(Math.round((levels[b] - info.minDb) * 2));
                bandBars.get(b).setEnabled(fx.isEnabled());
                bandValues.get(b).setText(db(levels[b]));
            }
        }
        selectChip();
        updating = false;
    }

    private void selectChip() {
        for (int i = 0; i < chips.size(); i++) {
            boolean sel = fx.isEnabled() && i == fx.preset();
            TextView c = chips.get(i);
            c.setTextColor(sel ? 0xFF000000 : Ui.TEXT);
            c.setBackground(Ui.ripple(Ui.rounded(sel ? Ui.GREEN : Ui.CARD, Ui.dp(this, 18))));
        }
    }
}
