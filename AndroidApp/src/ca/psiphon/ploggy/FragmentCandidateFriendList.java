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

import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.squareup.otto.Subscribe;

/**
 * User interface which displays a list of friends.

 * This class subscribes to friend and status events to update displayed data
 * while in the foreground.
 */
public class FragmentCandidateFriendList extends ListFragment {

    private static final String LOG_TAG = "Candidate Friend List";

    private Adapters.CandidateFriendAdapter mCandidateFriendAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        mCandidateFriendAdapter = new Adapters.CandidateFriendAdapter(
                getActivity(),
                new Adapters.CursorFactory<Data.CandidateFriend>() {
                    @Override
                    public Data.ObjectCursor<Data.CandidateFriend> makeCursor() throws PloggyError {
                        return Data.getInstance().getCandidateFriends();
                    }
                });

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (mCandidateFriendAdapter != null) {
            setListAdapter(mCandidateFriendAdapter);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Events.getInstance().register(this);
        getActivity().setTitle(R.string.navigation_drawer_item_candidate_friend_list);

        // Update adapter for data changes while not in foreground
        mCandidateFriendAdapter.update(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        Events.getInstance().unregister(this);
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        Data.CandidateFriend candidateFriend = (Data.CandidateFriend)listView.getItemAtPosition(position);
        startActivity(ActivityAddFriend.makeAddFriendIntent(
                getActivity(), candidateFriend.mPublicIdentity));
    }

    @Subscribe
    public void onAddedFriend(Events.AddedFriend addedFriend) {
        updateCandidateFriends(true);
    }

    @Subscribe
    public void onRemovedFriend(Events.RemovedFriend removedFriend) {
        updateCandidateFriends(true);
    }

    @Subscribe
    public void UpdatedFriendGroup(Events.UpdatedFriendGroup updatedFriendGroup) {
        updateCandidateFriends(true);
    }

    private void updateCandidateFriends(boolean requery) {
        mCandidateFriendAdapter.update(requery);
    }
}
