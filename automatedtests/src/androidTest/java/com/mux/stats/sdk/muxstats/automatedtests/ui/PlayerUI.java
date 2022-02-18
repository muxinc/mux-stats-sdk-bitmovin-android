/*
 * Bitmovin Player Android SDK
 * Copyright (C) 2017, Bitmovin GmbH, All Rights Reserved
 *
 * This source code and its use and distribution, is subject to the terms
 * and conditions of the applicable license agreement.
 */

package com.mux.stats.sdk.muxstats.automatedtests.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.bitmovin.player.PlayerView;
import com.bitmovin.player.api.Player;
import com.bitmovin.player.api.event.PlayerEvent.AdBreakFinished;
import com.bitmovin.player.api.event.PlayerEvent.AdBreakStarted;
import com.bitmovin.player.api.event.PlayerEvent.AdError;
import com.bitmovin.player.api.event.PlayerEvent.AdFinished;
import com.bitmovin.player.api.event.PlayerEvent.AdStarted;
import com.bitmovin.player.api.event.PlayerEvent.Error;
import com.bitmovin.player.api.event.PlayerEvent.Metadata;
import com.bitmovin.player.api.event.PlayerEvent.Paused;
import com.bitmovin.player.api.event.PlayerEvent.Play;
import com.bitmovin.player.api.event.PlayerEvent.PlaybackFinished;
import com.bitmovin.player.api.event.PlayerEvent.Playing;
import com.bitmovin.player.api.event.PlayerEvent.Seek;
import com.bitmovin.player.api.event.PlayerEvent.Seeked;
import com.bitmovin.player.api.event.PlayerEvent.StallEnded;
import com.bitmovin.player.api.event.PlayerEvent.StallStarted;
import com.bitmovin.player.api.event.PlayerEvent.TimeChanged;
import com.bitmovin.player.api.event.PlayerEvent.VideoSizeChanged;
import com.bitmovin.player.api.event.SourceEvent.Loaded;
import com.bitmovin.player.api.ui.FullscreenHandler;
import com.mux.stats.sdk.muxstats.automatedtests.R;
import com.mux.stats.sdk.muxstats.bitmovinplayer.IBitmovinPlayerEventsListener;
import java.util.Timer;
import java.util.TimerTask;

public class PlayerUI extends RelativeLayout implements IBitmovinPlayerEventsListener {
    private static final String LIVE = "LIVE";
    private static final int UI_HIDE_TIME = 5000;

    public final PlayerView playerView;
    public final Player player;
    private ImageButton playButton;
    private ImageButton fullscreenButton;
    private SeekBar seekBar;
    private TextView positionView;
    private TextView durationView;

    private Drawable playDrawable;
    private Drawable pauseDrawable;

    private long lastUiInteraction;

    private Timer uiHideTimer;
    private TimerTask uiHideTask;

    private boolean live;
    private View controlView;

    public PlayerUI(Context context, Player player) {
        super(context);
        // Create new PlayerView with our PlayerConfiguration
        this.player = player;
        playerView = new PlayerView(context, player);
        playerView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setup();
    }

    private void setup() {
        LayoutInflater.from(getContext()).inflate(R.layout.player_ui, this);

        controlView = findViewById(R.id.controls);
        playButton = (ImageButton) controlView.findViewById(R.id.playback_button);
        fullscreenButton = (ImageButton) controlView.findViewById(R.id.fullscreen_button);
        playDrawable = ContextCompat.getDrawable(playerView.getContext(), R.drawable.ic_play_arrow_black_24dp);
        pauseDrawable = ContextCompat.getDrawable(playerView.getContext(), R.drawable.ic_pause_black_24dp);
        seekBar = (SeekBar) controlView.findViewById(R.id.seekbar);
        positionView = (TextView) controlView.findViewById(R.id.position);
        durationView = (TextView) controlView.findViewById(R.id.duration);

        seekBar.setOnSeekBarChangeListener(seekBarChangeListener);
        playButton.setOnClickListener(onClickListener);
        fullscreenButton.setOnClickListener(onClickListener);
        playButton.setOnTouchListener(onTouchListener);
        seekBar.setOnTouchListener(onTouchListener);
        setOnTouchListener(onTouchListener);

        // Add PlayerView to the layout
        addView(playerView, 0);

        uiHideTimer = new Timer();

        updateUi();
    }

    private void startUiHiderTask() {
        stopUiHiderTask();

        // Create Task which hides the UI after a specified time (UI_HIDE_TIME)
        uiHideTask = new TimerTask() {
            @Override
            public void run() {
                long timeSincelastUiInteraction = System.currentTimeMillis() - lastUiInteraction;
                if (timeSincelastUiInteraction > UI_HIDE_TIME) {
                    setControlsVisible(false);
                }
            }
        };
        // Schedule the hider task, so it checks the state every 100ms
        uiHideTimer.scheduleAtFixedRate(uiHideTask, 0, 100);
    }

    private void stopUiHiderTask() {
        if (uiHideTask != null) {
            uiHideTask.cancel();
            uiHideTask = null;
        }
    }

    public void setVisible(boolean visible) {
        lastUiInteraction = System.currentTimeMillis();
        setControlsVisible(visible);
    }

    private void setControlsVisible(final boolean visible) {
        post(new Runnable() {
            @Override
            public void run() {
                if (visible) {
                    startUiHiderTask();
                } else {
                    stopUiHiderTask();
                }

                int visibility = visible ? View.VISIBLE : View.INVISIBLE;
                controlView.setVisibility(visibility);
            }
        });
    }

    public void setFullscreenHandler(FullscreenHandler fullscreenHandler) {
        playerView.setFullscreenHandler(fullscreenHandler);
    }

    public void destroy() {
        uiHideTimer.cancel();
    }

    public void onStart() {
        playerView.onStart();
    }

    public void onResume() {
        playerView.onResume();
    }

    public void onPause() {
        playerView.onPause();
    }

    public void onStop() {
        playerView.onStop();
    }

    public void onDestroy() {
        playerView.onDestroy();
        destroy();
    }

    /**
     * UI Listeners
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    private SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // Only seek/timeShift when the user changes the progress (and not the TimeChangedEvent)
            if (fromUser) {
                // If the current stream is a live stream, we have to use the timeShift method
                if (!player.isLive()) {
                    player.seek(progress / 1000d);
                } else {
                    player.timeShift((progress - seekBar.getMax()) / 1000d);
                }
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    };

    private final OnClickListener onClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v == playButton || v == PlayerUI.this) {
                if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.play();
                }
            }
            if (v == fullscreenButton) {
                if (playerView.isFullscreen()) {
                    playerView.exitFullscreen();
                } else {
                    playerView.enterFullscreen();
                }
            }
        }
    };

    private final OnTouchListener onTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            lastUiInteraction = System.currentTimeMillis();

            if (event.getAction() == MotionEvent.ACTION_UP) {
                // Start the hider task, when the UI is not touched
                startUiHiderTask();
            } else {
                // When the view is touched, the UI should be visible
                setControlsVisible(true);
            }
            return false;
        }
    };

    /**
     * Methods for UI update
     */
    private void updateUi() {
        seekBar.post(new Runnable() {
            @Override
            public void run() {
                int positionMs;
                int durationMs;

                // if the live state of the player changed, the UI should change it's mode
                if (live != player.isLive()) {
                    live = player.isLive();
                    if (live) {
                        positionView.setVisibility(GONE);
                        durationView.setText(LIVE);
                    } else {
                        positionView.setVisibility(VISIBLE);
                    }
                }

                if (live) {
                    // The Seekbar does not support negative values
                    // so the seekable range is shifted to the positive
                    durationMs = (int) (-player.getMaxTimeShift() * 1000);
                    positionMs = (int) (durationMs + player.getTimeShift() * 1000);
                } else {
                    // Converting to milliseconds
                    positionMs = (int) (player.getCurrentTime() * 1000);
                    durationMs = (int) (player.getDuration() * 1000);

                    // Update the TextViews displaying the current position and duration
                    positionView.setText(millisecondsToTimeString(positionMs));
                    durationView.setText(millisecondsToTimeString(durationMs));
                }

                // Update the values of the Seekbar
                seekBar.setProgress(positionMs);
                seekBar.setMax(durationMs);

                // Update the image of the playback button
                if (player.isPlaying()) {
                    playButton.setImageDrawable(pauseDrawable);
                } else {
                    playButton.setImageDrawable(playDrawable);
                }
            }
        });
    }

    private String millisecondsToTimeString(int milliseconds) {
        int second = (milliseconds / 1000) % 60;
        int minute = (milliseconds / (1000 * 60)) % 60;
        int hour = (milliseconds / (1000 * 60 * 60)) % 24;

        if (hour > 0) {
            return String.format("%02d:%02d:%02d", hour, minute, second);
        } else {
            return String.format("%02d:%02d", minute, second);
        }
    }

    @Override
    public void onPlaybackErrorListener(Error errorEvent) {

    }

    @Override
    public void onVideoSizeChangedListener(VideoSizeChanged videoChangeEvent) {

    }

    @Override
    public void onTimeChangeListener(TimeChanged timeChangeEvent) {
        updateUi();
    }

    @Override
    public void onPlayListener(Play playEvent) {
        updateUi();
    }

    @Override
    public void onPlayingListener(Playing playingEvent) {
        updateUi();
    }

    @Override
    public void onPausedListener(Paused pausedEvent) {
        updateUi();
    }

    @Override
    public void onPlaybackFinishedListener(PlaybackFinished playbackFinishedEvent) {
        updateUi();
    }

    @Override
    public void onSeekListener(Seek seekingEvent) {

    }

    @Override
    public void onSeekedListener(Seeked seekedEvent) {
        updateUi();
    }

    @Override
    public void onMetadataListener(Metadata metadataEvent) {

    }

    @Override
    public void onStallStartedListener(StallStarted stallStartedEvent) {

    }

    @Override
    public void onStallEndedListener(StallEnded stallEndedEvent) {
        updateUi();
    }

    @Override
    public void onAdBreakStartedListener(AdBreakStarted adBreakStartedEvent) {

    }

    @Override
    public void onAdStartedListener(AdStarted adStartedEvent) {

    }

    @Override
    public void onAdFinishedListener(AdFinished stallEndedEvent) {

    }

    @Override
    public void onAdBreakFinishedListener(AdBreakFinished stallEndedEvent) {

    }

    @Override
    public void onAdErrorListener(AdError stallEndedEvent) {

    }

    @Override
    public void onSourceLoadedListener(Loaded sourceLoadedEvent) {
        updateUi();
    }
}
