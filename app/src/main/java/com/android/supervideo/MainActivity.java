package com.android.supervideo;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
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
import android.widget.ProgressBar;
import android.widget.Toast;

import com.tencent.bugly.crashreport.CrashReport;
import com.tencent.rtmp.ITXLivePlayListener;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePlayConfig;
import com.tencent.rtmp.TXLivePlayer;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements ITXLivePlayListener {

    public static final int SOUND_HEJU = 1;

    private TXLivePlayer mLivePlayer = null;
    private boolean mIsPlaying;
    private TXCloudVideoView mPlayerView;
    private ProgressBar progressBar;
    private TXLivePlayConfig mPlayConfig;

    private WebView mWebView;

    private static final float CACHE_TIME_FAST = 1.0f;
    private static final float CACHE_TIME_SMOOTH = 5.0f;


    public static final String DOMAIN_URL = "http://app.wf0101.com/";


    private long mStartPlayTS = 0;

    //private AlertDialog alertDialog;

    private String playUrl;

    private long mTotalLossTime = 0;
    private long pauseTime = 0;

    private int currentOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;

    private int mPlayType = TXLivePlayer.PLAY_TYPE_LIVE_FLV;

    private int[] resIds = {
            R.raw.sound_1,
            R.raw.sound_2,
            R.raw.sound_3,
            R.raw.sound_4,
            R.raw.sound_5,
            R.raw.sound_6,
            R.raw.sound_7,
            R.raw.sound_8,
            R.raw.sound_9,
            R.raw.sound_10,
            R.raw.sound_11,
            R.raw.sound_12,
            R.raw.sound_13,
            R.raw.sound_14,
            R.raw.sound_15,
            R.raw.sound_16,
            R.raw.sound_17,
            R.raw.sound_18,
            R.raw.sound_19
    };

    SoundPool soundPool;
    HashMap<Integer, Integer> musicId = new HashMap<Integer, Integer>();

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.d("<gbb>", "------handle 接收到消息，重启");
            Log.d("<gbb>", "------handle 接收到消息，重启");
            Log.d("<gbb>", "------handle 接收到消息，重启");

            restartPlay();
        }
    };


    private ValueCallback<Uri> mUploadMessage;
    public ValueCallback<Uri[]> uploadMessage;
    public static final int REQUEST_SELECT_FILE = 100;
    private final static int FILECHOOSER_RESULTCODE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CrashReport.initCrashReport(getApplicationContext(), "c66c9212fd", false);
        //无title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //全屏
        /*getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
                */
        setContentView(R.layout.activity_main);

        /*int version = android.os.Build.VERSION.SDK_INT;
        Window window = getWindow();
        if (version >= Build.VERSION_CODES.KITKAT) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }*/


        int version = android.os.Build.VERSION.SDK_INT;
        if (version >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = this.getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            this.getWindow().setAttributes(lp);
        }


        View decorView = getWindow().getDecorView();
        int systemUiVisibility = decorView.getSystemUiVisibility();
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        systemUiVisibility |= flags;
        getWindow().getDecorView().setSystemUiVisibility(systemUiVisibility);


        fullScreen();

        progressBar = findViewById(R.id.progress_bar);
        mPlayerView = findViewById(R.id.video_view);

        mIsPlaying = false;

        initPlay();


        mPlayerView.setLogMargin(12, 12, 110, 60);
        mPlayerView.showLog(false);


        //showLoadingDialog();
        //startPlay();
        iniWebView();
        initSound();
    }

    private void initSound() {
        //soundPool = new SoundPool(19, AudioManager.STREAM_MUSIC, 5);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = null;
            audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

            soundPool = new SoundPool.Builder()
                    .setMaxStreams(20)
                    .setAudioAttributes(audioAttributes)
                    .build();
        } else { // 5.0 以前
            soundPool = new SoundPool(20, AudioManager.STREAM_MUSIC, 5);  // 创建SoundPool
        }

        for (int i = 0; i < resIds.length; i++) {
            soundPool.load(MainActivity.this, resIds[i], 1);
        }


        /*soundPool.load(MainActivity.this, R.raw.sound_2, 1);
        soundPool.load(MainActivity.this, R.raw.sound_3, 1);
        soundPool.load(MainActivity.this, R.raw.sound_4, 1);
        soundPool.load(MainActivity.this, R.raw.sound_5, 1);
        soundPool.load(MainActivity.this, R.raw.sound_6, 1);
        soundPool.load(MainActivity.this, R.raw.sound_7, 1);
        soundPool.load(MainActivity.this, R.raw.sound_8, 1);
        soundPool.load(MainActivity.this, R.raw.sound_9, 1);
        soundPool.load(MainActivity.this, R.raw.sound_10, 1);
        soundPool.load(MainActivity.this, R.raw.sound_12, 1);
        soundPool.load(MainActivity.this, R.raw.sound_13, 1);
        soundPool.load(MainActivity.this, R.raw.sound_14, 1);
        soundPool.load(MainActivity.this, R.raw.sound_15, 1);
        soundPool.load(MainActivity.this, R.raw.sound_16, 1);
        soundPool.load(MainActivity.this, R.raw.sound_17, 1);
        soundPool.load(MainActivity.this, R.raw.sound_18, 1);
        soundPool.load(MainActivity.this, R.raw.sound_19, 1);*/

        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int i, int i1) {
                Toast.makeText(MainActivity.this, "OnLoadComplete", Toast.LENGTH_SHORT);
                Log.d("11111", "OnLoadComplete");
            }
        });
    }

    /*
     * @param activity
     */
    public void fullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                //5.x开始需要把颜色设置透明，否则导航栏会呈现系统默认的浅灰色
                Window window = getWindow();
                View decorView = window.getDecorView();
                //两个 flag 要结合使用，表示让应用的主体内容占用系统状态栏的空间
                int option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                decorView.setSystemUiVisibility(option);
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(Color.TRANSPARENT);
            } else {
                Window window = getWindow();
                WindowManager.LayoutParams attributes = window.getAttributes();
                int flagTranslucentStatus = WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
                int flagTranslucentNavigation = WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
                attributes.flags |= flagTranslucentStatus;
                window.setAttributes(attributes);
            }
        }
    }


    private void initPlay() {
        if (mLivePlayer == null) {
            mLivePlayer = new TXLivePlayer(this);
        }
        mPlayConfig = new TXLivePlayConfig();
        mPlayConfig.setAutoAdjustCacheTime(true);
        mPlayConfig.setMaxAutoAdjustCacheTime(CACHE_TIME_FAST);
        mPlayConfig.setMinAutoAdjustCacheTime(CACHE_TIME_FAST);
        mLivePlayer.setConfig(mPlayConfig);


        mLivePlayer.setPlayerView(mPlayerView);

        mLivePlayer.setPlayListener(this);
        // 硬件加速在1080p解码场景下效果显著，但细节之处并不如想象的那么美好：
        // (1) 只有 4.3 以上android系统才支持
        // (2) 兼容性我们目前还仅过了小米华为等常见机型，故这里的返回值您先不要太当真
        mLivePlayer.enableHardwareDecode(true); //支持硬件加速
        // 设置填充模式
        mLivePlayer.setRenderMode(TXLiveConstants.RENDER_MODE_FULL_FILL_SCREEN);
        // 设置画面渲染方向
        mLivePlayer.setRenderRotation(TXLiveConstants.RENDER_ROTATION_LANDSCAPE);
        //设置播放器缓存策略
        //这里将播放器的策略设置为自动调整，调整的范围设定为1到4s，您也可以通过setCacheTime将播放器策略设置为采用
        //固定缓存时间。如果您什么都不调用，播放器将采用默认的策略（默认策略为自动调整，调整范围为1到4s）
        //mLivePlayer.setCacheTime(5);
    }

    private int index = 0;

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("确定要退出？")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        stopPlay();
                        MainActivity.this.finish();
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .show();

    }


    private boolean startPlay() {
        showLoadingDialog();
        if (!checkPlayUrl(playUrl)) {
            dismissLoadingDialog();
            return false;
        }
        mLivePlayer.startPlay(playUrl, mPlayType); // result返回值：0 success;  -1 empty url; -2 invalid url; -3 invalid playType;
        Log.w("video render", "timetrack start play");
        mStartPlayTS = System.currentTimeMillis();
        return true;
    }


    @Override
    protected void onDestroy() {
        stopPlay();
        soundPool.release();
        soundPool = null;
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
        if (!checkPlayUrl(playUrl)) {
            return;
        }
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
        try {
            progressBar.setVisibility(View.VISIBLE);



            /*if (alertDialog != null && alertDialog.isShowing()) {
                return;
            }
            if (alertDialog == null) {
                alertDialog = new AlertDialog.Builder(this).create();
            }
            alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable());
            alertDialog.setCancelable(true);
        *//*alertDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_BACK)
                    return true;
                return false;
            }
        });*//*
            alertDialog.show();
            alertDialog.setContentView(R.layout.dialog_loading);
            alertDialog.setCanceledOnTouchOutside(false);*/
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public void dismissLoadingDialog() {
        progressBar.setVisibility(View.GONE);
        /*if (null != alertDialog && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }*/
    }


    @Override
    public void onNetStatus(Bundle bundle) {

    }


    public void switchOrientation() {
        if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            currentOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        } else {
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

        mWebView.setWebChromeClient(new WebChromeClient() {

            // For 3.0+ Devices (Start)
            // onActivityResult attached before constructor
            protected void openFileChooser(ValueCallback uploadMsg, String acceptType) {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image*/*");
                startActivityForResult(Intent.createChooser(i, "File Browser"), FILECHOOSER_RESULTCODE);
            }


            // For Lollipop 5.0+ Devices
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public boolean onShowFileChooser(WebView mWebView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }

                uploadMessage = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, REQUEST_SELECT_FILE);
                } catch (ActivityNotFoundException e) {
                    uploadMessage = null;
                    Toast.makeText(getBaseContext(), "Cannot Open File Chooser", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }

            protected void openFileChooser(ValueCallback<Uri> uploadMsg) {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image*/*");
                startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);
            }
        });

        mWebView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });
        mWebView.addJavascriptInterface(new JsToJava(), "JsToJava");
        mWebView.loadUrl(DOMAIN_URL);
    }

    private class JsToJava {

        @JavascriptInterface
        public void appOrientation() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switchOrientation();
                }
            });
        }

        /**
         * 切换视频源
         *
         * @param url
         */
        @JavascriptInterface
        public void appSwitchSource(final String url) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (TextUtils.isEmpty(playUrl)) {
                        //说明是第一次播放
                        playUrl = url;
                        startPlay();
                    } else {
                        playUrl = url;
                        restartPlay();
                    }
                }
            });
        }

        /**
         * 关闭视频
         */
        @JavascriptInterface
        public void appClose() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLivePlayer.stopPlay(false);
                }
            });
        }

        @JavascriptInterface
        public void appPlaySound(final int index) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    playSound(index);
                }
            });
        }
    }


    private void playSound(int index) {
        if (index > 0 && index < 20) {
            soundPool.play(index, 1, 1, 0, 0, 1);
        }
    }


    //当然调用系统相机或相册需要回到当前页面 需要把回传值处理一下
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode == REQUEST_SELECT_FILE) {
                if (uploadMessage == null)
                    return;
                uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                uploadMessage = null;
            }
        } else if (requestCode == FILECHOOSER_RESULTCODE) {
            if (null == mUploadMessage)
                return;
            Uri result = data == null || resultCode != RESULT_OK ? null : data.getData();
            mUploadMessage.onReceiveValue(result);
            mUploadMessage = null;
        } else {
            Toast.makeText(getBaseContext(), "Failed to Upload Image", Toast.LENGTH_LONG).show();
        }
    }

}
