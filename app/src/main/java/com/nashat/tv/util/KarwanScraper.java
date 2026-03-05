package com.nashat.tv.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KarwanScraper {

    public interface ScrapeCallback {
        void onStreamFound(String streamUrl);
        void onError(String error);
    }

    private WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isIframeLoaded = false;
    private boolean hasFinished = false;

    public void scrapeStreamUrl(Context context, String channelUrl, ScrapeCallback callback) {
        handler.post(() -> {
            try {
                webView = new WebView(context);
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setDomStorageEnabled(true);
                webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
                webView.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36");

                webView.addJavascriptInterface(new Object() {
                    @JavascriptInterface
                    public void onStreamConfig(String config) {
                        if (hasFinished) return;
                        Log.d("KarwanScraper", "Config found: " + config);
                        
                        String url = null;
                        Matcher mDash = Pattern.compile("dash\\s*:\\s*['\"](.*?)['\"]").matcher(config);
                        if (mDash.find()) {
                            url = mDash.group(1);
                        } else {
                            Matcher mHls = Pattern.compile("hls\\s*:\\s*['\"](.*?)['\"]").matcher(config);
                            if (mHls.find()) {
                                url = mHls.group(1);
                            }
                        }

                        if (url != null && !url.isEmpty()) {
                            hasFinished = true;
                            final String finalUrl = url;
                            handler.post(() -> callback.onStreamFound(finalUrl));
                        }
                    }
                    
                    @JavascriptInterface
                    public void onIframeSrc(String src) {
                        if (hasFinished) return;
                        Log.d("KarwanScraper", "Iframe found: " + src);
                        if (src != null && !src.isEmpty()) {
                            isIframeLoaded = true;
                            String absoluteSrc = src.startsWith("http") ? src : "https://karwan.tv" + src;
                            handler.post(() -> webView.loadUrl(absoluteSrc));
                        }
                    }
                }, "JSBridge");

                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        if (hasFinished) return;
                        Log.d("KarwanScraper", "Page loaded: " + url);
                        if (!isIframeLoaded) {
                            // Find iframe
                            view.evaluateJavascript(
                                    "var iframe = document.querySelector('iframe');" +
                                    "if(iframe) { JSBridge.onIframeSrc(iframe.getAttribute('src')); }", null);
                        } else {
                            // Inside iframe, find stream source
                            view.evaluateJavascript(
                                    "var scripts = document.querySelectorAll('script');" +
                                    "var config = '';" +
                                    "for(var i=0; i<scripts.length; i++) {" +
                                    "  if(scripts[i].innerHTML.indexOf('src = {') !== -1) {" +
                                    "    config = scripts[i].innerHTML;" +
                                    "    break;" +
                                    "  }" +
                                    "}" +
                                    "if(config !== '') { JSBridge.onStreamConfig(config); }", null);
                        }
                    }
                });

                Log.d("KarwanScraper", "Loading URL: " + channelUrl);
                webView.loadUrl(channelUrl);
                
                // Timeout after 15 seconds
                handler.postDelayed(() -> {
                    if (!hasFinished) {
                        hasFinished = true;
                        callback.onError("Timeout fetching stream URL");
                    }
                }, 15000);

            } catch (Exception e) {
                if (!hasFinished) {
                    hasFinished = true;
                    callback.onError(e.getMessage());
                }
            }
        });
    }
}
