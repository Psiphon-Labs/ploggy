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

import android.os.Bundle;
import android.widget.ImageView;

/**
 * User interface for displaying friend status details.

 * Invoke with an intent containing an extra with FRIEND_ID_BUNDLE_KEY.
 * This class subscribes to status events to update displayed data
 * while in the foreground.
 */
public class ActivityShowPicture extends ActivitySendIdentityByNfc {

    private static final String LOG_TAG = "Show Picture";

    public static final String FILE_PATH_BUNDLE_KEY = "filePath";

    private String mFilePath;
    private ImageView mPicture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_picture);

        Bundle bundle = getIntent().getExtras();
        if (bundle == null) {
            finish();
            return;
        }

        mFilePath = bundle.getString(FILE_PATH_BUNDLE_KEY);
        if (mFilePath == null) {
            finish();
            return;
        }

        mPicture = (ImageView)findViewById(R.id.show_picture_image);
        Pictures.loadPicture(new File(mFilePath), mPicture);
    }
}
