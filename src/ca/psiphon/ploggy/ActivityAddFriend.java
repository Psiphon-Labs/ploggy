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

import java.nio.charset.Charset;
import java.text.DateFormat;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class ActivityAddFriend extends Activity implements View.OnClickListener, NfcAdapter.CreateNdefMessageCallback, NfcAdapter.OnNdefPushCompleteCallback {

    private static final String NFC_MIME_TYPE = "application/ca.psiphon.ploggy.android.beam";
    private static final String NFC_AAR_PACKAGE_NAME = "ca.psiphon.ploggy";
    
    private NfcAdapter mNfcAdapter;
    // TODO: no guarantee same device pushed to/received from
    private boolean mPushComplete;
    private Data.Friend mReceivedFriend;

    ImageView mSelfAvatarImage;
    TextView mSelfNicknameText;
    TextView mSelfTransportPublicKeyFingerprintText;
    TextView mSelfTransportPublicKeyTimestampText;
    TextView mSelfHiddenServiceHostnameText;
    
    private RelativeLayout mFriendSectionLayout;
    private ImageView mFriendAvatarImage;
    private TextView mFriendNicknameText;
    private TextView mFriendTransportPublicKeyFingerprintText;
    private TextView mFriendTransportPublicKeyTimestampText;
    private TextView mFriendHiddenServiceHostnameText;
    private Button mFriendAddButton;
        
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_friend);
        
        // TODO: http://developer.android.com/guide/topics/connectivity/nfc/nfc.html#p2p
        // TODO: http://stackoverflow.com/questions/10887275/aar-record-in-nfc-wheres-the-payload
        // TODO: http://stackoverflow.com/questions/15602275/android-beam-payload-transfer-from-both-devices-when-only-one-touch-to-beam
        // TODO: http://mobisocial.github.io/EasyNFC/apidocs/reference/mobisocial/nfc/addon/BluetoothConnector.html
        // TODO: http://code.google.com/p/ndef-tools-for-android/
        
        mSelfAvatarImage = (ImageView)findViewById(R.id.add_friend_self_avatar_image);
        mSelfNicknameText = (TextView)findViewById(R.id.add_friend_self_nickname_text);
        mSelfTransportPublicKeyFingerprintText = (TextView)findViewById(R.id.add_friend_self_transport_public_key_fingerprint_text);
        mSelfTransportPublicKeyTimestampText = (TextView)findViewById(R.id.add_friend_self_transport_public_key_timestamp_text);
        mSelfHiddenServiceHostnameText = (TextView)findViewById(R.id.add_friend_self_hidden_service_hostname_text);
        
        mFriendSectionLayout = (RelativeLayout)findViewById(R.id.add_friend_friend_section);
        mFriendAvatarImage = (ImageView)findViewById(R.id.add_friend_friend_avatar_image);
        mFriendNicknameText = (TextView)findViewById(R.id.add_friend_friend_nickname_text);
        mFriendTransportPublicKeyFingerprintText = (TextView)findViewById(R.id.add_friend_friend_transport_public_key_fingerprint_text);
        mFriendTransportPublicKeyTimestampText = (TextView)findViewById(R.id.add_friend_friend_transport_public_key_timestamp_text);
        mFriendHiddenServiceHostnameText = (TextView)findViewById(R.id.add_friend_friend_hidden_service_hostname_text);
        mFriendAddButton = (Button)findViewById(R.id.add_friend_add_button);
        mFriendAddButton.setOnClickListener(this);
        showFriend();
        
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) {
            Toast.makeText(this, R.string.prompt_nfc_not_available, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // TODO: isNdefPushEnabled (API 16)
        if (!mNfcAdapter.isEnabled()) {
            Toast.makeText(this, R.string.prompt_nfc_not_enabled, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mPushComplete = false;
        mReceivedFriend = null;
        mNfcAdapter.setNdefPushMessageCallback(this, this);
        mNfcAdapter.setOnNdefPushCompleteCallback(this, this);
    }

    private void showSelf() {
        try {
            Data.Self self = Data.getInstance().getSelf();
            Robohash.setRobohashImage(this, mSelfAvatarImage, self);
            mSelfNicknameText.setText(self.mNickname);        
            mSelfTransportPublicKeyFingerprintText.setText(self.mTransportKeyMaterial.getCertificate().getFingerprint());        
            mSelfTransportPublicKeyTimestampText.setText(DateFormat.getDateInstance().format(self.mTransportKeyMaterial.getCertificate().getTimestamp()));        
            mSelfHiddenServiceHostnameText.setText(self.mHiddenServiceKeyMaterial.mHostname);
            return;
        } catch (Utils.ApplicationError e) {
            // TODO: log?
        } catch (Data.DataNotFoundException e) {
            // TODO: log?
        }
        Robohash.setRobohashImage(this, mSelfAvatarImage, (Data.Self)null);
        mSelfNicknameText.setText("");    
        mSelfTransportPublicKeyFingerprintText.setText("");        
        mSelfTransportPublicKeyTimestampText.setText("");        
        mSelfHiddenServiceHostnameText.setText("");        
    }

    private void showFriend() {
        if (mReceivedFriend != null) {
            Robohash.setRobohashImage(this, mFriendAvatarImage, mReceivedFriend);
            mFriendNicknameText.setText(mReceivedFriend.mNickname);        
            mFriendTransportPublicKeyFingerprintText.setText(mReceivedFriend.mTransportCertificate.getFingerprint());        
            mFriendTransportPublicKeyTimestampText.setText(DateFormat.getDateInstance().format(mReceivedFriend.mTransportCertificate.getTimestamp()));        
            mFriendHiddenServiceHostnameText.setText(mReceivedFriend.mHiddenServiceIdentity.mHostname);
            mFriendAddButton.setEnabled(mPushComplete);
            mFriendSectionLayout.setVisibility(View.VISIBLE);
        } else {
            mFriendAddButton.setEnabled(false);
            mFriendSectionLayout.setVisibility(View.GONE);
        }
    }

    private void setupForegroundDispatch() {
        IntentFilter intentFilter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            intentFilter.addDataType(NFC_MIME_TYPE);
        } catch (IntentFilter.MalformedMimeTypeException e) {
            // TODO: log
            return;
        }
        mNfcAdapter.enableForegroundDispatch(
               this,
               PendingIntent.getActivity(
                       this,
                       0,
                       new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0),
               new IntentFilter[] {intentFilter},
               null);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        Events.register(this);
        // TODO: handle intent? or is onNewIntent always called?
        setupForegroundDispatch();
        ActivityGenerateSelf.checkLaunchGenerateSelf(this);
        showSelf();
    }

    @Override
    public void onPause() {
        super.onPause();
        Events.unregister(this);
        mNfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        // TODO: trigger UI update when already resumed...? Need foreground dispatch?
        // TODO: http://stackoverflow.com/questions/7748392/is-there-any-reason-not-to-call-setintent-when-overriding-onnewintent
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            Parcelable[] ndefMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage ndefMessage = (NdefMessage)ndefMessages[0];
            String payload = new String(ndefMessage.getRecords()[0].getPayload());
            try {
                Data.Friend friend = Json.fromJson(payload, Data.Friend.class);
                if (!Protocol.isValidFriend(friend)) {
                    // TODO: show error?
                    return;
                }
                mReceivedFriend = friend;
                showFriend();
                int promptId = mPushComplete ? R.string.prompt_nfc_friend_received_and_push_complete : R.string.prompt_nfc_friend_received_without_push_complete;
                Toast.makeText(this, promptId, Toast.LENGTH_LONG).show();
            } catch (Utils.ApplicationError e) {
                // TODO: log?
            }
        }
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        try {
            String payload = Json.toJson(Data.getInstance().getSelf().getFriend());
            return new NdefMessage(
            		new NdefRecord[] {
            				// TODO: NdefRecord.createMime(NFC_MIME_TYPE, ),
            				new NdefRecord(
            						NdefRecord.TNF_MIME_MEDIA,
            						NFC_MIME_TYPE.getBytes(Charset.forName("US-ASCII")),
            						null,
            						payload.getBytes()),
            				NdefRecord.createApplicationRecord(
            						NFC_AAR_PACKAGE_NAME) });
        } catch (Utils.ApplicationError e) {
            // TODO: log?
        } catch (Data.DataNotFoundException e) {
        }
        return null;
    }

    @Override
    public void onNdefPushComplete(NfcEvent nfcEvent) {
        final Context context = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Vibrator vibe = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
                vibe.vibrate(100);
                mPushComplete = true;
                showFriend();
                int promptId = (mReceivedFriend != null) ? R.string.prompt_nfc_push_complete_and_friend_received : R.string.prompt_nfc_push_complete_without_friend_received;
                Toast.makeText(context, promptId, Toast.LENGTH_LONG).show();
            }});
    }

    @Override
    public void onClick(View view) {
        if (view.equals(mFriendAddButton)) {            
            if (mReceivedFriend == null) {
                return;
            }
            try {
                Data.getInstance().insertOrUpdateFriend(mReceivedFriend);
                finish();
            } catch (Utils.ApplicationError e) {
                // TODO: log?
            }            
        }
    }
}
