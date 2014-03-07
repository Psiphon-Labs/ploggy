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

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

/**
 * User interface for displaying friend status details.

 * Invoke with an intent containing an extra with FRIEND_ID_BUNDLE_KEY.
 * This class subscribes to status events to update displayed data
 * while in the foreground.
 */
public class FragmentFriendDetail extends Fragment {

    private static final String LOG_TAG = "Friend Detail";

    public static final String FRIEND_ID_BUNDLE_KEY = "friendId";

    private String mFriendId;
    private ImageView mAvatarImage;
    private TextView mNicknameText;
    private TextView mFingerprintText;
    private TextView mLocationLabel;
    private TextView mLocationStreetAddressLabel;
    private TextView mLocationStreetAddressText;
    private TextView mLocationDistanceLabel;
    private TextView mLocationDistanceText;
    private TextView mLocationCoordinatesLabel;
    private TextView mLocationCoordinatesText;
    private TextView mLocationTimestampLabel;
    private TextView mLocationTimestampText;
    private TextView mLastReceivedFromTimestampText;
    private TextView mBytesReceivedFromText;
    private TextView mLastSentToTimestampText;
    private TextView mBytesSentToText;
    private TextView mAddedTimestampText;
    Utils.FixedDelayExecutor mRefreshUIExecutor;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.friend_detail, container, false);

        // *TODO* get friendID from fragment args
        /*
        Bundle bundle = getIntent().getExtras();
        if (bundle == null) {
            finish();
            return;
        }

        mFriendId = bundle.getString(FRIEND_ID_BUNDLE_KEY);
        if (mFriendId == null) {
            finish();
            return;
        }
        */

        mAvatarImage = (ImageView)view.findViewById(R.id.friend_status_details_avatar_image);
        mNicknameText = (TextView)view.findViewById(R.id.friend_status_details_nickname_text);
        mFingerprintText = (TextView)view.findViewById(R.id.friend_status_details_fingerprint_text);
        mLocationLabel = (TextView)view.findViewById(R.id.friend_status_details_location_label);
        mLocationStreetAddressLabel = (TextView)view.findViewById(R.id.friend_status_details_location_street_address_label);
        mLocationStreetAddressText = (TextView)view.findViewById(R.id.friend_status_details_location_street_address_text);
        mLocationDistanceLabel = (TextView)view.findViewById(R.id.friend_status_details_location_distance_label);
        mLocationDistanceText = (TextView)view.findViewById(R.id.friend_status_details_location_distance_text);
        mLocationCoordinatesLabel = (TextView)view.findViewById(R.id.friend_status_details_location_coordinates_label);
        mLocationCoordinatesText = (TextView)view.findViewById(R.id.friend_status_details_location_coordinates_text);
        mLocationTimestampLabel = (TextView)view.findViewById(R.id.friend_status_details_location_timestamp_label);
        mLocationTimestampText = (TextView)view.findViewById(R.id.friend_status_details_location_timestamp_text);
        mLastReceivedFromTimestampText = (TextView)view.findViewById(R.id.friend_status_details_last_received_from_timestamp_text);
        mBytesReceivedFromText = (TextView)view.findViewById(R.id.friend_status_details_bytes_received_from_text);
        mLastSentToTimestampText = (TextView)view.findViewById(R.id.friend_status_details_last_sent_to_timestamp_text);
        mBytesSentToText = (TextView)view.findViewById(R.id.friend_status_details_bytes_sent_to_text);
        mAddedTimestampText = (TextView)view.findViewById(R.id.friend_status_details_added_timestamp_text);

        show();

        // Refresh the message list every 5 seconds. This updates download state and "time ago" displays.
        // TODO: event driven redrawing?
        mRefreshUIExecutor = new Utils.FixedDelayExecutor(new Runnable() {@Override public void run() {show();}}, 5000);

        Events.getInstance().register(this);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        mRefreshUIExecutor.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        mRefreshUIExecutor.stop();
    }

    @Override
    public void onDestroyView() {
        Events.getInstance().unregister(this);
        super.onDestroy();
    }

    @Subscribe
    public void onUpdatedFriend(Events.UpdatedFriend updatedFriend) {
        show();
    }

    @Subscribe
    public void onUpdatedFriendLocation(Events.UpdatedFriendLocation updatedFriendLocation) {
        show();
    }

    private void show() {
        try {
            Context context = getActivity();
            Data data = Data.getInstance();

            Data.Friend friend = data.getFriendByIdOrThrow(mFriendId);

            Protocol.Location selfLocation = null;
            try {
                selfLocation = data.getSelfLocation();
            } catch (Data.NotFoundError e) {
                // Won't be able to compute distance
            }
            Protocol.Location friendLocation = null;
            try {
                friendLocation = data.getFriendLocation(mFriendId);
            } catch (Data.NotFoundError e) {
                // Won't be able to display friend location
            }

            Robohash.setRobohashImage(context, mAvatarImage, true, friend.mPublicIdentity);
            mNicknameText.setText(friend.mPublicIdentity.mNickname);
            mFingerprintText.setText(Utils.formatFingerprint(friend.mPublicIdentity.getFingerprint()));

            int locationVisibility = (friendLocation != null) ? View.VISIBLE : View.GONE;
            mLocationLabel.setVisibility(locationVisibility);
            mLocationStreetAddressLabel.setVisibility(locationVisibility);
            mLocationStreetAddressText.setVisibility(locationVisibility);
            mLocationDistanceLabel.setVisibility(locationVisibility);
            mLocationDistanceText.setVisibility(locationVisibility);
            mLocationCoordinatesLabel.setVisibility(locationVisibility);
            mLocationCoordinatesText.setVisibility(locationVisibility);
            mLocationTimestampLabel.setVisibility(locationVisibility);
            mLocationTimestampText.setVisibility(locationVisibility);
            if (friendLocation != null) {
                if (friendLocation.mStreetAddress.length() > 0) {
                    mLocationStreetAddressText.setText(friendLocation.mStreetAddress);
                } else {
                    mLocationStreetAddressText.setText(R.string.prompt_no_street_address_reported);
                }
                if (selfLocation != null && selfLocation.mTimestamp != null) {
                    int distance = Utils.calculateLocationDistanceInMeters(
                            selfLocation.mLatitude,
                            selfLocation.mLongitude,
                            friendLocation.mLatitude,
                            friendLocation.mLongitude);
                    mLocationDistanceText.setText(Utils.formatDistance(context, distance));
                } else {
                    mLocationDistanceText.setText(R.string.prompt_unknown_distance);
                }
                mLocationCoordinatesText.setText(
                        getString(
                                R.string.format_status_details_coordinates,
                                friendLocation.mLatitude,
                                friendLocation.mLongitude));
                mLocationTimestampText.setText(Utils.DateFormatter.formatRelativeDatetime(context, friendLocation.mTimestamp, true));
            }

            if (friend.mLastReceivedFromTimestamp != null) {
                mLastReceivedFromTimestampText.setText(Utils.DateFormatter.formatRelativeDatetime(context, friend.mLastReceivedFromTimestamp, true));
            } else {
                mLastReceivedFromTimestampText.setText(R.string.prompt_no_status_updates_received);
            }

            mBytesReceivedFromText.setText(Utils.byteCountToDisplaySize(friend.mBytesReceivedFrom, false));

            if (friend.mLastSentToTimestamp != null) {
                mLastSentToTimestampText.setText(Utils.DateFormatter.formatRelativeDatetime(context, friend.mLastSentToTimestamp, true));
            } else {
                mLastSentToTimestampText.setText(R.string.prompt_no_status_updates_sent);
            }

            mBytesSentToText.setText(Utils.byteCountToDisplaySize(friend.mBytesSentTo, false));

            mAddedTimestampText.setText(Utils.DateFormatter.formatRelativeDatetime(context, friend.mAddedTimestamp, true));
        } catch (PloggyError e) {
            Log.addEntry(LOG_TAG, "failed to display friend detail");
        }
    }
}
