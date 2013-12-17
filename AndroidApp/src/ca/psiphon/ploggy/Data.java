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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import android.content.Context;

/**
 * Data persistence for self, friends, and status.
 *
 * On disk, data is represented as JSON stored in individual files. In memory, data is represented
 * as immutable POJOs which are thread-safe and easily serializable. Self and friend metadata, including
 * identity, and recent status data are kept in-memory. Large data such as map tiles will be left on
 * disk with perhaps an in-memory cache.
 * 
 * Simple consistency is provided: data changes are first written to a commit file, then the commit
 * file replaces the data file. In memory structures are replaced only after the file write succeeds.
 * 
 * If local security is added to the scope of Ploggy, here's where we'd interface with SQLCipher and/or
 * KeyChain, etc.
 * 
 * ==== PROTOTYPE NOTE ====
 * This module is performant for the prototype only. Missing are:
 * - incremental synchronization
 * - synchronization based on logical timestamp
 * - efficient data storage and viewing
 * ========================
 * 
 */
public class Data {
    
    private static final String LOG_TAG = "Data";
    
    public static class Self {
        public final Identity.PublicIdentity mPublicIdentity;
        public final Identity.PrivateIdentity mPrivateIdentity;
        public final Date mCreatedTimestamp;

        public Self(
                Identity.PublicIdentity publicIdentity,
                Identity.PrivateIdentity privateIdentity,
                Date createdTimestamp) {
            mPublicIdentity = publicIdentity;
            mPrivateIdentity = privateIdentity;
            mCreatedTimestamp = createdTimestamp;
        }
    }
    
    public static class Friend {
        public final String mId;
        public final Identity.PublicIdentity mPublicIdentity;
        public final Date mAddedTimestamp;
        public final Date mLastSentStatusTimestamp;
        public final Date mLastReceivedStatusTimestamp;

        public Friend(
                Identity.PublicIdentity publicIdentity,
                Date addedTimestamp) throws Utils.ApplicationError {
            this(publicIdentity, addedTimestamp, null, null);
        }
        public Friend(
                Identity.PublicIdentity publicIdentity,
                Date addedTimestamp,
                Date lastSentStatusTimestamp,
                Date lastReceivedStatusTimestamp) throws Utils.ApplicationError {
            mId = Utils.formatFingerprint(publicIdentity.getFingerprint());
            mPublicIdentity = publicIdentity;
            mAddedTimestamp = addedTimestamp;
            mLastSentStatusTimestamp = lastSentStatusTimestamp;
            mLastReceivedStatusTimestamp = lastReceivedStatusTimestamp;
        }
    }

    public class FriendComparator implements Comparator<Friend> {
        @Override
        public int compare(Friend a, Friend b) {
            return a.mPublicIdentity.mNickname.compareToIgnoreCase(b.mPublicIdentity.mNickname);
        }
    }
    
    public static class Message {
        public final Date mTimestamp;
        public final String mContent;
        public final List<Resource> mAttachments;

        public Message(
                Date timestamp,
                String content,
                List<Resource> attachments) {
            mTimestamp = timestamp;
            mContent = content;
            mAttachments = attachments;
        }
    }
    
    public static class AnnotatedMessage {
        public final Identity.PublicIdentity mPublicIdentity;
        public final String mFriendId;
        public final Data.Message mMessage;

        public AnnotatedMessage(
                Identity.PublicIdentity publicIdentity,
                String friendId,
                Data.Message message) {
            mPublicIdentity = publicIdentity;
            mFriendId = friendId;
            mMessage = message;
        }
    }

    public class AnnotatedMessageComparator implements Comparator<AnnotatedMessage> {
        @Override
        public int compare(AnnotatedMessage a, AnnotatedMessage b) {
            // Descending time order
            int result = b.mMessage.mTimestamp.compareTo(a.mMessage.mTimestamp);
            if (result == 0) {
                result = a.mPublicIdentity.mNickname.compareToIgnoreCase(b.mPublicIdentity.mNickname);
            }
            return result;
        }
    }
    
    public static class Location {
        public final Date mTimestamp;
        public final double mLatitude;
        public final double mLongitude;
        public final int mPrecision;
        public final String mStreetAddress;

        public Location(
                Date timestamp,
                double latitude,
                double longitude,
                int precision,
                String streetAddress) {
            mTimestamp = timestamp;
            mLatitude = latitude;
            mLongitude = longitude;
            mPrecision = precision;
            mStreetAddress = streetAddress;            
        }
    }
    
    public static class Status {
        final List<Message> mMessages;
        public final Location mLocation;

        public Status(
                List<Message> messages,
                Location location) {
            mMessages = messages;
            mLocation = location;
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
    
    public static class LocalResource {
        public enum Type {PICTURE, RAW}
        public final Type mType;
        public final String mResourceId;
        public final String mMimeType;
        public final String mFilePath;
        public final String mTempFilePath;

        public LocalResource(
                Type type,
                String resourceId,
                String mimeType,
                String filePath,
                String tempFilePath) {
            mType = type;
            mResourceId = resourceId;
            mMimeType = mimeType;
            mFilePath = filePath;
            mTempFilePath = tempFilePath;
        }
    }
    
    public static class Download {
        public final String mFriendId;
        public final String mResourceId;
        public final String mMimeType;
        public final long mSize;
        public enum State {IN_PROGRESS, CANCELLED, COMPLETE}
        public final State mState;

        public Download(
                String friendId,
                String resourceId,
                String mimeType,
                long size,
                State state) {
            mFriendId = friendId;
            mResourceId = resourceId;
            mMimeType = mimeType;
            mSize = size;
            mState = state;
        }
    }
    
    // TODO: fix -- having these errors as subclasses of Utils.ApplicationError with
    // no log can result in silent failures when functions only handle the base class
    
    public static class DataNotFoundError extends Utils.ApplicationError {
        private static final long serialVersionUID = -8736069103392081076L;
        
        public DataNotFoundError() {
            // No log for this expected condition
            super(null, "");
        }
    }

    public static class DataAlreadyExistsError extends Utils.ApplicationError {
        private static final long serialVersionUID = 6287628326991088141L;

        public DataAlreadyExistsError() {
            // No log for this expected condition
            super(null, "");
        }
    }

    // ---- Singleton ----
    private static Data instance = null;
    public static synchronized Data getInstance() {
       if(instance == null) {
          instance = new Data();
       }
       return instance;
    }
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
    // -------------------

    // TODO: SQLCipher/IOCipher storage? key/value store?
    // TODO: use http://nelenkov.blogspot.ca/2011/11/using-ics-keychain-api.html?
    // ...consistency: write file, then update in-memory; 2pc; only for short lists of friends
    // ...eventually use file system for map tiles etc.
       
    private static final String DATA_DIRECTORY = "ploggyData"; 
    private static final String SELF_FILENAME = "self.json"; 
    private static final String SELF_STATUS_FILENAME = "selfStatus.json"; 
    private static final String FRIENDS_FILENAME = "friends.json"; 
    private static final String FRIEND_STATUS_FILENAME_FORMAT_STRING = "%s-friendStatus.json"; 
    private static final String LOCAL_RESOURCES_FILENAME = "localResources.json"; 
    private static final String DOWNLOADS_FILENAME = "downloads.json"; 
    private static final String COMMIT_FILENAME_SUFFIX = ".commit"; 
    
    Self mSelf;
    Status mSelfStatus;
    Location mPrivateSelfLocation;
    List<Friend> mFriends;
    HashMap<String, Status> mFriendStatuses;
    List<AnnotatedMessage> mNewMessages;
    List<AnnotatedMessage> mAllMessages;
    List<LocalResource> mLocalResources;
    List<Download> mDownloads;

    public synchronized void reset() throws Utils.ApplicationError {
        // Warning: deletes all files in DATA_DIRECTORY (not recursively)
        File directory = Utils.getApplicationContext().getDir(DATA_DIRECTORY, Context.MODE_PRIVATE);
        directory.mkdirs();
        boolean deleteFailed = false;
        for (String child : directory.list()) {
            File file = new File(directory, child);
            if (file.isFile()) {
                if (!file.delete()) {
                    deleteFailed = true;
                    // Keep attempting to delete remaining files...
                }
            }
        }
        if (deleteFailed) {
            throw new Utils.ApplicationError(LOG_TAG, "delete data file failed");
        }
    }
    
    public synchronized Self getSelf() throws Utils.ApplicationError, DataNotFoundError {
        if (mSelf == null) {
            mSelf = Json.fromJson(readFile(SELF_FILENAME), Self.class);
        }
        return mSelf;
    }

    public synchronized void updateSelf(Self self) throws Utils.ApplicationError {
        // When creating a new identity, remove status from previous identity
        deleteFile(String.format(SELF_STATUS_FILENAME));
        writeFile(SELF_FILENAME, Json.toJson(self));
        mSelf = self;
        Log.addEntry(LOG_TAG, "updated your identity");
        Events.post(new Events.UpdatedSelf());
    }

    public synchronized Status getSelfStatus() throws Utils.ApplicationError {
        if (mSelfStatus == null) {
            try {
                mSelfStatus = Json.fromJson(readFile(SELF_STATUS_FILENAME), Status.class);
            } catch (DataNotFoundError e) {
                // If there's no previous status, return a blank one
                return new Status(new ArrayList<Message>(), new Location(null, 0, 0, 0, null));
            }
        }
        return mSelfStatus;
    }

    public synchronized Location getCurrentSelfLocation() throws Utils.ApplicationError {
        // If location sharing was off when updateSelfStatusLocation was last called, then
        // mPrivateSelfLocation is the more up-to-date than mSelfStatus.
        if (mPrivateSelfLocation == null) {
            return getSelfStatus().mLocation;
        }
        return mPrivateSelfLocation;
    }

    public synchronized void addSelfStatusMessage(Message message, List<LocalResource> attachmentLocalResources) throws Utils.ApplicationError, DataNotFoundError {
        // Hack: initMessages before committing new message to avoid duplicate adds in addSelfMessageHelper
        initMessages();
        initLocalResources();
        List<LocalResource> newLocalResources = null;
        if (attachmentLocalResources != null) {
            newLocalResources = new ArrayList<LocalResource>(mLocalResources);
            newLocalResources.addAll(attachmentLocalResources);
        }

        Status currentStatus = getSelfStatus();
        List<Message> messages = new ArrayList<Message>(currentStatus.mMessages);
        messages.add(0, message);
        while (messages.size() > Protocol.MAX_MESSAGE_COUNT) {
            messages.remove(messages.size() - 1);
        }
        Status newStatus = new Status(messages, currentStatus.mLocation);

        if (newLocalResources != null) {
            writeFile(LOCAL_RESOURCES_FILENAME, Json.toJson(newLocalResources));
        }
        writeFile(SELF_STATUS_FILENAME, Json.toJson(newStatus));
        if (newLocalResources != null) {
            mLocalResources.addAll(attachmentLocalResources);
        }
        mSelfStatus = newStatus;
        Log.addEntry(LOG_TAG, "added your message");
        Events.post(new Events.UpdatedSelfStatus());
        addSelfMessageHelper(getSelf(), message);
    }

    public synchronized void updateSelfStatusLocation(Location location, boolean shared) throws Utils.ApplicationError {
        if (shared) {
            Status currentStatus = getSelfStatus();
            Status newStatus = new Status(currentStatus.mMessages, location);
            writeFile(SELF_STATUS_FILENAME, Json.toJson(newStatus));
            mSelfStatus = newStatus;
            mPrivateSelfLocation = location;
        } else {
            mPrivateSelfLocation = location;
        }
        Log.addEntry(LOG_TAG, "updated your location");
        Events.post(new Events.UpdatedSelfStatus());
    }

    private void initFriends() throws Utils.ApplicationError {
        if (mFriends == null) {
            try {
                mFriends = new ArrayList<Friend>(Arrays.asList(Json.fromJson(readFile(FRIENDS_FILENAME), Friend[].class)));
            } catch (DataNotFoundError e) {
                mFriends = new ArrayList<Friend>();
            }
        }
    }
    
    public synchronized List<Friend> getFriends() throws Utils.ApplicationError {
        initFriends();
        List<Friend> friends = new ArrayList<Friend>(mFriends);
        Collections.sort(friends, new FriendComparator());
        return friends;
    }

    public synchronized Friend getFriendById(String id) throws Utils.ApplicationError, DataNotFoundError {
        initFriends();
        for (Friend friend : mFriends) {
            if (friend.mId.equals(id)) {
                return friend;
            }
        }
        throw new DataNotFoundError();
    }

    public synchronized Friend getFriendByNickname(String nickname) throws Utils.ApplicationError, DataNotFoundError {
        initFriends();
        for (Friend friend : mFriends) {
            if (friend.mPublicIdentity.mNickname.equals(nickname)) {
                return friend;
            }
        }
        throw new DataNotFoundError();
    }

    public synchronized Friend getFriendByCertificate(String certificate) throws Utils.ApplicationError, DataNotFoundError {
        initFriends();
        for (Friend friend : mFriends) {
            if (friend.mPublicIdentity.mX509Certificate.equals(certificate)) {
                return friend;
            }
        }
        throw new DataNotFoundError();
    }

    public synchronized void addFriend(Friend friend) throws Utils.ApplicationError {
        initFriends();
        boolean friendWithIdExists = true;
        boolean friendWithNicknameExists = true;
        try {
            getFriendById(friend.mId);
        } catch (DataNotFoundError e) {
            friendWithIdExists = false;
        }
        try {
            getFriendByNickname(friend.mPublicIdentity.mNickname);
        } catch (DataNotFoundError e) {
            friendWithNicknameExists = false;
        }
        // TODO: report which conflict occurred
        if (friendWithIdExists || friendWithNicknameExists) {
            throw new DataAlreadyExistsError();
        }
        List<Friend> newFriends = new ArrayList<Friend>(mFriends);
        newFriends.add(friend);
        writeFile(FRIENDS_FILENAME, Json.toJson(newFriends));
        mFriends.add(friend);
        Log.addEntry(LOG_TAG, "added friend: " + friend.mPublicIdentity.mNickname);
        Events.post(new Events.AddedFriend(friend.mId));
    }

    private void updateFriendHelper(List<Friend> list, Friend friend) throws DataNotFoundError {
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).mId.equals(friend.mId)) {
                list.set(i, friend);
                found = true;
                break;
            }
        }
        if (!found) {
            throw new DataNotFoundError();
        }
    }

    public synchronized void updateFriend(Friend friend) throws Utils.ApplicationError {
        initFriends();
        List<Friend> newFriends = new ArrayList<Friend>(mFriends);
        updateFriendHelper(newFriends, friend);
        writeFile(FRIENDS_FILENAME, Json.toJson(newFriends));
        updateFriendHelper(mFriends, friend);
        Log.addEntry(LOG_TAG, "updated friend: " + friend.mPublicIdentity.mNickname);
        Events.post(new Events.UpdatedFriend(friend.mId));
    }

    public synchronized Date getFriendLastSentStatusTimestamp(String friendId) throws Utils.ApplicationError {
        Friend friend = getFriendById(friendId);
        return friend.mLastSentStatusTimestamp;
    }
    
    public synchronized void updateFriendLastSentStatusTimestamp(String friendId) throws Utils.ApplicationError {
        // TODO: don't write an entire file for each timestamp update!
        Friend friend = getFriendById(friendId);
        updateFriend(
            new Friend(
                friend.mPublicIdentity,
                friend.mAddedTimestamp,
                new Date(),
                friend.mLastReceivedStatusTimestamp));
    }
    
    public synchronized Date getFriendLastReceivedStatusTimestamp(String friendId) throws Utils.ApplicationError {
        Friend friend = getFriendById(friendId);
        return friend.mLastReceivedStatusTimestamp;
    }
    
    public synchronized void updateFriendLastReceivedStatusTimestamp(String friendId) throws Utils.ApplicationError {
        // TODO: don't write an entire file for each timestamp update!
        Friend friend = getFriendById(friendId);
        updateFriend(
            new Friend(
                friend.mPublicIdentity,
                friend.mAddedTimestamp,
                friend.mLastSentStatusTimestamp,
                new Date()));
    }
    
    private void removeFriendHelper(String id, List<Friend> list) throws DataNotFoundError {
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).mId.equals(id)) {
                list.remove(i);
                found = true;
                break;
            }
        }
        if (!found) {
            throw new DataNotFoundError();
        }
    }

    public synchronized void removeFriend(String id) throws Utils.ApplicationError, DataNotFoundError {
        initFriends();
        Friend friend = getFriendById(id);
        deleteFile(String.format(FRIEND_STATUS_FILENAME_FORMAT_STRING, id));
        List<Friend> newFriends = new ArrayList<Friend>(mFriends);
        removeFriendHelper(id, newFriends);
        writeFile(FRIENDS_FILENAME, Json.toJson(newFriends));
        removeFriendHelper(id, mFriends);
        Log.addEntry(LOG_TAG, "removed friend: " + friend.mPublicIdentity.mNickname);
        Events.post(new Events.RemovedFriend(id));
        // Reset all-messages to remove messages from deleted friend
        // TODO: reset new-messages
        mAllMessages = null;
        initMessages();
    }

    public synchronized Status getFriendStatus(String id) throws Utils.ApplicationError, DataNotFoundError {
        String filename = String.format(FRIEND_STATUS_FILENAME_FORMAT_STRING, id);
        return Json.fromJson(readFile(filename), Status.class);
    }

    public synchronized void updateFriendStatus(String id, Status status) throws Utils.ApplicationError {
        // Hack: initMessages before committing new status to avoid duplicate adds in addFriendMessagesHelper
        initMessages();
        Friend friend = getFriendById(id);
        Status previousStatus = null;
        try {
            previousStatus = getFriendStatus(id);
            
            // Mitigate push/pull race condition where older status overwrites newer status
            // Only checks messages, not location
            // TODO: more robust protocol... don't rely on clocks
            if (previousStatus.mMessages.size() > status.mMessages.size()) {
                Log.addEntry(LOG_TAG, "discarded friend status (fewer messages): " + friend.mPublicIdentity.mNickname);
                return;
            } else if (previousStatus.mMessages.size() == status.mMessages.size() && previousStatus.mMessages.size() > 0) {
                if (previousStatus.mMessages.get(0).mTimestamp == null || status.mMessages.get(0).mTimestamp == null) {
                    Log.addEntry(LOG_TAG, "discarded friend status (timestamp unexpectedly null): " + friend.mPublicIdentity.mNickname);
                    return;
                } else if (previousStatus.mMessages.get(0).mTimestamp.after(status.mMessages.get(0).mTimestamp)) {
                    Log.addEntry(LOG_TAG, "discarded friend status (older timestamp): " + friend.mPublicIdentity.mNickname);
                    return;
                }
            }
        } catch (DataNotFoundError e) {
        }
        String filename = String.format(FRIEND_STATUS_FILENAME_FORMAT_STRING, id);
        writeFile(filename, Json.toJson(status));
        Log.addEntry(LOG_TAG, "updated friend status: " + friend.mPublicIdentity.mNickname);
        Events.post(new Events.UpdatedFriendStatus(friend.mId));
        addFriendMessagesHelper(friend, status, previousStatus);
    }

    private void initMessages() throws Utils.ApplicationError {
        // TODO: this implementation is only intended for the prototype, which isn't sending incremental updates
        // TODO: persistent (on disk) new-message state?
        // Note: new-messages is not cleared in start() or stop(), so its state is retained when the Engine restarts
        if (mNewMessages == null) {
            mNewMessages = new ArrayList<AnnotatedMessage>();
        }
        if (mAllMessages == null) {
            mAllMessages = new ArrayList<AnnotatedMessage>();
            Self self = getSelf();
            try {
                for (Message message : getSelfStatus().mMessages) {
                    mAllMessages.add(new AnnotatedMessage(self.mPublicIdentity, null, message));
                }
            } catch (DataNotFoundError e) {
                // Skip
            }
            for (Friend friend : getFriends()) {
                // Hack to continue supporting self-as-friend, for now
                if (!self.mPublicIdentity.mX509Certificate.equals(friend.mPublicIdentity.mX509Certificate)) {
                    try {
                        for (Message message : getFriendStatus(friend.mId).mMessages) {
                            mAllMessages.add(new AnnotatedMessage(friend.mPublicIdentity, friend.mId, message));
                        }
                    } catch (DataNotFoundError e) {
                        // Skip
                    }
                }
            }
            Collections.sort(mAllMessages, new AnnotatedMessageComparator());
            Events.post(new Events.UpdatedAllMessages());
        }
    }
    
    private void addFriendMessagesHelper(Friend friend, Status status, Status previousStatus) throws Utils.ApplicationError {
        // TODO: this implementation is only intended for the prototype, which isn't sending incremental updates
        initMessages();
        Data.Message lastMessage = null;
        if (previousStatus != null &&
                previousStatus.mMessages.size() > 0) {
            lastMessage = previousStatus.mMessages.get(0);
        }
        List<AnnotatedMessage> newMessages = new ArrayList<AnnotatedMessage>();
        for (Data.Message message : status.mMessages) {
            if (lastMessage == null ||
                    !message.mTimestamp.equals(lastMessage.mTimestamp) ||
                    !message.mContent.equals(lastMessage.mContent)) {
                newMessages.add(new AnnotatedMessage(friend.mPublicIdentity, friend.mId, message));                
                // Automatically enqueue new message attachments for download
                for (Resource resource : message.mAttachments) {
                    try {
                        addDownload(friend.mId, resource);
                    } catch (DataAlreadyExistsError e) {
                        // Ignore
                    }
                }
            } else {
                break;
            }
        }

        if (newMessages.size() > 0) {            
            mNewMessages.addAll(0, newMessages);
            Events.post(new Events.UpdatedNewMessages());
            // Hack to continue supporting self-as-friend, for now
            if (!getSelf().mPublicIdentity.mX509Certificate.equals(friend.mPublicIdentity.mX509Certificate)) {
                mAllMessages.addAll(0, newMessages);
                Collections.sort(mAllMessages, new AnnotatedMessageComparator());
                Events.post(new Events.UpdatedAllMessages());
            }
        }
    }

    private void addSelfMessageHelper(Self self, Message message) throws Utils.ApplicationError {
        initMessages();
        mAllMessages.add(0, new AnnotatedMessage(self.mPublicIdentity, null, message));
        Events.post(new Events.UpdatedAllMessages());
    }
    
    public synchronized List<AnnotatedMessage> getNewMessages() throws Utils.ApplicationError {
        initMessages();
        return new ArrayList<AnnotatedMessage>(mNewMessages);
    }
    
    public synchronized void resetNewMessages() throws Utils.ApplicationError {
        initMessages();
        boolean updatedNewMessages = (mNewMessages.size() > 0);
        mNewMessages.clear();
        if (updatedNewMessages) {
            Events.post(new Events.UpdatedNewMessages());
        }
    }
    
    public synchronized List<AnnotatedMessage> getAllMessages() throws Utils.ApplicationError {
        initMessages();
        return new ArrayList<AnnotatedMessage>(mAllMessages);
    }
    
    private void initLocalResources() throws Utils.ApplicationError {
        if (mLocalResources == null) {
            try {
                mLocalResources = new ArrayList<LocalResource>(Arrays.asList(Json.fromJson(readFile(LOCAL_RESOURCES_FILENAME), LocalResource[].class)));
            } catch (DataNotFoundError e) {
                mLocalResources = new ArrayList<LocalResource>();
            }
        }
    }
    
    public synchronized LocalResource getLocalResource(String resourceId) throws Utils.ApplicationError, DataNotFoundError {
        initLocalResources();
        for (LocalResource localResource : mLocalResources) {
            if (localResource.mResourceId.equals(resourceId)) {
                return localResource;
            }
        }
        throw new DataNotFoundError();
    }

    private void initDownloads() throws Utils.ApplicationError {
        if (mDownloads == null) {
            try {
                mDownloads = new ArrayList<Download>(Arrays.asList(Json.fromJson(readFile(DOWNLOADS_FILENAME), Download[].class)));
            } catch (DataNotFoundError e) {
                mDownloads = new ArrayList<Download>();
            }
        }
    }

    public synchronized Download getDownload(String friendId, String resourceId) throws Utils.ApplicationError, DataNotFoundError {
        initDownloads();
        for (Download download : mDownloads) {
            if (download.mFriendId.equals(friendId) && download.mResourceId.equals(resourceId)) {
                return download;
            }
        }
        throw new DataNotFoundError();
    }

    public synchronized Download getNextInProgressDownload(String friendId) throws Utils.ApplicationError, DataNotFoundError {
        initDownloads();
        for (Download download : mDownloads) {
            if (download.mFriendId.equals(friendId) && download.mState == Download.State.IN_PROGRESS) {
                return download;
            }
        }
        throw new DataNotFoundError();
    }

    public synchronized void addDownload(String friendId, Resource resource) throws Utils.ApplicationError, DataAlreadyExistsError {
        initDownloads();
        try {
            getDownload(friendId, resource.mId);
            throw new DataAlreadyExistsError();
        } catch (DataNotFoundError e) {
        }        
        Friend friend = getFriendById(friendId);
        // TODO: double check resource ID is from valid resource in friend message?
        Download download = new Download(friendId, resource.mId, resource.mMimeType, resource.mSize, Download.State.IN_PROGRESS);
        List<Download> newDownloads = new ArrayList<Download>(mDownloads);
        newDownloads.add(download);
        writeFile(DOWNLOADS_FILENAME, Json.toJson(newDownloads));
        mDownloads.add(download);
        Log.addEntry(LOG_TAG, "added download from friend: " + friend.mPublicIdentity.mNickname);
        Events.post(new Events.AddedDownload(friendId, resource.mId));
    }
    
    private void updateDownloadHelper(List<Download> list, Download download) throws DataNotFoundError {
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).mFriendId.equals(download.mFriendId) && list.get(i).mResourceId.equals(download.mResourceId)) {
                list.set(i, download);
                found = true;
                break;
            }
        }
        if (!found) {
            throw new DataNotFoundError();
        }
    }

    public synchronized void updateDownloadState(String friendId, String resourceId, Download.State state) throws Utils.ApplicationError, DataNotFoundError {
        initDownloads();
        Friend friend = getFriendById(friendId);
        Download download = getDownload(friendId, resourceId);
        Download newDownload = new Download(download.mFriendId, download.mResourceId, download.mMimeType, download.mSize, state);

        List<Download> newDownloads = new ArrayList<Download>(mDownloads);
        updateDownloadHelper(newDownloads, newDownload);
        writeFile(DOWNLOADS_FILENAME, Json.toJson(newDownloads));
        updateDownloadHelper(mDownloads, newDownload);

        if (state == Download.State.IN_PROGRESS) {
            Log.addEntry(LOG_TAG, "resumed download from friend: " + friend.mPublicIdentity.mNickname);
        } else if (state == Download.State.CANCELLED) {
            Log.addEntry(LOG_TAG, "cancelled download from friend: " + friend.mPublicIdentity.mNickname);
        } else if (state == Download.State.COMPLETE) {
            Log.addEntry(LOG_TAG, "completed download from friend: " + friend.mPublicIdentity.mNickname);
        }
        // *** TODO: engine stop downloading on cancel
        // *** TODO: delete download file on cancel
        //Events.post(new Events.UpdatedDownloadState());
    }

    private static String readFile(String filename) throws Utils.ApplicationError, DataNotFoundError {
        FileInputStream inputStream = null;
        try {
            File directory = Utils.getApplicationContext().getDir(DATA_DIRECTORY, Context.MODE_PRIVATE);
            String commitFilename = filename + COMMIT_FILENAME_SUFFIX;
            File commitFile = new File(directory, commitFilename);
            File file = new File(directory, filename);
            replaceFileIfExists(commitFile, file);
            inputStream = new FileInputStream(file);
            return Utils.readInputStreamToString(inputStream);
        } catch (FileNotFoundException e) {
            throw new DataNotFoundError();
        } catch (IOException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                }
            }
        }        
    }

    private static void writeFile(String filename, String value) throws Utils.ApplicationError {
        FileOutputStream outputStream = null;
        try {
            File directory = Utils.getApplicationContext().getDir(DATA_DIRECTORY, Context.MODE_PRIVATE);
            String commitFilename = filename + COMMIT_FILENAME_SUFFIX;
            File commitFile = new File(directory, commitFilename);
            File file = new File(directory, filename);
            outputStream = new FileOutputStream(commitFile);
            outputStream.write(value.getBytes());
            outputStream.close();
            replaceFileIfExists(commitFile, file);
        } catch (IOException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private static void replaceFileIfExists(File commitFile, File file) throws IOException {
        if (commitFile.exists()) {
            file.delete();
            commitFile.renameTo(file);
        }
    }
    
    private static void deleteFile(String filename) throws Utils.ApplicationError {
        File directory = Utils.getApplicationContext().getDir(DATA_DIRECTORY, Context.MODE_PRIVATE);
        File file = new File(directory, filename);
        if (!file.delete()) {
            if (file.exists()) {
                throw new Utils.ApplicationError(LOG_TAG, "failed to delete file");
            }
        }
    }
}
