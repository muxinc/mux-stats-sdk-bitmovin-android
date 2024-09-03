package com.mux.stats.sdk.muxstats.bitmovinplayer;

import com.bitmovin.player.api.event.PlayerEvent;
import com.bitmovin.player.api.event.PlayerEvent.Error;
import com.bitmovin.player.api.event.SourceEvent;

public interface IBitmovinPlayerEventsListener {

  void onPlaybackErrorListener(Error errorEvent);

  void onVideoSizeChangedListener(PlayerEvent.VideoSizeChanged videoChangeEvent);

  void onTimeChangeListener(PlayerEvent.TimeChanged timeChangeEvent);

  void onPlayListener(PlayerEvent.Play playEvent);

  void onPlayingListener(PlayerEvent.Playing playingEvent);

  void onPausedListener(PlayerEvent.Paused pausedEvent);

  void onPlaybackFinishedListener(PlayerEvent.PlaybackFinished playbackFinishedEvent);

  void onSeekListener(PlayerEvent.Seek seekingEvent);

  void onSeekedListener(PlayerEvent.Seeked seekedEvent);

  void onMetadataListener(PlayerEvent.Metadata metadataEvent);

  void onStallStartedListener(PlayerEvent.StallStarted stallStartedEvent);

  void onStallEndedListener(PlayerEvent.StallEnded stallEndedEvent);

  void onAdBreakStartedListener(PlayerEvent.AdBreakStarted adBreakStartedEvent);

  void onAdStartedListener(PlayerEvent.AdStarted adStartedEvent);

  void onAdFinishedListener(PlayerEvent.AdFinished stallEndedEvent);

  void onAdBreakFinishedListener(PlayerEvent.AdBreakFinished stallEndedEvent);

  void onAdErrorListener(PlayerEvent.AdError stallEndedEvent);

  void onSourceLoadedListener(SourceEvent.Loaded sourceLoadedEvent);
}
