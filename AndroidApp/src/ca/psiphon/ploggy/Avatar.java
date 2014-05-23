/*
 * Copyright (c) 2014, Psiphon Inc.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.widget.ImageView;

/**
 * Unique avatars derived from identities.
 *
 * Port of Robohash (http://robohash.org) to Java.
 */
public class Avatar {

    private static final String LOG_TAG = "Avatar";

    private static final String ASSETS_SUBDIRECTORY = "robohash";
    private static final String CONFIG_FILENAME = "config.json";

    // TODO: subscribe to RemovedFriend events to clear associated bitmaps
    private static BitmapCache mCache = new BitmapCache();
    private static JSONObject mConfig = null;

    public static void setAvatarImage(
            Context context,
            ImageView imageView) {
        setAvatarImage(context, imageView, false);
    }

    public static void setTemporaryAvatarImage(
            Context context,
            ImageView imageView,
            Identity.PublicIdentity publicIdentity) {
        // Don't cache temporary avatar image
        setAvatarImage(context, imageView, false, publicIdentity.mId);
    }

    public static void setAvatarImage(
            Context context,
            ImageView imageView,
            Identity.PublicIdentity publicIdentity) {
        setAvatarImage(context, imageView, true, publicIdentity.mId);
    }

    public static void setGroupAvatarImage(
            Context context,
            ImageView imageView,
            String selfId,
            Protocol.Group group) {
        setGroupAvatarImage(context, imageView, selfId, group.mMembers);
    }

    public static void setGroupAvatarImage(
            ImageView imageView) {
        imageView.setImageResource(R.drawable.ic_navigation_drawer_group_list);
    }

    public static void setGroupAvatarImage(
            Context context,
            ImageView imageView,
            String selfId,
            Adapters.GroupMemberArrayAdapter membersAdapter) {
        List<Identity.PublicIdentity> members = new ArrayList<Identity.PublicIdentity>();
        for (int i = 0; i < membersAdapter.getCount(); i++) {
            members.add(membersAdapter.getItem(i));
        }
        setGroupAvatarImage(context, imageView, selfId, members);
    }

    public static void setGroupAvatarImage(
            Context context,
            ImageView imageView,
            String selfId,
            List<Identity.PublicIdentity> members) {
        // Group avatar is a composition of member avatars with self excluded.
        // In the case where only self is in the group, a generic icon is used.
        if (members.size() == 1 && members.get(0).equals(selfId)) {
            setGroupAvatarImage(imageView);
            return;
        }
        String[] ids = new String[members.size()-1];
        int index = 0;
        for (Identity.PublicIdentity publicIdentity : members) {
            if (!publicIdentity.mId.equals(selfId)) {
                ids[index++] = publicIdentity.mId;
            }
        }
        setAvatarImage(context, imageView, true, ids);
    }

    private static void setAvatarImage(
            Context context,
            ImageView imageView,
            boolean cacheCandidate,
            String ... ids) {
        if (ids.length > 0) {
            try {
                // TODO: cache compound avatar bitmaps?
                Bitmap firstBitmap = Avatar.getRobohash(context, cacheCandidate, ids[0]);
                if (ids.length == 1) {
                    imageView.setImageBitmap(firstBitmap);
                    return;
                }

                int width = firstBitmap.getWidth();
                int height = firstBitmap.getHeight();
                Bitmap bitmap = Bitmap.createBitmap(width*2, height*2, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                Paint paint = new Paint();
                paint.setAlpha(255);
                Rect srcRect = new Rect(0, 0, width, height);
                Rect destRect = new Rect(0, 0, width, height);

                if (ids.length == 2) {
                    canvas.drawBitmap(firstBitmap, srcRect, destRect, paint);
                    destRect = new Rect(width, height, width*2, height*2);
                    canvas.drawBitmap(Avatar.getRobohash(context, cacheCandidate, ids[1]), srcRect, destRect, paint);
                    imageView.setImageBitmap(bitmap);
                    return;
                } else if (ids.length == 3) {
                    destRect = new Rect(width/2, 0, width/2+width, height);
                    canvas.drawBitmap(firstBitmap, srcRect, destRect, paint);
                    destRect = new Rect(0, height, width, height*2);
                    canvas.drawBitmap(Avatar.getRobohash(context, cacheCandidate, ids[1]), srcRect, destRect, paint);
                    destRect = new Rect(width, height, width*2, height*2);
                    canvas.drawBitmap(Avatar.getRobohash(context, cacheCandidate, ids[2]), srcRect, destRect, paint);
                    imageView.setImageBitmap(bitmap);
                    return;
                } else if (ids.length == 4) {
                    canvas.drawBitmap(firstBitmap, srcRect, destRect, paint);
                    destRect = new Rect(width, 0, width*2, height);
                    canvas.drawBitmap(Avatar.getRobohash(context, cacheCandidate, ids[1]), srcRect, destRect, paint);
                    destRect = new Rect(0, height, width, height*2);
                    canvas.drawBitmap(Avatar.getRobohash(context, cacheCandidate, ids[2]), srcRect, destRect, paint);
                    destRect = new Rect(width, height, width*2, height*2);
                    canvas.drawBitmap(Avatar.getRobohash(context, cacheCandidate, ids[3]), srcRect, destRect, paint);
                    imageView.setImageBitmap(bitmap);
                    return;
                } else if (ids.length > 4) {
                    canvas.drawBitmap(firstBitmap, srcRect, destRect, paint);
                    destRect = new Rect(width, 0, width*2, height);
                    canvas.drawBitmap(Avatar.getRobohash(context, cacheCandidate, ids[1]), srcRect, destRect, paint);
                    destRect = new Rect(0, height, width, height*2);
                    canvas.drawBitmap(Avatar.getRobohash(context, cacheCandidate, ids[2]), srcRect, destRect, paint);
                    String text = "+" + Integer.toString(ids.length - 3);
                    Paint textPaint = new Paint();
                    textPaint.setColor(Color.BLACK);
                    textPaint.setTextSize((int) (width/4 * context.getResources().getDisplayMetrics().density));
                    Rect textBounds = new Rect();
                    textPaint.getTextBounds(text, 0, text.length(), textBounds);
                    int x = width + (width - textBounds.width())/2;
                    int y = height + (height + textBounds.height())/2;
                    canvas.drawText(text, x, y, textPaint);
                    imageView.setImageBitmap(bitmap);
                    return;
                }
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to create image");
            }
        }
        imageView.setImageResource(R.drawable.ic_unknown_avatar);
    }

    private static Bitmap getRobohash(
            Context context,
            boolean cacheCandidate,
            String id) throws PloggyError {
        try {
            Bitmap cachedBitmap = mCache.get(id);
            if (cachedBitmap != null) {
                return cachedBitmap;
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(id.getBytes());
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
                mCache.set(id, robotBitmap);
            }

            return robotBitmap;

        } catch (IOException e) {
            throw new PloggyError(LOG_TAG, e);
        } catch (JSONException e) {
            throw new PloggyError(LOG_TAG, e);
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
