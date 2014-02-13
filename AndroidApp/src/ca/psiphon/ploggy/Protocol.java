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

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Ploggy protocol
 *
 * On disk and within the protocol, data is represented as JSON. In memory, data
 * is represented as immutable POJOs which are thread-safe and easily serializable.
 * Helpers and defined values for verifying/enforcing Ploggy protocol.
 */
public class Protocol {

    public static final String WEB_SERVER_PROTOCOL = "https";

    public static final int WEB_SERVER_VIRTUAL_PORT = 443;

    public static final String ASK_PULL_GET_REQUEST_PATH = "/askPull";

    public static final String ASK_LOCATION_GET_REQUEST_PATH = "/askLocation";

    public static final String PUSH_PUT_REQUEST_PATH = "/push";
    public static final String PUSH_PUT_REQUEST_MIME_TYPE = "application/json";

    public static final String PULL_PUT_REQUEST_PATH = "/pull";
    public static final String PULL_PUT_REQUEST_MIME_TYPE = "application/json";
    public static final String PULL_PUT_RESPONSE_MIME_TYPE = "application/json";

    public static final String DOWNLOAD_GET_REQUEST_PATH = "/download";
    public static final String DOWNLOAD_GET_REQUEST_RESOURCE_ID_PARAMETER = "resourceId";

    public static final String POST_CONTENT_TYPE_DEFAULT = "text/plain";

    public static int RESOURCE_ID_LENGTH = 32;

    public static int MAX_MESSAGE_SIZE = 1000000;

    public static class SequenceNumbers {
        public final long mGroupSequenceNumber;
        public final long mPostSequenceNumber;

        public SequenceNumbers(
                long groupSequenceNumber,
                long postSequenceNumber) {
            mGroupSequenceNumber = groupSequenceNumber;
            mPostSequenceNumber = postSequenceNumber;
        }
    }

    public static class PullRequest {
        public final Map<String, SequenceNumbers> mGroupLastKnownSequenceNumbers;
        public final List<String> mGroupsToResignMembership;

        public PullRequest(
                Map<String, SequenceNumbers> groupLastKnownSequenceNumbers,
                List<String> groupsToResignMembership) {
            mGroupLastKnownSequenceNumbers = groupLastKnownSequenceNumbers;
            mGroupsToResignMembership = groupsToResignMembership;
        }
    }

    public static class PushRequest {
        public enum Type {
            GROUP,
            POST,
            LOCATION
        }
        public final Type mType;
        public final Object mObject;

        public PushRequest(
                Type type,
                Object object) {
            mType = type;
            mObject = object;
        }
    }

    public static class Group {
        public final String mId;
        public final String mName;
        public final String mPublisherId;
        //public final Identity.PublicIdentity mPublisher; // ...TODO: redundant?
        public final List<Identity.PublicIdentity> mMembers; //...TODO: need to include this for case where member isn't a friend...
        public final Date mCreatedTimestamp;
        public final Date mModifiedTimestamp;
        public final long mSequenceNumber;
        public final boolean mIsTombstone;

        public Group(
                String id,
                String name,
                String publisherId,
                List<Identity.PublicIdentity> members,
                Date createdTimestamp,
                Date modifiedTimestamp,
                long sequenceNumber,
                boolean isTombstone) {
            mId = id;
            mName = name;
            mPublisherId = publisherId;
            mMembers = members;
            mCreatedTimestamp = createdTimestamp;
            mModifiedTimestamp = modifiedTimestamp;
            mSequenceNumber = sequenceNumber;
            mIsTombstone = isTombstone;
        }
    }

    public static class Location {
        public final Date mTimestamp;
        public final double mLatitude;
        public final double mLongitude;
        public final String mStreetAddress;

        public Location(
                Date timestamp,
                double latitude,
                double longitude,
                String streetAddress) {
            mTimestamp = timestamp;
            mLatitude = latitude;
            mLongitude = longitude;
            mStreetAddress = streetAddress;
        }
    }

    public static class Post {
        public final String mId;
        public final String mPublisherId;
        public final String mGroupId;
        public final String mContentType;
        public final String mContent;
        public final List<Resource> mAttachments;
        public final Date mCreatedTimestamp;
        public final Date mModifiedTimestamp;
        public final long mSequenceNumber;
        public final boolean mIsTombstone;

        public Post(
                String id,
                String groupId,
                String publisherId,
                String contentType,
                String content,
                List<Resource> attachments,
                Date createdTimestamp,
                Date modifiedTimestamp,
                long sequenceNumber,
                boolean isTombstone) {
            mId = id;
            mGroupId = groupId;
            mPublisherId = publisherId;
            mContentType = contentType;
            mContent = content;
            mAttachments = attachments;
            mCreatedTimestamp = createdTimestamp;
            mModifiedTimestamp = modifiedTimestamp;
            mSequenceNumber = sequenceNumber;
            mIsTombstone = isTombstone;
        }
    }

    public static class Resource {
        public final String mId;
        public final String mMimeType;
        public final long mSize;

        public Resource(
                String id,
                String mimeType,
                long size) {
            mId = id;
            mMimeType = mimeType;
            mSize = size;
        }
    }

    public static boolean isValidNickname(String nickname) {
        // TODO: valid characters?
        return nickname.length() > 0;
    }

    public static void validatePublicIdentity(Identity.PublicIdentity publicIdentity) throws Utils.ApplicationError {
        // TODO: Nickname valid, cert valid, hostname valid
        // Identity.verifyPublicIdentity(friend.mPublicIdentity);
    }

    public static void validatePullRequest(PullRequest pullRequest) throws Utils.ApplicationError {
        // TODO: ...
    }

    public static void validateGroup(Group group) throws Utils.ApplicationError {
        // TODO: ...
    }

    public static void validateLocation(Location location) throws Utils.ApplicationError {
        // TODO: ...
    }

    public static void validatePost(Post post) throws Utils.ApplicationError {
        // TODO: ...
    }
}
