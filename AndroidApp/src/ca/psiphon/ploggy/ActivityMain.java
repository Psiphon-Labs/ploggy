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
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
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
public class ActivityMain extends ActivitySendIdentityByNfc implements ActionBar.TabListener {

    private static final String LOG_TAG = "Main Activity";

    public static final String ACTION_DISPLAY_MESSAGES = "ca.psiphon.ploggy.action.DISPLAY_MESSAGES";
    
    private int mMessageListTabIndex;    
    private ViewPager mViewPager;
    private AppTabsPagerAdapter mAppTabsPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create the adapter that will return a fragment for each of the three primary sections
        // of the app.
        mAppTabsPagerAdapter = new AppTabsPagerAdapter(getSupportFragmentManager());

        final ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Specify that the Home/Up button should not be enabled, since there is
        // no hierarchical parent.
        actionBar.setHomeButtonEnabled(false);

        // Set up the ViewPager, attaching the adapter and setting up a listener for when the
        // user swipes between sections.
        mViewPager = (ViewPager)findViewById(R.id.pager);
        mViewPager.setAdapter(mAppTabsPagerAdapter);
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                // When swiping between different app sections, select the corresponding tab.
                // We can also use ActionBar.Tab#select() to do this if we have a reference to the
                // Tab.
                actionBar.setSelectedNavigationItem(position);
            }
        });

        actionBar.addTab(
                actionBar.newTab()
                    .setText(R.string.title_self_status_fragment)
                    .setTabListener(this));
                    
        actionBar.addTab(
                actionBar.newTab()
                    .setText(R.string.title_friend_list_fragment)
                    .setTabListener(this));

        actionBar.addTab(
                actionBar.newTab()
                    .setText(R.string.title_message_list_fragment)
                    .setTabListener(this));
        mMessageListTabIndex = 2;

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
                getIntent().getAction().equals(ACTION_DISPLAY_MESSAGES)) {
            getActionBar().setSelectedNavigationItem(mMessageListTabIndex);
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
            ExportIdentity.composeEmail(this);
            return true;
        case R.id.action_activity_log:
            startActivity(new Intent(this, ActivityLogEntries.class));
            return true;
        case R.id.action_save_identity_to_file:
            ExportIdentity.saveIdentityToFile(this);
            return true;
        case R.id.action_run_tests:
            Tests.scheduleComponentTests();
            startActivity(new Intent(this, ActivityLogEntries.class));
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

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in the ViewPager.
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    /**
     * Returns a fragment corresponding to one of the primary sections of the app.
     */
    public static class AppTabsPagerAdapter extends FragmentPagerAdapter {

        public AppTabsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            switch (i) {
                case 0:
                    return new FragmentSelfStatusDetails();

                case 1:
                    return new FragmentFriendList();

                case 2:
                    return new FragmentMessageList();
            }

            assert(false);
            return null;
        }

        @Override
        public int getCount() {
            return 3;
        }
    }
}
