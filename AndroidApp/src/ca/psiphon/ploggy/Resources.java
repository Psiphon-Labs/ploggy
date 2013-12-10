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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import android.util.Pair;

/**
 * Helpers for managing local resource files
 * 
 * Supports two publishing modes:
 * 1. Picture, in which metadata is omitted from the shared copy, which is also automatically scaled down in data size
 * 2. Raw, in which the exact local file is shared (no metadata stripped, no scaling) 
 * 
 */
public class Resources {

    private static final String LOG_TAG = "Resources";

    private static final String LOCAL_RESOURCE_TEMPORARY_COPY_FILENAME_FORMAT_STRING = "%s.ploggyLocalResource"; 

    public static class MessageWithAttachments {
        public final Data.Message mMessage;
        public final List<Data.LocalResource> mLocalResources;

        public MessageWithAttachments(
                Data.Message message,
                List<Data.LocalResource> localResources) {
            mMessage = message;
            mLocalResources = localResources;
        }
    }
    
    public static MessageWithAttachments createMessageWithAttachment(
            Date messageTimestamp,
            String messageContent,
            Data.LocalResource.Type localResourceType,
            String attachmentMimeType,
            String attachmentFilePath) {        
         // Create a resource with a random ID and add it to the message
         // Friends only see the random ID, not the local resource file name
         // Note: never reusing resource IDs, even if same local e.g., file, has been published previously
         String id = Utils.formatFingerprint(Utils.getRandomBytes(Protocol.RESOURCE_ID_LENGTH));
         Data.Resource resource = new Data.Resource(id, attachmentMimeType, new File(attachmentFilePath).length());
         List<Data.Resource> messageAttachments = Arrays.asList(resource);
         Data.LocalResource localResource = new Data.LocalResource(localResourceType, id, attachmentMimeType, attachmentFilePath, null);
         List<Data.LocalResource> localResources = Arrays.asList(localResource);
         Data.Message message = new Data.Message(messageTimestamp, messageContent, messageAttachments);
         return new MessageWithAttachments(message, localResources);
     }

    public static InputStream openLocalResourceForReading(
            Data.LocalResource localResource, Pair<Long, Long> range) throws Utils.ApplicationError {
        InputStream inputStream = null;
        try {
            File file = new File(localResource.mFilePath);

            if (localResource.mType == Data.LocalResource.Type.PICTURE) {
                File temporaryCopyFile = getTemporaryCopyFile(localResource);
                // TODO: file size/date check sufficient?
                if (temporaryCopyFile.length() != file.length() ||
                        new Date(file.lastModified()).after(new Date(temporaryCopyFile.lastModified()))) {
                    Pictures.copyScaledBitmapWithoutMetadata(file, temporaryCopyFile);
                }
                file = temporaryCopyFile;
            }
            inputStream = new FileInputStream(file);
            // TODO: ignoring endAt (range.second)!
            if (range != null && range.first != inputStream.skip(range.first)) {
                throw new Utils.ApplicationError(LOG_TAG, "failed to seek to requested offset");
            }
            return inputStream;
        } catch (IOException e) {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e1) {
                }
            }
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }    
    
    private static File getTemporaryCopyFile(Data.LocalResource localResource) {
        File directory = Utils.getApplicationContext().getCacheDir();
        directory.mkdirs();
        return new File(directory, String.format(LOCAL_RESOURCE_TEMPORARY_COPY_FILENAME_FORMAT_STRING, localResource.mResourceId));
    }
}
