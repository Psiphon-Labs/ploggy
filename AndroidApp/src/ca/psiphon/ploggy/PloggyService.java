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
import java.util.List;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.text.Html;
import android.util.Pair;

import com.squareup.otto.Subscribe;

/**
 * Android Service for hosting long-running Engine instance.
 */
public class PloggyService extends Service {

    private static final String LOG_TAG = "Service";

    private Engine mEngine;
    private Notification mNotification = null;

    public PloggyService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        try {
            Events.getInstance().register(this);
            mEngine = new Engine(this);
            mEngine.start();
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to start Engine");
            stopSelf();
            return;
        }
        doForeground();
    }

    @Override
    public void onDestroy() {
        Events.getInstance().unregister(this);
        if (mEngine != null) {
            mEngine.stop();
            mEngine = null;
        }
    }

    private void doForeground() {
        updateNotification();
        startForeground(R.string.foregroundServiceNotificationId, mNotification);
    }


    @Subscribe
    public void UpdatedFriendPost(Events.UpdatedFriendPost updatedFriendPost) {
        // Update the service notification with new posts
        updateNotification();
    }

    @Subscribe
    public void UpdatedFriendPost(Events.MarkedAsReadPosts markedAsReadPosts) {
        // Update the service notification after posts are read
        updateNotification();
    }

    private synchronized void updateNotification() {
        // Max, as per documentation: http://developer.android.com/reference/android/app/Notification.InboxStyle.html
        final int MAX_LINES = 5;

        List<Pair<String, String>> unreadPosts = new ArrayList<Pair<String, String>>();
        Data data = Data.getInstance(this);
        Data.CursorIterator<Data.Post> unreadPostsCursor = null;

        try {
            unreadPostsCursor = data.getUnreadPosts();
            for (int i = 0; i < MAX_LINES && unreadPostsCursor.hasNext(); i++) {
                Data.Post post = unreadPostsCursor.next();
                unreadPosts.add(
                    new Pair<String, String>(
                        post.mPost.mContent,
                        data.getFriendByIdOrThrow(post.mPost.mPublisherId).mPublicIdentity.mNickname));
            }
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to update notification");
            return;
        } finally {
            if (unreadPostsCursor != null) {
                unreadPostsCursor.close();
            }
        }

        // Invoke main Activity when notification is clicked
        Intent intent = new Intent(this, ActivityMain.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        int iconResourceId;
        String contentTitle;
        if (unreadPosts.size() > 0) {
            intent.setAction(ActivityMain.ACTION_DISPLAY_MESSAGES);
            iconResourceId = R.drawable.ic_notification_with_new_messages;
            contentTitle =
                    getResources().getQuantityString(
                        R.plurals.foreground_service_notification_content_title_with_new_messages,
                        unreadPosts.size(),
                        unreadPosts.size());
        } else {
            intent.setAction("android.intent.action.MAIN");
            iconResourceId = R.drawable.ic_notification_without_new_messages;
            contentTitle = getString(R.string.foreground_service_notification_content_title_without_new_messages);
        }

        PendingIntent pendingIntent =
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder notificationBuilder =
            new Notification.Builder(this)
                .setContentIntent(pendingIntent)
                .setContentTitle(contentTitle)
                .setSmallIcon(iconResourceId);

        Notification notification;
        if (unreadPosts.size() > 0) {
            // Use default (system) sound, lights, and vibrate
            notificationBuilder.setDefaults(Notification.DEFAULT_ALL);

            // Build email-style big view with summary of new messages
            Notification.InboxStyle inboxStyleBuilder =
                new Notification.InboxStyle(notificationBuilder);
            for (int i = 0; i < unreadPosts.size(); i++) {
                inboxStyleBuilder.addLine(
                    Html.fromHtml(
                        getString(
                            R.string.foreground_service_notification_inbox_line,
                            unreadPosts.get(i).first,
                            unreadPosts.get(i).second)));
            }
            if (unreadPosts.size() > MAX_LINES) {
                inboxStyleBuilder.setSummaryText(
                    getString(
                        R.string.foreground_service_notification_inbox_summary,
                        unreadPosts.size() - MAX_LINES));
            }
            notification = inboxStyleBuilder.build();
        } else {
            notification = notificationBuilder.build();
        }

        if (mNotification == null) {
            mNotification = notification;
        }
        else {
            // TODO: if other notification attributes are added (ie ticker), copy them here
            mNotification.contentIntent = notification.contentIntent;
            mNotification.bigContentView = notification.bigContentView;
            mNotification.contentView = notification.contentView;
            mNotification.icon = notification.icon;
            mNotification.largeIcon = notification.largeIcon;
            mNotification.defaults = notification.defaults;
        }

        NotificationManager notificationManager =
                (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(R.string.foregroundServiceNotificationId, mNotification);
        }
    }
}
