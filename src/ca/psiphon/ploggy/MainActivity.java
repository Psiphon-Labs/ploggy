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

import java.util.ArrayList;

import android.os.Bundle;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getActionBar();
        // TODO: http://developer.android.com/guide/topics/ui/actionbar.html#Home
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

    // from: http://developer.android.com/guide/topics/ui/actionbar.html#Tabs

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
 
    @Override
    protected void onResume() {
        super.onResume();
        Events.bus.register(this);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Events.bus.unregister(this);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	// TODO: ...
        //getMenuInflater().inflate(R.menu.main, menu);
        //return true;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add_friend:
                return true;
            case R.id.action_settings:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    public static class FriendListFragment extends ListFragment {
    	@Override
		public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // TODO: temporary
            ArrayList<Data.Friend> dummyFriends = new ArrayList<Data.Friend>();
            dummyFriends.add(new Data.Friend("Nickname1", new TransportSecurity.Certificate("", "Certificate1"), new HiddenService.Identity("", "Hostname1")));
            dummyFriends.add(new Data.Friend("Nickname2", new TransportSecurity.Certificate("", "Certificate2"), new HiddenService.Identity("", "Hostname2")));
            dummyFriends.add(new Data.Friend("Nickname3", new TransportSecurity.Certificate("", "Certificate3"), new HiddenService.Identity("", "Hostname3")));

    		setListAdapter(new FriendAdapter(getActivity(), R.layout.friend_list_row, dummyFriends));
    	}
    }

    private static class FriendAdapter extends ArrayAdapter<Data.Friend> {
    	private ArrayList<Data.Friend> mFriends;

    	public FriendAdapter(Context context, int textViewResourceId, ArrayList<Data.Friend> friends) {
    		super(context, textViewResourceId, friends);
    		mFriends = friends;
    	}

    	@Override
    	public View getView(int position, View view, ViewGroup parent) {
    		if (view == null) {
    			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    			view = inflater.inflate(R.layout.friend_list_row, null);
    		}
    		Data.Friend friend = mFriends.get(position);
    		if (friend != null) {
    			ImageView avatarImage = (ImageView)view.findViewById(R.id.avatar_image);
    			TextView nicknameText = (TextView)view.findViewById(R.id.nickname_text);
    			TextView streetAddressText = (TextView)view.findViewById(R.id.street_address_text);
    			TextView timestampText = (TextView)view.findViewById(R.id.timestamp_text);
    			
    			// TODO: friend.mAvatar
    			avatarImage.setImageResource(R.drawable.ic_unknown_avatar);
    			nicknameText.setText(friend.mNickname);

    			// TODO: load status
    			streetAddressText.setText("123 Streetname St.\nCity\nState\nCountry");
    			timestampText.setText("2013-09-13 22:52:00");
    		}    		
    		return view;
    	}
    }

    public static class LogFragment extends ListFragment {
    }
}
