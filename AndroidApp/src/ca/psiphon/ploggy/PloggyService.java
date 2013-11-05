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

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Android Service for hosting long-running Engine instance.
 */
public class PloggyService extends Service {
    
    Engine mEngine;

    public PloggyService() {
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onCreate() {
        try {
            mEngine = new Engine(this);
            mEngine.start();
        } catch (Utils.ApplicationError e) {
            // TODO: log
        }
        doForeground();
    }
   
    @Override
    public void onDestroy() {
        if (mEngine != null) {
            mEngine.stop();
            mEngine = null;
        }
    }
    
    private void doForeground() {
        startForeground(R.string.foregroundServiceNotificationId, createNotification());
    }
    
    private Notification createNotification() {
        int titleID = R.string.app_name;
        int contentTextID = R.string.default_foreground_service_notification_message;
        int iconID = R.drawable.ic_launcher;

        // Invoke main Activity when notification is clicked
        Intent intent = new Intent("ACTION_VIEW", null, this, ca.psiphon.ploggy.ActivityMain.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);        
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    
        // Newer API (Notification.Builder) requires API level 11
        Notification notification
            = new Notification.Builder(this)
                .setContentIntent(pendingIntent)
                .setContentTitle(getText(titleID))
                .setContentText(getText(contentTextID))
                .setSmallIcon(iconID)
                .build();

        return notification;
    }
}
