package kr.laruyan.twitstreamviewer;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.File;

import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.UploadedMedia;
import twitter4j.conf.ConfigurationBuilder;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class UnattendedTweetIntentService extends IntentService {
    // TODO: Rename actions, choose action names that describe tasks that this
    // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
    private static final boolean isDebug = MainActivity.isDebug;
    private static final String ACTION_CONFIRM_AND_TWEET = "kr.laruyan.twitstreamviewer.action.CONFIRM_AND_TWEET";
    private static final String ACTION_DIRECT_TWEET = "kr.laruyan.twitstreamviewer.action.DIRECT_TWEET";

    // TODO: Rename parameters
    private static final String EXTRA_TWEET = "kr.laruyan.twitstreamviewer.extra.TWEET";
    private static final String EXTRA_IMG1 = "kr.laruyan.twitstreamviewer.extra.IMG1";
    private static final String EXTRA_IMG2 = "kr.laruyan.twitstreamviewer.extra.IMG2";
    private static final String EXTRA_IMG3 = "kr.laruyan.twitstreamviewer.extra.IMG3";
    private static final String EXTRA_IMG4 = "kr.laruyan.twitstreamviewer.extra.IMG4";

    private static final String CONSUMER_KEY = MainActivity.CONSUMER_KEY;
    private static final String CONSUMER_SECRET = MainActivity.CONSUMER_SECRET;
    private static final String PREF_KEY_ACCESS_TOKEN = MainActivity.PREF_KEY_ACCESS_TOKEN;
    private static final String PREF_KEY_ACCESS_SECRET = MainActivity.PREF_KEY_ACCESS_SECRET;

    private static final int MAX_IMAGE_UPLOADS = 4;

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startActionConfirmAndTweet(Context context, String tweet, String image1, String image2, String image3, String image4) {
        Intent intent = new Intent(context, UnattendedTweetIntentService.class);
        intent.setAction(ACTION_CONFIRM_AND_TWEET);
        intent.putExtra(EXTRA_TWEET, tweet);
        intent.putExtra(EXTRA_IMG1, image1);
        intent.putExtra(EXTRA_IMG2, image2);
        intent.putExtra(EXTRA_IMG3, image3);
        intent.putExtra(EXTRA_IMG4, image4);

        context.startService(intent);
    }

    /**
     * Starts this service to perform action Baz with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startActionDirectTweet(Context context, String tweet, String image1, String image2, String image3, String image4) {
        Intent intent = new Intent(context, UnattendedTweetIntentService.class);
        intent.setAction(ACTION_DIRECT_TWEET);
        intent.putExtra(EXTRA_TWEET, tweet);
        intent.putExtra(EXTRA_IMG1, image1);
        intent.putExtra(EXTRA_IMG2, image2);
        intent.putExtra(EXTRA_IMG3, image3);
        intent.putExtra(EXTRA_IMG4, image4);
        context.startService(intent);
    }

    public UnattendedTweetIntentService() {
        super("UnattendedTweetIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_CONFIRM_AND_TWEET.equals(action)) {
                final String tweet = intent.getStringExtra(EXTRA_TWEET);
                final String image1 = intent.getStringExtra(EXTRA_IMG1);
                final String image2 = intent.getStringExtra(EXTRA_IMG2);
                final String image3 = intent.getStringExtra(EXTRA_IMG3);
                final String image4 = intent.getStringExtra(EXTRA_IMG4);
                handleActionConfirmAndTweet(tweet, image1, image2, image3, image4);
            } else if (ACTION_DIRECT_TWEET.equals(action)) {
                final String tweet = intent.getStringExtra(EXTRA_TWEET);
                final String image1 = intent.getStringExtra(EXTRA_IMG1);
                final String image2 = intent.getStringExtra(EXTRA_IMG2);
                final String image3 = intent.getStringExtra(EXTRA_IMG3);
                final String image4 = intent.getStringExtra(EXTRA_IMG4);
                handleActionDirectTweet(tweet, image1, image2, image3, image4);
            }
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionConfirmAndTweet(String tweet, String image1, String image2, String image3, String image4) {
        // TODO: Handle action Foo

        handleActionDirectTweet(tweet, image1, image2, image3, image4);
    }

    /**
     * Handle action Baz in the provided background thread with the provided
     * parameters.
     */
    private void handleActionDirectTweet(String tweet, String image1, String image2, String image3, String image4) {
        // TODO: Handle action Baz



        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String oAuthAccessToken = prefs.getString(PREF_KEY_ACCESS_TOKEN, null);
        String oAuthAccessSecret = prefs.getString(PREF_KEY_ACCESS_SECRET, null);
        prefs = null;

        if(oAuthAccessSecret != null || oAuthAccessSecret != null) {
            ConfigurationBuilder cb = new ConfigurationBuilder();
            cb//.setDebugEnabled(true)
                    .setOAuthConsumerKey(CONSUMER_KEY)
                    .setOAuthConsumerSecret(CONSUMER_SECRET)
                    .setOAuthAccessToken(oAuthAccessToken)
                    .setOAuthAccessTokenSecret(oAuthAccessSecret);

            Twitter twitter = new TwitterFactory(cb.build()).getInstance();

            if(isDebug) {
                System.out.println("image URIs:\n" + image1 + "\n" + image2 + "\n" + image3 + "\n" + image4);
            }

            String[] imgs = {image1,image2,image3,image4};
            try {
                if(tweet == null){
                    tweet = "";
                }
                StatusUpdate statusToUpdate = new StatusUpdate(tweet);


                long[] tempMediaIds = new long[MAX_IMAGE_UPLOADS];
                int incrementalMediaCount = 0;

                for (String img : imgs){
                    if (img != null){
                        img = img.substring(img.indexOf("/"));
                        if(isDebug) {
                            System.out.println("ImageReadable: " + new File(img).canRead());
                        }
                        UploadedMedia media = twitter.uploadMedia(new File(img));
                        if(isDebug) {
                            System.out.println("Uploaded: id=" + media.getMediaId()
                                    + ", w=" + media.getImageWidth() + ", h=" + media.getImageHeight()
                                    + ", type=" + media.getImageType() + ", size=" + media.getSize());
                        }
                        tempMediaIds[incrementalMediaCount++] = media.getMediaId();
                    }
                }
                long[] mediaIds = new long[incrementalMediaCount];
                for (int i=0; i<incrementalMediaCount ; i++){
                    mediaIds[i] = tempMediaIds[i];
                }
                /*for (int i=0; i<imgs.length; i++) {

                    System.out.println("Uploading...[" + i + "/" + (imgs.length-1) + "][" + imgs[i] + "]");
                    UploadedMedia media = twitter.uploadMedia(new File(imgs[i]));
                    System.out.println("Uploaded: id=" + media.getMediaId()
                            + ", w=" + media.getImageWidth() + ", h=" + media.getImageHeight()
                            + ", type=" + media.getImageType() + ", size=" + media.getSize());
                    mediaIds[i-1] = media.getMediaId();

                }*/
                if(incrementalMediaCount>0) {
                    statusToUpdate.setMediaIds(mediaIds);
                }
                Status status = twitter.updateStatus(statusToUpdate);
                if(isDebug) {
                    System.out.println("Successfully updated the status to [" + status.getText() + "].");
                }
            } catch (TwitterException te) {
                te.printStackTrace();
            }
        }

    }

}
