/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.itechneps.android.exoplayer2.source.hls;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Pair;
import com.itechneps.android.exoplayer2.Format;
import com.itechneps.android.exoplayer2.drm.DrmInitData;
import com.itechneps.android.exoplayer2.extractor.Extractor;
import com.itechneps.android.exoplayer2.extractor.ExtractorInput;
import com.itechneps.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.itechneps.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.itechneps.android.exoplayer2.extractor.ts.Ac3Extractor;
import com.itechneps.android.exoplayer2.extractor.ts.AdtsExtractor;
import com.itechneps.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.itechneps.android.exoplayer2.extractor.ts.TsExtractor;
import com.itechneps.android.exoplayer2.util.MimeTypes;
import com.itechneps.android.exoplayer2.util.TimestampAdjuster;

import java.io.EOFException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Default {@link HlsExtractorFactory} implementation.
 */
public final class DefaultHlsExtractorFactory implements HlsExtractorFactory {

  public static final String AAC_FILE_EXTENSION = ".aac";
  public static final String AC3_FILE_EXTENSION = ".ac3";
  public static final String EC3_FILE_EXTENSION = ".ec3";
  public static final String MP3_FILE_EXTENSION = ".mp3";
  public static final String MP4_FILE_EXTENSION = ".mp4";
  public static final String M4_FILE_EXTENSION_PREFIX = ".m4";
  public static final String MP4_FILE_EXTENSION_PREFIX = ".mp4";
  public static final String CMF_FILE_EXTENSION_PREFIX = ".cmf";
  public static final String VTT_FILE_EXTENSION = ".vtt";
  public static final String WEBVTT_FILE_EXTENSION = ".webvtt";

  @DefaultTsPayloadReaderFactory.Flags private final int payloadReaderFactoryFlags;

  /** Creates a factory for HLS segment extractors. */
  public DefaultHlsExtractorFactory() {
    this(/* payloadReaderFactoryFlags= */ 0);
  }

  /**
   * Creates a factory for HLS segment extractors.
   *
   * @param payloadReaderFactoryFlags Flags to add when constructing any {@link
   *     DefaultTsPayloadReaderFactory} instances. Other flags may be added on top of {@code
   *     payloadReaderFactoryFlags} when creating {@link DefaultTsPayloadReaderFactory}.
   */
  public DefaultHlsExtractorFactory(int payloadReaderFactoryFlags) {
    this.payloadReaderFactoryFlags = payloadReaderFactoryFlags;
  }

  @Override
  public Pair<Extractor, Boolean> createExtractor(
      Extractor previousExtractor,
      Uri uri,
      Format format,
      List<Format> muxedCaptionFormats,
      DrmInitData drmInitData,
      TimestampAdjuster timestampAdjuster,
      Map<String, List<String>> responseHeaders,
      ExtractorInput extractorInput)
      throws InterruptedException, IOException {

    if (previousExtractor != null) {
      // A extractor has already been successfully used. Return one of the same type.
      if (previousExtractor instanceof TsExtractor
          || previousExtractor instanceof FragmentedMp4Extractor) {
        // TS and fMP4 extractors can be reused.
        return buildResult(previousExtractor);
      } else if (previousExtractor instanceof WebvttExtractor) {
        return buildResult(new WebvttExtractor(format.language, timestampAdjuster));
      } else if (previousExtractor instanceof AdtsExtractor) {
        return buildResult(new AdtsExtractor());
      } else if (previousExtractor instanceof Ac3Extractor) {
        return buildResult(new Ac3Extractor());
      } else if (previousExtractor instanceof Mp3Extractor) {
        return buildResult(new Mp3Extractor());
      } else {
        throw new IllegalArgumentException(
            "Unexpected previousExtractor type: " + previousExtractor.getClass().getSimpleName());
      }
    }

    // Try selecting the extractor by the file extension.
    Extractor extractorByFileExtension =
        createExtractorByFileExtension(
            uri, format, muxedCaptionFormats, drmInitData, timestampAdjuster);
    extractorInput.resetPeekPosition();
    if (sniffQuietly(extractorByFileExtension, extractorInput)) {
      return buildResult(extractorByFileExtension);
    }

    // We need to manually sniff each known type, without retrying the one selected by file
    // extension.

    if (!(extractorByFileExtension instanceof WebvttExtractor)) {
      WebvttExtractor webvttExtractor = new WebvttExtractor(format.language, timestampAdjuster);
      if (sniffQuietly(webvttExtractor, extractorInput)) {
        return buildResult(webvttExtractor);
      }
    }

    if (!(extractorByFileExtension instanceof AdtsExtractor)) {
      AdtsExtractor adtsExtractor = new AdtsExtractor();
      if (sniffQuietly(adtsExtractor, extractorInput)) {
        return buildResult(adtsExtractor);
      }
    }

    if (!(extractorByFileExtension instanceof Ac3Extractor)) {
      Ac3Extractor ac3Extractor = new Ac3Extractor();
      if (sniffQuietly(ac3Extractor, extractorInput)) {
        return buildResult(ac3Extractor);
      }
    }

    if (!(extractorByFileExtension instanceof Mp3Extractor)) {
      Mp3Extractor mp3Extractor =
          new Mp3Extractor(/* flags= */ 0, /* forcedFirstSampleTimestampUs= */ 0);
      if (sniffQuietly(mp3Extractor, extractorInput)) {
        return buildResult(mp3Extractor);
      }
    }

    if (!(extractorByFileExtension instanceof FragmentedMp4Extractor)) {
      FragmentedMp4Extractor fragmentedMp4Extractor =
          new FragmentedMp4Extractor(
              /* flags= */ 0,
              timestampAdjuster,
              /* sideloadedTrack= */ null,
              drmInitData,
              muxedCaptionFormats != null ? muxedCaptionFormats : Collections.emptyList());
      if (sniffQuietly(fragmentedMp4Extractor, extractorInput)) {
        return buildResult(fragmentedMp4Extractor);
      }
    }

    if (!(extractorByFileExtension instanceof TsExtractor)) {
      TsExtractor tsExtractor =
          createTsExtractor(
              payloadReaderFactoryFlags, format, muxedCaptionFormats, timestampAdjuster);
      if (sniffQuietly(tsExtractor, extractorInput)) {
        return buildResult(tsExtractor);
      }
    }

    // Fall back on the extractor created by file extension.
    return buildResult(extractorByFileExtension);
  }

  private Extractor createExtractorByFileExtension(
      Uri uri,
      Format format,
      List<Format> muxedCaptionFormats,
      DrmInitData drmInitData,
      TimestampAdjuster timestampAdjuster) {
    String lastPathSegment = uri.getLastPathSegment();
    if (lastPathSegment == null) {
      lastPathSegment = "";
    }
    if (MimeTypes.TEXT_VTT.equals(format.sampleMimeType)
        || lastPathSegment.endsWith(WEBVTT_FILE_EXTENSION)
        || lastPathSegment.endsWith(VTT_FILE_EXTENSION)) {
      return new WebvttExtractor(format.language, timestampAdjuster);
    } else if (lastPathSegment.endsWith(AAC_FILE_EXTENSION)) {
      return new AdtsExtractor();
    } else if (lastPathSegment.endsWith(AC3_FILE_EXTENSION)
        || lastPathSegment.endsWith(EC3_FILE_EXTENSION)) {
      return new Ac3Extractor();
    } else if (lastPathSegment.endsWith(MP3_FILE_EXTENSION)) {
      return new Mp3Extractor(/* flags= */ 0, /* forcedFirstSampleTimestampUs= */ 0);
    } else if (lastPathSegment.endsWith(MP4_FILE_EXTENSION)
        || lastPathSegment.startsWith(M4_FILE_EXTENSION_PREFIX, lastPathSegment.length() - 4)
        || lastPathSegment.startsWith(MP4_FILE_EXTENSION_PREFIX, lastPathSegment.length() - 5)
        || lastPathSegment.startsWith(CMF_FILE_EXTENSION_PREFIX, lastPathSegment.length() - 5)) {
      return new FragmentedMp4Extractor(
          /* flags= */ 0,
          timestampAdjuster,
          /* sideloadedTrack= */ null,
          drmInitData,
          muxedCaptionFormats != null ? muxedCaptionFormats : Collections.emptyList());
    } else {
      // For any other file extension, we assume TS format.
      return createTsExtractor(
          payloadReaderFactoryFlags, format, muxedCaptionFormats, timestampAdjuster);
    }
  }

  private static TsExtractor createTsExtractor(
      @DefaultTsPayloadReaderFactory.Flags int userProvidedPayloadReaderFactoryFlags,
      Format format,
      List<Format> muxedCaptionFormats,
      TimestampAdjuster timestampAdjuster) {
    @DefaultTsPayloadReaderFactory.Flags
    int payloadReaderFactoryFlags =
        DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
            | userProvidedPayloadReaderFactoryFlags;
    if (muxedCaptionFormats != null) {
      // The playlist declares closed caption renditions, we should ignore descriptors.
      payloadReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_OVERRIDE_CAPTION_DESCRIPTORS;
    } else {
      // The playlist does not provide any closed caption information. We preemptively declare a
      // closed caption track on channel 0.
      muxedCaptionFormats =
          Collections.singletonList(
              Format.createTextSampleFormat(
                  /* id= */ null,
                  MimeTypes.APPLICATION_CEA608,
                  /* selectionFlags= */ 0,
                  /* language= */ null));
    }
    String codecs = format.codecs;
    if (!TextUtils.isEmpty(codecs)) {
      // Sometimes AAC and H264 streams are declared in TS chunks even though they don't really
      // exist. If we know from the codec attribute that they don't exist, then we can
      // explicitly ignore them even if they're declared.
      if (!MimeTypes.AUDIO_AAC.equals(MimeTypes.getAudioMediaMimeType(codecs))) {
        payloadReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_IGNORE_AAC_STREAM;
      }
      if (!MimeTypes.VIDEO_H264.equals(MimeTypes.getVideoMediaMimeType(codecs))) {
        payloadReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_IGNORE_H264_STREAM;
      }
    }

    return new TsExtractor(
        TsExtractor.MODE_HLS,
        timestampAdjuster,
        new DefaultTsPayloadReaderFactory(payloadReaderFactoryFlags, muxedCaptionFormats));
  }

  private static Pair<Extractor, Boolean> buildResult(Extractor extractor) {
    return new Pair<>(
        extractor,
        extractor instanceof AdtsExtractor
            || extractor instanceof Ac3Extractor
            || extractor instanceof Mp3Extractor);
  }

  private static boolean sniffQuietly(Extractor extractor, ExtractorInput input)
      throws InterruptedException, IOException {
    boolean result = false;
    try {
      result = extractor.sniff(input);
    } catch (EOFException e) {
      // Do nothing.
    } finally {
      input.resetPeekPosition();
    }
    return result;
  }

}
