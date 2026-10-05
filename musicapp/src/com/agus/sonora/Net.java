package com.agus.sonora;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny HTTP helper: GET on a background pool, callbacks on the main thread. */
final class Net {

    interface Callback {
        void onResult(String body, Exception error);
    }

    /** Pluggable transport so tests can serve canned responses. */
    interface Fetcher {
        byte[] get(String url) throws IOException;
    }

    static final Fetcher HTTP = new Fetcher() {
        @Override
        public byte[] get(String url) throws IOException {
            for (int redirects = 0; redirects < 5; redirects++) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "Sonora/1.6 (Android)");
                c.setRequestProperty("Accept-Language", java.util.Locale.getDefault().toLanguageTag());
                try {
                    int code = c.getResponseCode();
                    if (code / 100 == 3) { // http <-> https redirects are not followed automatically
                        String loc = c.getHeaderField("Location");
                        if (loc == null) throw new IOException("HTTP " + code);
                        url = new URL(new URL(url), loc).toString();
                        continue;
                    }
                    if (code / 100 != 2) throw new IOException("HTTP " + code);
                    InputStream in = c.getInputStream();
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        if (out.size() > 8 * 1024 * 1024) throw new IOException("respuesta demasiado grande");
                    }
                    in.close();
                    return out.toByteArray();
                } finally {
                    c.disconnect();
                }
            }
            throw new IOException("demasiadas redirecciones");
        }
    };

    static volatile Fetcher fetcher = HTTP;

    /** Follows redirects without downloading the body; pluggable for tests. */
    interface Resolver {
        String resolve(String url) throws IOException;
    }

    static final Resolver REDIRECTS = new Resolver() {
        @Override
        public String resolve(String url) throws IOException {
            for (int i = 0; i < 8; i++) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", "Sonora/1.6 (Android)");
                c.setRequestProperty("Range", "bytes=0-0");
                try {
                    int code = c.getResponseCode();
                    if (code / 100 == 3) {
                        String loc = c.getHeaderField("Location");
                        if (loc == null) throw new IOException("HTTP " + code);
                        url = new URL(new URL(url), loc).toString();
                        continue;
                    }
                    if (code / 100 != 2) throw new IOException("HTTP " + code);
                    return url;
                } finally {
                    c.disconnect();
                }
            }
            throw new IOException("demasiadas redirecciones");
        }
    };

    static volatile Resolver resolver = REDIRECTS;

    static String resolve(String url) throws IOException {
        return resolver.resolve(url);
    }

    static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Net() {
    }

    static byte[] getBytes(String url) throws IOException {
        return fetcher.get(url);
    }

    static String getString(String url) throws IOException {
        return new String(fetcher.get(url), "UTF-8");
    }

    static void getAsync(final String url, final Callback cb) {
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                String body = null;
                Exception error = null;
                try {
                    body = getString(url);
                } catch (Exception e) {
                    error = e;
                }
                final String b = body;
                final Exception e = error;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onResult(b, e);
                    }
                });
            }
        });
    }

    static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
