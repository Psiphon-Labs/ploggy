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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;

public class Engine {

    // ---- Singleton ----
    private static Engine instance = null;
    public static synchronized Engine getInstance() {
       if(instance == null) {
          instance = new Engine();
       }
       return instance;
    }
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
    // -------------------
    
    private long mFriendPollPeriod;
    private Timer mTimer;
    private ExecutorService mTaskThreadPool;
    private LocationMonitor mLocationMonitor;
    private WebServer mWebServer;
    private TorWrapper mTorWrapper;

    private Engine() {
        Utils.initSecureRandom();
    }    

    public synchronized void start() throws Utils.ApplicationError {
        Events.register(this);
        mTaskThreadPool = Executors.newCachedThreadPool();
        mTimer = new Timer();
        mLocationMonitor = new LocationMonitor(Utils.getApplicationContext());
        mLocationMonitor.start();
        // TODO: check Data.getInstance().hasSelf()...
        startSharingService();
        initFriendPollPeriod();
        schedulePollFriends();
        // TODO: Events.bus.post(new Events.EngineRunning()); ?
    }

    public synchronized void stop() {
        Events.unregister(this);
        stopSharingService();
        if (mLocationMonitor != null) {
        	mLocationMonitor.stop();
        	mLocationMonitor = null;
        }
        if (mTaskThreadPool != null) {
	        try
	        {
	            mTaskThreadPool.shutdown();
	            if (!mTaskThreadPool.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
	                mTaskThreadPool.shutdownNow();
	                mTaskThreadPool.awaitTermination(100, TimeUnit.MILLISECONDS);                
	            }
	        }
	        catch (InterruptedException e)
	        {
	            Thread.currentThread().interrupt();
	        }
	        mTaskThreadPool = null;
        }
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        // TODO: Events.bus.post(new Events.EngineStopped()); ?
    }

    public synchronized void submitTask(Runnable task) {
        mTaskThreadPool.submit(task);
    }
    
    private class ScheduledTask extends TimerTask {
        Runnable mTask;
        public ScheduledTask(Runnable task) {
            mTask = task;
        }
        @Override
        public void run() {
            mTaskThreadPool.submit(mTask);
        }
    }
    
    public synchronized void scheduleTask(Runnable task, long delayMilliseconds) {
        mTimer.schedule(new ScheduledTask(task), delayMilliseconds);
    }
    
    private void startSharingService() throws Utils.ApplicationError {
        try {
            Data.Self self = Data.getInstance().getSelf();
            stopSharingService();
            mWebServer = new WebServer(self.mTransportKeyMaterial);
            mWebServer.start();
            mTorWrapper = new TorWrapper(self.mHiddenServiceKeyMaterial, mWebServer.getListeningPort());
            mTorWrapper.start();
        } catch (Data.DataNotFoundException e) {
            throw new Utils.ApplicationError(e);
        } catch (IOException e) {
            throw new Utils.ApplicationError(e);
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
    
    public synchronized Proxy getLocalProxy() throws Utils.ApplicationError {
        if (mTorWrapper != null) {
            return new Proxy(
                    Proxy.Type.SOCKS,
                    new InetSocketAddress(TorWrapper.SOCKS_PROXY_HOSTNAME, mTorWrapper.getSocksProxyPort()));
        }
        throw new Utils.ApplicationError();
    }
    
    @Subscribe
    private synchronized void handleRequestUpdatePreferences(
            Events.RequestUpdatePreferences requestUpdatePreferences) {
        // TODO: update prefs (task)
        // TODO: Events.bus.post(new Events.UpdatedPreferences());
    }

    @Subscribe
    private synchronized void handleRequestGenerateSelf(
            Events.RequestGenerateSelf requestGenerateSelf) {
        
        // TODO: check if already in progress?

        final Events.RequestGenerateSelf taskRequestGenerateSelf = requestGenerateSelf;
        Runnable task = new Runnable() {
                public void run() {
                    try {
                        // TODO: validate nickname?
                        // TODO: cancellable generation?
                        stopSharingService();
                        Data.Self self = new Data.Self(
                                taskRequestGenerateSelf.mNickname,
                                TransportSecurity.KeyMaterial.generate(),
                                HiddenService.KeyMaterial.generate());
                        Data.getInstance().updateSelf(self);
                        Events.post(new Events.GeneratedSelf(self));
                    } catch (/*TEMP*/Exception e) {
                        Events.post(new Events.RequestFailed(taskRequestGenerateSelf.mRequestId, e.getMessage()));
                    } finally {
                        // Apply new transport and hidden service credentials, or restart with old settings on error
                        try {
                            startSharingService();
                        } catch (Utils.ApplicationError e) {
                            // TODO: ...
                        }                        
                    }
                }
            };
        mTaskThreadPool.submit(task);
    }
    
    @Produce
    private synchronized Events.GeneratedSelf produceGeneratedSelf() {
        // TODO: ...
        return null;
    }

    @Subscribe
    private synchronized void handleRequestDecodeFriend(
            Events.RequestDecodeFriend requestDecodeFriend)  {
        // TODO: ...
    }

    @Subscribe
    private synchronized void handleRequestAddFriend(
            Events.RequestAddFriend requestAddFriend)  {
        // ...[re-]validate
        // ...insert or update data
        // ... update trust manager back end?
        // ...schedule polling (if new) with schedulePollFriend()
        // TODO: ...
    }

    @Subscribe
    private synchronized void handleRequestDeleteFriend(
            Events.RequestDeleteFriend requestDeleteFriend) {
        // ...doesn't cancel polling
        // TODO: ...
    }

    @Produce
    private synchronized Events.NewSelfStatus produceNewSelfStatus() {
        // TODO: ...
        return null;
    }
    
    private void initFriendPollPeriod() {
        // TODO: adjust for foreground, battery, sleep, network type 
        mFriendPollPeriod = 60*1000;
    }

    private void schedulePollFriends() throws Utils.ApplicationError {
        for (Data.Friend friend : Data.getInstance().getFriends()) {
            schedulePollFriend(friend.mId, true);
        }
    }
    
    private void schedulePollFriend(String friendId, boolean initialRequest) {
        final String taskFriendId = friendId;
        Runnable task = new Runnable() {
            public void run() {
                try {
                    Data.Self self = Data.getInstance().getSelf();
                    Data.Friend friend = Data.getInstance().getFriendById(taskFriendId);
                    String response = WebClient.makeGetRequest(
                            self.mTransportKeyMaterial,
                            friend.mTransportCertificate,
                            friend.mHiddenServiceIdentity,
                            Protocol.GET_STATUS_REQUEST_PATH,
                            null);
                    Data.Status friendStatus = Json.fromJson(response, Data.Status.class);
                    Events.post(new Events.NewFriendStatus(friendStatus));
                    // Schedule next poll
                    Engine.getInstance().schedulePollFriend(taskFriendId, false);
                } catch (Data.DataNotFoundException e) {
                    // Next poll won't be scheduled
                } catch (Utils.ApplicationError e) {
                    // TODO: ...?
                }
            }
        };
        long delay = initialRequest ? 0 : mFriendPollPeriod;
        scheduleTask(task, delay);
    }
}
