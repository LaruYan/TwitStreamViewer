package kr.laruyan.twitstreamviewer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;


public class MainActivity extends Activity {

    private Button btnStart;
    private CheckBox ckbEdge;
    private Button btnEnd;
    private Button btnSignOut;
    private LinearLayout lnlPin;
    private EditText etPin;
    private Button btnPin;

    private Twitter mTwitter = null;
    private RequestToken mRequestToken = null;
    private final Activity mActivity = this;

    public static final boolean isDebug = true;
    public static final String CONSUMER_KEY = "YOUR_CONSUMER_KEY_HERE";
    public static final String CONSUMER_SECRET = "YOUR_CONSUMER_SECRET_HERE";
    public static final String PREF_KEY_ACCESS_TOKEN = "oauth.accessToken";
    public static final String PREF_KEY_ACCESS_SECRET = "oauth.accessTokenSecret";
    public static final String EXTRA_KEY_EDGEMODE = "edge.mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(isDebug){
            this.hideSystemUI();
        }

        btnStart = (Button)findViewById(R.id.button_Start);
        btnStart.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                String oAuthAccessToken = prefs.getString(PREF_KEY_ACCESS_TOKEN, null);
                String oAuthAccessSecret = prefs.getString(PREF_KEY_ACCESS_SECRET,null);

                if(oAuthAccessToken == null || oAuthAccessSecret == null){
                    lnlPin.setVisibility(View.VISIBLE);


                   try {
                       new OnOShowURLButtonClickTask().execute();
                   /*
                        final Twitter twitter = new TwitterFactory(new ConfigurationBuilder()
                                .setOAuthConsumerKey(CONSUMER_KEY)
                                .setOAuthConsumerSecret(CONSUMER_SECRET).build()).getInstance();


                        Thread thread = new Thread(new Runnable(){
                            @Override
                            public void run() {
                                try {
                                    MainActivity.this.startActivity(new Intent(Intent.ACTION_VIEW, Uri
                                            .parse(twitter.getOAuthRequestToken().getAuthenticationURL())));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                        thread.start();
*/
                        //TwitStreamViewIntentService.startActionStart(getApplicationContext(),oAuthAccessToken,oAuthAccessSecret);
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }else {
                    //TwitStreamViewIntentService.startActionStart(getApplicationContext(),oAuthAccessToken,oAuthAccessSecret);
                    if(ckbEdge.isChecked()){
                        startService(new Intent(MainActivity.this, TwitStreamViewService.class).putExtra(EXTRA_KEY_EDGEMODE,true));
                    }else {
                        startService(new Intent(MainActivity.this, TwitStreamViewService.class));
                    }
                }
                prefs = null;
            }
        });
        ckbEdge = (CheckBox)findViewById(R.id.checkBox_Edge);

        lnlPin = (LinearLayout)findViewById(R.id.linearLayout_Pin);
        btnPin = (Button)findViewById(R.id.button_Pin);
        etPin = (EditText)findViewById(R.id.editText_Pin);
        btnPin.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                lnlPin.setVisibility(View.GONE);

                new OnOAuthButtonClickTask().execute();
            }
        });


        btnEnd = (Button)findViewById(R.id.button_End);
        btnEnd.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                //TwitStreamViewIntentService.startActionEnd(getApplicationContext());
                stopService(new Intent(MainActivity.this, TwitStreamViewService.class));
            }
        });
        btnSignOut = (Button)findViewById(R.id.button_SignOut);
        btnSignOut.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                //TwitStreamViewIntentService.startActionEnd(getApplicationContext());
                stopService(new Intent(MainActivity.this, TwitStreamViewService.class));
                SharedPreferences.Editor prefsEditor = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
                prefsEditor.putString(PREF_KEY_ACCESS_TOKEN, null);
                prefsEditor.putString(PREF_KEY_ACCESS_SECRET, null);
                prefsEditor.commit();
            }
        });


    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * ボタンをタップすると，OAuthの認証URLを取得してブラウザを起動する．
     * リスナ
     * @author ABS104a
     * https://github.com/ABS104a/TwitterOAuthTest/blob/master/TwitterOAuthTest/src/com/abs104a/twitteroauthtest/OAuthActivity.java
     */
    public class OnOShowURLButtonClickTask extends AsyncTask<Void,Void,Void>{

        @Override
        protected Void doInBackground(Void... params) {
            //<a class="keyword" href="http://d.hatena.ne.jp/keyword/Twitter">Twitter</a>の<a class="keyword" href="http://d.hatena.ne.jp/keyword/%A5%A4%A5%F3%A5%B9%A5%BF%A5%F3%A5%B9">インスタンス</a>を取得
            mTwitter = TwitterFactory.getSingleton();
            //ConsumerKeyをセットする．
            mTwitter.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);

            try {
                //リクエストトークンを取得
                mRequestToken = mTwitter.getOAuthRequestToken();
                //認証URLを取得
                final String url = mRequestToken.getAuthorizationURL();

                //ブラウザを起動するためのintentを作成．
                final Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(url));
                //intentを起動
                startActivity(intent);
            } catch (TwitterException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * OAuthボタンをタップした時に呼ばれるリスナ
     * EditTextからPINを読み取りトークンを取得する．
     * @author ABS104a
     *
     */
    public class OnOAuthButtonClickTask extends AsyncTask<Void,Void,AccessToken> {

        @Override
        protected AccessToken doInBackground(Void... params) {

            //EditText mEditText = (EditText)mActivity.findViewById(R.id.editText);
            try {
                //アクセストークンを取得
                return mTwitter.getOAuthAccessToken(mRequestToken,etPin.getText().toString());
            } catch (TwitterException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(AccessToken result) {
            super.onPostExecute(result);
            if(result == null) return;
            //トークンの書き出し(取得に成功した場合)
            SharedPreferences.Editor prefsEditor = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
            prefsEditor.putString(PREF_KEY_ACCESS_TOKEN, result.getToken());
            prefsEditor.putString(PREF_KEY_ACCESS_SECRET, result.getTokenSecret());
            prefsEditor.commit();
            prefsEditor = null;
            //Twitterクラスにアクセストークンをセット（これでTwitterクラスが使えるようになる）
            mTwitter.setOAuthAccessToken(result);

            //Tweetの送信(通常は通信を伴うため別スレッド上で動かす
            //Android4.2以降ではメインスレッドでHTTP投げようとすると例外吐くようです．
            //<ex>
            //mTwitter.updateStatus("hogehoge");
        }

    }

    // This snippet hides the system bars.
    private void hideSystemUI() {
        // Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    // This snippet shows the system bars. It does this by removing all the flags
// except for the ones that make the content appear under the system bars.
    private void showSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
}
