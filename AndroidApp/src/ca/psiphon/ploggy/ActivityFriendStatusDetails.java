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
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * User interface for displaying friend status details.

 * Invoke with an intent containing an extra with FRIEND_ID_BUNDLE_KEY.
 * This class subscribes to status events to update displayed data
 * while in the foreground.
 */
public class ActivityFriendStatusDetails extends ActivitySendIdentityByNfc {
    
    private static final String LOG_TAG = "Friend Status Details";
    
    public static final String FRIEND_ID_BUNDLE_KEY = "friendId";
    
    private String mFriendId;
    private ScrollView mScrollView;
    private ImageView mAvatarImage;
    private TextView mNicknameText;
    private TextView mFingerprintText;
    private TextView mMessageLabel;
    private ListView mMessagesList;
    private TextView mLocationLabel;
    private TextView mLocationStreetAddressLabel;
    private TextView mLocationStreetAddressText;
    private TextView mLocationDistanceLabel;
    private TextView mLocationDistanceText;
    private TextView mLocationCoordinatesLabel;
    private TextView mLocationCoordinatesText;
    private TextView mLocationPrecisionLabel;
    private TextView mLocationPrecisionText;
    private TextView mLocationTimestampLabel;
    private TextView mLocationTimestampText;
    private TextView mLastReceivedStatusTimestampText;
    private TextView mLastSentStatusTimestampText;
    private TextView mAddedTimestampText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_status_details);

        mScrollView = (ScrollView)findViewById(R.id.friend_status_details_scroll_view);
        mAvatarImage = (ImageView)findViewById(R.id.friend_status_details_avatar_image);
        mNicknameText = (TextView)findViewById(R.id.friend_status_details_nickname_text);
        mFingerprintText = (TextView)findViewById(R.id.friend_status_details_fingerprint_text);
        mMessageLabel = (TextView)findViewById(R.id.friend_status_details_message_label);
        mMessagesList = (ListView)findViewById(R.id.friend_status_details_messages_list);
        mLocationLabel = (TextView)findViewById(R.id.friend_status_details_location_label);
        mLocationStreetAddressLabel = (TextView)findViewById(R.id.friend_status_details_location_street_address_label);
        mLocationStreetAddressText = (TextView)findViewById(R.id.friend_status_details_location_street_address_text);
        mLocationDistanceLabel = (TextView)findViewById(R.id.friend_status_details_location_distance_label);
        mLocationDistanceText = (TextView)findViewById(R.id.friend_status_details_location_distance_text);
        mLocationCoordinatesLabel = (TextView)findViewById(R.id.friend_status_details_location_coordinates_label);
        mLocationCoordinatesText = (TextView)findViewById(R.id.friend_status_details_location_coordinates_text);
        mLocationPrecisionLabel = (TextView)findViewById(R.id.friend_status_details_location_precision_label);
        mLocationPrecisionText = (TextView)findViewById(R.id.friend_status_details_location_precision_text);
        mLocationTimestampLabel = (TextView)findViewById(R.id.friend_status_details_location_timestamp_label);
        mLocationTimestampText = (TextView)findViewById(R.id.friend_status_details_location_timestamp_text);
        mLastReceivedStatusTimestampText = (TextView)findViewById(R.id.friend_status_details_last_received_status_timestamp_text);
        mLastSentStatusTimestampText = (TextView)findViewById(R.id.friend_status_details_last_sent_status_timestamp_text);
        mAddedTimestampText = (TextView)findViewById(R.id.friend_status_details_added_timestamp_text);

        // TODO: use header/footer of listview instead of hack embedding of listview in scrollview
        mScrollView.setOnTouchListener(
            new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    mMessagesList.requestDisallowInterceptTouchEvent(false);
                    return false;
                }
            });
        mMessagesList.setOnTouchListener(
            new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return false;
                }
            });
        
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
        
        show();
        
        Events.register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    
        Events.unregister(this);
    }

    @Subscribe
    public void onUpdatedFriendStatus(Events.UpdatedFriendStatus updatedFriendStatus) {
        show();
    }

    private void show() {
        try {
            Data data = Data.getInstance();
            Data.Status selfStatus = null;
            Data.Friend friend = data.getFriendById(mFriendId);
            Data.Status friendStatus = data.getFriendStatus(mFriendId);
            
            try {
                selfStatus = data.getSelfStatus();
            } catch (Data.DataNotFoundError e) {
                // Won't be able to compute distance
            }
            Date lastSentStatusTimestamp = data.getFriendLastSentStatusTimestamp(friend.mId);
            Date lastReceivedStatusTimestamp = data.getFriendLastReceivedStatusTimestamp(friend.mId);
    
            Robohash.setRobohashImage(this, mAvatarImage, true, friend.mPublicIdentity);
            mNicknameText.setText(friend.mPublicIdentity.mNickname);
            mFingerprintText.setText(Utils.formatFingerprint(friend.mPublicIdentity.getFingerprint()));

            int messageVisibility = (friendStatus.mMessages.size() > 0) ? View.VISIBLE : View.GONE;
            mMessageLabel.setVisibility(messageVisibility);
            mMessagesList.setVisibility(messageVisibility);
            if (friendStatus.mMessages.size() > 0) {
                mMessagesList.setAdapter(new Utils.MessageAdapter(this, friendStatus.mMessages));
            }

            int locationVisibility = (friendStatus.mLocation.mTimestamp != null) ? View.VISIBLE : View.GONE;
            mLocationLabel.setVisibility(locationVisibility);
            mLocationStreetAddressLabel.setVisibility(locationVisibility);
            mLocationStreetAddressText.setVisibility(locationVisibility);
            mLocationDistanceLabel.setVisibility(locationVisibility);
            mLocationDistanceText.setVisibility(locationVisibility);
            mLocationCoordinatesLabel.setVisibility(locationVisibility);
            mLocationCoordinatesText.setVisibility(locationVisibility);
            mLocationPrecisionLabel.setVisibility(locationVisibility);
            mLocationPrecisionText.setVisibility(locationVisibility);
            mLocationTimestampLabel.setVisibility(locationVisibility);
            mLocationTimestampText.setVisibility(locationVisibility);
            if (friendStatus.mLocation.mTimestamp != null) {
                if (friendStatus.mLocation.mStreetAddress.length() > 0) {
                    mLocationStreetAddressText.setText(friendStatus.mLocation.mStreetAddress);
                } else {
                    mLocationStreetAddressText.setText(R.string.prompt_no_street_address_reported);
                }
                if (selfStatus != null) {
                    int distance = Utils.calculateLocationDistanceInMeters(
                            selfStatus.mLocation.mLatitude,
                            selfStatus.mLocation.mLongitude,
                            friendStatus.mLocation.mLatitude,
                            friendStatus.mLocation.mLongitude);
                    mLocationDistanceText.setText(
                            getString(R.string.format_status_details_distance, distance));
                } else {
                    mLocationDistanceText.setText(R.string.prompt_unknown_distance);
                }
                mLocationCoordinatesText.setText(
                        getString(
                                R.string.format_status_details_coordinates,
                                friendStatus.mLocation.mLatitude,
                                friendStatus.mLocation.mLongitude));
                mLocationPrecisionText.setText(
                        getString(
                                R.string.format_status_details_precision,
                                friendStatus.mLocation.mPrecision));
                mLocationTimestampText.setText(Utils.formatSameDayTime(friendStatus.mLocation.mTimestamp));
            }

            if (lastReceivedStatusTimestamp != null) {
                mLastReceivedStatusTimestampText.setText(Utils.formatSameDayTime(lastReceivedStatusTimestamp));
            } else {
                mLastReceivedStatusTimestampText.setText(R.string.prompt_no_status_updates_received);
            }
            if (lastSentStatusTimestamp != null) {
                mLastSentStatusTimestampText.setText(Utils.formatSameDayTime(lastSentStatusTimestamp));
            } else {
                mLastSentStatusTimestampText.setText(R.string.prompt_no_status_updates_sent);
            }
            mAddedTimestampText.setText(Utils.formatSameDayTime(friend.mAddedTimestamp));
        } catch (Data.DataNotFoundError e) {
            Toast toast = Toast.makeText(this, getString(R.string.prompt_status_details_data_not_found), Toast.LENGTH_SHORT);
            toast.show();
            finish();
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to display friend status details");
            finish();
        }
    }
}
