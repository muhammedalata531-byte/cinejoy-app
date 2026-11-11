package com.cinejoy.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
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
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.RenderProcessGoneDetail;
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

    private static final String TARGET_URL = "https://cinejoy.pk/";
    private static final int FILE_CHOOSER_REQUEST_CODE = 100;
    private static final long OK_LONG_PRESS_MS = 500;

    private WebView webView;
    private WebChromeClient activeChromeClient;
    private ProgressBar progressBar;
    private FrameLayout customViewContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private RelativeLayout splashLayout;
    private TextView drBadge;

    private ValueCallback<Uri[]> filePathCallback;
    private boolean mainFrameError = false;
    private int rendererCrashCount = 0;
    private long lastRendererCrashAt = 0L;

    private boolean isAdBlockActive = true;
    private boolean isMouseModeActive = false;
    private float currentPlaybackSpeed = 1.0f;

    // Native virtual mouse
    private FrameLayout nativeCursorView;
    private float cursorX = 960f;
    private float cursorY = 540f;
    private int screenWidth = 1920;
    private int screenHeight = 1080;

    // OK long-press tracking
    private boolean isOkLongPressFired = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable okLongPressRunnable;

    private static final String[] BLOCKED_PATTERNS = new String[]{
        "doubleclick.net", "google-analytics.com", "googlesyndication.com",
        "googletagservices.com", "partner.googleadservices.com", "adservice.google",
        "pagead2.googlesyndication", "popads.net", "popcash.net", "adsterra.com",
        "propellerads.com", "propellerclick.com", "monetag.com", "mtag.pw",
        "clickadu.com", "hilltopads.net", "hilltopads.com", "ad-maven.com",
        "ad-maven.net", "adcash.com", "onclickads.net", "onclckds.com",
        "exoclick.com", "trafficfactory.biz", "juicyads.com", "trafficstars.com",
        "tsyndicate.com", "adnxs.com", "adform.net", "criteo.com", "criteo.net",
        "taboola.com", "outbrain.com", "histats.com", "mc.yandex.ru",
        "cloudflareinsights.com", "quantserve.com", "scorecardresearch.com",
        "zedo.com", "smartadserver.com", "pubmatic.com", "rubiconproject.com",
        "openx.net", "indexww.com", "bidswitch.net", "casalemedia.com",
        "sharethrough.com", "spotxchange.com", "teads.tv", "media.net",
        "revcontent.com", "mgid.com", "adskeeper.co.uk", "popunder", "/ads/",
        "/adbanner", "/banner.js", "popunder", "adblock-detect", "admaven"
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();

        DisplayMetrics dm = new DisplayMetrics();
        try {
            getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        } catch (Exception ignored) {
            getWindowManager().getDefaultDisplay().getMetrics(dm);
        }
        screenWidth = Math.max(800, dm.widthPixels);
        screenHeight = Math.max(600, dm.heightPixels);
        cursorX = screenWidth / 2f;
        cursorY = screenHeight / 2f;

        RelativeLayout rootLayout = new RelativeLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#050505"));

        webView = new WebView(this);
        RelativeLayout.LayoutParams wvParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(wvParams);
        webView.setBackgroundColor(Color.parseColor("#050505"));
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();
        // Critical for Mali GPU TVs: never add extra rendering layers
        webView.setLayerType(View.LAYER_TYPE_NONE, null);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#95FF50")));
        progressBar.setBackgroundColor(Color.TRANSPARENT);
        RelativeLayout.LayoutParams pbParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, 8);
        pbParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        progressBar.setLayoutParams(pbParams);

        customViewContainer = new FrameLayout(this);
        customViewContainer.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        customViewContainer.setBackgroundColor(Color.BLACK);
        customViewContainer.setVisibility(View.GONE);

        drBadge = createDrMuhammedBadge();
        nativeCursorView = createNativeCursorView();
        splashLayout = createSplashScreen();

        rootLayout.addView(webView);
        rootLayout.addView(progressBar);
        rootLayout.addView(customViewContainer);
        rootLayout.addView(drBadge);
        rootLayout.addView(nativeCursorView);
        rootLayout.addView(splashLayout);
        setContentView(rootLayout);

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

        String defaultUa = settings.getUserAgentString();
        if (defaultUa != null) {
            settings.setUserAgentString(defaultUa.replace("; wv", ""));
        }

        // Allows live DOM inspection over adb (devtools protocol) for diagnosis
        try {
            WebView.setWebContentsDebuggingEnabled(true);
        } catch (Exception ignored) {}

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        activeChromeClient = new WebChromeClient() {
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
                    exitFullscreen();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                customViewContainer.setVisibility(View.VISIBLE);
                customViewContainer.addView(view);
                hideSystemUI();
                if (nativeCursorView != null) {
                    nativeCursorView.bringToFront();
                }
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }

            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> callback,
                                             FileChooserParams fileChooserParams) {
                MainActivity.this.filePathCallback = callback;
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
                try {
                    android.util.Log.i("CineJoyJS", consoleMessage.message());
                } catch (Exception ignored) {}
                return false;
            }
        };
        webView.setWebChromeClient(activeChromeClient);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Legacy overload (API 21-23) - keeps old TVs working
                return handleUrlOverride(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrlOverride(view, request.getUrl().toString());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (isAdBlockActive && request != null && request.getUrl() != null) {
                    String url = request.getUrl().toString();
                    if (isAdOrTracker(url)) {
                        return new WebResourceResponse("text/plain", "UTF-8",
                            new ByteArrayInputStream(new byte[0]));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                mainFrameError = false;
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(15);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                webView.requestFocus();
                if (!mainFrameError) {
                    injectTvEngine();
                }
                hideSplashScreen();
                updateBadgePill();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // Legacy overload (API 21-23)
                handleMainFrameError(failingUrl);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    handleMainFrameError(request.getUrl().toString());
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // WebView renderer crashed: recover instead of leaving a black screen
                long now = System.currentTimeMillis();
                if (now - lastRendererCrashAt < 60000L) {
                    rendererCrashCount++;
                } else {
                    rendererCrashCount = 1;
                }
                lastRendererCrashAt = now;

                if (rendererCrashCount <= 2) {
                    rebuildWebView();
                    Toast.makeText(MainActivity.this,
                        "تم استعادة المحرك تلقائياً بعد عطل مؤقت 🔄",
                        Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this,
                        "عطل متكرر في محرك العرض. أغلق التطبيق وافتحه مرة أخرى 🛠️",
                        Toast.LENGTH_LONG).show();
                }
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

        Toast.makeText(this, "🎬 CineJoy TV v2.0 — By Dr. Muhammed", Toast.LENGTH_SHORT).show();

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                hideSplashScreen();
            }
        }, 6000);

        // SPA pages navigate without onPageFinished: re-inject (guarded, idempotent) periodically
        Runnable engineKeepAlive = new Runnable() {
            @Override
            public void run() {
                injectTvEngine();
                mainHandler.postDelayed(this, 3000);
            }
        };
        mainHandler.postDelayed(engineKeepAlive, 3000);
    }

    private boolean handleUrlOverride(WebView view, String url) {
        if (url == null) return false;
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception ignored) {}
        return true;
    }

    private void handleMainFrameError(String url) {
        mainFrameError = true;
        progressBar.setVisibility(View.GONE);
        hideSplashScreen();
        if (webView != null) {
            webView.loadData(buildErrorPageHtml(url), "text/html; charset=utf-8", "UTF-8");
        }
    }

    private String buildErrorPageHtml(String url) {
        return "<!DOCTYPE html><html dir='rtl'><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
            + "<style>body{background:#050505;color:#fff;font-family:sans-serif;display:flex;"
            + "flex-direction:column;align-items:center;justify-content:center;height:100vh;margin:0;}"
            + "h1{color:#95FF50;font-size:28px;margin-bottom:8px;}"
            + "p{color:#ccc;font-size:16px;text-align:center;line-height:1.7;max-width:80%;}"
            + ".hint{color:#95FF50;font-size:14px;margin-top:16px;}</style></head><body>"
            + "<h1>تعذر الاتصال بالإنترنت</h1>"
            + "<p>تأكد من اتصال التلفاز بشبكة Wi-Fi ثم أعد المحاولة.</p>"
            + "<p class='hint'>اضغط زر القائمة (MENU) في الريموت ثم اختر «إعادة تحميل الصفحة»</p>"
            + "</body></html>";
    }

    private void rebuildWebView() {
        try {
            if (webView != null) {
                webView.stopLoading();
                webView.loadUrl("about:blank");
            }
        } catch (Exception ignored) {}
        recreate();
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
    // TV ENGINE INJECTION (ES5-COMPATIBLE JS)
    // Works on old Chromium 37 WebView (Android 5)
    // ==========================================

    private void injectTvEngine() {
        if (webView == null) return;

        String js = "(function(){"
            + "if(window.__cinejoyReady)return;"
            + "window.__cinejoyReady=true;"
            + "var style=document.createElement('style');"
            + "style.textContent='.cinejoy-focused{outline:3px solid #95FF50 !important;"
            + "outline-offset:2px !important;border-radius:6px !important;}'"
            + "+'*{-webkit-tap-highlight-color:rgba(149,255,80,0.25) !important;}';"
            + "try{(document.head||document.documentElement).appendChild(style);}catch(e){}"
            + "window.open=function(){return null;};"
            + "try{localStorage.setItem('addies','false');}catch(e){}"
            + "var SEL='a[href],button,input:not([type=hidden]):not([disabled]),select,textarea,"
            + "[tabindex]:not([tabindex=\"-1\"]),[role=\"button\"],[role=\"menuitem\"],"
            + "[role=\"option\"],[role=\"tab\"],[onclick],.art-subtitle,.art-setting,"
            + ".art-control,.art-icon,.art-server,.vjs-menu-item,.vjs-menu-button,.vjs-button,"
            + "li[data-val],.dropdown-item,.server-item,.episode-item,.quality-item,.subtitle-item,"
            + ".sliders_item,.video-item,.poster,.poster-item,.film-item';"
            + "var lastFocused=null;"
            + "function vis(el){"
            + "try{var r=el.getBoundingClientRect();"
            + "if(r.width<4||r.height<4)return false;"
            + "var s=window.getComputedStyle(el);"
            + "if(s.display==='none'||s.visibility==='hidden')return false;"
            + "if(s.opacity==='0')return false;"
            + "return true;}catch(e){return false;}"
            + "}"
            + "function cands(){"
            + "try{var els=document.querySelectorAll(SEL);var out=[];"
            + "for(var i=0;i<els.length;i++){var e=els[i];"
            + "if(!e.hasAttribute('tabindex')&&!e.disabled){"
            + "try{e.setAttribute('tabindex','0');}catch(e2){}}"
            + "if(vis(e)){e.__cr=e.getBoundingClientRect();out.push(e);}}"
            + "return out;}catch(e){return [];}"
            + "}"
            + "function cx(e){var r=e.__cr;try{if(!r)r=e.getBoundingClientRect();}catch(er){return 0;}return r.left+r.width/2;}"
            + "function cy(e){var r=e.__cr;try{if(!r)r=e.getBoundingClientRect();}catch(er){return 0;}return r.top+r.height/2;}"
            + "function mark(el){"
            + "if(lastFocused&&lastFocused.classList){try{lastFocused.classList.remove('cinejoy-focused');}catch(e){}}"
            + "lastFocused=el;"
            + "if(el.classList){try{el.classList.add('cinejoy-focused');}catch(e){}}"
            + "try{el.focus();}catch(e){}"
            + "try{el.scrollIntoView(false);}catch(e){}"
            + "}"
            + "function first(){"
            + "var cs=cands();if(!cs.length)return false;"
            + "var best=cs[0],bd=9999999;"
            + "var sx=window.innerWidth/2,sy=window.innerHeight/2;"
            + "for(var i=0;i<cs.length;i++){"
            + "var d=Math.abs(cx(cs[i])-sx)+Math.abs(cy(cs[i])-sy);"
            + "if(d<bd){bd=d;best=cs[i];}}"
            + "mark(best);return true;"
            + "}"
            + "window.__cinejoyMove=function(dir){"
            + "var cs=cands();if(!cs.length)return 'none';"
            + "var cur=null;"
            + "if(lastFocused&&vis(lastFocused))cur=lastFocused;"
            + "else if(document.activeElement&&document.activeElement!==document.body"
            + "&&document.activeElement!==document.documentElement)cur=document.activeElement;"
            + "if(!cur){return first()?'focus':'none';}"
            + "var bx=cx(cur),by=cy(cur);"
            + "var best=null,bd=9999999;"
            + "for(var i=0;i<cs.length;i++){"
            + "var e=cs[i];if(e===cur)continue;"
            + "var dx=cx(e)-bx,dy=cy(e)-by;"
            + "var along,perp;"
            + "if(dir==='left'){if(dx>=-6)continue;along=-dx;perp=Math.abs(dy);}"
            + "else if(dir==='right'){if(dx<=6)continue;along=dx;perp=Math.abs(dy);}"
            + "else if(dir==='up'){if(dy>=-6)continue;along=-dy;perp=Math.abs(dx);}"
            + "else{if(dy<=6)continue;along=dy;perp=Math.abs(dx);}"
            + "var d=along+perp*2.2;"
            + "if(d<bd){bd=d;best=e;}}"
            + "if(!best){"
            + "if(dir==='up')window.scrollBy(0,-240);"
            + "else if(dir==='down')window.scrollBy(0,240);"
            + "else if(dir==='left')window.scrollBy(-180,0);"
            + "else window.scrollBy(180,0);"
            + "return 'scroll';}"
            + "mark(best);return 'focus';"
            + "};"
            + "window.__cinejoyClick=function(){"
            + "var el=(lastFocused&&vis(lastFocused))?lastFocused:"
            + "((document.activeElement&&document.activeElement!==document.body"
            + "&&document.activeElement!==document.documentElement)?document.activeElement:null);"
            + "if(!el)return 'none';"
            + "var r=null;try{r=el.getBoundingClientRect();}catch(e){}"
            + "var x=r?r.left+r.width/2:0,y=r?r.top+r.height/2:0;"
            + "var opts={bubbles:true,cancelable:true,view:window,clientX:x,clientY:y,button:0};"
            + "function fire(t){"
            + "try{var ev=(typeof PointerEvent==='function')"
            + "?new PointerEvent(t,opts):new MouseEvent(t,opts);"
            + "el.dispatchEvent(ev);}catch(e){}"
            + "}"
            + "try{el.focus();}catch(e){}"
            + "fire('pointerdown');fire('mousedown');fire('touchstart');"
            + "fire('pointerup');fire('mouseup');fire('touchend');"
            + "try{el.click();}catch(e){}"
            + "return 'click';"
            + "};"
            + "try{var probe=cands();console.log('CINEJOY_ENGINE_LOADED candidates='+probe.length);}catch(elog){console.log('CINEJOY_ENGINE_PROBE_ERR='+elog);}"
            + "try{"
            + "var dd=document.elementFromPoint(window.innerWidth/2,window.innerHeight/2);"
            + "var chain=[];var nn=dd;"
            + "while(nn&&chain.length<6){"
            + "var ss=window.getComputedStyle(nn);"
            + "chain.push(nn.tagName+(nn.id?'#'+nn.id:'')+' pe='+ss.pointerEvents+' z='+ss.zIndex);"
            + "nn=nn.parentElement;}"
            + "console.log('CINEJOY_TOPCHAIN: '+(chain.join(' > ')||'null'));"
            + "}catch(e3){console.log('CINEJOY_TOPCHAIN_ERR='+e3);}"
            + "try{"
            + "var ADPATS=['doubleclick.net','google-analytics','googlesyndication','googletagservices',"
            + "'adservice.google','pagead2','popads','popcash','adsterra','propellerads','propellerclick',"
            + "'monetag','mtag.pw','clickadu','hilltopads','ad-maven','adcash','onclickads','onclckds',"
            + "'exoclick','trafficfactory','juicyads','trafficstars','tsyndicate','adnxs','adform','criteo',"
            + "'taboola','outbrain','histats','mc.yandex','cloudflareinsights','quantserve',"
            + "'scorecardresearch','zedo','smartadserver','pubmatic','rubiconproject','openx','indexww',"
            + "'bidswitch','casalemedia','sharethrough','spotxchange','teads.tv','media.net','revcontent',"
            + "'mgid','adskeeper','onclickalgo','rtb','popunder','admaven'];"
            + "function neutralizeOverlays(){"
            + "try{"
            + "var ifs=document.querySelectorAll('iframe');"
            + "for(var i=0;i<ifs.length;i++){"
            + "var f=ifs[i];"
            + "var r=f.getBoundingClientRect();"
            + "var big=r.width>=window.innerWidth*0.85&&r.height>=window.innerHeight*0.85;"
            + "if(!big)continue;"
            + "var src='';try{src=(f.getAttribute('src')||'');}catch(se){}"
            + "var sl=src.toLowerCase();"
            + "var bad=false;"
            + "for(var j=0;j<ADPATS.length;j++){if(sl.indexOf(ADPATS[j])!==-1){bad=true;break;}}"
            + "if(!bad&&(sl===''||sl.indexOf('about:blank')!==-1||f.hasAttribute('srcdoc')))bad=true;"
            + "if(bad){"
            + "f.style.pointerEvents='none';f.style.zIndex='-1';"
            + "console.log('CINEJOY_OVERLAY_NEUTRALIZED iframe src='+src.substring(0,120));}"
            + "}"
            + "var divs=document.querySelectorAll('div,section');"
            + "for(var k=0;k<divs.length;k++){"
            + "var d2=divs[k];"
            + "var s2=null;try{s2=window.getComputedStyle(d2);}catch(serr){continue;}"
            + "if(s2.zIndex!=='2147483647'&&s2.zIndex!=='2147483646')continue;"
            + "var r2=d2.getBoundingClientRect();"
            + "if(r2.width<window.innerWidth*0.8)continue;"
            + "d2.style.pointerEvents='none';"
            + "var kids2=d2.children;"
            + "for(var m=0;m<kids2.length;m++){kids2[m].style.pointerEvents='none';}"
            + "console.log('CINEJOY_OVERLAY_NEUTRALIZED div '+r2.width+'x'+r2.height);"
            + "}"
            + "}catch(e4){}}"
            + "neutralizeOverlays();"
            + "window.__cinejoyOverlayTimer=setInterval(neutralizeOverlays,1500);"
            + "try{"
            + "var __mo=new MutationObserver(function(muts){"
            + "clearTimeout(window.__cinejoyScanT);"
            + "window.__cinejoyScanT=setTimeout(neutralizeOverlays,60);"
            + "});"
            + "__mo.observe(document.documentElement,{childList:true,subtree:true});"
            + "}catch(moe){console.log('CINEJOY_MO_ERR='+moe);}"
            + "}catch(eo){console.log('CINEJOY_OVERLAY_INIT_ERR='+eo);}"
            + "})();";

        webView.evaluateJavascript(js, null);
    }

    // ==========================================
    // NATIVE VIRTUAL MOUSE ENGINE
    // ==========================================

    private FrameLayout createNativeCursorView() {
        FrameLayout container = new FrameLayout(this);
        int sizeDp = 30;
        int sizePx = (int) (sizeDp * getResources().getDisplayMetrics().density);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(sizePx, sizePx);
        container.setLayoutParams(params);

        View outerRing = new View(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.parseColor("#7795FF50"));
        ring.setStroke(3, Color.parseColor("#95FF50"));
        outerRing.setBackground(ring);
        FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(sizePx, sizePx);
        outerRing.setLayoutParams(ringParams);

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

        if (webView != null && customView == null) {
            if (cursorY < 120) {
                webView.scrollBy(0, -60);
            } else if (cursorY > screenHeight - 120) {
                webView.scrollBy(0, 60);
            }
        }
    }

    /**
     * Dispatches a native touch (DOWN -> small MOVE -> UP after a real delay) at (x, y).
     * The delay is essential: instant DOWN+UP pairs are ignored by Artplayer/Vidlink players.
     */
    private void dispatchNativeClick(float x, float y) {
        final View target = (customViewContainer != null
            && customViewContainer.getVisibility() == View.VISIBLE
            && customView != null) ? customView : webView;

        if (target == null) return;
        final long now = SystemClock.uptimeMillis();

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

        try {
            MotionEvent downEvent = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_DOWN, 1, props, coords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
            target.dispatchTouchEvent(downEvent);
            downEvent.recycle();

            MotionEvent moveEvent = MotionEvent.obtain(
                now, now + 40, MotionEvent.ACTION_MOVE, 1, props, coords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
            target.dispatchTouchEvent(moveEvent);
            moveEvent.recycle();

            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        MotionEvent upEvent = MotionEvent.obtain(
                            now, now + 90, MotionEvent.ACTION_UP, 1, props, coords,
                            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
                        target.dispatchTouchEvent(upEvent);
                        upEvent.recycle();
                    } catch (Exception ignored) {}
                }
            }, 80);
        } catch (Exception ignored) {}

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
                if (webView != null) webView.requestFocus();
            }
        }

        updateBadgePill();

        Toast.makeText(this,
            isMouseModeActive
                ? "🎯 نمط الماوس مفعّل: حرّك بالأسهم واضغط OK للنقر (BACK للرجوع)"
                : "📱 نمط الأسهم مفعّل (OK مطولاً لتفعيل الماوس)",
            Toast.LENGTH_SHORT).show();
    }

    private void updateBadgePill() {
        if (drBadge == null) return;
        drBadge.animate().cancel();
        drBadge.setAlpha(1.0f);
        drBadge.setVisibility(View.VISIBLE);

        GradientDrawable bg = new GradientDrawable();
        if (isMouseModeActive) {
            drBadge.setText("🎯 ماوس مفعّل | BACK للخروج");
            drBadge.setTextColor(Color.parseColor("#050505"));
            bg.setColor(Color.parseColor("#95FF50"));
            bg.setCornerRadius(30);
        } else {
            drBadge.setText("📱 ريموت | 🎯 الماوس (OK مطولاً)");
            drBadge.setTextColor(Color.parseColor("#95FF50"));
            bg.setColor(Color.parseColor("#CC111111"));
            bg.setCornerRadius(30);
            bg.setStroke(2, Color.parseColor("#95FF50"));
        }
        drBadge.setBackground(bg);
        if (!isMouseModeActive) {
            drBadge.animate().alpha(0.70f).setDuration(500).setStartDelay(3000).start();
        }
    }

    private void exitFullscreen() {
        if (customView == null) return;
        try {
            customViewContainer.removeView(customView);
        } catch (Exception ignored) {}
        customView = null;
        customViewContainer.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            try {
                customViewCallback.onCustomViewHidden();
            } catch (Exception ignored) {}
            customViewCallback = null;
        }
        hideSystemUI();
        webView.requestFocus();
    }

    private void sendVideoCommand(String jsBody) {
        if (webView == null) return;
        webView.evaluateJavascript(
            "(function(){var v=document.querySelector('video');"
                + "if(!v){var vs=document.querySelectorAll('video');"
                + "for(var i=0;i<vs.length;i++){if(vs[i].offsetWidth>0){v=vs[i];break;}}}"
                + "if(v){" + jsBody + "}})()", null);
    }

    // ==========================================
    // KEY EVENT HANDLING FOR ANDROID TV REMOTES
    // Critical: keys are intercepted in dispatchKeyEvent BEFORE the WebView
    // consumes them (a focused-in-touch-mode WebView eats DPAD/OK keys
    // internally, so onKeyDown is never called). This is why navigation
    // failed on real TVs.
    // ==========================================

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        boolean isIntercepted = keyCode == KeyEvent.KEYCODE_DPAD_UP
            || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
            || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
            || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
            || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
            || keyCode == KeyEvent.KEYCODE_ENTER
            || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
            || keyCode == KeyEvent.KEYCODE_BACK
            || keyCode == KeyEvent.KEYCODE_MENU
            || keyCode == KeyEvent.KEYCODE_SETTINGS
            || keyCode == KeyEvent.KEYCODE_PROG_YELLOW
            || keyCode == KeyEvent.KEYCODE_PROG_BLUE
            || keyCode == KeyEvent.KEYCODE_PROG_RED
            || keyCode == KeyEvent.KEYCODE_INFO
            || keyCode == KeyEvent.KEYCODE_BUTTON_Y
            || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
            || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
            || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND;

        if (isIntercepted) {
            if (action == KeyEvent.ACTION_DOWN) {
                handleKeyDown(keyCode, event);
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                handleKeyUp(keyCode, event);
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private void handleKeyDown(int keyCode, KeyEvent event) {

        // 1. OK / ENTER: long-press toggles Mouse Mode
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
            || keyCode == KeyEvent.KEYCODE_ENTER
            || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {

            if (event.getRepeatCount() == 0) {
                isOkLongPressFired = false;
                okLongPressRunnable = new Runnable() {
                    @Override
                    public void run() {
                        isOkLongPressFired = true;
                        toggleNativeMouseMode();
                    }
                };
                mainHandler.postDelayed(okLongPressRunnable, OK_LONG_PRESS_MS);
            }
            return;
        }

        // 2. Direct shortcuts (yellow / blue / info / gamepad-Y) toggle Mouse Mode
        if (keyCode == KeyEvent.KEYCODE_PROG_YELLOW || keyCode == KeyEvent.KEYCODE_PROG_BLUE
            || keyCode == KeyEvent.KEYCODE_INFO || keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            toggleNativeMouseMode();
            return;
        }

        // 3. Mouse mode: arrows move the cursor with real acceleration
        if (isMouseModeActive) {
            int repeat = event.getRepeatCount();
            float step = Math.min(150f, 45f + repeat * 10f);

            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    cursorY = Math.max(8f, cursorY - step);
                    updateCursorPosition();
                    return;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    cursorY = Math.min((float) screenHeight - 8f, cursorY + step);
                    updateCursorPosition();
                    return;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    cursorX = Math.max(8f, cursorX - step);
                    updateCursorPosition();
                    return;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    cursorX = Math.min((float) screenWidth - 8f, cursorX + step);
                    updateCursorPosition();
                    return;
                case KeyEvent.KEYCODE_BACK:
                    toggleNativeMouseMode();
                    return;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    dispatchNativeClick(cursorX, cursorY);
                    return;
            }
            return;
        }

        // 4. Normal mode: arrows go through our JS spatial navigation engine
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (webView != null) {
                    String dir;
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_UP: dir = "up"; break;
                        case KeyEvent.KEYCODE_DPAD_DOWN: dir = "down"; break;
                        case KeyEvent.KEYCODE_DPAD_LEFT: dir = "left"; break;
                        default: dir = "right"; break;
                    }
                    webView.evaluateJavascript(
                        "window.__cinejoyMove&&window.__cinejoyMove('" + dir + "')",
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String value) {
                                try {
                                    android.util.Log.i("CineJoyJS", "move(" + dir + ") -> " + value);
                                } catch (Exception ignored) {}
                            }
                        });
                }
                return;
        }

        // 5. Menu & media keys
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
            case KeyEvent.KEYCODE_PROG_RED:
                showDrMuhammedMenu();
                return;

            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                sendVideoCommand("if(v.paused){v.play();}else{v.pause();}");
                return;

            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                sendVideoCommand("v.currentTime+=10;");
                return;

            case KeyEvent.KEYCODE_MEDIA_REWIND:
                sendVideoCommand("v.currentTime-=10;");
                return;

            case KeyEvent.KEYCODE_BACK:
                if (customView != null) {
                    exitFullscreen();
                    return;
                }
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    return;
                }
                showExitConfirmDialog();
                return;
        }
    }

    private void handleKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
            || keyCode == KeyEvent.KEYCODE_ENTER
            || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {

            if (okLongPressRunnable != null) {
                mainHandler.removeCallbacks(okLongPressRunnable);
                okLongPressRunnable = null;
            }

            if (isOkLongPressFired) {
                isOkLongPressFired = false;
                return;
            }

            if (isMouseModeActive) {
                dispatchNativeClick(cursorX, cursorY);
            } else {
                if (webView != null) {
                    webView.evaluateJavascript(
                        "window.__cinejoyClick&&window.__cinejoyClick()",
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String value) {
                                try {
                                    android.util.Log.i("CineJoyJS", "click -> " + value);
                                } catch (Exception ignored) {}
                            }
                        });
                }
            }
        }
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
                .setDuration(250)
                .setListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        if (splashLayout != null) splashLayout.setVisibility(View.GONE);
                    }
                });
        }
    }

    private TextView createDrMuhammedBadge() {
        TextView badge = new TextView(this);
        badge.setText("📱 ريموت | 🎯 الماوس");
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
        builder.setTitle("🎬 CineJoy TV Hub — By Dr. Muhammed");

        String[] options = {
            "🎯 المؤشر الافتراضي: " + (isMouseModeActive ? "مفعّل 🟢" : "معطّل ⚪"),
            "🛡️ حظر الإعلانات: " + (isAdBlockActive ? "مفعّل ✅" : "معطّل ❌"),
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
                            Toast.makeText(MainActivity.this, "تم مسح الذاكرة وإعادة التحميل 🧹",
                                Toast.LENGTH_SHORT).show();
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

        sendVideoCommand("v.playbackRate=" + currentPlaybackSpeed + ";");
        Toast.makeText(this, "⚡ سرعة الفيديو: " + currentPlaybackSpeed + "x", Toast.LENGTH_SHORT).show();
    }

    private void skipIntro() {
        sendVideoCommand("v.currentTime+=85;");
        Toast.makeText(this, "⏩ تم تخطي 85 ثانية (المقدمة)", Toast.LENGTH_SHORT).show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
            .setTitle("حول تطبيق CineJoy TV v2.0")
            .setMessage("✨ CineJoy Pro Edition — Smart TV & Android\n"
                + "👨‍💻 تم التطوير والتخصيص بواسطة: Dr. Muhammed\n\n"
                + "• محرك تنقل مكاني جديد يعمل مع كل الأزرار والقوائم\n"
                + "• مؤشر ماوس أسرع بنقرات حقيقية داخل المشغلات والإطارات\n"
                + "• استرداد تلقائي بعد أعطال محرك العرض (بدون شاشة سوداء)\n"
                + "• حظر موسّع للإعلانات وتحميل أسرع للصفحات\n"
                + "• صفحة خطأ عربية واضحة عند انقطاع الاتصال")
            .setPositiveButton("حسناً", null)
            .show();
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
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
            if (webView != null && !isMouseModeActive) webView.requestFocus();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
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
            if (!isMouseModeActive) webView.requestFocus();
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
        if (okLongPressRunnable != null) {
            mainHandler.removeCallbacks(okLongPressRunnable);
        }
        mainHandler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
