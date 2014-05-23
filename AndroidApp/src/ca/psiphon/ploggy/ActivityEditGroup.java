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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import com.squareup.otto.Subscribe;

/**
 * User interface for editing groups.
 */
public class ActivityEditGroup extends ActivityPloggyBase
        implements View.OnClickListener, ActionMode.Callback, View.OnLongClickListener {

    private static final String LOG_TAG = "Edit Group";

    public static final String GROUP_ID_BUNDLE_KEY = "groupId";

    private boolean mIsNewGroup;
    private String mGroupId;
    private ImageView mGroupAvatarImage;
    private EditText mNameEdit;
    private ListView mMemberList;
    private Adapters.GroupMemberArrayAdapter mMemberAdapter;
    private ActionMode mActionMode;
    private Button mSaveButton;
    private boolean mIsReadOnly = true;
    private boolean mHasEdits = false;
    private Toast mBackPressedToast;

    private static final String EXTRA_GROUP_ID = "GROUP_ID";

    public static void startEditGroup(Context context, String groupId) {
        Intent intent = new Intent(context, ActivityEditGroup.class);
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_GROUP_ID, groupId);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_group);

        mGroupAvatarImage = (ImageView)findViewById(R.id.edit_group_avatar_image);
        Avatar.setGroupAvatarImage(mGroupAvatarImage);

        mNameEdit = (EditText) findViewById(R.id.edit_group_name_edit);
        mNameEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                setHasEdits(true);
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        mMemberList = (ListView) findViewById(R.id.edit_group_member_list);
        mSaveButton = (Button) findViewById(R.id.edit_group_save_button);
        mSaveButton.setOnClickListener(this);

        Events.getInstance().register(this);
    }

    @Override
    protected void onDestroy() {
        Events.getInstance().unregister(this);
        super.onDestroy();
    }

    @Subscribe
    public void onUpdatedFriendGroup(Events.UpdatedFriendGroup updatedFriendGroup) {
        // When displaying a friend's group, show updates received in the background
        if (mGroupId != null && updatedFriendGroup.mGroupId.equals(mGroupId) && mIsReadOnly) {
            show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mIsNewGroup = true;
        if (getIntent() != null &&
            getIntent().getExtras() != null &&
            getIntent().getExtras().containsKey(EXTRA_GROUP_ID)) {
            mIsNewGroup = false;
            mGroupId = getIntent().getExtras().getString(EXTRA_GROUP_ID);
            if (mGroupId == null) {
                finish();
                return;
            }
        }

        show();

        // Don't show the keyboard until edit selected
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    @Override
    protected void onPause() {
        // Don't leave toast dangling if e.g., Home button pressed
        if (mBackPressedToast != null) {
            View view = mBackPressedToast.getView();
            if (view != null && view.isShown()) {
                    mBackPressedToast.cancel();
            }
        }
        super.onPause();
    }

    private void show() {
        try {
            Data data = Data.getInstance();
            if (mIsNewGroup) {
                mNameEdit.setText("");
                mMemberAdapter = new Adapters.GroupMemberArrayAdapter(this);
                mMemberAdapter.add(data.getSelfOrThrow().mPublicIdentity);
                mMemberList.setAdapter(mMemberAdapter);
                mIsReadOnly = false;
            } else {
                Data.Group group = data.getGroupOrThrow(mGroupId);
                mNameEdit.setText(group.mGroup.mName);
                mMemberAdapter = new Adapters.GroupMemberArrayAdapter(this);
                mMemberAdapter.addAll(group.mGroup.mMembers);
                mMemberAdapter.sort(new Identity.PublicIdentityComparator());
                mMemberList.setAdapter(mMemberAdapter);

                // Disable editing when not self published group
                boolean isSelfPublished = group.mGroup.mPublisherId.equals(data.getSelfId());
                mIsReadOnly = !isSelfPublished;
            }
            showAvatar();
        } catch (PloggyError e) {
            Log.addEntry(LOG_TAG, "failed to show group");
        }
        mNameEdit.setEnabled(!mIsReadOnly);
        setHasEdits(false);
    }

    private void showAvatar() {
        try {
            Avatar.setGroupAvatarImage(this, mGroupAvatarImage, Data.getInstance().getSelfId(), mMemberAdapter);
        } catch (PloggyError e) {
            Log.addEntry(LOG_TAG, "failed to show group avatar");
        }
    }

    private void setHasEdits(boolean hasEdits) {
        mSaveButton.setEnabled(hasEdits);
        mHasEdits = hasEdits;
    }

    @Override
    public void onBackPressed() {
        // When the back button is pressed and edits have been made, invoke a
        // confirmation step before dismissing the activity (and discarding changes)
        if (!mHasEdits) {
            super.onBackPressed();
            return;
        }
        if (mBackPressedToast != null) {
            View view = mBackPressedToast.getView();
            if (view != null && view.isShown()) {
                mBackPressedToast.cancel();
                super.onBackPressed();
                return;
            }
        }
        mBackPressedToast = Toast.makeText(this, R.string.prompt_edit_group_back, Toast.LENGTH_LONG);
        mBackPressedToast.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.edit_group_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getItemId() == R.id.action_add_group_member) {
                item.setVisible(!mIsReadOnly);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_add_group_member:
            doAddGroupMember();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private void doAddGroupMember() {
        String name = mNameEdit.getText().toString();
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setIcon(R.drawable.ic_action_add_group_member);
        alertDialog.setTitle(getString(R.string.prompt_edit_group_add_member, name));
        final Adapters.CandidateGroupMemberAdapter finalCandidateGroupMemberAdapter =
            new Adapters.CandidateGroupMemberAdapter(
                this,
                new Adapters.CursorFactory<Data.Friend>() {
                    @Override
                    public Data.ObjectCursor<Data.Friend> makeCursor() throws PloggyError {
                        // Candidates are all friends except current (edited version) members
                        List<String> memberIds = new ArrayList<String>();
                        for (int i = 0; i < mMemberAdapter.getCount(); i++) {
                            memberIds.add(mMemberAdapter.getItem(i).mId);
                        }
                        return Data.getInstance().getFriendsExcept(memberIds);
                    }
                });
        // *TODO* display Toast instead of dialog when there are no candidate friends
        // (can't check finalCandidateGroupMemberAdapter.getCount() == 0 yet due to AsyncTask)
        alertDialog.setAdapter(
                finalCandidateGroupMemberAdapter,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Data.Friend friend = finalCandidateGroupMemberAdapter.getItem(which);
                        mMemberAdapter.add(friend.mPublicIdentity);
                        mMemberAdapter.sort(new Identity.PublicIdentityComparator());
                        // *TODO* ensure new member is visible
                        mMemberAdapter.notifyDataSetChanged();
                        showAvatar();
                        setHasEdits(true);
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

    @Override
    public boolean onLongClick(View view) {
        if (mActionMode == null) {
            mActionMode = startActionMode(this);
            view.setSelected(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        if (!mIsReadOnly) {
            mode.getMenuInflater().inflate(R.menu.edit_group_context, menu);
        }
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (item.getItemId() == R.id.action_edit_group_remove_member) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            Identity.PublicIdentity member = mMemberAdapter.getItem(info.position);
            try {
                if (!member.mId.equals(Data.getInstance().getSelfOrThrow().mId)) {
                    mMemberAdapter.remove(member);
                    mMemberAdapter.notifyDataSetChanged();
                    showAvatar();
                }
                // *TODO* what's the UI for resigning from a group?
                // TODO: else display toast?
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to remove group member");
            }
            setHasEdits(true);
            mode.finish();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionMode = null;
    }

    @Override
    public void onClick(View view) {
        if (view.equals(mSaveButton)) {
            if (mSaveButton == null) {
                return;
            }
            String name = mNameEdit.getText().toString();
            try {
                List<Identity.PublicIdentity> members = new ArrayList<Identity.PublicIdentity>();
                for (int i = 0; i < mMemberAdapter.getCount(); i++) {
                    members.add(mMemberAdapter.getItem(i));
                }
                saveGroup(mIsNewGroup, mGroupId, name, members);
                String prompt = getString(R.string.prompt_edit_group_saved, name);
                Toast.makeText(this, prompt, Toast.LENGTH_LONG).show();
                finish();
            } catch (Data.AlreadyExistsError e) {
                String prompt = getString(R.string.prompt_edit_group_already_exists, name);
                Toast.makeText(this, prompt, Toast.LENGTH_LONG).show();
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to save group");
            }
        }
    }

    private void saveGroup(
            boolean isNewGroup,
            String id,
            String name,
            List<Identity.PublicIdentity> members)
                    throws Data.AlreadyExistsError, PloggyError {
        Data data = Data.getInstance();
        Protocol.Group saveGroup = null;
        Date now = new Date();
        if (isNewGroup) {
            saveGroup = new Protocol.Group(
                    Utils.makeId(),
                    name,
                    data.getSelfId(),
                    members,
                    now,
                    now,
                    Protocol.UNASSIGNED_SEQUENCE_NUMBER,
                    false);
        } else {
            Protocol.Group group = data.getGroupOrThrow(mGroupId).mGroup;
            saveGroup = new Protocol.Group(
                    group.mId,
                    group.mName,
                    group.mPublisherId,
                    members,
                    group.mCreatedTimestamp,
                    now,
                    group.mSequenceNumber,
                    group.mIsTombstone);
        }
        data.putGroup(saveGroup);
    }
}
