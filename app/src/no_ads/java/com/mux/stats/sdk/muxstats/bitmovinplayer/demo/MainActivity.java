package com.mux.stats.sdk.muxstats.bitmovinplayer.demo;

import android.content.res.Configuration;
import android.os.Handler;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Point;
import android.os.Bundle;

import com.bitmovin.player.PlayerView;
import com.bitmovin.player.api.PlaybackConfig;
import com.bitmovin.player.api.Player;
import com.bitmovin.player.api.PlayerConfig;
import com.bitmovin.player.api.source.SourceConfig;
import com.bitmovin.player.api.source.SourceOptions;
import com.bitmovin.player.api.source.SourceType;
import com.bitmovin.player.api.ui.StyleConfig;
import com.mux.stats.sdk.core.MuxSDKViewOrientation;
import com.mux.stats.sdk.core.model.CustomerData;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.muxstats.bitmovinplayer.MuxStatsSDKBitmovinPlayer;

public class MainActivity extends AppCompatActivity {

    private Player player;
    private PlayerUI playerUi;
    private MuxStatsSDKBitmovinPlayer muxStats;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create new StyleConfig
        StyleConfig styleConfig = new StyleConfig();
        // Disable UI
        styleConfig.setUiEnabled(false);

        // Creating a new PlayerConfig
        PlayerConfig playerConfig = new PlayerConfig();
        // Assign created StyleConfig to the PlayerConfig
        playerConfig.setStyleConfig(styleConfig);
        // Assign a SourceItem to the PlayerConfig

        player = Player.create(this, playerConfig);
        playerUi = new PlayerUI(this, player);

        player.load(new SourceConfig("https://bitdash-a.akamaihd.net/content/sintel/sintel.mpd", SourceType.Dash));

        LinearLayout rootView = (LinearLayout) findViewById(R.id.activity_main);

        playerUi.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootView.addView(playerUi);
        configureMuxSdk();
        // player instance only allow for one event listener at a time, so MuxSDK will register a
        // listener for each event and will redispatch the events on IBitmovinPlayerEventsListener
        // That will be reused by PlayerUi class.
        muxStats.addBitmovinPlayerEventListener(playerUi);
    }

    private PlayerConfig createPlayerConfig() {
        // Creating a new PlayerConfig
        PlayerConfig playerConfig = new PlayerConfig();

        PlaybackConfig playbackConfig = new PlaybackConfig();
        playbackConfig.setAutoplayEnabled(true);
        playerConfig.setPlaybackConfig(playbackConfig);

        return playerConfig;
    }

    private void configureMuxSdk() {
        CustomerPlayerData customerPlayerData = new CustomerPlayerData();
        customerPlayerData.setEnvironmentKey("YOUR ENV KEY HERE");
        CustomerVideoData customerVideoData = new CustomerVideoData();
        customerVideoData.setVideoTitle("Sintel");
        CustomerData customerData = new CustomerData(
            customerPlayerData,
            customerVideoData,
            null
        );
        muxStats = new MuxStatsSDKBitmovinPlayer(
            this,
            playerUi.playerView,
            "demo-view-player",
            customerData);

        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        muxStats.setScreenSize(size.x, size.y);
        muxStats.enableMuxCoreDebug(true, false);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (muxStats == null) {
            return;
        }
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            muxStats.orientationChange(MuxSDKViewOrientation.LANDSCAPE);
        }
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            muxStats.orientationChange(MuxSDKViewOrientation.PORTRAIT);
        }
    }


    @Override
    protected void onStart()
    {
        super.onStart();
        this.playerUi.onStart();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        this.playerUi.onResume();
    }

    @Override
    protected void onPause()
    {
        this.playerUi.onPause();
        super.onPause();
    }

    @Override
    protected void onStop()
    {
        this.playerUi.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy()
    {
        muxStats.removeBitmovinPlayerEventListener(playerUi);
        this.playerUi.onDestroy();
        super.onDestroy();
    }
}
