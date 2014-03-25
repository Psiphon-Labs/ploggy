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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.net.TrafficStats;
import android.os.Looper;
import android.os.Process;

import com.squareup.otto.Subscribe;

/**
 * Component tests.
 *
 * Covered (by end-to-end request from one peer to another through Tor):
 * - TorWrapper
 * - Identity
 * - X509
 * - HiddenService
 * - WebClient
 * - WebServer
 */
public class Tests {

    private static final String LOG_TAG = "Tests";

    private static Timer mTimer = new Timer();

    public static void scheduleComponentTests() {
        mTimer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        Looper.prepare();
                        Tests.runComponentTests();
                    }
                },
                2000);
    }


    private static final String ALICE = "Alice";
    private static final String BOB = "Bob";
    private static final String CAROL = "Carol";
    private static final String EVE = "Eve";

    private static final String ALICE_GROUP = "Alice's Group";

    private static final int AWAIT_SYNC_TIMEOUT_SECONDS = 120;

    public static void runComponentTests() {
        PloggyInstance alice = null;
        PloggyInstance bob = null;
        PloggyInstance carol = null;
        PloggyInstance eve = null;

        try {
            alice = new PloggyInstance(ALICE);
            bob = new PloggyInstance(BOB);
            carol = new PloggyInstance(CAROL);
            eve = new PloggyInstance(EVE);

            Log.addEntry(LOG_TAG, "Baseline TrafficStats data usage...");
            // TrafficStats docs state state are only reset on device boot
            logAppDataUsage();

            alice.start();
            bob.start();
            carol.start();
            eve.start();

            alice.addFriend(bob);
            alice.addFriend(carol);

            bob.addFriend(alice);

            carol.addFriend(alice);

            // Alice does not make Eve a friend
            eve.addFriend(alice);

            String aliceGroupId = alice.addGroup(ALICE_GROUP);

            Log.addEntry(LOG_TAG, "Locally write and read 10K posts...");
            //alice.addPosts(aliceGroupId, 10000);
            alice.addPosts(aliceGroupId, 100);
            alice.loadPosts(aliceGroupId);

            Log.addEntry(LOG_TAG, "Add friends to group and sync group and posts...");
            alice.addGroupMember(aliceGroupId, bob);
            alice.addGroupMember(aliceGroupId, carol);
            alice.awaitSync(aliceGroupId, bob);
            alice.awaitSync(aliceGroupId, carol);
            alice.compareGroupData(aliceGroupId, bob);
            alice.compareGroupData(aliceGroupId, carol);

            Log.addEntry(LOG_TAG, "Recorded data usage (vs. TrafficStats)...");
            alice.logFriendsDataTransfer();
            bob.logFriendsDataTransfer();
            carol.logFriendsDataTransfer();
            logAppDataUsage();

            Log.addEntry(LOG_TAG, "Group as friend introduction service...");
            bob.addCandidateFriends();
            bob.checkIsFriend(carol);
            carol.addCandidateFriends();
            carol.checkIsFriend(bob);

            Log.addEntry(LOG_TAG, "Group subscribers publish posts...");
            bob.addPosts(aliceGroupId, 100);
            carol.addPosts(aliceGroupId, 100);
            bob.awaitSync(aliceGroupId, alice);
            bob.awaitSync(aliceGroupId, carol);
            carol.awaitSync(aliceGroupId, alice);
            carol.awaitSync(aliceGroupId, bob);
            alice.compareGroupData(aliceGroupId, bob);
            alice.compareGroupData(aliceGroupId, carol);
            bob.compareGroupData(aliceGroupId, carol);

            Log.addEntry(LOG_TAG, "Latency for chat-like post exchange...");
            // TODO: should be triggered by Events.UpdatedFriendPost?
            for (int i = 0; i < 100; i++) {
                alice.addPosts(aliceGroupId, 1);
                alice.awaitSync(aliceGroupId, bob);
                bob.addPosts(aliceGroupId, 1);
                bob.awaitSync(aliceGroupId, alice);
            }

            // TODO:
            // - location sharing
            // - group remove/resign/delete cases
            // - attachments

            Log.addEntry(LOG_TAG, "Component tests succeeded");
        } catch (PloggyError e) {
            Log.addEntry(LOG_TAG, "Component tests failed");
        } catch (InterruptedException e) {
            Log.addEntry(LOG_TAG, "Component tests interrupted");
        } finally {
            if (alice != null) {
                alice.stop();
            }
            if (bob != null) {
                bob.stop();
            }
            if (carol != null) {
                carol.stop();
            }
            if (eve != null) {
                eve.stop();
            }
        }
    }

    private static class PloggyInstance {
        private final Context mContext;
        private final String mInstanceName;
        private final Engine mEngine;
        private final Data mData;

        public PloggyInstance(String instanceName) throws PloggyError {
            mContext = Utils.getApplicationContext();
            mInstanceName = instanceName;
            Data.deleteDatabase(mInstanceName);
            mData = Data.getInstance(mInstanceName);
            mEngine = new Engine(mInstanceName);
        }

        public void start() throws PloggyError {
            Log.addEntry(LOG_TAG, "Starting " + mInstanceName);
            HiddenService.KeyMaterial selfHiddenServiceKeyMaterial = HiddenService.generateKeyMaterial(mInstanceName);
            X509.KeyMaterial selfX509KeyMaterial = X509.generateKeyMaterial(selfHiddenServiceKeyMaterial.mHostname);
            Data.Self self = new Data.Self(
                    Identity.makeSignedPublicIdentity(
                            mInstanceName,
                            selfX509KeyMaterial,
                            selfHiddenServiceKeyMaterial),
                    Identity.makePrivateIdentity(
                            selfX509KeyMaterial,
                            selfHiddenServiceKeyMaterial),
                    new Date());
            mData.putSelf(self);
            Events.getInstance(mInstanceName).register(this);
            mEngine.start();
            Log.addEntry(LOG_TAG, "Started " + mInstanceName);
        }

        public void stop() {
            Log.addEntry(LOG_TAG, "Stopping " + mInstanceName);
            mEngine.stop();
            Events.getInstance(mInstanceName).unregister(this);
            Log.addEntry(LOG_TAG, "Stopped " + mInstanceName);
        }

        public Identity.PublicIdentity getPublicIdentity() throws PloggyError {
            return mData.getSelfOrThrow().mPublicIdentity;
        }

        void addFriend(PloggyInstance ploggyInstance) throws PloggyError {
            addFriend(ploggyInstance.getPublicIdentity());
        }

        void addFriend(Identity.PublicIdentity publicIdentity) throws PloggyError {
            Data.Friend friend = new Data.Friend(publicIdentity, new Date());
            try {
                mData.addFriend(friend);
            } catch (Data.AlreadyExistsError e) {
                throw new PloggyError(LOG_TAG, e);
            }
            Log.addEntry(LOG_TAG, mInstanceName + " added friend " + publicIdentity.mNickname);
        }

        String addGroup(String name) throws PloggyError {
            String groupId = Utils.makeId();
            String publisherId = getPublicIdentity().mId;
            Date now = new Date();
            Protocol.Group group = new Protocol.Group(
                    groupId,
                    name,
                    publisherId,
                    new ArrayList<Identity.PublicIdentity>(),
                    now,
                    now,
                    -1,
                    false);
            try {
                mData.putGroup(group);
            } catch (Data.AlreadyExistsError e) {
                throw new PloggyError(LOG_TAG, e);
            }
            Log.addEntry(LOG_TAG, mInstanceName + " added group " + name);
            return groupId;
        }

        void addGroupMember(String groupId, PloggyInstance ploggyInstance)
                throws PloggyError {
            Date now = new Date();
            Protocol.Group group = mData.getGroupOrThrow(groupId).mGroup;
            List<Identity.PublicIdentity> updatedMembers =
                    new ArrayList<Identity.PublicIdentity>(group.mMembers);
            updatedMembers.add(ploggyInstance.getPublicIdentity());
            Protocol.Group updatedGroup = new Protocol.Group(
                    group.mId,
                    group.mName,
                    group.mPublisherId,
                    updatedMembers,
                    group.mCreatedTimestamp,
                    now,
                    group.mSequenceNumber,
                    group.mIsTombstone);
            try {
                mData.putGroup(updatedGroup);
            } catch (Data.AlreadyExistsError e) {
                throw new PloggyError(LOG_TAG, e);
            }
            Log.addEntry(
                    LOG_TAG,
                    mInstanceName + " added group member " + ploggyInstance.getPublicIdentity().mNickname);
        }

        void addPosts(String groupId, int count) throws PloggyError {
            String publisherId = getPublicIdentity().mId;
            List<Protocol.Resource> attachments = new ArrayList<Protocol.Resource>();
            Date now = new Date();
            for (int i = 0; i < count; i++) {
                String content = Utils.encodeBase64(Utils.getRandomBytes(250));
                Protocol.Post post = new Protocol.Post(
                        Utils.makeId(),
                        groupId,
                        publisherId,
                        Protocol.POST_CONTENT_TYPE_DEFAULT,
                        content,
                        attachments,
                        now,
                        now,
                        -1,
                        false);
                mData.addPost(post, null);
            }
            Log.addEntry(
                    LOG_TAG,
                    mInstanceName + " added posts: " + Integer.toString(count));
        }

        @SuppressWarnings("unused")
        void loadPosts(String groupId) throws PloggyError {
            int count = 0;
            for (Data.Post post : mData.getPostsIterator(groupId)) {
                count++;
            }
            Log.addEntry(
                    LOG_TAG,
                    mInstanceName + " loaded posts: " + Integer.toString(count));
        }

        void awaitSync(String groupId, PloggyInstance ploggyInstance)
                throws PloggyError, InterruptedException {
            Identity.PublicIdentity publicIdentity = ploggyInstance.getPublicIdentity();
            for (int i = 0; i < AWAIT_SYNC_TIMEOUT_SECONDS; i++) {
                Thread.sleep(1000);
                Data.Group group = mData.getGroupOrThrow(groupId);
                // *TODO* compare MemberLastConfirmedSequenceNumbers with current max Group/Post seq numbers.
                Protocol.SequenceNumbers lastConfirmedSequenceNumbers =
                        group.mMemberLastConfirmedSequenceNumbers.get(publicIdentity.mId);
                if (lastConfirmedSequenceNumbers.mGroupSequenceNumber == group.mGroup.mSequenceNumber
                        && lastConfirmedSequenceNumbers.mPostSequenceNumber == group.mLastPostSequenceNumber) {
                    Log.addEntry(
                            LOG_TAG,
                            mInstanceName + " got sync for " + publicIdentity.mNickname);
                    return;
                }
                if (i % 10 == 0) {
                    Log.addEntry(
                            LOG_TAG,
                            mInstanceName + " awaiting sync for " + publicIdentity.mNickname);
                }
            }
            throw new PloggyError(LOG_TAG, "awaitSync timed out");
        }

        void compareGroupData(String groupId, PloggyInstance ploggyInstance)
                throws PloggyError {
            String selfGroup = Json.toJson(mData.getGroupOrThrow(groupId).mGroup);
            String friendGroup = Json.toJson(Data.getInstance(ploggyInstance.mInstanceName).getGroupOrThrow(groupId).mGroup);
            if (!selfGroup.equals(friendGroup)) {
                throw new PloggyError(LOG_TAG, "compareGroupData - group mismatch");
            }
            Data.ObjectCursor<Data.Post> selfIterator = mData.getPosts(groupId);
            Data.ObjectCursor<Data.Post> friendIterator = Data.getInstance(ploggyInstance.mInstanceName).getPosts(groupId);
            while (selfIterator.hasNext()) {
                if (!friendIterator.hasNext()) {
                    throw new PloggyError(LOG_TAG, "compareGroupData - friend has fewer posts");
                }
                String selfPost = Json.toJson(selfIterator.next().mPost);
                String friendPost = Json.toJson(friendIterator.next().mPost);
                if (!selfPost.equals(friendPost)) {
                    throw new PloggyError(LOG_TAG, "compareGroupData - post mismatch");
                }
            }
            if (friendIterator.hasNext()) {
                throw new PloggyError(LOG_TAG, "compareGroupData - friend has more posts");
            }
        }

        void logFriendsDataTransfer() throws PloggyError {
            for (Data.Friend friend : mData.getFriendsIterator()) {
                Log.addEntry(
                        LOG_TAG,
                        mInstanceName + " data usage for friend " + friend.mPublicIdentity.mNickname + ": " +
                            Utils.byteCountToDisplaySize(friend.mBytesSentTo, false) + " sent to " +
                            Utils.byteCountToDisplaySize(friend.mBytesReceivedFrom, false) + " received from ");
            }
        }

        void addCandidateFriends() throws PloggyError {
            for (Data.CandidateFriend friend : mData.getCandidateFriendsIterator()) {
                addFriend(friend.mPublicIdentity);
            }
        }

        void checkIsFriend(PloggyInstance ploggyInstance) throws PloggyError {
            Identity.PublicIdentity publicIdentity = ploggyInstance.getPublicIdentity();
            mData.getFriendByIdOrThrow(publicIdentity.mId);
            Log.addEntry(
                    LOG_TAG,
                    mInstanceName + " has friend " + publicIdentity.mNickname);
        }

        @Subscribe
        public synchronized void onTorCircuitEstablished(Events.TorCircuitEstablished torCircuitEstablished) {
            Log.addEntry(LOG_TAG, "Tor circuit established for " + mInstanceName);
        }
    }

    private static void logAppDataUsage() {
        // *TODO* need Tor child process uid? Or this?
        int uid = Process.myUid();
        long bytesSent = TrafficStats.getUidTxBytes(uid);
        long bytesReceived = TrafficStats.getUidRxBytes(uid);
        Log.addEntry(
                LOG_TAG,
                "App data usage: " +
                    Utils.byteCountToDisplaySize(bytesSent, false) + " sent " +
                    Utils.byteCountToDisplaySize(bytesReceived, false) + " received ");
    }
}


    /*

    *TODO* remove obsolete test code -- once all test cases are covered

    private static class MockRequestHandler implements WebServer.RequestHandler {

        private final ExecutorService mThreadPool = Executors.newCachedThreadPool();
        private final Date mMockTimestamp;
        private final double mMockLatitude;
        private final double mMockLongitude;
        private final String mMockAddress;

        MockRequestHandler() {
            mMockTimestamp = new Date();
            mMockLatitude = Math.random()*100.0 - 50.0;
            mMockLongitude = Math.random()*100.0 - 50.0;
            mMockAddress = "301 Front St W, Toronto, ON M5V 2T6";
        }

        public void stop() {
            Utils.shutdownExecutorService(mThreadPool);
        }

        @Override
        public void submitWebRequestTask(Runnable task) {
            mThreadPool.execute(task);
        }

        public Data.Status getMockStatus() {
            return new Data.Status(
                    Arrays.asList(new Data.Message(mMockTimestamp, "", null)),
                    new Data.Location(
                        mMockTimestamp,
                        mMockLatitude,
                        mMockLongitude,
                        10,
                        mMockAddress));
        }

        @Override
        public Data.Status handlePullStatusRequest(String friendId) throws Utils.ApplicationError {
            Log.addEntry(LOG_TAG, "handle pull status request...");
            return getMockStatus();
        }

        @Override
        public void handlePushStatusRequest(String friendId, Data.Status status) throws Utils.ApplicationError {
            Log.addEntry(LOG_TAG, "handle push status request...");
        }

        @Override
        public DownloadResponse handleDownloadRequest(
                String friendCertificate, String resourceId, Pair<Long, Long> range) throws Utils.ApplicationError {
            Log.addEntry(LOG_TAG, "handle download request...");
            return null;
        }
    }

    public static void runComponentTests() {
        WebServer selfWebServer = null;
        MockRequestHandler selfRequestHandler = null;
        WebServer friendWebServer = null;
        MockRequestHandler friendRequestHandler = null;
        TorWrapper selfTor = null;
        TorWrapper friendTor = null;
        try {

            Log.addEntry(LOG_TAG, "Make self...");
            String selfNickname = "Me";
            HiddenService.KeyMaterial selfHiddenServiceKeyMaterial = HiddenService.generateKeyMaterial();
            X509.KeyMaterial selfX509KeyMaterial = X509.generateKeyMaterial(selfHiddenServiceKeyMaterial.mHostname);
            Data.Self self = new Data.Self(
                    Identity.makeSignedPublicIdentity(
                            selfNickname,
                            selfX509KeyMaterial,
                            selfHiddenServiceKeyMaterial),
                    Identity.makePrivateIdentity(
                            selfX509KeyMaterial,
                            selfHiddenServiceKeyMaterial),
                    new Date());

            Log.addEntry(LOG_TAG, "Make friend...");
            String friendNickname = "My Friend";
            HiddenService.KeyMaterial friendHiddenServiceKeyMaterial = HiddenService.generateKeyMaterial();
            X509.KeyMaterial friendX509KeyMaterial = X509.generateKeyMaterial(friendHiddenServiceKeyMaterial.mHostname);
            Data.Self friendSelf = new Data.Self(
                    Identity.makeSignedPublicIdentity(
                            friendNickname,
                            friendX509KeyMaterial,
                            friendHiddenServiceKeyMaterial),
                    Identity.makePrivateIdentity(
                            friendX509KeyMaterial,
                            friendHiddenServiceKeyMaterial),
                    new Date());
            Data.Friend friend = new Data.Friend(friendSelf.mPublicIdentity, new Date());

            // Not running hidden service for other friend: this is to test multiple client certs in the web server
            Log.addEntry(LOG_TAG, "Make other friend...");
            HiddenService.KeyMaterial otherFriendHiddenServiceKeyMaterial = HiddenService.generateKeyMaterial();
            X509.KeyMaterial otherFriendX509KeyMaterial = X509.generateKeyMaterial(otherFriendHiddenServiceKeyMaterial.mHostname);

            Log.addEntry(LOG_TAG, "Make unfriendly key material...");
            HiddenService.KeyMaterial unfriendlyHiddenServiceKeyMaterial = HiddenService.generateKeyMaterial();
            X509.KeyMaterial unfriendlyX509KeyMaterial = X509.generateKeyMaterial(unfriendlyHiddenServiceKeyMaterial.mHostname);

            Log.addEntry(LOG_TAG, "Start self web server...");
            List<String> selfPeerCertificates = new ArrayList<String>();
            selfPeerCertificates.add(friend.mPublicIdentity.mX509Certificate);
            selfPeerCertificates.add(otherFriendX509KeyMaterial.mCertificate);
            selfRequestHandler = new MockRequestHandler();
            selfWebServer = new WebServer(selfRequestHandler, selfX509KeyMaterial, selfPeerCertificates);
            try {
                selfWebServer.start();
            } catch (IOException e) {
                throw new Utils.ApplicationError(LOG_TAG, e);
            }

            Log.addEntry(LOG_TAG, "Start friend web server...");
            List<String> friendPeerCertificates = new ArrayList<String>();
            friendPeerCertificates.add(self.mPublicIdentity.mX509Certificate);
            friendPeerCertificates.add(otherFriendX509KeyMaterial.mCertificate);
            friendRequestHandler = new MockRequestHandler();
            friendWebServer = new WebServer(friendRequestHandler, friendX509KeyMaterial, friendPeerCertificates);
            try {
                friendWebServer.start();
            } catch (IOException e) {
                throw new Utils.ApplicationError(LOG_TAG, e);
            }

            // Test direct web request (not through Tor)
            // Repeat multiple times to exercise keep-alive connection
            String response;
            String expectedResponse = Json.toJson(selfRequestHandler.getMockStatus());
            for (int i = 0; i < 4; i++) {
                Log.addEntry(LOG_TAG, "Direct GET request from valid friend...");
                response = WebClient.makeGetRequest(
                        friendX509KeyMaterial,
                        self.mPublicIdentity.mX509Certificate,
                        WebClient.UNTUNNELED_REQUEST,
                        "127.0.0.1",
                        selfWebServer.getListeningPort(),
                        Protocol.PULL_STATUS_REQUEST_PATH);
                Protocol.validateStatus(Json.fromJson(response, Data.Status.class));
                if (!response.equals(expectedResponse)) {
                    throw new Utils.ApplicationError(LOG_TAG, "unexpected status response value");
                }
                Log.addEntry(LOG_TAG, "Direct POST request from valid friend...");
                WebClient.makeJsonPostRequest(
                        friendX509KeyMaterial,
                        self.mPublicIdentity.mX509Certificate,
                        WebClient.UNTUNNELED_REQUEST,
                        "127.0.0.1",
                        selfWebServer.getListeningPort(),
                        Protocol.PUSH_STATUS_REQUEST_PATH,
                        expectedResponse);
            }

            Log.addEntry(LOG_TAG, "Run self Tor...");
            List<TorWrapper.HiddenServiceAuth> selfHiddenServiceAuths = new ArrayList<TorWrapper.HiddenServiceAuth>();
            selfHiddenServiceAuths.add(
                    new TorWrapper.HiddenServiceAuth(
                            friendSelf.mPublicIdentity.mHiddenServiceHostname,
                            friendSelf.mPublicIdentity.mHiddenServiceAuthCookie));
            selfTor = new TorWrapper(
                    TorWrapper.Mode.MODE_RUN_SERVICES,
                    "runComponentTests-self",
                    selfHiddenServiceAuths,
                    selfHiddenServiceKeyMaterial,
                    selfWebServer.getListeningPort());
            selfTor.start();

            Log.addEntry(LOG_TAG, "Run friend Tor...");
            List<TorWrapper.HiddenServiceAuth> friendHiddenServiceAuths = new ArrayList<TorWrapper.HiddenServiceAuth>();
            friendHiddenServiceAuths.add(
                    new TorWrapper.HiddenServiceAuth(
                            self.mPublicIdentity.mHiddenServiceHostname,
                            self.mPublicIdentity.mHiddenServiceAuthCookie));
            friendTor = new TorWrapper(
                    TorWrapper.Mode.MODE_RUN_SERVICES,
                    "runComponentTests-friend",
                    friendHiddenServiceAuths,
                    friendHiddenServiceKeyMaterial,
                    friendWebServer.getListeningPort());
            friendTor.start();
            selfTor.start();
            selfTor.awaitStarted();
            friendTor.awaitStarted();

            // TODO: monitor publication state via Tor control interface?
            int publishWaitMilliseconds = 30000;
            Log.addEntry(LOG_TAG, String.format("Wait %d ms. while hidden service is published...", publishWaitMilliseconds));
            try {
                Thread.sleep(publishWaitMilliseconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            for (int i = 0; i < 4; i++) {
                Log.addEntry(LOG_TAG, "request from valid friend...");
                response = WebClient.makeGetRequest(
                        friendX509KeyMaterial,
                        self.mPublicIdentity.mX509Certificate,
                        friendTor.getSocksProxyPort(),
                        self.mPublicIdentity.mHiddenServiceHostname,
                        Protocol.WEB_SERVER_VIRTUAL_PORT,
                        Protocol.PULL_STATUS_REQUEST_PATH);
                Protocol.validateStatus(Json.fromJson(response, Data.Status.class));
                if (!response.equals(expectedResponse)) {
                    throw new Utils.ApplicationError(LOG_TAG, "unexpected status response value");
                }
            }

            Log.addEntry(LOG_TAG, "Request from invalid friend...");
            boolean failed = false;
            try {
                WebClient.makeGetRequest(
                        unfriendlyX509KeyMaterial,
                        self.mPublicIdentity.mX509Certificate,
                        friendTor.getSocksProxyPort(),
                        self.mPublicIdentity.mHiddenServiceHostname,
                        Protocol.WEB_SERVER_VIRTUAL_PORT,
                        Protocol.PULL_STATUS_REQUEST_PATH);
            } catch (Utils.ApplicationError e) {
                if (!e.getMessage().contains("No peer certificate")) {
                    throw e;
                }
                failed = true;
            }
            if (!failed) {
                throw new Utils.ApplicationError(LOG_TAG, "unexpected success");
            }

            // Re-run friend's Tor with an invalid hidden service auth cookie
            Log.addEntry(LOG_TAG, "Request from friend with invalid hidden service auth cookie...");
            friendTor.stop();
            friendHiddenServiceAuths = new ArrayList<TorWrapper.HiddenServiceAuth>();
            byte[] badAuthCookie = new byte[16]; // 128-bit value, as per spec
            new Random().nextBytes(badAuthCookie);
            friendHiddenServiceAuths.add(
                    new TorWrapper.HiddenServiceAuth(
                            self.mPublicIdentity.mHiddenServiceHostname,
                            Utils.encodeBase64(badAuthCookie).substring(0, 22)));
            friendTor = new TorWrapper(
                    TorWrapper.Mode.MODE_RUN_SERVICES,
                    "runComponentTests-friend",
                    friendHiddenServiceAuths,
                    friendHiddenServiceKeyMaterial,
                    friendWebServer.getListeningPort());
            friendTor.start();
            friendTor.awaitStarted();
            failed = false;
            try {
                response = WebClient.makeGetRequest(
                        friendX509KeyMaterial,
                        self.mPublicIdentity.mX509Certificate,
                        friendTor.getSocksProxyPort(),
                        self.mPublicIdentity.mHiddenServiceHostname,
                        Protocol.WEB_SERVER_VIRTUAL_PORT,
                        Protocol.PULL_STATUS_REQUEST_PATH);
            } catch (Utils.ApplicationError e) {
                if (!e.getMessage().contains("SOCKS4a connect failed")) {
                    throw e;
                }
                failed = true;
            }
            if (!failed) {
                throw new Utils.ApplicationError(LOG_TAG, "unexpected success");
            }

            Log.addEntry(LOG_TAG, "Component test run success");
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "Test failed");
        } finally {
            if (selfTor != null) {
                selfTor.stop();
            }
            if (friendTor != null) {
                friendTor.stop();
            }
            if (selfWebServer != null) {
                selfWebServer.stop();
            }
            if (selfRequestHandler != null) {
                selfRequestHandler.stop();
            }
            if (friendWebServer != null) {
                friendWebServer.stop();
            }
            if (friendRequestHandler != null) {
                friendRequestHandler.stop();
            }
        }
    }
    */
