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

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.squareup.otto.Subscribe;

/**
 * User interface which displays all messages.
 *
 * This class subscribes to friend and status events to update displayed data
 * while in the foreground.
 */
public class FragmentMessageList extends Fragment {

    private static final String LOG_TAG = "Message List";

    private boolean mIsResumed = false;
    private Fragment mFragmentComposeMessage;
    private ListView mMessagesListView;
    private MessageAdapter mMessageAdapter;
    Utils.FixedDelayExecutor mRefreshUIExecutor;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.message_list, container, false);

        mFragmentComposeMessage = new FragmentComposeMessage();
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.add(R.id.fragment_message_list_compose_message, mFragmentComposeMessage).commit();

        mMessagesListView = (ListView)view.findViewById(R.id.message_list_messages);

        try {
            mMessageAdapter = new MessageAdapter(getActivity(), MessageAdapter.Mode.ALL_MESSAGES);
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to initialize message adapter");
        }

        // Refresh the message list every 5 seconds. This updates download state and "time ago" displays.
        // TODO: event driven redrawing?
        mRefreshUIExecutor = new Utils.FixedDelayExecutor(
                new Runnable() {@Override public void run() {updateMessages();}}, 5000);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (mMessageAdapter != null) {
            mMessagesListView.setAdapter(mMessageAdapter);
        }
        Events.register(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsResumed = true;
        mRefreshUIExecutor.start();
        Events.post(new Events.DisplayedMessages());
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsResumed = false;
        mRefreshUIExecutor.stop();
    }

    @Override
    public void onDestroyView() {
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.remove(mFragmentComposeMessage).commitAllowingStateLoss();
        Events.unregister(this);
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Note: require explicit result routing for nested fragment
        if (mFragmentComposeMessage != null) {
            mFragmentComposeMessage.onActivityResult(requestCode, resultCode, data);
        }
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
}
