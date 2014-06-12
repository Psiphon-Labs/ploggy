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

import java.io.File;

import android.content.Context;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * List adapter which displays rows of friends/groups/posts.
 *
 * Adapters wrap Data.ObjectCursor. Supports display of
 * very large number of items without loading all into memory.
 *
 * Post adapter includes widgets/logic for managing downloads of
 * message attachments. When the message belongs to self, message
 * attachments are treated as local resources, not downloads.
 */
public class Adapters {

    private static final String LOG_TAG = "Adapters";

    public interface CursorFactory<T> {
        Data.ObjectCursor<T> makeCursor() throws PloggyError;
    }

    public static class FriendAdapter extends ObjectCursorAdapter<Data.Friend> {

        public FriendAdapter(Context context, CursorFactory<Data.Friend> cursorFactory) {
            super(context, cursorFactory);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context context = getContext();
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.friend_list_row, null);
            }

            ImageView avatarImage = (ImageView)view.findViewById(R.id.friend_list_avatar_image);
            TextView nicknameText = (TextView)view.findViewById(R.id.friend_list_nickname_text);
            TextView lastTimestampText = (TextView)view.findViewById(R.id.friend_list_last_timestamp_text);
            TextView postTimestampText = (TextView)view.findViewById(R.id.friend_list_post_timestamp_text);
            TextView postGroupText = (TextView)view.findViewById(R.id.friend_list_post_group_text);
            TextView postContentText = (TextView)view.findViewById(R.id.friend_list_post_content_text);
            TextView locationTimestampText = (TextView)view.findViewById(R.id.friend_list_location_timestamp_text);
            TextView locationStreetAddressText = (TextView)view.findViewById(R.id.friend_list_location_street_address_text);
            TextView locationDistanceText = (TextView)view.findViewById(R.id.friend_list_location_distance_text);

            // Not hiding missing fields
            lastTimestampText.setText("");
            postTimestampText.setText("");
            postContentText.setText("");
            locationTimestampText.setText("");
            locationStreetAddressText.setText("");
            locationDistanceText.setText("");

            try {
                Data.Friend friend = getItem(position);
                if (friend == null) {
                    throw new PloggyError(LOG_TAG, "item not found");
                }

                Data data = Data.getInstance();

                Avatar.setAvatarImage(context, avatarImage, friend.mPublicIdentity);
                nicknameText.setText(friend.mPublicIdentity.mNickname);

                // *TODO* don't do sub-queries here, do in background thread

                Protocol.Location selfLocation = null;
                try {
                    selfLocation = data.getSelfLocation();
                } catch (Data.NotFoundError e) {
                    // Won't be able to compute distance
                }
                Protocol.Location friendLocation = null;
                try {
                    friendLocation = data.getFriendLocation(friend.mId);
                } catch (Data.NotFoundError e) {
                    // Won't be able to display friend location
                }

                // Display most recent successful communication timestamp
                String lastTimestamp = "";
                if (friend.mLastReceivedFromTimestamp != null &&
                        (friend.mLastSentToTimestamp == null ||
                         friend.mLastReceivedFromTimestamp.after(friend.mLastSentToTimestamp))) {
                    lastTimestamp = Utils.DateFormatter.formatRelativeDatetime(context, friend.mLastReceivedFromTimestamp, true);
                } else if (friend.mLastSentToTimestamp != null) {
                    lastTimestamp = Utils.DateFormatter.formatRelativeDatetime(context, friend.mLastSentToTimestamp, true);
                }
                lastTimestampText.setText(lastTimestamp);
                if (lastTimestamp.length() > 0) {
                    // On touch, show log entries
                    lastTimestampText.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Context context = getContext();
                                context.startActivity(
                                        ActivityMain.makeDisplayViewIntent(
                                                context,
                                                new ActivityMain.ViewTag(ActivityMain.ViewType.LOG_ENTRIES)));
                            }
                        });
                }

                // *TODO* Data.NotFoundError --> messageTimestampText.setText(R.string.prompt_no_status_updates_received);

                Data.Post mostRecentPost = null;
                try {
                    mostRecentPost = data.getMostRecentPostByFriend(friend.mId);
                } catch (Data.NotFoundError e) {
                    // No recent post to display
                }
                if (mostRecentPost != null) {
                    postGroupText.setText(data.getGroupOrThrow(mostRecentPost.mPost.mGroupId).mGroup.mName);
                    postContentText.setText(mostRecentPost.mPost.mContent);
                    postTimestampText.setText(
                            Utils.DateFormatter.formatRelativeDatetime(context, mostRecentPost.mPost.mModifiedTimestamp, true));
                } else {
                    postContentText.setText(R.string.no_posts);
                }

                if (friendLocation != null) {
                    locationTimestampText.setText(
                            Utils.DateFormatter.formatRelativeDatetime(context, friendLocation.mTimestamp, true));
                    if (friendLocation.mStreetAddress.length() > 0) {
                        locationStreetAddressText.setText(friendLocation.mStreetAddress);
                    } else {
                        locationStreetAddressText.setText(R.string.prompt_no_street_address_reported);
                    }
                    if (selfLocation != null && selfLocation.mTimestamp != null) {
                        int distance = Utils.calculateLocationDistanceInMeters(
                                selfLocation.mLatitude,
                                selfLocation.mLongitude,
                                friendLocation.mLatitude,
                                friendLocation.mLongitude);
                        locationDistanceText.setText(Utils.formatDistance(context, distance));
                    } else {
                        locationDistanceText.setText(R.string.prompt_unknown_distance);
                    }
                } else {
                    locationStreetAddressText.setText(R.string.no_location);
                }
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to display friend");
                // *TODO* view.<field>.setBlank();
            }

            return view;
        }
    }

    public static class CandidateFriendAdapter extends ObjectCursorAdapter<Data.CandidateFriend> {

        public CandidateFriendAdapter(Context context, CursorFactory<Data.CandidateFriend> cursorFactory) {
            super(context, cursorFactory);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context context = getContext();
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.candidate_friend_list_row, null);
            }

            ImageView avatarImage = (ImageView)view.findViewById(R.id.candidate_friend_list_avatar_image);
            TextView nicknameText = (TextView)view.findViewById(R.id.candidate_friend_list_nickname_text);
            TextView groupsText = (TextView)view.findViewById(R.id.candidate_friend_list_groups_text);

            try {
                Data.CandidateFriend candidateFriend = getItem(position);
                if (candidateFriend == null) {
                    throw new PloggyError(LOG_TAG, "item not found");
                }

                Data data = Data.getInstance();

                Avatar.setAvatarImage(context, avatarImage, candidateFriend.mPublicIdentity);
                nicknameText.setText(candidateFriend.mPublicIdentity.mNickname);

                // *TODO* don't do sub-queries here, do in background thread

                StringBuilder groups = new StringBuilder();
                for (String groupId : candidateFriend.mGroupIds) {
                    if (groups.length() > 0) {
                        groups.append(context.getString(R.string.candidate_friend_list_group_seperator));
                    }
                    groups.append(data.getGroupOrThrow(groupId).mGroup.mName);
                }
                groupsText.setText(groups.toString());
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to display candidate friend");
                // *TODO* view.<field>.setBlank();
            }

            return view;
        }
    }

    public static class CandidateGroupMemberAdapter extends ObjectCursorAdapter<Data.Friend> {

        public CandidateGroupMemberAdapter(Context context, CursorFactory<Data.Friend> cursorFactory) {
            super(context, cursorFactory);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context context = getContext();
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.group_member_list_row, null);
            }

            ImageView avatarImage = (ImageView)view.findViewById(R.id.group_member_list_avatar_image);
            TextView nicknameText = (TextView)view.findViewById(R.id.group_member_list_name_text);

            try {
                Data.Friend friend = getItem(position);
                if (friend == null) {
                    throw new PloggyError(LOG_TAG, "item not found");
                }

                Avatar.setAvatarImage(context, avatarImage, friend.mPublicIdentity);
                nicknameText.setText(friend.mPublicIdentity.mNickname);

            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to display group member");
                // *TODO* view.<field>.setBlank();
            }

            return view;
        }
    }

    public static class GroupMemberArrayAdapter extends ArrayAdapter<Identity.PublicIdentity> {

        public GroupMemberArrayAdapter(Context context) {
            super(context, R.layout.group_member_list_row);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context context = getContext();
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.group_member_list_row, null);
            }

            ImageView avatarImage = (ImageView)view.findViewById(R.id.group_member_list_avatar_image);
            TextView nicknameText = (TextView)view.findViewById(R.id.group_member_list_name_text);

            try {
                Identity.PublicIdentity publicIdentity = getItem(position);
                if (publicIdentity == null) {
                    throw new PloggyError(LOG_TAG, "item not found");
                }

                Avatar.setAvatarImage(context, avatarImage, publicIdentity);
                nicknameText.setText(publicIdentity.mNickname);
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to display group member");
                // *TODO* view.<field>.setBlank();
            }

            return view;
        }
    }

    public static class GroupAdapter extends ObjectCursorAdapter<Data.Group> {

        public GroupAdapter(Context context, CursorFactory<Data.Group> cursorFactory) {
            super(context, cursorFactory);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context context = getContext();
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.group_list_row, null);
            }

            ImageView avatarImage = (ImageView)view.findViewById(R.id.group_list_avatar_image);
            TextView nameText = (TextView)view.findViewById(R.id.group_list_name_text);
            TextView postPublisherText = (TextView)view.findViewById(R.id.group_list_post_publisher_text);
            TextView postContentText = (TextView)view.findViewById(R.id.group_list_post_content_text);
            TextView postTimestampText = (TextView)view.findViewById(R.id.group_list_post_timestamp_text);

            // Not hiding missing fields
            nameText.setText("");
            postPublisherText.setText("");
            postContentText.setText("");
            postTimestampText.setText("");

            try {
                Data.Group group = getItem(position);
                if (group == null) {
                    throw new PloggyError(LOG_TAG, "item not found");
                }

                Data data = Data.getInstance();

                Avatar.setGroupAvatarImage(context, avatarImage, data.getSelfId(), group.mGroup);

                nameText.setText(group.mGroup.mName);

                // *TODO* don't do sub-queries here, do in background thread

                Data.Post mostRecentPost = null;
                try {
                    mostRecentPost = data.getMostRecentPostInGroup(group.mGroup.mId);
                } catch (Data.NotFoundError e) {
                    // No recent post to display
                }
                if (mostRecentPost != null) {
                    // TODO: getNickname helper
                    String nickname;
                    if (mostRecentPost.mPost.mPublisherId.equals(data.getSelfId())) {
                        nickname = data.getSelfOrThrow().mPublicIdentity.mNickname;
                    } else {
                        nickname = data.getFriendByIdOrThrow(mostRecentPost.mPost.mPublisherId).mPublicIdentity.mNickname;
                    }
                    postPublisherText.setText(nickname);
                    postContentText.setText(mostRecentPost.mPost.mContent);
                    postTimestampText.setText(
                            Utils.DateFormatter.formatRelativeDatetime(context, mostRecentPost.mPost.mModifiedTimestamp, true));
                    if (mostRecentPost.mState == Data.Post.State.UNREAD) {
                        postPublisherText.setTypeface(null, Typeface.BOLD);
                        postContentText.setTypeface(null, Typeface.BOLD);
                    } else {
                        postPublisherText.setTypeface(null, Typeface.NORMAL);
                        postContentText.setTypeface(null, Typeface.NORMAL);
                    }
                } else {
                    postContentText.setText(R.string.no_posts);
                }
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to display group");
                // *TODO* view.<field>.setBlank();
            }

            return view;
        }
    }

    public static class PostAdapter extends ObjectCursorAdapter<Data.Post> {

        private static final int POST_SENT_VIEW = 0;
        private static final int POST_RECEIVED_VIEW = 1;

        public PostAdapter(Context context, CursorFactory<Data.Post> cursorFactory) {
            super(context, cursorFactory);
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            try {
                Data.Post post = getItem(position);
                if (post == null) {
                    throw new PloggyError(LOG_TAG, "item not found");
                }

                Data data = Data.getInstance();

                boolean isSelfPublished = post.mPost.mPublisherId.equals(data.getSelfId());

                return isSelfPublished ? POST_SENT_VIEW : POST_RECEIVED_VIEW;
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to display post");
            }
            return Adapter.IGNORE_ITEM_VIEW_TYPE;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context context = getContext();
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                switch (getItemViewType(position)) {
                case POST_SENT_VIEW:
                    view = inflater.inflate(R.layout.post_sent, null);
                    break;
                case POST_RECEIVED_VIEW:
                    view = inflater.inflate(R.layout.post_received, null);
                    break;
                }
            }

            ImageView avatarImage = (ImageView)view.findViewById(R.id.post_publisher_avatar_image);
            TextView nicknameText = (TextView)view.findViewById(R.id.post_publisher_nickname_text);
            TextView contentText = (TextView)view.findViewById(R.id.post_content_text);
            TextView pictureDownloadText = (TextView)view.findViewById(R.id.post_picture_download_text);
            ImageView pictureThumbnailImage = (ImageView)view.findViewById(R.id.post_picture_thumbnail);
            TextView timestampText = (TextView)view.findViewById(R.id.post_timestamp_text);

            try {
                Data.Post post = getItem(position);
                if (post == null) {
                    throw new PloggyError(LOG_TAG, "item not found");
                }

                // *TODO* post.mState..?

                Data data = Data.getInstance();

                boolean isSelfPublished = post.mPost.mPublisherId.equals(data.getSelfId());

                Identity.PublicIdentity publisher = null;
                if (isSelfPublished) {
                    publisher = data.getSelfOrThrow().mPublicIdentity;
                } else {
                    publisher = data.getFriendByIdOrThrow(post.mPost.mPublisherId).mPublicIdentity;
                }

                Data.Download download = null;
                double downloadProgress = 0.0;
                Data.LocalResource localResource = null;

                try {
                    if (post.mPost.mAttachments != null && post.mPost.mAttachments.size() > 0) {
                        if (isSelfPublished) {
                            localResource = data.getLocalResource(post.mPost.mAttachments.get(0).mId);
                        } else {
                            download = data.getDownload(
                                    post.mPost.mPublisherId, post.mPost.mAttachments.get(0).mId);
                            if (download.mState == Data.Download.State.IN_PROGRESS) {
                                long downloadedSize = Downloads.getDownloadedSize(download);
                                if (download.mSize > 0) {
                                    downloadProgress = 100.0*downloadedSize/download.mSize;
                                }
                            }
                        }
                    }
                } catch (Data.NotFoundError e) {
                    Log.addEntry(LOG_TAG, "failed to load post attachment");
                }

                Avatar.setAvatarImage(context, avatarImage, publisher);
                nicknameText.setText(publisher.mNickname);
                contentText.setText(post.mPost.mContent);

                if (download != null) {
                    switch (download.mState) {
                    case IN_PROGRESS:
                        pictureDownloadText.setVisibility(View.VISIBLE);
                        pictureThumbnailImage.setVisibility(View.GONE);
                        pictureDownloadText.setText(context.getString(R.string.format_download_progress, downloadProgress));
                        break;
                    case CANCELLED:
                        pictureDownloadText.setVisibility(View.VISIBLE);
                        pictureThumbnailImage.setVisibility(View.GONE);
                        pictureDownloadText.setText("");
                        break;
                    case COMPLETE:
                        pictureDownloadText.setVisibility(View.GONE);
                        pictureThumbnailImage.setVisibility(View.VISIBLE);
                        Pictures.loadThumbnailWithClickToShowPicture(context, Downloads.getDownloadFile(download), pictureThumbnailImage);
                        break;
                    }
                } else if (localResource != null) {
                    pictureDownloadText.setVisibility(View.GONE);
                    pictureThumbnailImage.setVisibility(View.VISIBLE);
                    Pictures.loadThumbnailWithClickToShowPicture(context, new File(localResource.mFilePath), pictureThumbnailImage);
                } else {
                    pictureDownloadText.setVisibility(View.GONE);
                    pictureThumbnailImage.setVisibility(View.GONE);
                }

                timestampText.setText(Utils.DateFormatter.formatRelativeDatetime(context, post.mPost.mModifiedTimestamp, true));
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to display post");
                // *TODO* view.<field>.setBlank();
            }

            return view;
        }

        @Override
        public Data.Post getItem(int position) {
            // Inverted display (newest posts at bottom)
            // This is intended to be used along with android:stackFromBottom="true"
            return super.getItem(super.getCount() - position - 1);
        }
    }

    private static abstract class ObjectCursorAdapter<T> extends BaseAdapter {

        private final Context mContext;
        private final CursorFactory<T> mCursorFactory;
        private Data.ObjectCursor<T> mCursor;

        public ObjectCursorAdapter(Context context, CursorFactory<T> cursorFactory) {
            mContext = context;
            mCursorFactory = cursorFactory;
            mCursor = null;
            update(true);
        }

        public Context getContext() {
            return mContext;
        }

        public Data.ObjectCursor<T> getCursor() {
            return mCursor;
        }

        // Execute database query on background thread
        private static class MakeCursorAsyncTask<T> extends AsyncTask<Object, Object, Data.ObjectCursor<T>> {
            private final ObjectCursorAdapter<T> mAdapter;
            MakeCursorAsyncTask(ObjectCursorAdapter<T> adapter) {
                mAdapter = adapter;
            }
            @Override
            protected Data.ObjectCursor<T> doInBackground(Object... params) {
                try {
                    return mAdapter.makeCursor();
                } catch (PloggyError e) {
                    Log.addEntry(LOG_TAG, "make cursor failed");
                    return null;
                }
            }
            @Override
            protected void onPostExecute(Data.ObjectCursor<T> cursor) {
                if (cursor != null) {
                    mAdapter.mCursor = cursor;
                    mAdapter.notifyDataSetChanged();
                }
            }
        }

        public void update(boolean requery) {
            if (!requery && mCursor != null) {
                // Simply redraw without updating cursor
                notifyDataSetChanged();
                return;
            }
            MakeCursorAsyncTask<T> task = new MakeCursorAsyncTask<T>(this);
            task.execute();
        }

        protected Data.ObjectCursor<T> makeCursor() throws PloggyError {
            return mCursorFactory.makeCursor();
        }

        @Override
        public int getCount() {
            if (mCursor == null) {
                return 0;
            }
            return mCursor.getCount();
        }

        @Override
        public T getItem(int position) {
            if (mCursor == null) {
                return null;
            }
            try {
                return mCursor.get(position);
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "adapter getItem failed");
                return null;
            }
        }

        @Override
        public boolean hasStableIds() {
            // TODO: could we have approximate stable IDs by truncating the
            // Post/Group/Friend.mID (256-bit) value to long (64-bits)?
            return false;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }
}
