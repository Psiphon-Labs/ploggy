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

import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

/**
 * User interface which displays a list of friends.

 * This class subscribes to friend and status events to update displayed data
 * while in the foreground.
 */
public class FragmentFriendList extends ListFragment {

    private static final String LOG_TAG = "Friend List";

    private boolean mIsResumed = false;
    private FriendAdapter mFriendAdapter;
    Utils.FixedDelayExecutor mRefreshUIExecutor;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        try {
            mFriendAdapter = new FriendAdapter(getActivity());
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to initialize friend adapter");
        }

        // Refresh the message list every 5 seconds. This updates "time ago" displays.
        // TODO: event driven redrawing?
        mRefreshUIExecutor = new Utils.FixedDelayExecutor(new Runnable() {@Override public void run() {updateFriends();}}, 5000);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (mFriendAdapter != null) {
            setListAdapter(mFriendAdapter);
        }
        registerForContextMenu(getListView());
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsResumed = true;
        mRefreshUIExecutor.start();
        Events.register(this);
        Events.post(new Events.DisplayedFriends());
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsResumed = false;
        mRefreshUIExecutor.stop();
        Events.unregister(this);
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        Data.Friend friend = (Data.Friend)listView.getItemAtPosition(position);
        Intent intent = new Intent(getActivity(), ActivityFriendStatusDetails.class);
        Bundle bundle = new Bundle();
        bundle.putString(ActivityFriendStatusDetails.FRIEND_ID_BUNDLE_KEY, friend.mId);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        if (view.equals(getListView())) {
            getActivity().getMenuInflater().inflate(R.menu.friend_list_context, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        if (item.getItemId() == R.id.action_friend_list_delete_friend) {
            final Data.Friend finalFriend = (Data.Friend)getListView().getItemAtPosition(info.position);
            new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.label_delete_friend_title))
                .setMessage(getString(R.string.label_delete_friend_message, finalFriend.mPublicIdentity.mNickname))
                .setPositiveButton(getString(R.string.label_delete_friend_positive),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Data.getInstance().removeFriend(finalFriend.mId);
                                } catch (Data.DataNotFoundError e) {
                                    // Ignore
                                } catch (Utils.ApplicationError e) {
                                    Log.addEntry(LOG_TAG, "failed to delete friend: " + finalFriend.mPublicIdentity.mNickname);
                                }
                            }
                        })
                .setNegativeButton(getString(R.string.label_delete_friend_negative), null)
                .show();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Subscribe
    public void onAddedFriend(Events.AddedFriend addedFriend) {
        updateFriends();
    }

    @Subscribe
    public void onUpdatedFriend(Events.UpdatedFriend updatedFriend) {
        updateFriends();
    }

    @Subscribe
    public void onUpdatedFriendStatus(Events.UpdatedFriendStatus updatedFriendStatus) {
        updateFriends();
    }

    @Subscribe
    public void onDeletedFriend(Events.RemovedFriend removedFriend) {
        updateFriends();
    }

    @Subscribe
    public void onUpdatedNewMessages(Events.UpdatedNewMessages updatedNewMessages) {
        if (mIsResumed) {
            Events.post(new Events.DisplayedFriends());
        }
    }

    private void updateFriends() {
        try {
            mFriendAdapter.updateFriends();
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to update friend list");
        }
    }

    private static class FriendAdapter extends BaseAdapter {
        private final Context mContext;
        private List<Data.Friend> mFriends;

        public FriendAdapter(Context context) throws Utils.ApplicationError {
            mContext = context;
            mFriends = Data.getInstance().getFriends();
        }

        public void updateFriends() throws Utils.ApplicationError {
            mFriends = Data.getInstance().getFriends();
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.friend_list_row, null);
            }
            Data.Friend friend = mFriends.get(position);
            if (friend != null) {
                ImageView avatarImage = (ImageView)view.findViewById(R.id.friend_list_avatar_image);
                TextView nicknameText = (TextView)view.findViewById(R.id.friend_list_nickname_text);
                TextView lastTimestampText = (TextView)view.findViewById(R.id.friend_list_last_timestamp_text);
                TextView messageTimestampText = (TextView)view.findViewById(R.id.friend_list_message_timestamp_text);
                TextView messageContentText = (TextView)view.findViewById(R.id.friend_list_message_content_text);
                TextView locationTimestampText = (TextView)view.findViewById(R.id.friend_list_location_timestamp_text);
                TextView locationStreetAddressText = (TextView)view.findViewById(R.id.friend_list_location_street_address_text);
                TextView locationDistanceText = (TextView)view.findViewById(R.id.friend_list_location_distance_text);

                // Not hiding missing fields
                lastTimestampText.setText("");
                messageTimestampText.setText("");
                messageContentText.setText("");
                locationTimestampText.setText("");
                locationStreetAddressText.setText("");
                locationDistanceText.setText("");

                Robohash.setRobohashImage(mContext, avatarImage, true, friend.mPublicIdentity);
                nicknameText.setText(friend.mPublicIdentity.mNickname);
                try {
                    Data data = Data.getInstance();
                    Data.Location selfLocation = null;
                    try {
                        selfLocation = data.getCurrentSelfLocation();
                    } catch (Data.DataNotFoundError e) {
                        // Won't be able to compute distance
                    }

                    Data.Status friendStatus = data.getFriendStatus(friend.mId);
                    
                    // Display most recent successful communication timestamp
                    String lastTimestamp = "";
                    if (friend.mLastReceivedStatusTimestamp != null &&
                            (friend.mLastSentStatusTimestamp == null ||
                             friend.mLastReceivedStatusTimestamp.after(friend.mLastSentStatusTimestamp))) {
                        lastTimestamp = Utils.DateFormatter.formatRelativeDatetime(mContext, friend.mLastReceivedStatusTimestamp, true);
                    } else if (friend.mLastSentStatusTimestamp != null) {
                        lastTimestamp = Utils.DateFormatter.formatRelativeDatetime(mContext, friend.mLastSentStatusTimestamp, true);
                    }
                    lastTimestampText.setText(lastTimestamp);
                    if (lastTimestamp.length() > 0) {
                        // On touch, show log entries
                        lastTimestampText.setOnClickListener(
                            new View.OnClickListener() {
                                public void onClick(View v) {
                                    mContext.startActivity(new Intent(mContext, ActivityLogEntries.class));
                                }
                            });
                    }

                    if (friendStatus.mMessages.size() > 0) {
                        Data.Message message = friendStatus.mMessages.get(0);
                        messageContentText.setText(message.mContent);
                        messageTimestampText.setText(Utils.DateFormatter.formatRelativeDatetime(mContext, message.mTimestamp, true));
                    }
                    if (friendStatus.mLocation.mTimestamp != null) {
                        locationTimestampText.setText(Utils.DateFormatter.formatRelativeDatetime(mContext, friendStatus.mLocation.mTimestamp, true));
                        if (friendStatus.mLocation.mStreetAddress.length() > 0) {
                            locationStreetAddressText.setText(friendStatus.mLocation.mStreetAddress);
                        } else {
                            locationStreetAddressText.setText(R.string.prompt_no_street_address_reported);
                        }
                        if (selfLocation != null && selfLocation.mTimestamp != null) {
                            int distance = Utils.calculateLocationDistanceInMeters(
                                    selfLocation.mLatitude,
                                    selfLocation.mLongitude,
                                    friendStatus.mLocation.mLatitude,
                                    friendStatus.mLocation.mLongitude);
                            locationDistanceText.setText(Utils.formatDistance(mContext, distance));
                        } else {
                            locationDistanceText.setText(R.string.prompt_unknown_distance);
                        }
                    }
                } catch (Data.DataNotFoundError e) {
                    messageTimestampText.setText(R.string.prompt_no_status_updates_received);
                } catch (Utils.ApplicationError e) {
                    Log.addEntry(LOG_TAG, "failed to display friend");
                }
            }
            return view;
        }

        @Override
        public int getCount() {
            return mFriends.size();
        }

        @Override
        public Object getItem(int position) {
            return mFriends.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }
}
