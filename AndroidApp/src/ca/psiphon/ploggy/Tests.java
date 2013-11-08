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
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ca.psiphon.ploggy.Data.Status;
import ca.psiphon.ploggy.Utils.ApplicationError;

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
                        Tests.runComponentTests();
                    }
                },
                2000);
    }
    
    private static class MockRequestHandler implements WebServer.RequestHandler {
        
        private ExecutorService mThreadPool = Executors.newCachedThreadPool();
        private Date mMockTimestamp;
        private double mMockLatitude;
        private double mMockLongitude;
        private String mMockAddress;

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
        public void submitTask(Runnable task) {
            mThreadPool.execute(task);
        }

        public Status getMockStatus() {
            return new Status(
                    mMockTimestamp,
                    mMockLatitude,
                    mMockLongitude,
                    10,
                    mMockAddress);
        }

        @Override
        public Status handlePullStatusRequest(String friendId) throws ApplicationError {
            Log.addEntry(LOG_TAG, "handle pull status request...");
            return getMockStatus();
        }

        @Override
        public void handlePushStatusRequest(String friendId, Status status) throws ApplicationError {
            Log.addEntry(LOG_TAG, "handle push status request...");
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
            String selfNickname = "Me";
            Log.addEntry(LOG_TAG, "Generate X509 key material...");
            X509.KeyMaterial selfX509KeyMaterial = X509.generateKeyMaterial();
            Log.addEntry(LOG_TAG, "Generate hidden service key material...");
            HiddenService.KeyMaterial selfHiddenServiceKeyMaterial = HiddenService.generateKeyMaterial();
            Log.addEntry(LOG_TAG, "Make self...");
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
            X509.KeyMaterial friendX509KeyMaterial = X509.generateKeyMaterial();
            HiddenService.KeyMaterial friendHiddenServiceKeyMaterial = HiddenService.generateKeyMaterial();
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
            Log.addEntry(LOG_TAG, "Make unfriendly key material...");
            X509.KeyMaterial unfriendlyX509KeyMaterial = X509.generateKeyMaterial();
            Log.addEntry(LOG_TAG, "Start self web server...");
            ArrayList<String> friendCertificates = new ArrayList<String>();
            friendCertificates.add(friend.mPublicIdentity.mX509Certificate);
            selfRequestHandler = new MockRequestHandler();
            selfWebServer = new WebServer(selfRequestHandler, selfX509KeyMaterial, friendCertificates);
            try {
                selfWebServer.start();
            } catch (IOException e) {
                throw new Utils.ApplicationError(LOG_TAG, e);
            }
            Log.addEntry(LOG_TAG, "Start friend web server...");
            ArrayList<String> selfCertificates = new ArrayList<String>();
            selfCertificates.add(self.mPublicIdentity.mX509Certificate);
            friendRequestHandler = new MockRequestHandler();
            friendWebServer = new WebServer(friendRequestHandler, friendX509KeyMaterial, selfCertificates);
            try {
                friendWebServer.start();
            } catch (IOException e) {
                throw new Utils.ApplicationError(LOG_TAG, e);
            }
            String response;
            String expectedResponse = Json.toJson(selfRequestHandler.getMockStatus());
            // Repeat multiple times to exercise keep-alive connection
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
                WebClient.makePostRequest(
                        friendX509KeyMaterial,
                        self.mPublicIdentity.mX509Certificate,
                        WebClient.UNTUNNELED_REQUEST,
                        "127.0.0.1",
                        selfWebServer.getListeningPort(),
                        Protocol.PUSH_STATUS_REQUEST_PATH,
                        expectedResponse);
            }
            Log.addEntry(LOG_TAG, "Run self Tor...");
            selfTor = new TorWrapper(
                    TorWrapper.Mode.MODE_RUN_SERVICES,
                    "runComponentTests-self",
                    selfHiddenServiceKeyMaterial,
                    selfWebServer.getListeningPort());
            selfTor.start();
            Log.addEntry(LOG_TAG, "Run friend Tor...");
            friendTor = new TorWrapper(
                    TorWrapper.Mode.MODE_RUN_SERVICES,
                    "runComponentTests-friend",
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
                        self.mPublicIdentity.getHiddenServiceHostnameUri(),
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
                        self.mPublicIdentity.getHiddenServiceHostnameUri(),
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
            // Log.addEntry(LOG_TAG, "Request to invalid friend...");
            // TODO: implement (create a distinct hidden service)
            // Log.addEntry(LOG_TAG, "Invalid request from friend...");
            // TODO: implement
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
}
