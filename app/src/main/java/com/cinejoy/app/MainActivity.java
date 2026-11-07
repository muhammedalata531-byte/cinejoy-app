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
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
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
    private boolean isMouseModeActive = false;
    private float currentPlaybackSpeed = 1.0f;

    // Native Virtual Mouse Engine variables
    private FrameLayout nativeCursorView;
    private float cursorX = 600f;
    private float cursorY = 400f;
    private int screenWidth = 1920;
    private int screenHeight = 1080;

    // D-Pad long press tracking for DPAD_CENTER / ENTER
    private boolean isDpadCenterLongPressing = false;
    private final Handler dpadHandler = new Handler(Looper.getMainLooper());
    private Runnable dpadLongPressRunnable;

    // Blocked ad domains to save CPU and network bandwidth
    private static final String[] BLOCKED_PATTERNS = new String[]{
        "doubleclick.net", "google-analytics.com",
        "googlesyndication.com", "popads.net", "adsterra.com", "propellerads.com",
        "monetag.com", "clickadu.com", "histats.com", "exoclick.com",
        "trafficfactory.biz", "juicyads.com", "adnxs.com", "adform.net",
        "/tag.min.js", "popunder", "adservice"
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on during playback
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Immersive sticky fullscreen mode
        hideSystemUI();

        // Get actual screen resolution
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        screenWidth = Math.max(800, dm.widthPixels);
        screenHeight = Math.max(600, dm.heightPixels);
        cursorX = screenWidth / 2f;
        cursorY = screenHeight / 2f;

        // Main Root layout
        RelativeLayout rootLayout = new RelativeLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#050505"));

        // Optimized WebView for Android TV with Hardware Acceleration
        webView = new WebView(this);
        RelativeLayout.LayoutParams wvParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(wvParams);
        webView.setBackgroundColor(Color.parseColor("#050505"));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null); // 60 FPS Hardware Acceleration
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();

        // Progress bar (Neon Lime)
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#95FF50")));
        progressBar.setBackgroundColor(Color.TRANSPARENT);
        RelativeLayout.LayoutParams pbParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, 8);
        pbParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        progressBar.setLayoutParams(pbParams);

        // Fullscreen Video Container
        customViewContainer = new FrameLayout(this);
        customViewContainer.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        customViewContainer.setBackgroundColor(Color.BLACK);
        customViewContainer.setVisibility(View.GONE);

        // Floating Control Pill
        drBadge = createDrMuhammedBadge();

        // Native Virtual Mouse Pointer Overlay
        nativeCursorView = createNativeCursorView();

        // Splash Screen
        splashLayout = createSplashScreen();

        rootLayout.addView(webView);
        rootLayout.addView(progressBar);
        rootLayout.addView(customViewContainer);
        rootLayout.addView(drBadge);
        rootLayout.addView(nativeCursorView);
        rootLayout.addView(splashLayout);
        setContentView(rootLayout);

        // Configure WebSettings for High Performance on Android TV
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
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setTextZoom(100);
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);

        // Remove '; wv' from User-Agent for standard browser compatibility
        String defaultUa = settings.getUserAgentString();
        if (defaultUa != null) {
            settings.setUserAgentString(defaultUa.replace("; wv", ""));
        }

        // Cookie management
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
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
                        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(15);
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

                // Inject lightweight TV styles & enhancements (NO LAG)
                injectLightweightTvEnhancements();

                hideSplashScreen();
                updateBadgePill();
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

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(TARGET_URL);
        }

        Toast.makeText(this, "🎬 CineJoy TV Edition — By Dr. Muhammed", Toast.LENGTH_SHORT).show();

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                hideSplashScreen();
            }
        }, 3000);
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
    // NATIVE VIRTUAL MOUSE ENGINE (SMOOTH & FAST)
    // ==========================================

    private FrameLayout createNativeCursorView() {
        FrameLayout container = new FrameLayout(this);
        int sizeDp = 28;
        int sizePx = (int) (sizeDp * getResources().getDisplayMetrics().density);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(sizePx, sizePx);
        container.setLayoutParams(params);
        container.setElevation(100f);

        // Outer Neon Ring
        View outerRing = new View(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.parseColor("#6695FF50"));
        ring.setStroke(3, Color.parseColor("#95FF50"));
        outerRing.setBackground(ring);
        FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(sizePx, sizePx);
        outerRing.setLayoutParams(ringParams);

        // Center Target Dot
        View centerDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(Color.WHITE);
        int dotSizePx = (int) (6 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dotSizePx, dotSizePx);
        dotParams.gravity = Gravity.CENTER;
        centerDot.setBackground(dot);
        centerDot.setLayoutParams(dotParams);

        container.addView(outerRing);
        container.addView(centerDot);
        container.setVisibility(View.GONE);
        return container;
    }

    private void updateCursorPosition() {
        if (nativeCursorView == null) return;
        int size = nativeCursorView.getWidth();
        if (size == 0) size = (int) (28 * getResources().getDisplayMetrics().density);
        nativeCursorView.setX(cursorX - size / 2f);
        nativeCursorView.setY(cursorY - size / 2f);

        // Smooth boundary scrolling without heavy layout thrashing
        if (webView != null) {
            if (cursorY < 100) {
                webView.scrollBy(0, -60);
            } else if (cursorY > screenHeight - 100) {
                webView.scrollBy(0, 60);
            }
        }
    }

    /**
     * Dispatches native Android MotionEvents (ACTION_DOWN + ACTION_UP) directly to the WebView surface.
     * Guaranteed to work on ANY button, iframe, video player control, or server list.
     */
    private void dispatchNativeClick(float x, float y) {
        if (webView == null) return;
        long downTime = SystemClock.uptimeMillis();

        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[1];
        props[0] = new MotionEvent.PointerProperties();
        props[0].id = 0;
        props[0].toolType = MotionEvent.TOOL_TYPE_FINGER;

        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
        coords[0] = new MotionEvent.PointerCoords();
        coords[0].x = x;
        coords[0].y = y;
        coords[0].pressure = 1.0f;
        coords[0].size = 1.0f;

        MotionEvent downEvent = MotionEvent.obtain(
            downTime, downTime,
            MotionEvent.ACTION_DOWN,
            1, props, coords,
            0, 0, 1f, 1f, 0, 0,
            0, 0
        );

        MotionEvent upEvent = MotionEvent.obtain(
            downTime, downTime + 80,
            MotionEvent.ACTION_UP,
            1, props, coords,
            0, 0, 1f, 1f, 0, 0,
            0, 0
        );

        webView.dispatchTouchEvent(downEvent);
        webView.dispatchTouchEvent(upEvent);
        downEvent.recycle();
        upEvent.recycle();

        // Cursor visual click pulse
        if (nativeCursorView != null) {
            nativeCursorView.animate()
                .scaleX(0.7f)
                .scaleY(0.7f)
                .setDuration(60)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (nativeCursorView != null) {
                            nativeCursorView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(60).start();
                        }
                    }
                })
                .start();
        }
    }

    private void toggleNativeMouseMode() {
        isMouseModeActive = !isMouseModeActive;
        if (nativeCursorView != null) {
            if (isMouseModeActive) {
                cursorX = screenWidth / 2f;
                cursorY = screenHeight / 2f;
                nativeCursorView.setVisibility(View.VISIBLE);
                nativeCursorView.setAlpha(1.0f);
                nativeCursorView.setScaleX(1.0f);
                nativeCursorView.setScaleY(1.0f);
                updateCursorPosition();
            } else {
                nativeCursorView.setVisibility(View.GONE);
            }
        }

        updateBadgePill();

        Toast.makeText(
            this,
            isMouseModeActive
                ? "🎯 نمط الماوس مفعّل — حرّك بالأسهم واضغط OK للنقر\nاضغط BACK أو OK مطولاً للرجوع"
                : "📱 نمط الأسهم مفعّل — اضغط مطولاً على OK لتفعيل الماوس",
            Toast.LENGTH_SHORT
        ).show();
    }

    private void updateBadgePill() {
        if (drBadge == null) return;
        drBadge.animate().cancel();
        drBadge.setAlpha(1.0f);
        drBadge.setVisibility(View.VISIBLE);

        if (isMouseModeActive) {
            drBadge.setText("🎯 ماوس | BACK للخروج");
            drBadge.setTextColor(Color.parseColor("#050505"));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#95FF50"));
            bg.setCornerRadius(30);
            drBadge.setBackground(bg);
            drBadge.setAlpha(1.0f);
        } else {
            drBadge.setText("📱 ريموت | ☰ القائمة");
            drBadge.setTextColor(Color.parseColor("#95FF50"));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#CC111111"));
            bg.setCornerRadius(30);
            bg.setStroke(2, Color.parseColor("#95FF50"));
            drBadge.setBackground(bg);
            drBadge.animate().alpha(0.65f).setDuration(500).setStartDelay(3000).start();
        }
    }

    // ==========================================
    // KEY EVENT HANDLING FOR ANDROID TV REMOTES
    // ==========================================

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        // 1. Long-press OK (600ms) to toggle Mouse Mode
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {

            if (event.getRepeatCount() == 0) {
                isDpadCenterLongPressing = false;
                dpadLongPressRunnable = new Runnable() {
                    @Override
                    public void run() {
                        isDpadCenterLongPressing = true;
                        toggleNativeMouseMode();
                    }
                };
                dpadHandler.postDelayed(dpadLongPressRunnable, 600);
            }
            return true;
        }

        // 2. When Mouse Mode is active: Arrow keys ONLY move cursor, NEVER interfere with web focus or video
        if (isMouseModeActive) {
            int repeat = event.getRepeatCount();
            float step = Math.min(60f, 16f + repeat * 4f);

            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    cursorY = Math.max(8f, cursorY - step);
                    updateCursorPosition();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    cursorY = Math.min((float) screenHeight - 8f, cursorY + step);
                    updateCursorPosition();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    cursorX = Math.max(8f, cursorX - step);
                    updateCursorPosition();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    cursorX = Math.min((float) screenWidth - 8f, cursorX + step);
                    updateCursorPosition();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    toggleNativeMouseMode(); // Back exits mouse mode first
                    return true;
            }
            return true;
        }

        // 3. Normal TV mode: Do NOT intercept directional arrows!
        // Pass them directly to the WebView so the video player or web page handles them natively!
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return super.onKeyDown(keyCode, event);
        }

        // 4. Menu & Media Keys
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
                showExitConfirmDialog();
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {

            // Cancel the long-press timer
            if (dpadLongPressRunnable != null) {
                dpadHandler.removeCallbacks(dpadLongPressRunnable);
                dpadLongPressRunnable = null;
            }

            // If long-press just fired, consume and don't click
            if (isDpadCenterLongPressing) {
                isDpadCenterLongPressing = false;
                return true;
            }

            // Click action:
            if (isMouseModeActive) {
                dispatchNativeClick(cursorX, cursorY);
            } else {
                // In D-Pad mode, click activeElement cleanly without crashing TouchEvent
                if (webView != null) {
                    webView.evaluateJavascript(
                        "(function() {" +
                        "  var el = document.activeElement;" +
                        "  if (el && el !== document.body) {" +
                        "    if (typeof el.click === 'function') el.click();" +
                        "    else el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));" +
                        "  }" +
                        "})();",
                        null
                    );
                }
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    // ==========================================
    // UI COMPONENTS & DR. MUHAMMED HUB
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

        ImageView logoView = new ImageView(this);
        logoView.setImageResource(R.drawable.ic_launcher);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(220, 220);
        logoParams.bottomMargin = 24;
        logoView.setLayoutParams(logoParams);

        TextView titleText = new TextView(this);
        titleText.setText("CINEJOY");
        titleText.setTextSize(32);
        titleText.setTextColor(Color.parseColor("#95FF50"));
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        titleText.setGravity(Gravity.CENTER);

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
                .setDuration(300)
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
        badge.setText("📱 ريموت | ☰ القائمة");
        badge.setTextSize(11);
        badge.setTextColor(Color.parseColor("#95FF50"));
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(26, 12, 26, 12);
        badge.setFocusable(false);

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

        badge.animate().alpha(0.65f).setDuration(500).setStartDelay(3000).start();
        return badge;
    }

    private void showDrMuhammedMenu() {
        if (drBadge != null) {
            drBadge.animate().cancel();
            drBadge.setAlpha(1.0f);
            drBadge.setVisibility(View.VISIBLE);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎬 CineJoy TV Hub — By Dr. Muhammed");

        String[] options = {
            "🎯 مؤشر الماوس الافتراضي: " + (isMouseModeActive ? "مفعّل 🟢 (OK مطولاً للتبديل)" : "معطّل ⚪ (OK مطولاً للتفعيل)"),
            "🛡️ حظر الإعلانات والبوب أب: " + (isAdBlockActive ? "مفعّل ✅" : "معطّل ❌"),
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
                        toggleNativeMouseMode();
                        break;
                    case 1:
                        isAdBlockActive = !isAdBlockActive;
                        Toast.makeText(MainActivity.this,
                            isAdBlockActive ? "تم تفعيل حظر الإعلانات ✅" : "تم تعطيل حظر الإعلانات ❌",
                            Toast.LENGTH_SHORT).show();
                        if (webView != null) webView.reload();
                        break;
                    case 2:
                        cyclePlaybackSpeed();
                        break;
                    case 3:
                        skipIntro();
                        break;
                    case 4:
                        if (webView != null) webView.reload();
                        break;
                    case 5:
                        if (webView != null) {
                            webView.clearCache(true);
                            CookieManager.getInstance().removeAllCookies(null);
                            webView.reload();
                            Toast.makeText(MainActivity.this, "تم مسح الذاكرة وإعادة التحميل 🧹", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case 6:
                        showAboutDialog();
                        break;
                    case 7:
                        finish();
                        break;
                }
                hideSystemUI();
                updateBadgePill();
            }
        });

        builder.setNegativeButton("إغلاق", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                hideSystemUI();
                if (webView != null) webView.requestFocus();
                updateBadgePill();
            }
        });
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
            .setTitle("حول تطبيق CineJoy TV v1.3")
            .setMessage("✨ CineJoy Pro Edition — Smart TV & Android\n" +
                        "👨‍💻 تم التطوير والتخصيص بواسطة: Dr. Muhammed\n\n" +
                        "• أداء فائق السرعة وخفيف 100% بدون أي تعليق\n" +
                        "• مؤشر ماوس أصلي بالكامل للنقر داخل كافة المشغلات والإطارات\n" +
                        "• تفعيل الماوس بضغطة مطولة على OK، والخروج بزر BACK\n" +
                        "• تحكم سلس بتقديم وتأخير الفيديو بدون أي تعارض مع الأسهم\n" +
                        "• حظر فوري للإعلانات والصفحات المنبثقة")
            .setPositiveButton("حسناً", null)
            .show();
    }

    /**
     * ULTRA-LIGHTWEIGHT TV ENHANCEMENTS:
     * - NO MutationObservers!
     * - NO heavy setInterval loops!
     * - NO CPU-hogging drop shadows!
     * - Only clean, crisp focus outline and popup blocking.
     */
    private void injectLightweightTvEnhancements() {
        if (webView == null) return;

        String tvEngineJs = "(function() {" +
            "  if (window.__cinejoyTvInjected) return;" +
            "  window.__cinejoyTvInjected = true;" +

            // 1. Crisp, lightweight focus outline for Android TV (zero GPU lag)
            "  var style = document.createElement('style');" +
            "  style.id = 'cinejoy-tv-styles';" +
            "  style.textContent = `" +
            "    :focus, .tv-focused {" +
            "      outline: 3px solid #95FF50 !important;" +
            "      outline-offset: 2px !important;" +
            "      border-radius: 6px !important;" +
            "    }" +
            "    * {" +
            "      -webkit-tap-highlight-color: rgba(149, 255, 80, 0.3) !important;" +
            "    }" +
            "  `;" +
            "  (document.head || document.documentElement).appendChild(style);" +

            // 2. Block Popups & Ads
            "  window.open = function() { return null; };" +
            "  try { localStorage.setItem('addies', 'false'); } catch(e){}" +

            // 3. Make interactive elements focusable on initial page load (one-time pass, NO loops)
            "  setTimeout(function() {" +
            "    var els = document.querySelectorAll('a, button, input, select, [role=\"button\"], [onclick]');" +
            "    for (var i = 0; i < els.length; i++) {" +
            "      if (!els[i].hasAttribute('tabindex')) els[i].setAttribute('tabindex', '0');" +
            "    }" +
            "  }, 1000);" +

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
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
            if (webView != null) webView.requestFocus();
        }
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
        if (dpadLongPressRunnable != null) {
            dpadHandler.removeCallbacks(dpadLongPressRunnable);
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}

