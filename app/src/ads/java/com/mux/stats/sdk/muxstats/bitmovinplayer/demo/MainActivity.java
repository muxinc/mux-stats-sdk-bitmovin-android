package com.mux.stats.sdk.muxstats.bitmovinplayer.demo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.util.JsonReader;
import android.widget.ListView;

import com.bitmovin.player.PlayerView;
import com.bitmovin.player.api.Player;
import com.bitmovin.player.api.source.SourceConfig;
import com.mux.stats.sdk.core.MuxSDKViewOrientation;
import com.mux.stats.sdk.core.model.CustomerData;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.muxstats.bitmovinplayer.MuxStatsSDKBitmovinPlayer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private Player player;
    private MuxStatsSDKBitmovinPlayer muxStats;

    private ListView adTypeList;
    private ArrayList<AdSample> adSamples = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.bitmovinPlayerView);
        adTypeList = findViewById(R.id.ad_type_selection);

        player = Player.create(this);
        playerView.setPlayer(player);
        // load source using a source config
        player.load(SourceConfig.fromUrl("https://bitdash-a.akamaihd.net/content/sintel/sintel.mpd"));

        configureMuxSdk();
        initAdTypeList();
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

    void initAdTypeList() {
        JsonReader reader;
        ArrayList<String> adNames = new ArrayList<>();
        try {
            InputStream in = getAssets().open("media.json");
            reader = new JsonReader(new InputStreamReader(in, java.nio.charset.Charset.forName("UTF-8")));
            reader.beginArray();
            while (reader.hasNext()) {
                AdSample adSample = new AdSample();
                reader.beginObject();
                String name = null;
                String adTagUri = null;
                while (reader.hasNext()) {
                    String attributeName = reader.nextName();
                    String attributeValue = reader.nextString();
                    if (attributeName.equalsIgnoreCase("name")) {
                        adSample.setName(attributeValue);
                    }
                    if (attributeName.equalsIgnoreCase("ad_tag_uri")) {
                        adSample.setAdTagUri(attributeValue);
                    }
                    if (attributeName.equalsIgnoreCase("uri")) {
                        adSample.setUri(attributeValue);
                    }
                }
                reader.endObject();
                adSamples.add(adSample);
            }
            reader.close();
            adTypeList.setAdapter(new AdListAdapter(this, adSamples, adTypeList));

            adTypeList.setOnItemClickListener((parent, view, position, id) -> {
                // Reset player playback
//                bitmovinPlayer.pause();
//                AdSample selectedAd = (AdSample) adTypeList.getAdapter().getItem(position);
//                if (selectedAd.getName().startsWith("VMAP")) {
//                    setupVMAPAd(selectedAd.getAdTagUri());
//                } else {
//                    setupVASTAd(selectedAd.getAdTagUri());
//                }
            });
            adTypeList.performItemClick(
                adTypeList.findViewWithTag(
                    adTypeList.getAdapter().
                        getItem(0)),
                0,
                adTypeList.getAdapter().getItemId(0));
            adTypeList.setSelection(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
