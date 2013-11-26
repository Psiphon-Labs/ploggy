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

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;

/**
 * The main UI screen.
 *
 * This activity displays a list of friends, along with a summary of their status.
 * Users can tab between the friend list and a list of event logs. The Action Bar
 * menu is populated with the main app actions.
 * This class subscribes to friend and status events to update displayed data
 * while in the foreground.
 */
public class ActivityMain extends ActivitySendIdentityByNfc {

    private static final String LOG_TAG = "Main Activity";

    public static final String ACTION_DISPLAY_FRIENDS = "ca.psiphon.ploggy.action.DISPLAY_FRIENDS";
    
    private int mFriendTabIndex;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // TODO: http://developer.android.com/reference/android/support/v4/view/ViewPager.html instead?
        actionBar.addTab(
                actionBar.newTab()
                    .setText(R.string.title_your_status_fragment)
                    .setTabListener(
                            new TabListener<FragmentSelfStatusDetails>(
                                    this,
                                    "fragment_self_status_details",
                                    FragmentSelfStatusDetails.class)));
        actionBar.addTab(
                actionBar.newTab()
                    .setText(R.string.title_friend_list_fragment)
                    .setTabListener(
                            new TabListener<FragmentFriendList>(
                                    this,
                                    "fragment_friend_list",
                                    FragmentFriendList.class)));
        mFriendTabIndex = 1;

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

        // Don't show the keyboard until edit selected
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        if (getIntent().getAction() != null &&
                getIntent().getAction().equals(ACTION_DISPLAY_FRIENDS)) {
            getActionBar().setSelectedNavigationItem(mFriendTabIndex);
        }
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
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
        case R.id.action_generate_self:
            startActivity(new Intent(this, ActivityGenerateSelf.class));
            return true;
        case R.id.action_email_self:
            SendIdentityByEmail.composeEmail(this);
            return true;
        case R.id.action_activity_log:
            startActivity(new Intent(this, ActivityLogEntries.class));
            return true;
        case R.id.action_run_tests:
            Tests.scheduleComponentTests();
            getActionBar().setSelectedNavigationItem(1);
            return true;
        case R.id.action_settings:
            startActivity(new Intent(this, ActivitySettings.class));
            return true;
        case R.id.action_quit:
            stopService(new Intent(this, PloggyService.class));
            finish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    // Adapted from: http://developer.android.com/reference/android/app/ActionBar.html#newTab%28%29
    private static class TabListener<T extends Fragment> implements ActionBar.TabListener {
        private Fragment mFragment;
        private final Activity mActivity;
        private final String mTag;
        private final Class<T> mClass;
        private final Bundle mArgs;


        public TabListener(Activity activity, String tag, Class<T> clz) {
            this(activity, tag, clz, null);

        }

        public TabListener(Activity activity, String tag, Class<T> clz, Bundle args) {
            mActivity = activity;
            mTag = tag;
            mClass = clz;
            mArgs = args;

            // Check to see if we already have a fragment for this tab, probably
            // from a previously saved state.  If so, deactivate it, because our
            // initial state is that a tab isn't shown.
            mFragment = mActivity.getFragmentManager().findFragmentByTag(mTag);
            if (mFragment != null && !mFragment.isDetached()) {
                FragmentTransaction ft = mActivity.getFragmentManager().beginTransaction();
                ft.detach(mFragment);
                ft.commit();
            }
        }

        @Override
        public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
            if (mFragment == null) {
                mFragment = Fragment.instantiate(mActivity, mClass.getName(), mArgs);
                ft.add(android.R.id.content, mFragment, mTag);
            } else {
                ft.attach(mFragment);
            }
        }

        @Override
        public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
            if (mFragment != null) {
                ft.detach(mFragment);
            }
        }

        @Override
        public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
        }
    }
}
