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
            mEngine = new Engine();
            mEngine.start();
        } catch (PloggyError e) {
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
        startForeground(R.string.foregroundServiceNotificationId, makeForegroundNotification());
        updateUnreadPostsNotifications();
    }

    @Subscribe
    public void UpdatedFriendPost(Events.UpdatedFriendPost updatedFriendPost) {
        // *TODO* don't update notification if group post list currently displayed?
        updateUnreadPostsNotification(
                updatedFriendPost.mGroupId,
                updatedFriendPost.mGroupName);
    }

    @Subscribe
    public void markedAsReadPosts(Events.MarkedAsReadPosts markedAsReadPosts) {
        updateUnreadPostsNotification(
                markedAsReadPosts.mGroupId,
                markedAsReadPosts.mGroupName);
    }

    private Notification makeForegroundNotification() {
        Intent intent = new Intent(this, ActivityMain.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction("android.intent.action.MAIN");
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder notificationBuilder =
                new Notification.Builder(this)
                    .setContentIntent(pendingIntent)
                    .setContentTitle(getString(R.string.foreground_service_notification_content_title))
                    .setSmallIcon(R.drawable.ic_notification_without_new_messages);

        return notificationBuilder.build();
    }

    private void updateUnreadPostsNotifications() {
        Data data = Data.getInstance();
        Data.ObjectCursor<Data.Group> unreadPostsGroups = null;
        for (Data.Group group : data.getUnreadPostGroupsIterator()) {
            updateUnreadPostsNotification(
                    group.mGroup.mId,
                    group.mGroup.mName);
        }
    }

    private void updateUnreadPostsNotification(String groupId, String groupName) {
        // Max, as per documentation: http://developer.android.com/reference/android/app/Notification.InboxStyle.html
        final int MAX_LINES = 5;

        Data data = Data.getInstance();
        Data.ObjectCursor<Data.Post> unreadPostsCursor = null;
        List<Pair<String, String>> unreadPosts = new ArrayList<Pair<String, String>>();
        int additionalLines = 0;
        try {
            // *TODO* not using background thread? use AsyncTask?
            unreadPostsCursor = data.getUnreadPosts(groupId);
            additionalLines = unreadPostsCursor.getCount() - MAX_LINES;
            for (int i = 0; i < MAX_LINES && unreadPostsCursor.hasNext(); i++) {
                Data.Post post = unreadPostsCursor.next();
                unreadPosts.add(
                    new Pair<String, String>(
                        post.mPost.mContent,
                        data.getFriendByIdOrThrow(post.mPost.mPublisherId).mPublicIdentity.mNickname));
            }
        } catch (PloggyError e) {
            Log.addEntry(LOG_TAG, "failed to update notification");
            return;
        } finally {
            if (unreadPostsCursor != null) {
                unreadPostsCursor.close();
            }
        }

        NotificationManager notificationManager =
                (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            Log.addEntry(LOG_TAG, "failed to update notification");
            return;
        }

        if (unreadPosts.size() == 0) {
            // Clear any existing notification
            notificationManager.cancel(groupId, R.string.unreadPostsNotificationId);
        } else {
            // Create or update notification
            Intent intent = ActivityMain.makeDisplayViewIntent(
                    this, new ActivityMain.ViewTag(ActivityMain.ViewType.GROUP_POSTS, groupId));
            PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            Notification.Builder notificationBuilder =
                new Notification.Builder(this)
                    .setContentIntent(pendingIntent)
                    .setContentTitle(
                            getResources().getQuantityString(
                                R.plurals.unread_posts_notification_content_title,
                                unreadPosts.size(),
                                unreadPosts.size()))
                    .setContentInfo(groupName)
                    .setSmallIcon(R.drawable.ic_notification_with_new_messages)
                    // Use default (system) sound, lights, and vibrate
                    .setDefaults(Notification.DEFAULT_ALL);

            // Build email-style big view with summary of new messages
            Notification.InboxStyle inboxStyleBuilder =
                new Notification.InboxStyle(notificationBuilder);
            for (Pair<String, String> unreadPost : unreadPosts) {
                inboxStyleBuilder.addLine(
                    Html.fromHtml(
                        getString(
                            R.string.unread_posts_notification_inbox_line,
                            unreadPost.first,
                            unreadPost.second)));
            }
            if (additionalLines > 0) {
                inboxStyleBuilder.setSummaryText(
                    getString(
                        R.string.unread_posts_notification_inbox_summary,
                        additionalLines));
            }

            Notification notification = inboxStyleBuilder.build();

            notificationManager.notify(groupId, R.string.unreadPostsNotificationId, notification);
        }
    }
}
