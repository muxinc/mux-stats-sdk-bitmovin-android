package com.mux.stats.sdk.muxstats.bitmovinplayer;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.bitmovin.player.BitmovinPlayer;
import com.bitmovin.player.BitmovinPlayerView;
import com.bitmovin.player.api.event.data.BitmovinPlayerEvent;
import com.bitmovin.player.api.event.data.ErrorEvent;
import com.bitmovin.player.api.event.data.MetadataEvent;
import com.bitmovin.player.api.event.data.PausedEvent;
import com.bitmovin.player.api.event.data.SeekEvent;
import com.bitmovin.player.api.event.data.SourceLoadedEvent;
import com.bitmovin.player.api.event.data.StallEndedEvent;
import com.bitmovin.player.api.event.data.StallStartedEvent;
import com.bitmovin.player.api.event.data.TimeChangedEvent;
import com.bitmovin.player.api.event.data.VideoSizeChangedEvent;
import com.bitmovin.player.api.event.listener.OnErrorListener;
import com.bitmovin.player.api.event.listener.OnMetadataListener;
import com.bitmovin.player.api.event.listener.OnPausedListener;
import com.bitmovin.player.api.event.listener.OnPlayListener;
import com.bitmovin.player.api.event.listener.OnPlayingListener;
import com.bitmovin.player.api.event.listener.OnSeekListener;
import com.bitmovin.player.api.event.listener.OnSeekedListener;
import com.bitmovin.player.api.event.listener.OnSourceLoadedListener;
import com.bitmovin.player.api.event.listener.OnStallEndedListener;
import com.bitmovin.player.api.event.listener.OnStallStartedListener;
import com.bitmovin.player.api.event.listener.OnTimeChangedListener;
import com.bitmovin.player.api.event.listener.OnVideoSizeChangedListener;
import com.bitmovin.player.model.Metadata;
import com.mux.stats.sdk.core.MuxSDKViewOrientation;
import com.mux.stats.sdk.core.events.EventBus;
import com.mux.stats.sdk.core.events.IEvent;
import com.mux.stats.sdk.core.events.InternalErrorEvent;
import com.mux.stats.sdk.core.events.playback.EndedEvent;
import com.mux.stats.sdk.core.events.playback.PauseEvent;
import com.mux.stats.sdk.core.events.playback.PlayEvent;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.core.events.playback.SeekedEvent;
import com.mux.stats.sdk.core.events.playback.SeekingEvent;
import com.mux.stats.sdk.core.events.playback.TimeUpdateEvent;
import com.mux.stats.sdk.core.events.playback.VideoChangeEvent;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.core.util.MuxLogger;
import com.mux.stats.sdk.muxstats.IDevice;
import com.mux.stats.sdk.muxstats.IPlayerListener;
import com.mux.stats.sdk.muxstats.MuxErrorException;
import com.mux.stats.sdk.muxstats.MuxStats;
import java.lang.ref.WeakReference;

import static android.os.SystemClock.elapsedRealtime;
import static com.mux.stats.sdk.muxstats.bitmovinplayer.Util.secondsToMs;

public class MuxStatsSDKBitmovinPlayer extends EventBus implements IPlayerListener {
    public static final String TAG = "MuxStatsSDKTHEOplayer";

    protected PlayerState state;
    protected MuxStats muxStats;
    protected WeakReference<BitmovinPlayerView> player;

    protected static final int ERROR_UNKNOWN = -1;
    protected static final int ERROR_DRM = -2;
    protected static final int ERROR_IO = -3;

    protected String mimeType;
    protected int sourceWidth;
    protected int sourceHeight;
    protected Integer sourceAdvertisedBitrate;
    protected Float sourceAdvertisedFramerate;
    protected long sourceDuration;
    protected boolean playWhenReady;
    protected boolean isBuffering;

    protected double playbackPosition;

    public int streamType = -1;

    public enum PlayerState {
        BUFFERING, ERROR, PAUSED, PLAY, PLAYING, INIT
    }


    public MuxStatsSDKBitmovinPlayer(Context ctx, final BitmovinPlayerView player, String playerName,
                                     CustomerPlayerData customerPlayerData, CustomerVideoData customerVideoData) {
        super();
        this.player = new WeakReference<>(player);
        // TODO Replace this with a dynamic way to get a player version
        MuxStats.setHostDevice(new MuxDevice(ctx, "2.42.0"));
        MuxStats.setHostNetworkApi(new MuxNetworkRequests());
        muxStats = new MuxStats(this, playerName, customerPlayerData, customerVideoData);
        addListener(muxStats);


        player.getPlayer().addEventListener(new OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(VideoSizeChangedEvent videoSizeChangedEvent) {
                sourceWidth = videoSizeChangedEvent.getWidth();
                sourceHeight = videoSizeChangedEvent.getHeight();
                dispatch(new VideoChangeEvent(null));
            }
        });

        player.getPlayer().addEventListener(new OnTimeChangedListener() {
            @Override
            public void onTimeChanged(TimeChangedEvent timeChangedEvent) {
                playbackPosition = timeChangedEvent.getTime();
                dispatch(new TimeUpdateEvent(null));
            }
        });


        player.getPlayer().addEventListener(new OnPlayListener() {
            @Override
            public void onPlay(com.bitmovin.player.api.event.data.PlayEvent playEvent) {
                play();
            }
        });

        player.getPlayer().addEventListener(new OnPlayingListener() {
            @Override
            public void onPlaying(com.bitmovin.player.api.event.data.PlayingEvent playingEvent) {
                playing();
            }
        });

        player.getPlayer().addEventListener(new OnPausedListener() {
            @Override
            public void onPaused(PausedEvent pausedEvent) {
                if (pausedEvent.getTime() == player.getPlayer().getDuration()) {
                    dispatch(new EndedEvent(null));
                } else {
                    pause();
                }
            }
        });

        player.getPlayer().addEventListener(new OnSeekListener() {
            @Override
            public void onSeek(SeekEvent seekEvent) {
                dispatch(new SeekingEvent(null));
            }
        });

        player.getPlayer().addEventListener(new OnSeekedListener() {
            @Override
            public void onSeeked(com.bitmovin.player.api.event.data.SeekedEvent seekedEvent) {
                dispatch(new SeekedEvent(null));
            }
        });

        player.getPlayer().addEventListener(new OnErrorListener() {
            @Override
            public void onError(ErrorEvent errorEvent) {
                internalError(new MuxErrorException(0, errorEvent.getMessage()));
            }
        });

        player.getPlayer().addEventListener(new OnMetadataListener() {
            @Override
            public void onMetadata(MetadataEvent metadataEvent) {
                Metadata metadata = metadataEvent.getMetadata();
                Log.i(TAG, "Type: " + metadataEvent.getType());
                for (int i = 0; i < metadata.length(); i++){
                    Log.i(TAG, "    Entry: " + metadata.get(i).getType());
                }
            }
        });

        player.getPlayer().addEventListener(new OnStallStartedListener() {
            @Override
            public void onStallStarted(StallStartedEvent stallStartedEvent) {
                isBuffering = true;
            }
        });

        player.getPlayer().addEventListener(new OnStallEndedListener() {
            @Override
            public void onStallEnded(StallEndedEvent stallEndedEvent) {
                isBuffering = false;
            }
        });

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
        // TODO get the mime type
//        try {
//            if (player.get().getPlayer().getSource() != null) {
//                List<TypedSource> sources = player.get().getPlayer().getSource().getSources();
//                return sources.size() > 0 ? sources.get(0).getType().toString() : "";
//            }
//        } catch (Exception e) {
////            e.printStackTrace();
//        }
        return null;
    }

    @Override
    public Integer getSourceWidth() {
        return sourceHeight;
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
            return player.get().getMeasuredWidth();
        }
        return 0;
    }

    @Override
    public int getPlayerViewHeight() {
        if(player != null && player.get() != null) {
            return player.get().getMeasuredHeight();
        }
        return 0;
    }

    // EventBus

    @Override
    public void dispatch(IEvent event) {
        if (player != null && player.get() != null && muxStats != null) {
            super.dispatch(event);
        }
    }

    // Internal methods to change stats
    protected void buffering() {
        state = PlayerState.BUFFERING;
        dispatch(new TimeUpdateEvent(null));
    }

    protected void pause() {
        Log.d(TAG, "Pausing file");
        state = PlayerState.PAUSED;
        dispatch(new PauseEvent(null));
    }

    protected void play() {
        Log.d(TAG, "Playing file");
        state = PlayerState.PLAY;
        dispatch(new PlayEvent(null));
    }

    protected void playing() {
        if (state ==  PlayerState.PAUSED) {
            play();
        }
        state = PlayerState.PLAYING;
        dispatch(new PlayingEvent(null));
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

    public void setPlayerView(BitmovinPlayerView playerView) {
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
        public long getElapsedRealtime() {
            return elapsedRealtime();
        }

        @Override
        public void outputLog(String tag, String msg) {
            Log.v(tag, msg);
        }
    }
}

