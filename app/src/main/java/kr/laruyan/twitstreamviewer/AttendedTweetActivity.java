package kr.laruyan.twitstreamviewer;

import android.app.Activity;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;

import java.util.ArrayList;


public class AttendedTweetActivity extends Activity {

    private static final boolean isDebug = MainActivity.isDebug;
    private static final String EXTRA_FINISH = "kr.laruyan.twitstreamviewer.attendedtweetactivity.finish";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();

        if (intent != null) {
            final String action = intent.getAction();
            final String type = intent.getType();

            if (Intent.ACTION_SEND.equals(action) && type != null) {
                if("text/plain".equals(type)){
                    String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if (sharedText != null) {
                        // Update UI to reflect text being shared
                        UnattendedTweetIntentService.startActionConfirmAndTweet(this, sharedText, null, null, null, null);
                    }
                }else if(type.startsWith("image/")){
                    Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (imageUri != null) {
                        // Update UI to reflect image being shared
                        UnattendedTweetIntentService.startActionConfirmAndTweet(this, null, imageUri + "", null, null, null);
                    }
                }
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null){
                if(type.startsWith("image/")){
                    ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    if (imageUris != null) {
                        if (imageUris.size() <= 4){
                            // Update UI to reflect multiple images being shared

                            UnattendedTweetIntentService.startActionConfirmAndTweet(this,
                                    null,
                                    imageUris.size() >= 1 ? getPath(imageUris.get(0)) + "" : null,
                                    imageUris.size() >= 2 ? getPath(imageUris.get(1)) + "" : null,
                                    imageUris.size() >= 3 ? getPath(imageUris.get(2)) + "" : null,
                                    imageUris.size() == 4 ? getPath(imageUris.get(3)) + "" : null);
                        }
                    }
                }
            }
        }
        intent = null;
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
            finishAndRemoveTask();
        }else {
            Intent finishIntent = new Intent(this, AttendedTweetActivity.class);
            //finishIntent.putExtra(EXTRA_FINISH, true);
            finishIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(finishIntent);
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //if (this.getIntent().getExtras() != null && this.getIntent().getBooleanExtra(EXTRA_FINISH, false)) {
            finish();
        //}
    }


    private String getPath(Uri uri) {
        String[]  data = { MediaStore.Images.Media.DATA };
        CursorLoader loader = new CursorLoader(this, uri, data, null, null, null);
        Cursor cursor = loader.loadInBackground();
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }
}
