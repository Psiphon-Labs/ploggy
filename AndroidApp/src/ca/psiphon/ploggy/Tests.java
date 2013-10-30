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

	public static void insertMockFriends() {
	    try {
            Data data = Data.getInstance();
            for (int i = 0; i < 50; i++) {
                String nickname = String.format((Locale)null, "Nickname%02d", i);
                // TODO: too slow
                //X509.KeyMaterial x509KeyMaterial = X509.generateKeyMaterial();
                X509.KeyMaterial x509KeyMaterial = new X509.KeyMaterial(Utils.getRandomHexString(1024), Utils.getRandomHexString(1024));
                // TODO: NetworkOnMainThreadException
                //HiddenService.KeyMaterial hiddenServiceKeyMaterial = HiddenService.generateKeyMaterial();
                HiddenService.KeyMaterial hiddenServiceKeyMaterial = new HiddenService.KeyMaterial(Utils.getRandomHexString(1024), Utils.getRandomHexString(1024));
                data.insertOrUpdateFriend(
                        new Data.Friend(
                                new Identity.PublicIdentity(
                                        nickname,
                                        x509KeyMaterial.mCertificate,
                                        hiddenServiceKeyMaterial.mHostname,
                                        "")));
            }
	    } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "insertMockFriends failed");
	    }
	}
	
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

	    public void stop() {
	        Utils.shutdownExecutorService(mThreadPool);
	    }
	    
        @Override
        public void submitTask(Runnable task) {
            mThreadPool.execute(task);
        }

        public Status getMockStatus() {
            return new Status(
                    Utils.getCurrentTimestamp(),
                    Math.random()*100.0 - 50.0,
                    Math.random()*100.0 - 50.0,
                    10,
                    "301 Front St W, Toronto, ON M5V 2T6");
        }

        @Override
        public Status handlePullStatusRequest(String friendId) throws ApplicationError {
            return getMockStatus();
        }

        @Override
        public void handlePushStatusRequest(String friendId, Status status) throws ApplicationError {
        }
	}
	
	public static void runComponentTests() {
	    MockRequestHandler mockRequestHandler = null;
	    WebServer webServer = null;
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
                            selfHiddenServiceKeyMaterial));
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
                            friendHiddenServiceKeyMaterial));
            Data.Friend friend = new Data.Friend(friendSelf.mPublicIdentity);
            Log.addEntry(LOG_TAG, "Make unfriendly key material...");
            X509.KeyMaterial unfriendlyX509KeyMaterial = X509.generateKeyMaterial();
            Log.addEntry(LOG_TAG, "Start web server...");
            ArrayList<String> friendCertificates = new ArrayList<String>();
            friendCertificates.add(friend.mPublicIdentity.mX509Certificate);
            mockRequestHandler = new MockRequestHandler();
            webServer = new WebServer(mockRequestHandler, selfX509KeyMaterial, friendCertificates);
            try {
                webServer.start();
            } catch (IOException e) {
                throw new Utils.ApplicationError(LOG_TAG, e);
            }
            Log.addEntry(LOG_TAG, "Direct request from valid friend...");
            String response = WebClient.makeGetRequest(
                    friendX509KeyMaterial,
                    self.mPublicIdentity.mX509Certificate,
                    WebClient.UNTUNNELED_REQUEST,
                    "127.0.0.1",
                    webServer.getListeningPort(),
                    Protocol.PULL_STATUS_REQUEST_PATH);
            Protocol.validateStatus(Json.fromJson(response, Data.Status.class));
            if (!response.equals(Json.toJson(mockRequestHandler.getMockStatus()))) {
                throw new Utils.ApplicationError(LOG_TAG, "unexpected status response value");
            }
            Log.addEntry(LOG_TAG, "Run self Tor...");
            selfTor = new TorWrapper(
                    TorWrapper.Mode.MODE_RUN_SERVICES,
                    "runComponentTests-self",
                    selfHiddenServiceKeyMaterial,
                    webServer.getListeningPort());
            selfTor.start();
            Log.addEntry(LOG_TAG, "Run friend Tor...");
            // TODO: distinct web server
            friendTor = new TorWrapper(
                    TorWrapper.Mode.MODE_RUN_SERVICES,
                    "runComponentTests-friend",
                    friendHiddenServiceKeyMaterial,
                    webServer.getListeningPort());
            friendTor.start();
            selfTor.start();
            selfTor.awaitStarted();
            friendTor.awaitStarted();
            // TODO: monitor publication state via Tor control interface?
            int publishWaitMilliseconds = 120000;
            Log.addEntry(LOG_TAG, String.format("Wait %d ms. while hidden service is published...", publishWaitMilliseconds));
            try {
                Thread.sleep(publishWaitMilliseconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Log.addEntry(LOG_TAG, "Request from valid friend...");
            response = WebClient.makeGetRequest(
                    friendX509KeyMaterial,
                    self.mPublicIdentity.mX509Certificate,
                    friendTor.getSocksProxyPort(),
                    // TODO: helper; and/or encode pubic identity differently?
                    new String(Utils.decodeBase64(self.mPublicIdentity.mHiddenServiceHostname)).trim(),
                    Protocol.WEB_SERVER_VIRTUAL_PORT,
                    Protocol.PULL_STATUS_REQUEST_PATH);
            Protocol.validateStatus(Json.fromJson(response, Data.Status.class));
            if (!response.equals(Json.toJson(mockRequestHandler.getMockStatus()))) {
                throw new Utils.ApplicationError(LOG_TAG, "unexpected status response value");
            }
            Log.addEntry(LOG_TAG, "Request from invalid friend...");
            boolean failed = false;
            try {
                WebClient.makeGetRequest(
                        unfriendlyX509KeyMaterial,
                        self.mPublicIdentity.mX509Certificate,
                        friendTor.getSocksProxyPort(),
                        new String(Utils.decodeBase64(self.mPublicIdentity.mHiddenServiceHostname)).trim(),
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
            Log.addEntry(LOG_TAG, "Request to invalid friend...");
            // TODO: implement (create a distinct hidden service)
            Log.addEntry(LOG_TAG, "Invalid request from friend...");
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
	        if (webServer != null) {
	            webServer.stop();
	        }
	        if (mockRequestHandler != null) {
	            mockRequestHandler.stop();
	        }
	    }
	}
}
