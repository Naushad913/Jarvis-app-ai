package com.jarvis.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 7;

    private WebView web;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean active = false;
    private String pendingListenLang = null;
    private String pendingText = null;
    private float pendingRate = 1f;
    private String pendingLang = "en-US";
    private String pendingId = "0";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#04070c"));
        getWindow().setNavigationBarColor(Color.parseColor("#04070c"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#04070c"));
        setContentView(web);

        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, u));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                return false;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidVoice");
        web.loadUrl("file:///android_asset/index.html");

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { emit("tts", "start:" + id); }
                    @Override public void onDone(String id) { emit("tts", "done:" + id); }
                    @Override public void onError(String id) { emit("tts", "done:" + id); }
                    @Override public void onStop(String id, boolean interrupted) { emit("tts", "done:" + id); }
                });
                if (pendingText != null) {
                    String t = pendingText;
                    pendingText = null;
                    doSpeak(t, pendingRate, pendingLang, pendingId);
                }
            }
        });
    }

    /** Send an event to the page. Safe to call from any thread. */
    private void emit(String type, String arg) {
        final String js = "window.__jv&&window.__jv.ev(" + JSONObject.quote(type) + "," + JSONObject.quote(arg) + ")";
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript(js, null);
        });
    }

    private void finishListening() {
        if (active) {
            active = false;
            emit("end", "");
        }
    }

    private void beginListening(String lang) {
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            emit("error", "service-not-allowed");
            emit("end", "");
            return;
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { emit("start", ""); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int eventType, Bundle params) { }

            @Override
            public void onPartialResults(Bundle partial) {
                ArrayList<String> list = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) emit("partial", list.get(0));
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) emit("final", list.get(0));
                finishListening();
            }

            @Override
            public void onError(int error) {
                String code;
                switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        code = "no-speech";
                        break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        code = "not-allowed";
                        break;
                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        code = "network";
                        break;
                    case SpeechRecognizer.ERROR_AUDIO:
                        code = "audio-capture";
                        break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        code = "aborted";
                        break;
                    default:
                        code = "error-" + error;
                }
                emit("error", code);
                finishListening();
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        active = true;
        recognizer.startListening(intent);
    }

    private void doSpeak(String text, float rate, String lang, String id) {
        try {
            tts.setLanguage(Locale.forLanguageTag(lang));
        } catch (Exception ignored) {
        }
        tts.setSpeechRate(rate);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MIC) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted && pendingListenLang != null) {
            String lang = pendingListenLang;
            pendingListenLang = null;
            beginListening(lang);
        } else {
            pendingListenLang = null;
            emit("error", "not-allowed");
            emit("end", "");
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /** Methods the page calls as window.AndroidVoice.*  (they arrive on a background thread). */
    public class Bridge {
        @JavascriptInterface
        public void startListening(final String lang) {
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    pendingListenLang = lang;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                } else {
                    beginListening(lang);
                }
            });
        }

        @JavascriptInterface
        public void stopListening() {
            runOnUiThread(() -> {
                if (recognizer != null) recognizer.stopListening();
            });
        }

        @JavascriptInterface
        public void cancelListening() {
            runOnUiThread(() -> {
                if (recognizer != null) recognizer.cancel();
                finishListening();
            });
        }

        @JavascriptInterface
        public void speak(final String text, final float rate, final String lang, final String id) {
            runOnUiThread(() -> {
                if (!ttsReady) {
                    pendingText = text;
                    pendingRate = rate;
                    pendingLang = lang;
                    pendingId = id;
                    return;
                }
                doSpeak(text, rate, lang, id);
            });
        }

        @JavascriptInterface
        public void stopSpeaking() {
            runOnUiThread(() -> {
                if (tts != null && ttsReady) tts.stop();
            });
        }

        @JavascriptInterface
        public void keepScreenOn(final boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }
    }
}
