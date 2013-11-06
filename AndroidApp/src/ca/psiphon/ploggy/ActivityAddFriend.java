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

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.app.Activity;

/**
 * Common user interface for adding friends.
 */
public class ActivityAddFriend extends Activity implements View.OnClickListener {

    private ImageView mSelfAvatarImage;
    private TextView mSelfNicknameText;
    private TextView mSelfFingerprintText;
    
    private RelativeLayout mFriendSectionLayout;
    private ImageView mFriendAvatarImage;
    private TextView mFriendNicknameText;
    private TextView mFriendFingerprintText;
    private Button mFriendAddButton;
        
    // These variables track the workflow state. Note that there's no check in this workflow
    // that the same device was pushed to/received from.
    protected boolean mSelfPushComplete;
    protected Data.Friend mReceivedFriend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_friend);
        
        mSelfAvatarImage = (ImageView)findViewById(R.id.add_friend_self_avatar_image);
        mSelfNicknameText = (TextView)findViewById(R.id.add_friend_self_nickname_text);
        mSelfFingerprintText = (TextView)findViewById(R.id.add_friend_self_fingerprint_text);
        
        mFriendSectionLayout = (RelativeLayout)findViewById(R.id.add_friend_friend_section);
        mFriendAvatarImage = (ImageView)findViewById(R.id.add_friend_friend_avatar_image);
        mFriendNicknameText = (TextView)findViewById(R.id.add_friend_friend_nickname_text);
        mFriendFingerprintText = (TextView)findViewById(R.id.add_friend_friend_fingerprint_text);
        mFriendAddButton = (Button)findViewById(R.id.add_friend_add_button);
        mFriendAddButton.setOnClickListener(this);
        showFriend();
        
        mSelfPushComplete = false;
        mReceivedFriend = null;
    }

    protected void showSelf() {
        try {
            Data.Self self = Data.getInstance().getSelf();
            Robohash.setRobohashImage(this, mSelfAvatarImage, true, self.mPublicIdentity);
            mSelfNicknameText.setText(self.mPublicIdentity.mNickname);
            mSelfFingerprintText.setText(Utils.formatFingerprint(self.mPublicIdentity.getFingerprint()));        
            return;
        } catch (Utils.ApplicationError e) {
            // TODO: log?
        }
        Robohash.setRobohashImage(this, mSelfAvatarImage, true, null);
        mSelfNicknameText.setText("");
        mSelfFingerprintText.setText("");
    }

    protected void showFriend() {
        if (mReceivedFriend != null) {
            try {
                Robohash.setRobohashImage(this, mFriendAvatarImage, true, mReceivedFriend.mPublicIdentity);
                mFriendNicknameText.setText(mReceivedFriend.mPublicIdentity.mNickname);        
                mFriendFingerprintText.setText(Utils.formatFingerprint(mReceivedFriend.mPublicIdentity.getFingerprint()));        
                mFriendAddButton.setEnabled(mSelfPushComplete);
                mFriendSectionLayout.setVisibility(View.VISIBLE);
                return;
            } catch (Utils.ApplicationError e) {
                // TODO: log?
            }
        }
        mFriendAddButton.setEnabled(false);
        mFriendSectionLayout.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        ActivityGenerateSelf.checkLaunchGenerateSelf(this);
        showSelf();
    }

    @Override
    public void onClick(View view) {
        if (view.equals(mFriendAddButton)) {            
            if (!mSelfPushComplete || mReceivedFriend == null) {
                return;
            }
            try {
                Data.getInstance().addFriend(mReceivedFriend);
                finish();
            } catch (Data.DataAlreadyExistsError e) {
                // TODO: display error message (toast): friend already exists
            } catch (Utils.ApplicationError e) {
                // TODO: log? display error message (toast): friend already exists
            }            
        }
    }
}
