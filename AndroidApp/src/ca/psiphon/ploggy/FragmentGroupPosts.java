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

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.squareup.otto.Subscribe;

/**
 * User interface which displays group posts.
 */
public class FragmentGroupPosts extends Fragment {

    private static final String LOG_TAG = "Group Posts";

    private String mGroupId;
    private Fragment mFragmentComposePost;
    private ListView mPostList;
    private Adapters.PostAdapter mPostAdapter;
    Utils.FixedDelayExecutor mRefreshUIExecutor;

    private static final String ARGUMENT_GROUP_ID = "GROUP_ID";

    public static FragmentGroupPosts newInstance(String groupId) {
        FragmentGroupPosts fragment = new FragmentGroupPosts();
        Bundle arguments = new Bundle();
        arguments.putString(ARGUMENT_GROUP_ID, groupId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.group_posts, container, false);

        Bundle arguments = getArguments();
        if (arguments != null && arguments.containsKey(ARGUMENT_GROUP_ID)) {
            mGroupId = arguments.getString(ARGUMENT_GROUP_ID);
        } else {
            throw new RuntimeException("missing expected groupId in FragmentGroupDetail");
        }

        mPostAdapter = new Adapters.PostAdapter(
                getActivity(),
                new Adapters.CursorFactory<Data.Post>() {
                    @Override
                    public Data.ObjectCursor<Data.Post> makeCursor() throws PloggyError {
                        return Data.getInstance().getPosts(mGroupId);
                    }
                });

        mPostList = (ListView)view.findViewById(R.id.post_list);
        mPostList.setAdapter(mPostAdapter);

        boolean canPost = true;
        boolean canEdit = true;
        try {
            Data.Group group = Data.getInstance().getGroupOrThrow(mGroupId);
            canPost = (group.mState == Data.Group.State.PUBLISHING || group.mState == Data.Group.State.SUBSCRIBING);
            canEdit = (group.mState == Data.Group.State.PUBLISHING);
        } catch (PloggyError e) {
            Log.addEntry(LOG_TAG, "failed to check group state");
        }

        // *TODO* recheck canPost/canEdit in UpdatedSelfGroup/UpdatedFriendGroup

        if (canPost) {
            mFragmentComposePost = FragmentComposePost.newInstance(mGroupId);
            FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
            transaction.add(R.id.fragment_compose_post, mFragmentComposePost).commit();
        }

        if (canEdit) {
            setHasOptionsMenu(true);
        }

        // Refresh the message list every 5 seconds. This updates download state and "time ago" displays.
        // TODO: event driven redrawing?
        mRefreshUIExecutor = new Utils.FixedDelayExecutor(
                new Runnable() {@Override public void run() {updatePosts(false);}},
                TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS));

        return view;
    }

    @Override
    public void onDestroyView() {
        if (mFragmentComposePost != null) {
            FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
            transaction.remove(mFragmentComposePost).commitAllowingStateLoss();
        }
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        Events.getInstance().register(this);
        mRefreshUIExecutor.start();
        updateTitle();
        try {
            Data.getInstance().markAsReadPosts(mGroupId);
        } catch (PloggyError e) {
            Log.addEntry(LOG_TAG, "failed to mark as read posts");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Events.getInstance().unregister(this);
        mRefreshUIExecutor.stop();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.group_posts_actions, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_edit_group) {
            ActivityEditGroup.startEditGroup(getActivity(), mGroupId);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Note: require explicit result routing for nested fragment
        if (mFragmentComposePost != null) {
            mFragmentComposePost.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Subscribe
    public void UpdatedSelfGroup(Events.UpdatedSelfGroup updatedSelfGroup) {
        if (updatedSelfGroup.mGroupId.equals(mGroupId)) {
            updateTitle();
        }
    }

    @Subscribe
    public void UpdatedFriendGroup(Events.UpdatedFriendGroup updatedFriendGroup) {
        if (updatedFriendGroup.mGroupId.equals(mGroupId)) {
            updateTitle();
        }
    }

    @Subscribe
    public void UpdatedSelfPost(Events.UpdatedSelfPost updatedSelfPost) {
        if (updatedSelfPost.mGroupId.equals(mGroupId)) {
            updatePosts(true);
        }
    }

    @Subscribe
    public void UpdatedFriendPost(Events.UpdatedFriendPost updatedFriendPost) {
        if (updatedFriendPost.mGroupId.equals(mGroupId)) {
            updatePosts(true);
        }
    }

    private void updateTitle() {
        try {
            Data.Group group = Data.getInstance().getGroupOrThrow(mGroupId);
            getActivity().setTitle(group.mGroup.mName);
        } catch (PloggyError e) {
            Log.addEntry(LOG_TAG, "failed to set group name");
        }
    }

    private void updatePosts(boolean requery) {
        mPostAdapter.update(requery);
    }
}
