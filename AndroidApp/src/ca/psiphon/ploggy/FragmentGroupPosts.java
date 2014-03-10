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
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.squareup.otto.Subscribe;

/**
 * User interface which displays group posts.
 */
public class FragmentGroupPosts extends Fragment {

    private static final String LOG_TAG = "Group Detail";

    private boolean mIsResumed = false;
    private String mGroupId;
    private Fragment mFragmentComposePost;
    private ListView mPostListView;
    private Adapters.PostAdapter mPostAdapter;
    Utils.FixedDelayExecutor mRefreshUIExecutor;

    private static final String ARGUMENT_GROUP_ID = "groupId";

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

        mFragmentComposePost = FragmentComposePost.newInstance(mGroupId);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.add(R.id.fragment_compose_post, mFragmentComposePost).commit();

        mPostAdapter = new Adapters.PostAdapter(
                getActivity(),
                new Adapters.CursorFactory<Data.Post>() {
                    @Override
                    public Data.ObjectCursor<Data.Post> makeCursor() throws PloggyError {
                        return Data.getInstance().getPosts(mGroupId);
                    }
                });

        mPostListView = (ListView)view.findViewById(R.id.post_list);
        mPostListView.setAdapter(mPostAdapter);

        // Refresh the message list every 5 seconds. This updates download state and "time ago" displays.
        // TODO: event driven redrawing?
        mRefreshUIExecutor = new Utils.FixedDelayExecutor(
                new Runnable() {@Override public void run() {updatePosts(false);}}, 5000);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Events.getInstance().register(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsResumed = true;
        mRefreshUIExecutor.start();
        // *TODO* mark as read messages? [was: Events.post(new Events.DisplayedMessages());]
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
        transaction.remove(mFragmentComposePost).commitAllowingStateLoss();
        Events.getInstance().unregister(this);
        super.onDestroyView();
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

    private void updatePosts(boolean requery) {
        mPostAdapter.update(requery);
    }
}
