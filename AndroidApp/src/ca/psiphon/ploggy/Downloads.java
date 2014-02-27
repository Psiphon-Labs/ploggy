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

package ca.psiphon.ploggy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.content.Context;
import ca.psiphon.ploggy.Utils.ApplicationError;

/**
 * Helpers for managing download files
 */
public class Downloads {

    private static final String LOG_TAG = "Downloads";

    private static final String DOWNLOAD_FILENAME_FORMAT_STRING = "%s-%s.download";
    private static final String DOWNLOADS_DIRECTORY = "ploggyDownloads";

    public static long getDownloadedSize(Data.Download download) {
        return getDownloadFile(download).length();
    }

    public static OutputStream openDownloadResourceForAppending(Data.Download download) throws ApplicationError {
        try {
            OutputStream outputStream = new FileOutputStream(getDownloadFile(download), true);
            return outputStream;
        } catch (IOException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    public static File getDownloadFile(Data.Download download) {
        File directory = Utils.getApplicationContext().getDir(DOWNLOADS_DIRECTORY, Context.MODE_PRIVATE);
        directory.mkdirs();
        return new File(directory, String.format(DOWNLOAD_FILENAME_FORMAT_STRING, download.mPublisherId, download.mResourceId));
    }
}
