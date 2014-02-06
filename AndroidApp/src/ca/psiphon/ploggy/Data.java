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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Manages local, persistent Ploggy data. Implementation of sync protocol
 * and other data-related "business logic".
 *
 * Data persistence implemented using Sqlite/Sqlcipher. Code is written assuming
 * SQLite WAL-mode concurrency and transaction isolation. BEGIN IMMEDIATE is the
 * preferred behavior for write transactions. Long running read transactions will
 * block the WAL checkpointer and are to be avoided. Multiple readers are 
 * supported and expected (no "synchronized" mutex on Data object).
 * See: http://www.sqlite.org/wal.html and http://www.sqlite.org/isolation.html.
 * Also see: http://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html#enableWriteAheadLogging%28%29
 * regarding SQLiteDatabase thread-safety and WAL-mode.
 * 
 * Use of SQL is encapsulated: API exposes Java POJOs and iterators.
 *
 * Use getInstance() to get the singleton Data instance. Use the version that takes
 * a name parameter to get an independent Data instance for testing.
 */

/*

*IN PROGRESS*

- Resolve whether to use execSQL vs. update() for UPDATEs with expressions in the SET clause
- Friend: add column(s) to store data transfer per friend
- Friend lastSentSequenceNumber: needs to be per Group
- Support per-group privacy settings
- Delete-friend-who-is-own-group-member: friend won't sync loss of membership; but could it be inferred based on a 403 error?
- Group order in pull requests: using LinkedHashMap but order is lost in JSON: http://stackoverflow.com/questions/5396680/whats-the-proper-represantation-of-linkedhashmap-in-json
- Create indexes for queries (see http://www.sqlite.org/optoverview.html)
- Should we have explicit transactions for returned cursors (and have CursorIterator track a transaction)?
- Cache POJOs (especially for getFriend and getGroup)
- Post to event bus; e.g. to trigger UI updates due to cascading deletes
- Prepared statements?
- Review all "*TODO*" comments
- Switch to SQLCipher
- Unit tests (or coverage in Tests module)

*/

public class Data extends SQLiteOpenHelper {

    private static final String LOG_TAG = "Data";

    public static class NotFoundError extends Exception {
        private static final long serialVersionUID = -8736069103392081076L;

        public NotFoundError() {
        }
    }

    public static class Self {
        public final String mId;
        public final Identity.PublicIdentity mPublicIdentity;
        public final Identity.PrivateIdentity mPrivateIdentity;
        public final Date mCreatedTimestamp;

        public Self(
                Identity.PublicIdentity publicIdentity,
                Identity.PrivateIdentity privateIdentity,
                Date createdTimestamp) throws Utils.ApplicationError {
            mId = publicIdentity.getId();
            mPublicIdentity = publicIdentity;
            mPrivateIdentity = privateIdentity;
            mCreatedTimestamp = createdTimestamp;
        }
    }

    public static class CandidateFriend {
        public final String mId;
        public final Identity.PublicIdentity mPublicIdentity;
        public final List<String> mGroupIds;

        public CandidateFriend(
                Identity.PublicIdentity publicIdentity,
                List<String> groupIds) throws Utils.ApplicationError {
            mId = publicIdentity.getId();
            mPublicIdentity = publicIdentity;
            mGroupIds = groupIds;
        }
    }

    public static class Friend {
        public final String mId;
        public final Identity.PublicIdentity mPublicIdentity;
        public final Date mAddedTimestamp;
        public final Date mLastSentTimestamp;
        public final long mLastSentSequenceNumber;
        public final Date mLastReceivedTimestamp;
        public final long mLastReceivedSequenceNumber;

        public Friend(
                Identity.PublicIdentity publicIdentity,
                Date addedTimestamp,
                Date lastSentTimestamp,
                long lastSentSequenceNumber,
                Date lastReceivedTimestamp,
                long lastReceivedSequenceNumber) throws Utils.ApplicationError {
            mId = publicIdentity.getId();
            mPublicIdentity = publicIdentity;
            mAddedTimestamp = addedTimestamp;
            mLastSentSequenceNumber = lastSentSequenceNumber;
            mLastSentTimestamp = lastSentTimestamp;
            mLastReceivedTimestamp = lastReceivedTimestamp;
            mLastReceivedSequenceNumber = lastReceivedSequenceNumber;
        }
    }

    public static class Group {
        public final Protocol.Group mGroup;
        public enum State {
            // Self is publisher and group is active (not deleted, posts being added).
            PUBLISHING,

            // Self is not a publisher, and as last received group is active.
            SUBSCRIBING,

            // Self is not publisher but wants to leave group; group is not displayed in
            // UI and post updates are discarded; will notify friends and group publisher
            // of this state, publisher will update group to remove self as member, and
            // then this group's data will be completely deleted.
            RESIGNING,

            // Self is publisher and group is deleted. Will send tombstone to all members.
            // All group posts are deleted; only group tombstone is retained.
            TOMBSTONE,

            // Self is not publisher and received TOMBSTONE from publisher. Group and
            // content still visible locally until local user deletes, after which this
            // group's data will be completely deleted.
            DEAD,

            // Self is not published and removed publisher as friend. Same treatment as
            // DEAD state, except that publisher will not be in TOMBSTONE state and not
            // receive notification to remove self as a member.
            // TODO: what happens when re-add friend?
            ORPHANED
        }
        public final State mState;

        public Group(
                Protocol.Group group,
                State state) {
            mGroup = group;
            mState = state;
        }
    }

    public static class Post {
        public final Protocol.Post mPost;
        public enum State {
            UNREAD,
            READ,
            TOMBSTONE
        }
        public final State mState;

        public Post(
                Protocol.Post post,
                State state) {
            mPost = post;
            mState = state;
        }
    }

    public static class LocalResource {
        public final String mResourceId;
        public final String mGroupId;
        public enum Type {PICTURE, RAW}
        public final Type mType;
        public final String mMimeType;
        public final String mFilePath;
        public final String mTempFilePath;

        public LocalResource(
                String resourceId,
                String groupId,
                Type type,
                String mimeType,
                String filePath,
                String tempFilePath) {
            mResourceId = resourceId;
            mGroupId = groupId;
            mType = type;
            mMimeType = mimeType;
            mFilePath = filePath;
            mTempFilePath = tempFilePath;
        }
    }

    public static class Download {
        public final String mPublisherId;
        public final String mResourceId;
        public final String mMimeType;
        public final long mSize;
        public enum State {IN_PROGRESS, CANCELLED, COMPLETE}
        public final State mState;

        public Download(
                String publisherId,
                String resourceId,
                String mimeType,
                long size,
                State state) {
            mPublisherId = publisherId;
            mResourceId = resourceId;
            mMimeType = mimeType;
            mSize = size;
            mState = state;
        }
    }

    private static final int DATABASE_VERSION = 1;

    private static final String DEFAULT_DATABASE_NAME = "ploggy.db";

    private static final String[] DATABASE_SCHEMA = {

            "CREATE TABLE Self (" +
                "id TEXT PRIMARY KEY," +
                "publicIdentity TEXT NOT NULL, " +
                "privateIdentity TEXT NOT NULL, " +
                "createdTimestamp TEXT NOT NULL)",

            "CREATE TABLE Friend (" +
                "id TEXT PRIMARY KEY," +
                "publicIdentity TEXT NOT NULL, " +
                "nickname TEXT UNIQUE NOT NULL, " +
                "certificate TEXT UNIQUE NOT NULL," +
                "addedTimestamp TEXT NOT NULL," +
                "lastSentTimestamp TEXT NOT NULL," +
                "lastSentSequenceNumber INTEGER NOT NULL," +
                "lastReceivedTimestamp TEXT NOT NULL," +
                "lastReceivedSequenceNumber INTEGER NOT NULL)",
            "CREATE INDEX FriendNickname ON Friend (nickname)",
            "CREATE INDEX FriendCertificate ON Friend (certificate)",

            "CREATE TABLE Group (" +
                "id TEXT PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "publisherId TEXT NOT NULL," +
                "createdTimestamp TEXT NOT NULL," +
                "modifiedTimestamp TEXT NOT NULL," +
                "sequenceNumber INTEGER NOT NULL," +
                "state Text NOT NULL," +
                "UNIQUE(name, publisherId))",
            // *TODO* create indexes for Group queries

            "CREATE TABLE GroupMember (" +
                "groupId TEXT NOT NULL," +
                "memberId TEXT NOT NULL," +
                "memberNickname TEXT NOT NULL," +
                "memberPublicIdentity TEXT NOT NULL," +
                "PRIMARY KEY (groupId, memberId))",

            "CREATE TABLE Location (" +
                "publisherId TEXT PRIMARY KEY," +
                "timestamp TEXT NOT NULL," +
                "latitude REAL NOT NULL," +
                "longitude REAL NOT NULL," +
                "streetAddress TEXT NOT NULL)",

            "CREATE TABLE Post (" +
                "id TEXT PRIMARY KEY," +
                "publisherId TEXT NOT NULL," +
                "groupId TEXT NOT NULL," +
                "contentType TEXT NOT NULL," +
                "content TEXT NOT NULL," +
                "attachments TEXT NOT NULL," +
                "createdTimestamp TEXT NOT NULL," +
                "modifiedTimestamp TEXT NOT NULL," +
                "sequenceNumber INTEGER NOT NULL," +
                "state TEXT NOT NULL)",
            // *TODO* create indexes for Post queries (see http://www.sqlite.org/optoverview.html)

            "CREATE TABLE LocalResource (" +
                "resourceId TEXT PRIMARY KEY," +
                "groupId TEXT NOT NULL," +
                "type INTEGER NOT NULL," +
                "mimeType TEXT NOT NULL," +
                "filePath TEXT NOT NULL," +
                "tempFilePath TEXT NOT NULL)",

            "CREATE TABLE Download (" +
                "publisherId TEXT," +
                "resourceId TEXT," +
                "mimeType TEXT NOT NULL," +
                "size INTEGER NOT NULL," +
                "state INTEGER NOT NULL," +
                "PRIMARY KEY (friendId, resourceId))",
    };

    private static Map<String, Data> instances = new HashMap<String, Data>();

    public static synchronized Data getInstance() {
        return getInstance(null);
    }

    public static synchronized Data getInstance(String name) {
        if (name == null) {
            name = DEFAULT_DATABASE_NAME;
        }
        if (!instances.containsKey(name)) {
            instances.put(name, new Data(name));
        }
        return instances.get(name);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    private final SQLiteDatabase mDatabase;

    private Data(String name) {
        super(Utils.getApplicationContext(), name, null, DATABASE_VERSION);
        mDatabase = getWritableDatabase();
    }

    // *TODO* who calls SQLiteOpenHelper.close()?

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            for (String statement : DATABASE_SCHEMA) {
                db.execSQL(statement);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    @Override
    public void onConfigure (SQLiteDatabase db) {
        // Enable parallel execution of queries from multiple threads on the same database
        // see: http://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html#enableWriteAheadLogging%28%29
        db.enableWriteAheadLogging();
    }

    public void putSelf(Self self) throws Utils.ApplicationError {
        try {
            mDatabase.beginTransactionNonExclusive();
            // Only one Self row
            // *TODO* should delete all groups/posts on change self identity?
            mDatabase.delete("Self", null, null);
            ContentValues values = new ContentValues();
            values.put("id", self.mId);
            values.put("publicIdentity", Json.toJson(self.mPublicIdentity));
            values.put("privateIdentity", Json.toJson(self.mPublicIdentity));
            values.put("createdTimestamp", dateToString(self.mCreatedTimestamp));
            mDatabase.insertOrThrow("Self", null, values);
            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
    }

    public Self getSelf() throws Utils.ApplicationError, NotFoundError {
        return getObject(
            "SELECT publicIdentity, privateIdentity, createdTimestamp FROM Self",
            null,
            new IRowToObject<Self>() {
                @Override
                public Self rowToObject(SQLiteDatabase database, Cursor cursor) throws Utils.ApplicationError {
                    return new Self(
                            Json.fromJson(cursor.getString(0), Identity.PublicIdentity.class),
                            Json.fromJson(cursor.getString(1), Identity.PrivateIdentity.class),
                            stringToDate(cursor.getString(2)));
                }
            });
    }

    private String getSelfId() throws Utils.ApplicationError {
        try {
            return getStringColumn("SELECT id FROM Self", null);
        } catch (NotFoundError e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    public void putFriend(Friend friend) throws Utils.ApplicationError {
        try {
            mDatabase.beginTransactionNonExclusive();
            ContentValues values = new ContentValues();
            values.put("id", friend.mId);
            values.put("publicIdentity", Json.toJson(friend.mPublicIdentity));
            values.put("nickname", friend.mPublicIdentity.mNickname);
            values.put("certificate", friend.mPublicIdentity.mX509Certificate);
            values.put("addedTimestamp", dateToString(friend.mAddedTimestamp));
            values.put("lastSentTimestamp", dateToString(friend.mLastSentTimestamp));
            values.put("lastSentSequenceNumber", friend.mLastSentSequenceNumber);
            values.put("lastReceivedTimestamp", dateToString(friend.mLastReceivedTimestamp));
            values.put("lastReceivedSequenceNumber", friend.mLastReceivedSequenceNumber);
            mDatabase.replaceOrThrow("Friend", null, values);
            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
    }

    public void updateFriendSentState(String friendId, Date lastSentTimestamp, long lastSentSequenceNumber)
            throws Utils.ApplicationError, NotFoundError {
        try {
            mDatabase.beginTransactionNonExclusive();
            ContentValues values = new ContentValues();
            values.put("lastSentTimestamp", dateToString(lastSentTimestamp));
            values.put("lastSentSequenceNumber", lastSentSequenceNumber);
            if (1 != mDatabase.update("Friend", values, "id = ?", new String[]{friendId})) {
                throw new Utils.ApplicationError(LOG_TAG, "update friend failed");
            }
            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
    }

    public void removeFriend(String friendId) throws Utils.ApplicationError {
        try {
            mDatabase.beginTransactionNonExclusive();
            mDatabase.delete("Friend", "id = ?", new String[]{friendId});
            mDatabase.delete("Location", "publisherId = ?", new String[]{friendId});

            // Note: keeping all friend's posts

            // Treat friend's groups as ORPHANED
            ContentValues values = new ContentValues();
            values.put("state", Group.State.ORPHANED.name());
            mDatabase.update("Group", values, "publisherId = ?", new String[]{friendId});

            // Remove friend as member from published groups
            // *TODO* filter by group state? e.g., do or don't update if TOMBSTONE?
            String modifiedTimestamp = dateToString(new Date());
            mDatabase.execSQL(
                    "UPDATE Group " +
                        "SET sequenceNumber = sequenceNumber + 1, modifiedTimestamp = ? " +
                        "WHERE publisherId = (SELECT id FROM Self) AND " + 
                            "Group.id IN (SELECT groupId FROM GroupMember WHERE memberId = ?)",
                     new String[]{modifiedTimestamp, friendId});
            mDatabase.delete(
                    "GroupMember",
                    "id IN (SELECT id FROM Group WHERE publisherId IN (SELECT id FROM Self)) AND memberId = ?",
                    new String[]{friendId});

            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
    }

    private static final String SELECT_FRIEND =
            "SELECT publicIdentity, addedTimestamp, lastSentTimestamp, lastSentSequenceNumber, " +
                    "lastReceivedTimestamp, lastReceivedSequenceNumber FROM Friend";

    private static final IRowToObject<Friend> mRowToFriend =
        new IRowToObject<Friend>() {
            @Override
            public Friend rowToObject(SQLiteDatabase database, Cursor cursor) throws Utils.ApplicationError {
                return new Friend(
                        Json.fromJson(cursor.getString(0), Identity.PublicIdentity.class), stringToDate(cursor.getString(1)),
                        stringToDate(cursor.getString(2)), cursor.getLong(3), stringToDate(cursor.getString(4)), cursor.getLong(5));
            }
        };

    public Friend getFriendById(String friendId) throws Utils.ApplicationError, NotFoundError {
        return getObject(SELECT_FRIEND + " WHERE id = ?", new String[]{friendId}, mRowToFriend);
    }

    public Friend getFriendByNickname(String nickname) throws Utils.ApplicationError, NotFoundError {
        return getObject(SELECT_FRIEND + " WHERE nickname = ?", new String[]{nickname}, mRowToFriend);
    }

    public Friend getFriendByCertificate(String certificate) throws Utils.ApplicationError, NotFoundError {
        return getObject(SELECT_FRIEND + " WHERE certificate = ?", new String[]{certificate}, mRowToFriend);
    }

    public CursorIterator<Friend> getFriends() throws Utils.ApplicationError {
        return getObjectCursor(SELECT_FRIEND, null, mRowToFriend);
    }

    private static final IRowToObject<CandidateFriend> mRowToCandidateFriend =
        new IRowToObject<CandidateFriend>() {
            @Override
            public CandidateFriend rowToObject(SQLiteDatabase database, Cursor cursor) throws Utils.ApplicationError {
                String memberId = cursor.getString(0);
                Identity.PublicIdentity memberPublicIdentity = Json.fromJson(cursor.getString(1), Identity.PublicIdentity.class);
                List<String> groupIds = new ArrayList<String>();
                Cursor groupCursor = null;
                // TODO: can we avoid this additional-query-per-row "anti-pattern"?
                try {
                    String query =
                        "SELECT groupId FROM GroupMember WHERE memberId = ?";
                    groupCursor = database.rawQuery(query, new String[]{memberId});
                    groupCursor.moveToFirst();
                    while (!groupCursor.isAfterLast()) {
                        groupIds.add(groupCursor.getString(0));
                        groupCursor.moveToNext();
                    }
                } catch (SQLiteException e) {
                    throw new Utils.ApplicationError(LOG_TAG, e);
                } finally {
                    if (groupCursor != null) {
                        groupCursor.close();
                    }
                }
                return new CandidateFriend(memberPublicIdentity, groupIds);
            }
        };

    public CursorIterator<CandidateFriend> getCandidateFriends() throws Utils.ApplicationError {
        return getObjectCursor(
                "SELECT DISTINCT memberId, memberPublicIdentity FROM GroupMember " +
                    "WHERE memberId NOT IN (SELECT id FROM Friend) " +
                    "ORDER BY memberNickname ASC, memberId ASC",
                null,
                mRowToCandidateFriend);
    }

    public void putGroup(Protocol.Group group) throws Utils.ApplicationError {
        // *TODO* where is best to generate IDs and set timestamps for groups? caller?
        // *TODO* where validate group: no duplicate members, etc.?
        try {
            mDatabase.beginTransactionNonExclusive();
            if (!group.mPublisherId.equals(getSelfId())) {
                throw new Utils.ApplicationError(LOG_TAG, "overwriting group not published by self");
            }
            try {
                // *TODO* check Group.publisherId == Self
                if (!Group.State.PUBLISHING.name().equals(
                        getStringColumn("SELECT state FROM Group WHERE id = ?", new String[]{group.mId}))) {
                    throw new Utils.ApplicationError(LOG_TAG, "overwriting group not in publishing state");
                }
            } catch (NotFoundError e) {                
            }
            long newSequenceNumber = 1;
            try {
                newSequenceNumber =
                        1 + getLongColumn("SELECT sequenceNumber FROM Group WHERE groupId = ?", new String[]{group.mId});
            } catch (NotFoundError e) {
            }
            replaceGroup(group, newSequenceNumber, Group.State.PUBLISHING);
            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
    }
    
    private void replaceGroup(Protocol.Group group, long sequenceNumber, Group.State state)
            throws Utils.ApplicationError , SQLiteException {
        // Helper used by putGroup and putPullResponse; assumes already in transaction
        mDatabase.delete("GroupMember", "groupId = ?", new String[]{group.mId});
        ContentValues values = new ContentValues();
        values.put("id", group.mId);
        values.put("name", group.mName);
        values.put("publisherId", group.mPublisherId);
        values.put("createdTimestamp", dateToString(group.mCreatedTimestamp));
        values.put("modifiedTimestamp", dateToString(group.mModifiedTimestamp));
        values.put("sequenceNumber", sequenceNumber);
        values.put("state", state.name());
        mDatabase.replaceOrThrow("Group", null, values);
        for (Identity.PublicIdentity memberPublicIdentity : group.mMembers) {
            ContentValues memberValues = new ContentValues();
            memberValues.put("groupId", group.mId);
            memberValues.put("memberId", memberPublicIdentity.getId());
            memberValues.put("memberNickname", memberPublicIdentity.mNickname);
            memberValues.put("memberPublicIdentity", Json.toJson(memberPublicIdentity));
            mDatabase.insertOrThrow("GroupMember", null, memberValues);
        }        
    }

    public void removeGroup(String groupId) throws Utils.ApplicationError {
        try {
            mDatabase.beginTransactionNonExclusive();
            Group group = getGroup(groupId);
            Group.State newState = null;
            switch (group.mState) {
            case PUBLISHING:
                newState = Group.State.TOMBSTONE;
                mDatabase.delete("Post", "groupId = ?", new String[]{groupId});
                break;
            case SUBSCRIBING:
                newState = Group.State.RESIGNING;
                mDatabase.delete("Post", "groupId = ?", new String[]{groupId});
                break;
            case RESIGNING:
                // Nothing to change. Keeping Group as a placeholder for state.
                break;
            case TOMBSTONE:
                // Nothing to change. Keeping Group as a placeholder for state.
                // TODO: could completely delete if know that all members have received tombstone?
                return;
            case ORPHANED:
            case DEAD:
                deleteGroup(groupId);
                break;
            }
            if (newState != null) {
                ContentValues values = new ContentValues();
                if (group.mGroup.mPublisherId.equals(getSelfId())) {
                    long newSequenceNumber =  group.mGroup.mSequenceNumber + 1;
                    values.put("sequenceNumber", newSequenceNumber);
                }
                values.put("state", newState.name());
                if (1 != mDatabase.update("Group", values, "id = ?", new String[]{groupId})) {
                    throw new Utils.ApplicationError(LOG_TAG, "update group state failed");
                }
            }
            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (NotFoundError e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
    }
    
    private void deleteGroup(String groupId) throws Utils.ApplicationError {
        // Helper used by removeGroup and putPullResponse; assumes already in transaction
        mDatabase.delete("Group", "groupId = ?", new String[]{groupId});
        mDatabase.delete("GroupMember", "groupId = ?", new String[]{groupId});
        mDatabase.delete("Post", "groupId = ?", new String[]{groupId});
    }

    private static final String SELECT_GROUP =
            "SELECT id, name, publisherId, createdTimestamp, modifiedTimestamp, sequenceNumber, state FROM Group";

    private static final IRowToObject<Group> mRowToGroup =
        new IRowToObject<Group>() {
            @Override
            public Group rowToObject(SQLiteDatabase database, Cursor cursor) throws Utils.ApplicationError {
                String groupId = cursor.getString(0);
                List<Identity.PublicIdentity> members = new ArrayList<Identity.PublicIdentity>();
                Cursor memberCursor = null;
                try {
                    String query =
                        "SELECT memberPublicIdentity FROM GroupMember " +
                            "WHERE groupId = ? " +
                            "ORDER BY memberNickname ASC, memberId ASC";
                    memberCursor = database.rawQuery(query, new String[]{groupId});
                    memberCursor.moveToFirst();
                    while (!memberCursor.isAfterLast()) {
                        members.add(Json.fromJson(memberCursor.getString(0), Identity.PublicIdentity.class));
                        memberCursor.moveToNext();
                    }
                } catch (SQLiteException e) {
                    throw new Utils.ApplicationError(LOG_TAG, e);
                } finally {
                    if (memberCursor != null) {
                        memberCursor.close();
                    }
                }
                Group.State state = Group.State.valueOf(cursor.getString(6));
                return new Group(
                        new Protocol.Group(
                            groupId,
                            cursor.getString(1),
                            cursor.getString(2),
                            members,
                            stringToDate(cursor.getString(3)),
                            stringToDate(cursor.getString(4)),
                            cursor.getLong(5),
                            (state == Group.State.TOMBSTONE)),
                        state);
            }
        };

    public Group getGroup(String groupId) throws Utils.ApplicationError, NotFoundError {
        return getObject(SELECT_GROUP + " WHERE id = ?", new String[]{groupId}, mRowToGroup);
    }

    public CursorIterator<Group> getVisibleGroups() throws Utils.ApplicationError {
        return getObjectCursor(
                SELECT_GROUP + " WHERE state IN (?, ?, ?)",
                new String[]{Group.State.PUBLISHING.name(), Group.State.SUBSCRIBING.name(), Group.State.ORPHANED.name()},
                mRowToGroup);
    }

    public CursorIterator<Group> getHiddenGroups() throws Utils.ApplicationError {
        return getObjectCursor(
                SELECT_GROUP + " WHERE state IN (?, ?)",
                new String[]{Group.State.RESIGNING.name(), Group.State.TOMBSTONE.name()},
                mRowToGroup);
    }

    public void putSelfLocation(Protocol.Location location)
            throws Utils.ApplicationError {
        putLocation(getSelfId(), location);
    }

    public void putFriendLocation(String friendId, Protocol.Location location)
            throws Utils.ApplicationError {
        Protocol.validateLocation(location);
        putLocation(friendId, location);
    }

    private void putLocation(String publisherId, Protocol.Location location)
            throws Utils.ApplicationError {
        try {
            mDatabase.beginTransactionNonExclusive();
            ContentValues values = new ContentValues();
            values.put("publisherId", publisherId);
            values.put("timestamp", dateToString(location.mTimestamp));
            values.put("latitude", location.mLatitude);
            values.put("longitude", location.mLongitude);
            values.put("streetAddress", location.mStreetAddress);
            mDatabase.replaceOrThrow("Location", null, values);
            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
    }

    private static final String SELECT_LOCATION =
            "SELECT timestamp, latitude, longitude, streetAddress FROM Location";

    private static final IRowToObject<Protocol.Location> mRowToLocation =
        new IRowToObject<Protocol.Location>() {
            @Override
            public Protocol.Location rowToObject(SQLiteDatabase database, Cursor cursor) throws Utils.ApplicationError {
                return new Protocol.Location(
                        stringToDate(cursor.getString(0)), cursor.getDouble(1), cursor.getDouble(2), cursor.getString(3));
            }
        };

    public Protocol.Location getSelfLocation() throws Utils.ApplicationError, NotFoundError {
        return getObject(
                SELECT_LOCATION + " WHERE publisherId = (SELECT id FROM Self)", null, mRowToLocation);
    }

    public Protocol.Location getFriendLocation(String friendId) throws Utils.ApplicationError, NotFoundError {
        return getObject(
                SELECT_LOCATION + " WHERE publisherId = ?", new String[]{friendId}, mRowToLocation);
    }
    
    public void addPost(Protocol.Post post, List<LocalResource> attachmentLocalResources)
            throws Utils.ApplicationError {
        try {
            mDatabase.beginTransactionNonExclusive();
            try {
                Group.State groupState =
                        Group.State.valueOf(getStringColumn("SELECT state FROM Group WHERE id = ?", new String[]{post.mGroupId}));
                if (groupState != Group.State.PUBLISHING && groupState != Group.State.SUBSCRIBING) {
                    throw new Utils.ApplicationError(LOG_TAG, "invalid group state for post");
                }
            } catch (NotFoundError e) {
                throw new Utils.ApplicationError(LOG_TAG, "group not found for post");
            }
            long newSequenceNumber = 1;
            try {
                newSequenceNumber =
                    1 + getLongColumn(
                        "SELECT MAX(sequenceNumber) FROM Post WHERE publisherId = (SELECT id FROM Self) AND groupId = ?",
                        new String[]{post.mGroupId});
            } catch (NotFoundError e) {
            }
            ContentValues values = new ContentValues();
            values.put("id", post.mId);
            values.put("publisherId", getSelfId());
            values.put("groupId", post.mGroupId);
            values.put("contentType", post.mContentType);
            values.put("content", post.mContent);
            values.put("attachments", Json.toJson(post.mAttachments));
            values.put("createdTimestamp", dateToString(post.mCreatedTimestamp));
            values.put("modifiedTimestamp", dateToString(post.mModifiedTimestamp));
            values.put("sequenceNumber", newSequenceNumber);
            values.put("state", Post.State.READ.name());
            mDatabase.insertOrThrow("Post", null, values);
            int attachmentIndex = 0;
            for (LocalResource localResource : attachmentLocalResources) {
                if (!post.mAttachments.get(attachmentIndex).mId.equals(localResource.mResourceId)) {
                    throw new Utils.ApplicationError(LOG_TAG, "invalid local resource id");
                }
                if (!post.mGroupId.equals(localResource.mGroupId)) {
                    throw new Utils.ApplicationError(LOG_TAG, "invalid local resource group");
                }
                ContentValues localResourceValues = new ContentValues();
                localResourceValues.put("resourceId", localResource.mResourceId);
                localResourceValues.put("groupId", localResource.mGroupId);
                localResourceValues.put("type", localResource.mType.name());
                localResourceValues.put("mimeType", localResource.mMimeType);
                localResourceValues.put("filePath", localResource.mFilePath);
                localResourceValues.put("tempFilePath", localResource.mTempFilePath);
                mDatabase.insertOrThrow("LocalResource", null, localResourceValues);
                attachmentIndex++;
            }
            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
    }

    public void removePost(String postId) throws Utils.ApplicationError {
        try {
            mDatabase.beginTransactionNonExclusive();
            // Only publisher can remove post (unlike removeGroup)
            if (1 != getCount(
                    "SELECT COUNT(*) FROM Post WHERE id = ? AND publisherId = (SELECT id FROM Self)",
                    new String[]{postId})) {
                throw new Utils.ApplicationError(LOG_TAG, "removing unpublished post");
            }
            // *TODO* check post's group's state?
            long newSequenceNumber = 1;
            try {
                newSequenceNumber =
                    1 + getLongColumn(
                        "SELECT MAX(sequenceNumber) FROM Post " +
                            "WHERE publisherId = (SELECT id FROM Self) " +
                            "AND groupId = (SELECT groupId FROM Post WHERE Post.id = ?)",
                        new String[]{postId});
            } catch (NotFoundError e) {
                throw new Utils.ApplicationError(LOG_TAG, "unexpected sequence number not found");
            }
            ContentValues values = new ContentValues();
            values.put("sequenceNumber", newSequenceNumber);
            values.put("state", Post.State.TOMBSTONE.name());
            // Only updates if state is not already TOMBSTONE
            mDatabase.update(
                    "Post",
                    values,
                    "id = ? AND state <> ?",
                    new String[]{postId, Post.State.TOMBSTONE.name()});
            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
    }

    public void markAsReadPosts(List<String> postIds) throws Utils.ApplicationError {
        try {
            mDatabase.beginTransactionNonExclusive();
            for (String postId : postIds) {
                ContentValues values = new ContentValues();
                values.put("state", Post.State.READ.name());
                // Only updates when state is UNREAD
                mDatabase.update(
                        "Post",
                        values,
                        "id = ? AND state = ?",
                        new String[]{postId, Post.State.UNREAD.name()});
            }
            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
    }

    private static final String SELECT_POST =
            "SELECT id, publisherId, groupId, contentType, content, attachments, " +
                    "createdTimestamp, modifiedTimestamp, sequenceNumber, state FROM Post";

    private static final IRowToObject<Post> mRowToPost =
        new IRowToObject<Post>() {
            @Override
            public Post rowToObject(SQLiteDatabase database, Cursor cursor) throws Utils.ApplicationError {
                Post.State state = Post.State.valueOf(cursor.getString(9));
                return new Post(
                        new Protocol.Post(
                            cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4),
                            new ArrayList<Protocol.Resource>(Arrays.asList(Json.fromJson(cursor.getString(5), Protocol.Resource[].class))),
                            stringToDate(cursor.getString(6)), stringToDate(cursor.getString(7)), cursor.getLong(8),
                            (state == Post.State.TOMBSTONE)),
                        state);
            }
        };

    public CursorIterator<Post> getUnreadPosts() throws Utils.ApplicationError {
        return getObjectCursor(
                SELECT_POST +
                    " WHERE contentType = ? AND unread = 1" +
                    " ORDER BY modifiedTimestamp DESC",
                new String[]{Protocol.POST_CONTENT_TYPE_DEFAULT},
                mRowToPost);
    }

    public CursorIterator<Post> getPosts(String groupId) throws Utils.ApplicationError {
        return getObjectCursor(
                SELECT_POST +
                    " WHERE contentType = ? AND groupId = ?" +
                    " ORDER BY modifiedTimestamp DESC",
                new String[]{Protocol.POST_CONTENT_TYPE_DEFAULT, groupId},
                mRowToPost);
    }

    public CursorIterator<Post> getPostsForUnreceivedGroups() throws Utils.ApplicationError {
        return getObjectCursor(
                SELECT_POST +
                    " WHERE contentType = ? AND groupId NOT IN (SELECT id FROM Group)" +
                    " ORDER BY modifiedTimestamp DESC",
                new String[]{Protocol.POST_CONTENT_TYPE_DEFAULT},
                mRowToPost);
    }

    public Protocol.PullRequest getPullRequest(String friendId, boolean requestPullBack)
            throws Utils.ApplicationError, NotFoundError {
        Map<String, Protocol.SequenceNumbers> groupLastReceivedSequenceNumbers =
                new LinkedHashMap<String, Protocol.SequenceNumbers>();
        List<String> groupsToResignMembership = new ArrayList<String>();
        Cursor cursor = null;
        try {
            mDatabase.beginTransactionNonExclusive();
            // Group state cases:
            // TOMBSTONE - don't pull 
            // DEAD - don't pull because: no one is publishing; pull list would never shrink; reveals that group isn't yet locally deleted.
            // PUBLISHING/SUBSCRIBING - want group changes and posts
            // RESIGNING/ORPHANED - want to signal resigning status so publisher removes user and others don't send posts
            // TODO: how are ORPHANED groups cleaned up? If not friends with publisher, won't ever get removed as member?
            // TODO: don't need subquery when state is RESIGNING/ORPHANED?
            String query =
                "SELECT Group.id, Group.sequenceNumber, Group.state, " +
                        "(SELECT MAX(sequenceNumber) FROM Post WHERE Post.groupId = Group.groupId and publisherId = ?) " +
                    "FROM Group " + 
                    "WHERE ? IN (SELECT GroupMember.memberId FROM GroupMember WHERE GroupMember.groupId = Group.id) " +
                        "AND Group.state IN (?, ?, ?, ?)" +
                    "ORDER BY id ASC";
            cursor = mDatabase.rawQuery(
                    query,
                    new String[]{
                            friendId, friendId,
                            Group.State.PUBLISHING.name(), Group.State.SUBSCRIBING.name(),
                            Group.State.RESIGNING.name(), Group.State.ORPHANED.name()});
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                String id = cursor.getString(0);
                long groupSequenceNumber = cursor.getLong(1);
                Group.State state = Group.State.valueOf(cursor.getString(2));
                long postSequenceNumber = cursor.getLong(3);
                switch (state) {
                case PUBLISHING:
                case SUBSCRIBING:
                    groupLastReceivedSequenceNumbers.put(
                            id,
                            new Protocol.SequenceNumbers(groupSequenceNumber, postSequenceNumber));
                    break;
                case RESIGNING:
                case ORPHANED:
                    // In the ORPHANED case, tell the friend we "resigned" from the group -- the friend
                    // may still be friends with the publisher and the group may still be active.
                    groupsToResignMembership.add(id);
                    break;
                case TOMBSTONE:
                case DEAD:
                    // Don't pull
                    break;
                }
                cursor.moveToNext();
            }
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            mDatabase.endTransaction();
        }
        return new Protocol.PullRequest(groupLastReceivedSequenceNumbers, groupsToResignMembership, requestPullBack);
    }

    public class PullResponseIterator implements Iterable<String>, Iterator<String> {

        private final Map<String, Protocol.SequenceNumbers> mGroupsToSend;
        private final Iterator<Map.Entry<String, Protocol.SequenceNumbers>> mGroupsToSendIterator;
        private CursorIterator<Post> mPostIterator;
        private String mNext;

        public PullResponseIterator(String friendId, Protocol.PullRequest pullRequest)
                throws Utils.ApplicationError {
            mGroupsToSend = getGroupsToSend(friendId, pullRequest);
            mGroupsToSendIterator = mGroupsToSend.entrySet().iterator();
            mPostIterator = null;
            mNext = getNext();
        }

        @Override
        public Iterator<String> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            return mNext != null;
        }

        @Override
        public String next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String result = mNext;
            mNext = getNext();
            return result;
        }

        private String getNext() {
            try {
                if (mPostIterator != null && mPostIterator.hasNext()) {
                    return Json.toJson(mPostIterator.next());
                } else {
                    // Loop through PullRequest until there's something to send: either a group or a post
                    while (mGroupsToSendIterator.hasNext()) {
                        Map.Entry<String, Protocol.SequenceNumbers> groupToSend = mGroupsToSendIterator.next();
                        String groupId = groupToSend.getKey();
                        Protocol.SequenceNumbers lastReceivedSequenceNumbers = groupToSend.getValue();
                        // TODO: re-check membership/group state?
                        Group group = getGroup(groupId);
                        mPostIterator = getPosts(groupId, lastReceivedSequenceNumbers.mPostSequenceNumber);
                        // Only the publisher sends group updates
                        if (group.mGroup.mPublisherId.equals(getSelfId()) &&
                                group.mGroup.mSequenceNumber < lastReceivedSequenceNumbers.mGroupSequenceNumber) {
                            return Json.toJson(group.mGroup);
                        } else if (mPostIterator.hasNext()) {
                            return Json.toJson(mPostIterator.next().mPost);
                        }
                        // Else, we didn't have anything to send for this group, so loop around to the next one
                    }
                }
            } catch (NotFoundError e) {
                Log.addEntry(LOG_TAG, "push iterator failed with item not found");
            } catch (Utils.ApplicationError e) {
                Log.addEntry(LOG_TAG, "push iterator failed");
            }
            return null;
        }

        private Map<String, Protocol.SequenceNumbers> getGroupsToSend(String friendId, Protocol.PullRequest pullRequest)
                throws Utils.ApplicationError {
            Map<String, Protocol.SequenceNumbers> groupsToSend =
                    new LinkedHashMap<String, Protocol.SequenceNumbers>();
            Cursor cursor = null;
            try {
                mDatabase.beginTransactionNonExclusive();
                // Requested groups that are not found locally (or found but friend is not a member) are silently ignored
                // Pull response is in order of pullRequest, and then by group name for groups the friend doesn't know about
                // TODO: join instead of subquery?
                String query =
                    "SELECT id, state FROM Group WHERE ? IN (SELECT memberId FROM GroupMember WHERE groupId = Group.id)";
                cursor = mDatabase.rawQuery(query, new String[]{friendId});
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    // Here we check group ACL and group state; sequence numbers are checked in getNext()
                    String groupId = cursor.getString(0);
                    Group.State state = Group.State.valueOf(cursor.getString(1));
                    switch (state) {
                    case PUBLISHING:
                    case SUBSCRIBING:
                    case TOMBSTONE:
                    case DEAD:
                        // Add the group to the "send" list
                        // In local TOMBSTONE/DEAD state, we send the group AND posts so receiver can see all posts while DEAD
                        if (pullRequest.mGroupLastReceivedSequenceNumbers.containsKey(groupId)) {
                            // Case: the friend requested the group
                            groupsToSend.put(groupId, pullRequest.mGroupLastReceivedSequenceNumbers.get(groupId));
                        } else if (!pullRequest.mGroupsToResignMembership.contains(groupId)) {
                            // Case: the friend didn't request the group and isn't resigning -- so the friend hasn't
                            // received it from the publisher
                            groupsToSend.put(groupId, new Protocol.SequenceNumbers(0,  0));
                        }
                        break;
                    case RESIGNING:
                    case ORPHANED:
                        // Ignore the group
                        break;
                    }
                    cursor.moveToNext();
                }
            } catch (SQLiteException e) {
                throw new Utils.ApplicationError(LOG_TAG, e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                mDatabase.endTransaction();
            }
            return groupsToSend;
        }
        
        private CursorIterator<Post> getPosts(String groupId, long afterSequenceNumber)
                throws Utils.ApplicationError {
            return getObjectCursor(
                    SELECT_POST +
                        " WHERE publisherId = (SELECT id FROM Self) AND groupId = ? AND sequenceNumber > ?" +
                        " ORDER BY sequenceNumber ASC",
                    new String[]{groupId, Long.toString(afterSequenceNumber)},
                    mRowToPost);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        public void close() {
            if (mPostIterator != null) {
                mPostIterator.close();
                mPostIterator = null;
            }
        }
    }

    public PullResponseIterator putPullRequest(String friendId, Protocol.PullRequest pullRequest)
            throws Utils.ApplicationError {
        // If publisher of group that friend wants to be removed from, do that now, before
        // sending pull data.
        // TODO: don't start a transaction if not publisher of any groups in groupsToResignMembership?
        Protocol.validatePullRequest(pullRequest);
        if (pullRequest.mGroupsToResignMembership.size() > 0) {
            try {
                mDatabase.beginTransactionNonExclusive();
                String modifiedTimestamp = dateToString(new Date());
                for (String groupId : pullRequest.mGroupsToResignMembership) {
                    // Silently fails when friend isn't a member, or self isn't the publisher
                    // *TODO* filter by group state? e.g., do or don't update if TOMBSTONE?
                    mDatabase.execSQL(
                            "UPDATE Group " +
                                "SET sequenceNumber = sequenceNumber + 1, modifiedTimestamp = ? " +
                                "WHERE id = ? AND publisherId = (SELECT id FROM Self) " +
                                "AND ? IN (SELECT memberId FROM GroupMember WHERE groupId = Group.id)",
                             new String[]{modifiedTimestamp, groupId, friendId});
                    mDatabase.delete(
                            "GroupMember",
                            "groupId = ? AND memberId = ? " +
                                "AND groupId IN (SELECT id FROM Group WHERE publisherId = (SELECT id FROM Self))",
                            new String[]{groupId, friendId});
                }
                mDatabase.setTransactionSuccessful();
            } catch (SQLiteException e) {
                throw new Utils.ApplicationError(LOG_TAG, e);
            } finally {
                mDatabase.endTransaction();
            }
        }
        // *TODO* this is another transaction?
        // *TODO* this iterator, used with blocking network I/O, blocks the SQLite checkpointer? 
        return new PullResponseIterator(friendId, pullRequest);
    }

    public void putPullResponse(String friendId, Protocol.PullRequest pullRequest, Protocol.Group pulledGroup, List<Protocol.Post> pulledPosts)
            throws Utils.ApplicationError {
        // NOTE: writing would be fastest if the entire "push" PUT request stream were written in one transaction,
        // but that would block other database access. As a compromise, we support writing chunks of objects. This
        // has the benefit of writing a few objects per transaction, plus advancing the last received sequence number
        // even if the PUT request is somehow abnormally terminated.
        //
        // In the protocol, the stream of pulled objects should look like this:
        // [GroupX,] Post-in-GroupX, Post-in-GroupX, ..., [GroupY,] Post-in-GroupY, Post-in-GroupY
        // So this handler is to be called once each time the Group changes, if there are Group objects
        //
        // IMPORTANT: Only pass in the "pullRequest" with the 1st chunk of objects -- it's used to delete
        // groups in the RESIGNING state once it's known that the group publishing friend has learned of
        // this state.
        try {
            mDatabase.beginTransactionNonExclusive();
            
            if (pullRequest != null) {
                // The request was successful, so we can assume groupsToResignMembership were accepted by the
                // friend and if the friend was the group publisher we are now removed as a group member. So
                // fully delete the group in this case.
                for (String groupId : pullRequest.mGroupsToResignMembership) {
                    if (1 == getCount(
                            "SELECT COUNT(*) FROM Group WHERE id = ? AND publisherId = ?",
                            new String[]{groupId, friendId})) {
                        deleteGroup(groupId);
                    }
                }
            }
            
            if (pulledGroup != null) {
                putReceivedGroup(friendId, pulledGroup);
            }
            
            String expectedGroupId = (pulledGroup != null ? pulledGroup.mId : null);
            
            for (Protocol.Post post : pulledPosts) {
                putReceivedPost(friendId, post, expectedGroupId);
            }

            updateFriendLastReceivedTime(friendId);

            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
    }
    
    public void putPushedGroup(String friendId, Protocol.Group group) throws Utils.ApplicationError {
        // Unlike putPushedObject, there's no pull trigger since all we would
        // know is we missed an older version of the group object itself.
        try {
            mDatabase.beginTransactionNonExclusive();
            putReceivedGroup(friendId, group);
            updateFriendLastReceivedTime(friendId);
            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
    }
    
    public boolean putPushedPost(String friendId, Protocol.Post post) throws Utils.ApplicationError {
        boolean triggerPull = false;
        try {
            mDatabase.beginTransactionNonExclusive();
            // Trigger a pull from this friend if we *may* have missed an update
            if (0 == getCount(
                    "SELECT COUNT(*) FROM Post WHERE id = ? AND publisherId = ? AND groupId = ? AND sequenceNumber >= ?",
                    new String[]{post.mId, post.mPublisherId, post.mGroupId, Long.toString(post.mSequenceNumber - 1)})) {
                Log.addEntry(LOG_TAG, "pull triggered by push sequence number");
                triggerPull = true;
            }
            putReceivedPost(friendId, post, null);
            updateFriendLastReceivedTime(friendId);
            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
        return triggerPull;
    }

    private void putReceivedGroup(String friendId, Protocol.Group group)
            throws Utils.ApplicationError, SQLiteException {
        // Helper used by putPullResponse and putPushedGroup; assumes in transaction
        Protocol.validateGroup(group);
        long previousSequenceNumber = 0;
        Group.State previousState = Group.State.SUBSCRIBING;
        try {
            String[] columns = getRow(
                    "SELECT publisherId, sequenceNumber, state, FROM Group WHERE id = ?",
                    new String[]{group.mId});
            String publisherId = columns[0];
            previousSequenceNumber = Long.parseLong(columns[1]);
            previousState = Group.State.valueOf(columns[2]);
            // Check that friend is publisher
            if (!publisherId.equals(group.mPublisherId) ||
                    !publisherId.equals(friendId)) {
                throw new Utils.ApplicationError(LOG_TAG, "invalid group publisher");                        
            }
        } catch (NotFoundError e) {
            // This is a new group
        }
        
        if (previousSequenceNumber >= group.mSequenceNumber) {
            // Discard stale updates
            Log.addEntry(LOG_TAG, "received stale group update");
        } else {
            // Accept update
        
            // State change when publisher has deleted the group
            Group.State newState = Group.State.SUBSCRIBING;
            if (group.mIsTombstone) {
                newState = Group.State.DEAD;
            }

            switch (previousState) {
            case PUBLISHING:
            case TOMBSTONE:
            case DEAD:
            case RESIGNING:
                Log.addEntry(LOG_TAG, "received unexpected group state");
                // Not expecting an update in these cases - discard
                break;
            case SUBSCRIBING:
            case ORPHANED:
                // In ORPHANED case, friend was re-added
                replaceGroup(group, group.mSequenceNumber, newState);
                break;
            }
        }
    }
    
    private void putReceivedPost(String friendId, Protocol.Post post, String expectedGroupId) 
            throws Utils.ApplicationError, SQLiteException {
        // Helper used by putPullResponse and putPushedGroup; assumes in transaction
        Protocol.validatePost(post);
        if (0 != getCount(
                "SELECT COUNT(*) FROM Post WHERE id = ? AND publisherId <> ?",
                new String[]{post.mId, friendId})) {
            throw new Utils.ApplicationError(LOG_TAG, "invalid post publisher");
        }
        if (!post.mPublisherId.equals(friendId)) {
            throw new Utils.ApplicationError(LOG_TAG, "mismatched post publisher");
        }
        if (expectedGroupId != null && !post.mGroupId.equals(expectedGroupId)) {
            throw new Utils.ApplicationError(LOG_TAG, "mismatched post group id");
        }
        // Check group state as well as post publisher's membership
        try {
            Group.State state = Group.State.valueOf(
                getStringColumn(
                        "SELECT state FROM Group WHERE id = ? AND " +
                            "? IN (SELECT memberId FROM GroupMember WHERE groupId = Group.id)",
                        new String[]{post.mGroupId, friendId}));
            // Some cases where friend will send posts to be discarded:
            // When own state is TOMBSTONE, the friend may still be in SUBSCRIBING state
            // (has not yet received TOMBSTONE state) and will send posts.
            // When own state is RESIGNING because we deleted the publisher but the sending
            // friend did not.
            // Since DEAD and ORPHANED states still display groups, we'll take the posts.
            if (state == Group.State.TOMBSTONE || state == Group.State.RESIGNING){
                // Discard the post
                return;
            }
        } catch (NotFoundError e) {
            throw new Utils.ApplicationError(LOG_TAG, "invalid post from non-group member");                                        
        }
        if (0 != getCount(
                "SELECT COUNT(*) FROM Post WHERE id = ? AND publisherId = ? AND groupId = ? AND sequenceNumber >= ?",
                new String[]{post.mId, post.mPublisherId, post.mGroupId, Long.toString(post.mSequenceNumber)})) {
            Log.addEntry(LOG_TAG, "received stale post update");
            // Discard stale update
            return;
        }
        // Accept update
        if (post.mIsTombstone) {
            // Delete the object when publisher sends a tombstone
            mDatabase.delete("Post", "id = ?", new String[]{post.mId});                    
        } else {
            ContentValues values = new ContentValues();
            values.put("id", post.mId);
            values.put("publisherId", friendId);
            values.put("groupId", post.mGroupId);
            values.put("contentType", post.mContentType);
            values.put("content", post.mContent);
            values.put("attachments", Json.toJson(post.mAttachments));
            values.put("createdTimestamp", dateToString(post.mCreatedTimestamp));
            values.put("modifiedTimestamp", dateToString(post.mModifiedTimestamp));
            values.put("sequenceNumber", post.mSequenceNumber);
            values.put("unread", 1);
            mDatabase.replaceOrThrow("Post", null, values);
            // Automatically enqueue new message attachments for download
            for (Protocol.Resource resource : post.mAttachments) {
                addDownload(friendId, resource);
            }
        }        
    }
    
    private void updateFriendLastReceivedTime(String friendId) throws Utils.ApplicationError {
        // Helper used by putPullResponse and putPushedGroup; assumes in transaction
        // Set friend lastReceivedTimestamp to current time 
        ContentValues values = new ContentValues();
        values.put("lastReceivedTimestamp", dateToString(new Date()));
        if (1 != mDatabase.update("Friend", values, "id = ?", new String[]{friendId})) {
            throw new Utils.ApplicationError(LOG_TAG, "update friend failed");
        }        
    }
    
    public void addLocalResource(LocalResource localResource) throws Utils.ApplicationError {
        try {
            mDatabase.beginTransactionNonExclusive();
            ContentValues values = new ContentValues();
            values.put("resourceId", localResource.mResourceId);
            values.put("groupId", localResource.mGroupId);
            values.put("type", localResource.mType.name());
            values.put("mimeType", localResource.mMimeType);
            values.put("filePath", localResource.mFilePath);
            values.put("tempFilePath", localResource.mTempFilePath);
            mDatabase.insertOrThrow("LocalResource", null, values);
            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
    }
    
    private static final String SELECT_LOCAL_RESOURCE =
            "SELECT resourceId, groupId, type, mimeType, filePath, tempFilePath FROM LocalResource";

    private static final IRowToObject<LocalResource> mRowToLocalResource =
        new IRowToObject<LocalResource>() {
            @Override
            public LocalResource rowToObject(SQLiteDatabase database, Cursor cursor) throws Utils.ApplicationError {
                return new LocalResource(
                        cursor.getString(0), cursor.getString(1), LocalResource.Type.valueOf(cursor.getString(2)),
                        cursor.getString(3), cursor.getString(4), cursor.getString(5));
            }
        };

    public LocalResource getLocalResource(String friendId, String resourceId)
            throws Utils.ApplicationError, NotFoundError {
        // Reports NotFound when friend is not a member of group that resource belongs to
        return getObject(
                SELECT_LOCAL_RESOURCE + " WHERE resourceid = ? AND groupId IN (SELECT groupId FROM GroupMember WHERE memberId = ?)",
                new String[]{resourceId, friendId},
                mRowToLocalResource);
    }

    public void addDownload(String friendId, Protocol.Resource resource)
            throws Utils.ApplicationError {
        try {
            mDatabase.beginTransactionNonExclusive();
            ContentValues values = new ContentValues();
            values.put("publisherId", friendId);
            values.put("resourceId", resource.mId);
            values.put("state", Download.State.IN_PROGRESS.name());
            values.put("mimeType", resource.mMimeType);
            values.put("size", resource.mSize);
            mDatabase.insertOrThrow("Download", null, values);
            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
    }

    public void updateDownloadState(String friendId, String resourceId, Download.State state)
            throws Utils.ApplicationError, NotFoundError {
        try {
            mDatabase.beginTransactionNonExclusive();
            ContentValues values = new ContentValues();
            values.put("state", state.name());
            if (1 != mDatabase.update(
                    "Download", values, "publisherId = ? AND resourceId = ?", new String[]{friendId, resourceId})) {
                throw new Utils.ApplicationError(LOG_TAG, "update download failed");
            }
            mDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            mDatabase.endTransaction();
        }
    }

    private static final String SELECT_DOWNLOAD =
            "SELECT publisherId, resourceId, mimeType, size, state FROM Download";

    private static final IRowToObject<Download> mRowToDownload =
        new IRowToObject<Download>() {
            @Override
            public Download rowToObject(SQLiteDatabase database, Cursor cursor) throws Utils.ApplicationError {
                return new Download(
                        cursor.getString(0), cursor.getString(1), cursor.getString(2),
                        cursor.getLong(3), Download.State.valueOf(cursor.getString(4)));
            }
        };

    public Download getDownload(String friendId, String resourceId)
            throws Utils.ApplicationError, NotFoundError {
        return getObject(
                SELECT_DOWNLOAD + " WHERE publisherId = ? AND resourceid = ?",
                new String[]{friendId, resourceId},
                mRowToDownload);
    }

    public Download getNextInProgressDownload(String friendId)
            throws Utils.ApplicationError, NotFoundError {
        // *TODO* ORDER BY...?
        return getObject(
                SELECT_DOWNLOAD + " WHERE publisherId = ? AND state = ?",
                new String[]{friendId, Download.State.IN_PROGRESS.name()},
                mRowToDownload);
    }
    
    private long getCount(String query, String[] args) throws Utils.ApplicationError {
        // Helper for SELECT COUNT, which should not throw NotFoundError
        try {
            return getLongColumn(query, args);
        } catch (NotFoundError e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    private long getLongColumn(String query, String[] args) throws Utils.ApplicationError, NotFoundError {
        Cursor cursor = null;
        try {
            cursor = mDatabase.rawQuery(query, args);
            if (!cursor.moveToFirst()) {
                throw new NotFoundError();
            }
            return cursor.getLong(0);
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String getStringColumn(String query, String[] args) throws Utils.ApplicationError, NotFoundError {
        Cursor cursor = null;
        try {
            cursor = mDatabase.rawQuery(query, args);
            if (!cursor.moveToFirst()) {
                throw new NotFoundError();
            }
            return cursor.getString(0);
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String[] getRow(String query, String[] args) throws Utils.ApplicationError, NotFoundError {
        Cursor cursor = null;
        try {
            cursor = mDatabase.rawQuery(query, args);
            if (!cursor.moveToFirst()) {
                throw new NotFoundError();
            }
            String[] columns = new String[cursor.getColumnCount()];
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                columns[i] = cursor.getString(i);
            }
            return columns;
        } catch (SQLiteException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static interface IRowToObject<T> {
        public T rowToObject(SQLiteDatabase database, Cursor cursor) throws Utils.ApplicationError;
    }

    private <T> T getObject(String query, String[] args, IRowToObject<T> rowToObject)
            throws Utils.ApplicationError, NotFoundError {
        Cursor cursor = null;
        try {
            cursor = mDatabase.rawQuery(query, args);
            if (!cursor.moveToFirst()) {
                throw new NotFoundError();
            }
            return rowToObject.rowToObject(mDatabase, cursor);
        } catch (SQLiteException e) {
            if (cursor != null) {
                cursor.close();
            }
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    private <T> CursorIterator<T> getObjectCursor(String query, String[] args, IRowToObject<T> rowToObject)
            throws Utils.ApplicationError {
        Cursor cursor = null;
        try {
            cursor = mDatabase.rawQuery(query, args);
            return new CursorIterator<T>(mDatabase, cursor, rowToObject);
        } catch (SQLiteException e) {
            if (cursor != null) {
                cursor.close();
            }
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    public static class CursorIterator<T> implements Iterable<T>, Iterator<T> {

        private final SQLiteDatabase mDatabase;
        private final Cursor mCursor;
        private final IRowToObject<T> mRowToObject;
        private T mNext;

        public CursorIterator(
                SQLiteDatabase database, Cursor cursor, IRowToObject<T> rowToObject) {
            mDatabase = database;
            mCursor = cursor;
            mCursor.moveToFirst();
            mNext = getNext();
            mRowToObject = rowToObject;
        }

        @Override
        public Iterator<T> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            return mNext != null;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            T result = mNext;
            mNext = getNext();
            return result;
        }

        private T getNext() {
            if (mCursor.isAfterLast()) {
                return null;
            }
            try {
                T next = mRowToObject.rowToObject(mDatabase, mCursor);
                mCursor.moveToNext();
                return next;
            } catch (Utils.ApplicationError e) {
                Log.addEntry(LOG_TAG, "failed to get next cursor object");
                return null;
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        public int getCount() {
            return mCursor.getCount();
        }

        public void close() {
            mCursor.close();
        }
    }
    
    private static Date stringToDate(String string) {
        // *TODO*
        return null;
    }

    private static String dateToString(Date date) {
        // *TODO*
        return null;
    }
}
