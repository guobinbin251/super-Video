package com.android.supervideo;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.tencent.liteav.demo.play.SuperPlayerModel;
import com.tencent.rtmp.ITXLivePlayListener;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePlayConfig;
import com.tencent.rtmp.TXLivePlayer;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements ITXLivePlayListener {

    private TXLivePlayer mLivePlayer = null;
    private boolean mIsPlaying;
    private TXCloudVideoView mPlayerView;
    private TXLivePlayConfig mPlayConfig;

    private WebView mWebView;
    private EditText editText;

    private static final float CACHE_TIME_FAST = 1.0f;
    private static final float CACHE_TIME_SMOOTH = 5.0f;

    public static final int ACTIVITY_TYPE_PUBLISH = 1;
    public static final int ACTIVITY_TYPE_LIVE_PLAY = 2;
    public static final int ACTIVITY_TYPE_VOD_PLAY = 3;
    public static final int ACTIVITY_TYPE_LINK_MIC = 4;
    public static final int ACTIVITY_TYPE_REALTIME_PLAY = 5;

    private long mStartPlayTS = 0;

    private AlertDialog alertDialog;

    private String playUrl;
    private String playUrlFirst = "rtmp://wf888.fun:10085/hls/rJgkkUBmg";
    private String playUrlSecond = "rtmp://wf888.fun:10085/hls/YwH3zUfmR";
    private String playUrlThird = "rtmp://wf888.fun:10085/hls/occPiUfmg";

    private long mTotalLossTime = 0;
    private long pauseTime = 0;

    private int currentLine = 1;
    private int currentOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;

    private int mPlayType = TXLivePlayer.PLAY_TYPE_LIVE_FLV;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.d("<gbb>","------handle 接收到消息，重启");
            Log.d("<gbb>","------handle 接收到消息，重启");
            Log.d("<gbb>","------handle 接收到消息，重启");

            restartPlay();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //无title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //全屏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);
        mPlayerView = findViewById(R.id.video_view);

        playUrl = playUrlFirst;

        mIsPlaying = false;


        if (mLivePlayer == null) {
            mLivePlayer = new TXLivePlayer(this);
        }
        mPlayerView.setLogMargin(12, 12, 110, 60);
        mPlayerView.showLog(false);

        mPlayConfig = new TXLivePlayConfig();
        mPlayConfig.setAutoAdjustCacheTime(true);
        mPlayConfig.setMaxAutoAdjustCacheTime(CACHE_TIME_FAST);
        mPlayConfig.setMinAutoAdjustCacheTime(CACHE_TIME_FAST);
        mLivePlayer.setConfig(mPlayConfig);
        showLoadingDialog();
        startPlay();
        editText = findViewById(R.id.et_url);
        iniWebView();
    }

    @Override
    public void onBackPressed() {
        stopPlay();
        super.onBackPressed();
    }


    private boolean startPlay() {

        if (!checkPlayUrl(playUrl)) {
            return false;
        }

        mLivePlayer.setPlayerView(mPlayerView);

        mLivePlayer.setPlayListener(this);
        // 硬件加速在1080p解码场景下效果显著，但细节之处并不如想象的那么美好：
        // (1) 只有 4.3 以上android系统才支持
        // (2) 兼容性我们目前还仅过了小米华为等常见机型，故这里的返回值您先不要太当真
        mLivePlayer.enableHardwareDecode(true); //支持硬件加速
        // 设置填充模式
        mLivePlayer.setRenderMode(TXLiveConstants.RENDER_MODE_ADJUST_RESOLUTION);
        // 设置画面渲染方向
        mLivePlayer.setRenderRotation(TXLiveConstants.RENDER_ROTATION_PORTRAIT);
        //设置播放器缓存策略
        //这里将播放器的策略设置为自动调整，调整的范围设定为1到4s，您也可以通过setCacheTime将播放器策略设置为采用
        //固定缓存时间。如果您什么都不调用，播放器将采用默认的策略（默认策略为自动调整，调整范围为1到4s）
        //mLivePlayer.setCacheTime(5);
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Referer", "qcloud.com");
        mPlayConfig.setHeaders(headers);

        mLivePlayer.setConfig(mPlayConfig);
        int result = mLivePlayer.startPlay(playUrl, mPlayType); // result返回值：0 success;  -1 empty url; -2 invalid url; -3 invalid playType;

        Log.w("video render", "timetrack start play");

        mStartPlayTS = System.currentTimeMillis();

        return true;
    }


    @Override
    protected void onDestroy() {
        stopPlay();
        super.onDestroy();
    }

    private void stopPlay() {
        if (mLivePlayer != null) {
            mLivePlayer.stopRecord();
            mLivePlayer.setPlayListener(null);
            mLivePlayer.stopPlay(true);
        }
        mIsPlaying = false;
    }

    public void restartPlay() {
        showLoadingDialog();
        mLivePlayer.stopPlay(false);
        mLivePlayer.startPlay(playUrl, mPlayType);
        pauseTime = 0;
        mTotalLossTime = 0;
    }

    private boolean checkPlayUrl(final String playUrl) {
        if (TextUtils.isEmpty(playUrl) || (!playUrl.startsWith("http://") && !playUrl.startsWith("https://") && !playUrl.startsWith("rtmp://") && !playUrl.startsWith("/"))) {
            Toast.makeText(getApplicationContext(), "播放地址不合法，直播目前仅支持rtmp,flv播放方式!", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (playUrl.startsWith("rtmp://")) {
            mPlayType = TXLivePlayer.PLAY_TYPE_LIVE_RTMP;
        } else if ((playUrl.startsWith("http://") || playUrl.startsWith("https://")) && playUrl.contains(".flv")) {
            mPlayType = TXLivePlayer.PLAY_TYPE_LIVE_FLV;
        } else {
            Toast.makeText(getApplicationContext(), "播放地址不合法，直播目前仅支持rtmp,flv播放方式!", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    @Override
    public void onPlayEvent(int event, Bundle param) {
        String playEventLog = "receive event: " + event + ", " + param.getString(TXLiveConstants.EVT_DESCRIPTION);
        Log.d("<gbb>", playEventLog);

        if (event == TXLiveConstants.PLAY_EVT_PLAY_BEGIN) {

            if (mHandler.hasMessages(0)) {
                mHandler.removeMessages(0);
            }
            Log.d("<gbb>", "----------begin---------");
            Log.d("<gbb>", "PlayFirstRender,cost=" + (System.currentTimeMillis() - mStartPlayTS));
            /*if (pauseTime > 0) {
                long waitTime = System.currentTimeMillis() - pauseTime;
                mTotalLossTime += waitTime;
            }*/

            long waitTime = System.currentTimeMillis() - pauseTime;
            Log.d("<gbb>", "----------waitTime=" + waitTime);
            Log.d("<gbb>", "--1--------mTotalLossTime=" + mTotalLossTime);
            if (mTotalLossTime > 3000) {
                restartPlay();
            }

        } else if (event == TXLiveConstants.PLAY_ERR_NET_DISCONNECT || event == TXLiveConstants.PLAY_EVT_PLAY_END) {
            stopPlay();
        } else if (event == TXLiveConstants.PLAY_EVT_PLAY_LOADING) {
            pauseTime = System.currentTimeMillis();
            Log.d("<gbb>", "--2--------mTotalLossTime=" + mTotalLossTime);
            if (mTotalLossTime > 3000) {
                restartPlay();
            }
            if (mHandler.hasMessages(0)) {
                mHandler.removeMessages(0);
            }
            mHandler.sendEmptyMessageDelayed(0, 3000 - mTotalLossTime);
        } else if (event == TXLiveConstants.PLAY_EVT_RCV_FIRST_I_FRAME) {
            dismissLoadingDialog();
            Log.d("<gbb>", "----------FIRST_I_FRAME---------");
        } else if (event == TXLiveConstants.PLAY_EVT_CHANGE_ROTATION) {
            return;
        } else if (event == TXLiveConstants.PLAY_WARNING_VIDEO_PLAY_LAG) {
            String description = param.getString(TXLiveConstants.EVT_DESCRIPTION);
            if (description.endsWith("ms")) {
                if (mHandler.hasMessages(0)) {
                    mHandler.removeMessages(0);
                }
                String regEx = "[^0-9]";
                Pattern p = Pattern.compile(regEx);
                Matcher m = p.matcher(description);
                String trim = m.replaceAll("").trim();
                long waitTime = Long.valueOf(trim);
                Log.d("<gbb>", "wait time is" + waitTime);
                mTotalLossTime += waitTime;
                Log.d("<gbb>", "--2--------mTotalLossTime=" + mTotalLossTime);
                if (mTotalLossTime > 3000) {
                    restartPlay();
                }
            }
            String aaa = "receive event: " + event + ", " + param.getString(TXLiveConstants.EVT_DESCRIPTION);
        }

        if (event < 0) {
            Toast.makeText(getApplicationContext(), param.getString(TXLiveConstants.EVT_DESCRIPTION), Toast.LENGTH_SHORT).show();
        }
    }

    public void showLoadingDialog() {
        try{
            if (alertDialog != null && alertDialog.isShowing()) {
                return;
            }
            if (alertDialog == null) {
                alertDialog = new AlertDialog.Builder(this).create();
            }
            alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable());
            alertDialog.setCancelable(true);
        /*alertDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_BACK)
                    return true;
                return false;
            }
        });*/
            alertDialog.show();
            alertDialog.setContentView(R.layout.dialog_loading);
            alertDialog.setCanceledOnTouchOutside(false);
        }catch (Exception ex){
            ex.printStackTrace();
        }

    }

    public void dismissLoadingDialog() {
        if (null != alertDialog && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
    }


    @Override
    public void onNetStatus(Bundle bundle) {

    }

    public void switchFirst(View view){
        if(currentLine != 1){
            currentLine = 1;
            playUrl = playUrlFirst;
            restartPlay();
        }

    }

    public void switchSecond(View view){
        if(currentLine != 2){
            currentLine = 2;
            playUrl = playUrlSecond;
            restartPlay();
        }
    }

    public void switchThird(View view){
        if(currentLine != 3){
            currentLine = 3;
            playUrl = playUrlThird;
            restartPlay();
        }
    }

    public void goWeb(View view){
        mWebView.loadUrl(editText.getText().toString());
    }


    public void switchOrientation(View view){
        if(currentOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT){
            currentOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }else{
            currentOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        setRequestedOrientation(currentOrientation);
    }


    private void iniWebView() {
        mWebView = findViewById(R.id.webview);

        WebSettings webSettings = mWebView.getSettings();
        //如果访问的页面中要与Javascript交互，则webview必须设置支持Javascript
        webSettings.setJavaScriptEnabled(true);
        //支持插件
        //webSettings.setPluginsEnabled(true);

        mWebView.setBackgroundColor(0); // 设置背景色
        mWebView.getBackground().setAlpha(0); // 设置填充透明度 范围：0-255

        //设置自适应屏幕，两者合用
        webSettings.setUseWideViewPort(true); //将图片调整到适合webview的大小
        webSettings.setLoadWithOverviewMode(true); // 缩放至屏幕的大小
        webSettings.setDomStorageEnabled(true);
        //缩放操作
        webSettings.setSupportZoom(true); //支持缩放，默认为true。是下面那个的前提。
        webSettings.setBuiltInZoomControls(true); //设置内置的缩放控件。若为false，则该WebView不可缩放
        webSettings.setDisplayZoomControls(false); //隐藏原生的缩放控件
        webSettings.setDomStorageEnabled(true);

        //其他细节操作WebView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
//        webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        webSettings.setAllowFileAccess(true); //设置可以访问文件
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true); //支持通过JS打开新窗口
        webSettings.setLoadsImagesAutomatically(true); //支持自动加载图片
        webSettings.setDefaultTextEncodingName("utf-8");//设置编码格式

        //syncCookie(WebActivity.this, mUrl);

        mWebView.setWebViewClient(new WebViewClient() {


            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                //syncCookie(WebActivity.this, url);
                mWebView.loadUrl(url);
                return true;

            }


            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
            }

            //load页面失败的时候
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
            }

            @Override
            public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
                super.onReceivedSslError(view, handler, error);
                handler.proceed();

            }

            @Override
            public void onPageFinished(WebView view, String url) {

                super.onPageFinished(view, url);
            }
        });

        mWebView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });
        mWebView.addJavascriptInterface(new JsToJava(), "JsToJava");
    }

    private class JsToJava {

        @JavascriptInterface
        public void appOrientation() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switchOrientation(null);
                }
            });
        }

        @JavascriptInterface
        public void appFirst() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switchFirst(null);
                }
            });
        }
        @JavascriptInterface
        public void appSecond() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switchSecond(null);
                }
            });
        }
        @JavascriptInterface
        public void appThird() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switchThird(null);
                }
            });
        }
    }
}
