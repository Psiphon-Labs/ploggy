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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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

    public static class PostWithAttachments {
        public final Protocol.Post mPost;
        public final List<Data.LocalResource> mLocalResources;

        public PostWithAttachments(
                Protocol.Post post,
                List<Data.LocalResource> localResources) {
            mPost = post;
            mLocalResources = localResources;
        }
    }

    public static PostWithAttachments createPostWithAttachment(
            Data data,
            String postGroupId,
            Date postTimestamp,
            String postContent,
            Data.LocalResource.Type localResourceType,
            String attachmentMimeType,
            String attachmentFilePath) throws Utils.ApplicationError {
         // Create a resource with a random ID and add it to the post
         // Friends only see the random ID, not the local resource file name
         // Note: never reusing resource IDs, even if same local e.g., file, has been published previously
         String resourceId = Utils.makeId();
         List<Protocol.Resource> attachments = new ArrayList<Protocol.Resource>();
         List<Data.LocalResource> localResources = new ArrayList<Data.LocalResource>();
         if (attachmentMimeType != null || attachmentFilePath != null) {
             File file = new File(attachmentFilePath);
             if (localResourceType == Data.LocalResource.Type.PICTURE) {
                 // If the resource is transformed (e.g., picture is auto-scaled-down and has no metadata)
                 // then we need to do that now, to get the correct size
                 file = makeScaledDownPictureFileCopy(attachmentFilePath, resourceId);
             }
             attachments.add(new Protocol.Resource(resourceId, attachmentMimeType, file.length()));
             localResources.add(new Data.LocalResource(resourceId, postGroupId, localResourceType, attachmentMimeType, attachmentFilePath, null));
         }
         return new PostWithAttachments(
                 new Protocol.Post(
                         Utils.makeId(),
                         postGroupId,
                         data.getSelfOrThrow().mId,
                         Protocol.POST_CONTENT_TYPE_DEFAULT,
                         postContent,
                         attachments,
                         postTimestamp,
                         postTimestamp,
                         Data.UNASSIGNED_SEQUENCE_NUMBER,
                         false),
                 localResources);
     }

    public static InputStream openLocalResourceForReading(
            Data.LocalResource localResource, Pair<Long, Long> range) throws Utils.ApplicationError {
        InputStream inputStream = null;
        try {
            File file = new File(localResource.mFilePath);
            if (localResource.mType == Data.LocalResource.Type.PICTURE) {
                file = makeScaledDownPictureFileCopy(localResource.mFilePath, localResource.mResourceId);
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

    private static File getTemporaryCopyFile(String resourceId) {
        File directory = Utils.getApplicationContext().getCacheDir();
        directory.mkdirs();
        return new File(directory, String.format(LOCAL_RESOURCE_TEMPORARY_COPY_FILENAME_FORMAT_STRING, resourceId));
    }

    private static File makeScaledDownPictureFileCopy(String sourceFilePath, String resourceId) throws Utils.ApplicationError {
        File file = new File(sourceFilePath);
        File temporaryCopyFile = getTemporaryCopyFile(resourceId);
        // TODO: date check sufficient?
        if (!temporaryCopyFile.exists() ||
                new Date(file.lastModified()).after(new Date(temporaryCopyFile.lastModified()))) {
            Pictures.copyScaledBitmapWithoutMetadata(file, temporaryCopyFile);
        }
        return temporaryCopyFile;
    }
}
