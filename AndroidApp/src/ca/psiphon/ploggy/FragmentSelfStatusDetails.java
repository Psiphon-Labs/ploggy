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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * User interface which displays self status details and
 * allows user to set message.
 * 
 * This class subscribes to status events to update data
 * while in the foreground (e.g., location data updated
 * the the engine).
 */
public class FragmentSelfStatusDetails extends Fragment implements TextView.OnEditorActionListener {
    
    private static final String LOG_TAG = "Self Status Details";

    private ImageView mAvatarImage;
    private TextView mNicknameText;
    private TextView mFingerprintText;
    private EditText mMessageContentEdit;
    private TextView mMessageTimestampLabel;
    private TextView mMessageTimestampText;
    private TextView mLocationLabel;
    private TextView mLocationStreetAddressLabel;
    private TextView mLocationStreetAddressText;
    private TextView mLocationCoordinatesLabel;
    private TextView mLocationCoordinatesText;
    private TextView mLocationPrecisionLabel;
    private TextView mLocationPrecisionText;
    private TextView mLocationTimestampLabel;
    private TextView mLocationTimestampText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.self_status_details, container, false);

        mAvatarImage = (ImageView)view.findViewById(R.id.self_status_details_avatar_image);
        mNicknameText = (TextView)view.findViewById(R.id.self_status_details_nickname_text);
        mFingerprintText = (TextView)view.findViewById(R.id.self_status_details_fingerprint_text);
        mMessageContentEdit = (EditText)view.findViewById(R.id.self_status_details_message_content_edit);
        mMessageTimestampLabel = (TextView)view.findViewById(R.id.self_status_details_message_timestamp_label);
        mMessageTimestampText = (TextView)view.findViewById(R.id.self_status_details_message_timestamp_text);
        mLocationLabel = (TextView)view.findViewById(R.id.self_status_details_location_label);
        mLocationStreetAddressLabel = (TextView)view.findViewById(R.id.self_status_details_location_street_address_label);
        mLocationStreetAddressText = (TextView)view.findViewById(R.id.self_status_details_location_street_address_text);
        mLocationCoordinatesLabel = (TextView)view.findViewById(R.id.self_status_details_location_coordinates_label);
        mLocationCoordinatesText = (TextView)view.findViewById(R.id.self_status_details_location_coordinates_text);
        mLocationPrecisionLabel = (TextView)view.findViewById(R.id.self_status_details_location_precision_label);
        mLocationPrecisionText = (TextView)view.findViewById(R.id.self_status_details_location_precision_text);
        mLocationTimestampLabel = (TextView)view.findViewById(R.id.self_status_details_location_timestamp_label);
        mLocationTimestampText = (TextView)view.findViewById(R.id.self_status_details_location_timestamp_text);

        show(view);

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
        View view = getView();
        if (view != null) { 
            show(view);
        }
    }       

    private void show(View view) {
        try {
            Data data = Data.getInstance();
            Data.Self self = data.getSelf();
            Data.Status selfStatus = data.getSelfStatus();
            
            // Entire view may be hidden due to DataNotFoundError below
            view.setVisibility(View.VISIBLE);

            Robohash.setRobohashImage(getActivity(), mAvatarImage, true, self.mPublicIdentity);
            mNicknameText.setText(self.mPublicIdentity.mNickname);
            mFingerprintText.setText(Utils.formatFingerprint(self.mPublicIdentity.getFingerprint()));

            int messageVisibility = (selfStatus.mMessage.mTimestamp != null) ? View.VISIBLE : View.GONE;
            // Nowt: always show message section label and content edit
            mMessageTimestampLabel.setVisibility(messageVisibility);
            mMessageTimestampText.setVisibility(messageVisibility);
            mMessageContentEdit.setOnEditorActionListener(this);
            if (selfStatus.mMessage.mTimestamp != null) {
                mMessageContentEdit.setText(selfStatus.mMessage.mContent);
                mMessageTimestampText.setText(Utils.formatSameDayTime(selfStatus.mMessage.mTimestamp));                
            }

            int locationVisibility = (selfStatus.mLocation.mTimestamp != null) ? View.VISIBLE : View.GONE;
            mLocationLabel.setVisibility(locationVisibility);
            mLocationStreetAddressLabel.setVisibility(locationVisibility);
            mLocationStreetAddressText.setVisibility(locationVisibility);
            mLocationCoordinatesLabel.setVisibility(locationVisibility);
            mLocationCoordinatesText.setVisibility(locationVisibility);
            mLocationPrecisionLabel.setVisibility(locationVisibility);
            mLocationPrecisionText.setVisibility(locationVisibility);
            mLocationTimestampLabel.setVisibility(locationVisibility);
            mLocationTimestampText.setVisibility(locationVisibility);
            if (selfStatus.mLocation.mTimestamp != null) {
                if (selfStatus.mLocation.mStreetAddress.length() > 0) {
                    mLocationStreetAddressText.setText(selfStatus.mLocation.mStreetAddress);
                } else {
                    mLocationStreetAddressText.setText(R.string.prompt_no_street_address_reported);
                }
                mLocationCoordinatesText.setText(
                        getString(
                                R.string.format_status_details_coordinates,
                                selfStatus.mLocation.mLatitude,
                                selfStatus.mLocation.mLongitude));
                mLocationPrecisionText.setText(
                        getString(
                                R.string.format_status_details_precision,
                                selfStatus.mLocation.mPrecision));
                mLocationTimestampText.setText(Utils.formatSameDayTime(selfStatus.mLocation.mTimestamp));
            }
        } catch (Data.DataNotFoundError e) {
            // TODO: display "no data" prompt?
            view.setVisibility(View.GONE);
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to display self status details");
            view.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            try {
                Data.getInstance().updateSelfStatusMessage(
                        new Data.Message(new Date(), mMessageContentEdit.getText().toString()));
            } catch (Utils.ApplicationError e) {
                Log.addEntry(LOG_TAG, "failed to update self message");
            }
            return true;
        }
        return false;
    }
}
