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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.InputFilter;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ScrollView;
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
public class FragmentSelfStatusDetails extends Fragment implements View.OnClickListener, TextView.OnEditorActionListener {

    private static final String LOG_TAG = "Self Status Details";

    private ScrollView mScrollView;
    private ImageView mAvatarImage;
    private TextView mNicknameText;
    private TextView mFingerprintText;
    private EditText mNewMessageContentEdit;
    private ImageButton mNewMessageAddButton;
    private ListView mMessagesList;
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

        mScrollView = (ScrollView)view.findViewById(R.id.self_status_details_scroll_view);
        mAvatarImage = (ImageView)view.findViewById(R.id.self_status_details_avatar_image);
        mNicknameText = (TextView)view.findViewById(R.id.self_status_details_nickname_text);
        mFingerprintText = (TextView)view.findViewById(R.id.self_status_details_fingerprint_text);
        mNewMessageContentEdit = (EditText)view.findViewById(R.id.self_status_details_new_message_content_edit);
        mNewMessageAddButton = (ImageButton)view.findViewById(R.id.self_status_details_new_message_add_button);
        mMessagesList = (ListView)view.findViewById(R.id.self_status_details_messages_list);
        mLocationLabel = (TextView)view.findViewById(R.id.self_status_details_location_label);
        mLocationStreetAddressLabel = (TextView)view.findViewById(R.id.self_status_details_location_street_address_label);
        mLocationStreetAddressText = (TextView)view.findViewById(R.id.self_status_details_location_street_address_text);
        mLocationCoordinatesLabel = (TextView)view.findViewById(R.id.self_status_details_location_coordinates_label);
        mLocationCoordinatesText = (TextView)view.findViewById(R.id.self_status_details_location_coordinates_text);
        mLocationPrecisionLabel = (TextView)view.findViewById(R.id.self_status_details_location_precision_label);
        mLocationPrecisionText = (TextView)view.findViewById(R.id.self_status_details_location_precision_text);
        mLocationTimestampLabel = (TextView)view.findViewById(R.id.self_status_details_location_timestamp_label);
        mLocationTimestampText = (TextView)view.findViewById(R.id.self_status_details_location_timestamp_text);

        // TODO: use header/footer of listview instead of hack embedding of listview in scrollview
        // from: http://stackoverflow.com/questions/4490821/scrollview-inside-scrollview/11554823#11554823
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

        InputFilter[] filters = new InputFilter[1];
        filters[0] = new InputFilter.LengthFilter(Protocol.MAX_MESSAGE_LENGTH);
        mNewMessageContentEdit.setFilters(filters);
        mNewMessageContentEdit.setOnEditorActionListener(this);

        mNewMessageAddButton.setOnClickListener(this);

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
    public void onUpdatedSelf(Events.UpdatedSelf updatedSelf) {
        View view = getView();
        if (view != null) {
            show(view);
        }
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

            // Note: always show message section label and content edit
            int messageVisibility = (selfStatus.mMessages.size() > 0) ? View.VISIBLE : View.GONE;
            mMessagesList.setVisibility(messageVisibility);
            if (selfStatus.mMessages.size() > 0) {
                Utils.MessageAdapter adapter = new Utils.MessageAdapter(getActivity(), selfStatus.mMessages);
                mMessagesList.setAdapter(adapter);
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
                mLocationTimestampText.setText(Utils.DateFormatter.formatRelativeDatetime(getActivity(), selfStatus.mLocation.mTimestamp, true));
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
    public void onClick(View view) {
        if (view.equals(mNewMessageAddButton)) {
            addNewMessage();
        }
    }

    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
        if (textView.equals(mNewMessageContentEdit) &&
                actionId == EditorInfo.IME_ACTION_SEND) {
            addNewMessage();
            return true;
        }
        return false;
    }

    private void addNewMessage() {
        try {
            String messageContent = mNewMessageContentEdit.getText().toString();
            if (messageContent.length() > 0) {
                Data.getInstance().addSelfStatusMessage(
                        new Data.Message(new Date(), messageContent));
                mNewMessageContentEdit.getEditableText().clear();
                Utils.hideKeyboard(getActivity());
            }
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to update self message");
        }
    }
}
