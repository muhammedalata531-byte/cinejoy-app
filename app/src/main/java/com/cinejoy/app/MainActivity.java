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
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
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

import java.io.ByteArrayInputStream;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private FrameLayout customViewContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private RelativeLayout splashLayout;
    private TextView drBadge;

    private static final String TARGET_URL = "https://cinejoy.pk/";
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST_CODE = 100;

    private boolean isAdBlockActive = true;
    private boolean isTvBoostActive = true;
    private boolean isMouseModeActive = false;
    private float currentPlaybackSpeed = 1.0f;

    // Ad & tracker domains blocked at network layer to save TV RAM & CPU
    private static final String[] BLOCKED_PATTERNS = new String[]{
        "llvpn.com", "a.wing.st", "doubleclick.net", "google-analytics.com",
        "googlesyndication.com", "popads.net", "adsterra.com", "propellerads.com",
        "monetag.com", "yandex.ru", "clickadu.com", "histats.com", "exoclick.com",
        "trafficfactory.biz", "juicyads.com", "adnxs.com", "adform.net",
        "/tag.min.js", "popunder", "/ads/", "adservice", "partnerads"
    };

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

        // Optimized WebView for Android TV
        webView = new WebView(this);
        RelativeLayout.LayoutParams wvParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(wvParams);
        webView.setBackgroundColor(Color.parseColor("#050505"));
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

        // Dr. Muhammed Quick Badge (auto-fades on TV after launch)
        drBadge = createDrMuhammedBadge();

        // Splash Screen Overlay by Dr. Muhammed
        splashLayout = createSplashScreen();

        rootLayout.addView(webView);
        rootLayout.addView(progressBar);
        rootLayout.addView(customViewContainer);
        rootLayout.addView(drBadge);
        rootLayout.addView(splashLayout);
        setContentView(rootLayout);

        // Configure WebSettings for high performance & TV screens
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
        settings.setSupportMultipleWindows(false); // Block popups spawning extra windows
        settings.setJavaScriptCanOpenWindowsAutomatically(false); // Disallow popup windows
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

        // WebViewClient with Network-level AdBlocker & Spatial Navigator Injection
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Block external ad schemes
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
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (isAdBlockActive && request != null && request.getUrl() != null) {
                    String url = request.getUrl().toString();
                    if (isAdOrTracker(url)) {
                        // Return empty stream immediately to stop CPU and network load
                        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(15);
                // Pre-inject anti-ad flag before webpage scripts execute
                view.evaluateJavascript(
                    "(function() { try { localStorage.setItem('addies', 'false'); } catch(e){} window.open = function() { return null; }; })();",
                    null
                );
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                webView.requestFocus();

                // Inject TV Remote Navigation Engine & Performance Boost
                injectTvNavigationAndEnhancements();

                // Dismiss splash screen smoothly
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
        Toast.makeText(this, "🎬 CineJoy TV Edition — By Dr. Muhammed", Toast.LENGTH_SHORT).show();

        // Safety timeout to hide splash after 3 seconds max
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                hideSplashScreen();
            }
        }, 3000);

        // Auto-fade Dr. Muhammed badge after 6 seconds so it doesn't obstruct movies on TV
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (drBadge != null && drBadge.getVisibility() == View.VISIBLE) {
                    drBadge.animate().alpha(0f).setDuration(600).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            drBadge.setVisibility(View.GONE);
                        }
                    });
                }
            }
        }, 6000);
    }

    private boolean isAdOrTracker(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        for (String pattern : BLOCKED_PATTERNS) {
            if (lower.contains(pattern)) return true;
        }
        return false;
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
        authorText.setText("⚡ TV Edition — Developed by Dr. Muhammed");
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
        badge.setText("👨‍💻 Dr. Muhammed (Menu)");
        badge.setTextSize(11);
        badge.setTextColor(Color.parseColor("#95FF50"));
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(24, 10, 24, 10);
        badge.setFocusable(false); // Keep focus on web items for remote navigation

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
        builder.setTitle("🎬 CineJoy TV Hub — By Dr. Muhammed");

        String[] options = {
            "🎯 نمط مؤشر الماوس (Virtual Mouse): " + (isMouseModeActive ? "مفعّل 🟢" : "معطّل ⚪"),
            "🛡️ حظر الإعلانات والبوب أب: " + (isAdBlockActive ? "مفعّل ✅" : "معطّل ❌"),
            "⚡ تسريع أداء الشاشة (TV Hardware Booster): " + (isTvBoostActive ? "مفعّل ✅" : "معطّل ❌"),
            "⚡ سرعة التشغيل: " + currentPlaybackSpeed + "x",
            "⏩ تخطي المقدمة (Skip 85s)",
            "🔄 إعادة تحميل الصفحة",
            "🧹 مسح الذاكرة المؤقتة (Clear Cache)",
            "ℹ️ حول التطبيق والمطور",
            "🚪 خروج من التطبيق"
        };

        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        toggleMouseMode();
                        break;
                    case 1:
                        isAdBlockActive = !isAdBlockActive;
                        Toast.makeText(MainActivity.this,
                            isAdBlockActive ? "تم تفعيل حظر الإعلانات ✅" : "تم تعطيل حظر الإعلانات ❌",
                            Toast.LENGTH_SHORT).show();
                        if (webView != null) webView.reload();
                        break;
                    case 2:
                        isTvBoostActive = !isTvBoostActive;
                        Toast.makeText(MainActivity.this,
                            isTvBoostActive ? "تم تفعيل تسريع الشاشة ✅" : "تم تعطيل تسريع الشاشة ❌",
                            Toast.LENGTH_SHORT).show();
                        injectTvNavigationAndEnhancements();
                        break;
                    case 3:
                        cyclePlaybackSpeed();
                        break;
                    case 4:
                        skipIntro();
                        break;
                    case 5:
                        if (webView != null) webView.reload();
                        break;
                    case 6:
                        if (webView != null) {
                            webView.clearCache(true);
                            CookieManager.getInstance().removeAllCookies(null);
                            webView.reload();
                            Toast.makeText(MainActivity.this, "تم مسح الذاكرة وإعادة التحميل 🧹", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case 7:
                        showAboutDialog();
                        break;
                    case 8:
                        finish();
                        break;
                }
                hideSystemUI();
            }
        });

        builder.setNegativeButton("إغلاق", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                hideSystemUI();
                if (webView != null) webView.requestFocus();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void toggleMouseMode() {
        isMouseModeActive = !isMouseModeActive;
        if (webView != null) {
            webView.evaluateJavascript("window.toggleVirtualMouse && window.toggleVirtualMouse(" + isMouseModeActive + ");", null);
        }
        Toast.makeText(this,
            isMouseModeActive ? "🎯 تم تفعيل مؤشر الماوس الافتراضي (حرك بالأسهم واضغط OK)" : "📱 تم العودة إلى نمط التنقل العادي بالريموت",
            Toast.LENGTH_SHORT).show();
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
            .setTitle("حول تطبيق CineJoy TV")
            .setMessage("✨ CineJoy Pro Edition — Smart TV & Android\n" +
                        "👨‍💻 تم التطوير والتخصيص بواسطة: Dr. Muhammed\n\n" +
                        "الميزات المدمجة:\n" +
                        "• محرك تنقل متقدم بالريموت كنترول (D-Pad Spatial Navigation)\n" +
                        "• مؤشر ماوس افتراضي (Virtual Mouse) للتحكم بالمشغلات الدقيقة\n" +
                        "• تمييز بصري نيون ساطع للعنصر المحدد على الشاشة الكبيرة\n" +
                        "• حظر فوري للإعلانات والصفحات المنبثقة على مستوى الشبكة\n" +
                        "• تسريع أداء الشاشات الضعيفة بإلغاء المؤثرات الثقيلة\n" +
                        "• التحكم الكامل بالفيديو (تشغيل، إيقاف، تقديم، تأخير) عبر الريموت")
            .setPositiveButton("حسناً", null)
            .show();
    }

    // Injects Spatial Navigation Engine, High-Contrast TV Focus, Performance Boost & Virtual Mouse
    private void injectTvNavigationAndEnhancements() {
        if (webView == null) return;

        String tvEngineJs = "(function() {" +
            "  if (window.__cinejoyTvInjected) return;" +
            "  window.__cinejoyTvInjected = true;" +
            "" +
            "  /* 1. TV Focus & GPU Performance CSS */" +
            "  var style = document.createElement('style');" +
            "  style.id = 'cinejoy-tv-styles';" +
            "  style.textContent = `" +
            "    :focus, .tv-focused {" +
            "      outline: 4px solid #95FF50 !important;" +
            "      outline-offset: 3px !important;" +
            "      border-radius: 8px !important;" +
            "      box-shadow: 0 0 22px rgba(149, 255, 80, 0.9) !important;" +
            "      transform: scale(1.05) !important;" +
            "      transition: transform 0.12s ease-out, outline 0.12s ease-out !important;" +
            "      z-index: 999999 !important;" +
            "    }" +
            "    * {" +
            "      backdrop-filter: none !important;" +
            "      -webkit-backdrop-filter: none !important;" +
            "      text-shadow: none !important;" +
            "    }" +
            "    #cinejoy-virtual-cursor {" +
            "      position: fixed;" +
            "      width: 26px;" +
            "      height: 26px;" +
            "      border-radius: 50%;" +
            "      background: rgba(149, 255, 80, 0.9);" +
            "      border: 3px solid #ffffff;" +
            "      box-shadow: 0 0 16px #95FF50, 0 0 5px #000000;" +
            "      pointer-events: none;" +
            "      z-index: 2147483647;" +
            "      transform: translate(-50%, -50%);" +
            "      display: none;" +
            "      transition: left 0.05s linear, top 0.05s linear;" +
            "    }" +
            "  `;" +
            "  (document.head || document.documentElement).appendChild(style);" +
            "" +
            "  /* 2. Block Popups & Ads */" +
            "  window.open = function() { return null; };" +
            "  try { localStorage.setItem('addies', 'false'); } catch(e){}" +
            "" +
            "  /* 3. Automatic Tabindex Scanner for Movie Cards */" +
            "  function makeFocusable() {" +
            "    var selectors = ['a[href]', 'button', 'input', 'select', '[role=\"button\"]', '[onclick]', 'div[class*=\"card\"]', 'div[class*=\"item\"]', 'div[class*=\"poster\"]', 'div[class*=\"movie\"]', 'div[class*=\"episode\"]'];" +
            "    var elements = document.querySelectorAll(selectors.join(', '));" +
            "    for (var i = 0; i < elements.length; i++) {" +
            "      var el = elements[i];" +
            "      if (!el.hasAttribute('tabindex') && el.offsetParent !== null) {" +
            "        el.setAttribute('tabindex', '0');" +
            "      }" +
            "    }" +
            "  }" +
            "  makeFocusable();" +
            "  setInterval(makeFocusable, 1800);" +
            "" +
            "  /* 4. Virtual Mouse State */" +
            "  window.__cinejoyMouseMode = false;" +
            "  var cursorX = window.innerWidth / 2;" +
            "  var cursorY = window.innerHeight / 2;" +
            "  var cursorEl = document.createElement('div');" +
            "  cursorEl.id = 'cinejoy-virtual-cursor';" +
            "  document.body.appendChild(cursorEl);" +
            "" +
            "  window.toggleVirtualMouse = function(enable) {" +
            "    if (typeof enable === 'boolean') {" +
            "      window.__cinejoyMouseMode = enable;" +
            "    } else {" +
            "      window.__cinejoyMouseMode = !window.__cinejoyMouseMode;" +
            "    }" +
            "    cursorEl.style.display = window.__cinejoyMouseMode ? 'block' : 'none';" +
            "    if (window.__cinejoyMouseMode) {" +
            "      cursorEl.style.left = cursorX + 'px';" +
            "      cursorEl.style.top = cursorY + 'px';" +
            "    }" +
            "    return window.__cinejoyMouseMode;" +
            "  };" +
            "" +
            "  /* 5. Spatial Navigation Engine */" +
            "  function getFocusable() {" +
            "    var all = document.querySelectorAll('[tabindex=\"0\"], a, button, input');" +
            "    var vis = [];" +
            "    for (var i = 0; i < all.length; i++) {" +
            "      var r = all[i].getBoundingClientRect();" +
            "      if (r.width > 0 && r.height > 0 && all[i].offsetParent !== null) vis.push(all[i]);" +
            "    }" +
            "    return vis;" +
            "  }" +
            "" +
            "  function moveFocus(dir) {" +
            "    var active = document.activeElement;" +
            "    var elements = getFocusable();" +
            "    if (!elements.length) return;" +
            "    if (!active || active === document.body || !active.getBoundingClientRect) {" +
            "      elements[0].focus();" +
            "      elements[0].scrollIntoView({ behavior: 'smooth', block: 'center' });" +
            "      return;" +
            "    }" +
            "    var cur = active.getBoundingClientRect();" +
            "    var cx = cur.left + cur.width / 2;" +
            "    var cy = cur.top + cur.height / 2;" +
            "    var best = null;" +
            "    var minD = Infinity;" +
            "    for (var i = 0; i < elements.length; i++) {" +
            "      var el = elements[i];" +
            "      if (el === active) continue;" +
            "      var r = el.getBoundingClientRect();" +
            "      var ex = r.left + r.width / 2;" +
            "      var ey = r.top + r.height / 2;" +
            "      var dx = ex - cx;" +
            "      var dy = ey - cy;" +
            "      var valid = (dir === 'down' && dy > 12) || (dir === 'up' && dy < -12) || (dir === 'right' && dx > 12) || (dir === 'left' && dx < -12);" +
            "      if (valid) {" +
            "        var d = (dir === 'up' || dir === 'down') ? Math.abs(dy) + Math.abs(dx) * 2.5 : Math.abs(dx) + Math.abs(dy) * 2.5;" +
            "        if (d < minD) { minD = d; best = el; }" +
            "      }" +
            "    }" +
            "    if (best) {" +
            "      best.focus();" +
            "      best.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });" +
            "    } else {" +
            "      if (dir === 'down') window.scrollBy({ top: 320, behavior: 'smooth' });" +
            "      else if (dir === 'up') window.scrollBy({ top: -320, behavior: 'smooth' });" +
            "      else if (dir === 'right') window.scrollBy({ left: 320, behavior: 'smooth' });" +
            "      else if (dir === 'left') window.scrollBy({ left: -320, behavior: 'smooth' });" +
            "    }" +
            "  }" +
            "" +
            "  window.addEventListener('keydown', function(e) {" +
            "    if (window.__cinejoyMouseMode) {" +
            "      var step = 45;" +
            "      if (e.key === 'ArrowUp' || e.keyCode === 38) { cursorY = Math.max(10, cursorY - step); e.preventDefault(); }" +
            "      else if (e.key === 'ArrowDown' || e.keyCode === 40) { cursorY = Math.min(window.innerHeight - 10, cursorY + step); e.preventDefault(); }" +
            "      else if (e.key === 'ArrowLeft' || e.keyCode === 37) { cursorX = Math.max(10, cursorX - step); e.preventDefault(); }" +
            "      else if (e.key === 'ArrowRight' || e.keyCode === 39) { cursorX = Math.min(window.innerWidth - 10, cursorX + step); e.preventDefault(); }" +
            "      else if (e.key === 'Enter' || e.keyCode === 13) {" +
            "        cursorEl.style.display = 'none';" +
            "        var t = document.elementFromPoint(cursorX, cursorY);" +
            "        cursorEl.style.display = 'block';" +
            "        if (t) { t.click(); if (typeof t.focus === 'function') t.focus(); }" +
            "        e.preventDefault();" +
            "        return;" +
            "      }" +
            "      cursorEl.style.left = cursorX + 'px';" +
            "      cursorEl.style.top = cursorY + 'px';" +
            "      if (cursorY < 80) window.scrollBy({ top: -250, behavior: 'smooth' });" +
            "      if (cursorY > window.innerHeight - 80) window.scrollBy({ top: 250, behavior: 'smooth' });" +
            "      return;" +
            "    }" +
            "" +
            "    var video = document.querySelector('video');" +
            "    var isVideoActive = video && (document.fullscreenElement || video.offsetWidth > 450);" +
            "    if (isVideoActive) {" +
            "      if (e.key === 'Enter' || e.keyCode === 13) {" +
            "        if (video.paused) video.play(); else video.pause();" +
            "        e.preventDefault(); return;" +
            "      } else if (e.key === 'ArrowRight' || e.keyCode === 39) {" +
            "        video.currentTime += 10; e.preventDefault(); return;" +
            "      } else if (e.key === 'ArrowLeft' || e.keyCode === 37) {" +
            "        video.currentTime -= 10; e.preventDefault(); return;" +
            "      }" +
            "    }" +
            "" +
            "    if (e.key === 'ArrowDown' || e.keyCode === 40) { moveFocus('down'); e.preventDefault(); }" +
            "    else if (e.key === 'ArrowUp' || e.keyCode === 38) { moveFocus('up'); e.preventDefault(); }" +
            "    else if (e.key === 'ArrowRight' || e.keyCode === 39) { moveFocus('right'); e.preventDefault(); }" +
            "    else if (e.key === 'ArrowLeft' || e.keyCode === 37) { moveFocus('left'); e.preventDefault(); }" +
            "    else if (e.key === 'Enter' || e.keyCode === 13) {" +
            "      if (document.activeElement && typeof document.activeElement.click === 'function') {" +
            "        document.activeElement.click();" +
            "      }" +
            "    }" +
            "  }, true);" +
            "})();";

        webView.evaluateJavascript(tvEngineJs, null);
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
        // Android TV Remote Controller Key Handling
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_SETTINGS:
            case KeyEvent.KEYCODE_PROG_RED:
            case KeyEvent.KEYCODE_BUTTON_Y:
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
                // 1. Exit fullscreen video first
                if (customView != null) {
                    if (webView != null && webView.getWebChromeClient() != null) {
                        ((WebChromeClient) webView.getWebChromeClient()).onHideCustomView();
                    }
                    return true;
                }

                // 2. Disable virtual mouse mode if active
                if (isMouseModeActive) {
                    toggleMouseMode();
                    return true;
                }

                // 3. Web navigation back
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    return true;
                }

                // 4. Confirm before exit on TV to prevent accidental quitting
                showExitConfirmDialog();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showExitConfirmDialog() {
        new AlertDialog.Builder(this)
            .setTitle("تطبيق CineJoy")
            .setMessage("هل تريد حقاً الخروج من التطبيق؟")
            .setPositiveButton("نعم (خروج)", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            })
            .setNegativeButton("إلغاء", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    hideSystemUI();
                    if (webView != null) webView.requestFocus();
                }
            })
            .show();
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
