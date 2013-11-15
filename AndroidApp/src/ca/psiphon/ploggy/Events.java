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

    public static void initialize() {
        mBus = new Bus(ThreadEnforcer.MAIN);
        mHandler = new Handler();
    }
    
    public static void register(Object object) {
        mBus.register(object);
    }
    
    public static void unregister(Object object) {
        mBus.unregister(object);
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
}
