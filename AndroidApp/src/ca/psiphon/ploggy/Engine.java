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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

- call updateFriendSent/Recv byte counts in WebServer and WebClient
- add task to restart Engine/Tor if no circuit after N minutes; and restart if no comms after N hours(?)
- double-check Protocol.validate() called where required in Engine
- pull more fixes from NanoHttpd upstream
- Review all "*TODO*" comments

*/

public class Engine implements OnSharedPreferenceChangeListener, WebServer.RequestHandler {

    private static final String LOG_TAG = "Engine";

    private final String mInstanceName;
    private final Context mContext;
    private final Data mData;
    private final Handler mHandler;
    private final SharedPreferences mSharedPreferences;
    private boolean mStopped;
    private Runnable mPreferencesRestartTask;
    private Runnable mDownloadRetryTask;
    private ExecutorService mTaskThreadPool;
    private ExecutorService mPeerRequestThreadPool;
    enum FriendTaskType {ASK_PULL, ASK_LOCATION, PUSH_TO, PULL_FROM, DOWNLOAD_FROM};
    private Map<FriendTaskType, HashMap<String, Runnable>> mFriendTaskObjects;
    private Map<FriendTaskType, HashMap<String, Future<?>>> mFriendTaskFutures;
    private Map<String, ArrayList<Protocol.Payload>> mFriendPushQueue;
    private LocationMonitor mLocationMonitor;
    private WebServer mWebServer;
    private TorWrapper mTorWrapper;
    private WebClientConnectionPool mWebClientConnectionPool;

    private static final int PREFERENCE_CHANGE_RESTART_DELAY_IN_MILLISECONDS = 5*1000; // 5 sec.
    private static final int DOWNLOAD_RETRY_PERIOD_IN_MILLISECONDS = 10*60*1000; // 10 min.

    private static final int THREAD_POOL_SIZE = 30;

    // FRIEND_REQUEST_DELAY_IN_SECONDS is intended to compensate for
    // peer hidden service publish latency. Use this when scheduling requests
    // unless in response to a received peer communication (so, use it on
    // start up, or when a friend is added, for example).
    private static final int FRIEND_REQUEST_DELAY_IN_MILLISECONDS = 30*1000;

    private static final String DEFAULT_ENGINE_NAME = "ploggy";

    public Engine(Context context) {
        this(context, DEFAULT_ENGINE_NAME);
    }

    public Engine(Context context, String instanceName) {
        Utils.initSecureRandom();
        mContext = context;
        mInstanceName = instanceName;
        mData = Data.getInstance(mContext, mInstanceName);
        mHandler = new Handler();
        // TODO: distinct instance of preferences for each persona
        // e.g., getSharedPreferencesName("persona1");
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mStopped = true;
    }

    public synchronized void start() throws Utils.ApplicationError {
        if (!mStopped) {
            stop();
        }
        mStopped = false;
        Log.addEntry(LOG_TAG, "starting...");
        Events.getInstance(mInstanceName).register(this);
        mTaskThreadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        // Using a distinct worker thread pool and queue to manage peer
        // requests, so local tasks are not blocked by peer actions.
        mPeerRequestThreadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        mFriendTaskObjects = new EnumMap<FriendTaskType, HashMap<String, Runnable>>(FriendTaskType.class);
        mFriendTaskFutures = new EnumMap<FriendTaskType, HashMap<String, Future<?>>>(FriendTaskType.class);
        mFriendPushQueue = new HashMap<String, ArrayList<Protocol.Payload>>();
        mLocationMonitor = new LocationMonitor(this);
        mLocationMonitor.start();
        startHiddenService();
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        Log.addEntry(LOG_TAG, "started");
    }

    public synchronized void stop() {
        Log.addEntry(LOG_TAG, "stopping...");
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        Events.getInstance(mInstanceName).unregister(this);
        stopDownloadRetryTask();
        stopHiddenService();
        if (mLocationMonitor != null) {
            mLocationMonitor.stop();
            mLocationMonitor = null;
        }
        if (mFriendTaskObjects != null) {
            mFriendTaskObjects.clear();
            mFriendTaskObjects = null;
        }
        if (mFriendTaskFutures != null) {
            mFriendTaskFutures.clear();
            mFriendTaskFutures = null;
        }
        if (mFriendPushQueue != null) {
            mFriendPushQueue.clear();
            mFriendPushQueue = null;
        }
        if (mTaskThreadPool != null) {
            Utils.shutdownExecutorService(mTaskThreadPool);
            mTaskThreadPool = null;
        }
        if (mPeerRequestThreadPool != null) {
            Utils.shutdownExecutorService(mPeerRequestThreadPool);
            mPeerRequestThreadPool = null;
        }
        mStopped = true;
        Log.addEntry(LOG_TAG, "stopped");
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
                    } catch (Utils.ApplicationError e) {
                        Log.addEntry(LOG_TAG, "failed to restart engine after preference change");
                    }
                }
            };
        } else {
            mHandler.removeCallbacks(mPreferencesRestartTask);
        }
        mHandler.postDelayed(mPreferencesRestartTask, PREFERENCE_CHANGE_RESTART_DELAY_IN_MILLISECONDS);
    }


    @Subscribe
    public synchronized void onTorCircuitEstablished(Events.TorCircuitEstablished torCircuitEstablished) {
        try {
            initializeWebClientConnectionPool();
            // Ask friends to pull local, self changes...
            askPullFromFriends();
            // ...and pull changes from friends
            pullFromFriends();
            startDownloadRetryTask();
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to start friend poll after Tor circuit established");
        }
    }

    @Subscribe
    public synchronized void onUpdatedSelf(Events.UpdatedSelf updatedSelf) {
        // Apply new transport and hidden service credentials
        try {
            start();
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to restart hidden service after self updated");
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
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed restart sharing service after added friend");
        }
    }

    @Subscribe
    public synchronized void onRemovedFriend(Events.RemovedFriend removedFriend) {
        // Full stop/start to clear friend task cache
        try {
            start();
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed restart sharing service after removed friend");
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
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to update self status with new location");
        }
    }

    @Subscribe
    public synchronized void onUpdatedSelfGroup(Events.UpdatedSelfGroup updatedSelfGroup) {
        try {
            pushToFriends(mData.getGroupOrThrow(updatedSelfGroup.mGroupId).mGroup);
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed push to friends after self group update");
        }
    }

    @Subscribe
    public synchronized void onUpdatedSelfLocation(Events.UpdatedSelfLocation updatedSelfLocation) {
        try {
            pushToFriends(mData.getSelfLocationOrThrow());
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed push to friends after self location update");
        }
    }

    @Subscribe
    public synchronized void onUpdatedSelfPost(Events.UpdatedSelfPost updatedSelfPost) {
        try {
            pushToFriends(mData.getPostOrThrow(updatedSelfPost.mPostId).mPost);
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed push to friends after self post update");
        }
    }

    // *TODO* ...?
    /*
    @Subscribe
    public synchronized void onDisplayedMessages(Events.DisplayedMessages displayedMessages) {
        try {
            Data.getInstance().resetNewMessages();
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to reset new messages");
        }
    }
    */

    @Subscribe
    public synchronized void onAddedDownload(Events.AddedDownload addedDownload) {
        // Schedule immediate download, if not already downloading from friend
        triggerFriendTask(FriendTaskType.DOWNLOAD_FROM, addedDownload.mFriendId);
    }

    public synchronized Future<?> submitTask(Runnable task) {
        if (mTaskThreadPool != null) {
            return mTaskThreadPool.submit(task);
        }
        return null;
    }

    @Override
    public synchronized void submitWebRequestTask(Runnable task) {
        if (mPeerRequestThreadPool != null) {
            mPeerRequestThreadPool.submit(task);
        }
    }

    private void startHiddenService() throws Utils.ApplicationError {
        stopHiddenService();

        Data.Self self = mData.getSelfOrThrow();
        List<String> friendCertificates = new ArrayList<String>();
        for (Data.Friend friend : mData.getFriends()) {
            friendCertificates.add(friend.mPublicIdentity.mX509Certificate);
        }
        mWebServer = new WebServer(
                this,
                new X509.KeyMaterial(self.mPublicIdentity.mX509Certificate, self.mPrivateIdentity.mX509PrivateKey),
                friendCertificates);
        try {
            mWebServer.start();
        } catch (IOException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }

        List<TorWrapper.HiddenServiceAuth> hiddenServiceAuths = new ArrayList<TorWrapper.HiddenServiceAuth>();
        for (Data.Friend friend : mData.getFriends()) {
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
        // Friend poll depends on Tor wrapper, so stop it first
        stopDownloadRetryTask();
        if (mTorWrapper != null) {
            mTorWrapper.stop();
        }
        if (mWebServer != null) {
            mWebServer.stop();
        }
    }

    private void initializeWebClientConnectionPool() throws Utils.ApplicationError {
        if (mWebClientConnectionPool != null) {
            mWebClientConnectionPool.shutdown();
        }
        mWebClientConnectionPool = new WebClientConnectionPool(mData, getTorSocksProxyPort());
    }

    public synchronized int getTorSocksProxyPort() throws Utils.ApplicationError {
        if (mTorWrapper != null) {
            return mTorWrapper.getSocksProxyPort();
        }
        throw new Utils.ApplicationError(LOG_TAG, "no Tor socks proxy");
    }

    private void askPullFromFriends() throws Utils.ApplicationError {
        for (Data.Friend friend : mData.getFriends()) {
            triggerFriendTask(FriendTaskType.ASK_PULL, friend.mId);
        }
    }

    public void askLocationFromFriend(String friendId) throws Utils.ApplicationError {
        triggerFriendTask(FriendTaskType.ASK_LOCATION, friendId);
    }

    private void pullFromFriends() throws Utils.ApplicationError {
        for (Data.Friend friend : mData.getFriends()) {
            triggerFriendTask(FriendTaskType.PULL_FROM, friend.mId);
        }
    }

    private void pushToFriends(Protocol.Group group) throws Utils.ApplicationError {
        pushToGroup(group, new Protocol.Payload(Protocol.Payload.Type.GROUP, group));
    }

    private void pushToFriends(Protocol.Location location) throws Utils.ApplicationError {
        // *TODO* group location sharing preferences
        for (Data.Friend friend : mData.getFriends()) {
            enqueueFriendPushPayload(friend.mId, new Protocol.Payload(Protocol.Payload.Type.LOCATION, location));
            triggerFriendTask(FriendTaskType.PUSH_TO, friend.mId);
        }
    }

    private void pushToFriends(Protocol.Post post) throws Utils.ApplicationError {
        Data.Group group = mData.getGroupOrThrow(post.mGroupId);
        pushToGroup(group.mGroup, new Protocol.Payload(Protocol.Payload.Type.POST, post));
    }

    private void pushToGroup(Protocol.Group group, Protocol.Payload payload) throws Utils.ApplicationError {
        for (Identity.PublicIdentity member : group.mMembers) {
            enqueueFriendPushPayload(member.mId, payload);
            triggerFriendTask(FriendTaskType.PUSH_TO, member.mId);
        }
    }

    private void startDownloadRetryTask() throws Utils.ApplicationError {
        stopDownloadRetryTask();
        // Start a recurring timer with initial delay
        // FRIEND_REQUEST_DELAY_IN_MILLISECONDS and subsequent delay
        // DOWNLOAD_RETRY_PERIOD_IN_MILLISECONDS. The timer triggers
        // friend downloads, if any are pending.
        if (mDownloadRetryTask == null) {
            mDownloadRetryTask = new Runnable() {
                @Override
                public void run() {
                    try {
                        for (Data.Friend friend : mData.getFriends()) {
                            triggerFriendTask(FriendTaskType.DOWNLOAD_FROM, friend.mId);
                        }
                    } catch (Utils.ApplicationError e) {
                        Log.addEntry(LOG_TAG, "failed to poll friends");
                    } finally {
                        mHandler.postDelayed(this, DOWNLOAD_RETRY_PERIOD_IN_MILLISECONDS);
                    }
                }
            };
        } else {
            mHandler.removeCallbacks(mDownloadRetryTask);
        }
        mHandler.postDelayed(mDownloadRetryTask, FRIEND_REQUEST_DELAY_IN_MILLISECONDS);
    }

    private void stopDownloadRetryTask() {
        if (mDownloadRetryTask != null) {
            mHandler.removeCallbacks(mDownloadRetryTask);
        }
    }

    private synchronized void triggerFriendTask(FriendTaskType taskType, String friendId) {
        // Schedules one push/pull/download per friend at a time.
        if (mFriendTaskObjects.get(taskType) == null) {
            mFriendTaskObjects.put(taskType, new HashMap<String, Runnable>());
        }
        // Cache instantiated task functions
        Runnable task = mFriendTaskObjects.get(taskType).get(friendId);
        if (task == null) {
            switch (taskType) {
            case ASK_PULL:
                task = makeAskPullToFriendTask(friendId);
                break;
            case ASK_LOCATION:
                task = makeAskLocationToFriendTask(friendId);
                break;
            case PUSH_TO:
                task = makePushToFriendTask(friendId);
                break;
            case PULL_FROM:
                task = makePullFromFriendTask(friendId);
                break;
            case DOWNLOAD_FROM:
                task = makeDownloadFromFriendTask(friendId);
                break;
            }
            mFriendTaskObjects.get(taskType).put(friendId, task);
        }
        if (mFriendTaskFutures.get(taskType) == null) {
            mFriendTaskFutures.put(taskType, new HashMap<String, Future<?>>());
        }
        // If a Future is present, the task is in progress.
        // On completion, tasks remove their Futures from mFriendTaskFutures.
        if (mFriendTaskFutures.get(taskType).get(friendId) != null) {
            return;
        }
        Future<?> future = submitTask(task);
        mFriendTaskFutures.get(taskType).put(friendId, future);
    }

    /*
    // *TODO* may be obsolete code
    private synchronized void cancelPendingFriendTask(FriendTaskType taskType, String friendId) {
        // Remove pending (not running) task, if present in queue
        Future<?> future = mFriendTaskFutures.get(taskType).get(friendId);
        if (future != null) {
            if (future.cancel(false)) {
                mFriendTaskFutures.get(taskType).remove(friendId);
            }
        }
    }
    */

    private synchronized void completedFriendTask(FriendTaskType taskType, String friendId) {
        mFriendTaskFutures.get(taskType).remove(friendId);
    }

    private synchronized void enqueueFriendPushPayload(String friendId, Protocol.Payload payload) {
        if (mFriendPushQueue.get(friendId) == null) {
            mFriendPushQueue.put(friendId, new ArrayList<Protocol.Payload>());
        }
        mFriendPushQueue.get(friendId).add(payload);
    }

    private synchronized Protocol.Payload dequeueFriendPushPayload(String friendId) {
        ArrayList<Protocol.Payload> queue = mFriendPushQueue.get(friendId);
        if (queue == null || queue.isEmpty()) {
            return null;
        }
        return queue.remove(0);
    }

    // TODO: refactor common code in makeTask functions?

    private Runnable makeAskPullToFriendTask(String friendId) {
        final String finalFriendId = friendId;
        return new Runnable() {
            @Override
            public void run() {
                try {
                    if (!mTorWrapper.isCircuitEstablished()) {
                        return;
                    }
                    Data.Self self = mData.getSelf();
                    Data.Friend friend = mData.getFriendById(finalFriendId);
                    Log.addEntry(LOG_TAG, "ask pull to: " + friend.mPublicIdentity.mNickname);
                    WebClientRequest webClientRequest =
                        new WebClientRequest(
                            mWebClientConnectionPool,
                            friend.mPublicIdentity.mHiddenServiceHostname,
                            Protocol.WEB_SERVER_VIRTUAL_PORT,
                            WebClientRequest.RequestType.GET,
                            Protocol.ASK_PULL_GET_REQUEST_PATH);
                    webClientRequest.makeRequest();
                } catch (Data.NotFoundError e) {
                    // Friend was deleted while task was enqueued. Ignore error.
                } catch (Utils.ApplicationError e) {
                    try {
                        Log.addEntry(
                                LOG_TAG,
                                "failed to ask pull to: " +
                                    mData.getFriendByIdOrThrow(finalFriendId).mPublicIdentity.mNickname);
                    } catch (Utils.ApplicationError e2) {
                        Log.addEntry(LOG_TAG, "failed to ask pull");
                    }
                } finally {
                    completedFriendTask(FriendTaskType.ASK_PULL, finalFriendId);
                }
            }
        };
    }

    private Runnable makeAskLocationToFriendTask(String friendId) {
        final String finalFriendId = friendId;
        return new Runnable() {
            @Override
            public void run() {
                try {
                    if (!mTorWrapper.isCircuitEstablished()) {
                        return;
                    }
                    Data.Self self = mData.getSelf();
                    Data.Friend friend = mData.getFriendById(finalFriendId);
                    Log.addEntry(LOG_TAG, "ask location to: " + friend.mPublicIdentity.mNickname);
                    WebClientRequest webClientRequest =
                            new WebClientRequest(
                                mWebClientConnectionPool,
                                friend.mPublicIdentity.mHiddenServiceHostname,
                                Protocol.WEB_SERVER_VIRTUAL_PORT,
                                WebClientRequest.RequestType.GET,
                                Protocol.ASK_LOCATION_GET_REQUEST_PATH);
                    webClientRequest.makeRequest();
                } catch (Data.NotFoundError e) {
                    // Friend was deleted while task was enqueued. Ignore error.
                } catch (Utils.ApplicationError e) {
                    try {
                        Log.addEntry(
                                LOG_TAG,
                                "failed to ask location to: " +
                                    mData.getFriendByIdOrThrow(finalFriendId).mPublicIdentity.mNickname);
                    } catch (Utils.ApplicationError e2) {
                        Log.addEntry(LOG_TAG, "failed to ask location");
                    }
                } finally {
                    completedFriendTask(FriendTaskType.ASK_PULL, finalFriendId);
                }
            }
        };
    }

    private Runnable makePushToFriendTask(String friendId) {
        final String finalFriendId = friendId;
        return new Runnable() {
            @Override
            public void run() {
                try {
                    if (!mTorWrapper.isCircuitEstablished()) {
                        return;
                    }
                    Data.Self self = mData.getSelf();
                    Data.Friend friend = mData.getFriendById(finalFriendId);
                    while (true) {
                        Protocol.Payload payload = dequeueFriendPushPayload(finalFriendId);
                        if (payload == null) {
                            // *TODO* race condition when item enqueue before completedFriendTask is called; triggerFriendTask won't start a new task
                            break;
                        }
                        Log.addEntry(LOG_TAG, "push to: " + friend.mPublicIdentity.mNickname);
                        WebClientRequest webClientRequest =
                                new WebClientRequest(
                                    mWebClientConnectionPool,
                                    friend.mPublicIdentity.mHiddenServiceHostname,
                                    Protocol.WEB_SERVER_VIRTUAL_PORT,
                                    WebClientRequest.RequestType.PUT,
                                    Protocol.PUSH_PUT_REQUEST_PATH).
                                        requestBody(Json.toJson(payload.mObject));
                        webClientRequest.makeRequest();
                        switch (payload.mType) {
                        case GROUP:
                            mData.confirmSentTo(friend.mId, (Protocol.Group)payload.mObject);
                            break;
                        case POST:
                            mData.confirmSentTo(friend.mId, (Protocol.Post)payload.mObject);
                            break;
                        default:
                            break;
                        }
                    }
                } catch (Data.NotFoundError e) {
                    // Friend was deleted while task was enqueued. Ignore error.
                } catch (Utils.ApplicationError e) {
                    try {
                        Log.addEntry(
                                LOG_TAG,
                                "failed to push to: " +
                                    mData.getFriendByIdOrThrow(finalFriendId).mPublicIdentity.mNickname);
                    } catch (Utils.ApplicationError e2) {
                        Log.addEntry(LOG_TAG, "failed to push");
                    }
                } finally {
                    completedFriendTask(FriendTaskType.PUSH_TO, finalFriendId);
                }
            }
        };
    }

    private Runnable makePullFromFriendTask(String friendId) {
        final String finalFriendId = friendId;
        return new Runnable() {
            @Override
            public void run() {
                try {
                    if (!mTorWrapper.isCircuitEstablished()) {
                        return;
                    }
                    Data.Self self = mData.getSelf();
                    Data.Friend friend = mData.getFriendById(finalFriendId);
                    Log.addEntry(LOG_TAG, "pull from: " + friend.mPublicIdentity.mNickname);
                    // Pull twice. The first pull is to actually get data. The second pull
                    // is to explicitly acknowledge the received data via the last received
                    // sequence numbers passed in the second pull request. The second pull
                    // may receive additional data.
                    // The primary (first) pull is also the only one where we request a
                    // pull in the other direction from the peer.
                    for (int i = 0; i < 2; i++) {
                        final Protocol.PullRequest finalPullRequest = mData.getPullRequest(finalFriendId);
                        WebClientRequest.ResponseBodyHandler responseBodyHandler = new WebClientRequest.ResponseBodyHandler() {
                            @Override
                            public void consume(InputStream responseBodyInputStream) throws Utils.ApplicationError {
                                Protocol.PullRequest pullRequest = finalPullRequest;
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
                                        break;
                                    case POST:
                                        Protocol.Post post = (Protocol.Post)payload.mObject;
                                        Protocol.validatePost(post);
                                        posts.add(post);
                                        break;
                                    default:
                                        break;
                                    }
                                    if (groups.size() + posts.size() >= Data.MAX_PULL_RESPONSE_TRANSACTION_OBJECT_COUNT) {
                                        mData.putPullResponse(finalFriendId, pullRequest, groups, posts);
                                        pullRequest = null;
                                        groups.clear();
                                        posts.clear();
                                    }
                                }
                                mData.putPullResponse(finalFriendId, pullRequest, groups, posts);
                            }
                        };
                        WebClientRequest webClientRequest =
                                new WebClientRequest(
                                    mWebClientConnectionPool,
                                    friend.mPublicIdentity.mHiddenServiceHostname,
                                    Protocol.WEB_SERVER_VIRTUAL_PORT,
                                    WebClientRequest.RequestType.PUT,
                                    Protocol.PULL_PUT_REQUEST_PATH).
                                        requestBody(Json.toJson(finalPullRequest)).
                                        responseBodyHandler(responseBodyHandler);
                        webClientRequest.makeRequest();
                    }
                } catch (Data.NotFoundError e) {
                    // Friend was deleted while task was enqueued. Ignore error.
                    // RemovedFriend should eventually cancel schedule.
                } catch (Utils.ApplicationError e) {
                    try {
                        Log.addEntry(
                                LOG_TAG,
                                "failed to pull from: " +
                                    mData.getFriendByIdOrThrow(finalFriendId).mPublicIdentity.mNickname);
                    } catch (Utils.ApplicationError e2) {
                        Log.addEntry(LOG_TAG, "failed to pull");
                    }
                } finally {
                    completedFriendTask(FriendTaskType.PULL_FROM, finalFriendId);
                }
            }
        };
    }

    private Runnable makeDownloadFromFriendTask(String friendId) {
        final String finalFriendId = friendId;
        return new Runnable() {
            @Override
            public void run() {
                try {
                    if (!mTorWrapper.isCircuitEstablished()) {
                        return;
                    }
                    if (getBooleanPreference(R.string.preferenceExchangeFilesWifiOnly)
                            && !Utils.isConnectedNetworkWifi(mContext)) {
                        // Will retry after next delay period
                        return;
                    }
                    Data.Self self = mData.getSelf();
                    Data.Friend friend = mData.getFriendById(finalFriendId);
                    while (true) {
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
                            Log.addEntry(LOG_TAG, "download from: " + friend.mPublicIdentity.mNickname);
                            List<Pair<String, String>> requestParameters =
                                    Arrays.asList(new Pair<String, String>(Protocol.DOWNLOAD_GET_REQUEST_RESOURCE_ID_PARAMETER, download.mResourceId));
                            Pair<Long, Long> range = new Pair<Long, Long>(downloadedSize, (long)-1);
                            WebClientRequest webClientRequest =
                                    new WebClientRequest(
                                        mWebClientConnectionPool,
                                        friend.mPublicIdentity.mHiddenServiceHostname,
                                        Protocol.WEB_SERVER_VIRTUAL_PORT,
                                        WebClientRequest.RequestType.GET,
                                        Protocol.DOWNLOAD_GET_REQUEST_PATH).
                                            requestParameters(requestParameters).
                                            rangeHeader(range).
                                            responseBodyOutputStream(Downloads.openDownloadResourceForAppending(download));
                            webClientRequest.makeRequest();
                        }
                        mData.updateDownloadState(friend.mId, download.mResourceId, Data.Download.State.COMPLETE);
                        // TODO: WebClient post to event bus for download progress (replacing timer-based refreshes...)
                        // TODO: 404/403: denied by peer? -- change Download state to reflect this and don't retry (e.g., new state: CANCELLED)
                        // TODO: update some last received timestamp?
                    }
                } catch (Data.NotFoundError e) {
                    // Friend was deleted while task was enqueued. Ignore error.
                    // RemovedFriend should eventually cancel schedule.
                } catch (Utils.ApplicationError e) {
                    try {
                        Log.addEntry(
                                LOG_TAG,
                                "failed to download from: " +
                                    mData.getFriendByIdOrThrow(finalFriendId).mPublicIdentity.mNickname);
                    } catch (Utils.ApplicationError e2) {
                        Log.addEntry(LOG_TAG, "failed to download status");
                    }
                } finally {
                    completedFriendTask(FriendTaskType.DOWNLOAD_FROM, finalFriendId);
                }
            }
        };
    }


    // Note: WebServer callbacks not synchronized
    @Override
    public String getFriendNicknameByCertificate(String friendCertificate) throws Utils.ApplicationError {
        Data.Friend friend = mData.getFriendByCertificateOrThrow(friendCertificate);
        return friend.mPublicIdentity.mNickname;
    }

    // Note: WebServer callbacks not synchronized
    @Override
    public void updateFriendSent(String friendCertificate, Date lastSentToTimestamp, long additionalBytesSentTo)
            throws Utils.ApplicationError  {
        Data.Friend friend = mData.getFriendByCertificateOrThrow(friendCertificate);
        mData.updateFriendSentOrThrow(friend.mId, lastSentToTimestamp, additionalBytesSentTo);
    }

    // Note: WebServer callbacks not synchronized
    @Override
    public void updateFriendReceived(String friendCertificate, Date lastReceivedFromTimestamp, long additionalBytesReceivedFrom)
            throws Utils.ApplicationError {
        Data.Friend friend = mData.getFriendByCertificateOrThrow(friendCertificate);
        mData.updateFriendReceivedOrThrow(friend.mId, lastReceivedFromTimestamp, additionalBytesReceivedFrom);
    }

    // Note: WebServer callbacks not synchronized
    @Override
    public void handleAskPullRequest(String friendCertificate) throws Utils.ApplicationError {
        try {
            Data.Friend friend = mData.getFriendByCertificate(friendCertificate);
            triggerFriendTask(FriendTaskType.PULL_FROM, friend.mId);
            Log.addEntry(LOG_TAG, "served ask pull request for " + friend.mPublicIdentity.mNickname);
        } catch (Data.NotFoundError e) {
            throw new Utils.ApplicationError(LOG_TAG, "failed to handle ask pull request: friend not found");
        }
    }

    // Note: WebServer callbacks not synchronized
    @Override
    public void handleAskLocationRequest(String friendCertificate) throws Utils.ApplicationError {
        try {
            Data.Friend friend = mData.getFriendByCertificate(friendCertificate);
            // *TODO* check location ACL; trigger fix (if not in progress: see spec)
            Log.addEntry(LOG_TAG, "served ask location request for " + friend.mPublicIdentity.mNickname);
        } catch (Data.NotFoundError e) {
            throw new Utils.ApplicationError(LOG_TAG, "failed to handle ask location request: friend not found");
        }
    }

    // Note: WebServer callbacks not synchronized
    @Override
    public void handlePushRequest(String friendCertificate, String requestBody) throws Utils.ApplicationError  {
        try {
            Data.Friend friend = mData.getFriendByCertificate(friendCertificate);
            // TODO: stream requestBody instead of loading entirely into memory
            Json.PayloadIterator payloadIterator = new Json.PayloadIterator(requestBody);
            boolean needPull = false;
            for (Protocol.Payload payload : payloadIterator) {
                switch(payload.mType) {
                case GROUP:
                    Protocol.Group group = (Protocol.Group)payload.mObject;
                    Protocol.validateGroup(group);
                    mData.putPushedGroup(friend.mId, group);
                    break;
                case LOCATION:
                    Protocol.Location location = (Protocol.Location)payload.mObject;
                    Protocol.validateLocation(location);
                    mData.putPushedLocation(friend.mId, location);
                    break;
                case POST:
                    Protocol.Post post = (Protocol.Post)payload.mObject;
                    Protocol.validatePost(post);
                    if (mData.putPushedPost(friend.mId, post)) {
                        needPull = true;
                    }
                    break;
                default:
                    break;
                }
            }
            if (needPull) {
                triggerFriendTask(FriendTaskType.PULL_FROM, friend.mId);
            }
            // *TODO* log too noisy?
            Log.addEntry(LOG_TAG, "served push request for " + friend.mPublicIdentity.mNickname);
        } catch (Data.NotFoundError e) {
            // *TODO* use XYZOrThrow instead?
            throw new Utils.ApplicationError(LOG_TAG, "failed to handle push request: friend not found");
        }
    }

    // Note: WebServer callbacks not synchronized
    @Override
    public WebServer.RequestHandler.PullResponse handlePullRequest(String friendCertificate, String requestBody) throws Utils.ApplicationError {
        try {
            Data.Friend friend = mData.getFriendByCertificate(friendCertificate);
            Protocol.PullRequest pullRequest = Json.fromJson(requestBody, Protocol.PullRequest.class);
            Protocol.validatePullRequest(pullRequest);
            mData.confirmSentTo(friend.mId, pullRequest);
            Data.PullResponseIterator pullResponseIterator = mData.getPullResponse(friend.mId, pullRequest);
            Log.addEntry(LOG_TAG, "served pull request for " + friend.mPublicIdentity.mNickname);
            return new WebServer.RequestHandler.PullResponse(
                    new Utils.StringIteratorInputStream(pullResponseIterator));
        } catch (Data.NotFoundError e) {
            // *TODO* use XYZOrThrow instead?
            throw new Utils.ApplicationError(LOG_TAG, "failed to handle pull status request: friend not found");
        }
    }

    // Note: WebServer callbacks not synchronized
    @Override
    public WebServer.RequestHandler.DownloadResponse handleDownloadRequest(
            String friendCertificate, String resourceId, Pair<Long, Long> range) throws Utils.ApplicationError  {
        try {
            Data.Friend friend = mData.getFriendByCertificate(friendCertificate);
            Data.LocalResource localResource = mData.getLocalResource(friend.mId, resourceId);
            // Note: don't check availability until after input validation
            if (getBooleanPreference(R.string.preferenceExchangeFilesWifiOnly)
                    && !Utils.isConnectedNetworkWifi(mContext)) {
                // Download service not available
                return new DownloadResponse(false, null, null);
            }
            InputStream inputStream = Resources.openLocalResourceForReading(localResource, range);
            Log.addEntry(LOG_TAG, "served download request for " + friend.mPublicIdentity.mNickname);
            return new DownloadResponse(true, localResource.mMimeType, inputStream);
        } catch (Data.NotFoundError e) {
            // *TODO* use XYZOrThrow instead?
            throw new Utils.ApplicationError(LOG_TAG, "failed to handle download request: friend or resource not found");
        }
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
