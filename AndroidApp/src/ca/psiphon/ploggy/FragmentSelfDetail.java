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

import java.util.concurrent.TimeUnit;

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
 * User interface which displays self status details and
 * allows user to set message.
 *
 * This class subscribes to status events to update data
 * while in the foreground (e.g., location data updated
 * the the engine).
 */
public class FragmentSelfDetail extends Fragment {

    private static final String LOG_TAG = "Self Detail";

    private ImageView mAvatarImage;
    private TextView mNicknameText;
    private TextView mFingerprintText;
    private TextView mLocationLabel;
    private TextView mLocationStreetAddressLabel;
    private TextView mLocationStreetAddressText;
    private TextView mLocationCoordinatesLabel;
    private TextView mLocationCoordinatesText;
    private TextView mLocationTimestampLabel;
    private TextView mLocationTimestampText;
    Utils.FixedDelayExecutor mRefreshUIExecutor;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.self_detail, container, false);

        mAvatarImage = (ImageView)view.findViewById(R.id.self_status_details_avatar_image);
        mNicknameText = (TextView)view.findViewById(R.id.self_status_details_nickname_text);
        mFingerprintText = (TextView)view.findViewById(R.id.self_status_details_fingerprint_text);
        mLocationLabel = (TextView)view.findViewById(R.id.self_status_details_location_label);
        mLocationStreetAddressLabel = (TextView)view.findViewById(R.id.self_status_details_location_street_address_label);
        mLocationStreetAddressText = (TextView)view.findViewById(R.id.self_status_details_location_street_address_text);
        mLocationCoordinatesLabel = (TextView)view.findViewById(R.id.self_status_details_location_coordinates_label);
        mLocationCoordinatesText = (TextView)view.findViewById(R.id.self_status_details_location_coordinates_text);
        mLocationTimestampLabel = (TextView)view.findViewById(R.id.self_status_details_location_timestamp_label);
        mLocationTimestampText = (TextView)view.findViewById(R.id.self_status_details_location_timestamp_text);

        // Refresh the message list every 5 seconds. This updates "time ago" displays.
        // TODO: event driven redrawing?
        mRefreshUIExecutor = new Utils.FixedDelayExecutor(
                new Runnable() {@Override public void run() {show();}},
                TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS));

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        Events.getInstance().register(this);
        mRefreshUIExecutor.start();
        getActivity().setTitle(R.string.navigation_drawer_item_self_detail);
        show();
    }

    @Override
    public void onPause() {
        super.onPause();
        Events.getInstance().unregister(this);
        mRefreshUIExecutor.stop();
    }

    @Subscribe
    public void onUpdatedSelf(Events.UpdatedSelf updatedSelf) {
        show();
    }

    @Subscribe
    public void onUpdatedSelfLocation(Events.UpdatedSelfLocation updatedSelfLocation) {
        show();
    }

    private void show() {
        try {
            Context context = getActivity();
            Data data = Data.getInstance();

            Data.Self self = data.getSelfOrThrow();
            Protocol.Location selfLocation = null;
            try {
                selfLocation = data.getSelfLocation();
            } catch (Data.NotFoundError e) {
                // Won't be able to compute distance
            }

            Avatar.setAvatarImage(context, mAvatarImage, self.mPublicIdentity);
            mNicknameText.setText(self.mPublicIdentity.mNickname);
            mFingerprintText.setText(Utils.formatFingerprint(self.mPublicIdentity.getFingerprint()));

            int locationVisibility = (selfLocation != null) ? View.VISIBLE : View.GONE;
            mLocationLabel.setVisibility(locationVisibility);
            mLocationStreetAddressLabel.setVisibility(locationVisibility);
            mLocationStreetAddressText.setVisibility(locationVisibility);
            mLocationCoordinatesLabel.setVisibility(locationVisibility);
            mLocationCoordinatesText.setVisibility(locationVisibility);
            mLocationTimestampLabel.setVisibility(locationVisibility);
            mLocationTimestampText.setVisibility(locationVisibility);
            if (selfLocation != null) {
                if (selfLocation.mStreetAddress.length() > 0) {
                    mLocationStreetAddressText.setText(selfLocation.mStreetAddress);
                } else {
                    mLocationStreetAddressText.setText(R.string.prompt_no_street_address_reported);
                }
                mLocationCoordinatesText.setText(
                        getString(
                                R.string.format_status_details_coordinates,
                                selfLocation.mLatitude,
                                selfLocation.mLongitude));
                mLocationTimestampText.setText(Utils.DateFormatter.formatRelativeDatetime(context, selfLocation.mTimestamp, true));
            }
        } catch (PloggyError e) {
            // TODO: hide identity/message views?
            Log.addEntry(LOG_TAG, "failed to display self detail");
        }
    }
}
