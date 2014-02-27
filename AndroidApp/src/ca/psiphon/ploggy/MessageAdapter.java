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
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * List adapter which displays rows of messages.
 *
 * Includes widgets/logic for managing downloads of message attachments.
 * When the message belongs to self, message attachments are treated as
 * local resources, not downloads.
 *
 * This component has three modes:
 * 1. General message list, which displays nickname and avatar.
 * 2. Single friend message list, which just displays message content.
 * 3. Self message list, which just displays message content.
 *
 */
public class MessageAdapter extends BaseAdapter {

    private static final String LOG_TAG = "Message Adapter";

    // TODO: support multiple attachments

    public enum Mode {ALL_MESSAGES, FRIEND_MESSAGES, SELF_MESSAGES}

    private final Context mContext;
    private final Mode mMode;
    private List<Data.AnnotatedMessage> mAnnotatedMessages;
    private final String mFriendId;
    private List<Data.Message> mMessages;

    public MessageAdapter(Context context, Mode mode) throws PloggyError {
        this(context, mode, null);
    }

    public MessageAdapter(Context context, Mode mode, String friendId) throws PloggyError {
        mContext = context;
        mMode = mode;
        mFriendId = friendId;
        updateMessages();
    }

    public void updateMessages() throws PloggyError {
        switch (mMode) {
        case ALL_MESSAGES:
            mAnnotatedMessages = Data.getInstance().getAllMessages();
            break;
        case FRIEND_MESSAGES:
            mMessages = Data.getInstance().getFriendStatus(mFriendId).mMessages;
            break;
        case SELF_MESSAGES:
            mMessages = Data.getInstance().getSelfStatus().mMessages;
            break;
        }
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.message_list_row, null);
        }

        Identity.PublicIdentity publicIdentity = null;
        String friendId = null;
        Data.Message message = null;
        Data.Download download = null;
        double downloadProgress = 0.0;
        Data.LocalResource localResource = null;

        switch (mMode) {
        case ALL_MESSAGES:
            Data.AnnotatedMessage annotatedMessage = mAnnotatedMessages.get(position);
            if (annotatedMessage == null) {
                return view;
            }
            publicIdentity = annotatedMessage.mPublicIdentity;
            friendId = annotatedMessage.mFriendId;
            message = annotatedMessage.mMessage;
            break;
        case FRIEND_MESSAGES:
        case SELF_MESSAGES:
            message = mMessages.get(position);
            if (message == null) {
                return view;
            }
            friendId = mFriendId;
            break;
        }

        if (message.mAttachments != null && message.mAttachments.size() > 0) {
            try {
                if (friendId != null) {
                    download = Data.getInstance().getDownload(friendId, message.mAttachments.get(0).mId);
                    if (download.mState == Data.Download.State.IN_PROGRESS) {
                        long downloadedSize = Downloads.getDownloadedSize(download);
                        if (download.mSize > 0) {
                            downloadProgress = 100.0*downloadedSize/download.mSize;
                        }
                    }
                } else {
                    localResource = Data.getInstance().getLocalResource(message.mAttachments.get(0).mId);
                }
            } catch (Data.DataNotFoundError e) {
                Log.addEntry(LOG_TAG, "attachment data not found");
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to load attachment data");
            }
        }

        ImageView avatarImage = (ImageView)view.findViewById(R.id.message_avatar_image);
        TextView nicknameText = (TextView)view.findViewById(R.id.message_nickname_text);
        TextView contentText = (TextView)view.findViewById(R.id.message_content_text);
        TextView pictureDownloadText = (TextView)view.findViewById(R.id.message_picture_download_text);
        ImageView pictureThumbnailImage = (ImageView)view.findViewById(R.id.message_picture_thumbnail);
        TextView timestampText = (TextView)view.findViewById(R.id.message_timestamp_text);

        if (publicIdentity != null) {
            Robohash.setRobohashImage(mContext, avatarImage, true, publicIdentity);
            nicknameText.setText(publicIdentity.mNickname);
        } else {
            avatarImage.setVisibility(View.GONE);
            nicknameText.setVisibility(View.GONE);
        }
        contentText.setText(message.mContent);

        if (download != null) {
            switch (download.mState) {
            case IN_PROGRESS:
                pictureDownloadText.setVisibility(View.VISIBLE);
                pictureThumbnailImage.setVisibility(View.GONE);
                pictureDownloadText.setText(mContext.getString(R.string.format_download_progress, downloadProgress));
                break;
            case CANCELLED:
                pictureDownloadText.setVisibility(View.VISIBLE);
                pictureThumbnailImage.setVisibility(View.GONE);
                pictureDownloadText.setText("");
                break;
            case COMPLETE:
                pictureDownloadText.setVisibility(View.GONE);
                pictureThumbnailImage.setVisibility(View.VISIBLE);
                Pictures.loadThumbnailWithClickToShowPicture(mContext, Downloads.getDownloadFile(download), pictureThumbnailImage);
                break;
            }
        } else if (localResource != null) {
            pictureDownloadText.setVisibility(View.GONE);
            pictureThumbnailImage.setVisibility(View.VISIBLE);
            Pictures.loadThumbnailWithClickToShowPicture(mContext, new File(localResource.mFilePath), pictureThumbnailImage);
        } else {
            pictureDownloadText.setVisibility(View.GONE);
            pictureThumbnailImage.setVisibility(View.GONE);
        }

        timestampText.setText(Utils.DateFormatter.formatRelativeDatetime(mContext, message.mTimestamp, true));

        return view;
    }

    @Override
    public int getCount() {
        if (mMode == Mode.ALL_MESSAGES) {
            return mAnnotatedMessages.size();
        } else {
            return mMessages.size();
        }
    }

    @Override
    public Object getItem(int position) {
        if (mMode == Mode.ALL_MESSAGES) {
            return mAnnotatedMessages.get(position);
        } else {
            return mMessages.get(position);
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }
}
