/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.itechneps.android.exoplayer2.metadata;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.Nullable;
import com.itechneps.android.exoplayer2.BaseRenderer;
import com.itechneps.android.exoplayer2.C;
import com.itechneps.android.exoplayer2.ExoPlaybackException;
import com.itechneps.android.exoplayer2.Format;
import com.itechneps.android.exoplayer2.FormatHolder;
import com.itechneps.android.exoplayer2.util.Assertions;
import com.itechneps.android.exoplayer2.util.Util;

import java.util.Arrays;

/**
 * A renderer for metadata.
 */
public final class MetadataRenderer extends BaseRenderer implements Callback {

  /**
   * @deprecated Use {@link MetadataOutput}.
   */
  @Deprecated
  public interface Output extends MetadataOutput {}

  private static final int MSG_INVOKE_RENDERER = 0;
  // TODO: Holding multiple pending metadata objects is temporary mitigation against
  // https://github.com/google/ExoPlayer/issues/1874. It should be removed once this issue has been
  // addressed.
  private static final int MAX_PENDING_METADATA_COUNT = 5;

  private final MetadataDecoderFactory decoderFactory;
  private final MetadataOutput output;
  private final @Nullable Handler outputHandler;
  private final FormatHolder formatHolder;
  private final MetadataInputBuffer buffer;
  private final Metadata[] pendingMetadata;
  private final long[] pendingMetadataTimestamps;

  private int pendingMetadataIndex;
  private int pendingMetadataCount;
  private MetadataDecoder decoder;
  private boolean inputStreamEnded;

  /**
   * @param output The output.
   * @param outputLooper The looper associated with the thread on which the output should be called.
   *     If the output makes use of standard Android UI components, then this should normally be the
   *     looper associated with the application's main thread, which can be obtained using {@link
   *     android.app.Activity#getMainLooper()}. Null may be passed if the output should be called
   *     directly on the player's internal rendering thread.
   */
  public MetadataRenderer(MetadataOutput output, @Nullable Looper outputLooper) {
    this(output, outputLooper, MetadataDecoderFactory.DEFAULT);
  }

  /**
   * @param output The output.
   * @param outputLooper The looper associated with the thread on which the output should be called.
   *     If the output makes use of standard Android UI components, then this should normally be the
   *     looper associated with the application's main thread, which can be obtained using {@link
   *     android.app.Activity#getMainLooper()}. Null may be passed if the output should be called
   *     directly on the player's internal rendering thread.
   * @param decoderFactory A factory from which to obtain {@link MetadataDecoder} instances.
   */
  public MetadataRenderer(
      MetadataOutput output, @Nullable Looper outputLooper, MetadataDecoderFactory decoderFactory) {
    super(C.TRACK_TYPE_METADATA);
    this.output = Assertions.checkNotNull(output);
    this.outputHandler =
        outputLooper == null ? null : Util.createHandler(outputLooper, /* callback= */ this);
    this.decoderFactory = Assertions.checkNotNull(decoderFactory);
    formatHolder = new FormatHolder();
    buffer = new MetadataInputBuffer();
    pendingMetadata = new Metadata[MAX_PENDING_METADATA_COUNT];
    pendingMetadataTimestamps = new long[MAX_PENDING_METADATA_COUNT];
  }

  @Override
  public int supportsFormat(Format format) {
    if (decoderFactory.supportsFormat(format)) {
      return supportsFormatDrm(null, format.drmInitData) ? FORMAT_HANDLED : FORMAT_UNSUPPORTED_DRM;
    } else {
      return FORMAT_UNSUPPORTED_TYPE;
    }
  }

  @Override
  protected void onStreamChanged(Format[] formats, long offsetUs) throws ExoPlaybackException {
    decoder = decoderFactory.createDecoder(formats[0]);
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) {
    flushPendingMetadata();
    inputStreamEnded = false;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (!inputStreamEnded && pendingMetadataCount < MAX_PENDING_METADATA_COUNT) {
      buffer.clear();
      int result = readSource(formatHolder, buffer, false);
      if (result == C.RESULT_BUFFER_READ) {
        if (buffer.isEndOfStream()) {
          inputStreamEnded = true;
        } else if (buffer.isDecodeOnly()) {
          // Do nothing. Note this assumes that all metadata buffers can be decoded independently.
          // If we ever need to support a metadata format where this is not the case, we'll need to
          // pass the buffer to the decoder and discard the output.
        } else {
          buffer.subsampleOffsetUs = formatHolder.format.subsampleOffsetUs;
          buffer.flip();
          int index = (pendingMetadataIndex + pendingMetadataCount) % MAX_PENDING_METADATA_COUNT;
          Metadata metadata = decoder.decode(buffer);
          if (metadata != null) {
            pendingMetadata[index] = metadata;
            pendingMetadataTimestamps[index] = buffer.timeUs;
            pendingMetadataCount++;
          }
        }
      }
    }

    if (pendingMetadataCount > 0 && pendingMetadataTimestamps[pendingMetadataIndex] <= positionUs) {
      invokeRenderer(pendingMetadata[pendingMetadataIndex]);
      pendingMetadata[pendingMetadataIndex] = null;
      pendingMetadataIndex = (pendingMetadataIndex + 1) % MAX_PENDING_METADATA_COUNT;
      pendingMetadataCount--;
    }
  }

  @Override
  protected void onDisabled() {
    flushPendingMetadata();
    decoder = null;
  }

  @Override
  public boolean isEnded() {
    return inputStreamEnded;
  }

  @Override
  public boolean isReady() {
    return true;
  }

  private void invokeRenderer(Metadata metadata) {
    if (outputHandler != null) {
      outputHandler.obtainMessage(MSG_INVOKE_RENDERER, metadata).sendToTarget();
    } else {
      invokeRendererInternal(metadata);
    }
  }

  private void flushPendingMetadata() {
    Arrays.fill(pendingMetadata, null);
    pendingMetadataIndex = 0;
    pendingMetadataCount = 0;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_INVOKE_RENDERER:
        invokeRendererInternal((Metadata) msg.obj);
        return true;
      default:
        // Should never happen.
        throw new IllegalStateException();
    }
  }

  private void invokeRendererInternal(Metadata metadata) {
    output.onMetadata(metadata);
  }

}
