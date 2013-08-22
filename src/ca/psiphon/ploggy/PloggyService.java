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

public class PloggyService extends Service {
    
    Engine mEngine;

    public PloggyService() {
        mEngine = Engine.getInstance();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onCreate() {
        mEngine.start(this);
        doForeground();
    }
   
    @Override
    public void onDestroy() {
        mEngine.stop();
    }
    
    private void doForeground() {
        startForeground(R.string.foreground_service_notification_id, createNotification());
    }
    
    @SuppressWarnings("deprecation")
    private Notification createNotification() {
        int contextTitleID = R.string.app_name;
        int contentTextID = R.string.default_foreground_service_notification_message;
        int iconID = R.drawable.ic_launcher;

        // Invoke main Activity if notification is clicked
        Intent intent = new Intent("ACTION_VIEW", null, this, ca.psiphon.ploggy.MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);        
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    
        // Newer API (Notification.Builder) requires API level 11
        Notification notification = new Notification(iconID, null, System.currentTimeMillis());
        notification.setLatestEventInfo(this, getText(contextTitleID), getText(contentTextID), pendingIntent); 
        
        return notification;
    }
}
