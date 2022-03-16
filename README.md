# Mux Stats SDK for Bitmovin Android Player

This is the Mux wrapper around the Bitmovin player SDK, built on top of Mux's core Java library,
providing Mux Data performance analytics for applications utilizing
[Bitmovin](https://bitmovin.com/docs/player).

## Setup and Usage 
### Add the Mux Bitmovin SDK to your build 

Use whichever of the following options is best for your project

#### Option 1: Using `settings.gradle`
Add the following lines to your `dependencyResolutionManagement {...}` block
```groovy
maven {
  url "https://muxinc.jfrog.io/artifactory/default-maven-release-local"
}
```

#### Option 2: Using `build.gradle`
Add the following lines to your project's `build.gradle` 
```groovy
allprojects {
    repositories {
        maven {
          url "https://muxinc.jfrog.io/artifactory/default-maven-release-local"
        }
    }
}
```

### Add the SDK as a Dependency in your application
Add one the following lines to the `dependencies` block in your app module's `build.gradle`, depending on which bitmovin library you are using. The current version can be found on our [Releases Page](https://github.com/muxinc/mux-stats-sdk-bitmovin-android/releases)

```groovy
implementation 'com.mux.stats.sdk.muxstats:muxstatssdkbitmovinplayer:[CurrentVersion]'
```

### Monitor Bitmovin Player using Mux Data
The Mux Data SDK for Bitmovin Player can be used by creating a new instance of `MuxStatsBitmovinPlayer` with the desired configuration. The constructor requires a `MuxStatsBitmovinPlayer`, which Mux will observe for data events.

```java
// Initialize with data about you, your video, and your app
CustomerPlayerData customerPlayerData = new CustomerPlayerData();
customerPlayerData.setEnvironmentKey("YOUR_ENVIRONMENT_KEY_HERE");
CustomerVideoData customerVideoData = new CustomerVideoData();
customerVideoData.setVideoTitle("VIDEO_TITLE_HERE");
CustomData customData = new CustomData();
customData.setCustomData1("YOUR_CUSTOM_STRING_HERE");
CustomerData customerData = new CustomerData(customerPlayerData, customerVideoData, null);
customerData.setCustomData(customData);

// Create a new Mux Stats monitor 
muxStatsBitmovin = new MuxStatsSDKBitmovinPlayer(
        this, /* context */
        playerView, /* Bitmovin player view */ 
        "demo-view-player", /* player name */
        customerData /* Mux CustomerData */);

// Set the size of the screen
Point size = new Point();
getWindowManager().getDefaultDisplay().getSize(size);
muxStatsBitmovin.setScreenSize(size.x, size.y);
```

You must also release the `MuxStatsSDKBitmovinPlayer` object when your component's lifecycle is ending. For example:

```java
@Override
public void onDestroy() {
    muxStatsBitmovin.release();
    super.onDestroy();
}
```

## Releases
The current version of the SDK is `v0.5.0`, as of 3/14/2022

All release notes can be found in our [changelog](RELEASENOTES.md)

## Contributing
### Developer Quick Start
- Open this project in Android Studio, and let Gradle run to configure the application.
- Build variants can be selected to support different versions of Bitmovin player.

### Style
The code in this repo conforms to the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html). Run the reformatter on files before committing.
The code was formatted in Android Studio/IntelliJ using the [Google Java Style for IntelliJ](https://github.com/google/styleguide/blob/gh-pages/intellij-java-google-style.xml). The style can be installed via the Java-style section of the IDE preferences (`Editor -> Code Style - >Java`).

## Documentation
See [our docs](https://docs.mux.com/docs/theoplayer-integration-guide) for more information.
