package com.mux.stats.sdk.muxstats.bitmovinplayer.demo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.util.JsonReader;
import android.widget.ListView;

import com.bitmovin.player.BitmovinPlayer;
import com.bitmovin.player.BitmovinPlayerView;
import com.bitmovin.player.config.PlayerConfiguration;
import com.bitmovin.player.config.advertising.AdItem;
import com.bitmovin.player.config.advertising.AdSource;
import com.bitmovin.player.config.advertising.AdSourceType;
import com.bitmovin.player.config.advertising.AdvertisingConfiguration;
import com.bitmovin.player.config.media.SourceConfiguration;
import com.mux.stats.sdk.core.MuxSDKViewOrientation;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.muxstats.bitmovinplayer.MuxStatsSDKBitmovinPlayer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private BitmovinPlayerView bitmovinPlayerView;
    private BitmovinPlayer bitmovinPlayer;
    private MuxStatsSDKBitmovinPlayer muxStats;

    private ListView adTypeList;
    private ArrayList<AdSample> adSamples = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bitmovinPlayerView = findViewById(R.id.bitmovinPlayerView);
        adTypeList = findViewById(R.id.ad_type_selection);

        bitmovinPlayer = bitmovinPlayerView.getPlayer();

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
        this.bitmovinPlayerView.onStart();
        super.onStart();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        this.bitmovinPlayerView.onResume();
    }

    @Override
    protected void onPause()
    {
        this.bitmovinPlayerView.onPause();
        super.onPause();
    }

    @Override
    protected void onStop()
    {
        this.bitmovinPlayerView.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy()
    {
        this.bitmovinPlayerView.onDestroy();
        super.onDestroy();
    }

    private void configureMuxSdk() {
        CustomerPlayerData customerPlayerData = new CustomerPlayerData();
        customerPlayerData.setEnvironmentKey("eo12j5272jd1vpcb8ntfmk9kb");
        CustomerVideoData customerVideoData = new CustomerVideoData();
        customerVideoData.setVideoTitle("Sintel");
        muxStats = new MuxStatsSDKBitmovinPlayer(
                this,
                bitmovinPlayerView,
                "demo-view-player",
                customerPlayerData,
                customerVideoData);

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
                bitmovinPlayer.pause();
                AdSample selectedAd = (AdSample) adTypeList.getAdapter().getItem(position);
                if (selectedAd.getName().startsWith("VMAP")) {
                    setupVMAPAd(selectedAd.getAdTagUri());
                } else {
                    setupVASTAd(selectedAd.getAdTagUri());
                }
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

    void setupVMAPAd(String adTagUri) {
        SourceConfiguration sourceConfiguration = new SourceConfiguration();
        sourceConfiguration.addSourceItem("https://bitdash-a.akamaihd.net/content/sintel/sintel.mpd");

        AdSource firstAdSource = new AdSource(AdSourceType.IMA, adTagUri);
        AdItem adItem = new AdItem("pre", firstAdSource);
        AdvertisingConfiguration advertisingConfiguration = new AdvertisingConfiguration(adItem);

        PlayerConfiguration playerConfiguration = new PlayerConfiguration();
        playerConfiguration.setSourceConfiguration(sourceConfiguration);
        playerConfiguration.setAdvertisingConfiguration(advertisingConfiguration);
        // load source using the created source configuration

        bitmovinPlayer.setup(playerConfiguration);
    }

    void setupVASTAd(String adTagUri) {
        PlayerConfiguration playerConfiguration = new PlayerConfiguration();

        SourceConfiguration sourceConfiguration = new SourceConfiguration();
        sourceConfiguration.addSourceItem("https://bitdash-a.akamaihd.net/content/sintel/sintel.mpd");

        AdSource firstAdSource = new AdSource(AdSourceType.IMA, adTagUri);
        AdItem adItem = new AdItem("pre", firstAdSource);
        AdvertisingConfiguration advertisingConfiguration = new AdvertisingConfiguration(adItem);

        playerConfiguration.setSourceConfiguration(sourceConfiguration);
        playerConfiguration.setAdvertisingConfiguration(advertisingConfiguration);
        // load source using the created source configuration
        bitmovinPlayer.setup(playerConfiguration);
    }
}
