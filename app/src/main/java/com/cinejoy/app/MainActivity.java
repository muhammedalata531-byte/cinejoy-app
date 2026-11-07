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
    private float cursorX = 960f;
    private float cursorY = 540f;
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

        // Optimized WebView for Android TV (Standard hardware acceleration via Window)
        webView = new WebView(this);
        RelativeLayout.LayoutParams wvParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(wvParams);
        webView.setBackgroundColor(Color.parseColor("#050505"));
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
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                webView.requestFocus();

                // Inject TV subtitle & focus enhancements
                injectTvSubtitleAndControlEnhancements();

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
                if (nativeCursorView != null) {
                    nativeCursorView.bringToFront();
                }
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

        Toast.makeText(this, "ðŸŽ¬ CineJoy TV Edition â€” By Dr. Muhammed", Toast.LENGTH_SHORT).show();

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
        int sizeDp = 30;
        int sizePx = (int) (sizeDp * getResources().getDisplayMetrics().density);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(sizePx, sizePx);
        container.setLayoutParams(params);

        // High-contrast outer neon circle
        View outerRing = new View(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.parseColor("#8895FF50"));
        ring.setStroke(3, Color.parseColor("#95FF50"));
        outerRing.setBackground(ring);
        FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(sizePx, sizePx);
        outerRing.setLayoutParams(ringParams);

        // Center crosshair pointer dot
        View centerDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(Color.WHITE);
        int dotSizePx = (int) (8 * getResources().getDisplayMetrics().density);
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
        if (size == 0) size = (int) (30 * getResources().getDisplayMetrics().density);
        nativeCursorView.setX(cursorX - size / 2f);
        nativeCursorView.setY(cursorY - size / 2f);

        // Auto edge scrolling
        if (webView != null) {
            if (cursorY < 120) {
                webView.scrollBy(0, -60);
            } else if (cursorY > screenHeight - 120) {
                webView.scrollBy(0, 60);
            }
        }
    }

    /**
     * Dispatches native hardware touch events (ACTION_DOWN + ACTION_UP) directly to the active surface.
     * Guaranteed to trigger touches inside any video player, iframe, or subtitle list!
     */
    private void dispatchNativeClick(float x, float y) {
        View target = (customViewContainer != null && customViewContainer.getVisibility() == View.VISIBLE && customView != null)
            ? customView : webView;

        if (target == null) return;
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
            downTime, downTime + 70,
            MotionEvent.ACTION_UP,
            1, props, coords,
            0, 0, 1f, 1f, 0, 0,
            0, 0
        );

        target.dispatchTouchEvent(downEvent);
        target.dispatchTouchEvent(upEvent);
        downEvent.recycle();
        upEvent.recycle();

        // Cursor visual pulse animation
        if (nativeCursorView != null) {
            nativeCursorView.animate()
                .scaleX(0.6f)
                .scaleY(0.6f)
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
                nativeCursorView.bringToFront();
                updateCursorPosition();
            } else {
                nativeCursorView.setVisibility(View.GONE);
            }
        }

        updateBadgePill();

        Toast.makeText(
            this,
            isMouseModeActive
                ? "ðŸŽ¯ Ù†Ù…Ø· Ø§Ù„Ù…Ø§ÙˆØ³ Ù…ÙØ¹Ù‘Ù„: Ø­Ø±Ù‘Ùƒ Ø¨Ø§Ù„Ø£Ø³Ù‡Ù… ÙˆØ§Ø¶ØºØ· OK Ù„Ù„Ù†Ù‚Ø± Ø¹Ù„Ù‰ Ø§Ù„ØªØ±Ø¬Ù…Ø© ÙˆØ§Ù„Ø¥Ø¹Ø¯Ø§Ø¯Ø§Øª\n(Ø§Ø¶ØºØ· BACK Ø£Ùˆ OK Ù…Ø·ÙˆÙ„Ø§Ù‹ Ù„Ù„Ø±Ø¬ÙˆØ¹)"
                : "ðŸ“± Ù†Ù…Ø· Ø§Ù„Ø£Ø³Ù‡Ù… Ù…ÙØ¹Ù‘Ù„ (Ø§Ø¶ØºØ· Ù…Ø·ÙˆÙ„Ø§Ù‹ Ø¹Ù„Ù‰ OK Ø£Ùˆ Ø²Ø± Ø§Ù„Ù‚Ø§Ø¦Ù…Ø© Ù„ØªÙØ¹ÙŠÙ„ Ø§Ù„Ù…Ø§ÙˆØ³)",
            Toast.LENGTH_SHORT
        ).show();
    }

    private void updateBadgePill() {
        if (drBadge == null) return;
        drBadge.animate().cancel();
        drBadge.setAlpha(1.0f);
        drBadge.setVisibility(View.VISIBLE);

        if (isMouseModeActive) {
            drBadge.setText("ðŸŽ¯ Ù…Ø§ÙˆØ³ Ù…ÙØ¹Ù‘Ù„ | BACK Ù„Ù„Ø®Ø±ÙˆØ¬");
            drBadge.setTextColor(Color.parseColor("#050505"));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#95FF50"));
            bg.setCornerRadius(30);
            drBadge.setBackground(bg);
            drBadge.setAlpha(1.0f);
        } else {
            drBadge.setText("ðŸ“± Ø±ÙŠÙ…ÙˆØª | ðŸŽ¯ Ø§Ù„Ù…Ø§ÙˆØ³ (OK Ù…Ø·ÙˆÙ„Ø§Ù‹)");
            drBadge.setTextColor(Color.parseColor("#95FF50"));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#CC111111"));
            bg.setCornerRadius(30);
            bg.setStroke(2, Color.parseColor("#95FF50"));
            drBadge.setBackground(bg);
            drBadge.animate().alpha(0.70f).setDuration(500).setStartDelay(3000).start();
        }
    }

    // ==========================================
    // KEY EVENT HANDLING FOR ANDROID TV REMOTES
    // ==========================================

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        // 1. Long-press OK (450ms) to toggle Mouse Mode
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
                dpadHandler.postDelayed(dpadLongPressRunnable, 450);
            }
            return true;
        }

        // 2. Direct shortcut keys to toggle Mouse Mode:
        // Yellow button, Blue button, or Info key on TV remote toggles Mouse Mode instantly!
        if (keyCode == KeyEvent.KEYCODE_PROG_YELLOW || keyCode == KeyEvent.KEYCODE_PROG_BLUE
                || keyCode == KeyEvent.KEYCODE_INFO || keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            toggleNativeMouseMode();
            return true;
        }

        // 3. When Mouse Mode is active: Arrow keys ONLY move cursor smoothly
        if (isMouseModeActive) {
            int repeat = event.getRepeatCount();
            float step = Math.min(65f, 18f + repeat * 4f);

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

        // 4. In Normal Mode: Directional Arrows
        // Pass them directly to WebView / Video Player
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return super.onKeyDown(keyCode, event);
        }

        // 5. Menu & Media Keys
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
            case KeyEvent.KEYCODE_PROG_RED:
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
                // In D-Pad mode, trigger click on activeElement or subtitles
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
        authorText.setText("âš¡ TV Edition â€” Developed by Dr. Muhammed");
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
        badge.setText("ðŸ“± Ø±ÙŠÙ…ÙˆØª | ðŸŽ¯ Ø§Ù„Ù…Ø§ÙˆØ³");
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

        // Clicking the badge directly toggles Mouse Mode or opens menu
        badge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleNativeMouseMode();
            }
        });

        badge.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showDrMuhammedMenu();
                return true;
            }
        });

        badge.animate().alpha(0.70f).setDuration(500).setStartDelay(3000).start();
        return badge;
    }

    private void showDrMuhammedMenu() {
        if (drBadge != null) {
            drBadge.animate().cancel();
            drBadge.setAlpha(1.0f);
            drBadge.setVisibility(View.VISIBLE);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("ðŸŽ¬ CineJoy TV Hub â€” By Dr. Muhammed");

        String[] options = {
            "ðŸŽ¯ Ù…Ø¤Ø´Ø± Ø§Ù„Ù…Ø§ÙˆØ³ Ø§Ù„Ø§ÙØªØ±Ø§Ø¶ÙŠ: " + (isMouseModeActive ? "Ù…ÙØ¹Ù‘Ù„ ðŸŸ¢ (Ø§Ø¶ØºØ· Ù„Ù„ØªØ¹Ø·ÙŠÙ„)" : "Ù…Ø¹Ø·Ù‘Ù„ âšª (Ø§Ø¶ØºØ· Ù„Ù„ØªÙØ¹ÙŠÙ„)"),
            "ðŸ›¡ï¸ Ø­Ø¸Ø± Ø§Ù„Ø¥Ø¹Ù„Ø§Ù†Ø§Øª ÙˆØ§Ù„Ø¨ÙˆØ¨ Ø£Ø¨: " + (isAdBlockActive ? "Ù…ÙØ¹Ù‘Ù„ âœ…" : "Ù…Ø¹Ø·Ù‘Ù„ âŒ"),
            "âš¡ Ø³Ø±Ø¹Ø© Ø§Ù„ØªØ´ØºÙŠÙ„: " + currentPlaybackSpeed + "x",
            "â© ØªØ®Ø·ÙŠ Ø§Ù„Ù…Ù‚Ø¯Ù…Ø© (Skip 85s)",
            "ðŸ”„ Ø¥Ø¹Ø§Ø¯Ø© ØªØ­Ù…ÙŠÙ„ Ø§Ù„ØµÙØ­Ø©",
            "ðŸ§¹ Ù…Ø³Ø­ Ø§Ù„Ø°Ø§ÙƒØ±Ø© Ø§Ù„Ù…Ø¤Ù‚ØªØ© (Clear Cache)",
            "â„¹ï¸ Ø­ÙˆÙ„ Ø§Ù„ØªØ·Ø¨ÙŠÙ‚ ÙˆØ§Ù„Ù…Ø·ÙˆØ±",
            "ðŸšª Ø®Ø±ÙˆØ¬ Ù…Ù† Ø§Ù„ØªØ·Ø¨ÙŠÙ‚"
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
                            isAdBlockActive ? "ØªÙ… ØªÙØ¹ÙŠÙ„ Ø­Ø¸Ø± Ø§Ù„Ø¥Ø¹Ù„Ø§Ù†Ø§Øª âœ…" : "ØªÙ… ØªØ¹Ø·ÙŠÙ„ Ø­Ø¸Ø± Ø§Ù„Ø¥Ø¹Ù„Ø§Ù†Ø§Øª âŒ",
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
                            Toast.makeText(MainActivity.this, "ØªÙ… Ù…Ø³Ø­ Ø§Ù„Ø°Ø§ÙƒØ±Ø© ÙˆØ¥Ø¹Ø§Ø¯Ø© Ø§Ù„ØªØ­Ù…ÙŠÙ„ ðŸ§¹", Toast.LENGTH_SHORT).show();
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

        builder.setNegativeButton("Ø¥ØºÙ„Ø§Ù‚", new DialogInterface.OnClickListener() {
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
        Toast.makeText(this, "âš¡ Ø³Ø±Ø¹Ø© Ø§Ù„ÙÙŠØ¯ÙŠÙˆ: " + currentPlaybackSpeed + "x", Toast.LENGTH_SHORT).show();
    }

    private void skipIntro() {
        if (webView != null) {
            webView.evaluateJavascript(
                "(function() { var v = document.querySelector('video'); if (v) { v.currentTime += 85; } })()",
                null
            );
            Toast.makeText(this, "â© ØªÙ… ØªØ®Ø·ÙŠ 85 Ø«Ø§Ù†ÙŠØ© (Ø§Ù„Ù…Ù‚Ø¯Ù…Ø©)", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Ø­ÙˆÙ„ ØªØ·Ø¨ÙŠÙ‚ CineJoy TV v1.5")
            .setMessage("âœ¨ CineJoy Pro Edition â€” Smart TV & Android\n" +
                        "ðŸ‘¨â€ðŸ’» ØªÙ… Ø§Ù„ØªØ·ÙˆÙŠØ± ÙˆØ§Ù„ØªØ®ØµÙŠØµ Ø¨ÙˆØ§Ø³Ø·Ø©: Dr. Muhammed\n\n" +
                        "â€¢ ØªØ­ÙƒÙ… ÙØ§Ø¦Ù‚ Ø¨Ø§Ù„Ù…Ø§ÙˆØ³ Ø§Ù„Ø§ÙØªØ±Ø§Ø¶ÙŠ Ù„Ø§Ø®ØªÙŠØ§Ø± Ø§Ù„ØªØ±Ø¬Ù…Ø© ÙˆØ¶Ø¨Ø· Ø§Ù„Ø¬ÙˆØ¯Ø© Ø¨Ø¯Ù‚Ø©\n" +
                        "â€¢ ØªÙØ¹ÙŠÙ„ Ø§Ù„Ù…Ø§ÙˆØ³ ÙÙˆØ±Ø§Ù‹ Ø¨Ø§Ù„Ø¶ØºØ· Ø¹Ù„Ù‰ Ø§Ù„Ø´Ø§Ø±Ø©ØŒ Ø£Ùˆ Ø§Ù„Ø²Ø± Ø§Ù„Ø£ØµÙØ±ØŒ Ø£Ùˆ Ø¶ØºØ·Ø© Ù…Ø·ÙˆÙ„Ø© Ø¹Ù„Ù‰ OK\n" +
                        "â€¢ Ø¯Ø¹Ù… Ø§Ù„ØªÙ†Ù‚Ù„ Ø§Ù„Ø·Ø¨ÙŠØ¹ÙŠ Ø¨Ø§Ù„Ø£Ø³Ù‡Ù… ÙˆØªÙ‚Ø¯ÙŠÙ… ÙˆØªØ£Ø®ÙŠØ± Ø§Ù„ÙÙŠØ¯ÙŠÙˆ\n" +
                        "â€¢ Ø£Ø¯Ø§Ø¡ Ø³Ø±ÙŠØ¹ ÙˆØ®ÙÙŠÙ Ø¨Ø¯ÙˆÙ† Ø£ÙŠ Ø§Ø³ØªÙ‡Ù„Ø§Ùƒ Ø²Ø§Ø¦Ø¯ Ù„Ù„Ø°Ø§ÙƒØ±Ø©")
            .setPositiveButton("Ø­Ø³Ù†Ø§Ù‹", null)
            .show();
    }

    /**
     * Injects Subtitle, Player Controls, and Clean TV Navigation Enhancements
     */
    private void injectTvSubtitleAndControlEnhancements() {
        if (webView == null) return;

        String tvEngineJs = "(function() {" +
            "  if (window.__cinejoyTvInjected) return;" +
            "  window.__cinejoyTvInjected = true;" +

            // 1. High contrast crisp outline for TV navigation
            "  var style = document.createElement('style');" +
            "  style.id = 'cinejoy-tv-styles';" +
            "  style.textContent = `" +
            "    :focus, .tv-focused, [data-focus=\"true\"] {" +
            "      outline: 3px solid #95FF50 !important;" +
            "      outline-offset: 2px !important;" +
            "      border-radius: 6px !important;" +
            "    }" +
            "    * {" +
            "      -webkit-tap-highlight-color: rgba(149, 255, 80, 0.3) !important;" +
            "    }" +
            "  `;" +
            "  (document.head || document.documentElement).appendChild(style);" +

            // 2. Block Popups
            "  window.open = function() { return null; };" +
            "  try { localStorage.setItem('addies', 'false'); } catch(e){}" +

            // 3. Make interactive elements, subtitle options, and dropdown items focusable
            "  function scanAndEnableFocus() {" +
            "    var selectors = 'a, button, input, select, [role=\"button\"], [role=\"menuitem\"], [role=\"option\"], .art-subtitle, .art-setting, .vjs-menu-item, li[data-val], .dropdown-item';" +
            "    var els = document.querySelectorAll(selectors);" +
            "    for (var i = 0; i < els.length; i++) {" +
            "      if (!els[i].hasAttribute('tabindex')) els[i].setAttribute('tabindex', '0');" +
            "    }" +
            "  }" +
            "  setTimeout(scanAndEnableFocus, 1000);" +
            "  document.addEventListener('click', function() { setTimeout(scanAndEnableFocus, 300); }, true);" +

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
            .setTitle("ØªØ·Ø¨ÙŠÙ‚ CineJoy")
            .setMessage("Ù‡Ù„ ØªØ±ÙŠØ¯ Ø­Ù‚Ø§Ù‹ Ø§Ù„Ø®Ø±ÙˆØ¬ Ù…Ù† Ø§Ù„ØªØ·Ø¨ÙŠÙ‚ØŸ")
            .setPositiveButton("Ù†Ø¹Ù… (Ø®Ø±ÙˆØ¬)", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            })
            .setNegativeButton("Ø¥Ù„ØºØ§Ø¡", new DialogInterface.OnClickListener() {
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
