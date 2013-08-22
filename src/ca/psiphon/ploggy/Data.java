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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class Data {

    public static class Preferences {
        public int mLocationUpdatePeriodInSeconds = 600;
        public int mLocationFixPeriodInSeconds = 60;
        public boolean mAllowUseNetworkLocationProvider = true;
        public boolean mAllowUseGeoCoder = true;
        public boolean mAllowUseMobileNetwork = true;
        // TODO:
        // - geofencing
        // - time-of-day limits
    }
    
    public static class Self {
        public String mNickname;
        public String mIdenticon; // cached
        public String mTransportPublicKey;
        public String mTransportPrivateKey;
        public String mHiddenServiceHostname;
        public String mHiddenServicePrivateKey;
    }
    
    public static class Friend {
        public String mNickname;
        public String mIdenticon; // cached
        public String mTransportPublicKey;
        public String mHiddenServiceHostname;
    }
    
    public static class LocationPackage {
        public String mLongitude;
        public String mLatitude;
        public String mStreetAddress;
        public String mTimestamp;
        public String mStatusMessage;
        public ArrayList<String> mMapTileIds;
        public String mPhotoId;        
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

    Preferences mPreferences;
    Self mSelf;
    LocationPackage mSelfLocationPackage;
    ArrayList<Friend> mFriends;
    HashMap<String, LocationPackage> mFriendLocationPackages;
    
    public synchronized Data.Preferences getPreferences() {
        return null;
    }

    public synchronized void updatePreferences(Data.Preferences preferences) {
    }
    
    // 1. write JSON to disk
    // 2. set in-memory value
    // 3. serve (some) reads from memory

    public synchronized Data.Self getSelf() {
        return null;
    }

    public synchronized void updateSelf(Data.Self self) {
    }

    public synchronized Data.LocationPackage getSelfLocationPackage() {
        return null;
    }

    public synchronized void getSelfLocationPackage(Data.LocationPackage locationPackage) {
    }

    public synchronized ArrayList<Data.Friend> getFriends() {
        return null;
    }

    public synchronized void updateFriend(Data.Friend friend) {
    }

    public synchronized void removeFriend(Data.Friend friend) {
    }

    public synchronized Data.LocationPackage readFriendLocationPackage() {
        return null;
    }

    public synchronized void writeFriendLocationPackage(Data.LocationPackage locationPackage) {
    }
    
    // 2pc:
    // - write <friendID>-loc.json.new
    // - delete <friendID>-loc.json
    // - [resumable] rename <friendID>-loc.json.new -->  <friendID>-loc.json

    // data files: (1) map tile/photo; (2) may or may not exist/be complete/be cached
    
    public static synchronized InputStream openDataFileForRead(String dataFileId) {
        return null;
    }

    public static synchronized int getDataFileSizeIncomplete(String dataFileId) {
        return -1;
    }

    public static synchronized InputStream openDataFileForAppend(String dataFileId) {        
        return null;
    }
}
