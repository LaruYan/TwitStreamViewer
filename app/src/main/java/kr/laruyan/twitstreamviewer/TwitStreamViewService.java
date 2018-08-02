package kr.laruyan.twitstreamviewer;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import twitter4j.MediaEntity;
import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.URLEntity;
import twitter4j.conf.ConfigurationBuilder;

public class TwitStreamViewService extends Service {
    public TwitStreamViewService() {
    }
    private static final boolean isDebug = MainActivity.isDebug;

    private static final String CONSUMER_KEY = MainActivity.CONSUMER_KEY;
    private static final String CONSUMER_SECRET = MainActivity.CONSUMER_SECRET;
    private static final String PREF_KEY_ACCESS_TOKEN = MainActivity.PREF_KEY_ACCESS_TOKEN;
    private static final String PREF_KEY_ACCESS_SECRET = MainActivity.PREF_KEY_ACCESS_SECRET;
    private static final String EXTRA_KEY_EDGEMODE = MainActivity.EXTRA_KEY_EDGEMODE;

    //private Twitter twitter = null;
    private WindowManager windowManager = null;
    //private AudioManager audioManager = null;

    private TwitterStream twitterStream = null;
    private StatusListener listener = null;
    private RelativeLayout relativeLayout_floating = null;
    private ScrollView scrollView_floating = null;
    private LinearLayout linearLayout_floating = null;

    //private Thread threadDownloadImages = null;
    private Handler handlerDownloadImages = null;

    private int popupAreaWidth = 0;
    private int popupAreaHeight = 0;
    private boolean isLowRamDevice = false;
    private boolean isRunning = false;
    private boolean isEdge = false;

    private static final long DELAY_UI_ATTACH = 250;
    private static final long DELAY_SELF_DESTRUCT_VIEW = 10000;


    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int returnOnStartCommand = super.onStartCommand(intent, flags, startId);

        if(!isRunning) {
            HandlerThread threadDownloadImages = new HandlerThread("DownloadImageThread");
            threadDownloadImages.start();
            handlerDownloadImages = new Handler(threadDownloadImages.getLooper());
            isEdge = intent.getBooleanExtra(EXTRA_KEY_EDGEMODE, false);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String oAuthAccessToken = prefs.getString(PREF_KEY_ACCESS_TOKEN, null);
            String oAuthAccessSecret = prefs.getString(PREF_KEY_ACCESS_SECRET, null);
            prefs = null;
            ConfigurationBuilder cb = new ConfigurationBuilder();
            cb//.setDebugEnabled(true)
                    .setOAuthConsumerKey(CONSUMER_KEY)
                    .setOAuthConsumerSecret(CONSUMER_SECRET)
                    .setOAuthAccessToken(oAuthAccessToken)
                    .setOAuthAccessTokenSecret(oAuthAccessSecret);

            if(isEdge){
                if(isDebug){
                    System.out.println("It's Edge mode");
                }
                initializeEdgeView();
                return returnOnStartCommand;
            }else {
                if(isDebug){
                    System.out.println("It's Normal mode");
                }
                initializeHomeView();
            }

            if (listener == null) {
                listener = new StatusListener() {
                    public void onStatus(Status status) {
                        if (isDebug) {
                            System.out.println(status.getUser().getName() + " : " + status.getText());
                        }
                        if(isEdge){
                            attachEdgeView(status);
                        }else {
                            attachTweetView(status);
                        }
                    }

                    public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
                    }

                    public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
                    }

                    public void onScrubGeo(long l, long l2) {
                    }

                    public void onStallWarning(StallWarning stallWarning) {
                    }

                    public void onException(Exception ex) {
                        ex.printStackTrace();
                    }
                };
            }
            if (twitterStream == null) {
                twitterStream = new TwitterStreamFactory(cb.build()).getInstance();

            }
            twitterStream.addListener(listener);
            // sample() method internally creates a thread which manipulates TwitterStream and calls these adequate listener methods continuously.
            twitterStream.user();

            isRunning = true;
        }

        return returnOnStartCommand;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(handlerDownloadImages != null){
            handlerDownloadImages.removeCallbacksAndMessages(null);
        }
        if(twitterStream != null) {
            twitterStream.shutdown();
        }
        if(isEdge) {
            if (relativeLayout_floating != null && windowManager != null) {
                windowManager.removeView(relativeLayout_floating);
            }
        }else {
            if (scrollView_floating != null && windowManager != null) {
                windowManager.removeView(scrollView_floating);
            }
        }
    }

    private void initializeHomeView(){
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        popupAreaWidth = (int)((2f/3f)*calculateSmallestWidth(windowManager));

        linearLayout_floating = new LinearLayout(this);
        //linearLayout_floating.setBackgroundColor(0x99FF0000);
        final LinearLayout.LayoutParams paramsLL = new LinearLayout.LayoutParams(
                //300,300
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        //paramsLL.resolveLayoutDirection(LinearLayout.VERTICAL);
        linearLayout_floating.setOrientation(LinearLayout.VERTICAL);
        //linearLayout_floating.setDividerPadding((int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()));
        //if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        linearLayout_floating.setDividerDrawable(getResources().getDrawable(R.drawable.divider_between_items));
        //}
        linearLayout_floating.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        //paramsLL.gravity = Gravity.TOP | Gravity.LEFT;

        scrollView_floating = new ScrollView(this);
        //scrollView_floating.setBackgroundColor(0x9900FF00);
        final WindowManager.LayoutParams paramsSV = new WindowManager.LayoutParams(
                //WindowManager.LayoutParams.MATCH_PARENT,
                popupAreaWidth,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);

        paramsSV.gravity = Gravity.TOP | Gravity.END;
        paramsSV.x = 0;
        paramsSV.y = 0;

        scrollView_floating.addView(linearLayout_floating, paramsLL);

        //넣어야할 기능인데 이거 때문에 제대로 작동하지 않는듯 하다아..
        scrollView_floating.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                scrollView_floating.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView_floating.fullScroll(View.FOCUS_DOWN);
                        //scrollView_floating.smoothScrollTo(0, scrollView_floating.getMaxScrollAmount());
                        //System.out.println("Scroll Change: "+scrollView_floating.getMaxScrollAmount());
                    }
                });
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            //checkLowRamDevice

            ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            isLowRamDevice = activityManager.isLowRamDevice();
            activityManager = null;
            if (isLowRamDevice) {
                if (isDebug) {
                    System.out.println("this is LowRamDevice! you should get another workaround");
                }
                //scrollView_floating.setBackgroundColor(0x42FF0000);
            } else {
                scrollView_floating.setAlpha(0.66667f);
            }
        }

        windowManager.addView(scrollView_floating, paramsSV);
    }

    private void initializeEdgeView(){
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        popupAreaWidth = calculateSmallestWidth(windowManager);
        popupAreaHeight = calculateSoftKeyHeight(windowManager);
        System.out.println(popupAreaWidth +" x "+ popupAreaHeight);
        //TODO:chainfire님은 이걸 어떻게 하셨을까.. 해답은 사이즈 수동지정인걸까.
        relativeLayout_floating = new RelativeLayout(this);
        final WindowManager.LayoutParams paramsRL = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                //public static final int FIRST_SYSTEM_WINDOW = 2000;
                //public static final int TYPE_NAVIGATION_BAR = FIRST_SYSTEM_WINDOW+19;
                //public static final int TYPE_BOOT_PROGRESS = FIRST_SYSTEM_WINDOW+21;
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        paramsRL.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        relativeLayout_floating.setBackgroundColor(0x9900FF00);

        scrollView_floating = new ScrollView(this);
        //scrollView_floating.setBackgroundColor(0x9900FF00);
        final RelativeLayout.LayoutParams paramsSV = new RelativeLayout.LayoutParams(
                popupAreaWidth,
                popupAreaHeight
        );
        relativeLayout_floating.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if(right > bottom) {
                    //is landscape mode
                    scrollView_floating.setRotation(-(90f / 360f));
                }else{
                    //is portrait mode
                    scrollView_floating.setRotation(0f);
                }
            }
        });
        scrollView_floating.setBackgroundColor(0x990000FF);
        relativeLayout_floating.addView(scrollView_floating,paramsSV);

        linearLayout_floating = new LinearLayout(this);
        linearLayout_floating.setBackgroundColor(0x99FF0000);
        final LinearLayout.LayoutParams paramsLL = new LinearLayout.LayoutParams(
                //300,300
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        linearLayout_floating.setOrientation(LinearLayout.VERTICAL);

        scrollView_floating.addView(linearLayout_floating,paramsLL);


        windowManager.addView(relativeLayout_floating, paramsRL);
    }

    private void attachTweetView(Status status){

        try {
            Looper.prepare();
        }catch(RuntimeException re){
            if(isDebug) {
                re.printStackTrace();
            }
        }

        final View inflatedView =// new View(this);
                View.inflate(this, R.layout.item_tweet_no_picture, null);

        final LinearLayout llActivityMini = (LinearLayout) inflatedView.findViewById(R.id.linearLayoutActivityProfile);
        final ImageView ivActivityType = (ImageView) inflatedView.findViewById(R.id.imageView_ActivityType);
        final TextView tvActivityPerson = (TextView) inflatedView.findViewById(R.id.textView_ActivityTwitterNameAndID);

        final ImageView ivProfileMini = (ImageView) inflatedView.findViewById(R.id.imageView_ProfileMini);
        final TextView tvTwitterNameAndId = (TextView) inflatedView.findViewById(R.id.textView_TwitterNameAndID);
        final TextView tvTwitterStatus = (TextView) inflatedView.findViewById(R.id.textView_TwitterStatus);

        final SurfaceView surfaceView_VideoTweet = (SurfaceView) inflatedView.findViewById(R.id.surfaceView_VideoTweet);
        //final WebView webView_VideoTweet = (WebView) inflatedView.findViewById(R.id.webView_VideoTweet);
        //final WebView webView_VideoTweet = new WebView(this);

        //final UnFocusedVideoView ufVideoView_VideoTweet = (UnFocusedVideoView) inflatedView.findViewById(R.id.ufVideoView_VideoTweet);
        //final VideoView videoView_VideoTweet = (VideoView) inflatedView.findViewById(R.id.videoView_VideoTweet);

        final LinearLayout llMediaPanel = (LinearLayout) inflatedView.findViewById(R.id.linearLayoutMediaContent);
        //final LinearLayout llMediaPart1 = (LinearLayout) inflatedView.findViewById(R.id.linearLayoutMediaPart1);
        final ImageView ivMedia1 = (ImageView) inflatedView.findViewById(R.id.imageView_Media1);
        final ImageView ivMedia2 = (ImageView) inflatedView.findViewById(R.id.imageView_Media2);
        final LinearLayout llMediaPart2 = (LinearLayout) inflatedView.findViewById(R.id.linearLayoutMediaPart2);
        final ImageView ivMedia3 = (ImageView) inflatedView.findViewById(R.id.imageView_Media3);
        final ImageView ivMedia4 = (ImageView) inflatedView.findViewById(R.id.imageView_Media4);

        llMediaPanel.setLayoutParams(new LinearLayout.LayoutParams(
                popupAreaWidth,
                popupAreaWidth / 2
        ));
        //final TextView tv_new = new TextView(getApplicationContext());
        //tv_new.setText(status.getUser().getName() + " : " + status.getText());
        //tv_new.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);

        //String imageUrl = status.getUser().getMiniProfileImageURLHttps();

        if(isLowRamDevice){
            //lowRamDevice workaround
            inflatedView.setBackgroundColor(0xAAFF0000);
        }else{
            inflatedView.setBackgroundColor(0xFFFF0000);
        }


        //int dimen = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        //inflatedView.setPaddingRelative(dimen,dimen,dimen,dimen);
        if(status.isRetweet()) {
            ivActivityType.setImageResource(R.drawable.ic_tweet_retweet);
            tvActivityPerson.setText(status.getUser().getName() + " @" + status.getUser().getScreenName());
            llActivityMini.setVisibility(View.VISIBLE);
            status = status.getRetweetedStatus();
        }   /*Status retweetedStatus = status.getRetweetedStatus();
            //ivProfileMini.setImageBitmap(checkCacheAndReturnBitmap(retweetedStatus.getUser().getMiniProfileImageURLHttps()));
            checkCacheAndSetBitmap(ivProfileMini, retweetedStatus.getUser().getMiniProfileImageURLHttps(),retweetedStatus.getUser().getId());
            tvTwitterNameAndId.setText(retweetedStatus.getUser().getName() + " @" + retweetedStatus.getUser().getScreenName());
            tweetContent = parseTcoTweet(retweetedStatus);
        }else{
            //ivProfileMini.setImageBitmap(checkCacheAndReturnBitmap(status.getUser().getMiniProfileImageURLHttps()));
            checkCacheAndSetBitmap(ivProfileMini, status.getUser().getMiniProfileImageURLHttps(),status.getUser().getId());
            tvTwitterNameAndId.setText(status.getUser().getName()+" @"+status.getUser().getScreenName());
            tweetContent = parseTcoTweet(status);
        }*/

        checkCacheAndSetBitmap(ivProfileMini, status.getUser().getMiniProfileImageURLHttps(),status.getUser().getId());
        tvTwitterNameAndId.setText(status.getUser().getName()+" @"+status.getUser().getScreenName());
        tvTwitterStatus.setText(parseTcoTweet(status));

        //set Text Area.. if empty visibility gone
        if(tvTwitterStatus.getText().length()<1){
            tvTwitterStatus.setVisibility(View.GONE);
        }

        //check if media exists
        String[] mediaUrls = getMediaEntitiesSmall(status.getExtendedMediaEntities());//getMediaEntities();
        //int mediaHasMany = mediaEntities.length;

        int videoCursor = 0;
        if(mediaUrls.length == 0) {
            //no official media upload found
            String[] fullUrls = parseTcoToFullUrls(status);
            for(int i = fullUrls.length-1; i>=0;i--){
                //check last third party media URL
                //02-17 14:00:53.321  21075-21167/kr.laruyan.twitstreamviewer I/System.out﹕ URL: URLEntityJSONImpl{url='http://t.co/**********', expandedURL='http://vine.co/v/***********', displayURL='vine.co/v/***********'}
                //02-17 14:02:12.578  21075-21167/kr.laruyan.twitstreamviewer I/System.out﹕ URL: URLEntityJSONImpl{url='http://t.co/**********', expandedURL='http://youtu.be/***********?t=7s', displayURL='youtu.be/***********?t=…'}
                if(fullUrls[i].contains("youtube.com/watch?v=")){
                    //this url has browser-pasted youtube address
                    //setWebViewYoutubeEmbed(webView_VideoTweet,fullUrls[i]);
                    break;
                }else if(fullUrls[i].contains("youtu.be/")){
                    //this url has shortened youtube address
                    //setWebViewYoutubeEmbed(webView_VideoTweet, fullUrls[i]);
                    break;
                }else if(fullUrls[i].contains("vine.co/v/")){
                    //this url has vine address
                    checkCacheAndSetVideo(surfaceView_VideoTweet, fullUrls[i], 0);
                    break;
                }
            }

        } else if((videoCursor = checkIfMediaHasVideo(mediaUrls))<mediaUrls.length){
            //아직 움짤은 하나밖에 표시되지 않으니까 이 트릭을 써보자.. 첫번째 미디어부터 뜨도록 되어있어.
            // it's video!
            // RAW Video: https://pbs.twimg.com/tweet_video/***************.mp4
            // This tweet has media1/1: https://pbs.twimg.com/tweet_video_thumb/***************.png
            mediaUrls[videoCursor] = parseOfficialVideoLink(mediaUrls[videoCursor]);

            //surfaceView_VideoTweet
            checkCacheAndSetVideo(surfaceView_VideoTweet,mediaUrls[videoCursor],0);

            /*
            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                checkCacheAndSetVideoAPI21(ufVideoView_VideoTweet, mediaUrls[0]);
            }else{
                checkCacheAndSetVideo(videoView_VideoTweet, mediaUrls[0]);
            }*/

        }else {

            //공식 미디어 갯수가 16개까지 일 수도 있고 비디오가 거기 끼어있을 수도 있다.
            //공식앱의 대응은 비디오를 우선시하고 없는경우 첫번째 미디어를 우선시한다.
            switch (mediaUrls.length) {
                case 0:
                    break;
                case 1:
                    llMediaPanel.setVisibility(View.VISIBLE);
                    checkCacheAndSetBitmap(ivMedia1, mediaUrls[0],0);
                    break;
                case 2:
                    llMediaPanel.setVisibility(View.VISIBLE);
                    checkCacheAndSetBitmap(ivMedia1, mediaUrls[0],0);
                    llMediaPart2.setVisibility(View.VISIBLE);
                    checkCacheAndSetBitmap(ivMedia3, mediaUrls[1],0);
                    break;
                case 3:
                    llMediaPanel.setVisibility(View.VISIBLE);
                    checkCacheAndSetBitmap(ivMedia1, mediaUrls[0],0);
                    llMediaPart2.setVisibility(View.VISIBLE);
                    checkCacheAndSetBitmap(ivMedia3, mediaUrls[1],0);
                    checkCacheAndSetBitmap(ivMedia4, mediaUrls[2],0);
                    break;
                default:
                case 4:
                    llMediaPanel.setVisibility(View.VISIBLE);
                    checkCacheAndSetBitmap(ivMedia1, mediaUrls[0],0);
                    checkCacheAndSetBitmap(ivMedia2, mediaUrls[1],0);
                    llMediaPart2.setVisibility(View.VISIBLE);
                    checkCacheAndSetBitmap(ivMedia3, mediaUrls[2],0);
                    checkCacheAndSetBitmap(ivMedia4, mediaUrls[3],0);
                    break;
            }
        }
        mediaUrls = null;

        linearLayout_floating.post(new Runnable() {
            @Override
            public void run() {
                linearLayout_floating.addView(inflatedView);

                scrollView_floating.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView_floating.fullScroll(View.FOCUS_DOWN);
                        //scrollView_floating.smoothScrollTo(0,scrollView_floating.getMaxScrollAmount());
                        //System.out.println("Scroll Add: "+scrollView_floating.getMaxScrollAmount());
                    }
                });

            }
        });
        linearLayout_floating.postDelayed(new Runnable() {

            @Override
            public void run() {
                linearLayout_floating.removeView(inflatedView);
                //llActivityMini
                ivActivityType.setImageBitmap(null);
                tvActivityPerson.setText(null);

                ivProfileMini.setImageBitmap(null);
                tvTwitterNameAndId.setText(null);
                tvTwitterStatus.setText(null);

                //surfaceView_VideoTweet.
                //ufVideoView_VideoTweet.setVideoPath("");
                //videoView_VideoTweet.setVideoPath("");
                //webView_VideoTweet.loadUrl("about:blank");
                //llMediaPanel
                //llMediaPart1
                ivMedia1.setImageBitmap(null);
                ivMedia2.setImageBitmap(null);
                //llMediaPart2
                ivMedia3.setImageBitmap(null);
                ivMedia4.setImageBitmap(null);
            }
        }, DELAY_SELF_DESTRUCT_VIEW);
    }

    private void attachEdgeView(Status status){
        //
    }

    private static final int calculateSmallestWidth(WindowManager windowManager){
        Display display =  windowManager.getDefaultDisplay();

        Point size = new Point();
        display.getRealSize(size);

        if(isDebug){
            System.out.println("SmallestWidth: Display Size - x: " + size.x + " / y: " + size.y);
        }

        return size.x < size.y ? size.x : size.y;
    }

    private static final int calculateSoftKeyHeight(WindowManager windowManager){
        Display display =  windowManager.getDefaultDisplay();

        Point displaySize = new Point();
        display.getRealSize(displaySize);

        Point displayExcludingDecorSize = new Point();
        display.getSize(displayExcludingDecorSize);

        Point size = new Point();
        size.x = displaySize.x - displayExcludingDecorSize.x;
        size.y = displaySize.y - displayExcludingDecorSize.y;

        if(isDebug){
            System.out.println("Softkey: Display Size - x: "+size.x+" / y: "+size.y);
        }

        return size.x > size.y ? size.x : size.y;
    }

    private void checkCacheAndSetBitmap(final ImageView iv, final String url,final long id){
        if(iv == null){
            if(isDebug) {
                System.out.println("ImageView is Null");
            }
            return ;
        }

        final TrialWaitMemoObject wait = new TrialWaitMemoObject();
        handlerDownloadImages.post(new Runnable() {
            @Override
            public void run() {
                //boolean cacheHit = false;
                //check cache if there is a file there.
                String filePath = checkCacheAndDownloadFile(url, id);

                if (filePath == null) {
                    if (isDebug) {
                        System.out.println("file path is null, skipping ");
                    }
                    return;
                }

                if (iv.isAttachedToWindow()) {
                    if (isDebug) {
                        System.out.println("ImageView Attached to the Window");
                    }
                } else {
                    if (isDebug) {
                        System.out.println("ImageView Not yet Attached available Delay: " + wait.availableDelay);
                    }
                    if (wait.availableDelay > 0) {
                        handlerDownloadImages.postDelayed(this, DELAY_UI_ATTACH);
                        wait.availableDelay -= DELAY_UI_ATTACH;
                    }
                    return;
                }

                final Bitmap bitmap = BitmapFactory.decodeFile(filePath);
                if (bitmap == null) {
                    //if decode fails, delete them to redownload again later.
                    File fileTobeDeleted = new File(filePath);
                    fileTobeDeleted.delete();
                    fileTobeDeleted = null;
                }
                filePath = null;
                //final Drawable drawable = Drawable.createFromPath(filePath);


                if (iv.post(new Runnable() {
                    @Override
                    public void run() {
                        //iv.setVisibility(View.GONE);
                        if (isDebug) {
                            System.out.println("resetting BMP to ImageView");
                        }
                        //tv.setCompoundDrawables(drawable,null,null,null);
                        iv.setImageBitmap(bitmap);
                        iv.setVisibility(View.VISIBLE);
                    }
                })) {

                    if (isDebug) {
                        System.out.println("post Succeded");
                    }
                } else {
                    if (isDebug) {
                        System.out.println("post Failed");
                    }
                }
            }
        });
    }
    /*
        private void checkCacheAndSetVideoAPI21(final UnFocusedVideoView vv, final String url){
            if(vv == null){
                if(isDebug) {
                    System.out.println("UnFocusedVideoView is Null");
                }
                return ;
            }

            handlerDownloadImages.post(new Runnable() {
                @Override
                public void run() {
                    //boolean cacheHit = false;
                    //check cache if there is a file there.
                    String filePath = getApplicationContext().getExternalCacheDir()+url.substring(url.lastIndexOf("/"));
                    if( ! new File(filePath).canRead()){
                        if(isDebug) {
                            System.out.println("File " + filePath + " is not readable. downloading");
                        }
                        filePath = downloadFileFromUrl(url);
                    }

                    if(filePath == null){
                        if(isDebug){
                            System.out.println("file path is null, skipping ");
                        }
                        return ;
                    }

                    if(vv.isAttachedToWindow()){
                        if(isDebug) {
                            System.out.println("VideoView Attached to the Window");
                        }
                    }else{
                        if(isDebug) {
                            System.out.println("VideoView Not yet Attached");
                        }
                        handlerDownloadImages.postDelayed(this,DELAY_UI_ATTACH);
                        return ;
                    }

                    final String finalFilePath = filePath;

                    vv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            mp.setLooping(true);
                            vv.start();
                        }
                    });
                    vv.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            vv.resume();
                        }
                    });

                    //if(audioManager == null) {
                    //    audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    //}
                    //audioManager.abandonAudioFocus();



                    if(vv.post(new Runnable() {
                        @Override
                        public void run() {
                            //iv.setVisibility(View.GONE);
                            if(isDebug) {
                                System.out.println("setting VideoPath to VideoView: "+finalFilePath);
                            }
                            vv.setLayoutParams(new LinearLayout.LayoutParams(popupAreaWidth,popupAreaWidth/2));
                            vv.setVideoPath(finalFilePath);
                            vv.setVisibility(View.VISIBLE);
                            vv.start();

                            //tryout abandoning audiofocus..
                            //AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                            //System.out.println("abandonAudioFocus: "+audioManager.abandonAudioFocus(null));
                        }})){

                        if(isDebug) {
                            System.out.println("post Succeded");
                        }
                    }else{
                        if(isDebug) {
                            System.out.println("post Failed");
                        }
                    }
                }
            });
        }

        private void checkCacheAndSetVideo(final VideoView vv, final String url){
            if(vv == null){
                if(isDebug) {
                    System.out.println("VideoView is Null");
                }
                return ;
            }

            handlerDownloadImages.post(new Runnable() {
                @Override
                public void run() {
                    //boolean cacheHit = false;
                    //check cache if there is a file there.
                    String filePath = getApplicationContext().getExternalCacheDir()+url.substring(url.lastIndexOf("/"));
                    if( ! new File(filePath).canRead()){
                        if(isDebug) {
                            System.out.println("File " + filePath + " is not readable. downloading");
                        }
                        filePath = downloadFileFromUrl(url);
                    }

                    if(filePath == null){
                        if(isDebug){
                            System.out.println("file path is null, skipping ");
                        }
                        return ;
                    }

                    if(vv.isAttachedToWindow()){
                        if(isDebug) {
                            System.out.println("VideoView Attached to the Window");
                        }
                    }else{
                        if(isDebug) {
                            System.out.println("VideoView Not yet Attached");
                        }
                        handlerDownloadImages.postDelayed(this,DELAY_UI_ATTACH);
                        return ;
                    }

                    final String finalFilePath = filePath;

                    vv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            mp.setLooping(true);
                            vv.start();
                        }
                    });
                    vv.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            vv.resume();
                        }
                    });

                    //if(audioManager == null) {
                    //    audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    //}
                    //audioManager.abandonAudioFocus();


                    if(vv.post(new Runnable() {
                        @Override
                        public void run() {
                            //iv.setVisibility(View.GONE);
                            if(isDebug) {
                                System.out.println("setting VideoPath to VideoView: "+finalFilePath);
                            }
                            vv.setLayoutParams(new LinearLayout.LayoutParams(popupAreaWidth,popupAreaWidth/2));
                            vv.setVideoPath(finalFilePath);
                            vv.setVisibility(View.VISIBLE);
                            vv.start();

                        }})){

                        if(isDebug) {
                            System.out.println("post Succeded");
                        }
                    }else{
                        if(isDebug) {
                            System.out.println("post Failed");
                        }
                    }
                }
            });
        }
    */
    private void checkCacheAndSetVideo(final SurfaceView sv, final String url, final long id){
        if(sv == null){
            if(isDebug) {
                System.out.println("SurfaceView is Null");
            }
            return ;
        }
        final TrialWaitMemoObject wait = new TrialWaitMemoObject();
        final VineVideoURLMemoObject vineMemo = new VineVideoURLMemoObject();
        handlerDownloadImages.post(new Runnable() {
            @Override
            public void run() {
                //boolean cacheHit = false;
                //check cache if there is a file there.
                String filePath = null;
                if(url.contains("vine.co/v/")){
                    //get ready to parse vine video
                    if(vineMemo.videoUrl == null) {
                        vineMemo.videoUrl = parseVineVideoUrl(url);
                    }
                    filePath = checkCacheAndDownloadFile(vineMemo.videoUrl, id);
                }else {
                    filePath = checkCacheAndDownloadFile(url, id);
                }

                if(filePath == null){
                    if(isDebug){
                        System.out.println("file path is null, skipping ");
                    }
                    return ;
                }


                if(sv.isAttachedToWindow()){
                    if(isDebug) {
                        System.out.println("VideoView Attached to the Window");
                    }
                }else{
                    if(isDebug) {
                        System.out.println("VideoView Not yet Attached available Delay: "+wait.availableDelay);
                    }
                    if(wait.availableDelay>0) {
                        handlerDownloadImages.postDelayed(this, DELAY_UI_ATTACH);
                        wait.availableDelay -= DELAY_UI_ATTACH;
                    }
                    return ;
                }

                final String finalFilePath = filePath;
                filePath = null;

                final SurfaceHolder surfaceHolder = sv.getHolder();
                final MediaPlayer mediaPlayer = new MediaPlayer();
                final LinearLayout.LayoutParams svLayoutParams = new LinearLayout.LayoutParams(popupAreaWidth,popupAreaWidth/2);
                surfaceHolder.addCallback(new SurfaceHolder.Callback(){

                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {

                        try {
                            mediaPlayer.setDataSource(finalFilePath);
                        }catch (IOException ioe){
                            if(isDebug){
                                ioe.printStackTrace();
                            }
                            //mediaPlayer = null;
                        }
                        mediaPlayer.setDisplay(surfaceHolder);
                        mediaPlayer.prepareAsync();
                        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                            @Override
                            public void onPrepared(MediaPlayer mp) {
                                if(isDebug){
                                    System.out.println("Video is starting...");
                                }

                                // for compatibility, we adjust size based on aspect ratio
                                if ( mp.getVideoWidth() * svLayoutParams.height  < svLayoutParams.width * mp.getVideoHeight() ) {
                                    //Log.i("@@@", "image too wide, correcting");
                                    svLayoutParams.width = svLayoutParams.height * mp.getVideoWidth() / mp.getVideoHeight();
                                } else if ( mp.getVideoWidth() * svLayoutParams.height  > svLayoutParams.width * mp.getVideoHeight() ) {
                                    //Log.i("@@@", "image too tall, correcting");
                                    svLayoutParams.height = svLayoutParams.width * mp.getVideoHeight() / mp.getVideoWidth();
                                }
                                sv.post(new Runnable(){
                                    @Override
                                    public void run() {
                                        sv.setLayoutParams(svLayoutParams);
                                    }
                                });

                                mp.setLooping(true);
                                mp.start();
                            }
                        });
                    }

                    @Override
                    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                        if(isDebug){
                            System.out.println("surfaceChanged(holder, "+format+", "+width+", "+height+")");
                        }
                    }

                    @Override
                    public void surfaceDestroyed(SurfaceHolder holder) {
                        //mediaPlayer.setDataSource("");
                        mediaPlayer.release();
                    }
                });

                if(sv.post(new Runnable() {
                    @Override
                    public void run() {
                        if(isDebug) {
                            System.out.println("setting VideoPath to VideoView: "+finalFilePath);
                        }

                        sv.setLayoutParams(svLayoutParams);///
                        sv.setVisibility(View.VISIBLE);

                    }})){

                    if(isDebug) {
                        System.out.println("post Succeded");
                    }
                }else{
                    if(isDebug) {
                        System.out.println("post Failed");
                    }
                }
            }
        });
    }

    private void setWebViewYoutubeEmbed(final WebView wv, final String url) {
        if(wv == null){
            if(isDebug){
                System.out.println("WebView is Null");
            }
            return ;
        }

        final TrialWaitMemoObject wait = new TrialWaitMemoObject();
        handlerDownloadImages.post(new Runnable() {
            @Override
            public void run() {
                //boolean cacheHit = false;
                //check cache if there is a file there.
                final String webAddress = parseYouTubeGetEmbedUrl(url);

                if(webAddress == null){
                    if(isDebug){
                        System.out.println("file path is null, skipping ");
                    }
                    return ;
                }

                if(wv.isAttachedToWindow()){
                    if(isDebug) {
                        System.out.println("WebView Attached to the Window");
                    }
                }else{
                    if(isDebug) {
                        System.out.println("WebView Not yet Attached available Delay: "+wait.availableDelay);
                    }
                    if(wait.availableDelay>0) {
                        handlerDownloadImages.postDelayed(this, DELAY_UI_ATTACH);
                        wait.availableDelay -= DELAY_UI_ATTACH;
                    }
                    return ;
                }


                if(wv.post(new Runnable() {
                    @Override
                    public void run() {
                        //iv.setVisibility(View.GONE);
                        if(isDebug) {
                            System.out.println("resetting URL to WebView");
                        }
                        //tv.setCompoundDrawables(drawable,null,null,null);
                        wv.loadUrl(webAddress);
                        wv.setVisibility(View.VISIBLE);
                    }})){

                    if(isDebug) {
                        System.out.println("post Succeded");
                    }
                }else{
                    if(isDebug) {
                        System.out.println("post Failed");
                    }
                }
            }
        });

    }

    public String downloadFileFromUrl(String uri,long id)
    {
        File file=null;
        File folder=null;
        String filePath=null;
        String fileName = stripOutFileExtension(uri.substring(uri.lastIndexOf("/")+1))+id;
        if(isDebug) {
            System.out.println("File URL: "+ uri);
            System.out.println("File Name: " + fileName);
            System.out.println("File Uploader: " + id);
        }

        try {

            URL url = new URL(uri);

            //create the new connection

            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            //set up some things on the connection
            urlConnection.setRequestMethod("GET");

            //mimic browser
            //urlConnection.setRequestProperty("User-Agent","Mozilla/5.0 ( compatible ) ");
            //urlConnection.setRequestProperty("Accept","*/*");
            //urlConnection.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 6.3; Win64; x64; rv:38.0) Gecko/20100101 Firefox/38.0");

            //urlConnection.setDoOutput(true);
            urlConnection.setDoOutput(false);
            //urlConnection.setDoOutput(isDebug);

            //and connect!

            urlConnection.connect();

            if(isDebug) {
                System.out.println(urlConnection.getResponseCode() +" " + urlConnection.getResponseMessage()+"\n");
                BufferedReader br = null;
                if (urlConnection.getErrorStream() != null) {
                    br = new BufferedReader(new InputStreamReader(urlConnection.getErrorStream()));
                    String str = null;
                    while ((str = br.readLine()) != null) {
                        System.out.println(str);
                    }
                    str = null;
                }
                br = null;
            }
            //set the path where we want to save the file
            //in this case, going to save it on the root directory of the
            //sd card.

            folder = new File(getApplicationContext().getExternalCacheDir()+"");

            //create a new file, specifying the path, and the filename
            //which we want to save the file as.

            //String filename = "page" + no + ".PNG";

            file = new File(folder, fileName);

            if (file.canWrite()){
                file.createNewFile();
            }

            //this will be used to write the downloaded data into the file we created
            FileOutputStream fileOutput = new FileOutputStream(file);

            //this will be used in reading the data from the internet
            InputStream inputStream = urlConnection.getInputStream();

            //this is the total size of the file
            int totalSize = urlConnection.getContentLength();
            //variable to store total downloaded bytes
            int downloadedSize = 0;

            //create a buffer...
            byte[] buffer = new byte[1024];
            int bufferLength = 0; //used to store a temporary size of the buffer

            //now, read through the input buffer and write the contents to the file
            while ((bufferLength = inputStream.read(buffer)) > 0) {
                //add the data in the buffer to the file in the file output stream (the file on the sd card
                fileOutput.write(buffer, 0, bufferLength);
                //add up the size so we know how much is downloaded
                downloadedSize += bufferLength;
                //this is where you would do something to report the prgress, like this maybe
                if(isDebug) {
                    System.out.println("Progress- downloadedSize:" + downloadedSize + "totalSize:" + totalSize);
                }
            }
            //close the output stream when done
            inputStream.close();
            fileOutput.close();

            inputStream = null;
            fileOutput = null;

            if (downloadedSize == totalSize) {
                filePath = file.getPath();
            }else if(totalSize < downloadedSize){
                filePath = file.getPath();
            }

            urlConnection.disconnect();
            urlConnection = null;
            url = null;
            //catch some possible errors...
        } catch(NullPointerException npe){
            filePath=null;
            if(isDebug) {
                npe.printStackTrace();
            }
        } catch (MalformedURLException mue) {
            if(isDebug){
                mue.printStackTrace();
            }
        } catch (IOException ioe) {
            filePath=null;
            if(isDebug){
                ioe.printStackTrace();
            }
        }

        if(isDebug) {
            System.out.println("downloaded filepath: " + filePath);
        }

        if(filePath==null && file != null){
            file.delete();
        }

        file = null;
        folder = null;

        fileName = null;

        return filePath;
    }

    private static final String parseTcoTweet(Status status){

        String statusString = status.getText();

        URLEntity[] urlEntities = status.getURLEntities();
        for (URLEntity urlEntity : urlEntities) {
            //this app only shows popup not clickable links so getting display url
            if (isDebug) {
                System.out.println("URL: " + urlEntity);
            }
            statusString = statusString.replace(urlEntity.getURL(), urlEntity.getDisplayURL());
        }
        urlEntities = null;

        //if there's no URL entities.. delete Media URL as preview provides.
        MediaEntity[] mediaEntities = status.getMediaEntities();
        for (MediaEntity mediaEntity : mediaEntities) {
            //this app only shows popup not clickable links so getting display url
            if (isDebug) {
                System.out.println("URL: " + mediaEntity);
            }
            statusString = statusString.replace(mediaEntity.getURL(), "");
        }
        mediaEntities = null;

        return statusString.trim();
    }

    private static final String[] parseTcoToFullUrls(Status status) {

        URLEntity[] urlEntities = status.getURLEntities();
        String[] statusUrls = new String[urlEntities.length];
        for (int i = 0 ; i < urlEntities.length ; i++) {
            //this app only shows popup not clickable links so getting display url
            if (isDebug) {
                System.out.println("URL: " + urlEntities[i]);
            }
            statusUrls[i] = urlEntities[i].getExpandedURL();
        }
        urlEntities = null;

        return statusUrls;
    }

    private String parseVineVideoUrl(String uri) {
        String vineUrl = null;

        try {

            URL url = new URL(uri);

            //create the new connection

            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            //set up some things on the connection
            urlConnection.setRequestMethod("GET");
            //urlConnection.setInstanceFollowRedirects(true);

            //mimic browser
            //urlConnection.setRequestProperty("User-Agent","Mozilla/5.0 ( compatible ) ");
            //urlConnection.setRequestProperty("Accept","*/*");
            //urlConnection.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 6.3; Win64; x64; rv:38.0) Gecko/20100101 Firefox/38.0");

            //urlConnection.setDoOutput(true);
            urlConnection.setDoOutput(false);
            //urlConnection.setDoOutput(isDebug);

            //and connect!

            urlConnection.connect();

            if (isDebug) {
                System.out.println(urlConnection.getResponseCode() + " " + urlConnection.getResponseMessage() + "\n");

                if (urlConnection.getErrorStream() != null) {
                    BufferedReader br = null;
                    br = new BufferedReader(new InputStreamReader(urlConnection.getErrorStream()));
                    String str = null;
                    while ((str = br.readLine()) != null) {
                        System.out.println(str);
                    }
                    str = null;
                    br = null;
                }

            }

            boolean redirect = false;
            do {
                // normally, 3xx is redirect
                int status = urlConnection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP
                            || status == HttpURLConnection.HTTP_MOVED_PERM
                            || status == HttpURLConnection.HTTP_SEE_OTHER) {
                        redirect = true;
                    }
                }else{
                    redirect = false;
                }

                if (redirect) {

                    // get redirect url from "location" header field
                    String newUrl = urlConnection.getHeaderField("Location");
                    if(newUrl != null) {
                        // get the cookie if need, for login
                        //String cookies = conn.getHeaderField("Set-Cookie");

                        // open the new connnection again
                        urlConnection = (HttpURLConnection) new URL(newUrl).openConnection();
                        System.out.println("Redirect to URL : " + newUrl);
                        //urlConnection.setRequestProperty("Cookie", cookies);
                        //urlConnection.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
                        //urlConnection.addRequestProperty("User-Agent", "Mozilla");
                        //urlConnection.addRequestProperty("Referer", "google.com");
                        urlConnection.setRequestMethod("GET");
                        urlConnection.setDoOutput(false);
                        urlConnection.connect();


                        if (isDebug) {
                            System.out.println(urlConnection.getResponseCode() + " " + urlConnection.getResponseMessage() + "\n");

                            if (urlConnection.getErrorStream() != null) {
                                BufferedReader br = null;
                                br = new BufferedReader(new InputStreamReader(urlConnection.getErrorStream()));
                                String str = null;
                                while ((str = br.readLine()) != null) {
                                    System.out.println(str);
                                }
                                str = null;
                                br = null;
                            }

                        }
                    }

                }
            } while(redirect);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(urlConnection.getInputStream()));
            String inputLine = null;
            StringBuffer html = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                html.append(inputLine);
            }
            in.close();
            String stringOutput = html.toString();
            in = null;
            inputLine = null;
            html = null;
            /*//this will be used in reading the data from the internet
            InputStream inputStream = urlConnection.getInputStream();

            //String that save htmls;
            String stringOutput = (new Scanner(inputStream).useDelimiter("/z").next());
            inputStream.close();
            inputStream = null;*/

            urlConnection.disconnect();
            urlConnection = null;
            url = null;

            stringOutput = stringOutput.substring(stringOutput.indexOf("<meta property=\"twitter:player:stream\" content=\""));
            if(isDebug){
                System.out.println("parsing vine url from: "+stringOutput);
            }
            stringOutput = stringOutput.substring(48, stringOutput.indexOf("?"));
            if(isDebug){
                System.out.println("parsed vine url to: "+stringOutput);
            }
            vineUrl = stringOutput;
            stringOutput = null;
            //catch some possible errors...

        } catch(IndexOutOfBoundsException ioobe){
            vineUrl=null;
            if(isDebug) {
                ioobe.printStackTrace();
            }
        } catch(NullPointerException npe){
            vineUrl=null;
            if(isDebug) {
                npe.printStackTrace();
            }
        } catch (MalformedURLException mue) {
            if(isDebug){
                mue.printStackTrace();
            }
        } catch (IOException ioe) {
            vineUrl=null;
            if(isDebug){
                ioe.printStackTrace();
            }
        }

        if(isDebug) {
            System.out.println("download video url: " + vineUrl);
        }

        return vineUrl;

    }

    private static final String[] getMediaEntitiesSmall(MediaEntity[] mediaEntities){
        String[] mediaUrls = new String[mediaEntities.length];
        for (int i = 0; i < mediaEntities.length ; i++){

            mediaUrls[i] = mediaEntities[i].getMediaURLHttps()+":small";
            if(isDebug){
                //System.out.println("ExpandedURL: "+mediaEntities[i].getExpandedURL() + "\n" +
                //    "Type: " + mediaEntities[i].getType() + "\n" +
                //    "Sizes: " + mediaEntities[i].getSizes()// + "\n"
                //);
                System.out.println("This tweet has media"+(i+1)+"/"+mediaEntities.length+": "+mediaUrls[i]);
            }
        }
        return mediaUrls;
    }

    private static final int checkIfMediaHasVideo(String[] mediaUrls) {
        int cursor = 0;
        for(String mediaUrl : mediaUrls) {
            if(mediaUrl.contains("tweet_video_thumb")){
                break;
            }else{
                cursor++;
            }
        }

        return cursor;
    }

    private static final String parseOfficialVideoLink(String mediaUrl){
        try {
            mediaUrl = mediaUrl.substring(0, mediaUrl.lastIndexOf(".")) + ".mp4";
            mediaUrl = mediaUrl.replace("_thumb", "");
        }catch(IndexOutOfBoundsException ioobe) {
            if (isDebug) {
                ioobe.printStackTrace();
            }
        }
        return mediaUrl;
    }

    private static final String stripOutFileExtension(String fileName){
        int extensionPos = fileName.lastIndexOf(".");
        if(extensionPos>=0) {
            if (isDebug) {
                System.out.println("Stripping out just a name from " + fileName);
            }
            fileName = fileName.substring(0, extensionPos);
            if (isDebug) {
                System.out.println("stripped out filename to " + fileName);
            }
        }
        extensionPos = 0;
        return fileName;
    }

    private String checkCacheAndDownloadFile(String url, long id){
        //check cache if there is a file there.
        if (url == null){
            return null;
        }else {
            String filePath = getApplicationContext().getExternalCacheDir() + stripOutFileExtension(url.substring(url.lastIndexOf("/"))) + id;
            if (!new File(filePath).canRead()) {
                if (isDebug) {
                    System.out.println("File " + filePath + " is not readable. downloading");
                }
                filePath = downloadFileFromUrl(url, id);
            }
            return filePath;
        }
    }

    private static final String parseYouTubeGetEmbedUrl(String url) {
        try {
            //https://www.youtube.com/embed/***********
            if (url.contains("youtube.com/watch?v=")) {
                //this url has browser-pasted youtube address
                url = url.substring(url.indexOf("youtube.com/watch?v=")+20);
            } else if (url.contains("youtu.be/")) {
                //this url has shortened youtube address
                url = url.substring(url.indexOf("youtu.be/")+9);
            }
            url = "https://www.youtube.com/embed/" + url;
        }catch(IndexOutOfBoundsException ioobe){
            if(isDebug){
                ioobe.printStackTrace();
            }
        }

        return url;
    }

    public class TrialWaitMemoObject {
        long availableDelay = TwitStreamViewService.DELAY_SELF_DESTRUCT_VIEW;
    }

    public class VineVideoURLMemoObject {
        String videoUrl = null;
    }

}
