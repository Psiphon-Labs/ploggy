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

import java.util.Date;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Vibrator;
import android.widget.Toast;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * User interface for adding friends by NFC.
 * 
 * Implements NFC (Android Beam) identity exchange. Due to the "Touch to Beam" OS prompt enforced
 * for Beam for Android 4+, apps cannot automatically send a Beam in response to an incoming Beam
 * (for mutual identity exchange triggered by one device). So this Activity implements a workflow
 * instructing users what steps to follow. Foreground dispatch is used to ensure that the same
 * Activity instance receives the peer's Beam.
 * 
 * This Activity also handles the android.nfc.action.NDEF_DISCOVERED intent for Ploggy Beams, so
 * this will be launched if Ploggy isn't in the foreground.
 */
public class ActivityAddFriendByNfc extends ActivityAddFriend implements NfcAdapter.CreateNdefMessageCallback, NfcAdapter.OnNdefPushCompleteCallback {

    private static final String LOG_TAG = "Add Friend By NFC";

    private static final String NFC_MIME_TYPE = "application/ca.psiphon.ploggy.android.beam";
    private static final String NFC_AAR_PACKAGE_NAME = "ca.psiphon.ploggy";
    
    private NfcAdapter mNfcAdapter;
        
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) {
            Toast.makeText(this, R.string.prompt_nfc_not_available, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (!mNfcAdapter.isEnabled() || !mNfcAdapter.isNdefPushEnabled()) {
            Toast.makeText(this, R.string.prompt_nfc_not_enabled, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mNfcAdapter.setNdefPushMessageCallback(this, this);
        mNfcAdapter.setOnNdefPushCompleteCallback(this, this);
    }

    private void setupForegroundDispatch() {
        IntentFilter intentFilter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            intentFilter.addDataType(NFC_MIME_TYPE);
        } catch (IntentFilter.MalformedMimeTypeException e) {
            Log.addEntry(LOG_TAG, e.getMessage());
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
        // TODO: handle intent? or is onNewIntent always called?
        setupForegroundDispatch();
    }

    @Override
    public void onPause() {
        super.onPause();
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
                Identity.PublicIdentity publicIdentity = Json.fromJson(payload, Identity.PublicIdentity.class);
                Protocol.validatePublicIdentity(publicIdentity);
                Data.Friend friend = new Data.Friend(publicIdentity, new Date());
                // TODO: display validation error?
                mReceivedFriend = friend;
                showFriend();
                // TODO: update add_friend_description_text as well?
                int promptId = mSelfPushComplete ? R.string.prompt_nfc_friend_received_and_push_complete : R.string.prompt_nfc_friend_received_without_push_complete;
                Toast.makeText(this, promptId, Toast.LENGTH_LONG).show();
            } catch (Utils.ApplicationError e) {
                Log.addEntry(LOG_TAG, "failed to handle inbound NFC message");
            }
        }
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        try {
            String payload = Json.toJson(Data.getInstance().getSelf().mPublicIdentity);
            return new NdefMessage(
                    new NdefRecord[] {
                            NdefRecord.createMime(NFC_MIME_TYPE, payload.getBytes()),
                            NdefRecord.createApplicationRecord(NFC_AAR_PACKAGE_NAME) });
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to create outbound NFC message");
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
                mSelfPushComplete = true;
                showFriend();
                // TODO: update add_friend_description_text as well?
                int promptId = (mReceivedFriend != null) ? R.string.prompt_nfc_push_complete_and_friend_received : R.string.prompt_nfc_push_complete_without_friend_received;
                Toast.makeText(context, promptId, Toast.LENGTH_LONG).show();
            }});
    }
}
