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
import android.widget.AutoCompleteTextView;
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
    private AutoCompleteTextView mMessageEdit;
    private TextView mStreetAddressText;
    private TextView mCoordinatesText;
    private TextView mPrecisionText;
    private TextView mMessageTimestampText;
    private TextView mLocationTimestampText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.self_status_details, container, false);

        mAvatarImage = (ImageView)view.findViewById(R.id.self_status_details_avatar_image);
        mNicknameText = (TextView)view.findViewById(R.id.self_status_details_nickname_text);
        mFingerprintText = (TextView)view.findViewById(R.id.self_status_details_fingerprint_text);
        mMessageEdit = (AutoCompleteTextView)view.findViewById(R.id.self_status_details_message_edit);
        mStreetAddressText = (TextView)view.findViewById(R.id.self_status_details_street_address_text);
        mCoordinatesText = (TextView)view.findViewById(R.id.self_status_details_coordinates_text);
        mPrecisionText = (TextView)view.findViewById(R.id.self_status_details_precision_text);
        mMessageTimestampText = (TextView)view.findViewById(R.id.self_status_details_message_timestamp_text);
        mLocationTimestampText = (TextView)view.findViewById(R.id.self_status_details_location_timestamp_text);
        show();

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
        show();
    }       

    private void show() {
        try {
            Data data = Data.getInstance();
            Data.Self self = data.getSelf();
            Data.Status selfStatus = data.getSelfStatus();
            
            getView().setVisibility(View.VISIBLE);
            Robohash.setRobohashImage(getActivity(), mAvatarImage, true, self.mPublicIdentity);
            mNicknameText.setText(self.mPublicIdentity.mNickname);
            mFingerprintText.setText(Utils.formatFingerprint(self.mPublicIdentity.getFingerprint()));
            mMessageEdit.setText(selfStatus.mMessage.mText);
            mMessageEdit.setOnEditorActionListener(this);
            if (selfStatus.mLocation.mStreetAddress.length() > 0) {
                mStreetAddressText.setText(selfStatus.mLocation.mStreetAddress);
            } else {
                mStreetAddressText.setText(R.string.prompt_no_street_address_reported);
            }
            mCoordinatesText.setText(
                    getString(
                            R.string.format_status_details_coordinates,
                            selfStatus.mLocation.mLatitude,
                            selfStatus.mLocation.mLongitude));
            mPrecisionText.setText(
                    getString(
                            R.string.format_status_details_precision,
                            selfStatus.mLocation.mPrecision));
            mMessageTimestampText.setText(Utils.formatSameDayTime(selfStatus.mMessage.mTimestamp));
            mLocationTimestampText.setText(Utils.formatSameDayTime(selfStatus.mLocation.mTimestamp));
        } catch (Data.DataNotFoundError e) {
            // TODO: display "no data" prompt?
            getView().setVisibility(View.GONE);
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to display self status details");
            getView().setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            try {
                Data.getInstance().updateSelfMessage(
                        new Data.Message(new Date(), mMessageEdit.getText().toString()));
            } catch (Utils.ApplicationError e) {
                Log.addEntry(LOG_TAG, "failed to update self message");
            }
            return true;
        }
        return false;
    }
}
