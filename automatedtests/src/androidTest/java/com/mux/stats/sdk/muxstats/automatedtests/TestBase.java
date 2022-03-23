package com.mux.stats.sdk.muxstats.automatedtests;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.action.MotionEvents;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.uiautomator.UiDevice;
import com.bitmovin.player.PlayerView;
import com.bitmovin.player.api.source.Source;
import com.bitmovin.player.api.source.SourceType;
import com.mux.stats.sdk.core.events.playback.PauseEvent;
import com.mux.stats.sdk.core.events.playback.PlayEvent;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.core.events.playback.SeekedEvent;
import com.mux.stats.sdk.core.events.playback.SeekingEvent;
import com.mux.stats.sdk.muxstats.automatedtests.mockup.MockNetworkRequest;
import com.mux.stats.sdk.muxstats.automatedtests.mockup.http.SimpleHTTPServer;
import com.mux.stats.sdk.muxstats.automatedtests.ui.SimplePlayerActivity;
import java.io.IOException;
import java.util.Collection;
import org.hamcrest.Matcher;
import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

public abstract class TestBase {

  static final String TAG = "MuxStats";

  @Rule
  public ActivityTestRule<SimplePlayerActivity> activityRule =
      new ActivityTestRule<>(SimplePlayerActivity.class);
  // This does not work for r2.11.1 flavor
//    public ActivityScenarioRule<SimplePlayerTestActivity> activityRule =
//            new ActivityScenarioRule(new Intent(
//                    ApplicationProvider.getApplicationContext(),
//                    SimplePlayerTestActivity.class).putExtras(getActivityOptions())
//            );

  @Rule
  public TestName currentTestName = new TestName();

  static final int PLAY_PERIOD_IN_MS = 10000;
  static final int PAUSE_PERIOD_IN_MS = 3000;
  static final int WAIT_FOR_NETWORK_PERIOD_IN_MS = 12000;

  // I could not make this work as expected
//    static final int SEEK_PERIOD_IN_MS = 5000;
  protected int runHttpServerOnPort = 5000;
  protected int bandwidthLimitInBitsPerSecond = 1500000;
  protected int sampleFileBitrate = 1083904;
  protected String urlToPlay = "http://localhost:5000/dash/google_glass/playlist.mpd";
//  protected String urlToPlay = "https://bitdash-a.akamaihd.net/content/sintel/sintel.mpd";
  SourceType sourceType = SourceType.Dash;
  // UTC timestamp whenlow network bandwidth was triggered
  long startedJammingTheNetworkAt;
  // Amount of video playback time in player buffer
  private long bufferedTime;
  protected int networkJamPeriodInMs = 10000;
  // This is the number of times the network bandwidth will be reduced,
  // not constantly but each 10 ms a random number between 2 and factor will divide
  // the regular amount of bytes to send.
  // This will stop server completly, this will allow us to easier calculate the rebuffer period
  protected int networkJamFactor = 4;
  protected int waitForPlaybackToStartInMS = 30000;

  //    protected ActivityScenario<SimplePlayerTestActivity> testScenario;
  protected SimplePlayerActivity testActivity;
  protected Activity currentActivity;
  protected SimpleHTTPServer httpServer;
  protected PlayerView pView;
  protected Source testMediaSource;
  protected MockNetworkRequest networkRequest;
  protected long playbackStartPosition = 0;
  protected boolean playWhenReady = true;

  protected boolean testActivityFinished;
//  protected PlayerControlView controlView;
  protected View pauseButton;
  protected View playButton;


  @Before
  public void init() {
    try {
      httpServer = new SimpleHTTPServer(runHttpServerOnPort, bandwidthLimitInBitsPerSecond);
//            httpServer.setSeekLatency(SEEK_PERIOD_IN_MS);
    } catch (IOException e) {
      e.printStackTrace();
      // Failed to start server
      fail("Failed to start HTTP server, why !!!");
    }
    try {
      testActivity = (SimplePlayerActivity) getActivityInstance();
    } catch (ClassCastException e) {
      fail("Got wrong activity instance in test init !!!");
    }
    if (testActivity == null) {
      fail("Test activity not found !!!");
    }
    testActivityFinished = false;
    testActivity.runOnUiThread(() -> {
      testActivity.setVideoTitle(BuildConfig.FLAVOR + "-" + currentTestName.getMethodName());
      testActivity.setUrlToPlay(urlToPlay, sourceType);
      testActivity.setPlayWhenReady(playWhenReady);
      testActivity.initMuxSats();
      testActivity.setPlaybackStartPosition(playbackStartPosition);
      testActivity.startPlayback();
      pView = testActivity.getPlayerView();
      testMediaSource = testActivity.getTestMediaSource();
      networkRequest = testActivity.getMockNetwork();
    });
//        testScenario = activityRule.getScenario();
  }

  @After
  public void cleanup() {
    if (httpServer != null) {
      httpServer.kill();
    }
    finishActivity();
//        testScenario.close();
  }

//    public abstract Bundle getActivityOptions();

  public void jamNetwork() {
    testActivity.runOnUiThread(() -> {
      startedJammingTheNetworkAt = System.currentTimeMillis();
      httpServer.jamNetwork(networkJamPeriodInMs, networkJamFactor, true);
    });
  }

  public void exitActivity() {
    testActivity.runOnUiThread(() -> testActivity.finish());
  }

  public CheckupResult checkPlaybackPeriodAtIndex(int index, long expectedPlayPeriod)
      throws JSONException {
    CheckupResult result = new CheckupResult();
    int playIndex = networkRequest.getIndexForNextEvent(index, PlayEvent.TYPE);
    int playingIndex = networkRequest.getIndexForNextEvent(index, PlayingEvent.TYPE);
    int pauseIndex = networkRequest.getIndexForNextEvent(index, PauseEvent.TYPE);
    // Play index not necessary
    if (pauseIndex == -1 || playingIndex == -1) {
      fail("Missing basic playback events, playIndex: "
          + playIndex + ", playingIndex: " + playingIndex +
          ", pauseIndex: " + pauseIndex + ", starting at index: " + index +
          ", availableEvents: " + networkRequest.getReceivedEventNames());
    }
    if (!((playIndex < playingIndex) && (playingIndex < pauseIndex))) {
      fail("Basic playback events not ordered correctly, playIndex: "
          + playIndex + ", playingIndex: " + playingIndex +
          ", pauseIndex: " + pauseIndex + ", starting at index: " + index +
          ", availableEvents: " + networkRequest.getReceivedEventNames());
    }
    long playbackPeriod = networkRequest.getCreationTimeForEvent(pauseIndex) -
        networkRequest.getCreationTimeForEvent(playingIndex);
    if (Math.abs(playbackPeriod - expectedPlayPeriod) > 500) {
      fail("Reported play period: " + playbackPeriod + " do not match expected play period: "
          + expectedPlayPeriod + ", playingIndex: " + playingIndex +
          ", pauseIndex: " + pauseIndex + ", starting at event index: " + index +
          ", availableEvents: " + networkRequest.getReceivedEventNames());
    }
    result.eventIndex = pauseIndex;
    result.playbackPeriod = playbackPeriod;
    return result;
  }

  public CheckupResult checkPausePeriodAtIndex(int index, long expectedPausePeriod)
      throws JSONException {
    CheckupResult result = new CheckupResult();
    int playIndex = networkRequest.getIndexForNextEvent(index, PlayEvent.TYPE);
    int playingIndex = networkRequest.getIndexForNextEvent(index, PlayingEvent.TYPE);
    int pauseIndex = networkRequest.getIndexForNextEvent(index, PauseEvent.TYPE);
    if (playIndex == -1 || pauseIndex == -1 || playingIndex == -1) {
      fail("Missing basic playback events, playIndex: "
          + playIndex + ", playingIndex: " + playingIndex +
          ", pauseIndex: " + pauseIndex + ", starting at index: " + index +
          ", availableEvents: " + networkRequest.getReceivedEventNames());
    }
    if (!((playIndex < playingIndex) && (playIndex > pauseIndex))) {
      fail("Basic playback events not ordered correctly, pauseIndex: "
          + pauseIndex + ", playIndex: " + playIndex +
          ", playingIndex: " + playingIndex + ", starting at index: " + index +
          ", availableEvents: " + networkRequest.getReceivedEventNames());
    }
    long pausekPeriod = networkRequest.getCreationTimeForEvent(playIndex) -
        networkRequest.getCreationTimeForEvent(pauseIndex);
    if (Math.abs(pausekPeriod - expectedPausePeriod) > 500) {
      fail("Reported pause period: " + pausekPeriod + " do not match expected play period: "
          + expectedPausePeriod + ", playIndex: " + playIndex +
          ", pauseIndex: " + pauseIndex + ", starting at event index: " + index +
          ", availableEvents: " + networkRequest.getReceivedEventNames());
    }
    result.pausePeriod = pausekPeriod;
    result.eventIndex = playingIndex;
    return result;
  }

  public CheckupResult checkSeekAtIndex(int index) throws JSONException {
    CheckupResult result = new CheckupResult();
    int seekingIndex = networkRequest.getIndexForNextEvent(index, SeekingEvent.TYPE);
    int seekedIndex = networkRequest.getIndexForNextEvent(index, SeekedEvent.TYPE);
    if (seekingIndex == -1 || seekedIndex == -1) {
      fail("Missing seeekingEvent or seekEvent at event index: " + index +
          ", availableEvents: " + networkRequest.getReceivedEventNames());
    }
    if (seekedIndex < seekingIndex) {
      fail("seeked event is preceding the seeking event at event index: " + index +
          ", availableEvents: " + networkRequest.getReceivedEventNames());
    }
    result.eventIndex = seekedIndex;
    result.seekPeriod = networkRequest.getCreationTimeForEvent(seekedIndex) -
        networkRequest.getCreationTimeForEvent(seekingIndex);
    return result;
  }

  public void pausePlayer() {
    // Pause video
    testActivity.runOnUiThread(() -> {
      if (pauseButton != null) {
        pauseButton.performClick();
      } else {
        pView.getPlayer().getConfig().getPlaybackConfig().setAutoplayEnabled(false);
      }
    });
  }

  public void resumePlayer() {
    testActivity.runOnUiThread(() -> {
      if (playButton != null) {
        playButton.performClick();
      } else {
        pView.getPlayer().getConfig().getPlaybackConfig().setAutoplayEnabled(false);
      }
    });
  }

  public void backgroundActivity() {
    UiDevice device = UiDevice.getInstance(getInstrumentation());
    device.pressHome();
  }

  public void finishActivity() {
    try {
      if (!testActivityFinished && testActivity != null) {
        testActivity.finish();
        testActivityFinished = true;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  protected Activity getActivityInstance() {
    getInstrumentation().runOnMainSync(() -> {
      Collection<Activity> resumedActivities =
          ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED);
      for (Activity activity : resumedActivities) {
        currentActivity = activity;
        break;
      }
    });

    return currentActivity;
  }

  protected String getExceptionFullTraceAndMessage(Exception e) {
    String lStackTraceString = "";
    for (StackTraceElement lStEl : e.getStackTrace()) {
      lStackTraceString += lStEl.toString() + "\n";
    }
    lStackTraceString += e.getMessage();
    return lStackTraceString;
  }

  class CheckupResult {

    int eventIndex;
    long pausePeriod;
    long seekPeriod;
    long playbackPeriod;
  }
}
