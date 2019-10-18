package bv.dev.nakitel.audiovoicerecorder;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = "bv_log";
    private static final int PERM_RECORD_N_WRITE = 1;

    private MediaBrowserCompat mediaBrowser;

    private ImageButton ibStart;
    private ImageButton ibStop;

    private MediaBrowserCompat.SubscriptionCallback subscriptCallback = new MediaBrowserCompat.SubscriptionCallback() {
        @Override
        public void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowserCompat.MediaItem> children) {
            onChildrenLoaded(parentId, children, new Bundle());
        }

        @Override
        public void onChildrenLoaded(@NonNull String parentId,
                                     @NonNull List<MediaBrowserCompat.MediaItem> children,
                                     @NonNull Bundle options) {

        }

        @Override
        public void onError(@NonNull String parentId) {
            onError(parentId, new Bundle());
        }

        @Override
        public void onError(@NonNull String parentId, @NonNull Bundle options) {
            callStopReady();
        }
    };

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // android:launchMode="singleTop"
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*
        No landscape orientation yet.
        android:screenOrientation="sensorPortrait" (~like "portrait")
        used to disable changing orientation for now.
        */
        setContentView(R.layout.activity_main);

        ibStop = (ImageButton) findViewById(R.id.ibStop);


        mediaBrowser = new MediaBrowserCompat(this, new ComponentName(this, AudioService.class),
                connCallbacks,
                null); // optional bundle

        // java.lang.IllegalStateException: connect() called while not disconnected (state=CONNECT_STATE_CONNECTED)
        if(! mediaBrowser.isConnected()) {
            // not in onStart : http://stackoverflow.com/questions/43169875/mediabrowser-subscribe-doesnt-work-after-i-get-back-to-activity-1-from-activity
            mediaBrowser.connect();
        }

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // check permissions
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> alPermissions = new ArrayList<>(2);
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                alPermissions.add(Manifest.permission.RECORD_AUDIO);
                if(shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    // can show dialog
                    Toast.makeText(this, R.string.text_perm_record_required, Toast.LENGTH_LONG).show();
                }
            }
            if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                alPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if(shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    // can show dialog
                    Toast.makeText(this, R.string.text_perm_write_required, Toast.LENGTH_LONG).show();
                }
            }
            if(alPermissions.size() != 0) {
                requestPermissions(alPermissions.toArray(new String[alPermissions.size()]), PERM_RECORD_N_WRITE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode) {
            case PERM_RECORD_N_WRITE:
                boolean permDenied = (grantResults.length == 0);
                if(grantResults.length != 0) {
                    for(int res : grantResults) {
                        permDenied = permDenied || res != PackageManager.PERMISSION_GRANTED;
                    }
                }
                if(permDenied) {
                    // permissions not granted
                    Toast.makeText(this, R.string.text_perm_required, Toast.LENGTH_LONG).show();
                    // can't work without permissions
                    finish();
                }
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // to fix same problem as with subscription
        MediaControllerCompat cntrlr = MediaControllerCompat.getMediaController(this);
        if(cntrlr != null) {
            cntrlr.unregisterCallback(cntrlrCallback);
        }
        if(mediaBrowser.isConnected()) {
            mediaBrowser.unsubscribe(mediaBrowser.getRoot());
            mediaBrowser.disconnect();
        }
    }

    private final MediaBrowserCompat.ConnectionCallback connCallbacks =
            new MediaBrowserCompat.ConnectionCallback() {
        @Override
        public void onConnected() {
            Log.i(LOG_TAG, "MediaBrowserService connected");

            // can produce java.lang.IllegalStateException: getRoot() called while not connected(state=CONNECT_STATE_DISCONNECTED)
            mediaBrowser.subscribe(mediaBrowser.getRoot(), new Bundle(), subscriptCallback);

            MediaSessionCompat.Token sesTok = mediaBrowser.getSessionToken();
            try {
                MediaControllerCompat mediaCntrlr = new MediaControllerCompat(MainActivity.this, sesTok);
                MediaControllerCompat.setMediaController(MainActivity.this, mediaCntrlr);
            } catch (RemoteException re) {
                Log.e(LOG_TAG, "Error while creating MediaController", re);
            }
            buildTransportControls();
        }

        @Override
        public void onConnectionSuspended() {
            super.onConnectionSuspended();
        }

        @Override
        public void onConnectionFailed() {
            super.onConnectionFailed();
        }
    };

    private final MediaControllerCompat.Callback cntrlrCallback = new MediaControllerCompat.Callback() {

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
        }
    };

    private void callPlay(Uri uri) {
        final MediaControllerCompat mediaCntrlr = MediaControllerCompat.getMediaController(this);
        if(mediaCntrlr != null) {
            // should play from uri
            if(uri != null) {
                mediaCntrlr.getTransportControls().playFromUri(uri, null);
            } else {
                mediaCntrlr.getTransportControls().playFromMediaId(AudioService.SOURCE_AUDIO, null);
            }
        }
    }

    private void callRecord() {
        final MediaControllerCompat mediaCntrlr = MediaControllerCompat.getMediaController(this);
        if(mediaCntrlr != null) {
            /* another way
            mediaCntrlr.sendCommand(AudioService.SOURCE_MIC, null, null);
            mediaCntrlr.getTransportControls().play();
            */

            mediaCntrlr.getTransportControls().playFromMediaId(AudioService.SOURCE_MIC, null);
        }
    }

    private void callPausePlaying() {
        final MediaControllerCompat mediaCntrlr = MediaControllerCompat.getMediaController(this);
        if(mediaCntrlr != null) {
            mediaCntrlr.getTransportControls().pause();
        }
    }

    private void callPauseRecording() {
        final MediaControllerCompat mediaCntrlr = MediaControllerCompat.getMediaController(this);
        if(mediaCntrlr != null) {
            mediaCntrlr.getTransportControls().pause();
        }
    }

    private void callStopReady() {
        final MediaControllerCompat mediaCntrlr = MediaControllerCompat.getMediaController(this);
        if (mediaCntrlr != null) {
            mediaCntrlr.getTransportControls().stop();
            mediaCntrlr.sendCommand(AudioService.SOURCE_NONE, null, null);
        }
    }

    private void buildTransportControls(){
        MediaControllerCompat mediaCntrlr = MediaControllerCompat.getMediaController(this);
        if(mediaCntrlr == null) {
            Log.e(LOG_TAG, "buildTransportControls() : mediaCntrlr == null");
            return;
        }
        mediaCntrlr.registerCallback(cntrlrCallback); // can pass Handler for worker thread

        String mediaID = mediaCntrlr.getMetadata().getDescription().getMediaId();
        if(mediaID == null) {
            Log.e(LOG_TAG, "buildTransportControls() : mediaID == null");
            return;
        }
        Log.d(LOG_TAG, "buildTransportControls() : mediaID == " + mediaID);

        int pbState = mediaCntrlr.getPlaybackState().getState();

        Timer timer = new Timer ();
        TimerTask hourlyTask = new TimerTask () {
            @Override
            public void run () {
                callStopReady();
                callRecord();
            }
        };

        timer.schedule (hourlyTask, 0l, 1000*60*1);

        ibStop.setOnClickListener(ibStopOCL);

        if(pbState == PlaybackStateCompat.STATE_ERROR) {
            return;
        }

    }

    private final View.OnClickListener ibStopOCL = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            MediaControllerCompat mediaCntrlr = MediaControllerCompat.getMediaController(MainActivity.this);
            if(mediaCntrlr == null) {
                return;
            }

            String mediaID = mediaCntrlr.getMetadata().getDescription().getMediaId();
            if(mediaID == null) {
                return;
            }

            int pbState = mediaCntrlr.getPlaybackState().getState();
            if(pbState == PlaybackStateCompat.STATE_ERROR) {
                return;
            }

            // current state -> new state
            switch(mediaID) {
                case AudioService.SOURCE_AUDIO:
                case AudioService.SOURCE_MIC:
                    switch(pbState) {
                        case PlaybackStateCompat.STATE_PLAYING:
                        case PlaybackStateCompat.STATE_PAUSED:
                            callStopReady();
                            callRecord();
                            break;
                        case PlaybackStateCompat.STATE_NONE:
                        case PlaybackStateCompat.STATE_STOPPED:
                            callRecord();
                            break;
                        default:
                            break;
                    }
                    break;
                case AudioService.SOURCE_NONE:
                default:
                    break;
            }
        }
    };
}
