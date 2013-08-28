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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ca.psiphon.ploggy.TransportSecurity.HiddenServiceIdentity;
import ca.psiphon.ploggy.TransportSecurity.TransportKeyPair;

import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;

import de.schildbach.wallet.util.LinuxSecureRandom;

import android.content.Context;

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
    
    private ExecutorService mTaskThreadPool;
    private LocationMonitor mLocationMonitor;

    private Engine() {
        new LinuxSecureRandom();
    }    

    public synchronized void start(Context context) {
        Events.bus.register(this);        
        mTaskThreadPool = Executors.newCachedThreadPool();
        mLocationMonitor = new LocationMonitor(context);
        mLocationMonitor.start();        
    }

    public synchronized void stop() {
        Events.bus.unregister(this);
        mLocationMonitor.stop();        
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
    }

    @Subscribe
    public synchronized void handleRequestUpdatePreferences(
            Events.RequestUpdatePreferences requestUpdatePreferences) {
    }

    @Subscribe
    public synchronized void handleRequestGenerateSelf(
            Events.RequestGenerateSelf requestGenerateSelf) {

        // TODO: check if already in progress

        Runnable task = new Runnable() {
                public void run() {
                    // TODO: cancellable
                    // TODO: catch errors
                    // TODO: Self
                    TransportKeyPair transportKeyPair = TransportSecurity.generateTransportKeyPair();
                    HiddenServiceIdentity hiddenServiceIdentity = TransportSecurity.generateHiddenServiceIdentity();
                    Data.getInstance().updateSelf(new Data.Self());
                    Events.bus.post(new Events.GeneratedSelf());
                    }
            };
        mTaskThreadPool.submit(task);
    }
    
    @Produce
    public synchronized Events.GeneratedSelf produceGeneratedSelf() {
        return null;
    }

    @Subscribe
    public synchronized void handleRequestDecodeFriend(
            Events.RequestDecodeFriend requestDecodeFriend)  {
    }

    @Subscribe
    public synchronized void handleRequestAddFriend(
            Events.RequestAddFriend requestAddFriend)  {
    }

    @Subscribe
    public synchronized void handleRequestDeleteFriend(
            Events.RequestDeleteFriend requestDeleteFriend) {
    }

    public static class PreparedNewLocationPackage {
    }    

    @Produce
    public synchronized Events.PreparedNewLocationPackage producePreparedNewLocationPackage() {
        return null;
    }    

    /*
    public synchronized Data.Self generateSelf() {
        return null;
    }

    public synchronized void addFriend(Data.Friend friend) {
        
    }
    
    public synchronized void removeFriend(Data.Friend friend) {
        
    }
    
    public synchronized void updateLocation(Data.Location location) {        
    }

    public synchronized Data.Location getSelfLocation() {
        return null;
    }

    public synchronized Data.Location getFriendLocation() {        
        return null;
    }
    */
}
