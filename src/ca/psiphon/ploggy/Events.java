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

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

public class Events {

    // TODO: post-on-MAIN-thread helper: runOnUiThread?
    
    //public static final Bus bus = new Bus(ThreadEnforcer.MAIN);
	public static final Bus bus = new Bus(ThreadEnforcer.ANY);
    
    public static class Request {
        private static long mNextRequestId = 0;        
        public final long mRequestId;

        public Request() {
            mRequestId = ++mNextRequestId;
        }
    }

    /*
    public static class Response {
        public final int mRequestId;
        public final boolean mSuccess;
        public final String mErrorMessage;
        
        public Response(int requestId, boolean success, String errorMessage) {
            mRequestId = requestId;
            mSuccess = success;
            mErrorMessage = errorMessage;
        }
    }
    */
    
    public static class RequestFailed {
        public final long mRequestId;
        public final String mErrorMessage;

        public RequestFailed(long requestId, String errorMessage) {
            mRequestId = requestId;
            mErrorMessage = errorMessage;
        }
    }
    
    public static class RequestUpdatePreferences extends Request {
        public final Data.Preferences mPreferences;

        public RequestUpdatePreferences(Data.Preferences preferences) {
            mPreferences = preferences;            
        }
    }

    public static class UpdatedPreferences {
        public final Data.Preferences mPreferences;

        public UpdatedPreferences(Data.Preferences preferences) {
            mPreferences = preferences;            
        }
    }

    public static class RequestGenerateSelf extends Request {
        public final String mNickname;

        public RequestGenerateSelf(String nickname) {
            mNickname = nickname;            
        }
    }

    public static class GeneratedSelf {
        public final Data.Self mSelf;

        public GeneratedSelf(Data.Self self) {
            mSelf = self;            
        }
    }
    /*
    @Produce public GeneratedSelf produceGeneratedSelf() {
        return new GeneratedSelf(self);
    }    
    */  

    public static class RequestDecodeFriend extends Request  {
        public final String mEncodedFriend;
        
        public RequestDecodeFriend(String encodedFriend) {
            mEncodedFriend = encodedFriend;
        }
    }

    public static class DecodedFriend {
        public final Data.Friend mFriend;
        
        public DecodedFriend(Data.Friend friend) {
            mFriend = friend;
        }
    }

    public static class RequestAddFriend extends Request  {
        public final Data.Friend mFriend;

        public RequestAddFriend(Data.Friend friend) {
            mFriend = friend;
        }
    }

    public static class AddedFriend {
        public final Data.Friend mFriend;

        public AddedFriend(Data.Friend friend) {
            mFriend = friend;
        }
    }
    // TODO subs: UI, pusher

    public static class RequestDeleteFriend extends Request {
        public final String mId;

        public RequestDeleteFriend(String id) {
            mId = id;
        }
    }

    public static class DeletedFriend {
        public final String mId;

        public DeletedFriend(String id) {
            mId = id;
        }
    }

    public static class AddedLogEntry {
    }

    public static class NewSelfLocation {
    	public final Location mLocation;
    	public final Address mAddress;

    	public NewSelfLocation(Location location, Address address) {
    		mLocation = location;
    		mAddress = address;
    	}
    }
    // TODO subs: ...

    public static class NewSelfStatus {
    }    
    // TODO @Produce: existing package --> for UI subscriber
    // TODO subs: pusher

    public static class NewFriendStatus {
        public final Data.Status mStatus;

        public NewFriendStatus(Data.Status status) {
            mStatus = status;
        }
    }
    // TODO @Produce: existing package --> for UI subscriber [...which Friend?]
}
