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

import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;

import com.squareup.otto.Subscribe;

/**
 * The main UI screen.
 *
 * This activity displays a list of friends, along with a summary of their status.
 * Users can tab between the friend list and a list of event logs. The Action Bar
 * menu is populated with the main app actions.
 * This class subscribes to friend and status events to update displayed data
 * while in the foreground.
 *
 * Navigation drawer example: https://developer.android.com/training/implementing-navigation/nav-drawer.html
 *
 */
public class ActivityMain extends ActivitySendIdentityByNfc implements ListView.OnItemClickListener {

    private static final String LOG_TAG = "Main Activity";

    public static enum ViewType {
        SELF_DETAIL,
        GROUP_LIST,
        FRIEND_LIST,
        CANDIDATE_FRIEND_LIST,
        LOG_ENTRIES,
        GROUP_DETAIL,
        FRIEND_DETAIL
    };

    public static final String ACTION_DISPLAY_VIEW = "ca.psiphon.ploggy.action.DISPLAY_VIEW";
    public static final String ACTION_DISPLAY_VIEW_EXTRA_TAG_TYPE = "VIEW_TAG_VIEW_TYPE";
    public static final String ACTION_DISPLAY_VIEW_EXTRA_TAG_ID = "VIEW_TAG_ID";

    public static class ViewTag {
        public final ViewType mType;
        public final String mId;

        ViewTag(ViewType type) {
            this(type, null);
        }

        ViewTag(ViewType type, String id) {
            mType = type;
            mId = id;
        }
    }

    public static Intent makeDisplayViewIntent(Context context, ViewTag viewTag) {
        Intent intent = new Intent(context, ActivityMain.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(ACTION_DISPLAY_VIEW);
        intent.putExtra(ACTION_DISPLAY_VIEW_EXTRA_TAG_TYPE, viewTag.mType.toString());
        if (viewTag.mId != null) {
            intent.putExtra(ACTION_DISPLAY_VIEW_EXTRA_TAG_ID, viewTag.mId);
        }
        return intent;
    }

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private ListView mDrawerList;
    private NavigationDrawerContent.Adapter mDrawerAdapter;
    private CharSequence mTitle;
    private CharSequence mDrawerTitle;
    private List<ViewTag> mFragmentBackStack;
    private Map<ViewTag, Fragment> mFragmentCache;

    private static final int FRAGMENT_BACK_STACK_SIZE = 2;
    private static final int FRAGMENT_CACHE_SIZE = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle =
            new ActionBarDrawerToggle(
                    this,
                    mDrawerLayout,
                    R.drawable.ic_navigation_drawer,
                    R.string.navigation_drawer_open,
                    R.string.navigation_drawer_close) {
                @Override
                public void onDrawerClosed(View view) {
                    super.onDrawerClosed(view);
                    getActionBar().setTitle(mTitle);
                    invalidateOptionsMenu();
                }
                @Override
                public void onDrawerOpened(View drawerView) {
                    super.onDrawerOpened(drawerView);
                    getActionBar().setTitle(mDrawerTitle);
                    invalidateOptionsMenu();
                }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        mDrawerAdapter = new NavigationDrawerContent.Adapter(this);
        mDrawerList.setAdapter(mDrawerAdapter);
        mTitle = mDrawerTitle = getTitle();

        // TODO: use the v7 support ActionBar if want to port to Android < 4
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);

        // *TODO* expand navigation drawer on app first run

        // *TODO* restore selected view
        //if (savedInstanceState != null) {
        //    actionBar.setSelectedNavigationItem(savedInstanceState.getInt("currentTab", 0));
        //}

        // *TODO* persist the starting view in preferences
        displayView(...);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        getActionBar().setTitle(mTitle);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // *TODO* persist selected view
        //outState.putInt("currentTab", getActionBar().getSelectedNavigationIndex());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ActivityGenerateSelf.checkLaunchGenerateSelf(this);

        // Don't show the keyboard until edit selected
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        if (getIntent().getAction() != null && getIntent().getAction().equals(ACTION_DISPLAY_VIEW)) {
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                ViewTag viewTag = new ViewTag(
                        ViewType.valueOf(extras.getString(ACTION_DISPLAY_VIEW_EXTRA_TAG_TYPE),
                        extras.getString(ACTION_DISPLAY_VIEW_EXTRA_TAG_ID));
                displayView(viewTag);
                // *TODO* mDrawerList.setItemChecked(position, true); ...?
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Navigation drawer click
        if (view == mDrawerList) {
            NavigationDrawerContent.Row row = mDrawerAdapter.getItem(position);
            if (row != null && row.getRowType() == NavigationDrawerContent.Row.Type.ITEM) {
                NavigationDrawerContent.Item item = (NavigationDrawerContent.Item) row;
                displayView(item.getViewTag());
                mDrawerList.setItemChecked(position, true);
                mDrawerLayout.closeDrawer(mDrawerList);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // *TODO*
        /*
        // If the nav drawer is open, hide action items related to the content view
        boolean drawerOpen = mDrawerLayout.isDrawerOpen(mDrawerList);
        menu.findItem(R.id.action_websearch).setVisible(!drawerOpen);
        */
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {
        case R.id.action_generate_self:
            startActivity(new Intent(this, ActivityGenerateSelf.class));
            return true;
        case R.id.action_email_identity:
            ExportIdentity.composeEmail(this);
            return true;
        case R.id.action_save_identity_to_file:
            ExportIdentity.saveIdentityToFile(this);
            return true;
        case R.id.action_run_tests:
            Tests.scheduleComponentTests();
            displayView(ViewType.LOG_ENTRIES);
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
    public void onBackPressed() {
        // Custom back stack allows for FIFO max depth and uses
        // our fragment cache. The cache supports multiple instances
        // of the same Fragment subclass but for different data.
        // E.g., multiple FragmentGroupPostLists. This is all to allow
        // for quick navigation between group "chat" views while also
        // popping in and out of e.g., Activity Log.

        // TODO: could this be implemented with stock addToBackStack
        // and fragment tags such as "<viewType>"+"<id>"?
        // Here's the main concern:
        // http://developer.android.com/guide/components/fragments.html#Transactions
        // "If you do not call addToBackStack() when you perform a transaction that
        //  removes a fragment, then that fragment is destroyed when the transaction
        //  is committed and the user cannot navigate back to it. Whereas, if you do
        //  call addToBackStack() when removing a fragment, then the fragment is stopped
        //  and will be resumed if the user navigates back."

        // TODO: this ignores at least two Android design guidelines:
        // do not use the back stack for lateral navigation; and populate
        // the back stack synthetically for deep links. Is this acceptable?

        if (mFragmentBackStack.size() <= 1) {
            // *TODO* add one more step, back to open drawer, before back to home screen
            // There's nothing but the oldest fragment, so let the OS
            // handle it -- goes back to the home screen.
            super.onBackPressed();
            return;
        }

        // Pop and detach the top (currently displayed) fragment and attach the
        // next fragment down in the stack. Both fragments remain in the cache.

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        ViewTag currentTag = mFragmentBackStack.remove(mFragmentBackStack.size()-1);
        Fragment currentFragment = mFragmentCache.get(currentTag);
        if (currentFragment == null) {
            throw new RuntimeException("unexpected fragment cache state in onBackPressed");
        }
        if (!currentFragment.isVisible()) {
            throw new RuntimeException("unexpected fragment visible state in onBackPressed");
        }

        fragmentTransaction.detach(currentFragment);

        // Re-attach or add the previous view in the back stack

        ViewTag previousTag = mFragmentBackStack.get(mFragmentBackStack.size()-1);
        Fragment previousFragment = mFragmentCache.get(previousTag);

        if (previousFragment == null) {
            // The previous fragment in the back stack may no longer be in the cache
            previousFragment = makeFragment(previousTag);
            fragmentTransaction.add(R.id.content_frame, previousFragment);
        } else {
            touchFragment(previousTag);
            fragmentTransaction.attach(previousFragment);
        }

        fragmentTransaction.commit();

        // *TODO* mDrawerList.setItemChecked(position, true); ...? + setTitle ?
    }

    private void displayView(ViewTag viewTag) {
        // Do nothing when view is already currently displayed
        if (mFragmentBackStack.size() > 0 &&
                mFragmentBackStack.get(mFragmentBackStack.size()-1).equals(viewTag)) {
            return;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        // Detach current view and leave on the back stack

        ViewTag currentTag = mFragmentBackStack.get(mFragmentBackStack.size()-1);
        Fragment currentFragment = mFragmentCache.get(currentTag);
        if (currentFragment == null) {
            throw new RuntimeException("unexpected fragment cache state in displayView");
        }
        if (!currentFragment.isVisible()) {
            throw new RuntimeException("unexpected fragment visible state in displayView");
        }

        fragmentTransaction.detach(currentFragment);

        // Re-attach or add new view (using cached fragment if present) and push on back stack

        Fragment nextFragment = mFragmentCache.get(viewTag);
        if (nextFragment == null) {
            nextFragment = makeFragment(viewTag);
            fragmentTransaction.add(R.id.content_frame, nextFragment);
        } else {
            touchFragment(viewTag);
            fragmentTransaction.attach(nextFragment);
        }
        mFragmentBackStack.add(viewTag);

        fragmentTransaction.commit();

        // Enforce maximum back stack depth. This leaves popped fragments in
        // the cache since users may soon navigate to them again via the drawer.

        while (mFragmentBackStack.size() > FRAGMENT_BACK_STACK_SIZE) {
            Fragment fragment = mFragmentCache.get(mFragmentBackStack.remove(0));
            if (fragment != null) {
                // TODO: since FragmentTransaction is asynchronous, would this
                // assertion fail when FRAGMENT_BACK_STACK_SIZE is only 1?
                if (!fragment.isDetached()) {
                    throw new RuntimeException("unexpected fragment detatched state in displayView");
                }
            }
        }

        // *TODO* have Fragment set the parent's title -- e.g., once Friend/Group loaded
        //setTitle(title);
    }

    // Create and cache the specified fragment type
    private Fragment makeFragment(ViewTag viewTag) {
        // Assumes caller has already checked the cache and is expecting a new, unattached Fragment
        if (mFragmentCache.get(viewTag) != null) {
            throw new RuntimeException("unexpected fragment cache state in makeFragment");
        }

        Fragment fragment = null;
        switch (viewTag.mType) {
        case SELF_DETAIL:
            fragment = new FragmentSelfDetail();
            break;
        case GROUP_LIST:
            fragment = new FragmentGroupList();
            break;
        case FRIEND_LIST:
            fragment = new FragmentFriendList();
            break;
        case CANDIDATE_FRIEND_LIST:
            fragment = new FragmentCandidateFriendList();
            break;
        case LOG_ENTRIES:
            fragment = new FragmentLogEntries();
            break;
        case GROUP_DETAIL:
            fragment = new FragmentGroupDetail();
            fragment.setArguments(new Bundle().putString(FragmentGroupDetail.ARG_GROUP_ID, viewTag.mId));
            break;
        case FRIEND_DETAIL:
            fragment = new FragmentFriendDetail();
            fragment.setArguments(new Bundle().putString(FragmentFriendDetail.ARG_FRIEND_ID, viewTag.mId));
            break;
        default:
            break;
        }

        // When cache is full, eject the oldest cache entry which isn't attached
        while (mFragmentCache.size() >= FRAGMENT_CACHE_SIZE) {
            Map.Entry<ViewTag, Fragment> oldestEntry = mFragmentCache.entrySet().iterator().next();
            if (oldestEntry.getValue().isVisible()) {
                throw new RuntimeException("unexpected fragment visible state in makeFragment");
            }
            mFragmentCache.remove(oldestEntry.getKey());
        }

        // Add new fragment to the cache
        mFragmentCache.put(viewTag, fragment);

        return fragment;
    }

    // Makes the specified fragment the newest item in the cache
    protected void touchFragment(ViewTag viewTag) {
        Fragment fragment = mFragmentCache.remove(viewTag);
        // Assumes caller has already checked the cache
        if (fragment == null) {
            throw new RuntimeException("unexpected fragment cache state in touchFragment");
        }
        mFragmentCache.put(viewTag, fragment);
    }

    @Subscribe
    public void onAddedFriend(Events.AddedFriend addedFriend) {
        mDrawerAdapter.update();
    }

    @Subscribe
    public void onRemovedFriend(Events.RemovedFriend removedFriend) {
        mDrawerAdapter.update();
    }

    @Subscribe
    public void onUpdatedSelfGroup(Events.UpdatedSelfGroup updatedSelfGroup) {
        mDrawerAdapter.update();
    }

    @Subscribe
    public void onUpdatedFriendGroup(Events.UpdatedFriendGroup updatedFriendGroup) {
        mDrawerAdapter.update();
    }
}
