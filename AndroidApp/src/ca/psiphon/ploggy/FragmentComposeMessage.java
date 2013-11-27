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
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

/**
 * Re-usable user interface fragment which implements composing and sending a new message.
 */
public class FragmentComposeMessage extends Fragment implements View.OnClickListener, TextView.OnEditorActionListener {

    private static final String LOG_TAG = "Compose Message";

    private EditText mContentEdit;
    private ImageButton mAddButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.compose_message, container, false);

        mContentEdit = (EditText)view.findViewById(R.id.compose_message_content_edit);
        mAddButton = (ImageButton)view.findViewById(R.id.compose_message_add_button);

        InputFilter[] filters = new InputFilter[1];
        filters[0] = new InputFilter.LengthFilter(Protocol.MAX_MESSAGE_LENGTH);
        mContentEdit.setFilters(filters);
        mContentEdit.setOnEditorActionListener(this);

        mAddButton.setOnClickListener(this);

        return view;
    }

    @Override
    public void onClick(View view) {
        if (view.equals(mAddButton)) {
            addNewMessage();
        }
    }

    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
        if (textView.equals(mContentEdit) &&
                actionId == EditorInfo.IME_ACTION_SEND) {
            addNewMessage();
            return true;
        }
        return false;
    }

    private void addNewMessage() {
        try {
            String messageContent = mContentEdit.getText().toString();
            if (messageContent.length() > 0) {
                Data.getInstance().addSelfStatusMessage(new Data.Message(new Date(), messageContent));
                mContentEdit.getEditableText().clear();
                Utils.hideKeyboard(getActivity());
            }
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to update self message");
        }
    }
}
