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

/*
 RoboHash.org

 The RoboHash images are available under the CC-BY-3.0 license.
 Set 1 artwork created by Zikri Kader
 Set 2 artwork created by Hrvoje Novakovic.
 Set 3 artwork created by Julian Peter Arias.

 The Python Code is available under the MIT/Expat license

 Copyright (c) 2011, Colin Davis

 Permission is hereby granted, free of charge, to any person obtaining a copy of this
 software and associated documentation files (the "Software"), to deal in the
 Software without restriction, including without limitation the rights to use, copy,
 modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 and to permit persons to whom the Software is furnished to do so, subject to the
 following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
 OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package ca.psiphon.ploggy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.widget.ImageView;

/**
 * Unique avatars derived from identity fingerprints - to aid visual verification. 
 * 
 * Port of Robohash (http://robohash.org) to Java.
 */
public class Robohash {
    
    private static final String LOG_TAG = "Robohash";

    private static final String ASSETS_SUBDIRECTORY = "robohash";
    private static final String CONFIG_FILENAME = "config.json";

    // TODO: subscribe to RemovedFriend events to clear associated bitmaps
    private static BitmapCache mCache = new BitmapCache();
    private static JSONObject mConfig = null;

    public static void setRobohashImage(
            Context context,
            ImageView imageView,
            boolean cacheCandidate,
            Identity.PublicIdentity publicIdentity) {
        if (publicIdentity != null) {
            try {
                imageView.setImageBitmap(Robohash.getRobohash(
                        context, cacheCandidate, publicIdentity.getFingerprint()));
                return;
            } catch (Utils.ApplicationError e) {
                Log.addEntry(LOG_TAG, "failed to create image");
            }
        }
        imageView.setImageResource(R.drawable.ic_unknown_avatar); 
    }
    
    public static Bitmap getRobohash(
            Context context,
            boolean cacheCandidate,
            byte[] data) throws Utils.ApplicationError {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(data);

            String key = Utils.formatFingerprint(digest);
            Bitmap cachedBitmap = mCache.get(key);
            if (cachedBitmap != null) {
                return cachedBitmap;
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(digest);
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
            // TODO: SecureRandom SHA1PRNG (but not LinuxSecureRandom)
            Random random = new Random(byteBuffer.getLong());
            
            AssetManager assetManager = context.getAssets();

            if (mConfig == null) {
                mConfig = new JSONObject(loadAssetToString(assetManager, CONFIG_FILENAME));
            }

            int width = mConfig.getInt("width");
            int height = mConfig.getInt("height");

            JSONArray colors = mConfig.getJSONArray("colors");
            JSONArray parts = colors.getJSONArray(random.nextInt(colors.length()));

            Bitmap robotBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas robotCanvas = new Canvas(robotBitmap);
            
            for (int i = 0; i < parts.length(); i++) {
                JSONArray partChoices = parts.getJSONArray(i);
                String selection = partChoices.getString(random.nextInt(partChoices.length()));
                Bitmap partBitmap = loadAssetToBitmap(assetManager, selection);
                Rect rect = new Rect(0, 0, width, height);
                Paint paint = new Paint();
                paint.setAlpha(255);
                robotCanvas.drawBitmap(partBitmap, rect, rect, paint);
                partBitmap.recycle();
            }
            
            if (cacheCandidate) {
                mCache.set(key, robotBitmap);
            }
            
            return robotBitmap;
            
        } catch (IOException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (JSONException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (NoSuchAlgorithmException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    private static String loadAssetToString(AssetManager assetManager, String assetName) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = assetManager.open(new File(ASSETS_SUBDIRECTORY, assetName).getPath());            
            return Utils.readInputStreamToString(inputStream);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private static Bitmap loadAssetToBitmap(AssetManager assetManager, String assetName) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = assetManager.open(new File(ASSETS_SUBDIRECTORY, assetName).getPath());            
            return BitmapFactory.decodeStream(inputStream);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }
}
