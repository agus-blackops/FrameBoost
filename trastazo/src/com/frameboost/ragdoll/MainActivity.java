package com.frameboost.ragdoll;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Vibrator;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity implements Game.Host, GLGameView.Listener {
    private Game game;
    private GLGameView glView;
    private Sfx sfx;
    private MusicPlayer music;
    private Vibrator vibrator;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sfx = new Sfx(getCacheDir());
        game = new Game(sfx, new File(getFilesDir(), "mods"));
        Game g = game;
        g.host = this;
        g.saveFile = new File(getFilesDir(), "autosave2.trz");
        g.settings = loadSettings();
        music = new MusicPlayer(getCacheDir());
        g.music = music;
        g.applySettings();
        music.play(Music.TITLE);
        music.start();
        try { vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); } catch (Throwable ignored) { }
        showEngine(g.settings.gpu);
        immersive();
    }

    /** GPU engine (GLSurfaceView + interface overlay) or the Canvas fallback. */
    private void showEngine(boolean gpu) {
        if (glView != null) { glView.onPause(); glView = null; }
        if (gpu) {
            FrameLayout root = new FrameLayout(this);
            glView = new GLGameView(this, game, this);
            root.addView(glView);
            root.addView(new GLGameView.OverlayView(this, game));
            setContentView(root);
        } else {
            setContentView(new GameView(this, game));
        }
    }

    /** The GPU engine could not start on this device: switch to Canvas and remember it. */
    @Override public void onEngineFailed(String why) {
        synchronized (game) {
            game.settings.gpu = false;
            saveSettings(game.settings);
            game.ui.toast("Motor GPU no disponible: usando el motor Canvas");
        }
        showEngine(false);
        immersive();
    }

    @Override public void switchEngine(final boolean gpu) {
        runOnUiThread(new Runnable() { @Override public void run() { showEngine(gpu); immersive(); } });
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /** Workshop editor: a plain text box where the player writes a mod script. */
    @Override public void editMod(final Mod existing) {
        runOnUiThread(new Runnable() { @Override public void run() { openEditor(existing); } });
    }

    private void openEditor(final Mod existing) {
        final EditText edit = new EditText(this);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setMinLines(6);
        edit.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        edit.setTypeface(android.graphics.Typeface.MONOSPACE);
        edit.setText(existing != null ? existing.source
                : "name Mi Mod\ndesc Lo que hace\ngravity 0.5\nonce spawn human 3\nevery 3 spawn crate\n");
        TextView help = new TextView(this);
        help.setText(Mod.HELP);
        help.setTextSize(11);
        help.setPadding(0, 16, 0, 0);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(36, 12, 36, 0);
        box.addView(edit);
        box.addView(help);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog.Builder bd = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Crear mod" : "Editar mod")
                .setView(sv)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String err;
                        synchronized (game) { err = game.saveMod(existing, edit.getText().toString()); }
                        if (err != null) Toast.makeText(MainActivity.this, err, Toast.LENGTH_LONG).show();
                        immersive();
                    }
                });
        if (existing != null) {
            bd.setNeutralButton("Borrar", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { synchronized (game) { game.deleteMod(existing); } immersive(); }
            });
        }
        bd.show();
    }

    private Settings loadSettings() {
        SharedPreferences sp = getSharedPreferences("trastazo", MODE_PRIVATE);
        Settings s = new Settings();
        s.music = sp.getFloat("music", s.music);
        s.sfx = sp.getFloat("sfx", s.sfx);
        s.haptics = sp.getBoolean("haptics", s.haptics);
        s.gore = sp.getBoolean("gore", s.gore);
        s.showFps = sp.getBoolean("fps", s.showFps);
        s.highQuality = sp.getBoolean("quality", s.highQuality);
        s.tutorialDone = sp.getBoolean("tutorial", s.tutorialDone);
        s.stars = sp.getString("stars", s.stars);
        s.textures = sp.getBoolean("textures", s.textures);
        s.muted = sp.getBoolean("muted", s.muted);
        s.ach = sp.getString("ach", s.ach);
        s.gpu = sp.getBoolean("gpu", s.gpu);
        return s;
    }

    @Override public void saveSettings(Settings s) {
        getSharedPreferences("trastazo", MODE_PRIVATE).edit()
                .putFloat("music", s.music).putFloat("sfx", s.sfx).putBoolean("haptics", s.haptics)
                .putBoolean("gore", s.gore).putBoolean("fps", s.showFps).putBoolean("quality", s.highQuality)
                .putBoolean("tutorial", s.tutorialDone).putString("stars", s.stars)
                .putBoolean("textures", s.textures).putBoolean("muted", s.muted).putString("ach", s.ach).putBoolean("gpu", s.gpu).apply();
    }

    @Override public void vibrate(int ms) {
        try { if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(ms); } catch (Throwable ignored) { }
    }

    @Override protected void onPause() {
        super.onPause();
        if (music != null) music.setPaused(true);
        if (game != null) synchronized (game) { game.saveScene(); }
        if (glView != null) glView.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (music != null) music.setPaused(false);
        if (glView != null) glView.onResume();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override public void onBackPressed() {
        Game g = game;
        if (g != null) {
            if (g.screens.screen == Screens.NONE) {
                if (g.ui.stripOpen) g.ui.stripOpen = false;
                else { g.screens.screen = Screens.PAUSE; g.saveScene(); }
                return;
            }
            if (g.screens.screen != Screens.TITLE) {
                g.menuAction(g.screens.screen == Screens.PAUSE ? Screens.A_RESUME
                        : (g.screens.screen == Screens.RESULT ? Screens.A_MENU : Screens.A_BACK));
                return;
            }
        }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (sfx != null) sfx.release();
        if (music != null) music.stop();
    }
}
