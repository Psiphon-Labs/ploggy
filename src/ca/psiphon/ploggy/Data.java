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

import ca.psiphon.ploggy.Utils.ApplicationError;

import android.content.Context;

public class Data {
    
    // ... immutable POJOs
    // TODO: rename mFieldName --> fieldName (since using object mapper for json)

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
    }
    
    public static class Self {
        public final String mNickname;
        public final String mIdenticon;
        public final TransportSecurity.KeyMaterial mTransportKeyMaterial;
        public final HiddenService.KeyMaterial mHiddenServiceKeyMaterial;

        public Self(
                String nickname,
                TransportSecurity.KeyMaterial transportKeyMaterial,
                HiddenService.KeyMaterial hiddenServiceKeyMaterial) {
            mNickname = nickname;
            String id = Utils.makeId(nickname, transportKeyMaterial.mPublicKey, hiddenServiceKeyMaterial.mHostname);
            mIdenticon = Utils.makeIdenticon(id);
            mTransportKeyMaterial = transportKeyMaterial;
            mHiddenServiceKeyMaterial = hiddenServiceKeyMaterial;            
        }
    }
    
    public static class Friend {
        public final String mId;
        public final String mNickname;
        public final String mIdenticon;
        public final TransportSecurity.PublicKey mTransportPublicKey;
        public final HiddenService.Identity mHiddenServiceIdentity;

        public Friend(
                String nickname,
                TransportSecurity.PublicKey transportPublicKey,
                HiddenService.Identity hiddenServiceIdentity) {
            mId = Utils.makeId(nickname, transportPublicKey.mPublicKey, hiddenServiceIdentity.mHostname);
            mNickname = nickname;
            mIdenticon = Utils.makeIdenticon(mId);
            mTransportPublicKey = transportPublicKey;
            mHiddenServiceIdentity = hiddenServiceIdentity;            
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
    
    public synchronized Data.Preferences getPreferences() throws Utils.ApplicationError {
        if (mPreferences == null) {
            try {
                mPreferences = Utils.fromJson(readFile(PREFERENCES_FILENAME), Preferences.class);
            } catch (DataNotFoundException e) {
                // Use default preferences
                mPreferences = new Preferences();
            }
        }
        return mPreferences;
    }

    public synchronized void updatePreferences(Data.Preferences preferences) throws Utils.ApplicationError {
        writeFile(PREFERENCES_FILENAME, Utils.toJson(preferences));
        mPreferences = preferences;
    }
    
    public synchronized Data.Self getSelf() throws Utils.ApplicationError, DataNotFoundException {
        if (mSelf == null) {
            mSelf = Utils.fromJson(readFile(SELF_FILENAME), Self.class);
        }
        return mSelf;
    }

    public synchronized void updateSelf(Data.Self self) throws Utils.ApplicationError {
        writeFile(SELF_FILENAME, Utils.toJson(self));
        mSelf = self;
    }

    public synchronized Data.Status getSelfStatus() throws Utils.ApplicationError, DataNotFoundException {
        if (mSelfStatus == null) {
            mSelfStatus = Utils.fromJson(readFile(SELF_STATUS_FILENAME), Status.class);
        }
        return mSelfStatus;
    }

    public synchronized void updateSelfStatus(Data.Status status) throws Utils.ApplicationError {
        writeFile(SELF_STATUS_FILENAME, Utils.toJson(status));
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
        // ...transportPublicKey.toX509()
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
    // TODO: use http://nelenkov.blogspot.ca/2011/11/using-ics-keychain-api.html?
    
    private static String readFile(String filename) throws Utils.ApplicationError, DataNotFoundException {
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

    private static void writeFile(String filename, String value) throws Utils.ApplicationError {
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
