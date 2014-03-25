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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.squareup.otto.Subscribe;

/**
 * User interface which displays a list of friends.

 * This class subscribes to friend and status events to update displayed data
 * while in the foreground.
 */
public class FragmentFriendList extends ListFragment implements ActionMode.Callback, View.OnLongClickListener {

    private static final String LOG_TAG = "Friend List";

    private Adapters.FriendAdapter mFriendAdapter;
    private Utils.FixedDelayExecutor mRefreshUIExecutor;
    private ActionMode mActionMode;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        mFriendAdapter = new Adapters.FriendAdapter(
                getActivity(),
                new Adapters.CursorFactory<Data.Friend>() {
                    @Override
                    public Data.ObjectCursor<Data.Friend> makeCursor() throws PloggyError {
                        return Data.getInstance().getFriends();
                    }
                });

        // Refresh the message list every 5 seconds. This updates "time ago" displays.
        // TODO: event driven redrawing?
        mRefreshUIExecutor = new Utils.FixedDelayExecutor(
                new Runnable() {@Override public void run() {updateFriends(false);}}, 5000);

        setHasOptionsMenu(true);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (mFriendAdapter != null) {
            setListAdapter(mFriendAdapter);
        }
        registerForContextMenu(getListView());
    }

    @Override
    public void onResume() {
        super.onResume();
        Events.getInstance().register(this);
        mRefreshUIExecutor.start();
        getActivity().setTitle(R.string.navigation_drawer_item_friend_list);
    }

    @Override
    public void onPause() {
        super.onPause();
        Events.getInstance().unregister(this);
        mRefreshUIExecutor.stop();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.friend_list_actions, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add_friend) {
            startActivity(new Intent(getActivity(), ActivityAddFriend.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        Data.Friend friend = (Data.Friend)listView.getItemAtPosition(position);
        startActivity(
                ActivityMain.makeDisplayViewIntent(
                        getActivity(),
                        new ActivityMain.ViewTag(
                                ActivityMain.ViewType.FRIEND_DETAIL,
                                friend.mId)));
    }

    @Override
    public boolean onLongClick(View view) {
        if (mActionMode == null) {
            mActionMode = getActivity().startActionMode(this);
            // *TODO* need setItemChecked + android:background="?android:attr/activatedBackgroundIndicator" ...?
            view.setSelected(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.friend_list_context, menu);
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (item.getItemId() == R.id.action_friend_list_delete_friend) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            promptDeleteFriend((Data.Friend)getListView().getItemAtPosition(info.position));
            mode.finish();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionMode = null;
    }

    private void promptDeleteFriend(Data.Friend friend) {
        // TODO: undo vs. confirmation prompt
        final Data.Friend finalFriend = friend;
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
    public void onRemovedFriend(Events.RemovedFriend removedFriend) {
        updateFriends(true);
    }

    @Subscribe
    public void UpdatedSelfPost(Events.UpdatedSelfPost updatedSelfPost) {
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
