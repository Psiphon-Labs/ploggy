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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import ca.psiphon.ploggy.Adapters.FriendAdapter;
import ca.psiphon.ploggy.Data.Friend;
import ca.psiphon.ploggy.Data.ObjectCursor;

import com.squareup.otto.Subscribe;

/**
 * User interface which displays a list of friends.

 * This class subscribes to friend and status events to update displayed data
 * while in the foreground.
 */
public class FragmentFriendList extends ListFragment {

    private static final String LOG_TAG = "Friend List";

    private FriendAdapter mFriendAdapter;
    Utils.FixedDelayExecutor mRefreshUIExecutor;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        mFriendAdapter = new FriendAdapter(
                getActivity(),
                new Adapters.CursorFactory<Data.Friend>() {
                    @Override
                    public ObjectCursor<Friend> makeCursor() throws PloggyError {
                        return Data.getInstance().getFriends();
                    }
                });

        // Refresh the message list every 5 seconds. This updates "time ago" displays.
        // TODO: event driven redrawing?
        mRefreshUIExecutor = new Utils.FixedDelayExecutor(
                new Runnable() {@Override public void run() {updateFriends(false);}}, 5000);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (mFriendAdapter != null) {
            setListAdapter(mFriendAdapter);
        }
        registerForContextMenu(getListView());
        Events.getInstance().register(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mRefreshUIExecutor.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        mRefreshUIExecutor.stop();
    }

    @Override
    public void onDestroyView() {
        Events.getInstance().unregister(this);
        super.onDestroyView();
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        Data.Friend friend = (Data.Friend)listView.getItemAtPosition(position);
        Intent intent = new Intent(getActivity(), ActivityFriendStatusDetails.class);
        Bundle bundle = new Bundle();
        bundle.putString(ActivityFriendStatusDetails.FRIEND_ID_BUNDLE_KEY, friend.mId);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        if (view.equals(getListView())) {
            getActivity().getMenuInflater().inflate(R.menu.friend_list_context, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // *TODO* use CAB for long-press actions (https://developer.android.com/design/patterns/selection.html)
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        if (item.getItemId() == R.id.action_friend_list_delete_friend) {
            final Data.Friend finalFriend = (Data.Friend)getListView().getItemAtPosition(info.position);
            new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.label_delete_friend_title))
                .setMessage(getString(R.string.label_delete_friend_message, finalFriend.mPublicIdentity.mNickname))
                .setPositiveButton(getString(R.string.label_delete_friend_positive),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Data.getInstance().removeFriend(finalFriend.mId);
                                } catch (PloggyError e) {
                                    Log.addEntry(LOG_TAG, "failed to delete friend: " + finalFriend.mPublicIdentity.mNickname);
                                }
                            }
                        })
                .setNegativeButton(getString(R.string.label_delete_friend_negative), null)
                .show();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Subscribe
    public void onUpdatedSelfLocation(Events.UpdatedFriendLocation updatedSelfLocation) {
        // Distance calculation may change in this case
        updateFriends(true);
    }

    @Subscribe
    public void onAddedFriend(Events.AddedFriend addedFriend) {
        updateFriends(true);
    }

    @Subscribe
    public void onUpdatedFriend(Events.UpdatedFriend updatedFriend) {
        updateFriends(true);
    }

    @Subscribe
    public void onUpdatedFriendLocation(Events.UpdatedFriendLocation updatedFriendLocation) {
        updateFriends(true);
    }

    @Subscribe
    public void onDeletedFriend(Events.RemovedFriend removedFriend) {
        updateFriends(true);
    }

    @Subscribe
    public void UpdatedFriendPost(Events.UpdatedFriendPost updatedFriendPost) {
        updateFriends(true);
    }

    private void updateFriends(boolean requery) {
        mFriendAdapter.update(requery);
    }
}
