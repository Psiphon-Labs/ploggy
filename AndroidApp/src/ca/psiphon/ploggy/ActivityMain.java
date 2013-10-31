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
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
            ActivityAddFriendByEmail.sendAttachment(this);
            return true;
        case R.id.action_settings:
            startActivity(new Intent(this, ActivitySettings.class));
            return true;
        case R.id.action_run_tests:
            // TODO: temporary
            Tests.scheduleComponentTests();
            getActionBar().setSelectedNavigationItem(1);
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
		public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            try {
				mFriendAdapter = new FriendAdapter(getActivity());
	    		setListAdapter(mFriendAdapter);
			} catch (Utils.ApplicationError e) {
				// TODO: log, or flip to log tab, or display toast?
			}

            Events.register(this);
    	}

        @Override
        public void onDestroy() {
            super.onDestroy();
            Events.unregister(this);
        }
        
        @Override
        public void onListItemClick (ListView listView, View view, int position, long id) {
            Data.Friend friend = (Data.Friend)listView.getItemAtPosition(position);
            Intent intent = new Intent(getActivity(), ActivityLocationDetails.class);
            Bundle bundle = new Bundle();
            bundle.putString(ActivityLocationDetails.FRIEND_ID_BUNDLE_KEY, friend.mId);
            intent.putExtras(bundle);
            startActivity(intent);
        }

        @Subscribe
        public void onAddedFriend(Events.UpdatedFriend updatedFriend) {
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

        @Subscribe
        public void onNewFriendStatus(Events.UpdatedFriendStatus updatedFriendStatus) {
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
    			TextView streetAddressText = (TextView)view.findViewById(R.id.friend_list_street_address_text);
    			TextView timestampText = (TextView)view.findViewById(R.id.friend_list_timestamp_text);
    			
    			Robohash.setRobohashImage(mContext, avatarImage, true, friend.mPublicIdentity);
    			nicknameText.setText(friend.mPublicIdentity.mNickname);
    			try {
        			Data.Status status = Data.getInstance().getFriendStatus(friend.mId);
        			// TODO: also show distance, last received timestamp, etc.
        			streetAddressText.setText(status.mStreetAddress);
        			timestampText.setText(status.mTimestamp);
    			} catch (Utils.ApplicationError e) {
    			    // TODO: treat DataNotFoundException differently?
                    streetAddressText.setText("");
                    timestampText.setText("");
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

    public static class LogFragment extends ListFragment {
    	private LogAdapter mLogAdapter;
    	
    	@Override
		public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            
            // TODO: use endless list (e.g., https://github.com/commonsguy/cwac-endless) and populate on scroll
            mLogAdapter = new LogAdapter(getActivity(), Log.readEntries());
    		setListAdapter(mLogAdapter);

    		Events.register(this);
    	}

        @Override
        public void onDestroy() {
            super.onDestroy();
            Events.unregister(this);
        }

        @Override
        public void onStart() {
            super.onStart();
            // TODO: ensure last entry visible
            getListView().setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
            mLogAdapter.registerDataSetObserver(
                    new DataSetObserver() {
                        @Override
                        public void onChanged() {
                            super.onChanged();
                            getListView().setSelection(mLogAdapter.getCount() - 1);
                        }
                    });
        }

        @Subscribe
        public void onLoggedEntry(Events.LoggedEntry loggedEntry) {
            mLogAdapter.addEntry(loggedEntry.mEntry);
        }    	
    }

    private static class LogAdapter extends BaseAdapter {
    	private Context mContext;
    	private ArrayList<Log.Entry> mEntries;
    	private DateFormat mDateFormat;

    	public LogAdapter(Context context, ArrayList<Log.Entry> entries) {
    		mContext = context;
    		mEntries = entries;
    		mDateFormat = DateFormat.getDateTimeInstance();
    	}
    	
    	public void addEntry(Log.Entry entry) {
    	    mEntries.add(entry);
    	    notifyDataSetChanged();
    	}

    	@Override
    	public View getView(int position, View view, ViewGroup parent) {
    		if (view == null) {
    			LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    			view = inflater.inflate(R.layout.log_row, null);
    		}
    		Log.Entry entry = mEntries.get(position);
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
			return mEntries.size();
		}

		@Override
		public Object getItem(int position) {
			return mEntries.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}
    }
}
