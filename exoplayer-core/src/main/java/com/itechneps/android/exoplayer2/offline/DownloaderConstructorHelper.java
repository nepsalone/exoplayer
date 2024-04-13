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
package com.itechneps.android.exoplayer2.offline;

import androidx.annotation.Nullable;
import com.itechneps.android.exoplayer2.C;
import com.itechneps.android.exoplayer2.upstream.DataSink;
import com.itechneps.android.exoplayer2.upstream.DataSource;
import com.itechneps.android.exoplayer2.upstream.DummyDataSource;
import com.itechneps.android.exoplayer2.upstream.FileDataSource;
import com.itechneps.android.exoplayer2.upstream.PriorityDataSource;
import com.itechneps.android.exoplayer2.upstream.cache.Cache;
import com.itechneps.android.exoplayer2.upstream.cache.CacheDataSink;
import com.itechneps.android.exoplayer2.upstream.cache.CacheDataSource;
import com.itechneps.android.exoplayer2.util.Assertions;
import com.itechneps.android.exoplayer2.util.PriorityTaskManager;

/** A helper class that holds necessary parameters for {@link Downloader} construction. */
public final class DownloaderConstructorHelper {

  private final Cache cache;
  private final DataSource.Factory upstreamDataSourceFactory;
  private final DataSource.Factory cacheReadDataSourceFactory;
  private final DataSink.Factory cacheWriteDataSinkFactory;
  private final PriorityTaskManager priorityTaskManager;

  /**
   * @param cache Cache instance to be used to store downloaded data.
   * @param upstreamDataSourceFactory A {@link DataSource.Factory} for downloading data.
   */
  public DownloaderConstructorHelper(Cache cache, DataSource.Factory upstreamDataSourceFactory) {
    this(cache, upstreamDataSourceFactory, null, null, null);
  }

  /**
   * @param cache Cache instance to be used to store downloaded data.
   * @param upstreamDataSourceFactory A {@link DataSource.Factory} for downloading data.
   * @param cacheReadDataSourceFactory A {@link DataSource.Factory} for reading data from the cache. If null
   *     then standard {@link FileDataSource} instances will be used.
   * @param cacheWriteDataSinkFactory A {@link DataSink.Factory} for writing data to the cache. If
   *     null then standard {@link CacheDataSink} instances will be used.
   * @param priorityTaskManager A {@link PriorityTaskManager} to use when downloading. If non-null,
   *     downloaders will register as tasks with priority {@link C#PRIORITY_DOWNLOAD} whilst
   *     downloading.
   */
  public DownloaderConstructorHelper(
      Cache cache,
      DataSource.Factory upstreamDataSourceFactory,
      @Nullable DataSource.Factory cacheReadDataSourceFactory,
      @Nullable DataSink.Factory cacheWriteDataSinkFactory,
      @Nullable PriorityTaskManager priorityTaskManager) {
    Assertions.checkNotNull(upstreamDataSourceFactory);
    this.cache = cache;
    this.upstreamDataSourceFactory = upstreamDataSourceFactory;
    this.cacheReadDataSourceFactory = cacheReadDataSourceFactory;
    this.cacheWriteDataSinkFactory = cacheWriteDataSinkFactory;
    this.priorityTaskManager = priorityTaskManager;
  }

  /** Returns the {@link Cache} instance. */
  public Cache getCache() {
    return cache;
  }

  /** Returns a {@link PriorityTaskManager} instance. */
  public PriorityTaskManager getPriorityTaskManager() {
    // Return a dummy PriorityTaskManager if none is provided. Create a new PriorityTaskManager
    // each time so clients don't affect each other over the dummy PriorityTaskManager instance.
    return priorityTaskManager != null ? priorityTaskManager : new PriorityTaskManager();
  }

  /**
   * Returns a new {@link CacheDataSource} instance. If {@code offline} is true, it can only read
   * data from the cache.
   */
  public CacheDataSource buildCacheDataSource(boolean offline) {
    DataSource cacheReadDataSource = cacheReadDataSourceFactory != null
        ? cacheReadDataSourceFactory.createDataSource() : new FileDataSource();
    if (offline) {
      return new CacheDataSource(cache, DummyDataSource.INSTANCE,
          cacheReadDataSource, null, CacheDataSource.FLAG_BLOCK_ON_CACHE, null);
    } else {
      DataSink cacheWriteDataSink = cacheWriteDataSinkFactory != null
          ? cacheWriteDataSinkFactory.createDataSink()
          : new CacheDataSink(cache, CacheDataSource.DEFAULT_MAX_CACHE_FILE_SIZE);
      DataSource upstream = upstreamDataSourceFactory.createDataSource();
      upstream = priorityTaskManager == null ? upstream
          : new PriorityDataSource(upstream, priorityTaskManager, C.PRIORITY_DOWNLOAD);
      return new CacheDataSource(cache, upstream, cacheReadDataSource,
          cacheWriteDataSink, CacheDataSource.FLAG_BLOCK_ON_CACHE, null);
    }
  }

}
