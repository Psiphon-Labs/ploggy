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

import com.squareup.otto.Subscribe;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

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
public class ActivityLocationDetails extends Activity implements View.OnClickListener {
    
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
    private TextView mLastReceivedTimestampLabel;
    private TextView mLastReceivedTimestampText;
    private TextView mLastSentTimestampLabel;
    private TextView mLastSentTimestampText;
    private ImageView mMapImage;
    private Button mDeleteFriendButton;

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
        mLastReceivedTimestampLabel = (TextView)findViewById(R.id.location_details_last_received_timestamp_label);
        mLastReceivedTimestampText = (TextView)findViewById(R.id.location_details_last_received_timestamp_text);
        mLastSentTimestampLabel = (TextView)findViewById(R.id.location_details_last_sent_timestamp_label);
        mLastSentTimestampText = (TextView)findViewById(R.id.location_details_last_sent_timestamp_text);
        mMapImage = (ImageView)findViewById(R.id.location_details_map_image);
        mDeleteFriendButton = (Button)findViewById(R.id.location_details_delete_friend_button);
        mDeleteFriendButton.setOnClickListener(this);

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
    public void onUpedatedFriendStatus(Events.UpdatedFriendStatus updatedFriendStatus) {
        showDetails();
    }       

    private void showDetails() {
        try {
            Data data = Data.getInstance();
            Data.Self self = data.getSelf();
            Data.Status selfStatus = data.getSelfStatus();
            Data.Friend friend = null;
            Data.Status friendStatus = null;
            
            // TODO: mMapImage is just a static placeholder
            
            if (mFriendId != null) {
                friend = data.getFriendById(mFriendId);
                friendStatus = data.getFriendStatus(mFriendId);

                Robohash.setRobohashImage(this, mAvatarImage, true, friend.mPublicIdentity);
                mNicknameText.setText(friend.mPublicIdentity.mNickname);
                mFingerprintText.setText(Utils.encodeHex(friend.mPublicIdentity.getFingerprint()));        
                mStreetAddressText.setText(friendStatus.mStreetAddress);
                int distance = Utils.calculateLocationDistanceInMeters(
                        selfStatus.mLongitude,
                        selfStatus.mLatitude,
                        friendStatus.mLongitude,
                        friendStatus.mLatitude);
                mDistanceText.setText(
                        getString(R.string.format_location_details_distance, distance));
                mCoordinatesText.setText(
                        getString(R.string.format_location_details_coordinates, friendStatus.mLongitude, friendStatus.mLatitude));
                mPrecisionText.setText(
                        getString(R.string.format_location_details_precision, friendStatus.mPrecision));
                mLastReceivedTimestampText.setText(friendStatus.mTimestamp);
                // TODO: where are last-sent timestamps recorded?
                mLastSentTimestampText.setText("");
            } else {
                Robohash.setRobohashImage(this, mAvatarImage, true, self.mPublicIdentity);
                mNicknameText.setText(self.mPublicIdentity.mNickname);
                mFingerprintText.setText(Utils.encodeHex(self.mPublicIdentity.getFingerprint()));        
                mStreetAddressText.setText(selfStatus.mStreetAddress);
                mDistanceLabel.setVisibility(View.GONE);
                mDistanceText.setVisibility(View.GONE);
                mCoordinatesText.setText(
                        getString(R.string.format_location_details_coordinates, selfStatus.mLongitude, selfStatus.mLatitude));
                mPrecisionText.setText(
                        getString(R.string.format_location_details_precision, selfStatus.mPrecision));
                mLastReceivedTimestampLabel.setVisibility(View.GONE);
                mLastReceivedTimestampText.setVisibility(View.GONE);
                mLastSentTimestampLabel.setVisibility(View.GONE);
                mLastSentTimestampText.setVisibility(View.GONE);
                mDeleteFriendButton.setVisibility(View.GONE);
                mDeleteFriendButton.setEnabled(false);
            }

            return;
        } catch (Data.DataNotFoundException e) {
            // TODO: could render Friend without selfStatus, omitting distance?
            Toast toast = Toast.makeText(this, getString(R.string.prompt_location_details_data_not_found), Toast.LENGTH_SHORT);
            toast.show();
            finish();
        } catch (Utils.ApplicationError e) {
            // TODO: log?
            finish();
        }
    }

    @Override
    public void onClick(View view) {
        if (view.equals(mDeleteFriendButton)) {
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.label_location_details_delete_friend_title))
                .setMessage(getString(R.string.label_location_details_delete_friend_message))
                .setPositiveButton(getString(R.string.label_location_details_delete_friend_positive),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (mFriendId != null) {
                                    try {
                                        Data.getInstance().removeFriend(mFriendId);
                                    } catch (Data.DataNotFoundException e) {
                                        // Ignore
                                    } catch (Utils.ApplicationError e) {
                                        // TODO: log?
                                    }
                                    finish();
                                }
                            }
                
                        })
                .setNegativeButton(getString(R.string.label_location_details_delete_friend_negative), null)
                .show();            
        }
    }
}
