package com.frameboost.ragdoll;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity implements Game.Host {
    private GameView view;
    private Sfx sfx;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sfx = new Sfx();
        view = new GameView(this, sfx, new File(getFilesDir(), "mods"));
        view.game.host = this;
        setContentView(view);
        immersive();
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
                        String err = view.game.saveMod(existing, edit.getText().toString());
                        if (err != null) Toast.makeText(MainActivity.this, err, Toast.LENGTH_LONG).show();
                        immersive();
                    }
                });
        if (existing != null) {
            bd.setNeutralButton("Borrar", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { view.game.deleteMod(existing); immersive(); }
            });
        }
        bd.show();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override public void onBackPressed() {
        if (view != null && view.game.ui.stripOpen) {
            view.game.ui.stripOpen = false;
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (sfx != null) sfx.release();
    }
}
