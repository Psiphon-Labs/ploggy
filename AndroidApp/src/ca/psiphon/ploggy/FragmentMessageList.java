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

import java.util.List;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

/**
 * User interface which displays all messages.

 * This class subscribes to friend and status events to update displayed data
 * while in the foreground.
 */
public class FragmentMessageList extends ListFragment {

    private static final String LOG_TAG = "Message List";

    private boolean mIsResumed = false;
    private View mHeaderView;
    private AnnotatedMessageAdapter mMessageAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (mHeaderView == null) {
            mHeaderView = inflater.inflate(R.layout.message_list_header, null);
        }
        try {
            mMessageAdapter = new AnnotatedMessageAdapter(getActivity());
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to initialize message adapter");
        }
        return view;
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        setListAdapter(null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (getListView().getHeaderViewsCount() == 0) {
            getListView().addHeaderView(mHeaderView);
        }
        if (mMessageAdapter != null) {
            setListAdapter(mMessageAdapter);
        }
        Events.register(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsResumed = true;
        Events.post(new Events.DisplayedMessages());
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsResumed = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Events.unregister(this);
    }

    @Subscribe
    public void onUpdatedNewMessages(Events.UpdatedNewMessages updatedNewMessages) {
        if (mIsResumed) {
            Events.post(new Events.DisplayedMessages());
        }
    }

    @Subscribe
    public void onUpdatedAllMessages(Events.UpdatedAllMessages updatedAllMessages) {
        updateMessages();
    }

    private void updateMessages() {
        try {
            mMessageAdapter.updateMessages();
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to update message list");
        }
    }

    public static class AnnotatedMessageAdapter extends BaseAdapter {
        private Context mContext;
        private List<Data.AnnotatedMessage> mMessages;
        
        public AnnotatedMessageAdapter(Context context) throws Utils.ApplicationError {
            mContext = context;
            mMessages = Data.getInstance().getAllMessages();
        }
        
        public void updateMessages() throws Utils.ApplicationError {
            mMessages = Data.getInstance().getAllMessages();
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.message_list_row, null);
            }
            Data.AnnotatedMessage message = mMessages.get(position);
            if (message != null) {
                TextView timestampText = (TextView)view.findViewById(R.id.message_timestamp_text);
                TextView nicknameText = (TextView)view.findViewById(R.id.message_nickname_text);
                TextView contentText = (TextView)view.findViewById(R.id.message_content_text);

                timestampText.setText(Utils.DateFormatter.formatRelativeDatetime(mContext, message.mMessage.mTimestamp, true));
                nicknameText.setText(message.mNickname);
                contentText.setText(message.mMessage.mContent);
            }
            return view;
        }

        @Override
        public int getCount() {
            return mMessages.size();
        }

        @Override
        public Object getItem(int position) {
            return mMessages.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }
}
