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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Pair;
import ca.psiphon.ploggy.widgets.TimePickerPreference;

import com.squareup.otto.Subscribe;

/**
 * Coordinator for background Ploggy work.
 *
 * The Engine:
 * - schedules friend status push/pulls
 * - schedules friend resource downloads
 * - maintains a worker thread pool for background tasks (pushing/pulling
 *   friends and handling friend requests
 * - runs the local location monitor
 * - (re)-starts and stops the local web server and Tor Hidden Service to
 *   handle requests from friends
 *
 * An Engine instance is intended to be run via an Android Service set to
 * foreground mode (i.e., long running).
 */

/*

*IN PROGRESS*

- double-check Protocol.validate() called where required in Engine
- pull more fixes from NanoHttpd upstream
- Review all "*TODO*" comments

*/

public class Engine implements OnSharedPreferenceChangeListener, WebServer.RequestHandler {

    private static final String LOG_TAG = "Engine";

    public static final String DEFAULT_PLOGGY_INSTANCE_NAME = "ploggy";

    private final String mInstanceName;
    private final Context mContext;
    private final Data mData;
    private final Handler mHandler;
    private final SharedPreferences mSharedPreferences;
    private boolean mStopped;
    private Runnable mPreferencesRestartTask;
    private Runnable mTorTimeoutRestartTask;
    private ScheduledExecutorService mTaskThreadPool;
    private ExecutorService mWebServerRequestThreadPool;
    private Map<Pair<String, FriendTaskType>, FriendTaskState> mFriendTaskStates;
    private Set<String> mLocationRecipients;
    private LocationFixer mLocationFixer;
    private WebServer mWebServer;
    private TorWrapper mTorWrapper;
    private long mTorCircuitEstablishedTime;
    private WebClientConnectionPool mWebClientConnectionPool;

    private enum FriendTaskType {ASK_LOCATION, REPORT_LOCATION, SYNC, DOWNLOAD};
    private static class FriendTaskState {
        public Runnable mTaskInstance = null;
        public Future<?> mScheduledTask = null;
        public long mBackoff = REQUEST_RETRY_BASE_FREQUENCY_IN_MILLISECONDS;
    }

    private static final long TOR_TIMEOUT_RESTART_IF_NOT_CONNECTED_IN_MILLISECONDS = TimeUnit.MINUTES.convert(5, TimeUnit.MILLISECONDS);
    private static final long TOR_TIMEOUT_RESTART_IF_NO_COMMUNICATION_IN_MILLISECONDS = TimeUnit.HOURS.convert(2, TimeUnit.MILLISECONDS);

    private static final long PREFERENCE_CHANGE_RESTART_DELAY_IN_MILLISECONDS = TimeUnit.SECONDS.convert(5, TimeUnit.MILLISECONDS);

    private static final long REQUEST_RETRY_BASE_FREQUENCY_IN_MILLISECONDS = TimeUnit.SECONDS.convert(30, TimeUnit.MILLISECONDS);
    private static final long REQUEST_RETRY_BACKOFF_FACTOR = 2;

    private static final int THREAD_POOL_SIZE = 30;

    // POST_CIRCUIT_REQUEST_DELAY is intended to compensate for
    // peer hidden service publish latency. Use this when scheduling requests
    // unless in response to a received peer communication (so, use it on
    // start up, or when a friend is added, for example).
    private static final long POST_CIRCUIT_REQUEST_DELAY_IN_NANOSECONDS = TimeUnit.SECONDS.convert(30, TimeUnit.NANOSECONDS);

    public Engine() {
        this(Engine.DEFAULT_PLOGGY_INSTANCE_NAME);
    }

    public Engine(String instanceName) {
        Utils.initSecureRandom();
        mContext = Utils.getApplicationContext();
        mInstanceName = instanceName;
        mData = Data.getInstance(mInstanceName);
        mHandler = new Handler();
        // TODO: distinct instance of preferences for each persona
        // e.g., getSharedPreferencesName("persona1");
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mStopped = true;
    }

    private String logTag() {
        return String.format("%s [%s]", LOG_TAG, mInstanceName);
    }

    public synchronized void start() throws PloggyError {
        if (!mStopped) {
            stop();
        }
        mStopped = false;
        Log.addEntry(logTag(), "starting...");
        Events.getInstance(mInstanceName).register(this);
        mTaskThreadPool = Executors.newScheduledThreadPool(THREAD_POOL_SIZE);
        // Using a distinct worker thread pool and queue to manage peer
        // requests, so local tasks are not blocked by peer actions.
        mWebServerRequestThreadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        mFriendTaskStates = new HashMap<Pair<String, FriendTaskType>, FriendTaskState>();
        mLocationRecipients = new HashSet<String>();
        mLocationFixer = new LocationFixer(this);
        mLocationFixer.start();
        startHiddenService();
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        setTorTimeout(TOR_TIMEOUT_RESTART_IF_NOT_CONNECTED_IN_MILLISECONDS);
        Log.addEntry(logTag(), "started");
    }

    public synchronized void stop() {
        Log.addEntry(logTag(), "stopping...");
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        Events.getInstance(mInstanceName).unregister(this);
        cancelTorTimeout();
        if (mLocationFixer != null) {
            mLocationFixer.stop();
            mLocationFixer = null;
        }
        if (mFriendTaskStates != null) {
            mFriendTaskStates.clear();
            mFriendTaskStates = null;
        }
        if (mTaskThreadPool != null) {
            Utils.shutdownExecutorService(mTaskThreadPool);
            mTaskThreadPool = null;
        }
        if (mWebServerRequestThreadPool != null) {
            Utils.shutdownExecutorService(mWebServerRequestThreadPool);
            mWebServerRequestThreadPool = null;
        }
        stopWebClientConnectionPool();
        stopHiddenService();
        mStopped = true;
        Log.addEntry(logTag(), "stopped");
    }

    @Override
    public synchronized void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Restart engine to apply changed preferences. Delay restart until user inputs are idle.
        // (This idle delay is important due to how SeekBarPreferences trigger onSharedPreferenceChanged
        // continuously as the user slides the seek bar). Delayed restart runs on main thread.
        if (mPreferencesRestartTask == null) {
            mPreferencesRestartTask = new Runnable() {
                @Override
                public void run() {
                    try {
                        stop();
                        start();
                    } catch (PloggyError e) {
                        Log.addEntry(logTag(), "failed to restart engine after preference change");
                    }
                }
            };
        } else {
            mHandler.removeCallbacks(mPreferencesRestartTask);
        }
        mHandler.postDelayed(mPreferencesRestartTask, PREFERENCE_CHANGE_RESTART_DELAY_IN_MILLISECONDS);
    }

    private void setTorTimeout(long milliseconds) {
        if (mTorTimeoutRestartTask == null) {
            mTorTimeoutRestartTask = new Runnable() {
                @Override
                public void run() {
                    try {
                        stop();
                        start();
                    } catch (PloggyError e) {
                        Log.addEntry(logTag(), "failed to restart engine after Tor timeout");
                    }
                }
            };
        } else {
            mHandler.removeCallbacks(mTorTimeoutRestartTask);
        }
        mHandler.postDelayed(mTorTimeoutRestartTask, milliseconds);
    }

    private void cancelTorTimeout() {
        if (mTorTimeoutRestartTask != null) {
            mHandler.removeCallbacks(mTorTimeoutRestartTask);
            mTorTimeoutRestartTask = null;
        }
    }

    @Subscribe
    public synchronized void onTorCircuitEstablished(Events.TorCircuitEstablished torCircuitEstablished) {
        try {
            setTorTimeout(TOR_TIMEOUT_RESTART_IF_NO_COMMUNICATION_IN_MILLISECONDS);
            mTorCircuitEstablishedTime = System.nanoTime();
            startWebClientConnectionPool();
            syncWithFriends();
            downloadFromFriends();
        } catch (PloggyError e) {
            Log.addEntry(logTag(), "failed to start friend poll after Tor circuit established");
        }
    }

    private long getPostCircuitDelayInMilliseconds() {
        long delay = POST_CIRCUIT_REQUEST_DELAY_IN_NANOSECONDS - (System.nanoTime() - mTorCircuitEstablishedTime);
        if (delay < 0) {
            return 0;
        }
        return TimeUnit.MILLISECONDS.convert(delay, TimeUnit.NANOSECONDS);
    }

    @Subscribe
    public synchronized void onUpdatedSelf(Events.UpdatedSelf updatedSelf) {
        // Apply new transport and hidden service credentials
        try {
            start();
        } catch (PloggyError e) {
            Log.addEntry(logTag(), "failed to restart hidden service after self updated");
        }
    }

    @Subscribe
    public synchronized void onAddedFriend(Events.AddedFriend addedFriend) {
        // Apply new set of friends to web server and pull schedule.
        // Friend poll will be started after Tor circuit is established.
        // TODO: don't need to restart Tor, just web server
        // (now need to restart Tor due to Hidden Service auth; but could use control interface instead?)
        try {
            start();
        } catch (PloggyError e) {
            Log.addEntry(logTag(), "failed restart sharing service after added friend");
        }
    }

    @Subscribe
    public synchronized void onUpdatedFriend(Events.UpdatedFriend updatedFriend) {
        // Update implies communication sent/received, so extend the restart the timeout
        setTorTimeout(TOR_TIMEOUT_RESTART_IF_NO_COMMUNICATION_IN_MILLISECONDS);
    }

    @Subscribe
    public synchronized void onRemovedFriend(Events.RemovedFriend removedFriend) {
        // Full stop/start to clear friend task cache
        try {
            start();
        } catch (PloggyError e) {
            Log.addEntry(logTag(), "failed restart sharing service after removed friend");
        }
    }

    @Subscribe
    public synchronized void onNewSelfLocationFix(Events.NewSelfLocationFix newSelfLocation) {
        try {
            String streetAddress;
            if (newSelfLocation.mAddress != null) {
                streetAddress = newSelfLocation.mAddress.toString();
            } else {
                streetAddress = "";
            }
            mData.putSelfLocation(
                    new Protocol.Location(
                            new Date(),
                            newSelfLocation.mLocation.getLatitude(),
                            newSelfLocation.mLocation.getLongitude(),
                            streetAddress));
        } catch (PloggyError e) {
            Log.addEntry(logTag(), "failed to update self status with new location");
        }
    }

    @Subscribe
    public synchronized void onUpdatedSelfGroup(Events.UpdatedSelfGroup updatedSelfGroup) {
        try {
            syncWithMembers(mData.getGroupOrThrow(updatedSelfGroup.mGroupId).mGroup);
        } catch (PloggyError e) {
            Log.addEntry(logTag(), "failed push to friends after self group update");
        }
    }

    @Subscribe
    public synchronized void onUpdatedSelfLocation(Events.UpdatedSelfLocation updatedSelfLocation) {
        try {
            reportLocationToFriends();
        } catch (PloggyError e) {
            Log.addEntry(logTag(), "failed push to friends after self location update");
        }
    }

    @Subscribe
    public synchronized void onUpdatedSelfPost(Events.UpdatedSelfPost updatedSelfPost) {
        try {
            syncWithMembers(mData.getPostOrThrow(updatedSelfPost.mPostId).mPost);
        } catch (PloggyError e) {
            Log.addEntry(logTag(), "failed push to friends after self post update");
        }
    }

    @Subscribe
    public synchronized void onAddedDownload(Events.AddedDownload addedDownload) {
        // Schedule immediate download, if not already downloading from friend
        triggerFriendTask(addedDownload.mFriendId, FriendTaskType.DOWNLOAD, 0);
    }

    private void startHiddenService() throws PloggyError {
        stopHiddenService();

        Data.Self self = mData.getSelfOrThrow();
        List<String> friendCertificates = new ArrayList<String>();
        for (Data.Friend friend : mData.getFriendsIterator()) {
            friendCertificates.add(friend.mPublicIdentity.mX509Certificate);
        }
        mWebServer = new WebServer(
                mData,
                this,
                new X509.KeyMaterial(self.mPublicIdentity.mX509Certificate, self.mPrivateIdentity.mX509PrivateKey),
                friendCertificates);
        try {
            mWebServer.start();
        } catch (IOException e) {
            throw new PloggyError(logTag(), e);
        }

        List<TorWrapper.HiddenServiceAuth> hiddenServiceAuths = new ArrayList<TorWrapper.HiddenServiceAuth>();
        for (Data.Friend friend : mData.getFriendsIterator()) {
            hiddenServiceAuths.add(
                    new TorWrapper.HiddenServiceAuth(
                            friend.mPublicIdentity.mHiddenServiceHostname,
                            friend.mPublicIdentity.mHiddenServiceAuthCookie));
        }
        mTorWrapper = new TorWrapper(
                mInstanceName,
                TorWrapper.Mode.MODE_RUN_SERVICES,
                hiddenServiceAuths,
                new HiddenService.KeyMaterial(
                        self.mPublicIdentity.mHiddenServiceHostname,
                        self.mPublicIdentity.mHiddenServiceAuthCookie,
                        self.mPrivateIdentity.mHiddenServicePrivateKey),
                mWebServer.getListeningPort());
        // TODO: in a background thread, monitor mTorWrapper.awaitStarted() to check for errors and retry...
        mTorWrapper.start();
        // Note: startFriendPoll is deferred until onTorCircuitEstablished
    }

    private void stopHiddenService() {
        if (mTorWrapper != null) {
            mTorWrapper.stop();
        }
        if (mWebServer != null) {
            mWebServer.stop();
        }
    }

    public synchronized int getTorSocksProxyPort() throws PloggyError {
        if (mTorWrapper != null) {
            return mTorWrapper.getSocksProxyPort();
        }
        throw new PloggyError(logTag(), "no Tor socks proxy");
    }

    private void startWebClientConnectionPool() throws PloggyError {
        stopWebClientConnectionPool();
        mWebClientConnectionPool = new WebClientConnectionPool(mData, getTorSocksProxyPort());
    }

    private void stopWebClientConnectionPool() {
        if (mWebClientConnectionPool != null) {
            mWebClientConnectionPool.shutdown();
            mWebClientConnectionPool = null;
        }
    }

    public synchronized Future<?> submitTask(Runnable task, long delayInMilliseconds) {
        if (mTaskThreadPool != null) {
            return mTaskThreadPool.schedule(task, delayInMilliseconds, TimeUnit.MILLISECONDS);
        }
        return null;
    }

    @Override
    public synchronized void submitWebRequestTask(Runnable task) {
        if (mWebServerRequestThreadPool != null) {
            mWebServerRequestThreadPool.submit(task);
        }
    }

    private synchronized void addFriendToReceiveLocation(String friendId) throws PloggyError {
        mLocationRecipients.add(friendId);
        mLocationFixer.start();
    }

    public void askLocationFromFriend(String friendId) throws PloggyError {
        triggerFriendTask(friendId, FriendTaskType.ASK_LOCATION, 0);
    }

    public void reportLocationToFriends() throws PloggyError {
        for (String friendId : mLocationRecipients) {
            triggerFriendTask(friendId, FriendTaskType.REPORT_LOCATION, 0);
            mLocationRecipients.clear();
        }
    }

    private void syncWithFriends() throws PloggyError {
        for (Data.Friend friend : mData.getFriendsIterator()) {
            triggerFriendTask(friend.mId, FriendTaskType.SYNC, 0);
        }
    }

    private void downloadFromFriends() throws PloggyError {
        for (Data.Friend friend : mData.getFriendsIterator()) {
            triggerFriendTask(friend.mId, FriendTaskType.DOWNLOAD, 0);
        }
    }

    private void syncWithMembers(Protocol.Post post) throws PloggyError {
        syncWithMembers(mData.getGroupOrThrow(post.mGroupId).mGroup);
    }

    private void syncWithMembers(Protocol.Group group) throws PloggyError {
        for (Identity.PublicIdentity member : group.mMembers) {
            triggerFriendTask(member.mId, FriendTaskType.SYNC, 0);
        }
    }

    // Note: should be called from synchronized member function -- for mFriendTaskStates manipulation
    private FriendTaskState getFriendTaskState(String friendId, FriendTaskType taskType) {
        Pair<String, FriendTaskType> stateKey = new Pair<String, FriendTaskType>(friendId, taskType);
        FriendTaskState state = mFriendTaskStates.get(stateKey);
        if (state == null) {
            state = new FriendTaskState();
            mFriendTaskStates.put(stateKey, state);
        }
        return state;
    }

    private synchronized void triggerFriendTask(
            String friendId, FriendTaskType taskType, long delayInMilliseconds) {
        // Schedules one sync/download per friend at a time.
        // Cache instantiated task functions
        FriendTaskState state = getFriendTaskState(friendId, taskType);
        if (state.mTaskInstance == null) {
            switch (taskType) {
            case ASK_LOCATION:
                state.mTaskInstance = makeAskLocationToFriendTask(friendId);
                break;
            case REPORT_LOCATION:
                state.mTaskInstance = makeReportLocationToFriendTask(friendId);
                break;
            case SYNC:
                state.mTaskInstance = makeSyncWithFriendTask(friendId);
                break;
            case DOWNLOAD:
                state.mTaskInstance = makeDownloadFromFriendTask(friendId);
                break;
            }
        }

        // If a Future is present, the task is in progress or in queue.
        // Try to cancel it and reschedule. When cancel fails, task is
        // running and not rescheduled.
        // *TODO* [no longer true for sync/download with retries] On completion, tasks remove their Futures from mFriendTaskFutures.
        // *TODO* *** race condition ***: in progress task can exit without e.g., pushing new data

        if (state.mScheduledTask != null) {
            if (state.mScheduledTask.cancel(false)) {
                state.mScheduledTask = null;
            }
        }

        // *TODO* assumes all taskType are Hidden Service requests
        long postCircuitDelay = getPostCircuitDelayInMilliseconds();
        if (delayInMilliseconds < postCircuitDelay) {
            delayInMilliseconds = postCircuitDelay;
        }

        if (state.mScheduledTask == null) {
            Log.addEntry(
                    logTag(),
                    "scheduled " + taskType.name() + " for " + friendId +
                    " in " + Long.toString(delayInMilliseconds) + "ms.");

            state.mScheduledTask = submitTask(state.mTaskInstance, delayInMilliseconds);
        }
    }

    private synchronized void completedFriendTask(String friendId, FriendTaskType taskType) {
        FriendTaskState state = getFriendTaskState(friendId, taskType);
        state.mScheduledTask = null;
    }

   private synchronized long getFriendBackoffInMillisecondsAndExtend(String friendId, FriendTaskType taskType) {
       FriendTaskState state = getFriendTaskState(friendId, taskType);
       long backoff = state.mBackoff;
       state.mBackoff *= REQUEST_RETRY_BACKOFF_FACTOR;
       return backoff;
    }

   private synchronized void resetFriendBackoff(String friendId, FriendTaskType taskType) {
       FriendTaskState state = getFriendTaskState(friendId, taskType);
       state.mBackoff = REQUEST_RETRY_BASE_FREQUENCY_IN_MILLISECONDS;
   }

    // TODO: refactor common code in makeTask functions?

    private Runnable makeAskLocationToFriendTask(String friendId) {
        final String finalFriendId = friendId;
        return new Runnable() {
            @Override
            public void run() {
                try {
                    if (!mTorWrapper.isCircuitEstablished()) {
                        return;
                    }
                    Data.Friend friend = mData.getFriendById(finalFriendId);
                    Log.addEntry(logTag(), "ask location to: " + friend.mPublicIdentity.mNickname);
                    WebClientRequest webClientRequest =
                            new WebClientRequest(
                                mWebClientConnectionPool,
                                friend.mPublicIdentity.mHiddenServiceHostname,
                                Protocol.WEB_SERVER_VIRTUAL_PORT,
                                WebClientRequest.RequestType.valueOf(Protocol.ASK_LOCATION_REQUEST_TYPE),
                                Protocol.ASK_LOCATION_REQUEST_PATH);
                    webClientRequest.makeRequest();
                } catch (Data.NotFoundError e) {
                    // Friend was deleted while task was enqueued. Ignore error.
                } catch (PloggyError e) {
                    try {
                        Log.addEntry(
                                logTag(),
                                "failed to ask location to: " +
                                    mData.getFriendByIdOrThrow(finalFriendId).mPublicIdentity.mNickname);
                    } catch (PloggyError e2) {
                        Log.addEntry(logTag(), "failed to ask location");
                    }
                } finally {
                    completedFriendTask(finalFriendId, FriendTaskType.ASK_LOCATION);
                }
            }
        };
    }

    private Runnable makeReportLocationToFriendTask(String friendId) {
        final String finalFriendId = friendId;
        return new Runnable() {
            @Override
            public void run() {
                try {
                    if (!mTorWrapper.isCircuitEstablished()) {
                        return;
                    }
                    Data.Friend friend = mData.getFriendById(finalFriendId);
                    Protocol.Location location = mData.getSelfLocation();
                    Log.addEntry(logTag(), "report location to: " + friend.mPublicIdentity.mNickname);
                    WebClientRequest webClientRequest =
                            new WebClientRequest(
                                mWebClientConnectionPool,
                                friend.mPublicIdentity.mHiddenServiceHostname,
                                Protocol.WEB_SERVER_VIRTUAL_PORT,
                                WebClientRequest.RequestType.valueOf(Protocol.REPORT_LOCATION_REQUEST_TYPE),
                                Protocol.REPORT_LOCATION_REQUEST_PATH).
                                    requestBody(Json.toJson(location));
                    webClientRequest.makeRequest();
                } catch (Data.NotFoundError e) {
                    // Friend was deleted while task was enqueued, or no location to report. Ignore error.
                } catch (PloggyError e) {
                    try {
                        Log.addEntry(
                                logTag(),
                                "failed to report location to: " +
                                    mData.getFriendByIdOrThrow(finalFriendId).mPublicIdentity.mNickname);
                    } catch (PloggyError e2) {
                        Log.addEntry(logTag(), "failed to report location");
                    }
                } finally {
                    completedFriendTask(finalFriendId, FriendTaskType.REPORT_LOCATION);
                }
            }
        };
    }

    private Runnable makeSyncWithFriendTask(String friendId) {
        final String finalFriendId = friendId;
        return new Runnable() {
            @Override
            public void run() {
                try {
                    // Pull until no payload data is received. Each subsequent pull
                    // explicitly acknowledges the received data via the last received
                    // sequence numbers passed in the previous pull request. So typically,
                    // two pull requests are executed.
                    resetFriendBackoff(finalFriendId, FriendTaskType.SYNC);
                    while (true) {
                        if (!mTorWrapper.isCircuitEstablished()) {
                            break;
                        }
                        Data.Friend friend = mData.getFriendById(finalFriendId);
                        Log.addEntry(logTag(), "sync with: " + friend.mPublicIdentity.mNickname);

                        final Protocol.SyncState finalSyncState = mData.getSyncState(finalFriendId);
                        final AtomicBoolean responseContainsNewData = new AtomicBoolean(false);
                        WebClientRequest.ResponseBodyHandler responseBodyHandler = new WebClientRequest.ResponseBodyHandler() {
                            @Override
                            public void consume(InputStream responseBodyInputStream) throws PloggyError {
                                Protocol.SyncState syncState = finalSyncState;
                                Set<String> needSyncFriendIds = new HashSet<String>();
                                List<Protocol.Group> groups = new ArrayList<Protocol.Group>();
                                List<Protocol.Post> posts = new ArrayList<Protocol.Post>();
                                Json.PayloadIterator payloadIterator = new Json.PayloadIterator(responseBodyInputStream);
                                // TODO: polymorphism instead of cases-for-types?
                                for (Protocol.Payload payload : payloadIterator) {
                                    switch(payload.mType) {
                                    case GROUP:
                                        Protocol.Group group = (Protocol.Group)payload.mObject;
                                        Protocol.validateGroup(group);
                                        groups.add(group);
                                        responseContainsNewData.set(true);
                                        break;
                                    case POST:
                                        Protocol.Post post = (Protocol.Post)payload.mObject;
                                        Protocol.validatePost(post);
                                        posts.add(post);
                                        responseContainsNewData.set(true);
                                        break;
                                    default:
                                        break;
                                    }
                                    if (groups.size() + posts.size() >= Data.MAX_SYNC_RESPONSE_TRANSACTION_OBJECT_COUNT) {
                                        mData.putSyncResponse(finalFriendId, syncState, groups, posts, needSyncFriendIds);
                                        syncState = null;
                                        groups.clear();
                                        posts.clear();
                                    }
                                }
                                mData.putSyncResponse(finalFriendId, syncState, groups, posts, needSyncFriendIds);
                                // Trigger sync for set of friends determined by syncResponse content. This potentially
                                // includes the request peer, based on offered sequence numbers; as well as group members
                                // for newly discovered groups.
                                for (String friendId : needSyncFriendIds) {
                                    triggerFriendTask(friendId, FriendTaskType.SYNC, 0);
                                }
                            }
                        };

                        WebClientRequest webClientRequest =
                                new WebClientRequest(
                                    mWebClientConnectionPool,
                                    friend.mPublicIdentity.mHiddenServiceHostname,
                                    Protocol.WEB_SERVER_VIRTUAL_PORT,
                                    WebClientRequest.RequestType.valueOf(Protocol.SYNC_REQUEST_TYPE),
                                    Protocol.SYNC_REQUEST_PATH).
                                        requestBody(Json.toJson(finalSyncState)).
                                        responseBodyHandler(responseBodyHandler);
                        webClientRequest.makeRequest();

                        // Keep going if sync returned data. We could also keep going if there's
                        // more data to push, but the peer now knows what this client is offering
                        // and will make its own sync request.
                        if (!responseContainsNewData.get()) {
                            break;
                        }
                    }
                } catch (Data.NotFoundError e) {
                    // Friend was deleted while task was enqueued. Ignore error.
                    // RemovedFriend should eventually cancel schedule.
                } catch (PloggyError e) {
                    try {
                        Log.addEntry(
                                logTag(),
                                "failed to sync with: " +
                                    mData.getFriendByIdOrThrow(finalFriendId).mPublicIdentity.mNickname);
                    } catch (PloggyError e2) {
                        Log.addEntry(logTag(), "failed to sync");
                    }
                } finally {
                    completedFriendTask(finalFriendId, FriendTaskType.SYNC);
                }

                long delay = getFriendBackoffInMillisecondsAndExtend(finalFriendId, FriendTaskType.SYNC);
                triggerFriendTask(finalFriendId, FriendTaskType.SYNC, delay);
            }
        };
    }

    private Runnable makeDownloadFromFriendTask(String friendId) {
        final String finalFriendId = friendId;
        return new Runnable() {
            @Override
            public void run() {
                try {
                    resetFriendBackoff(finalFriendId, FriendTaskType.DOWNLOAD);
                    while (true) {
                        if (!mTorWrapper.isCircuitEstablished()) {
                            break;
                        }
                        if (getBooleanPreference(R.string.preferenceExchangeFilesWifiOnly)
                                && !Utils.isConnectedNetworkWifi(mContext)) {
                            // Will retry after next delay period
                            break;
                        }
                        Data.Friend friend = mData.getFriendById(finalFriendId);
                        Data.Download download = null;
                        try {
                            download = mData.getNextInProgressDownload(finalFriendId);
                        } catch (Data.NotFoundError e) {
                            break;
                        }
                        // TODO: there's a potential race condition between getDownloadedSize and
                        // openDownloadResourceForAppending; we may want to lock the file first.
                        // However: currently only one thread downloads files for a given friend.
                        long downloadedSize = Downloads.getDownloadedSize(download);
                        if (downloadedSize == download.mSize) {
                            // Already downloaded complete file, but may have failed to commit
                            // the COMPLETED state change. Skip the download.
                        } else {
                            Log.addEntry(logTag(), "download from: " + friend.mPublicIdentity.mNickname);
                            List<Pair<String, String>> requestParameters =
                                    Arrays.asList(new Pair<String, String>(Protocol.DOWNLOAD_REQUEST_RESOURCE_ID_PARAMETER, download.mResourceId));
                            Pair<Long, Long> range = new Pair<Long, Long>(downloadedSize, (long)-1);
                            WebClientRequest webClientRequest =
                                    new WebClientRequest(
                                        mWebClientConnectionPool,
                                        friend.mPublicIdentity.mHiddenServiceHostname,
                                        Protocol.WEB_SERVER_VIRTUAL_PORT,
                                        WebClientRequest.RequestType.valueOf(Protocol.DOWNLOAD_REQUEST_TYPE),
                                        Protocol.DOWNLOAD_REQUEST_PATH).
                                            requestParameters(requestParameters).
                                            rangeHeader(range).
                                            responseBodyOutputStream(Downloads.openDownloadResourceForAppending(download));
                            webClientRequest.makeRequest();
                        }
                        mData.updateDownloadState(friend.mId, download.mResourceId, Data.Download.State.COMPLETE);
                        // TODO: WebClient post to event bus for download progress (replacing timer-based refreshes...)
                        // TODO: 404/403: denied by peer? -- change Download state to reflect this and don't retry (e.g., new state: CANCELLED)
                    }
                } catch (Data.NotFoundError e) {
                    // Friend was deleted while task was enqueued. Ignore error.
                    // RemovedFriend should eventually cancel schedule.
                } catch (PloggyError e) {
                    try {
                        Log.addEntry(
                                logTag(),
                                "failed to download from: " +
                                    mData.getFriendByIdOrThrow(finalFriendId).mPublicIdentity.mNickname);
                    } catch (PloggyError e2) {
                        Log.addEntry(logTag(), "failed to download");
                    }
                } finally {
                    completedFriendTask(finalFriendId, FriendTaskType.DOWNLOAD);
                }

                long delay = getFriendBackoffInMillisecondsAndExtend(finalFriendId, FriendTaskType.DOWNLOAD);
                triggerFriendTask(finalFriendId, FriendTaskType.DOWNLOAD, delay);
            }
        };
    }

    // Note: WebServer callbacks intentionally not synchronized -- for concurrent processing
    @Override
    public String getFriendNicknameByCertificate(String friendCertificate) throws PloggyError {
        Data.Friend friend = mData.getFriendByCertificateOrThrow(friendCertificate);
        return friend.mPublicIdentity.mNickname;
    }

    // Note: WebServer callbacks intentionally not synchronized -- for concurrent processing
    @Override
    public void updateFriendSent(String friendCertificate, Date lastSentToTimestamp, long additionalBytesSentTo)
            throws PloggyError  {
        Data.Friend friend = mData.getFriendByCertificateOrThrow(friendCertificate);
        mData.updateFriendSentOrThrow(friend.mId, lastSentToTimestamp, additionalBytesSentTo);
    }

    // Note: WebServer callbacks intentionally not synchronized -- for concurrent processing
    @Override
    public void updateFriendReceived(String friendCertificate, Date lastReceivedFromTimestamp, long additionalBytesReceivedFrom)
            throws PloggyError {
        Data.Friend friend = mData.getFriendByCertificateOrThrow(friendCertificate);
        mData.updateFriendReceivedOrThrow(friend.mId, lastReceivedFromTimestamp, additionalBytesReceivedFrom);
    }

    // Note: WebServer callbacks intentionally not synchronized -- for concurrent processing
    @Override
    public void handleAskLocationRequest(String friendCertificate) throws PloggyError {
        Data.Friend friend = mData.getFriendByCertificateOrThrow(friendCertificate);
        if (!currentlySharingLocation()) {
            throw new PloggyError(logTag(), "rejected ask location request for " + friend.mPublicIdentity.mNickname);
        }
        addFriendToReceiveLocation(friend.mId);
        Log.addEntry(logTag(), "served ask location request for " + friend.mPublicIdentity.mNickname);
    }

    // Note: WebServer callbacks intentionally not synchronized -- for concurrent processing
    @Override
    public void handleReportLocationRequest(String friendCertificate, String requestBody) throws PloggyError  {
        Data.Friend friend = mData.getFriendByCertificateOrThrow(friendCertificate);
        Protocol.Location location = Json.fromJson(requestBody, Protocol.Location.class);
        Protocol.validateLocation(location);
        mData.putPushedLocation(friend.mId, location);
        Log.addEntry(logTag(), "served report location request for " + friend.mPublicIdentity.mNickname);
    }

    // Note: WebServer callbacks intentionally not synchronized -- for concurrent processing
    @Override
    public WebServer.RequestHandler.SyncResponse handleSyncRequest(String friendCertificate, String requestBody) throws PloggyError {
        Data.Friend friend = mData.getFriendByCertificateOrThrow(friendCertificate);
        Protocol.SyncState requestSyncState = Json.fromJson(requestBody, Protocol.SyncState.class);
        Protocol.validateSyncState(requestSyncState);
        boolean needSync = mData.putSyncRequest(friend.mId, requestSyncState);
        Data.SyncPayloadIterator syncPayloadIterator = mData.getSyncPayload(friend.mId, requestSyncState);
        if (needSync) {
            triggerFriendTask(friend.mId, FriendTaskType.SYNC, 0);
        }
        // *TODO* log too noisy during regular operation?
        Log.addEntry(logTag(), "served sync request for " + friend.mPublicIdentity.mNickname);
        return new WebServer.RequestHandler.SyncResponse(new Utils.StringIteratorInputStream(syncPayloadIterator));
    }

    // Note: WebServer callbacks intentionally not synchronized -- for concurrent processing
    @Override
    public WebServer.RequestHandler.DownloadResponse handleDownloadRequest(
            String friendCertificate, String resourceId, Pair<Long, Long> range) throws PloggyError  {
        Data.Friend friend = mData.getFriendByCertificateOrThrow(friendCertificate);
        Data.LocalResource localResource = null;
        try {
            localResource = mData.getLocalResourceForDownload(friend.mId, resourceId);
        } catch (Data.NotFoundError e) {
            throw new PloggyError(logTag(), "local resource not found for download request");
        }
        // Note: don't check availability until after input validation
        if (getBooleanPreference(R.string.preferenceExchangeFilesWifiOnly)
                && !Utils.isConnectedNetworkWifi(mContext)) {
            // Download service not available
            return new DownloadResponse(false, null, null);
        }
        InputStream inputStream = Resources.openLocalResourceForReading(localResource, range);
        Log.addEntry(logTag(), "served download request for " + friend.mPublicIdentity.mNickname);
        return new DownloadResponse(true, localResource.mMimeType, inputStream);
    }

    public synchronized Context getContext() {
        return mContext;
    }

    public synchronized boolean getBooleanPreference(int keyResID) throws PloggyError {
        String key = mContext.getString(keyResID);
        // Defaults which are "false" are not present in the preferences file
        // if (!mSharedPreferences.contains(key)) {...}
        // TODO: this is ambiguous: there's now no test for failure to initialize defaults
        return mSharedPreferences.getBoolean(key, false);
    }

    public synchronized int getIntPreference(int keyResID) throws PloggyError {
        String key = mContext.getString(keyResID);
        if (!mSharedPreferences.contains(key)) {
            throw new PloggyError(logTag(), "missing preference default: " + key);
        }
        return mSharedPreferences.getInt(key, 0);
    }

    public synchronized boolean currentlySharingLocation() throws PloggyError {
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
