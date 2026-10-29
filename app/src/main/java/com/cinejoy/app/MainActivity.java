package com.cinejoy.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private FrameLayout customViewContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private RelativeLayout splashLayout;

    private static final String TARGET_URL = "https://cinejoy.pk/";
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST_CODE = 100;

    private boolean isAdBlockActive = true;
    private float currentPlaybackSpeed = 1.0f;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen awake for movies
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Immersive sticky fullscreen mode
        hideSystemUI();

        // Main Root layout
        RelativeLayout rootLayout = new RelativeLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#050505"));

        // WebView with hardware acceleration
        webView = new WebView(this);
        RelativeLayout.LayoutParams wvParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(wvParams);
        webView.setBackgroundColor(Color.parseColor("#050505"));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();

        // Progress bar at the top (Neon Lime color)
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#95FF50")));
        progressBar.setBackgroundColor(Color.TRANSPARENT);
        RelativeLayout.LayoutParams pbParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, 8);
        pbParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        progressBar.setLayoutParams(pbParams);

        // Container for full screen video playback
        customViewContainer = new FrameLayout(this);
        customViewContainer.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        customViewContainer.setBackgroundColor(Color.BLACK);
        customViewContainer.setVisibility(View.GONE);

        // Floating Control Button by Dr. Muhammed
        TextView drBadge = createDrMuhammedBadge();

        // Splash Screen Overlay by Dr. Muhammed
        splashLayout = createSplashScreen();

        rootLayout.addView(webView);
        rootLayout.addView(progressBar);
        rootLayout.addView(customViewContainer);
        rootLayout.addView(drBadge);
        rootLayout.addView(splashLayout);
        setContentView(rootLayout);

        // Configure WebSettings
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setTextZoom(100);

        // Anti-bot bypass: remove '; wv' from User-Agent
        String defaultUa = settings.getUserAgentString();
        if (defaultUa != null) {
            settings.setUserAgentString(defaultUa.replace("; wv", ""));
        }

        // Cookie management
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Allow all HTTP/HTTPS navigation (including video streams) inside WebView
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(15);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                webView.requestFocus();

                // Inject Dr. Muhammed AdBlock & Performance script
                injectDrMuhammedEnhancements();

                // Dismiss splash screen smoothly after page loads
                hideSplashScreen();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    progressBar.setVisibility(View.GONE);
                    hideSplashScreen();
                }
            }
        });

        // WebChromeClient
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    onHideCustomView();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                customViewContainer.setVisibility(View.VISIBLE);
                customViewContainer.addView(view);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                hideSystemUI();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                customViewContainer.removeView(customView);
                customView = null;
                customViewContainer.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                hideSystemUI();
                webView.requestFocus();
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                MainActivity.this.filePathCallback = filePathCallback;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                    return true;
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return true;
            }
        });

        // Downloads
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
        });

        // Load CineJoy
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(TARGET_URL);
        }

        // Welcome toast
        Toast.makeText(this, "🎬 CineJoy — By Dr. Muhammed", Toast.LENGTH_SHORT).show();

        // Safety timeout to hide splash after 3 seconds max
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                hideSplashScreen();
            }
        }, 3000);
    }

    // ==========================================
    // FEATURES BY DR. MUHAMMED
    // ==========================================

    private RelativeLayout createSplashScreen() {
        RelativeLayout splash = new RelativeLayout(this);
        splash.setLayoutParams(new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT));
        splash.setBackgroundColor(Color.parseColor("#050505"));

        LinearLayout centerBox = new LinearLayout(this);
        centerBox.setOrientation(LinearLayout.VERTICAL);
        centerBox.setGravity(Gravity.CENTER);
        RelativeLayout.LayoutParams cbParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT);
        cbParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        centerBox.setLayoutParams(cbParams);

        // App Icon
        ImageView logoView = new ImageView(this);
        logoView.setImageResource(R.drawable.ic_launcher);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(220, 220);
        logoParams.bottomMargin = 24;
        logoView.setLayoutParams(logoParams);

        // App Title
        TextView titleText = new TextView(this);
        titleText.setText("CINEJOY");
        titleText.setTextSize(32);
        titleText.setTextColor(Color.parseColor("#95FF50"));
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        titleText.setGravity(Gravity.CENTER);

        // By Dr. Muhammed Credit
        TextView authorText = new TextView(this);
        authorText.setText("⚡ Developed by Dr. Muhammed");
        authorText.setTextSize(15);
        authorText.setTextColor(Color.parseColor("#DDDDDD"));
        authorText.setGravity(Gravity.CENTER);
        authorText.setPadding(0, 12, 0, 0);

        centerBox.addView(logoView);
        centerBox.addView(titleText);
        centerBox.addView(authorText);
        splash.addView(centerBox);

        return splash;
    }

    private void hideSplashScreen() {
        if (splashLayout != null && splashLayout.getVisibility() == View.VISIBLE) {
            splashLayout.animate()
                .alpha(0f)
                .setDuration(400)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        splashLayout.setVisibility(View.GONE);
                    }
                });
        }
    }

    private TextView createDrMuhammedBadge() {
        TextView badge = new TextView(this);
        badge.setText("👨‍💻 Dr. Muhammed");
        badge.setTextSize(11);
        badge.setTextColor(Color.parseColor("#95FF50"));
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(24, 10, 24, 10);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#CC111111"));
        bg.setCornerRadius(30);
        bg.setStroke(2, Color.parseColor("#95FF50"));
        badge.setBackground(bg);

        RelativeLayout.LayoutParams badgeParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT);
        badgeParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        badgeParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        badgeParams.setMargins(0, 20, 20, 0);
        badge.setLayoutParams(badgeParams);

        badge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDrMuhammedMenu();
            }
        });

        return badge;
    }

    private void showDrMuhammedMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎬 CineJoy — By Dr. Muhammed");

        String[] options = {
            "🛡️ حظر الإعلانات والبوب أب: " + (isAdBlockActive ? "مفعّل ✅" : "معطّل ❌"),
            "⚡ سرعة التشغيل: " + currentPlaybackSpeed + "x",
            "⏩ تخطي المقدمة (Skip 85s)",
            "🔄 إعادة تحميل الصفحة",
            "🧹 مسح الذاكرة المؤقتة (Clear Cache)",
            "ℹ️ حول التطبيق والمطور"
        };

        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        isAdBlockActive = !isAdBlockActive;
                        Toast.makeText(MainActivity.this,
                            isAdBlockActive ? "تم تفعيل حظر الإعلانات ✅" : "تم تعطيل حظر الإعلانات ❌",
                            Toast.LENGTH_SHORT).show();
                        if (isAdBlockActive) injectDrMuhammedEnhancements();
                        break;
                    case 1:
                        cyclePlaybackSpeed();
                        break;
                    case 2:
                        skipIntro();
                        break;
                    case 3:
                        if (webView != null) webView.reload();
                        break;
                    case 4:
                        if (webView != null) {
                            webView.clearCache(true);
                            CookieManager.getInstance().removeAllCookies(null);
                            webView.reload();
                            Toast.makeText(MainActivity.this, "تم مسح الذاكرة وإعادة التحميل 🧹", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case 5:
                        showAboutDialog();
                        break;
                }
                hideSystemUI();
            }
        });

        builder.setNegativeButton("إغلاق", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void cyclePlaybackSpeed() {
        if (currentPlaybackSpeed == 1.0f) currentPlaybackSpeed = 1.25f;
        else if (currentPlaybackSpeed == 1.25f) currentPlaybackSpeed = 1.5f;
        else if (currentPlaybackSpeed == 1.5f) currentPlaybackSpeed = 2.0f;
        else currentPlaybackSpeed = 1.0f;

        if (webView != null) {
            webView.evaluateJavascript(
                "(function() { var v = document.querySelector('video'); if (v) { v.playbackRate = " + currentPlaybackSpeed + "; } })()",
                null
            );
        }
        Toast.makeText(this, "⚡ سرعة الفيديو: " + currentPlaybackSpeed + "x", Toast.LENGTH_SHORT).show();
    }

    private void skipIntro() {
        if (webView != null) {
            webView.evaluateJavascript(
                "(function() { var v = document.querySelector('video'); if (v) { v.currentTime += 85; } })()",
                null
            );
            Toast.makeText(this, "⏩ تم تخطي 85 ثانية (المقدمة)", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
            .setTitle("حول تطبيق CineJoy")
            .setMessage("✨ CineJoy Pro Edition\n" +
                        "👨‍💻 تم التطوير والتخصيص بواسطة: Dr. Muhammed\n\n" +
                        "الميزات المدمجة:\n" +
                        "• دعم رسمي للشاشات الذكية (Android TV) والريموت كنترول\n" +
                        "• حماية مدمجة ضد الإعلانات والصفحات المنبثقة\n" +
                        "• التحكم بسرعة الفيديو وتخطي المقدمات\n" +
                        "• تسريع عتادي ودعم كامل لملء الشاشة")
            .setPositiveButton("حسناً", null)
            .show();
    }

    private void injectDrMuhammedEnhancements() {
        if (!isAdBlockActive || webView == null) return;
        // Injects anti-popup & ad-remover javascript
        String js = "(function() {" +
            "  window.open = function() { return null; };" +
            "  var adSelectors = ['iframe[src*=\"ad\"]', 'div[class*=\"banner\"]', 'div[id*=\"pop\"]', 'a[href*=\"llvpn\"]'];" +
            "  adSelectors.forEach(function(s) {" +
            "    document.querySelectorAll(s).forEach(function(el) { el.remove(); });" +
            "  });" +
            "  localStorage.setItem('addies', 'false');" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
            if (webView != null) webView.requestFocus();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TV Remote & Controls
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
                showDrMuhammedMenu();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (webView != null) {
                    webView.evaluateJavascript(
                        "(function() { var v = document.querySelector('video'); if (v) { if (v.paused) v.play(); else v.pause(); } })()",
                        null
                    );
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (webView != null) {
                    webView.evaluateJavascript(
                        "(function() { var v = document.querySelector('video'); if (v) v.currentTime += 10; })()",
                        null
                    );
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (webView != null) {
                    webView.evaluateJavascript(
                        "(function() { var v = document.querySelector('video'); if (v) v.currentTime -= 10; })()",
                        null
                    );
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                if (customView != null) {
                    if (webView != null && webView.getWebChromeClient() != null) {
                        ((WebChromeClient) webView.getWebChromeClient()).onHideCustomView();
                    }
                    return true;
                }
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback != null) {
                Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.requestFocus();
        }
        hideSystemUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
