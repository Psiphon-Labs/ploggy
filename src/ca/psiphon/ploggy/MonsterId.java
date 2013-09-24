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
Plugin Name: WP_MonsterID
Version: 2.12
Plugin URI: http://scott.sherrillmix.com/blog/blogger/WP_MonsterID
Description: This plugin generates email specific monster icons for each user based on code and images by
<a href="http://www.splitbrain.org/projects/monsterid">Andreas Gohr</a> and images by <a href=" http://rocketworm.com/">Lemm</a>.
Author: Scott Sherrill-Mix
Author URI: http://scott.sherrillmix.com/blog/

The monster generation code and the original images are by <a href="http://www.splitbrain.org/projects/monsterid">Andreas Gohr</a>,
the updated artistic images came from <a href=" http://rocketworm.com/">Lemm</a>
and the underlying idea came from <a href="http://www.docuverse.com/blog/donpark/2007/01/18/visual-security-9-block-ip-identification">Don Park</a>
*/

package ca.psiphon.ploggy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
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

public class MonsterId {

    private static final String ASSETS_SUBDIRECTORY = "monsterid";
    private static final String CONFIG_FILENAME = "config.json";

    public static Bitmap getMonsterId(Context context, byte[] data) throws Utils.ApplicationError {
        
        // TODO: assets vs. res/raw -- memory management
        
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(data);
            ByteBuffer byteBuffer = ByteBuffer.wrap(digest);
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
            Random random = new Random(byteBuffer.getLong());
            
            AssetManager assetManager = context.getAssets();

            JSONObject config = new JSONObject(loadAssetToString(assetManager, CONFIG_FILENAME));

            int width = config.getInt("width");
            int height = config.getInt("height");

            Bitmap backgroundBitmap = loadAssetToBitmap(assetManager, config.getString("background"));
            Bitmap monsterBitmap = backgroundBitmap.copy(Bitmap.Config.ARGB_8888, true);
            backgroundBitmap.recycle();
            Canvas monsterCanvas = new Canvas(monsterBitmap);
            
            // TODO: http://stackoverflow.com/questions/4349075/bitmapfactory-decoderesource-returns-a-mutable-bitmap-in-android-2-2-and-an-immu/9194259#9194259
            // TOOD: http://stackoverflow.com/questions/4349075/bitmapfactory-decoderesource-returns-a-mutable-bitmap-in-android-2-2-and-an-immu/16314940#16314940
            // TODO: colorization

            JSONObject parts = config.getJSONObject("parts");
            Iterator<?> keys = parts.keys();
            while (keys.hasNext()) {
                JSONArray availableParts = parts.getJSONArray((String)keys.next());
                String selectedPart = availableParts.getString(random.nextInt(availableParts.length()));
                Bitmap partBitmap = loadAssetToBitmap(assetManager, selectedPart);

                Rect rect = new Rect(0, 0, width, height);
                Paint paint = new Paint();
                paint.setAlpha(255);
                monsterCanvas.drawBitmap(partBitmap, rect, rect, paint);
                partBitmap.recycle();
            }
            
            return monsterBitmap;
            
        } catch (IOException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        } catch (JSONException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        } catch (NoSuchAlgorithmException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        }
    }

    private static String loadAssetToString(AssetManager assetManager, String assetName) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = assetManager.open(new File(ASSETS_SUBDIRECTORY, assetName).getPath());            
            return Utils.inputStreamToString(inputStream);
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
