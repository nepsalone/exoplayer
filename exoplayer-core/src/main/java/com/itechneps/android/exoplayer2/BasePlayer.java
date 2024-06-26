/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.itechneps.android.exoplayer2;

import androidx.annotation.Nullable;
import com.itechneps.android.exoplayer2.util.Util;

/** Abstract base {@link Player} which implements common implementation independent methods. */
public abstract class BasePlayer implements Player {

  protected final Timeline.Window window;

  public BasePlayer() {
    window = new Timeline.Window();
  }

  @Override
  public final void seekToDefaultPosition() {
    seekToDefaultPosition(getCurrentWindowIndex());
  }

  @Override
  public final void seekToDefaultPosition(int windowIndex) {
    seekTo(windowIndex, /* positionMs= */ C.TIME_UNSET);
  }

  @Override
  public final void seekTo(long positionMs) {
    seekTo(getCurrentWindowIndex(), positionMs);
  }

  @Override
  public final boolean hasPrevious() {
    return getPreviousWindowIndex() != C.INDEX_UNSET;
  }

  @Override
  public final void previous() {
    int previousWindowIndex = getPreviousWindowIndex();
    if (previousWindowIndex != C.INDEX_UNSET) {
      seekToDefaultPosition(previousWindowIndex);
    }
  }

  @Override
  public final boolean hasNext() {
    return getNextWindowIndex() != C.INDEX_UNSET;
  }

  @Override
  public final void next() {
    int nextWindowIndex = getNextWindowIndex();
    if (nextWindowIndex != C.INDEX_UNSET) {
      seekToDefaultPosition(nextWindowIndex);
    }
  }

  @Override
  public final void stop() {
    stop(/* reset= */ false);
  }

  @Override
  public final int getNextWindowIndex() {
    Timeline timeline = getCurrentTimeline();
    return timeline.isEmpty()
        ? C.INDEX_UNSET
        : timeline.getNextWindowIndex(
            getCurrentWindowIndex(), getRepeatModeForNavigation(), getShuffleModeEnabled());
  }

  @Override
  public final int getPreviousWindowIndex() {
    Timeline timeline = getCurrentTimeline();
    return timeline.isEmpty()
        ? C.INDEX_UNSET
        : timeline.getPreviousWindowIndex(
            getCurrentWindowIndex(), getRepeatModeForNavigation(), getShuffleModeEnabled());
  }

  @Override
  @Nullable
  public final Object getCurrentTag() {
    int windowIndex = getCurrentWindowIndex();
    Timeline timeline = getCurrentTimeline();
    return windowIndex >= timeline.getWindowCount()
        ? null
        : timeline.getWindow(windowIndex, window, /* setTag= */ true).tag;
  }

  @Override
  public final int getBufferedPercentage() {
    long position = getBufferedPosition();
    long duration = getDuration();
    return position == C.TIME_UNSET || duration == C.TIME_UNSET
        ? 0
        : duration == 0 ? 100 : Util.constrainValue((int) ((position * 100) / duration), 0, 100);
  }

  @Override
  public final boolean isCurrentWindowDynamic() {
    Timeline timeline = getCurrentTimeline();
    return !timeline.isEmpty() && timeline.getWindow(getCurrentWindowIndex(), window).isDynamic;
  }

  @Override
  public final boolean isCurrentWindowSeekable() {
    Timeline timeline = getCurrentTimeline();
    return !timeline.isEmpty() && timeline.getWindow(getCurrentWindowIndex(), window).isSeekable;
  }

  @Override
  public final long getContentDuration() {
    Timeline timeline = getCurrentTimeline();
    return timeline.isEmpty()
        ? C.TIME_UNSET
        : timeline.getWindow(getCurrentWindowIndex(), window).getDurationMs();
  }

  @RepeatMode
  private int getRepeatModeForNavigation() {
    @RepeatMode int repeatMode = getRepeatMode();
    return repeatMode == REPEAT_MODE_ONE ? REPEAT_MODE_OFF : repeatMode;
  }
}
