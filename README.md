# iTechneps Exoplayer Library v2.9.6 #
## ExoPlayer HLS library module ##

Provides support for HTTP Live Streaming (HLS) content. To play HLS content,
instantiate a `HlsMediaSource` and pass it to `ExoPlayer.prepare`.

## ExoPlayer core library module ##

The core of the ExoPlayer library.
Implementation: IcyHttpDataSourceFactory.java
Implementation: IcyHttpDataSource.java
Its very easy to show shoutcast Icecast Streaming IcyMetadata
# Links #

	dependencies {
	 `implementation 'com.github.nepsalone.exoplayer:exoplayer-core:1.0.6'`
         `implementation 'com.github.nepsalone.exoplayer:exoplayer-hls:1.0.6'`
	}
 implement
 `DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(getApplicationContext(), null, icy);`
 
 icyData {
 `public IcyHttpDataSourceFactory icy = new IcyHttpDataSourceFactory
            .Builder(Tools.getUserAgent())
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMillis(1000)
            .setIcyHeadersListener(icyHeaders -> {
            })
            .setIcyMetadataChangeListener(icyMetadata -> {
                try {
                    if (sharedPref.getIcyMetadata().equals("true")) {
                        Tools.postDelayed(() -> {
                            if ("".equalsIgnoreCase(icyMetadata.getStreamTitle())) {
                                updateNotificationMetadata(Constant.item_radio.get(Constant.position).category_name);
                                onMediaMetadataCompatChanged(Constant.item_radio.get(Constant.position).category_name);
                            } else {
                                updateNotificationMetadata(icyMetadata.getStreamTitle());
                                onMediaMetadataCompatChanged(icyMetadata.getStreamTitle());
                            }
                        }, 1000);
                    }
                } catch (Exception e) {
                    e.printStackTrace();

                }
            }).build();`

     }
	    

* [Javadoc][]: Classes matching `com.google.android.exoplayer2.source.hls.*`
  belong to this module.
* [Javadoc][]: Classes matching `com.google.android.exoplayer2.source.core.*`
  belong to this module.

[Javadoc]: https://google.github.io/ExoPlayer/doc/reference/index.html
