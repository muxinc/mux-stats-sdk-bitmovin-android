package com.mux.stats.sdk.muxstats.bitmovinplayer;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
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
import com.mux.stats.sdk.core.events.playback.RenditionChangeEvent;
import com.mux.stats.sdk.core.events.playback.SeekedEvent;
import com.mux.stats.sdk.core.events.playback.SeekingEvent;
import com.mux.stats.sdk.core.events.playback.TimeUpdateEvent;
import com.mux.stats.sdk.core.model.CustomerData;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.core.model.ViewData;
import com.mux.stats.sdk.core.util.MuxLogger;
import com.mux.stats.sdk.muxstats.IDevice;
import com.mux.stats.sdk.muxstats.INetworkRequest;
import com.mux.stats.sdk.muxstats.IPlayerListener;
import com.mux.stats.sdk.muxstats.LogPriority;
import com.mux.stats.sdk.muxstats.MuxErrorException;
import com.mux.stats.sdk.muxstats.MuxStats;
import java.lang.ref.WeakReference;
import com.bitmovin.player.PlayerView;
import java.util.ArrayList;

import static android.os.SystemClock.elapsedRealtime;
import static com.mux.stats.sdk.muxstats.bitmovinplayer.Util.secondsToMs;

public class MuxStatsSDKBitmovinPlayer extends EventBus implements IPlayerListener {
    public static final String TAG = "MuxStatsSDKPlayer";

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
    protected boolean isBuffering;
    protected boolean inAdBreak;
    protected boolean inAdPlayback;

    protected double playbackPosition;
    public int streamType = -1;
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
        // TODO Replace this with a dynamic way to get a player version
        MuxStats.setHostDevice(new MuxDevice(ctx, "2.42.0"));
        MuxStats.setHostNetworkApi(networkRequest);
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
    }

    public void addBitmovinPlayerEventListener(IBitmovinPlayerEventsListener listener) {
        if (!registeredPlayerListeners.contains(listener)) {
            synchronized (this) {
                registeredPlayerListeners.add(listener);
            }
        }
    }

    public void removeBitmovinPlayerEventListener(IBitmovinPlayerEventsListener listener) {
        registeredPlayerListeners.remove(listener);
    }

    public MuxStatsSDKBitmovinPlayer(Context ctx, final PlayerView player, String playerName,
        CustomerData customerData) {
        this(ctx, player, playerName, customerData, false, new MuxNetworkRequests());
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
        };

    private final EventListener<PlayerEvent.TimeChanged> onTimeChangeListener =
        timeChangeEvent -> {
            playbackPosition = timeChangeEvent.getTime();
        };

    private final EventListener<PlayerEvent.Play> onPlayListener =
        playEvent -> {
            dispatch(new PlayEvent(null));
        };

    private final EventListener<PlayerEvent.Playing> onPlayingListener =
        playingEvent -> {
            dispatch(new PlayingEvent(null));
        };

    private final EventListener<PlayerEvent.Paused> onPausedListener =
        pausedEvent -> {
            dispatch(new PauseEvent(null));
        };

    private final EventListener<PlayerEvent.PlaybackFinished> onPlaybackFinishedListener =
        playbackFinishedEvent -> {
            dispatch(new EndedEvent(null));
        };

    private final EventListener<PlayerEvent.Seek> onSeekListener =
        seekingEvent -> {
            dispatch(new SeekingEvent(null));
        };

    private final EventListener<PlayerEvent.Seeked> onSeekedListener =
        seekedEvent -> {
            dispatch(new SeekedEvent(null));
        };

    private final EventListener<PlayerEvent.Metadata> onMetadataListener =
        metadataEvent -> {
            // Do nothing
        };

    private final EventListener<PlayerEvent.StallStarted> onStallStartedListener =
        stallStartedEvent -> {
            // Buffering started
            isBuffering = true;
        };

    private final EventListener<PlayerEvent.StallEnded> onStallEndedListener =
        stallEndedEvent -> {
            // Buffering ended
            isBuffering = false;
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
        };

    private final EventListener<PlayerEvent.AdStarted> onAdStartedListener =
        adStartedEvent -> {
            inAdPlayback = true;
            dispatch(new AdPlayEvent(null));
            dispatch(new AdPlayingEvent(null));
        };

    private final EventListener<PlayerEvent.AdFinished> onAdFinishedListener =
        stallEndedEvent -> {
            inAdPlayback = false;
            dispatch(new AdEndedEvent(null));
        };

    private final EventListener<PlayerEvent.AdBreakFinished> onAdBreakFinishedListener =
        stallEndedEvent -> {
            inAdBreak = false;
            // Reset all of our state correctly for getting out of ads
            dispatch(new AdBreakEndEvent(null));
            // For everything but preroll ads, we need to simulate a play event to resume
            if (getCurrentPosition() == 0) {
                dispatch(new PlayEvent(null));
            }
        };

    private final EventListener<PlayerEvent.AdError> onAdErrorListener =
        stallEndedEvent -> {
            dispatch(new AdErrorEvent(null));
        };

    private final EventListener<SourceEvent.Loaded> onSourceLoadedListener =
        sourceLoadedEvent -> {
            mimeType = sourceLoadedEvent.getSource().getConfig().getType().name();
            CustomerVideoData videoData = muxStats.getCustomerVideoData();
            videoData.setVideoSourceUrl(sourceLoadedEvent.getSource().getConfig().getUrl());
            muxStats.updateCustomerData(null, videoData);
        };


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
        return secondsToMs(player.get().getPlayer().getDuration());
    }

    @Override
    public boolean isPaused() {
        return player.get().getPlayer().isPaused();
    }

    @Override
    public boolean isBuffering() {
        return isBuffering;
    }

    @Override
    public int getPlayerViewWidth() {
        if(player != null && player.get() != null) {
            return pxToDp(player.get().getMeasuredWidth());
        }
        return 0;
    }

    @Override
    public int getPlayerViewHeight() {
        if(player != null && player.get() != null) {
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

    static class MuxDevice implements IDevice {
        private static final String PLAYER_SOFTWARE = "THEOplayer";

        private String deviceId;
        private String appName = "";
        private String appVersion = "";
        private String version = "";

        MuxDevice(Context ctx, String version) {
            deviceId = Settings.Secure.getString(ctx.getContentResolver(),
                Settings.Secure.ANDROID_ID);
            this.version = version;
            try {
                PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
                appName = pi.packageName;
                appVersion = pi.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                MuxLogger.d(TAG, "could not get package info");
            }
        }

        @Override
        public String getHardwareArchitecture() {
            return Build.HARDWARE;
        }

        @Override
        public String getOSFamily() {
            return "Android";
        }

        @Override
        public String getOSVersion() {
            return Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")";
        }

        @Override
        public String getManufacturer() {
            return Build.MANUFACTURER;
        }

        @Override
        public String getModelName() {
            return Build.MODEL;
        }

        @Override
        public String getPlayerVersion() {
            return this.version;
        }

        @Override
        public String getDeviceId() {
            return deviceId;
        }

        @Override
        public String getAppName() {
            return appName;
        }

        @Override
        public String getAppVersion() {
            return appVersion;
        }

        @Override
        public String getPluginName() {
            return BuildConfig.MUX_PLUGIN_NAME;
        }

        @Override
        public String getPluginVersion() {
            return BuildConfig.MUX_PLUGIN_VERSION;
        }

        @Override
        public String getPlayerSoftware() { return PLAYER_SOFTWARE; }

        @Override
        public String getNetworkConnectionType() {
            return null;
        }

        @Override
        public long getElapsedRealtime() {
            return elapsedRealtime();
        }

        @Override
        public void outputLog(LogPriority logPriority, String s, String s1) {

        }

        @Override
        public void outputLog(String tag, String msg) {
            Log.v(tag, msg);
        }
    }
}

