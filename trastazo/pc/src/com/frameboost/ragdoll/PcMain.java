package com.frameboost.ragdoll;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * PC port: the same game running in a desktop window. The Android drawing and audio classes are replaced by
 * Java2D / Java Sound versions (pc/shim); this class supplies the window, the game loop, mouse and keyboard
 * input, settings and save files.
 */
public final class PcMain implements Game.Host {
    /** The running game (lets the desktop smoke test drive it). */
    static volatile Game current;
    static volatile float currentScale = 1f;
    private final File dir;
    private final Game game;
    private final Sfx sfx;
    private final MusicPlayer music;
    private final JFrame frame = new JFrame("Trastazo");
    private final java.awt.Canvas view = new java.awt.Canvas();
    private volatile boolean running = true;
    private volatile boolean resized = true;
    private BufferedImage back;
    private android.graphics.Canvas backCanvas;
    private boolean leftDown, panning;
    /** Render resolution relative to the window: lowered automatically when drawing is too slow. */
    private volatile float scale = 1f;
    private float drawAvg = 10f;
    private int drawFrames;
    private float sx(int x) { return x * scale; }
    private float sy(int y) { return y * scale; }
    private int lastX, lastY;

    public static void main(String[] args) {
        System.setProperty("sun.java2d.d3d", "false");   // the Direct3D pipeline flickers with BufferStrategy on some drivers
        final PcMain[] app = new PcMain[1];
        try {
            SwingUtilities.invokeAndWait(new Runnable() { @Override public void run() { app[0] = new PcMain(); } });
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "No se pudo iniciar Trastazo:\n" + e, "Trastazo", JOptionPane.ERROR_MESSAGE);
            return;
        }
        app[0].loop();
    }

    private static File dataDir() {
        String appdata = System.getenv("APPDATA");
        File d = appdata != null ? new File(appdata, "Trastazo") : new File(System.getProperty("user.home"), ".trastazo");
        d.mkdirs();
        return d;
    }

    private PcMain() {
        Game.desktop = true;
        dir = dataDir();
        Report.install(dir, System.getProperty("os.name") + " · Java " + System.getProperty("java.version"));
        File cache = new File(dir, "cache");
        cache.mkdirs();
        sfx = new Sfx(cache);
        game = new Game(sfx, new File(dir, "mods"));
        game.host = this;
        current = game;
        game.saveFile = new File(dir, "autosave2.trz");
        game.settings = loadSettings();
        game.settings.gpu = false;
        music = new MusicPlayer(cache);
        game.music = music;
        game.applySettings();
        music.play(Music.TITLE);
        music.start();

        view.setBackground(Color.BLACK);
        view.setIgnoreRepaint(true);
        view.setFocusable(true);
        view.setPreferredSize(new Dimension(1280, 720));
        frame.setLayout(new BorderLayout());
        frame.add(view, BorderLayout.CENTER);
        frame.setMinimumSize(new Dimension(800, 450));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setIgnoreRepaint(true);
        try {
            java.io.InputStream in = PcMain.class.getResourceAsStream("/trastazo-logo.png");
            if (in != null) { Image icon = ImageIO.read(in); frame.setIconImage(icon); }
        } catch (Exception ignored) { }
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { quit(); }
            @Override public void windowIconified(WindowEvent e) { music.setPaused(true); }
            @Override public void windowDeactivated(WindowEvent e) { synchronized (game) { if (game.screens.screen == Screens.NONE) game.paused = true; } }
            @Override public void windowDeiconified(WindowEvent e) { music.setPaused(false); }
        });
        view.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { resized = true; }
        });
        installInput();
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        view.requestFocus();
    }

    // ------------------------------------------------------------------------------- input ---

    private void installInput() {
        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                view.requestFocus();
                lastX = e.getX(); lastY = e.getY();
                if (SwingUtilities.isLeftMouseButton(e) && !leftDown) {
                    leftDown = true;
                    synchronized (game) { game.touch(Game.DOWN, 0, sx(e.getX()), sy(e.getY())); }
                } else if (!SwingUtilities.isLeftMouseButton(e)) panning = true;
            }
            @Override public void mouseDragged(MouseEvent e) {
                synchronized (game) {
                    if (leftDown) game.touch(Game.MOVE, 0, sx(e.getX()), sy(e.getY()));
                    if (panning && game.screens.screen == Screens.NONE) game.panBy((e.getX() - lastX) * scale, (e.getY() - lastY) * scale);
                }
                lastX = e.getX(); lastY = e.getY();
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && leftDown) {
                    leftDown = false;
                    synchronized (game) { game.touch(Game.UP, 0, sx(e.getX()), sy(e.getY())); }
                } else if (!SwingUtilities.isLeftMouseButton(e)) panning = false;
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                synchronized (game) {
                    if (game.screens.screen != Screens.NONE) return;
                    // over the open catalog the wheel scrolls it; elsewhere it zooms
                    if (game.ui.stripOpen && sx(e.getX()) < game.ui.leftW()) {
                        game.ui.scrollY = Math.max(0, Math.min(game.ui.maxScrollY(), game.ui.scrollY + (float) e.getPreciseWheelRotation() * 60f * game.density));
                    } else game.zoomAt(sx(e.getX()), sy(e.getY()), (float) Math.pow(1.12, -e.getPreciseWheelRotation()));
                }
            }
        };
        view.addMouseListener(m);
        view.addMouseMotionListener(m);
        view.addMouseWheelListener(m);
        view.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                if (leftDown) { leftDown = false; synchronized (game) { game.touch(Game.CANCEL, 0, sx(lastX), sy(lastY)); } }
                panning = false;
            }
        });
        view.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { key(e); }
        });
    }

    private void key(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_F11 || (k == KeyEvent.VK_ENTER && e.isAltDown())) { toggleFullscreen(); return; }
        synchronized (game) {
            boolean playing = game.screens.screen == Screens.NONE;
            if (k == KeyEvent.VK_ESCAPE) {
                if (!game.back() && game.fullscreen) toggleFullscreen();
                return;
            }
            if (!playing) return;
            if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_P) game.pressButton(Ui.BTN_PAUSE);
            else if (k == KeyEvent.VK_Z && e.isControlDown()) { if (!game.ui.btnHidden[Ui.BTN_UNDO]) game.pressButton(Ui.BTN_UNDO); }
            else if (k == KeyEvent.VK_S) game.pressButton(Ui.BTN_SLOW);
            else if (k == KeyEvent.VK_G) { if (!game.ui.btnHidden[Ui.BTN_GRAV]) game.pressButton(Ui.BTN_GRAV); }
            else if (k == KeyEvent.VK_H) game.tool = Items.HAND;
            else if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_9 || k == KeyEvent.VK_0) {
                int i = k == KeyEvent.VK_0 ? 9 : k - KeyEvent.VK_1;
                if (!game.ui.dock && i < game.ui.cats.length) game.toggleCategory(game.ui.cats[i]);
            } else if (k == KeyEvent.VK_EQUALS || k == KeyEvent.VK_PLUS || k == KeyEvent.VK_ADD) game.zoomAt(game.sw / 2f, game.sh / 2f, 1.15f);
            else if (k == KeyEvent.VK_MINUS || k == KeyEvent.VK_SUBTRACT) game.zoomAt(game.sw / 2f, game.sh / 2f, 1f / 1.15f);
            else if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) game.panBy(80, 0);
            else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) game.panBy(-80, 0);
            else if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) game.panBy(0, 80);
            else if (k == KeyEvent.VK_DOWN) game.panBy(0, -80);
        }
    }

    // -------------------------------------------------------------------------------- loop ---

    private void loop() {
        long last = System.nanoTime();
        final long frameNs = 1_000_000_000L / 60;
        while (running) {
            long start = System.nanoTime();
            float dt = Math.min(0.1f, (start - last) / 1e9f);
            last = start;
            try {
                frame();
            } catch (IllegalStateException ignored) {
                // the buffer strategy is gone (full screen toggle, minimised): it is rebuilt next frame
                resized = true;
            }
            synchronized (game) { game.update(dt); }
            long left = frameNs - (System.nanoTime() - start);
            if (left > 0) { try { Thread.sleep(left / 1_000_000L, (int) (left % 1_000_000L)); } catch (InterruptedException ignored) { } }
        }
    }

    private void frame() {
        int vw = view.getWidth(), vh = view.getHeight();
        if (vw < 16 || vh < 16 || !view.isDisplayable()) return;
        int w = Math.round(vw * scale), h = Math.round(vh * scale);
        if (resized || back == null || back.getWidth() != w || back.getHeight() != h) {
            resized = false;
            if (backCanvas != null) backCanvas.dispose();
            back = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            backCanvas = new android.graphics.Canvas(back);
            // the interface was designed in dp for phones: scale it to the window height
            float d = Math.max(1.2f, Math.min(3.6f, Math.min(h / 400f, w / 720f)));
            synchronized (game) {
                // keep the view when only the window size or the render scale changes
                int ow = game.sw;
                float cx = game.camX, cy = game.camY, z = game.zoom;
                game.resize(w, h, d);
                if (ow > 0 && game.screens.screen == Screens.NONE) { game.camX = cx; game.camY = cy; game.zoom = z * w / ow; game.clampCam(); }
            }
            if (view.getBufferStrategy() == null) view.createBufferStrategy(2);
        }
        BufferStrategy bs = view.getBufferStrategy();
        if (bs == null) { view.createBufferStrategy(2); return; }
        long t0 = System.nanoTime();
        synchronized (game) { backCanvas.setAntiAlias(game.settings.highQuality); game.draw(backCanvas); }
        adapt((System.nanoTime() - t0) / 1e6f);
        do {
            do {
                Graphics g = bs.getDrawGraphics();
                if (scale < 1f) {
                    ((java.awt.Graphics2D) g).setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(back, 0, 0, vw, vh, null);
                } else g.drawImage(back, 0, 0, null);
                g.dispose();
            } while (bs.contentsRestored());
            bs.show();
        } while (bs.contentsLost());
        java.awt.Toolkit.getDefaultToolkit().sync();
    }

    /** Keeps the frame rate up on slow machines by drawing at a lower resolution (and back up when there is room). */
    private void adapt(float ms) {
        drawAvg += (ms - drawAvg) * 0.05f;
        if (++drawFrames < 90) return;
        float ns = scale;
        if (drawAvg > 13f && scale > 0.55f) ns = Math.max(0.5f, scale - 0.125f);
        else if (drawAvg < 6f && scale < 1f) ns = Math.min(1f, scale + 0.125f);
        if (ns != scale) { scale = ns; currentScale = ns; resized = true; drawFrames = 0; drawAvg = 9.5f; }
    }

    private void quit() {
        running = false;
        synchronized (game) { game.saveScene(); }
        saveSettings(game.settings);
        try { music.stop(); sfx.release(); } catch (Throwable ignored) { }
        frame.dispose();
        System.exit(0);
    }

    // ------------------------------------------------------------------------------- host ---

    @Override public void toggleFullscreen() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                boolean fs = !game.fullscreen;
                frame.dispose();
                frame.setUndecorated(fs);
                frame.setExtendedState(fs ? JFrame.MAXIMIZED_BOTH : JFrame.NORMAL);
                if (!fs) { frame.pack(); frame.setLocationRelativeTo(null); }
                frame.setVisible(true);
                view.requestFocus();
                synchronized (game) { game.fullscreen = fs; }
                resized = true;
            }
        });
    }

    @Override public void switchEngine(boolean gpu) { }

    @Override public void vibrate(int ms) { }

    @Override public void editMod(final Mod existing) {
        SwingUtilities.invokeLater(new Runnable() { @Override public void run() { openEditor(existing); } });
    }

    /** Workshop editor: a text box where the player writes a mod script. */
    private void openEditor(final Mod existing) {
        final JDialog dlg = new JDialog(frame, existing == null ? "Crear mod" : "Editar mod", true);
        final JTextArea edit = new JTextArea(existing != null ? existing.source
                : "name Mi Mod\ndesc Lo que hace\ngravity 0.5\nonce spawn human 3\nevery 3 spawn crate\n", 10, 50);
        edit.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        JTextArea help = new JTextArea(Mod.HELP, 10, 50);
        help.setEditable(false);
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setBackground(new Color(0xEEF0F4));
        JPanel buttons = new JPanel();
        JButton save = new JButton("Guardar"), cancel = new JButton("Cancelar"), del = new JButton("Borrar");
        save.addActionListener(e -> {
            String err;
            synchronized (game) { err = game.saveMod(existing, edit.getText()); }
            if (err != null) JOptionPane.showMessageDialog(dlg, err, "Mod", JOptionPane.WARNING_MESSAGE);
            else dlg.dispose();
        });
        cancel.addActionListener(e -> dlg.dispose());
        del.addActionListener(e -> { synchronized (game) { game.deleteMod(existing); } dlg.dispose(); });
        buttons.add(save);
        buttons.add(cancel);
        if (existing != null) buttons.add(del);
        JPanel box = new JPanel(new BorderLayout(0, 8));
        box.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        box.add(new JScrollPane(edit), BorderLayout.CENTER);
        box.add(new JScrollPane(help), BorderLayout.SOUTH);
        dlg.add(box, BorderLayout.CENTER);
        dlg.add(buttons, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(frame);
        dlg.setVisible(true);
        view.requestFocus();
    }

    private Settings loadSettings() {
        Settings s = new Settings();
        Properties p = new Properties();
        File f = new File(dir, "settings.properties");
        if (!f.exists()) return s;
        try (FileInputStream in = new FileInputStream(f)) { p.load(in); } catch (Exception e) { return s; }
        s.music = Float.parseFloat(p.getProperty("music", "" + s.music));
        s.sfx = Float.parseFloat(p.getProperty("sfx", "" + s.sfx));
        s.gore = Boolean.parseBoolean(p.getProperty("gore", "" + s.gore));
        s.showFps = Boolean.parseBoolean(p.getProperty("fps", "" + s.showFps));
        s.highQuality = Boolean.parseBoolean(p.getProperty("quality", "" + s.highQuality));
        s.tutorialDone = Boolean.parseBoolean(p.getProperty("tutorial", "" + s.tutorialDone));
        s.stars = p.getProperty("stars", s.stars);
        s.textures = Boolean.parseBoolean(p.getProperty("textures", "" + s.textures));
        s.muted = Boolean.parseBoolean(p.getProperty("muted", "" + s.muted));
        s.ach = p.getProperty("ach", s.ach);
        return s;
    }

    @Override public void saveSettings(Settings s) {
        Properties p = new Properties();
        p.setProperty("music", "" + s.music);
        p.setProperty("sfx", "" + s.sfx);
        p.setProperty("gore", "" + s.gore);
        p.setProperty("fps", "" + s.showFps);
        p.setProperty("quality", "" + s.highQuality);
        p.setProperty("tutorial", "" + s.tutorialDone);
        p.setProperty("stars", s.stars);
        p.setProperty("textures", "" + s.textures);
        p.setProperty("muted", "" + s.muted);
        p.setProperty("ach", s.ach);
        try (FileOutputStream out = new FileOutputStream(new File(dir, "settings.properties"))) { p.store(out, "Trastazo"); } catch (Exception ignored) { }
    }
}
