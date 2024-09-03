package com.mux.stats.sdk.muxstats.bitmovinplayer;

import static com.mux.stats.sdk.muxstats.bitmovinplayer.Util.secondsToMs;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;

import com.bitmovin.player.PlayerView;
import com.bitmovin.player.api.event.EventListener;
import com.bitmovin.player.api.event.PlayerEvent;
import com.bitmovin.player.api.event.SourceEvent;
import com.mux.stats.sdk.core.MuxSDKViewOrientation;
import com.mux.stats.sdk.core.events.EventBus;
import com.mux.stats.sdk.core.events.IEvent;
import com.mux.stats.sdk.core.events.InternalErrorEvent;
import com.mux.stats.sdk.core.events.playback.AdBreakEndEvent;
import com.mux.stats.sdk.core.events.playback.AdBreakStartEvent;
import com.mux.stats.sdk.core.events.playback.AdEndedEvent;
import com.mux.stats.sdk.core.events.playback.AdErrorEvent;
import com.mux.stats.sdk.core.events.playback.AdPlayEvent;
import com.mux.stats.sdk.core.events.playback.AdPlayingEvent;
import com.mux.stats.sdk.core.events.playback.EndedEvent;
import com.mux.stats.sdk.core.events.playback.PauseEvent;
import com.mux.stats.sdk.core.events.playback.PlayEvent;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.core.events.playback.RebufferEndEvent;
import com.mux.stats.sdk.core.events.playback.RebufferStartEvent;
import com.mux.stats.sdk.core.events.playback.RenditionChangeEvent;
import com.mux.stats.sdk.core.events.playback.SeekedEvent;
import com.mux.stats.sdk.core.events.playback.SeekingEvent;
import com.mux.stats.sdk.core.events.playback.TimeUpdateEvent;
import com.mux.stats.sdk.core.model.CustomerData;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.core.model.ViewData;
import com.mux.stats.sdk.muxstats.INetworkRequest;
import com.mux.stats.sdk.muxstats.IPlayerListener;
import com.mux.stats.sdk.muxstats.MuxDataSdk;
import com.mux.stats.sdk.muxstats.MuxErrorException;
import com.mux.stats.sdk.muxstats.MuxNetwork;
import com.mux.stats.sdk.muxstats.MuxSDKViewPresentation;
import com.mux.stats.sdk.muxstats.MuxStats;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class MuxStatsSDKBitmovinPlayer extends EventBus implements IPlayerListener {
  public static final String TAG = "MuxStatsSDKPlayer";

  protected static final long TIME_TO_WAIT_AFTER_FIRST_FRAME_RENDERED = 50; // in ms

  public enum PlayerState {
    BUFFERING, REBUFFERING, SEEKING, SEEKED, ERROR, PAUSED, PLAY, PLAYING, PLAYING_ADS,
    FINISHED_PLAYING_ADS, INIT, ENDED
  }

  protected MuxStats muxStats;
  protected WeakReference<PlayerView> player;
  protected WeakReference<Context> contextRef;

  protected static final int ERROR_UNKNOWN = -1;
  protected static final int ERROR_DRM = -2;
  protected static final int ERROR_IO = -3;

  protected String mimeType;
  protected int sourceWidth;
  protected int sourceHeight;
  protected Integer sourceAdvertisedBitrate;
  protected Float sourceAdvertisedFramerate;
  protected boolean inAdBreak;
  protected boolean inAdPlayback;

  protected double playbackPosition;
  public int streamType = -1;
  protected PlayerState state;
  boolean seekingInProgress;
  boolean firstFrameReceived;
  protected int numberOfPauseEventsSent = 0;
  protected int numberOfPlayEventsSent = 0;
  long firstFrameRenderedAt = 0;
  ArrayList<IBitmovinPlayerEventsListener> registeredPlayerListeners = new ArrayList<>();

  public MuxStatsSDKBitmovinPlayer(
      Context ctx,
      PlayerView player,
      String playerName,
      CustomerData customerData,
      boolean sentryEnabled,
      INetworkRequest networkRequest
  ) {
    super();
    this.player = new WeakReference<>(player);
    this.contextRef = new WeakReference<>(ctx);
    MuxStats.setHostDevice(
        new MuxDataSdk.AndroidDevice(
            ctx,
            BuildConfig.FLAVOR.substring(1).replace('_', '.'),
            BuildConfig.MUX_PLUGIN_NAME,
            BuildConfig.LIB_VERSION,
            "Bitmovin Player"
        )
    );
    MuxStats.setHostNetworkApi(networkRequest == null ?
        new MuxNetwork(MuxStats.getHostDevice()) : networkRequest);
    muxStats = new MuxStats(this, playerName, customerData);
    addListener(muxStats);


    if (player.getPlayer().getCurrentTime() > 0) {
      // playback started before muxStats was initialized
      dispatch(new PlayEvent(null));
      dispatch(new PlayingEvent(null));
      dispatch(new TimeUpdateEvent(null));
    }

    player.getPlayer().on(PlayerEvent.Error.class, onPlayerErrorListener);
    // Handle rendition change
    player.getPlayer().on(PlayerEvent.VideoSizeChanged.class, onVideoSizeChangeListener);
    player.getPlayer().on(PlayerEvent.TimeChanged.class, onTimeChangeListener);
    player.getPlayer().on(PlayerEvent.Play.class, onPlayListener);
    player.getPlayer().on(PlayerEvent.Playing.class, onPlayingListener);
    player.getPlayer().on(PlayerEvent.Paused.class, onPausedListener);
    player.getPlayer().on(PlayerEvent.PlaybackFinished.class, onPlaybackFinishedListener);
    player.getPlayer().on(PlayerEvent.Seek.class, onSeekListener);
    player.getPlayer().on(PlayerEvent.Seeked.class, onSeekedListener);
    player.getPlayer().on(PlayerEvent.Metadata.class, onMetadataListener);
    player.getPlayer().on(PlayerEvent.StallStarted.class, onStallStartedListener);
    player.getPlayer().on(PlayerEvent.StallEnded.class, onStallEndedListener);

    // Ads event listeners
    player.getPlayer().on(PlayerEvent.AdBreakStarted.class, onAdBreakStartedListener);
    player.getPlayer().on(PlayerEvent.AdStarted.class, onAdStartedListener);
    player.getPlayer().on(PlayerEvent.AdFinished.class, onAdFinishedListener);
    player.getPlayer().on(PlayerEvent.AdBreakFinished.class, onAdBreakFinishedListener);
    player.getPlayer().on(PlayerEvent.AdError.class, onAdErrorListener);

    player.getPlayer().on(SourceEvent.Loaded.class, onSourceLoadedListener);

    player.getPlayer().on(PlayerEvent.FullscreenEnabled.class, onFullscreenEnabledListener);
    player.getPlayer().on(PlayerEvent.FullscreenDisabled.class, onFullscreenDisabledListener);
  }

  public void addBitmovinPlayerEventListener(IBitmovinPlayerEventsListener listener) {
    if (!registeredPlayerListeners.contains(listener)) {
      registeredPlayerListeners.add(listener);
    }
  }

  public void removeBitmovinPlayerEventListener(IBitmovinPlayerEventsListener listener) {
    registeredPlayerListeners.remove(listener);
  }

  public MuxStatsSDKBitmovinPlayer(Context ctx, final PlayerView player, String playerName,
                                   CustomerData customerData) {
    this(ctx, player, playerName, customerData, false, null);
  }

  private final EventListener<PlayerEvent.Error> onPlayerErrorListener = errorEvent -> {
    internalError(new MuxErrorException(0, errorEvent.getMessage()));
  };

  private final EventListener<PlayerEvent.VideoSizeChanged> onVideoSizeChangeListener =
      videoChangeEvent -> {
        //TODO see how to get framerate
//            VideoQuality newQuality = videoPlaybackQualityChangedEvent.getNewVideoQuality();
//            if (newQuality.getFrameRate() > 0) {
//                sourceAdvertisedFramerate = newQuality.getFrameRate();
//            }
        sourceWidth = videoChangeEvent.getWidth();
        sourceHeight = videoChangeEvent.getHeight();
        RenditionChangeEvent event = new RenditionChangeEvent(null);
        dispatch(event);
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onVideoSizeChangedListener(videoChangeEvent);
        }
      };

  private final EventListener<PlayerEvent.TimeChanged> onTimeChangeListener =
      timeChangeEvent -> {
        playbackPosition = timeChangeEvent.getTime();
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onTimeChangeListener(timeChangeEvent);
        }
      };

  private final EventListener<PlayerEvent.Play> onPlayListener =
      playEvent -> {
        play();
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onPlayListener(playEvent);
        }
      };

  private final EventListener<PlayerEvent.Playing> onPlayingListener =
      playingEvent -> {
        playing();
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onPlayingListener(playingEvent);
        }
      };

  private final EventListener<PlayerEvent.Paused> onPausedListener =
      pausedEvent -> {
        pause();
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onPausedListener(pausedEvent);
        }
      };

  private final EventListener<PlayerEvent.PlaybackFinished> onPlaybackFinishedListener =
      playbackFinishedEvent -> {
        ended();
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onPlaybackFinishedListener(playbackFinishedEvent);
        }
      };

  private final EventListener<PlayerEvent.Seek> onSeekListener =
      seekingEvent -> {
        seeking();
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onSeekListener(seekingEvent);
        }
      };

  private final EventListener<PlayerEvent.Seeked> onSeekedListener =
      seekedEvent -> {
        seeked();
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onSeekedListener(seekedEvent);
        }
      };

  private final EventListener<PlayerEvent.Metadata> onMetadataListener =
      metadataEvent -> {
        // Do nothing
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onMetadataListener(metadataEvent);
        }
      };

  private final EventListener<PlayerEvent.StallStarted> onStallStartedListener =
      stallStartedEvent -> {
        // Buffering started
        buffering();
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onStallStartedListener(stallStartedEvent);
        }
      };

  private final EventListener<PlayerEvent.StallEnded> onStallEndedListener =
      stallEndedEvent -> {
        // Buffering ended
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onStallEndedListener(stallEndedEvent);
        }
      };

  private final EventListener<PlayerEvent.AdBreakStarted> onAdBreakStartedListener =
      adBreakStartedEvent -> {
        inAdBreak = true;
        // Record that we're in an ad break so we can supress standard play/playing/pause events
        AdBreakStartEvent adBreakEvent = new AdBreakStartEvent(null);
        // For everything but preroll ads, we need to simulate a pause event
        ViewData viewData = new ViewData();
        String adId = adBreakStartedEvent.getAdBreak().getId();
        String adCreativeId = adBreakStartedEvent.getAdBreak().getId();
        viewData.setViewPrerollAdId(adId);
        viewData.setViewPrerollCreativeId(adCreativeId);
        adBreakEvent.setViewData(viewData);
        dispatch(adBreakEvent);
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onAdBreakStartedListener(adBreakStartedEvent);
        }
      };

  private final EventListener<PlayerEvent.AdStarted> onAdStartedListener =
      adStartedEvent -> {
        inAdPlayback = true;
        dispatch(new AdPlayEvent(null));
        dispatch(new AdPlayingEvent(null));
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onAdStartedListener(adStartedEvent);
        }
      };

  private final EventListener<PlayerEvent.AdFinished> onAdFinishedListener =
      adFinishedEvent -> {
        inAdPlayback = false;
        dispatch(new AdEndedEvent(null));
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onAdFinishedListener(adFinishedEvent);
        }
      };

  private final EventListener<PlayerEvent.AdBreakFinished> onAdBreakFinishedListener =
      adBreakFinishedEvent -> {
        inAdBreak = false;
        // Reset all of our state correctly for getting out of ads
        dispatch(new AdBreakEndEvent(null));
        // For everything but preroll ads, we need to simulate a play event to resume
        if (getCurrentPosition() == 0) {
          dispatch(new PlayEvent(null));
        }
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onAdBreakFinishedListener(adBreakFinishedEvent);
        }
      };

  private final EventListener<PlayerEvent.AdError> onAdErrorListener =
      adErrorEvent -> {
        dispatch(new AdErrorEvent(null));
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onAdErrorListener(adErrorEvent);
        }
      };

  private final EventListener<SourceEvent.Loaded> onSourceLoadedListener =
      sourceLoadedEvent -> {
        mimeType = sourceLoadedEvent.getSource().getConfig().getType().name();
        CustomerVideoData videoData = muxStats.getCustomerVideoData();
        videoData.setVideoSourceUrl(sourceLoadedEvent.getSource().getConfig().getUrl());
        muxStats.updateCustomerData(null, videoData);
        for (IBitmovinPlayerEventsListener listener : registeredPlayerListeners) {
          listener.onSourceLoadedListener(sourceLoadedEvent);
        }
      };

  private final EventListener<PlayerEvent.FullscreenEnabled> onFullscreenEnabledListener =
      fullscreenEnabledEvent -> {
        muxStats.presentationChange(MuxSDKViewPresentation.FULLSCREEN);
      };

  private final EventListener<PlayerEvent.FullscreenDisabled> onFullscreenDisabledListener =
      fullscreenDisabledEvent -> {
        muxStats.presentationChange(MuxSDKViewPresentation.NORMAL);
      };

  protected void buffering() {
    if (state == PlayerState.REBUFFERING || seekingInProgress
        || state == PlayerState.SEEKED) {
      // ignore
      return;
    }
    // If we are going from playing to buffering then this is rebuffer event
    if (state == PlayerState.PLAYING) {
      rebufferingStarted();
      return;
    }
    // This is initial buffering event before playback starts
    state = PlayerState.BUFFERING;
    dispatch(new TimeUpdateEvent(null));
  }

  protected void pause() {
    if (state == PlayerState.SEEKED && numberOfPauseEventsSent > 0) {
      // No pause event after seeked
      return;
    }
    if (state == PlayerState.REBUFFERING) {
      rebufferingEnded();
    }
    if (seekingInProgress) {
      seeked();
      return;
    }
    state = PlayerState.PAUSED;
    dispatch(new PauseEvent(null));
  }

  protected void play() {
    // If this is the first play event it may be very important not to be skipped
    // In all other cases skip this play event
    if (
        (state == PlayerState.REBUFFERING
            || seekingInProgress
            || state == PlayerState.SEEKED) &&
            (numberOfPlayEventsSent > 0)
    ) {
      // Ignore play event after rebuffering and Seeking
      return;
    }
    state = PlayerState.PLAY;
    dispatch(new PlayEvent(null));
  }

  protected void playing() {
    if (seekingInProgress) {
      // We will dispatch playing event after seeked event
      return;
    }
    if (state == PlayerState.PAUSED || state == PlayerState.FINISHED_PLAYING_ADS) {
      play();
    }
    if (state == PlayerState.REBUFFERING) {
      rebufferingEnded();
    }

    state = PlayerState.PLAYING;
    dispatch(new PlayingEvent(null));
  }

  protected void rebufferingStarted() {
    state = PlayerState.REBUFFERING;
    dispatch(new RebufferStartEvent(null));
  }

  protected void rebufferingEnded() {
    dispatch(new RebufferEndEvent(null));
  }

  protected void seeking() {
    if (state == PlayerState.PLAYING) {
      dispatch(new PauseEvent(null));
    }
    state = PlayerState.SEEKING;
    seekingInProgress = true;
    firstFrameRenderedAt = -1;
    dispatch(new SeekingEvent(null));
    firstFrameReceived = false;
  }

  protected void seeked() {
    if (seekingInProgress) {
      // the player was seeking while paused
      dispatch(new SeekedEvent(null));
      seekingInProgress = false;
      state = PlayerState.SEEKED;
    }
  }

  protected void ended() {
    dispatch(new PauseEvent(null));
    dispatch(new EndedEvent(null));
    state = PlayerState.ENDED;
  }

  // IPlayerListener
  @Override
  public long getCurrentPosition() {
    return secondsToMs(playbackPosition);
  }

  @SuppressWarnings("unused")
  public void updateCustomerData(CustomerPlayerData customPlayerData, CustomerVideoData customVideoData) {
    muxStats.updateCustomerData(customPlayerData, customVideoData);
  }

  public CustomerVideoData getCustomerVideoData() {
    return muxStats.getCustomerVideoData();
  }

  public CustomerPlayerData getCustomerPlayerData() {
    return muxStats.getCustomerPlayerData();
  }

  public void enableMuxCoreDebug(boolean enable, boolean verbose) {
    muxStats.allowLogcatOutput(enable, verbose);
  }

  @Override
  public String getMimeType() {
    return mimeType;
  }

  @Override
  public Integer getSourceWidth() {
    return sourceWidth;
  }

  @Override
  public Integer getSourceHeight() {
    return sourceHeight;
  }

  @Override
  public Integer getSourceAdvertisedBitrate() {
    return sourceAdvertisedBitrate;
  }

  @Override
  public Float getSourceAdvertisedFramerate() {
    return sourceAdvertisedFramerate;
  }

  @Override
  public Long getSourceDuration() {
    return secondsToMs(playbackPosition);
  }

  @Override
  public boolean isPaused() {
    return player.get().getPlayer().isPaused();
  }

  @Override
  public boolean isBuffering() {
    return state == PlayerState.BUFFERING;
  }

  @Override
  public int getPlayerViewWidth() {
    if (player != null && player.get() != null) {
      return pxToDp(player.get().getMeasuredWidth());
    }
    return 0;
  }

  @Override
  public int getPlayerViewHeight() {
    if (player != null && player.get() != null) {
      return pxToDp(player.get().getMeasuredHeight());
    }
    return 0;
  }

  // Latency metrics
  @Override
  public Long getPlayerProgramTime() {
    return null;
  }

  @Override
  public Long getPlayerManifestNewestTime() {
    return null;
  }

  @Override
  public Long getVideoHoldback() {
    return null;
  }

  @Override
  public Long getVideoPartHoldback() {
    return null;
  }

  @Override
  public Long getVideoPartTargetDuration() {
    return null;
  }

  @Override
  public Long getVideoTargetDuration() {
    return null;
  }

  // EventBus

  @Override
  public void dispatch(IEvent event) {
    if (player != null && player.get() != null && muxStats != null) {
      super.dispatch(event);
    }
  }

  protected void internalError(Exception error) {
    Log.d(TAG, "Internal error");
    if (error instanceof MuxErrorException) {
      MuxErrorException muxError = (MuxErrorException) error;
      dispatch(new InternalErrorEvent(muxError.getCode(), muxError.getMessage()));
    } else {
      dispatch(new InternalErrorEvent(ERROR_UNKNOWN, error.getClass().getCanonicalName() + " - " + error.getMessage()));
    }
  }

  // Exposed methods to change stats

  public void videoChange(CustomerVideoData customerVideoData) {
    muxStats.videoChange(customerVideoData);
  }

  public void programChange(CustomerVideoData customerVideoData) {
    muxStats.programChange(customerVideoData);
  }

  public void orientationChange(MuxSDKViewOrientation orientation) {
    muxStats.orientationChange(orientation);
  }

  public void setPlayerView(PlayerView playerView) {
    this.player = new WeakReference<>(playerView);
  }

  public void setPlayerSize(int width, int height) {
    muxStats.setPlayerSize(width, height);
  }

  public void setScreenSize(int width, int height) {
    muxStats.setScreenSize(width, height);
  }

  public void error(MuxErrorException e) {
    muxStats.error(e);
  }

  public void setAutomaticErrorTracking(boolean enabled) {
    muxStats.setAutomaticErrorTracking(enabled);
  }

  public void setStreamType(int type) {
    streamType = type;
  }

  public void release() {
    muxStats.release();
    muxStats = null;
    player = null;
  }

  private int pxToDp(int px) {
    DisplayMetrics displayMetrics = contextRef.get().getResources().getDisplayMetrics();
    int dp = Math.round(px / (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    return dp;
  }
}
