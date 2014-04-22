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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * User interface for adding friends.
 *
 * This activity is invoked by various sources of serialized Identity blobs,
 * including .ploggy files (e.g., Email attachments), NFC messages, etc.
 */
public class ActivityAddFriend extends ActivityPloggyBase implements View.OnClickListener {

    private static final String LOG_TAG = "Add Friend";

    public static final String ACTION_ADD_FRIEND = "ca.psiphon.ploggy.action.DISPLAY_VIEW";
    public static final String ACTION_ADD_FRIEND_EXTRA_PUBLIC_IDENTITY = "PUBLIC_IDENTITY";
    public static final String ADD_FRIEND_IDENTITY_LINK_PREFIX = "ftp://identity.ploggy/#";

    public static Intent makeAddFriendIntent(Context context, Identity.PublicIdentity publicIdentity) {
        Intent intent = new Intent(context, ActivityAddFriend.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(ACTION_ADD_FRIEND);
        intent.putExtra(ACTION_ADD_FRIEND_EXTRA_PUBLIC_IDENTITY, Json.toJson(publicIdentity));
        return intent;
    }

    private TextView mHint;
    private RelativeLayout mFriendSection;
    private ImageView mFriendAvatarImage;
    private TextView mFriendNicknameText;
    private TextView mFriendFingerprintText;
    private Button mFriendAddButton;
    private Data.Friend mReceivedFriend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_friend);

        mHint = (TextView)findViewById(R.id.add_friend_hint_label);
        mFriendSection = (RelativeLayout)findViewById(R.id.add_friend_friend_section);
        mFriendAvatarImage = (ImageView)findViewById(R.id.add_friend_friend_avatar_image);
        mFriendNicknameText = (TextView)findViewById(R.id.add_friend_friend_nickname_text);
        mFriendFingerprintText = (TextView)findViewById(R.id.add_friend_friend_fingerprint_text);
        mFriendAddButton = (Button)findViewById(R.id.add_friend_add_button);
        mFriendAddButton.setOnClickListener(this);
        mReceivedFriend = null;
    }

    protected void showFriend() {
        if (mReceivedFriend != null) {
            try {
                mHint.setVisibility(View.INVISIBLE);
                mFriendSection.setVisibility(View.VISIBLE);
                Avatar.setAvatarImage(this, mFriendAvatarImage, true, mReceivedFriend.mPublicIdentity);
                mFriendNicknameText.setText(mReceivedFriend.mPublicIdentity.mNickname);
                mFriendFingerprintText.setText(Utils.formatFingerprint(mReceivedFriend.mPublicIdentity.getFingerprint()));
                return;
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to show friend");
            }
        } else {
            mHint.setVisibility(View.VISIBLE);
            mFriendSection.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    private void setupForegroundDispatch() {
        NfcAdapter nfcAdapter = getNfcAdapter();
        if (nfcAdapter != null) {
            IntentFilter intentFilter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
            try {
                intentFilter.addDataType(getNfcMimeType());
            } catch (IntentFilter.MalformedMimeTypeException e) {
                Log.addEntry(LOG_TAG, e.getMessage());
                return;
            }
            nfcAdapter.enableForegroundDispatch(
                   this,
                   PendingIntent.getActivity(
                           this,
                           0,
                           new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0),
                   new IntentFilter[] {intentFilter},
                   null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // TODO: ActivityGenerateSelf.checkLaunchGenerateSelf(this);?
        // TODO: foreground dispatch causes onResume to be called?
        Identity.PublicIdentity publicIdentity = null;
        Intent intent = getIntent();
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(ACTION_ADD_FRIEND)) {
                try {
                    publicIdentity =
                        Json.fromJson(
                            intent.getStringExtra(ACTION_ADD_FRIEND_EXTRA_PUBLIC_IDENTITY),
                            Identity.PublicIdentity.class);
                } catch (PloggyError e) {
                    Log.addEntry(LOG_TAG, "failed to parse JSON intent");
                }
            } else if (intent.getAction().equals(NfcAdapter.ACTION_NDEF_DISCOVERED)) {
                try {
                    Parcelable[] ndefMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
                    NdefMessage ndefMessage = (NdefMessage)ndefMessages[0];
                    String payload = new String(ndefMessage.getRecords()[0].getPayload());
                    publicIdentity = Json.fromJson(payload, Identity.PublicIdentity.class);
                } catch (PloggyError e) {
                    Log.addEntry(LOG_TAG, "failed to handle inbound NFC message");
                }
            } else {
                // Extract friend public identity from email attachment (or file)
                InputStream inputStream = null;
                try {
                    Uri uri = intent.getData();

                    if (uri.toString().startsWith(ADD_FRIEND_IDENTITY_LINK_PREFIX)) {
                        inputStream = Utils.makeInputStream(uri.getFragment());
                    }
                    else {
                        String scheme = uri.getScheme();
                        if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
                            ContentResolver contentResolver = getContentResolver();
                            inputStream = contentResolver.openInputStream(uri);
                        } else {
                            String filePath = uri.getEncodedPath();
                            if (filePath != null) {
                                inputStream = new FileInputStream(new File(filePath));
                            }
                        }
                    }

                    if (inputStream != null) {
                        String payload = Utils.readInputStreamToString(inputStream);
                        publicIdentity = Json.fromJson(payload, Identity.PublicIdentity.class);
                    }
                } catch (IOException e) {
                    Log.addEntry(LOG_TAG, e.getMessage());
                    Log.addEntry(LOG_TAG, "failed to open .ploggy file");
                } catch (PloggyError e) {
                    Log.addEntry(LOG_TAG, "failed to open .ploggy file");
                } finally {
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
        }

        // TODO: display validation error message in Activity?
        // TODO: update add_friend_description_text on success/failure?
        mReceivedFriend = null;
        if (publicIdentity != null) {
            try {
                Protocol.validatePublicIdentity(publicIdentity);
                Data.Friend friend = new Data.Friend(publicIdentity, new Date());
                mReceivedFriend = friend;
                showFriend();
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to display friend");
            }
        }
        showFriend();

        // Use foreground dispatch to ensure repeated incoming beams don't create new activities
        setupForegroundDispatch();
    }

    @Override
    public void onPause() {
        super.onPause();
        NfcAdapter nfcAdapter = getNfcAdapter();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    public void onClick(View view) {
        if (view.equals(mFriendAddButton)) {
            if (mReceivedFriend == null) {
                return;
            }
            try {
                Data.getInstance().addFriend(mReceivedFriend);
                String prompt = getString(R.string.prompt_add_friend_friend_added, mReceivedFriend.mPublicIdentity.mNickname);
                Toast.makeText(this, prompt, Toast.LENGTH_LONG).show();
                finish();
            } catch (Data.AlreadyExistsError e) {
                String prompt = getString(R.string.prompt_add_friend_friend_already_exists, mReceivedFriend.mPublicIdentity.mNickname);
                Toast.makeText(this, prompt, Toast.LENGTH_LONG).show();
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to add friend");
            }
        }
    }
}
