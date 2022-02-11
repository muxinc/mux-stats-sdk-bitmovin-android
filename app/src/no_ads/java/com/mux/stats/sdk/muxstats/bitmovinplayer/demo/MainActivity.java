package com.mux.stats.sdk.muxstats.bitmovinplayer.demo;

import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Point;
import android.os.Bundle;

import com.bitmovin.player.PlayerView;
import com.bitmovin.player.api.Player;
import com.bitmovin.player.api.source.SourceConfig;
import com.mux.stats.sdk.core.MuxSDKViewOrientation;
import com.mux.stats.sdk.core.model.CustomerData;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.muxstats.bitmovinplayer.MuxStatsSDKBitmovinPlayer;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private Player player;
    private MuxStatsSDKBitmovinPlayer muxStats;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.bitmovinPlayerView);

        player = Player.create(this);
        playerView.setPlayer(player);
        // load source using a source config
        player.load(SourceConfig.fromUrl("https://bitdash-a.akamaihd.net/content/sintel/sintel.mpd"));

        configureMuxSdk();
    }

    private void configureMuxSdk() {
        CustomerPlayerData customerPlayerData = new CustomerPlayerData();
        customerPlayerData.setEnvironmentKey("YOUR_KEY_HERE");
        CustomerVideoData customerVideoData = new CustomerVideoData();
        customerVideoData.setVideoTitle("Sintel");
        CustomerData customerData = new CustomerData(
            customerPlayerData,
            customerVideoData,
            null
        );
        muxStats = new MuxStatsSDKBitmovinPlayer(
            this,
            playerView,
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
        this.playerView.onStart();
        super.onStart();
    }

    @Override
    protected void onResume()
    {
        this.playerView.onResume();
        super.onResume();
    }

    @Override
    protected void onPause()
    {
        this.playerView.onPause();
        super.onPause();
    }

    @Override
    protected void onStop()
    {
        this.playerView.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy()
    {
        this.playerView.onDestroy();
        super.onDestroy();
    }
}
