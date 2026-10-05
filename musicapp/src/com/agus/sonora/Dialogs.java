package com.agus.sonora;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.List;

/** Dialogs shared by the main screen and the now-playing screen. */
final class Dialogs {

    interface OnName {
        void onName(String name);
    }

    private Dialogs() {
    }

    static AlertDialog.Builder builder(Activity a) {
        return new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert);
    }

    static void newPlaylist(final Activity a, final OnName done) {
        final EditText input = new EditText(a);
        input.setHint("Nombre de la playlist");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(true);
        FrameLayout box = new FrameLayout(a);
        int pad = Ui.dp(a, 20);
        box.setPadding(pad, Ui.dp(a, 8), pad, 0);
        box.addView(input);
        builder(a)
                .setTitle("Nueva playlist")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Crear", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) name = "Mi playlist n.º " + (Library.get(a).playlistNames().size() + 1);
                        if (Library.get(a).createPlaylist(name)) {
                            if (done != null) done.onName(name);
                        } else {
                            Toast.makeText(a, "Ya existe una playlist con ese nombre", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    static void addToPlaylist(final Activity a, final Track t) {
        final Library lib = Library.get(a);
        final List<String> names = lib.playlistNames();
        String[] items = new String[names.size() + 1];
        items[0] = "+ Nueva playlist";
        for (int i = 0; i < names.size(); i++) items[i + 1] = names.get(i);
        builder(a)
                .setTitle("Añadir a playlist")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            newPlaylist(a, new OnName() {
                                @Override
                                public void onName(String name) {
                                    add(a, name, t);
                                }
                            });
                        } else {
                            add(a, names.get(which - 1), t);
                        }
                    }
                })
                .show();
    }

    private static void add(Activity a, String playlist, Track t) {
        boolean added = Library.get(a).addToPlaylist(playlist, t);
        Toast.makeText(a, added ? "Añadida a " + playlist : "Ya estaba en " + playlist,
                Toast.LENGTH_SHORT).show();
    }

    static void showQueue(final Activity a) {
        final PlayerEngine p = PlayerEngine.get(a);
        List<Track> up = p.upcoming();
        if (up.isEmpty()) {
            Toast.makeText(a, "La cola está vacía", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[up.size()];
        for (int i = 0; i < up.size(); i++) {
            items[i] = (i == 0 ? "▶ " : (i + ". ")) + up.get(i).title + " — " + up.get(i).artist;
        }
        builder(a).setTitle("Cola de reproducción").setItems(items, null)
                .setPositiveButton("Cerrar", null).show();
    }
}
