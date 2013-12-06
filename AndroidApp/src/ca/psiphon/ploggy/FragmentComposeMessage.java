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

import java.io.File;
import java.util.Date;

import com.squareup.picasso.Picasso;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.text.InputFilter;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Re-usable user interface fragment which implements composing and sending a new message.
 */
public class FragmentComposeMessage extends Fragment implements View.OnClickListener, TextView.OnEditorActionListener {

    private static final String LOG_TAG = "Compose Message";

    // TODO: support multiple attachments

    private ImageButton mSetPictureButton;
    private ImageView mPictureThumbnail;
    private String mPictureMimeType;
    private String mPicturePath;
    private EditText mContentEdit;
    private ImageButton mSendButton;
    
    private static final int REQUEST_CODE_SELECT_IMAGE = 1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.compose_message, container, false);

        mSetPictureButton = (ImageButton)view.findViewById(R.id.compose_message_set_picture_button);
        mPictureThumbnail = (ImageView)view.findViewById(R.id.compose_message_picture_thumbnail);
        mContentEdit = (EditText)view.findViewById(R.id.compose_message_content_edit);
        mSendButton = (ImageButton)view.findViewById(R.id.compose_message_send_button);
        
        mSetPictureButton.setOnClickListener(this);
        
        mPictureThumbnail.setVisibility(View.GONE);
        mPictureThumbnail.setOnClickListener(this);
        registerForContextMenu(mPictureThumbnail);

        InputFilter[] filters = new InputFilter[1];
        filters[0] = new InputFilter.LengthFilter(Protocol.MAX_MESSAGE_LENGTH);
        mContentEdit.setFilters(filters);
        mContentEdit.setOnEditorActionListener(this);

        mSendButton.setOnClickListener(this);

        return view;
    }

    @Override
    public void onClick(View view) {
        if (view.equals(mSendButton)) {
            addNewMessage();
        } else if (view.equals(mSetPictureButton) || view.equals(mPictureThumbnail)) {
            selectPicture();
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_CODE_SELECT_IMAGE && data != null && data.getData() != null) {
            setPicture(data.getData());
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        if (view.equals(mPictureThumbnail)) {
            getActivity().getMenuInflater().inflate(R.menu.compose_message_picture_context, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_compose_message_remove_picture) {
            resetPicture();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    private void addNewMessage() {
        try {
            String messageContent = mContentEdit.getText().toString();
            if (mPicturePath != null || messageContent.length() > 0) {
                Resources.MessageWithAttachments message = Resources.createMessageWithAttachment(
                        new Date(),
                        messageContent,
                        Data.LocalResource.Type.PICTURE,
                        mPictureMimeType,
                        mPicturePath);
                Data.getInstance().addSelfStatusMessage(message.mMessage, message.mLocalResources);
                mContentEdit.getEditableText().clear();
                resetPicture();
                Utils.hideKeyboard(getActivity());
            }
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to update self message");
        }
    }

    private void selectPicture() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(
                Intent.createChooser(
                        intent,
                        getText(R.string.prompt_compose_message_select_picture)),
                        REQUEST_CODE_SELECT_IMAGE);
    }
    
    private void resetPicture() {
        mSetPictureButton.setVisibility(View.VISIBLE);
        mPictureThumbnail.setVisibility(View.GONE);
        mPictureMimeType = null;
        mPicturePath = null;
    }

    private void setPicture(Uri pictureUri) {
        // Try to use the MediaStore for gallery selections; otherwise, treat
        // the URI as a filesystem path.
        String path = null;
        String mimeType = null;
        Cursor cursor = null;
        try {
            String[] projection = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.MIME_TYPE};
            cursor = getActivity().getContentResolver().query(pictureUri, projection, null, null, null);
            if (cursor != null) {
                int dataColumnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                int mimeTypeColumnIndex = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE);
                if (dataColumnIndex != -1 && mimeTypeColumnIndex != -1) {
                    cursor.moveToFirst();
                    path = cursor.getString(dataColumnIndex);
                    mimeType = cursor.getString(mimeTypeColumnIndex);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (mimeType == null) {
            // TODO: vs. MimeTypeMap.getMimeTypeFromExtension?
            mimeType = getActivity().getContentResolver().getType(pictureUri);
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }
        }

        // For non-MediaStore cases
        if (path == null) {
            path = pictureUri.getPath();
        }

        // Abort when no path
        if (path == null) {
            return;
        }

        // Show a thumbnail; also, hide the add picture button (user can change picture by touching thumbnail instead).
        mSetPictureButton.setVisibility(View.GONE);
        Picasso.with(getActivity()).load(new File(path)).into(mPictureThumbnail);
        mPictureThumbnail.setVisibility(View.VISIBLE);

        // These fields hold the picture values used when the message is sent
        mPicturePath = path;
        mPictureMimeType = mimeType;        
    }
}
