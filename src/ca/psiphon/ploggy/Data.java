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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;

public class Data {
    
    // ... immutable POJOs

    public static class Preferences {
        public final int mLocationUpdatePeriodInSeconds = 600;
        public final int mLocationFixPeriodInSeconds = 60;
        public final int mLocationReportThresholdInMeters = 10;
        public final boolean mAllowUseNetworkLocationProvider = true;
        public final boolean mAllowUseGeoCoder = true;
        public final boolean mAllowUseMobileNetwork = true;
        
        // TODO: public Preferences(int, int, int, boolean, boolean, boolean) {}
        // TODO: fromJson, toJson

        // TODO:
        // - manually-triggered location sharing-only
        // - location reporting granularity
        // - geofencing
        // - time-of-day limits

        public static Preferences fromJson(String json) {
            // TODO: ...
            return null;
        }

        public Preferences toJson() {
            // TODO: ...
            return null;
        }
    }
    
    public static class Self {
        public final String mNickname;
        public final String mIdenticon;
        public final String mTransportKeyPair;
        public final String mHiddenServiceIdentity;

        public Self(String nickname, String transportKeyPair, String hiddenServiceIdentity) {
            mNickname = nickname;
            String id = Utils.makeId(
                            nickname,
                            TransportSecurity.KeyPair.fromJson(transportKeyPair).mPublicKey,
                            HiddenService.Identity.fromJson(hiddenServiceIdentity).mHostname);
            mIdenticon = Utils.makeIdenticon(id);
            mTransportKeyPair = transportKeyPair;
            mHiddenServiceIdentity = hiddenServiceIdentity;            
        }

        public static Self fromJson(String json) {
            // TODO: ...
            return null;
        }

        public String toJson() {
            // TODO: ...
            return null;
        }
    }
    
    public static class Friend {
        public final String mId;
        public final String mNickname;
        public final String mIdenticon;
        public final String mTransportPublicKey;
        public final String mHiddenServiceHostname;

        public Friend(String nickname, String identicon, String transportPublicKey, String hiddenServiceHostname) {
            mId = Utils.makeId(nickname, transportPublicKey, hiddenServiceHostname);
            mNickname = nickname;
            mIdenticon = Utils.makeIdenticon(mId);
            mTransportPublicKey = transportPublicKey;
            mHiddenServiceHostname = hiddenServiceHostname;            
        }

        public static Friend fromJson(String json) {
            // TODO: ...
            return null;
        }

        public String toJson() {
            // TODO: ...
            return null;
        }
    }
    
    public static class Status {
        public final String mTimestamp;
        public final String mLongitude;
        public final String mLatitude;
        public final String mStreetAddress;
        // TODO: public final ArrayList<String> mMapTileIds;
        // TODO: public final String mMessage;
        // TODO: public final String mPhotoId;        


        public Status(String timestamp, String longitude, String latitude, String streetAddress) {
            mTimestamp = timestamp;
            mLongitude = longitude;
            mLatitude = latitude;
            mStreetAddress = streetAddress;            
        }

        public static Status fromJson(String json) {
            // TODO: ...
            return null;
        }

        public String toJson() {
            // TODO: ...
            return null;
        }
    }
    
    public static class DataNotFoundException extends Exception {
        private static final long serialVersionUID = -8736069103392081076L;        
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

    private static final String PREFERENCES_FILENAME = "preferences.json"; 
    private static final String SELF_FILENAME = "self.json"; 
    private static final String SELF_STATUS_FILENAME = "selfStatus.json"; 
    private static final String FRIENDS_FILENAME = "friends.json"; 
    private static final String FRIEND_STATUS_FILENAME_FORMAT_STRING = "%s-friendStatus.json"; 
    
    Preferences mPreferences;
    Self mSelf;
    Status mSelfStatus;
    ArrayList<Friend> mFriends;
    HashMap<String, Status> mFriendStatuses;
    
    public synchronized Data.Preferences getPreferences() {
        if (mPreferences == null) {
            try {
                mPreferences = Preferences.fromJson(readFile(PREFERENCES_FILENAME));
            } catch (DataNotFoundException e) {
                // Use default preferences
                mPreferences = new Preferences();
            }
        }
        return mPreferences;
    }

    public synchronized void updatePreferences(Data.Preferences preferences) {
        writeFile(PREFERENCES_FILENAME, preferences.toJson());
        mPreferences = preferences;
    }
    
    public synchronized Data.Self getSelf() throws DataNotFoundException {
        if (mSelf == null) {
            mSelf = Self.fromJson(readFile(SELF_FILENAME));
        }
        return mSelf;
    }

    public synchronized void updateSelf(Data.Self self) {
        writeFile(SELF_FILENAME, self.toJson());
        mSelf = self;
    }

    public synchronized Data.Status getSelfStatus() throws DataNotFoundException {
        if (mSelfStatus == null) {
            mSelfStatus = Status.fromJson(readFile(SELF_STATUS_FILENAME));
        }
        return mSelfStatus;
    }

    public synchronized void updateSelfStatus(Data.Status status) {
        writeFile(SELF_STATUS_FILENAME, status.toJson());
        mSelfStatus = status;
    }

    public synchronized ArrayList<Data.Friend> getFriends() {
        // TODO: ...
        return null;
    }

    public synchronized Data.Friend getFriendById(String id) throws DataNotFoundException {
        // TODO: ...
        throw new DataNotFoundException();
    }

    public synchronized Data.Friend getFriendByTransportCertificate(X509Certificate certificate) throws DataNotFoundException {
        // TODO: ...
        // ...certificate.getEncoded()
        throw new DataNotFoundException();
    }

    public synchronized void updateFriend(Data.Friend friend) {
        // TODO: ...
    }

    public synchronized void removeFriend(Data.Friend friend) {
        // TODO: ...
    }

    public synchronized Data.Status getFriendStatus() throws DataNotFoundException {
        // TODO: ...
        return null;
    }

    public synchronized void updateFriendStatus(Data.Status status) {
        // TODO: ...
    }
    
    /*
    // 2pc:
    // - write <friendID>-loc.json.new
    // - delete <friendID>-loc.json
    // - [resumable] rename <friendID>-loc.json.new -->  <friendID>-loc.json

    // data files: (1) map tile/photo; (2) may or may not exist/be complete/be cached
    
    public static synchronized InputStream openDataFileForRead(String dataFileId) {
        // TODO: ...
        return null;
    }

    public static synchronized int getDataFileSizeIncomplete(String dataFileId) {
        // TODO: ...
        return -1;
    }

    public static synchronized InputStream openDataFileForAppend(String dataFileId) {        
        // TODO: ...
        return null;
    }
    */
    
    // TODO: SQLCipher/IOCipher storage?
    
    private static String readFile(String filename) throws DataNotFoundException {
        FileInputStream inputStream;
        try {
            inputStream = openFileInput(filename);
            return Utils.inputStreamToString(inputStream);
        } catch (FileNotFoundException e) {
            throw new DataNotFoundException();
        } catch (IOException e) {
            // TODO: ...
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }        
    }

    private static void writeFile(String filename, String value) {
        FileOutputStream outputStream;
        try {
            outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(value.getBytes());
        } catch (IOException e) {
            // TODO: ...
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }
}
