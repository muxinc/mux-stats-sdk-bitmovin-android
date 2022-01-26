package com.mux.stats.sdk.muxstats.automatedtests.ui;

import android.graphics.Point;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import com.bitmovin.player.PlayerView;
import com.bitmovin.player.api.Player;
import com.bitmovin.player.api.source.Source;
import com.mux.stats.sdk.core.model.CustomerData;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.muxstats.automatedtests.BuildConfig;
import com.mux.stats.sdk.muxstats.automatedtests.R;
import com.mux.stats.sdk.muxstats.automatedtests.mockup.MockNetworkRequest;
import com.mux.stats.sdk.muxstats.bitmovinplayer.MuxStatsSDKBitmovinPlayer;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class SimplePlayerActivity extends AppCompatActivity   {

  static final String TAG = "SimplePlayerActivity";

  protected static final String PLAYBACK_CHANNEL_ID = "playback_channel";
  protected static final int PLAYBACK_NOTIFICATION_ID = 1;
  protected static final String ARG_URI = "uri_string";
  protected static final String ARG_TITLE = "title";
  protected static final String ARG_START_POSITION = "start_position";

  String videoTitle = "Test Video";
  String urlToPlay;
  PlayerView playerView;
  Player player;
  //  DefaultTrackSelector trackSelector;
  Source testMediaSource;
  MuxStatsSDKBitmovinPlayer muxStats;
  //  AdsLoader adsLoader;
  Uri loadedAdTagUri;
  boolean playWhenReady = true;
  MockNetworkRequest mockNetwork;
  AtomicBoolean onResumedCalled = new AtomicBoolean(false);
  //  PlayerNotificationManager notificationManager;
//  MediaSessionCompat mediaSessionCompat;
//  MediaSessionConnector mediaSessionConnector;
  long playbackStartPosition = 0;

  Lock activityLock = new ReentrantLock();
  Condition playbackEnded = activityLock.newCondition();
  Condition playbackStopped = activityLock.newCondition();
  Condition seekEnded = activityLock.newCondition();
  Condition playbackStarted = activityLock.newCondition();
  Condition playbackBuffering = activityLock.newCondition();
  Condition activityClosed = activityLock.newCondition();
  Condition activityInitialized = activityLock.newCondition();
  ArrayList<String> addAllowedHeaders = new ArrayList<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Enter fullscreen
    hideSystemUI();
    setContentView(R.layout.activity_simple_player_test);
    disableUserActions();

    playerView = findViewById(R.id.bitmovinPlayerView);

    player = Player.create(this);
    playerView.setPlayer(player);
  }

  public void allowHeaderToBeSentToBackend(String headerName) {
    addAllowedHeaders.add(headerName);
  }

  @Override
  protected void onResume() {
    super.onResume();
    onResumedCalled.set(true);
  }

  @Override
  public void onStop() {
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    signalActivityClosed();
    if (muxStats != null) {
      muxStats.release();
    }
    releaseExoPlayer();
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    this.playWhenReady = playWhenReady;
  }

  public void setVideoTitle(String title) {
    videoTitle = title;
  }

  public void setAdTag(String tag) {
    loadedAdTagUri = Uri.parse(tag);
  }

  public void setUrlToPlay(String url) {
    urlToPlay = url;
  }

  public void setPlaybackStartPosition(long position) {
    playbackStartPosition = position;
  }

  public void releaseExoPlayer() {
    player.destroy();
    player = null;
  }

  public void hideSystemUI() {
    // Enables regular immersive mode.
    // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
    // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
      View decorView = getWindow().getDecorView();
      decorView.setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              // Set the content to appear under the system bars so that the
              // content doesn't resize when the system bars hide and show.
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              // Hide the nav bar and status bar
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
  }

  // Shows the system bars by removing all the flags
  // except for the ones that make the content appear under the system bars.
  public void showSystemUI() {
    View decorView = getWindow().getDecorView();
    decorView.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
  }

  public MuxStatsSDKBitmovinPlayer getMuxStats() {
    return muxStats;
  }

  public void initMuxSats() {
    // Mux details
    CustomerPlayerData customerPlayerData = new CustomerPlayerData();
    if (BuildConfig.SHOULD_REPORT_INSTRUMENTATION_TEST_EVENTS_TO_SERVER) {
      customerPlayerData.setEnvironmentKey(BuildConfig.INSTRUMENTATION_TEST_ENVIRONMENT_KEY);
    } else {
      customerPlayerData.setEnvironmentKey("YOUR_ENVIRONMENT_KEY");
    }
    CustomerVideoData customerVideoData = new CustomerVideoData();
    customerVideoData.setVideoTitle(videoTitle);
    mockNetwork = new MockNetworkRequest();
    CustomerData customerData = new CustomerData(customerPlayerData, customerVideoData, null);
    muxStats = new MuxStatsSDKBitmovinPlayer(
        this,
        playerView,
        "demo-player", customerData, true, mockNetwork);
    Point size = new Point();
    getWindowManager().getDefaultDisplay().getSize(size);
    muxStats.setScreenSize(size.x, size.y);
    muxStats.setPlayerView(playerView);
    muxStats.enableMuxCoreDebug(true, false);
//    for (String headerName : addAllowedHeaders) {
//      MuxStatsHelper.allowHeaderToBeSentToBackend(muxStats, headerName);
//    }
  }

  public void startPlayback() {
    // TODO start playback
  }

  public Source getTestMediaSource() {
    return testMediaSource;
  }

  public PlayerView getPlayerView() {
    return playerView;
  }

  public MockNetworkRequest getMockNetwork() {
    return mockNetwork;
  }

  public boolean waitForPlaybackToStop(long timeoutInMs) {
    try {
      activityLock.lock();
      return playbackStopped.await(timeoutInMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
      return false;
    } finally {
      activityLock.unlock();
    }
  }


  public boolean waitForPlaybackToFinish(long timeoutInMs) {
    try {
      activityLock.lock();
      return playbackEnded.await(timeoutInMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
      return false;
    } finally {
      activityLock.unlock();
    }
  }

  public void waitForActivityToInitialize() {
    if (!onResumedCalled.get()) {
      try {
        activityLock.lock();
        activityInitialized.await();
        activityLock.unlock();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public boolean waitForPlaybackToStart(long timeoutInMs) {
    try {
      activityLock.lock();
      return playbackStarted.await(timeoutInMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
      return false;
    } finally {
      activityLock.unlock();
    }
  }

  public void waitForPlaybackToStartBuffering() {
    if (!muxStats.isPaused()) {
      try {
        activityLock.lock();
        playbackBuffering.await();
      } catch (InterruptedException e) {
        e.printStackTrace();
      } finally {
        activityLock.unlock();
      }
    }
  }

  public void waitForActivityToClose() {
    try {
      activityLock.lock();
      activityClosed.await();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      activityLock.unlock();
    }
  }

  public void signalPlaybackStarted() {
    activityLock.lock();
    playbackStarted.signalAll();
    activityLock.unlock();
  }

  public void signalPlaybackStopped() {
    activityLock.lock();
    playbackStopped.signalAll();
    activityLock.unlock();
  }

  public void signalSeekEnded() {
    activityLock.lock();
    seekEnded.signalAll();
    activityLock.unlock();
  }

  public void signalPlaybackBuffering() {
    activityLock.lock();
    playbackBuffering.signalAll();
    activityLock.unlock();
  }

  public void signalPlaybackEnded() {
    activityLock.lock();
    playbackEnded.signalAll();
    activityLock.unlock();
  }

  public void signalActivityClosed() {
    activityLock.lock();
    activityClosed.signalAll();
    activityLock.unlock();
  }

  private void disableUserActions() {
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
  }

  private void enableUserActions() {
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
  }
}