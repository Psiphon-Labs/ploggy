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

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;

/**
 * User interface for displaying self and friend location details.
 * 
 * This activity also provides a button to delete friends.
 * 
 * Invoke with an intent containing an extra with FRIEND_ID_BUNDLE_KEY
 * to display friend details; otherwise displays self details.
 * This class subscribes to status events to update displayed data
 * while in the foreground.
 */
public class ActivityLocationDetails extends Activity {
    
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
    private ImageView mMapImage;

    public ActivityLocationDetails() {
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_details);

        mAvatarImage = (ImageView)findViewById(R.id.location_details_avatar_image);
        mNicknameText = (TextView)findViewById(R.id.location_details_nickname_text);
        mFingerprintText = (TextView)findViewById(R.id.location_details_fingerprint_text);
        mStreetAddressText = (TextView)findViewById(R.id.location_details_street_address_text);
        mDistanceLabel = (TextView)findViewById(R.id.location_details_distance_label);
        mDistanceText = (TextView)findViewById(R.id.location_details_distance_text);
        mCoordinatesText = (TextView)findViewById(R.id.location_details_coordinates_text);
        mPrecisionText = (TextView)findViewById(R.id.location_details_precision_text);
        mStatusTimestampText = (TextView)findViewById(R.id.location_details_status_timestamp_text);
        mLastReceivedStatusTimestampLabel = (TextView)findViewById(R.id.location_details_last_received_status_timestamp_label);
        mLastReceivedStatusTimestampText = (TextView)findViewById(R.id.location_details_last_received_status_timestamp_text);
        mLastSentStatusTimestampLabel = (TextView)findViewById(R.id.location_details_last_sent_status_timestamp_label);
        mLastSentStatusTimestampText = (TextView)findViewById(R.id.location_details_last_sent_status_timestamp_text);
        mAddedTimestampText = (TextView)findViewById(R.id.location_details_added_timestamp_text);
        mMapImage = (ImageView)findViewById(R.id.location_details_map_image);

        // TODO: onNewIntent?
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            mFriendId = bundle.getString(FRIEND_ID_BUNDLE_KEY);
        }
        
        showDetails();
        
        Events.register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Events.unregister(this);
    }

    @Subscribe
    public void onUpdatedSelfStatus(Events.UpdatedSelfStatus updatedSelfStatus) {
        showDetails();
    }       

    @Subscribe
    public void onUpdatedFriendStatus(Events.UpdatedFriendStatus updatedFriendStatus) {
        showDetails();
    }       

    private void showDetails() {
        try {
            Data data = Data.getInstance();
            Data.Self self = data.getSelf();
            Data.Status selfStatus = null;
            Data.Friend friend = null;
            Data.Status friendStatus = null;
            
            // TODO: mMapImage is just a static placeholder
            
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

                Robohash.setRobohashImage(this, mAvatarImage, true, friend.mPublicIdentity);
                mNicknameText.setText(friend.mPublicIdentity.mNickname);
                mFingerprintText.setText(Utils.formatFingerprint(friend.mPublicIdentity.getFingerprint()));
                if (friendStatus.mStreetAddress.length() > 0) {
                    mStreetAddressText.setText(R.string.prompt_no_street_address_reported);
                } else {
                    mStreetAddressText.setText(friendStatus.mStreetAddress);
                }
                if (selfStatus != null) {
                    int distance = Utils.calculateLocationDistanceInMeters(
                            selfStatus.mLongitude,
                            selfStatus.mLatitude,
                            friendStatus.mLongitude,
                            friendStatus.mLatitude);
                    mDistanceText.setText(
                            getString(R.string.format_location_details_distance, distance));
                } else {
                    mDistanceText.setText(R.string.prompt_unknown_distance);
                }
                mCoordinatesText.setText(
                        getString(R.string.format_location_details_coordinates, friendStatus.mLongitude, friendStatus.mLatitude));
                mPrecisionText.setText(
                        getString(R.string.format_location_details_precision, friendStatus.mPrecision));
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

                Robohash.setRobohashImage(this, mAvatarImage, true, self.mPublicIdentity);
                mNicknameText.setText(self.mPublicIdentity.mNickname);
                mFingerprintText.setText(Utils.formatFingerprint(self.mPublicIdentity.getFingerprint()));        
                mStreetAddressText.setText(selfStatus.mStreetAddress);
                mDistanceLabel.setVisibility(View.GONE);
                mDistanceText.setVisibility(View.GONE);
                mCoordinatesText.setText(
                        getString(R.string.format_location_details_coordinates, selfStatus.mLongitude, selfStatus.mLatitude));
                mPrecisionText.setText(
                        getString(R.string.format_location_details_precision, selfStatus.mPrecision));
                mStatusTimestampText.setText(Utils.formatSameDayTime(selfStatus.mTimestamp));
                mLastReceivedStatusTimestampLabel.setVisibility(View.GONE);
                mLastReceivedStatusTimestampText.setVisibility(View.GONE);
                mLastSentStatusTimestampLabel.setVisibility(View.GONE);
                mLastSentStatusTimestampText.setVisibility(View.GONE);
                mAddedTimestampText.setVisibility(View.GONE);
            }

            return;
        } catch (Data.DataNotFoundError e) {
            Toast toast = Toast.makeText(this, getString(R.string.prompt_location_details_data_not_found), Toast.LENGTH_SHORT);
            toast.show();
            finish();
        } catch (Utils.ApplicationError e) {
            // TODO: log?
            finish();
        }
    }
}
