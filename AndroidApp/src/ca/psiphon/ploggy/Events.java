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

import java.util.HashSet;
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

    // TODO: final?
    private static Bus mBus;
    private static Handler mHandler;
    private static Set<Object> mRegisteredObjects;

    public static void initialize() {
        mBus = new Bus(ThreadEnforcer.MAIN);
        mHandler = new Handler();

        // Activity and fragment lifecycle events make it difficult to reliably
        // make register and unregister calls in a 1-to-1 way. So we're going
        // to make sure that things only get registered once and unregistered if
        // they're actually registered.
        mRegisteredObjects = new HashSet<Object>();
    }

    public static void register(Object object) {
        if (!mRegisteredObjects.contains(object)) {
            mBus.register(object);
            mRegisteredObjects.add(object);
        }
    }

    public static void unregister(Object object) {
        if (mRegisteredObjects.contains(object)) {
            mBus.unregister(object);
            mRegisteredObjects.remove(object);
        }
    }

    public static void post(Object object) {
        final Object postObject = object;
        mHandler.post(
            new Runnable() {
                @Override
                public void run() {
                    mBus.post(postObject);
                }
            });
    }

    public static class UpdatedSelf {

        public UpdatedSelf() {
        }
    }

    public static class NewSelfLocation {
        public final Location mLocation;
        public final Address mAddress;

        public NewSelfLocation(Location location, Address address) {
            mLocation = location;
            mAddress = address;
        }
    }

    public static class UpdatedSelfStatus {

        public UpdatedSelfStatus() {
        }
    }

    public static class AddedFriend  {
        public final String mId;

        public AddedFriend(String id) {
            mId = id;
        }
    }

    public static class UpdatedFriend  {
        public final String mId;

        public UpdatedFriend(String id) {
            mId = id;
        }
    }

    public static class UpdatedFriendStatus {
        public final String mId;

        public UpdatedFriendStatus(String id) {
            mId = id;
        }
    }

    public static class RemovedFriend  {
        public final String mId;

        public RemovedFriend(String id) {
            mId = id;
        }
    }

    public static class UpdatedNewMessages {
    }

    public static class UpdatedAllMessages {
    }

    public static class DisplayedFriends {
    }

    public static class DisplayedMessages {
    }
}
