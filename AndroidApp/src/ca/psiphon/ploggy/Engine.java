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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Handler;
import android.preference.PreferenceManager;

import ca.psiphon.ploggy.widgets.TimePickerPreference;

import com.squareup.otto.Subscribe;

/**
 * Coordinator for background Ploggy work.
 * 
 * The Engine:
 * - schedule friend status push/pulls
 * - maintains a worker thread pool for background tasks (pushing/pulling
 *   friends and handling friend requests
 * - runs the local location monitor
 * - (re)-starts and stops the local web server and Tor Hidden Service to
 *   handle requests from friends
 *   
 * An Engine instance is intended to be run via an Android Service set to
 * foreground mode (i.e., long running).
 */
public class Engine implements OnSharedPreferenceChangeListener, WebServer.RequestHandler {
    
    private static final String LOG_TAG = "Engine";

    private Context mContext;
    private Handler mHandler;
    private Runnable mRestartTask;
    private SharedPreferences mSharedPreferences;
    private ScheduledExecutorService mTaskThreadPool;
    private HashMap<String, ScheduledFuture<?>> mFriendPullTasks;
    private LocationMonitor mLocationMonitor;
    private WebServer mWebServer;
    private TorWrapper mTorWrapper;
    
    private static final int THREAD_POOL_SIZE = 30;

    public Engine(Context context) {
        Utils.initSecureRandom();
        mContext = context;
        mHandler = new Handler();
        // TODO: distinct instance of preferences for each persona
        // e.g., getSharedPreferencesName("persona1");
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public synchronized void start() throws Utils.ApplicationError {
        Log.addEntry(LOG_TAG, "starting...");
        Events.register(this);
        mTaskThreadPool = Executors.newScheduledThreadPool(THREAD_POOL_SIZE);
        mFriendPullTasks = new HashMap<String, ScheduledFuture<?>>();
        mLocationMonitor = new LocationMonitor(this);
        mLocationMonitor.start();
        startSharingService();
        schedulePullFriends();
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        Log.addEntry(LOG_TAG, "started");
    }

    public synchronized void stop() {
        Log.addEntry(LOG_TAG, "stopping...");
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        Events.unregister(this);
        stopSharingService();
        if (mLocationMonitor != null) {
            mLocationMonitor.stop();
            mLocationMonitor = null;
        }
        if (mTaskThreadPool != null) {
            Utils.shutdownExecutorService(mTaskThreadPool);
            mTaskThreadPool = null;
            mFriendPullTasks = null;
        }
        Log.addEntry(LOG_TAG, "stopped");
    }

    @Override
    public synchronized void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Restart engine to apply changed preferences. Delay restart until user inputs are idle.
        // (This idle delay is important due to how SeekBarPreferences trigger onSharedPreferenceChanged
        // continuously as the user slides the seek bar). Delayed restart runs on main thread.
        if (mRestartTask == null) {
            mRestartTask = new Runnable() {
                @Override
                public void run() {
                    try {
                        stop();
                        start();
                    } catch (Utils.ApplicationError e) {
                        Log.addEntry(LOG_TAG, "failed restart engine after preference change");
                    }
                }
            };
        } else {
            mHandler.removeCallbacks(mRestartTask);
        }
        mHandler.postDelayed(mRestartTask, 5000);
    }

    public synchronized void submitTask(Runnable task) {
        mTaskThreadPool.submit(task);
    }
    
    private void startSharingService() throws Utils.ApplicationError {
        try {
            Data.Self self = Data.getInstance().getSelf();
            ArrayList<String> friendCertificates = new ArrayList<String>();
            for (Data.Friend friend : Data.getInstance().getFriends()) {
                friendCertificates.add(friend.mPublicIdentity.mX509Certificate);
            }
            stopSharingService();
            mWebServer = new WebServer(
                    this,
                    new X509.KeyMaterial(self.mPublicIdentity.mX509Certificate, self.mPrivateIdentity.mX509PrivateKey),
                    friendCertificates);
            mWebServer.start();
            mTorWrapper = new TorWrapper(
                    TorWrapper.Mode.MODE_RUN_SERVICES,
                    new HiddenService.KeyMaterial(self.mPublicIdentity.mHiddenServiceHostname, self.mPrivateIdentity.mHiddenServicePrivateKey),
                    mWebServer.getListeningPort());
            // TODO: poll mTorWrapper.awaitStarted() to check for errors and retry... 
            mTorWrapper.start();
        } catch (IOException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }
    
    private void stopSharingService() {
        if (mTorWrapper != null) {
            mTorWrapper.stop();
        }
        if (mWebServer != null) {
            mWebServer.stop();
        }
    }
    
    public synchronized int getTorSocksProxyPort() throws Utils.ApplicationError {
        if (mTorWrapper != null) {
            return mTorWrapper.getSocksProxyPort();
        }
        throw new Utils.ApplicationError(LOG_TAG, "no Tor socks proxy");
    }
    
    @Subscribe
    public synchronized void onUpdatedSelf(Events.UpdatedSelf updatedSelf) {
        // Apply new transport and hidden service credentials
        try {
            startSharingService();
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed restart sharing service after self updated");
        }                        
    }

    @Subscribe
    public synchronized void onNewSelfLocation(Events.NewSelfLocation newSelfLocation) {
        // TODO: location fix timestamp vs. status update timestamp?
        // TODO: apply precision factor to long/lat/address
        // TODO: factor Location.getAccuracy() into precision?
        try {
            StringBuilder address = new StringBuilder();
            if (newSelfLocation.mAddress != null) {
                for (int i = 0; i < newSelfLocation.mAddress.getMaxAddressLineIndex(); i++) {
                    // TODO: internationalization
                    if (i > 0) address.append(", ");
                    address.append(newSelfLocation.mAddress.getAddressLine(i));
                }
            }
            Data.getInstance().updateSelfStatus(
                    new Data.Status(
                            new Date(),
                            newSelfLocation.mLocation.getLatitude(),
                            newSelfLocation.mLocation.getLongitude(),
                            getIntPreference(R.string.preferenceLocationPrecisionInMeters),
                            address.toString()));
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to update self status with new location");
        }
    }
    
    @Subscribe
    public synchronized void onUpdatedSelfStatus(Events.UpdatedSelfStatus updatedSelfStatus) {
        try {
            // Immediately push new status to all friends. If this fails for any reason,
            // implicitly fall back to friends pulling status.
            pushToFriends();
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed push to friends after self status updated");
        }
    }
    
    @Subscribe
    public synchronized void AddedFriend(Events.AddedFriend addedFriend) {
        try {
            schedulePullFriend(addedFriend.mId);
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to schedule pull for added friend");
        }
    }
    
    @Subscribe
    public synchronized void RemovedFriend(Events.RemovedFriend removedFriend) {
        cancelPullFriend(removedFriend.mId);
    }
    
    private void pushToFriends() throws Utils.ApplicationError {
        // TODO: check for existing pushes in worker thread queue
        if (!currentlySharingLocation()) {
            return;
        }
        for (Data.Friend friend : Data.getInstance().getFriends()) {
            final String taskFriendId = friend.mId;
            Runnable task = new Runnable() {
                public void run() {
                    try {
                        Data data = Data.getInstance();
                        Data.Self self = data.getSelf();
                        Data.Status selfStatus = data.getSelfStatus();
                        Data.Friend friend = data.getFriendById(taskFriendId);
                        Log.addEntry(LOG_TAG, "make push status request to: " + friend.mPublicIdentity.mNickname);
                        WebClient.makePostRequest(
                                new X509.KeyMaterial(self.mPublicIdentity.mX509Certificate, self.mPrivateIdentity.mX509PrivateKey),
                                friend.mPublicIdentity.mX509Certificate,
                                getTorSocksProxyPort(),
                                friend.mPublicIdentity.getHiddenServiceHostnameUri(),
                                Protocol.WEB_SERVER_VIRTUAL_PORT,
                                Protocol.PUSH_STATUS_REQUEST_PATH,
                                Json.toJson(selfStatus));
                        data.updateFriendLastSentStatusTimestamp(taskFriendId);
                    } catch (Data.DataNotFoundError e) {
                        // Friend was deleted while push was enqueued. Ignore error.
                    } catch (Utils.ApplicationError e) {
                        Log.addEntry(LOG_TAG, "failed to push to friend");
                    }
                }
            };
            submitTask(task);
        }
    }

    private void cancelPullFriend(String friendId) {
        // Cancel any existing pull schedule for this friend
        if (mFriendPullTasks.containsKey(friendId)) {
            mFriendPullTasks.get(friendId).cancel(false);
        }
    }
    
    private void schedulePullFriend(String friendId) throws Utils.ApplicationError {
        final String finalFriendId = friendId;
        Runnable task = new Runnable() {
            public void run() {
                try {
                    Data data = Data.getInstance();
                    Data.Self self = data.getSelf();
                    Data.Friend friend = data.getFriendById(finalFriendId);
                    Log.addEntry(LOG_TAG, "make pull status request to: " + friend.mPublicIdentity.mNickname);
                    String response = WebClient.makeGetRequest(
                            new X509.KeyMaterial(self.mPublicIdentity.mX509Certificate, self.mPrivateIdentity.mX509PrivateKey),
                            friend.mPublicIdentity.mX509Certificate,
                            getTorSocksProxyPort(),
                            friend.mPublicIdentity.getHiddenServiceHostnameUri(),
                            Protocol.WEB_SERVER_VIRTUAL_PORT,
                            Protocol.PULL_STATUS_REQUEST_PATH);
                    Data.Status friendStatus = Json.fromJson(response, Data.Status.class);
                    data.updateFriendStatus(finalFriendId, friendStatus);
                    data.updateFriendLastReceivedStatusTimestamp(finalFriendId);
                } catch (Data.DataNotFoundError e) {
                    // Friend was deleted while pull was enqueued. Ignore error.
                    // RemovedFriend should eventually cancel schedule.
                } catch (Utils.ApplicationError e) {
                    Log.addEntry(LOG_TAG, "failed to pull from friend");
                }
            }
        };
        cancelPullFriend(friendId);
        // TODO: scheduleAtFixedRate has backlog issue
        ScheduledFuture<?> future = mTaskThreadPool.scheduleWithFixedDelay(
                task, 0, getIntPreference(R.string.preferenceLocationPullFrequencyInMinutes)*60*1000, TimeUnit.MILLISECONDS);
        mFriendPullTasks.put(friendId, future);
    }

    private void schedulePullFriends() throws Utils.ApplicationError {
        for (Data.Friend friend : Data.getInstance().getFriends()) {
            schedulePullFriend(friend.mId);
        }
    }
    
    public synchronized Data.Status handlePullStatusRequest(String friendCertificate) throws Utils.ApplicationError {
        // Friend is requesting (pulling) self status
        if (!currentlySharingLocation()) {
            return null;
        }
        // TODO: cancel any pending push to this friend?
        Data data = Data.getInstance();
        Data.Friend friend = data.getFriendByCertificate(friendCertificate);
        Data.Status status = data.getSelfStatus();
        // TODO: we don't yet know the friend really received the response bytes
        data.updateFriendLastSentStatusTimestamp(friend.mId);
        Log.addEntry(LOG_TAG, "served pull status request for: " + friend.mPublicIdentity.mNickname);
        return status;        
    }
    
    public synchronized void handlePushStatusRequest(String friendCertificate, Data.Status status) throws Utils.ApplicationError  {
        // Friend is pushing their own status
        Data data = Data.getInstance();
        Data.Friend friend = data.getFriendByCertificate(friendCertificate);
        data.updateFriendStatus(friend.mId, status);
        // TODO: we don't yet know the friend really received the response bytes
        data.updateFriendLastReceivedStatusTimestamp(friend.mId);        
        // Reschedule (delay) any outstanding pull from this friend
        schedulePullFriend(friend.mId);
        Log.addEntry(LOG_TAG, "served push status request for: " + friend.mPublicIdentity.mNickname);
    }
    
    public synchronized Context getContext() {
        return mContext;
    }
    
    public synchronized boolean getBooleanPreference(int keyResID) throws Utils.ApplicationError {
        String key = mContext.getString(keyResID);
        // Defaults which are "false" are not present in the preferences file
        // if (!mSharedPreferences.contains(key)) {...}
        // TODO: this is ambiguous: there's now no test for failure to initialize defaults
        return mSharedPreferences.getBoolean(key, false);        
    }
    
    public synchronized int getIntPreference(int keyResID) throws Utils.ApplicationError {
        String key = mContext.getString(keyResID);
        if (!mSharedPreferences.contains(key)) {
            throw new Utils.ApplicationError(LOG_TAG, "missing preference default: " + key);
        }
        return mSharedPreferences.getInt(key, 0);        
    }

    public synchronized boolean currentlySharingLocation() throws Utils.ApplicationError {
        if (!getBooleanPreference(R.string.preferenceAutomaticLocationSharing)) {
            return false;
        }
        
        Calendar now = Calendar.getInstance();
        
        if (getBooleanPreference(R.string.preferenceLimitLocationSharingTime)) {
            int currentHour = now.get(Calendar.HOUR_OF_DAY);
            int currentMinute = now.get(Calendar.MINUTE);
            
            String sharingTimeNotBefore = mSharedPreferences.getString(
                    mContext.getString(R.string.preferenceLimitLocationSharingTimeNotBefore), "");
            int notBeforeHour = TimePickerPreference.getHour(sharingTimeNotBefore);
            int notBeforeMinute = TimePickerPreference.getMinute(sharingTimeNotBefore);
            String sharingTimeNotAfter = mSharedPreferences.getString(
                    mContext.getString(R.string.preferenceLimitLocationSharingTimeNotAfter), "");
            int notAfterHour = TimePickerPreference.getHour(sharingTimeNotAfter);
            int notAfterMinute = TimePickerPreference.getMinute(sharingTimeNotAfter);

            if ((currentHour < notBeforeHour) ||
                (currentHour == notBeforeHour && currentMinute < notBeforeMinute) ||
                (currentHour > notAfterHour) ||
                (currentHour == notAfterHour && currentMinute > notAfterMinute)) {
                return false;
            }
        }

        // Map current Calendar.DAY_OF_WEEK (1..7) to preference's SUNDAY..SATURDAY symbols
        assert(Calendar.SUNDAY == 1 && Calendar.SATURDAY == 7);
        String[] weekdays = mContext.getResources().getStringArray(R.array.weekdays);
        String currentWeekday = weekdays[now.get(Calendar.DAY_OF_WEEK) - 1];

        Set<String> sharingDays = mSharedPreferences.getStringSet(
                mContext.getString(R.string.preferenceLimitLocationSharingDay),
                new HashSet<String>());
    
        if (!sharingDays.contains(currentWeekday)) {
            return false;
        }

        return true;
    }
}
