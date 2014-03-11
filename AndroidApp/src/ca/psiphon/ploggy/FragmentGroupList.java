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

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.squareup.otto.Subscribe;

/**
 * User interface which displays a list of groups.
 */
public class FragmentGroupList extends ListFragment {

    private static final String LOG_TAG = "Group List";

    private Adapters.GroupAdapter mGroupAdapter;
    Utils.FixedDelayExecutor mRefreshUIExecutor;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        mGroupAdapter = new Adapters.GroupAdapter(
                getActivity(),
                new Adapters.CursorFactory<Data.Group>() {
                    @Override
                    public Data.ObjectCursor<Data.Group> makeCursor() throws PloggyError {
                        return Data.getInstance().getVisibleGroups();
                    }
                });

        // Refresh the message list every 5 seconds. This updates "time ago" displays.
        // TODO: event driven redrawing?
        mRefreshUIExecutor = new Utils.FixedDelayExecutor(
                new Runnable() {@Override public void run() {updateGroups(false);}}, 5000);

        setHasOptionsMenu(true);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (mGroupAdapter != null) {
            setListAdapter(mGroupAdapter);
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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.friend_list_actions, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add_group) {
            startActivity(new Intent(getActivity(), ActivityEditGroup.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        Data.Group group = (Data.Group)listView.getItemAtPosition(position);
        startActivity(
                ActivityMain.makeDisplayViewIntent(
                        getActivity(),
                        new ActivityMain.ViewTag(
                                ActivityMain.ViewType.GROUP_POSTS,
                                group.mGroup.mId)));
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        // *TODO*
        /*
        if (view.equals(getListView())) {
            getActivity().getMenuInflater().inflate(R.menu.group_list_context, menu);
        }
        */
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // *TODO* use CAB for long-press actions (https://developer.android.com/design/patterns/selection.html)

        // *TODO* various delete-group cases: when publisher, when not publisher, etc.

        /*
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        if (item.getItemId() == R.id.action_group_list_delete_group) {
            final Data.Friend finalFriend = (Data.Friend)getListView().getItemAtPosition(info.position);
            new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.label_delete_group_title))
                .setMessage(getString(R.string.label_delete_group_message, finalFriend.mPublicIdentity.mNickname))
                .setPositiveButton(getString(R.string.label_delete_group_positive),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Data.getInstance().removeFriend(finalFriend.mId);
                                } catch (PloggyError e) {
                                    Log.addEntry(LOG_TAG, "failed to group friend: " + finalFriend.mPublicIdentity.mNickname);
                                }
                            }
                        })
                .setNegativeButton(getString(R.string.label_delete_group_negative), null)
                .show();
            return true;
        }
        */
        return super.onContextItemSelected(item);
    }

    @Subscribe
    public void onAddedFriend(Events.AddedFriend addedFriend) {
        updateGroups(true);
    }

    @Subscribe
    public void onRemovedFriend(Events.RemovedFriend removedFriend) {
        updateGroups(true);
    }

    @Subscribe
    public void UpdatedSelfGroup(Events.UpdatedSelfGroup updatedSelfGroup) {
        updateGroups(true);
    }

    @Subscribe
    public void UpdatedFriendGroup(Events.UpdatedFriendGroup updatedFriendGroup) {
        updateGroups(true);
    }

    @Subscribe
    public void UpdatedSelfPost(Events.UpdatedSelfPost updatedSelfPost) {
        updateGroups(true);
    }

    @Subscribe
    public void UpdatedFriendPost(Events.UpdatedFriendPost updatedFriendPost) {
        updateGroups(true);
    }

    private void updateGroups(boolean requery) {
        mGroupAdapter.update(requery);
    }
}
