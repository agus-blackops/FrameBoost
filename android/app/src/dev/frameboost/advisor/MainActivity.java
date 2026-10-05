package dev.frameboost.advisor;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

/**
 * Hosts the advisor's web UI.
 *
 * <p>The page and its assets are served from {@code https://appassets.androidplatform.net},
 * mapped onto {@code assets/www}, rather than from {@code file://}: a real https origin is what
 * lets the page fetch its WebAssembly, start workers and call api.anthropic.com under ordinary
 * browser rules. The host is the one Android reserves for exactly this purpose.
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/index.html";

    private WebView web;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        paintSystemBars(dark);

        web = new WebView(this);
        web.setBackgroundColor(dark ? Color.rgb(0x0f, 0x11, 0x15) : Color.rgb(0xf6, 0xf7, 0xf9));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new Client());
        setContentView(web);

        if (saved == null || web.restoreState(saved) == null) {
            web.loadUrl(START);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    private void paintSystemBars(boolean dark) {
        Window w = getWindow();
        w.setStatusBarColor(dark ? Color.rgb(0x17, 0x1a, 0x20) : Color.WHITE);
        w.setNavigationBarColor(dark ? Color.rgb(0x17, 0x1a, 0x20) : Color.WHITE);
        if (!dark) {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private final class Client extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            Uri uri = req.getUrl();
            if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost())) {
                return null; // the network, i.e. api.anthropic.com
            }
            String path = uri.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) path = "/index.html";
            if (path.contains("..")) return notFound();
            try {
                InputStream in = getAssets().open("www" + path);
                return new WebResourceResponse(mimeType(path), null, in);
            } catch (IOException e) {
                return notFound();
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri uri = req.getUrl();
            if (HOST.equals(uri.getHost())) return false;
            // Links in an answer open in the browser, never inside the app.
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException ignored) {
                // Nothing can open it; stay put.
            }
            return true;
        }
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                Collections.<String, String>emptyMap(), null);
    }

    private static String mimeType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js")) return "text/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".wasm")) return "application/wasm";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }
}
