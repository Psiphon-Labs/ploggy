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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Tests {
	
    private static final String LOG_TAG = "Tests";

	private static Timer mTimer = new Timer();

	public static void insertMockFriends() {
	    try {
            Data data = Data.getInstance();
            for (int i = 0; i < 5; i++) {
                String nickname = String.format((Locale)null, "Nickname%02d", i);
                X509.KeyMaterial x509KeyMaterial = X509.generateKeyMaterial();
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
            Log.addEntry(LOG_TAG, String.format("insertMockFriends failed: %s", e.getMessage()));
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
	
	public static void runComponentTests() {
	    ExecutorService threadPool = null;
	    WebServer webServer = null;
	    TorWrapper tor = null;
        String response;
	    try {
	        threadPool = Executors.newCachedThreadPool();
	        String selfNickname = "Me";
            Log.addEntry(LOG_TAG, "Generate X509 key material...");
            X509.KeyMaterial selfX509KeyMaterial = X509.generateKeyMaterial();
            Log.addEntry(LOG_TAG, "Generate hidden service key material...");
            HiddenService.KeyMaterial selfHiddenServiceKeyMaterial =HiddenService.generateKeyMaterial();
            Log.addEntry(LOG_TAG, "Make self...");
            Data.Self self = new Data.Self(
                    Identity.makeSignedPublicIdentity(
                            selfNickname,
                            selfX509KeyMaterial,
                            selfHiddenServiceKeyMaterial),
                    Identity.makePrivateIdentity(
                            selfX509KeyMaterial,
                            selfHiddenServiceKeyMaterial));
            // TODO: dependency injection
            Data.Status selfStatus = new Data.Status(
                    DateFormat.getDateTimeInstance().format(new Date()),
                    "0",
                    "0",
                    "<street>\n<city>\n<state>\n<country>\n");
            Data.getInstance().updateSelfStatus(selfStatus);
            Log.addEntry(LOG_TAG, "Make friend...");
            String friendNickname = "My Friend";
            X509.KeyMaterial friendX509KeyMaterial = X509.generateKeyMaterial();
            HiddenService.KeyMaterial friendHiddenServiceKeyMaterial = HiddenService.generateKeyMaterial();
            Data.Friend friend = new Data.Self(
                    Identity.makeSignedPublicIdentity(
                            friendNickname,
                            friendX509KeyMaterial,
                            friendHiddenServiceKeyMaterial),
                    Identity.makePrivateIdentity(
                            friendX509KeyMaterial,
                            friendHiddenServiceKeyMaterial)).getFriend();
            Log.addEntry(LOG_TAG, "Make unfriendly key material...");
            X509.KeyMaterial unfriendlyX509KeyMaterial = X509.generateKeyMaterial();
            Log.addEntry(LOG_TAG, "Start web server...");
            ArrayList<String> friendCertificates = new ArrayList<String>();
            friendCertificates.add(friend.mPublicIdentity.mX509Certificate);
            webServer = new WebServer(threadPool, selfX509KeyMaterial, friendCertificates);
            try {
                webServer.start();
            } catch (IOException e) {
                throw new Utils.ApplicationError(e);
            }
            Log.addEntry(LOG_TAG, "Direct request from valid friend...");
            response = WebClient.makeGetRequest(
                    friendX509KeyMaterial,
                    self.mPublicIdentity.mX509Certificate,
                    null,
                    "127.0.0.1",
                    webServer.getListeningPort(),
                    Protocol.GET_STATUS_REQUEST_PATH,
                    null);
            Protocol.validateStatus(Json.fromJson(response, Data.Status.class));
            if (!response.equals(Json.toJson(selfStatus))) {
                Log.addEntry(LOG_TAG, "Unexpected status response value");
                throw new Utils.ApplicationError();
            }
            Log.addEntry(LOG_TAG, "Run hidden service...");
            tor = new TorWrapper(
                    TorWrapper.Mode.MODE_RUN_SERVICES,
                    selfHiddenServiceKeyMaterial,
                    webServer.getListeningPort());
            tor.start();
            Proxy torProxy = new Proxy(
                    Proxy.Type.SOCKS,
                    new InetSocketAddress("127.0.0.1", tor.getSocksProxyPort()));
            // TODO: distinct Tor instance for friend?
            Log.addEntry(LOG_TAG, "Request from valid friend...");
            response = WebClient.makeGetRequest(
                    friendX509KeyMaterial,
                    self.mPublicIdentity.mX509Certificate,
                    torProxy,
                    self.mPublicIdentity.mHiddenServiceHostname,
                    Protocol.WEB_SERVER_VIRTUAL_PORT,
                    Protocol.GET_STATUS_REQUEST_PATH,
                    null);
            Protocol.validateStatus(Json.fromJson(response, Data.Status.class));
            if (!response.equals(Json.toJson(selfStatus))) {
                Log.addEntry(LOG_TAG, "Unexpected status response value");
                throw new Utils.ApplicationError();
            }
            Log.addEntry(LOG_TAG, "Request from invalid friend...");
            boolean failed = false;
            try {
                WebClient.makeGetRequest(
                        unfriendlyX509KeyMaterial,
                        self.mPublicIdentity.mX509Certificate,
                        torProxy,
                        self.mPublicIdentity.mHiddenServiceHostname,
                        Protocol.WEB_SERVER_VIRTUAL_PORT,
                        Protocol.GET_STATUS_REQUEST_PATH,
                        null);
            } catch (Utils.ApplicationError e) {
                if (!e.getMessage().equals("TODO")) {
                    throw e;
                }
                failed = true;
            }
            if (!failed) {
                Log.addEntry(LOG_TAG, "Unexpected success");
                throw new Utils.ApplicationError();
            }
            Log.addEntry(LOG_TAG, "Request to invalid friend...");
            // TODO: implement (create a distinct hidden service)
            Log.addEntry(LOG_TAG, "Invalid request from friend...");
            // TODO: implement
            Log.addEntry(LOG_TAG, "Stop hidden service...");
            tor.stop();
            Log.addEntry(LOG_TAG, "Stop web server...");
            webServer.stop();
	    } catch (Utils.ApplicationError e) {
	        Log.addEntry(LOG_TAG, String.format("Test failed: %s", e.getMessage()));
	    } finally {
	        if (tor != null) {
	            tor.stop();
	        }
	        if (webServer != null) {
	            webServer.stop();
	        }
	        if (threadPool != null) {
	            Utils.shutdownExecutorService(threadPool);
	        }
	        Log.addEntry(LOG_TAG, "Component test run completed");
	    }
	}
}
