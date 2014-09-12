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
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;

import com.squareup.otto.Subscribe;

/**
 * User interface which displays a list of groups.
 */
public class FragmentGroupList extends ListFragment
implements AdapterView.OnItemLongClickListener, AbsListView.MultiChoiceModeListener {

    private static final String LOG_TAG = "Group List";

    private Adapters.GroupAdapter mGroupAdapter;
    private Utils.FixedDelayExecutor mRefreshUIExecutor;
    private ActionMode mActionMode;

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
                new Runnable() {@Override public void run() {updateGroups(false);}},
                TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS));

        setHasOptionsMenu(true);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (mGroupAdapter != null) {
            setListAdapter(mGroupAdapter);
        }
        ListView listView = getListView();
        listView.setLongClickable(true);
        listView.setOnItemLongClickListener(this);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setMultiChoiceModeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        Events.getInstance().register(this);
        mRefreshUIExecutor.start();
        getActivity().setTitle(R.string.navigation_drawer_item_group_list);

        // Update adapter for data changes while not in foreground
        mGroupAdapter.update(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        Events.getInstance().unregister(this);
        mRefreshUIExecutor.stop();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.group_list_actions, menu);
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
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (mActionMode == null) {
            mActionMode = getActivity().startActionMode(this);
            return true;
        }
        return false;
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
        mode.setSubtitle(
                getResources().getQuantityString(
                        R.plurals.select_groups_subtitle,
                        getListView().getCheckedItemCount(),
                        getListView().getCheckedItemCount()));
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.group_list_context, menu);
        mode.setTitle(R.string.select_groups);
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (item.getItemId() == R.id.action_group_list_delete_group) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            promptDeleteGroup((Data.Group)getListView().getItemAtPosition(info.position));
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

    private void promptDeleteGroup(Data.Group group) {
        // TODO: undo vs. confirmation prompt
        final Data.Group finalGroup = group;
        new AlertDialog.Builder(getActivity())
            .setTitle(getString(R.string.label_delete_group_title))
            .setMessage(getString(R.string.label_delete_group_message, finalGroup.mGroup.mName))
            .setPositiveButton(getString(R.string.label_delete_group_positive),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                Data.getInstance().removeGroup(finalGroup.mGroup.mId);
                            } catch (PloggyError e) {
                                Log.addEntry(LOG_TAG, "failed to delete group: " + finalGroup.mGroup.mName);
                            }
                        }
                    })
            .setNegativeButton(getString(R.string.label_delete_friend_negative), null)
            .show();
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

    @Subscribe
    public void markedAsReadPosts(Events.MarkedAsReadPosts markedAsReadPosts) {
        updateGroups(true);
    }

    private void updateGroups(boolean requery) {
        mGroupAdapter.update(requery);
    }
}
