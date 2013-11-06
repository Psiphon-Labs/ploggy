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

import java.text.DateFormat;
import java.util.ArrayList;

import com.squareup.otto.Subscribe;

import android.os.Bundle;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * The main UI screen.
 * 
 * This activity displays a list of friends, along with a summary of their status.
 * Users can tab between the friend list and a list of event logs. The Action Bar
 * menu is populated with the main app actions.
 * This class subscribes to friend and status events to update displayed data
 * while in the foreground.
 */
public class ActivityMain extends Activity {

    private static final String LOG_TAG = "Main Activity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // TODO: http://developer.android.com/reference/android/support/v4/view/ViewPager.html instead?
        actionBar.addTab(
                actionBar.newTab()
                    .setText(R.string.title_friend_list_fragment)
                    .setTabListener(new TabListener<FriendListFragment>(this, "friend_list_fragment", FriendListFragment.class)));
        
        actionBar.addTab(
                actionBar.newTab()
                    .setText(R.string.title_log_fragment)
                    .setTabListener(new TabListener<LogFragment>(this, "log_fragment", LogFragment.class)));
                
        if (savedInstanceState != null) {
            actionBar.setSelectedNavigationItem(savedInstanceState.getInt("currentTab", 0));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentTab", getActionBar().getSelectedNavigationIndex());
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        ActivityGenerateSelf.checkLaunchGenerateSelf(this);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_self_location_details:
            startActivity(new Intent(this, ActivityLocationDetails.class));
            return true;
        case R.id.action_add_friend:
            startActivity(new Intent(this, ActivityAddFriendByNfc.class));
            return true;
        case R.id.action_generate_self:
            startActivity(new Intent(this, ActivityGenerateSelf.class));
            return true;
        case R.id.action_email_self:
            ActivityAddFriendByEmail.composeEmail(this);
            return true;
        case R.id.action_settings:
            startActivity(new Intent(this, ActivitySettings.class));
            return true;
        case R.id.action_run_tests:
            // TODO: temporary
            Tests.scheduleComponentTests();
            getActionBar().setSelectedNavigationItem(1);
            return true;
        case R.id.action_email_log:
            // TODO: temporary
            Log.composeEmail(this);
            return true;
        case R.id.action_quit:
            stopService(new Intent(this, PloggyService.class));
            finish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    // Adapted from: http://developer.android.com/guide/topics/ui/actionbar.html#Tabs
    private static class TabListener<T extends Fragment> implements ActionBar.TabListener {
        private Fragment mFragment;
        private final Activity mActivity;
        private final String mTag;
        private final Class<T> mClass;

        public TabListener(Activity activity, String tag, Class<T> clz) {
            mActivity = activity;
            mTag = tag;
            mClass = clz;
        }

        public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
            if (mFragment == null) {
                mFragment = Fragment.instantiate(mActivity, mClass.getName());
                ft.add(android.R.id.content, mFragment, mTag);
            } else {
                ft.attach(mFragment);
            }
        }

        public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
            if (mFragment != null) {
                ft.detach(mFragment);
            }
        }

        public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
        }
    }

    public static class FriendListFragment extends ListFragment {
        private FriendAdapter mFriendAdapter;
        
        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            try {
                mFriendAdapter = new FriendAdapter(getActivity());
                setListAdapter(mFriendAdapter);
            } catch (Utils.ApplicationError e) {
                // TODO: log, or flip to log tab, or display toast?
            }
            registerForContextMenu(this.getListView());            
            Events.register(this);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            Events.unregister(this);
        }
        
        @Override
        public void onListItemClick(ListView listView, View view, int position, long id) {
            Data.Friend friend = (Data.Friend)listView.getItemAtPosition(position);
            Intent intent = new Intent(getActivity(), ActivityLocationDetails.class);
            Bundle bundle = new Bundle();
            bundle.putString(ActivityLocationDetails.FRIEND_ID_BUNDLE_KEY, friend.mId);
            intent.putExtras(bundle);
            startActivity(intent);
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
            super.onCreateContextMenu(menu, view, menuInfo);
            MenuInflater inflater = this.getActivity().getMenuInflater();
            inflater.inflate(R.menu.friend_list_context, menu);
        }

        @Override
        public boolean onContextItemSelected(MenuItem item) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            final Data.Friend finalFriend = (Data.Friend)getListView().getItemAtPosition(info.position);
            switch (item.getItemId()) {
                case R.id.action_delete_friend:
                    new AlertDialog.Builder(getActivity())
                        .setTitle(getString(R.string.label_delete_friend_title))
                        .setMessage(getString(R.string.label_delete_friend_message, finalFriend.mPublicIdentity.mNickname))
                        .setPositiveButton(getString(R.string.label_delete_friend_positive),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            Data.getInstance().removeFriend(finalFriend.mId);
                                        } catch (Data.DataNotFoundError e) {
                                            // Ignore
                                        } catch (Utils.ApplicationError e) {
                                            Log.addEntry(LOG_TAG, "failed to delete: " + finalFriend.mPublicIdentity.mNickname);
                                        }
                                    }
                                })
                        .setNegativeButton(getString(R.string.label_delete_friend_negative), null)
                        .show();
                    return true;
                default:
                    return super.onContextItemSelected(item);
            }
        }

        @Subscribe
        public void onAddedFriend(Events.AddedFriend addedFriend) {
            try {
                mFriendAdapter.updateFriends();
            } catch (Utils.ApplicationError e) {
                // TODO: log, or flip to log tab, or display toast?
            }
        }        

        @Subscribe
        public void onUpdatedFriend(Events.UpdatedFriend updatedFriend) {
            try {
                mFriendAdapter.updateFriends();
            } catch (Utils.ApplicationError e) {
                // TODO: log, or flip to log tab, or display toast?
            }
        }        

        @Subscribe
        public void onUpdatedFriendStatus(Events.UpdatedFriendStatus updatedFriendStatus) {
            try {
                mFriendAdapter.updateFriends();
            } catch (Utils.ApplicationError e) {
                // TODO: log, or flip to log tab, or display toast?
            }
        }       

        @Subscribe
        public void onDeletedFriend(Events.RemovedFriend removedFriend) {
            try {
                mFriendAdapter.updateFriends();
            } catch (Utils.ApplicationError e) {
                // TODO: log, or flip to log tab, or display toast?
            }
        }
    }

    private static class FriendAdapter extends BaseAdapter {
        private Context mContext;
        private ArrayList<Data.Friend> mFriends;

        public FriendAdapter(Context context) throws Utils.ApplicationError {
            mContext = context;
            mFriends = Data.getInstance().getFriends();
        }
        
        public void updateFriends() throws Utils.ApplicationError {
            mFriends = Data.getInstance().getFriends();
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.friend_list_row, null);
            }
            Data.Friend friend = mFriends.get(position);
            if (friend != null) {
                ImageView avatarImage = (ImageView)view.findViewById(R.id.friend_list_avatar_image);
                TextView nicknameText = (TextView)view.findViewById(R.id.friend_list_nickname_text);
                TextView timestampText = (TextView)view.findViewById(R.id.friend_list_timestamp_text);
                TextView streetAddressText = (TextView)view.findViewById(R.id.friend_list_street_address_text);
                TextView distanceText = (TextView)view.findViewById(R.id.friend_list_distance_text);
                
                Robohash.setRobohashImage(mContext, avatarImage, true, friend.mPublicIdentity);
                nicknameText.setText(friend.mPublicIdentity.mNickname);
                try {
                    Data data = Data.getInstance();
                    Data.Status selfStatus = null;
                    try {
                        selfStatus = data.getSelfStatus();
                    } catch (Data.DataNotFoundError e) {
                        // Won't be able to compute distance
                    }
                    Data.Status friendStatus = data.getFriendStatus(friend.mId);
                    timestampText.setText(Utils.formatSameDayTime(friendStatus.mTimestamp));
                    if (friendStatus.mStreetAddress.length() > 0) {
                        streetAddressText.setText(friendStatus.mStreetAddress);                        
                    } else {
                        streetAddressText.setText(R.string.prompt_no_street_address_reported);
                    }
                    if (selfStatus != null) {
                        int distance = Utils.calculateLocationDistanceInMeters(
                                selfStatus.mLongitude,
                                selfStatus.mLatitude,
                                friendStatus.mLongitude,
                                friendStatus.mLatitude);
                        distanceText.setText(
                                mContext.getString(R.string.format_friend_list_distance, distance));
                    } else {
                        distanceText.setText(R.string.prompt_unknown_distance);
                    }
                } catch (Data.DataNotFoundError e) {
                    timestampText.setText(R.string.prompt_no_location_updates_received);
                    streetAddressText.setText("");
                    distanceText.setText("");
                } catch (Utils.ApplicationError e) {
                    Log.addEntry(LOG_TAG, "failed to display friend");
                    timestampText.setText("");
                    streetAddressText.setText("");
                    distanceText.setText("");
                }
            }            
            return view;
        }

        @Override
        public int getCount() {
            return mFriends.size();
        }

        @Override
        public Object getItem(int position) {
            return mFriends.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }

    public static class LogFragment extends ListFragment implements Log.Observer {
        private LogAdapter mLogAdapter;
        
        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            mLogAdapter = new LogAdapter(getActivity());
            setListAdapter(mLogAdapter);
        }

        @Override
        public void onStart() {
            super.onStart();
            getListView().setSelection(mLogAdapter.getCount() - 1);
            getListView().setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
            Log.registerObserver(this);
        }

        @Override
        public void onStop() {
            super.onStop();
            Log.unregisterObserver(this);
        }

        @Override
        public void onUpdatedRecentEntries() {
            mLogAdapter.notifyDataSetChanged();
            getListView().setSelection(mLogAdapter.getCount() - 1);
        }        
    }

    private static class LogAdapter extends BaseAdapter {
        private Context mContext;
        private DateFormat mDateFormat;

        public LogAdapter(Context context) {
            mContext = context;
            mDateFormat = DateFormat.getDateTimeInstance();
        }
        
        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.log_row, null);
            }
            Log.Entry entry = Log.getRecentEntry(position);
            if (entry != null) {
                TextView timestampText = (TextView)view.findViewById(R.id.log_timestamp_text);
                TextView tagText = (TextView)view.findViewById(R.id.log_tag_text);
                TextView messageText = (TextView)view.findViewById(R.id.log_message_text);

                timestampText.setText(mDateFormat.format(entry.mTimestamp));
                tagText.setText(entry.mTag);
                messageText.setText(entry.mMessage);
            }            
            return view;
        }

        @Override
        public int getCount() {
            return Log.getRecentEntryCount();
        }

        @Override
        public Object getItem(int position) {
            return Log.getRecentEntry(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }
}
