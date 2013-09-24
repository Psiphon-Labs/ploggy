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
import android.content.Context;
import android.content.Intent;

public class AddFriendActivity extends Activity implements View.OnClickListener, NfcAdapter.CreateNdefMessageCallback, NfcAdapter.OnNdefPushCompleteCallback {

    static private final String NFC_MIME_TYPE = "application/ca.psiphon.ploggy.android.beam";
    static private final String NFC_AAR_PACKAGE_NAME = "ca.psiphon.ploggy";
    
    private NfcAdapter mNfcAdapter;
    private ImageView mFriendAvatarImage;
    private RelativeLayout mFriendSectionLayout;
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
        
        Data.Self self = null;
        try {
            self = Data.getInstance().getSelf();
        } catch (Utils.ApplicationError e) {
            // TODO: log?
        } catch (Data.DataNotFoundException e) {
        }
        if (self == null) {
            // TODO: prompt?
            finish();
            return;
        }

        ImageView selfAvatarImage = (ImageView)findViewById(R.id.add_friend_self_avatar_image);
        TextView selfNicknameText = (TextView)findViewById(R.id.add_friend_self_nickname_text);
        TextView selfTransportPublicKeyFingerprintText = (TextView)findViewById(R.id.add_friend_self_transport_public_key_fingerprint_text);
        TextView selfTransportPublicKeyTimestampText = (TextView)findViewById(R.id.add_friend_self_transport_public_key_timestamp_text);
        TextView selfHiddenServiceHostnameText = (TextView)findViewById(R.id.add_friend_self_hidden_service_hostname_text);

        selfAvatarImage.setImageResource(R.drawable.ic_unknown_avatar);
        selfNicknameText.setText(self.mNickname);        
        selfTransportPublicKeyFingerprintText.setText(self.mTransportKeyMaterial.getCertificate().getFingerprint());        
        selfTransportPublicKeyTimestampText.setText(DateFormat.getDateInstance().format(self.mTransportKeyMaterial.getCertificate().getTimestamp()));        
        selfHiddenServiceHostnameText.setText(self.mHiddenServiceKeyMaterial.mHostname);        
        
        mFriendSectionLayout = (RelativeLayout)findViewById(R.id.add_friend_friend_section);
        mFriendSectionLayout.setVisibility(View.GONE);
        mFriendAvatarImage = (ImageView)findViewById(R.id.add_friend_friend_avatar_image);
        mFriendNicknameText = (TextView)findViewById(R.id.add_friend_friend_nickname_text);
        mFriendTransportPublicKeyFingerprintText = (TextView)findViewById(R.id.add_friend_friend_transport_public_key_fingerprint_text);
        mFriendTransportPublicKeyTimestampText = (TextView)findViewById(R.id.add_friend_friend_transport_public_key_timestamp_text);
        mFriendHiddenServiceHostnameText = (TextView)findViewById(R.id.add_friend_friend_hidden_service_hostname_text);
        mFriendAddButton = (Button)findViewById(R.id.add_friend_add_button);
        mFriendAddButton.setEnabled(false);
        mFriendAddButton.setOnClickListener(this);

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

        mNfcAdapter.setNdefPushMessageCallback(this, this);
        mNfcAdapter.setOnNdefPushCompleteCallback(this, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        Intent intent = getIntent();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage msg = (NdefMessage)rawMsgs[0];
            String payload = new String(msg.getRecords()[0].getPayload());
            try {
                Data.Friend friend = Json.fromJson(payload, Data.Friend.class);
                // TODO: store in pendingFriend, enable Add button
                mFriendAvatarImage.setImageResource(R.drawable.ic_unknown_avatar);
                mFriendNicknameText.setText(friend.mNickname);        
                mFriendTransportPublicKeyFingerprintText.setText(friend.mTransportCertificate.getFingerprint());        
                mFriendTransportPublicKeyTimestampText.setText(DateFormat.getDateInstance().format(friend.mTransportCertificate.getTimestamp()));        
                mFriendHiddenServiceHostnameText.setText(friend.mHiddenServiceIdentity.mHostname);
                mFriendAddButton.setEnabled(true);
                mFriendSectionLayout.setVisibility(View.VISIBLE);
            } catch (Utils.ApplicationError e) {
                // TODO: log?
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        // TODO: trigger UI update when already resumed...? Need foreground dispatch?
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        String payload;
        try {
            payload = Json.toJson(Data.getInstance().getSelf().getFriend());
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
    public void onNdefPushComplete(NfcEvent arg0) {
        // TODO: runOnUiThread(Runnable r)
        Vibrator vibe = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE) ;
        vibe.vibrate(500); 
    }

    @Override
    public void onClick(View view) {
        if (view.equals(mFriendAddButton)) {
            // TODO: ...
        }
    }
}
