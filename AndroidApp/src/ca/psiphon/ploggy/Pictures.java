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

package ca.psiphon.ploggy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;

/**
 * Helpers for pictures.
 *
 * - Scaled-down thumbnail display with caching.
 * - Create size-bounded scaled-down copy of a bitmap file for sharing.
 */
public class Pictures {

    private static final String LOG_TAG = "Pictures";

    private static final int MAX_PICTURE_SIZE_IN_PIXELS = 2097152; // approx. 8MB in ARGB_8888

    private static BitmapCache mThumbnailCache = new BitmapCache();

    public static boolean loadThumbnailWithClickToShowPicture(Context context, File source, ImageView target) {
        if (!loadThumbnail(context, source, target)) {
            return false;
        }
        // On click ImageView, load activity with full picture
        final Context finalContext = context;
        final String finalFilePath = source.getAbsolutePath();
        target.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ActivityShowPicture.startShowPicture(finalContext, finalFilePath);
                    }
                });
        return true;
    }

    public static boolean loadThumbnail(Context context, File source, ImageView target) {
        try {
            String key = source.getAbsolutePath();
            Bitmap bitmap = mThumbnailCache.get(key);
            if (bitmap == null) {
                // TODO: get actual dimensions of ImageView. Note: target.getWidth()
                // returns 0 when the views are not fully instantiated.
                int width = (int)context.getResources().getDimension(R.dimen.picture_thumbnail_width);
                int height = (int)context.getResources().getDimension(R.dimen.picture_thumbnail_height);
                bitmap = loadScaledBitmap(source, width, height);
                mThumbnailCache.set(key, bitmap);
            }
            target.setImageBitmap(bitmap);
            return true;
        } catch (PloggyError e) {
            target.setImageResource(R.drawable.ic_picture_load_error);
        }
        return false;
    }

    public static boolean loadPicture(File source, ImageView target) {
        try {
            target.setImageBitmap(loadScaledBitmap(source, MAX_PICTURE_SIZE_IN_PIXELS));
            return true;
        } catch (PloggyError e) {
            target.setImageResource(R.drawable.ic_picture_load_error);
            return false;
        }
    }

    public static void copyScaledBitmapWithoutMetadata(File source, File target) throws PloggyError {
        FileOutputStream outputStream = null;
        try {
            // Extracting a bitmap from the file omits EXIF and other metadata, regardless of
            // origin file type (JPEG, PNG, etc.)
            // TODO: implement this by streaming (or using BitmapRegionDecoder) to avoid loading the entire bitmap into memory
            Bitmap bitmap = loadScaledBitmap(source, MAX_PICTURE_SIZE_IN_PIXELS);
            outputStream = new FileOutputStream(target);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            bitmap.recycle();
        } catch (IOException e) {
            throw new PloggyError(LOG_TAG, e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private static Bitmap loadScaledBitmap(File source, int targetWidth, int targetHeight)
            throws PloggyError {
        BitmapFactory.Options options = new BitmapFactory.Options();
        decodeBitmapBounds(source, options);
        options.inSampleSize =
            inSampleSizeForDimensions(options.outWidth, options.outHeight, targetWidth, targetHeight);
        return decodeBitmap(source, options);
    }


    private static Bitmap loadScaledBitmap(File source, int maxSizeInPixels)
            throws PloggyError {
        BitmapFactory.Options options = new BitmapFactory.Options();
        decodeBitmapBounds(source, options);
        options.inSampleSize =
            inSampleSizeForMaximumSize(options.outWidth, options.outHeight, maxSizeInPixels);
        return decodeBitmap(source, options);
    }

    private static void decodeBitmapBounds(File source, BitmapFactory.Options options) {
        options.inJustDecodeBounds = true;
        // TODO: returns null for inJustDecodeBounds... so how to check for error?
        BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        options.inJustDecodeBounds = false;
    }

    private static Bitmap decodeBitmap(File source, BitmapFactory.Options options)
            throws PloggyError {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
            if (bitmap == null) {
                throw new PloggyError(LOG_TAG, "cannot decode image");
            }
            return bitmap;
        } catch (OutOfMemoryError e) {
            // Expected condition due to bitmap loading; friend will eventually retry download
            throw new PloggyError(LOG_TAG, "out of memory error");
        }
    }

    private static int inSampleSizeForDimensions(int width, int height, int targetWidth, int targetHeight) {
        // Scale the picture down so both width and height fit in target
        // Scale should be power of 2: http://developer.android.com/reference/android/graphics/BitmapFactory.Options.html#inSampleSize
        int inSampleSize = 1;
        while (width > targetWidth || height > targetHeight) {
            inSampleSize *= 2;
            width /= 2;
            height /= 2;
        }
        return inSampleSize;
    }

    private static int inSampleSizeForMaximumSize(int width, int height, int maxSizeInPixels) {
        // Scale the picture down so that it's total size in pixels <= MAX_PICTURE_SIZE_IN_PIXELS
        // Scale should be power of 2: http://developer.android.com/reference/android/graphics/BitmapFactory.Options.html#inSampleSize
        int sourceSizeInPixels = width * height;
        int inSampleSize = 1;
        while (sourceSizeInPixels > maxSizeInPixels) {
            inSampleSize *= 2;
            sourceSizeInPixels /= (inSampleSize * 2);
        }
        return inSampleSize;
    }
}
