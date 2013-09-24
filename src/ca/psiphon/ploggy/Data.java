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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

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

        // TODO:
        // - manually-triggered location sharing-only
        // - location reporting granularity
        // - geofencing
        // - time-of-day limits
    }
    
    public static class Self {
        public final String mNickname;
        public final String mAvatar;
        public final TransportSecurity.KeyMaterial mTransportKeyMaterial;
        public final HiddenService.KeyMaterial mHiddenServiceKeyMaterial;

        public Self(
                String nickname,
                TransportSecurity.KeyMaterial transportKeyMaterial,
                HiddenService.KeyMaterial hiddenServiceKeyMaterial) {
            mNickname = nickname;
            String id = Utils.makeId(nickname, transportKeyMaterial.mCertificate, hiddenServiceKeyMaterial.mHostname);
            mAvatar = Utils.makeIdenticon(id);
            mTransportKeyMaterial = transportKeyMaterial;
            mHiddenServiceKeyMaterial = hiddenServiceKeyMaterial;
        }
        
        public Friend getFriend() {
        	return new Friend(mNickname, mTransportKeyMaterial.getCertificate(), mHiddenServiceKeyMaterial.getIdentity());
        }
    }
    
    public static class Friend {
        public final String mId;
        public final String mNickname;
        public final String mAvatar;
        public final TransportSecurity.Certificate mTransportCertificate;
        public final HiddenService.Identity mHiddenServiceIdentity;

        public Friend(
                String nickname,
                TransportSecurity.Certificate certificate,
                HiddenService.Identity hiddenServiceIdentity) {
            mId = Utils.makeId(nickname, certificate.mCertificate, hiddenServiceIdentity.mHostname);
            mNickname = nickname;
            mAvatar = Utils.makeIdenticon(mId);
            mTransportCertificate = certificate;
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

    // TODO: SQLCipher/IOCipher storage? key/value store?
    // TODO: use http://nelenkov.blogspot.ca/2011/11/using-ics-keychain-api.html?
    // ...consistency: write file, then update in-memory; 2pc; only for short lists of friends
    // ...eventually use file system for map tiles etc.
       
    private static final String PREFERENCES_FILENAME = "preferences.json"; 
    private static final String SELF_FILENAME = "self.json"; 
    private static final String SELF_STATUS_FILENAME = "selfStatus.json"; 
    private static final String FRIENDS_FILENAME = "friends.json"; 
    private static final String FRIEND_STATUS_FILENAME_FORMAT_STRING = "%s-friendStatus.json"; 
    private static final String COMMIT_FILENAME_SUFFIX = ".commit"; 
    
    Preferences mPreferences;
    Self mSelf;
    Status mSelfStatus;
    List<Friend> mFriends;
    HashMap<String, Status> mFriendStatuses;
    
    public synchronized Preferences getPreferences() throws Utils.ApplicationError {
        if (mPreferences == null) {
            try {
                mPreferences = Json.fromJson(readFile(PREFERENCES_FILENAME), Preferences.class);
            } catch (DataNotFoundException e) {
                // Use default preferences
                mPreferences = new Preferences();
            }
        }
        return mPreferences;
    }

    public synchronized void updatePreferences(Preferences preferences) throws Utils.ApplicationError {
        writeFile(PREFERENCES_FILENAME, Json.toJson(preferences));
        mPreferences = preferences;
    }
    
    public synchronized Self getSelf() throws Utils.ApplicationError, DataNotFoundException {
    	// TODO: temp
    	return new Self("selfNickname", new TransportSecurity.KeyMaterial("type", "certificate", "privateKey"), new HiddenService.KeyMaterial("type", "hostname", "privateKey"));
    	/*
    	if (mSelf == null) {
            mSelf = Json.fromJson(readFile(SELF_FILENAME), Self.class);
        }
        return mSelf;
        */
    }

    public synchronized void updateSelf(Self self) throws Utils.ApplicationError {
        writeFile(SELF_FILENAME, Json.toJson(self));
        mSelf = self;
    }

    public synchronized Status getSelfStatus() throws Utils.ApplicationError, DataNotFoundException {
        if (mSelfStatus == null) {
            mSelfStatus = Json.fromJson(readFile(SELF_STATUS_FILENAME), Status.class);
        }
        return mSelfStatus;
    }

    public synchronized void updateSelfStatus(Data.Status status) throws Utils.ApplicationError {
        writeFile(SELF_STATUS_FILENAME, Json.toJson(status));
        mSelfStatus = status;
    }

    private void loadFriends() throws Utils.ApplicationError {
        if (mFriends == null) {
	    	try {
				mFriends = Json.fromJsonArray(readFile(FRIENDS_FILENAME), Friend.class);
			} catch (DataNotFoundException e) {
				mFriends = new ArrayList<Friend>();
			}
        	mFriends = Collections.synchronizedList(mFriends);
        }
    }
    
    public synchronized final List<Friend> getFriends() throws Utils.ApplicationError {
    	loadFriends();
        // TODO: return immutable List?
        return mFriends;
    }

    public synchronized Friend getFriendById(String id) throws Utils.ApplicationError, DataNotFoundException {
    	loadFriends();
    	synchronized(mFriends) {
	        for (Friend friend : mFriends) {
	        	if (friend.mId.equals(id)) {
	        		return friend;
	        	}
	        }
    	}
        throw new DataNotFoundException();
    }

    private void insertOrUpdate(Friend friend, List<Friend> list) {
    	boolean found = false;
        for (int i = 0; i < list.size(); i++) {
        	if (list.get(i).mId.equals(friend.mId)) {
        		list.set(i, friend);
        		found = true;
        		break;
        	}
        }
        if (!found) {
        	list.add(friend);
        }
    }

    public synchronized void insertOrUpdateFriend(Friend friend) throws Utils.ApplicationError {
    	loadFriends();
    	synchronized(mFriends) {
	    	ArrayList<Friend> newFriends = new ArrayList<Friend>(mFriends);
	    	insertOrUpdate(friend, newFriends);
	        writeFile(FRIENDS_FILENAME, Json.toJson(newFriends));
	    	insertOrUpdate(friend, mFriends);
    	}
    }

    private void remove(Friend friend, List<Friend> list) throws DataNotFoundException {
    	boolean found = false;
        for (int i = 0; i < list.size(); i++) {
        	if (list.get(i).mId.equals(friend.mId)) {
        		list.remove(i);
        		found = true;
        		break;
        	}
        }
        if (!found) {
        	throw new DataNotFoundException();
        }
    }

    public synchronized void removeFriend(Friend friend) throws Utils.ApplicationError, DataNotFoundException {
    	loadFriends();
    	synchronized(mFriends) {
	    	ArrayList<Friend> newFriends = new ArrayList<Friend>(mFriends);
	    	remove(friend, newFriends);
	        writeFile(FRIENDS_FILENAME, Json.toJson(newFriends));
	    	remove(friend, mFriends);
    	}
    }

    public synchronized Status getFriendStatus(String id) throws Utils.ApplicationError, DataNotFoundException {
    	String filename = String.format(FRIEND_STATUS_FILENAME_FORMAT_STRING, id);
        return Json.fromJson(readFile(filename), Status.class);
    }

    public synchronized void updateFriendStatus(String id, Status status) throws Utils.ApplicationError {
    	String filename = String.format(FRIEND_STATUS_FILENAME_FORMAT_STRING, id);
    	writeFile(filename, Json.toJson(status));
    }

    /*
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
    
    private static String readFile(String filename) throws Utils.ApplicationError, DataNotFoundException {
        FileInputStream inputStream = null;
        try {
        	String commitFilename = filename + COMMIT_FILENAME_SUFFIX;
        	replaceFileIfExists(commitFilename, filename);
            inputStream = Utils.getApplicationContext().openFileInput(filename);
            return Utils.inputStreamToString(inputStream);
        } catch (FileNotFoundException e) {
            throw new DataNotFoundException();
        } catch (IOException e) {
            throw new Utils.ApplicationError(e);
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
        	String commitFilename = filename + COMMIT_FILENAME_SUFFIX;
            outputStream = Utils.getApplicationContext().openFileOutput(commitFilename, Context.MODE_PRIVATE);
            outputStream.write(value.getBytes());
            outputStream.close();
            replaceFileIfExists(commitFilename, filename);
        } catch (IOException e) {
            throw new Utils.ApplicationError(e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private static void replaceFileIfExists(String commitFilename, String filename) throws IOException {
        File commitFile = new File(Utils.getApplicationContext().getFilesDir(), commitFilename);
        if (commitFile.exists()) {
	        File file = new File(Utils.getApplicationContext().getFilesDir(), filename);
	        file.delete();
	        commitFile.renameTo(file);
        }
    }
}
