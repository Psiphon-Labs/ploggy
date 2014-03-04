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
import android.content.Intent;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
                Data.Friend friend = mCursor.get(position);

                Data data = Data.getInstance();

                Robohash.setRobohashImage(mContext, avatarImage, true, friend.mPublicIdentity);
                nicknameText.setText(friend.mPublicIdentity.mNickname);

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
                    lastTimestamp = Utils.DateFormatter.formatRelativeDatetime(mContext, friend.mLastReceivedFromTimestamp, true);
                } else if (friend.mLastSentToTimestamp != null) {
                    lastTimestamp = Utils.DateFormatter.formatRelativeDatetime(mContext, friend.mLastSentToTimestamp, true);
                }
                lastTimestampText.setText(lastTimestamp);
                if (lastTimestamp.length() > 0) {
                    // On touch, show log entries
                    lastTimestampText.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                mContext.startActivity(new Intent(mContext, ActivityLogEntries.class));
                            }
                        });
                }

                // *TODO* Data.NotFoundError --> messageTimestampText.setText(R.string.prompt_no_status_updates_received);

                Data.Post mostRecentPost = null;
                try {
                    Data.Post post = data.getMostRecentPost(friend.mId);
                } catch (Data.NotFoundError e) {
                    // No recent post to display
                }
                if (mostRecentPost != null) {
                    postTimestampText.setText(
                            Utils.DateFormatter.formatRelativeDatetime(mContext, mostRecentPost.mPost.mModifiedTimestamp, true));
                    postContentText.setText(mostRecentPost.mPost.mContent);
                    postGroupText.setText(mostRecentPost.mGroupName);
                }

                if (friendLocation.mTimestamp != null) {
                    locationTimestampText.setText(
                            Utils.DateFormatter.formatRelativeDatetime(mContext, friendLocation.mTimestamp, true));
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
                        locationDistanceText.setText(Utils.formatDistance(mContext, distance));
                    } else {
                        locationDistanceText.setText(R.string.prompt_unknown_distance);
                    }
                }
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to display friend");
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
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.group_list_row, null);
            }

            // *TODO*
            // ...

            return view;
        }
    }

    public static class PostAdapter extends ObjectCursorAdapter<Data.Post> {

        public PostAdapter(Context context, CursorFactory<Data.Post> cursorFactory) {
            super(context, cursorFactory);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.post_list_row, null);
            }

            ImageView avatarImage = (ImageView)view.findViewById(R.id.message_avatar_image);
            TextView nicknameText = (TextView)view.findViewById(R.id.message_nickname_text);
            TextView contentText = (TextView)view.findViewById(R.id.message_content_text);
            TextView pictureDownloadText = (TextView)view.findViewById(R.id.message_picture_download_text);
            ImageView pictureThumbnailImage = (ImageView)view.findViewById(R.id.message_picture_thumbnail);
            TextView timestampText = (TextView)view.findViewById(R.id.message_timestamp_text);

            try {
                Data.Post post = mCursor.get(position);
                // *TODO* post.mState..?

                Data data = Data.getInstance();

                Identity.PublicIdentity publisher = null;
                if (post.mIsSelfPublisher) {
                    publisher = data.getSelfOrThrow().mPublicIdentity;
                } else {
                    publisher = data.getFriendByIdOrThrow(post.mPost.mPublisherId).mPublicIdentity;
                }

                Data.Download download = null;
                double downloadProgress = 0.0;
                Data.LocalResource localResource = null;

                if (post.mPost.mAttachments != null && post.mPost.mAttachments.size() > 0) {
                    if (post.mIsSelfPublisher) {
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

                Robohash.setRobohashImage(mContext, avatarImage, true, publisher);
                nicknameText.setText(publisher.mNickname);
                contentText.setText(post.mPost.mContent);

                if (download != null) {
                    switch (download.mState) {
                    case IN_PROGRESS:
                        pictureDownloadText.setVisibility(View.VISIBLE);
                        pictureThumbnailImage.setVisibility(View.GONE);
                        pictureDownloadText.setText(mContext.getString(R.string.format_download_progress, downloadProgress));
                        break;
                    case CANCELLED:
                        pictureDownloadText.setVisibility(View.VISIBLE);
                        pictureThumbnailImage.setVisibility(View.GONE);
                        pictureDownloadText.setText("");
                        break;
                    case COMPLETE:
                        pictureDownloadText.setVisibility(View.GONE);
                        pictureThumbnailImage.setVisibility(View.VISIBLE);
                        Pictures.loadThumbnailWithClickToShowPicture(mContext, Downloads.getDownloadFile(download), pictureThumbnailImage);
                        break;
                    }
                } else if (localResource != null) {
                    pictureDownloadText.setVisibility(View.GONE);
                    pictureThumbnailImage.setVisibility(View.VISIBLE);
                    Pictures.loadThumbnailWithClickToShowPicture(mContext, new File(localResource.mFilePath), pictureThumbnailImage);
                } else {
                    pictureDownloadText.setVisibility(View.GONE);
                    pictureThumbnailImage.setVisibility(View.GONE);
                }

                timestampText.setText(Utils.DateFormatter.formatRelativeDatetime(mContext, post.mPost.mModifiedTimestamp, true));
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to display post");
                // *TODO* view.<field>.setBlank();
            }

            return view;
        }
    }

    private static abstract class ObjectCursorAdapter<T> extends BaseAdapter {

        protected final Context mContext;
        protected final CursorFactory<T> mCursorFactory;
        protected Data.ObjectCursor<T> mCursor;

        public ObjectCursorAdapter(Context context, CursorFactory<T> cursorFactory) {
            mContext = context;
            mCursorFactory = cursorFactory;
            mCursor = null;
            update(true);
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
