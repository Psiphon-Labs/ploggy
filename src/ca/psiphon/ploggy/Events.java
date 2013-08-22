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

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

public class Events {

    public static final Bus bus = new Bus(ThreadEnforcer.MAIN);
    
    // TODO:
    // post-on-MAIN-thread helper: runOnUiThread?
    
    public static class Request {
        public int mRequestId;
    }

    public static class Response {
        public int mRequestId;
        public boolean mSuccess;
        public String mErrorMessage;
    }
    
    // RequestFailed event?
    
    public static class RequestUpdatePreferences extends Request {
        public Data.Preferences mPreferences;
    }

    public static class UpdatedPreferences extends Response {
        public Data.Preferences mPreferences;
    }

    public static class RequestGenerateSelf extends Request {
        public String mNickname;
    }

    public static class GeneratedSelf extends Response {
        public Data.Self mSelf;
    }
    /*
    @Produce public GeneratedSelf produceGeneratedSelf() {
        return new GeneratedSelf(self);
    }    
    */  

    public static class RequestDecodeFriend extends Request  {
        public String mEncodedFriend;
    }

    public static class DecodedFriend extends Response {
        public Data.Friend mFriend;
    }

    public static class RequestAddFriend extends Request  {
        public Data.Friend mFriend;
    }

    public static class AddedFriend extends Response {
        public Data.Friend mFriend;
    }
    // subs: UI, pusher

    public static class RequestDeleteFriend extends Request {
        public String mFriendNickname;
    }

    public static class DeletedFriend extends Response {
        public String mFriendNickname;
    }
    // subs: UI, pusher

    public static class DetectedNewLocation {
    }
    // subs: ...

    public static class PreparedNewLocationPackage {
    }    
    // @Produce: existing package --> for UI subscriber
    // subs: pusher

    public static class ReceivedFriendLocationPackage {
    }
    // @Produce: existing package --> for UI subscriber [...which Friend?]

}
