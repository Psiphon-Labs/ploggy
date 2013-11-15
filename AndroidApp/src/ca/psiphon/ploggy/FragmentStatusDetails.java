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

import java.util.Date;

import com.squareup.otto.Subscribe;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * User interface which displays self and friend status details.
 * 
 * Invoke with an intent containing an extra with FRIEND_ID_BUNDLE_KEY
 * to display friend details; otherwise displays self details.
 * This class subscribes to status events to update displayed data
 * while in the foreground.
 */
public class FragmentStatusDetails extends Fragment {
    
    private static final String LOG_TAG = "Status Details";

    public static final String FRIEND_ID_BUNDLE_KEY = "friendId";

    private String mFriendId;
    private ImageView mAvatarImage;
    private TextView mNicknameText;
    private TextView mFingerprintText;
    private TextView mStreetAddressText;
    private TextView mDistanceLabel;
    private TextView mDistanceText;
    private TextView mCoordinatesText;
    private TextView mPrecisionText;
    private TextView mStatusTimestampText;
    private TextView mLastReceivedStatusTimestampLabel;
    private TextView mLastReceivedStatusTimestampText;
    private TextView mLastSentStatusTimestampLabel;
    private TextView mLastSentStatusTimestampText;
    private TextView mAddedTimestampText;

    public FragmentStatusDetails() {
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.status_details, container, false);

        mAvatarImage = (ImageView)view.findViewById(R.id.status_details_avatar_image);
        mNicknameText = (TextView)view.findViewById(R.id.status_details_nickname_text);
        mFingerprintText = (TextView)view.findViewById(R.id.status_details_fingerprint_text);
        mStreetAddressText = (TextView)view.findViewById(R.id.status_details_street_address_text);
        mDistanceLabel = (TextView)view.findViewById(R.id.status_details_distance_label);
        mDistanceText = (TextView)view.findViewById(R.id.status_details_distance_text);
        mCoordinatesText = (TextView)view.findViewById(R.id.status_details_coordinates_text);
        mPrecisionText = (TextView)view.findViewById(R.id.status_details_precision_text);
        mStatusTimestampText = (TextView)view.findViewById(R.id.status_details_status_timestamp_text);
        mLastReceivedStatusTimestampLabel = (TextView)view.findViewById(R.id.status_details_last_received_status_timestamp_label);
        mLastReceivedStatusTimestampText = (TextView)view.findViewById(R.id.status_details_last_received_status_timestamp_text);
        mLastSentStatusTimestampLabel = (TextView)view.findViewById(R.id.status_details_last_sent_status_timestamp_label);
        mLastSentStatusTimestampText = (TextView)view.findViewById(R.id.status_details_last_sent_status_timestamp_text);
        mAddedTimestampText = (TextView)view.findViewById(R.id.status_details_added_timestamp_text);
        
        Events.register(this);
        
        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Events.unregister(this);
    }

    @Subscribe
    public void onUpdatedSelfStatus(Events.UpdatedSelfStatus updatedSelfStatus) {
        try {
            show();
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to update self status details");
        }
    }       

    @Subscribe
    public void onUpdatedFriendStatus(Events.UpdatedFriendStatus updatedFriendStatus) {
        try {
            show();
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to update friend status details");
        }
    }       

    public void show(String friendId) throws Utils.ApplicationError {
        mFriendId = friendId;
        show();
    }
    
    private void show() throws Utils.ApplicationError {
        Data data = Data.getInstance();
        Data.Self self = data.getSelf();
        Data.Status selfStatus = null;
        Data.Friend friend = null;
        Data.Status friendStatus = null;
        
        if (mFriendId != null) {
            try {
                selfStatus = data.getSelfStatus();
            } catch (Data.DataNotFoundError e) {
             // Won't be able to compute distance
            }                
            friend = data.getFriendById(mFriendId);
            friendStatus = data.getFriendStatus(mFriendId);
            Date lastSentStatusTimestamp = data.getFriendLastSentStatusTimestamp(friend.mId);
            Date lastReceivedStatusTimestamp = data.getFriendLastReceivedStatusTimestamp(friend.mId);

            Robohash.setRobohashImage(getActivity(), mAvatarImage, true, friend.mPublicIdentity);
            mNicknameText.setText(friend.mPublicIdentity.mNickname);
            mFingerprintText.setText(Utils.formatFingerprint(friend.mPublicIdentity.getFingerprint()));
            if (friendStatus.mStreetAddress.length() > 0) {
                mStreetAddressText.setText(friendStatus.mStreetAddress);
            } else {
                mStreetAddressText.setText(R.string.prompt_no_street_address_reported);
            }
            if (selfStatus != null) {
                int distance = Utils.calculateLocationDistanceInMeters(
                        selfStatus.mLatitude,
                        selfStatus.mLongitude,
                        friendStatus.mLatitude,
                        friendStatus.mLongitude);
                mDistanceText.setText(
                        getString(R.string.format_status_details_distance, distance));
            } else {
                mDistanceText.setText(R.string.prompt_unknown_distance);
            }
            mCoordinatesText.setText(
                    getString(R.string.format_status_details_coordinates, friendStatus.mLatitude, friendStatus.mLongitude));
            mPrecisionText.setText(
                    getString(R.string.format_status_details_precision, friendStatus.mPrecision));
            mStatusTimestampText.setText(Utils.formatSameDayTime(friendStatus.mTimestamp));
            if (lastReceivedStatusTimestamp != null) {
                mLastReceivedStatusTimestampText.setText(Utils.formatSameDayTime(lastReceivedStatusTimestamp));
            } else {
                mLastReceivedStatusTimestampText.setText(R.string.prompt_no_location_updates_received);
            }
            if (lastSentStatusTimestamp != null) {
                mLastSentStatusTimestampText.setText(Utils.formatSameDayTime(lastSentStatusTimestamp));
            } else {
                mLastSentStatusTimestampText.setText(R.string.prompt_no_location_updates_sent);
            }
            mAddedTimestampText.setText(Utils.formatSameDayTime(friend.mAddedTimestamp));
        } else {
            selfStatus = data.getSelfStatus();

            Robohash.setRobohashImage(getActivity(), mAvatarImage, true, self.mPublicIdentity);
            mNicknameText.setText(self.mPublicIdentity.mNickname);
            mFingerprintText.setText(Utils.formatFingerprint(self.mPublicIdentity.getFingerprint()));
            if (selfStatus.mStreetAddress.length() > 0) {
                mStreetAddressText.setText(selfStatus.mStreetAddress);
            } else {
                mStreetAddressText.setText(R.string.prompt_no_street_address_reported);
            }
            mDistanceLabel.setVisibility(View.GONE);
            mDistanceText.setVisibility(View.GONE);
            mCoordinatesText.setText(
                    getString(R.string.format_status_details_coordinates, selfStatus.mLatitude, selfStatus.mLongitude));
            mPrecisionText.setText(
                    getString(R.string.format_status_details_precision, selfStatus.mPrecision));
            mStatusTimestampText.setText(Utils.formatSameDayTime(selfStatus.mTimestamp));
            mLastReceivedStatusTimestampLabel.setVisibility(View.GONE);
            mLastReceivedStatusTimestampText.setVisibility(View.GONE);
            mLastSentStatusTimestampLabel.setVisibility(View.GONE);
            mLastSentStatusTimestampText.setVisibility(View.GONE);
            mAddedTimestampText.setVisibility(View.GONE);
        }
    }
}
