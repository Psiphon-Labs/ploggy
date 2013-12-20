/*
 * Copyright (c) 2013, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

// From:
// https://github.com/square/picasso/blob/master/picasso/src/main/java/com/squareup/picasso/LruCache.java
// https://github.com/square/picasso/blob/master/picasso/src/main/java/com/squareup/picasso/Utils.java
/*
 * Copyright (C) 2011 The Android Open Source Project
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

package ca.psiphon.ploggy;

import java.util.LinkedHashMap;
import java.util.Map;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;

/**
 *  A memory bitmap cache which uses a least-recently used eviction policy.
 *
 *  From: https://github.com/square/picasso
 */
public class BitmapCache {
    final LinkedHashMap<String, Bitmap> map;
    private final int maxSize;

    private int size;
    private int putCount;
    private int evictionCount;
    private int hitCount;
    private int missCount;

    /** Create a cache using an appropriate portion of the available RAM as the maximum size. */
    public BitmapCache() {
        this(calculateMemoryCacheSize(Utils.getApplicationContext()));
    }

    /** Create a cache with a given maximum size in bytes. */
    public BitmapCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("Max size must be positive.");
        }
        this.maxSize = maxSize;
        this.map = new LinkedHashMap<String, Bitmap>(0, 0.75f, true);
    }

    public Bitmap get(String key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }
        Bitmap mapValue;
        synchronized (this) {
            mapValue = map.get(key);
            if (mapValue != null) {
                hitCount++;
                return mapValue;
            }
            missCount++;
        }
        return null;
    }

    public void set(String key, Bitmap bitmap) {
        if (key == null || bitmap == null) {
            throw new NullPointerException("key == null || bitmap == null");
        }
        Bitmap previous;
        synchronized (this) {
            putCount++;
            size += bitmap.getByteCount();
            previous = map.put(key, bitmap);
            if (previous != null) {
                size -= previous.getByteCount();
            }
        }
        trimToSize(maxSize);
    }

    private void trimToSize(int maxSize) {
        while (true) {
            String key;
            Bitmap value;
            synchronized (this) {
                if (size < 0 || (map.isEmpty() && size != 0)) {
                    throw new IllegalStateException(
                            getClass().getName() + ".sizeOf() is reporting inconsistent results!");
                }

                if (size <= maxSize || map.isEmpty()) {
                    break;
                }

                Map.Entry<String, Bitmap> toEvict = map.entrySet().iterator().next();
                key = toEvict.getKey();
                value = toEvict.getValue();
                map.remove(key);
                size -= value.getByteCount();
                evictionCount++;
            }
        }
    }

    /** Clear the cache. */
    public final void evictAll() {
        trimToSize(-1); // -1 will evict 0-sized elements
    }

    /** Returns the sum of the sizes of the entries in this cache. */
    public final synchronized int size() {
        return size;
    }

    /** Returns the maximum sum of the sizes of the entries in this cache. */
    public final synchronized int maxSize() {
        return maxSize;
    }

    public final synchronized void clear() {
        evictAll();
    }

    /** Returns the number of times {@link #get} returned a value. */
    public final synchronized int hitCount() {
        return hitCount;
    }

    /** Returns the number of times {@link #get} returned {@code null}. */
    public final synchronized int missCount() {
        return missCount;
    }

    /** Returns the number of times {@link #set(String, Bitmap)} was called. */
    public final synchronized int putCount() {
        return putCount;
    }

    /** Returns the number of values that have been evicted. */
    public final synchronized int evictionCount() {
        return evictionCount;
    }

    static int calculateMemoryCacheSize(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass;
        if ((context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_LARGE_HEAP) != 0) {
            memoryClass = activityManager.getLargeMemoryClass();
        } else {
            memoryClass = activityManager.getMemoryClass();
        }
        // Target ~15% of the available heap.
        return 1024 * 1024 * memoryClass / 7;
    }
}
