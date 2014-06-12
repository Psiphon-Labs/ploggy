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
public class ActivityMain extends ActivityPloggyBase implements ListView.OnItemClickListener {

    private static final String LOG_TAG = "Main Activity";

    public static enum ViewType {
        SELF_DETAIL,
        GROUP_LIST,
        FRIEND_LIST,
        CANDIDATE_FRIEND_LIST,
        LOG_ENTRIES,
        GROUP_POSTS,
        FRIEND_DETAIL
    };

    public static final String ACTION_DISPLAY_VIEW = "ca.psiphon.ploggy.action.DISPLAY_VIEW";
    public static final String ACTION_DISPLAY_VIEW_EXTRA_TAG_TYPE = "VIEW_TAG_VIEW_TYPE";
    public static final String ACTION_DISPLAY_VIEW_EXTRA_TAG_ID = "VIEW_TAG_ID";

    public static class ViewTag {
        public final ViewType mType;
        public final String mId;

        private static final String BUNDLE_KEY_VIEW_TYPE = "ViewTag.mType";
        private static final String BUNDLE_KEY_ID = "ViewTag.mId";

        public ViewTag(ViewType type) {
            this(type, null);
        }

        public ViewTag(ViewType type, String id) {
            mType = type;
            mId = id;
        }

        public ViewTag(Bundle bundle) {
            this(ViewType.valueOf(bundle.getString(BUNDLE_KEY_VIEW_TYPE)), bundle.getString(BUNDLE_KEY_ID));
        }

        public void save(Bundle bundle) {
            bundle.putString(BUNDLE_KEY_VIEW_TYPE, mType.name());
            bundle.putString(BUNDLE_KEY_ID, mId);
        }

        @Override
        public String toString() {
            return mType.name() + mId;
        }
    }

    public static Intent makeDisplayViewIntent(Context context, ViewTag viewTag) {
        Intent intent = new Intent(context, ActivityMain.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(ACTION_DISPLAY_VIEW);
        intent.putExtra(ACTION_DISPLAY_VIEW_EXTRA_TAG_TYPE, viewTag.mType.name());
        if (viewTag.mId != null) {
            intent.putExtra(ACTION_DISPLAY_VIEW_EXTRA_TAG_ID, viewTag.mId);
        }
        return intent;
    }

    private static boolean mAnimatedDrawerHint = false;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private int mDrawerState;
    private ListView mDrawerList;
    private NavigationDrawerContent.Adapter mDrawerAdapter;
    private CharSequence mTitle;
    private CharSequence mDrawerTitle;
    private ViewTag mDisplayedViewTag;

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
                public void onDrawerStateChanged(int state) {
                    super.onDrawerStateChanged(state);
                    mDrawerState = state;
                    invalidateOptionsMenu();
                }
                @Override
                public void onDrawerClosed(View view) {
                    super.onDrawerClosed(view);
                    getActionBar().setTitle(mTitle);
                }
                @Override
                public void onDrawerOpened(View drawerView) {
                    super.onDrawerOpened(drawerView);
                    getActionBar().setTitle(mDrawerTitle);
                }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        mDrawerAdapter = new NavigationDrawerContent.Adapter(this);
        mDrawerList.setAdapter(mDrawerAdapter);
        mDrawerList.setOnItemClickListener(this);
        mTitle = mDrawerTitle = getTitle();
        mDrawerState = DrawerLayout.STATE_IDLE;

        // TODO: use the v7 support ActionBar if want to port to Android < 4
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);

        if (savedInstanceState != null) {
            displayView(new ViewTag(savedInstanceState));
        } else {
            // *TODO* persist the starting view in preferences
            displayView(new ViewTag(ViewType.SELF_DETAIL));
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mDisplayedViewTag != null) {
            mDisplayedViewTag.save(outState);
        }
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
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ActivityGenerateSelf.checkLaunchGenerateSelf(this);

        // Animate the navigation drawer on first run
        if (!mAnimatedDrawerHint) {
            mDrawerLayout.postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            mDrawerLayout.openDrawer(mDrawerList);
                            // TODO: now close the drawer? Is this only a peek?
                        }
                    },
                    1000);
            mAnimatedDrawerHint = true;
        }

        // Don't show the keyboard until edit selected
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        if (getIntent().getAction() != null && getIntent().getAction().equals(ACTION_DISPLAY_VIEW)) {
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                ViewTag viewTag = new ViewTag(
                        ViewType.valueOf(extras.getString(ACTION_DISPLAY_VIEW_EXTRA_TAG_TYPE)),
                        extras.getString(ACTION_DISPLAY_VIEW_EXTRA_TAG_ID));
                displayView(viewTag);
                // *TODO* mDrawerList.setItemChecked(position, true); ...?
            }
        }

        // Update adapter for data changes while not in foreground
        mDrawerAdapter.update();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Navigation drawer click
        if (parent == mDrawerList) {
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
        // *TODO* keep general items?
        boolean hideMenuItems =
                mDrawerLayout.isDrawerOpen(mDrawerList) ||
                mDrawerState != DrawerLayout.STATE_IDLE;
        for (int i = 0; i < menu.size(); i++) {
            menu.getItem(i).setVisible(!hideMenuItems);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {
        case R.id.action_show_self_detail:
            displayView(new ViewTag(ViewType.SELF_DETAIL));
            return true;
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
            displayView(new ViewTag(ViewType.LOG_ENTRIES));
            return true;
        case R.id.action_add_echo_bot:
            Tests.scheduleAddEchoBot();
            return true;
        case R.id.action_settings:
            startActivity(new Intent(this, ActivitySettings.class));
            return true;
        case R.id.action_show_activity_log:
            displayView(new ViewTag(ViewType.LOG_ENTRIES));
            return true;
        case R.id.action_quit:
            stopService(new Intent(this, PloggyService.class));
            finish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private void displayView(ViewTag viewTag) {
        mDisplayedViewTag = viewTag;
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(viewTag.toString());
        if (fragment != null) {
            if (fragment.isVisible()) {
                return;
            }
        } else {
            fragment = makeFragment(viewTag);
        }
        boolean isFirstFragment = (null == fragmentManager.findFragmentById(R.id.content_frame));
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.content_frame, fragment, viewTag.toString());
        if (!isFirstFragment) {
            fragmentTransaction.addToBackStack(null);
        }
        fragmentTransaction.commit();

        // *TODO* have Fragment set the parent's title -- e.g., once Friend/Group loaded
        //setTitle(title);
    }

    // Create and cache the specified fragment type
    private Fragment makeFragment(ViewTag viewTag) {
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
        case GROUP_POSTS:
            fragment = FragmentGroupPosts.newInstance(viewTag.mId);
            break;
        case FRIEND_DETAIL:
            fragment = FragmentFriendDetail.newInstance(viewTag.mId);
            break;
        default:
            break;
        }
        return fragment;
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

    @Subscribe
    public void UpdatedFriendPost(Events.UpdatedFriendPost updatedFriendPost) {
        mDrawerAdapter.update();
    }

    @Subscribe
    public void markedAsReadPosts(Events.MarkedAsReadPosts markedAsReadPosts) {
        mDrawerAdapter.update();
    }
}
