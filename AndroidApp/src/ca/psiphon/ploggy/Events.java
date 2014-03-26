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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.location.Address;
import android.location.Location;
import android.os.Handler;

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

/**
 * Support for Otto event bus.
 *
 * All event objects for Otto are defined here. Also, this class wraps a
 * static bus instance and provides a helper which ensures events are
 * processed on the main thread.
 */
public class Events {

    private static final String LOG_TAG = "Events";

    private static Map<String, Events> mInstances = new HashMap<String, Events>();

    public static synchronized Events getInstance() {
        return getInstance(Engine.DEFAULT_PLOGGY_INSTANCE_NAME);
    }
    public static synchronized Events getInstance(String name) {
        if (!mInstances.containsKey(name)) {
            mInstances.put(name, new Events(name));
        }
        return mInstances.get(name);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    private final String mInstanceName;
    private final Bus mBus;
    private final Handler mHandler;
    private final Set<Object> mRegisteredObjects;

    private Events(String instanceName) {
        mInstanceName = instanceName;
        ThreadEnforcer threadEnforcer = ThreadEnforcer.ANY;
        if (mInstanceName.equals(Engine.DEFAULT_PLOGGY_INSTANCE_NAME)) {
            threadEnforcer = ThreadEnforcer.MAIN;
        }
        mBus = new Bus(threadEnforcer, mInstanceName);
        mHandler = new Handler();

        // Activity and fragment lifecycle events make it difficult to reliably
        // make register and unregister calls in a 1-to-1 way. So we're going
        // to make sure that things only get registered once and unregistered if
        // they're actually registered.
        mRegisteredObjects = new HashSet<Object>();
    }

    public void register(Object object) {
        if (!mRegisteredObjects.contains(object)) {
            mBus.register(object);
            mRegisteredObjects.add(object);
        }
    }

    public void unregister(Object object) {
        if (mRegisteredObjects.contains(object)) {
            mBus.unregister(object);
            mRegisteredObjects.remove(object);
        }
    }

    public void post(Object object) {
        final Object postObject = object;
        mHandler.post(
            new Runnable() {
                @Override
                public void run() {
                    mBus.post(postObject);
                }
            });
    }

    public static class TorCircuitEstablished {
    }

    public static class BytesSentToFriend {
    }

    public static class BytesReceivedFromFriend {
    }

    public static class UpdatedSelf {

        public UpdatedSelf() {
        }
    }

    public static class AddedFriend  {
        public final String mFriendId;

        public AddedFriend(String friendId) {
            mFriendId = friendId;
        }
    }

    public static class UpdatedFriend  {
        public final String mFriendId;

        public UpdatedFriend(String friendId) {
            mFriendId = friendId;
        }
    }

    public static class RemovedFriend  {
        public final String mFriendId;

        public RemovedFriend(String friendId) {
            mFriendId = friendId;
        }
    }

    public static class UpdatedSelfGroup {
        public final String mGroupId;

        public UpdatedSelfGroup(String groupId) {
            mGroupId = groupId;
        }
    }

    public static class UpdatedFriendGroup {
        public final String mFriendId;
        public final String mGroupId;

        public UpdatedFriendGroup(String friendId, String groupId) {
            mFriendId = friendId;
            mGroupId = groupId;
        }
    }

    public static class NewSelfLocationFix {
        public final Location mLocation;
        public final Address mAddress;

        public NewSelfLocationFix(Location location, Address address) {
            mLocation = location;
            mAddress = address;
        }
    }

    public static class UpdatedSelfLocation {

        public UpdatedSelfLocation() {
        }
    }


    public static class UpdatedFriendLocation {
        public final String mFriendId;

        public UpdatedFriendLocation(String friendId) {
            mFriendId = friendId;
        }
    }

    public static class UpdatedSelfPost {
        public final String mGroupId;
        public final String mPostId;

        public UpdatedSelfPost(String groupId, String postId) {
            mGroupId = groupId;
            mPostId = postId;
        }
    }

    public static class UpdatedFriendPost {
        public final String mFriendId;
        public final String mGroupId;
        public final String mPostId;

        public UpdatedFriendPost(String friendId,String groupId,  String postId) {
            mGroupId = groupId;
            mFriendId = friendId;
            mPostId = postId;
        }
    }

    public static class MarkedAsReadPosts {
        public final String mGroupId;

        public MarkedAsReadPosts(String groupId) {
            mGroupId = groupId;
        }
    }

    /*
    // *TODO* ...?
    public static class UpdatedNewMessages {
    }

    // *TODO* ...?
    public static class DisplayedFriends {
    }

    // *TODO* ...?
    public static class DisplayedMessages {
    }
    */

    public static class AddedDownload {
        public final String mFriendId;
        public final String mResourceId;

        public AddedDownload(String friendId, String resourceId) {
            mFriendId = friendId;
            mResourceId = resourceId;
        }
    }
}
