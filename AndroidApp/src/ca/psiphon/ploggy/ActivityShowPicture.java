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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

/**
 * User interface for displaying friend status details.

 * Invoke with an intent containing an extra with FRIEND_ID_BUNDLE_KEY.
 * This class subscribes to status events to update displayed data
 * while in the foreground.
 */
public class ActivityShowPicture extends ActivityPloggyBase {

    private static final String LOG_TAG = "Show Picture";

    private String mFilePath;
    private ImageView mPicture;

    private static final String EXTRA_PICTURE_FILE_PATH = "PICTURE_FILE_PATH";

    public static void startShowPicture(Context context, String pictureFilePath) {
        Intent intent = new Intent(context, ActivityShowPicture.class);
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_PICTURE_FILE_PATH, pictureFilePath);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_picture);

        mPicture = (ImageView)findViewById(R.id.show_picture_image);
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();

        Bundle bundle = getIntent().getExtras();
        if (bundle == null) {
            finish();
            return;
        }

        mFilePath = bundle.getString(EXTRA_PICTURE_FILE_PATH);
        if (mFilePath == null) {
            finish();
            return;
        }

        Pictures.loadPicture(new File(mFilePath), mPicture);
    }
}
