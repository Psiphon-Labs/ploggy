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

import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

/**
 * Common base class for all activities.
 *
 * - Sends identity via NFC.
 * - Triggers location fixes while in foreground.
 *
 * Implements NFC (Android Beam) identity exchange. Due to the "Touch to Beam" OS prompt enforced
 * for Beam for Android 4+, apps cannot automatically send a Beam in response to an incoming Beam
 * (for mutual identity exchange triggered by one device). So NFC identity exchange is
 * unidirectional.
 *
 * This class is used as the base class for most/all Ploggy Activities so that identity is
 * exchanged when users initiate an Android Beam when any Ploggy UI is active.
 */
public class ActivityPloggyBase extends FragmentActivity implements NfcAdapter.CreateNdefMessageCallback, NfcAdapter.OnNdefPushCompleteCallback {

    private static final String LOG_TAG = "Base Activity";

    private static final String NFC_MIME_TYPE = "application/ca.psiphon.ploggy.android.beam";
    private static final String NFC_AAR_PACKAGE_NAME = "ca.psiphon.ploggy";

    private boolean mNfcEnabled;
    private NfcAdapter mNfcAdapter;
    private Utils.FixedDelayExecutor mLocationFixExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mNfcEnabled = false;
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter != null) {
            if (!mNfcAdapter.isEnabled() || !mNfcAdapter.isNdefPushEnabled()) {
                Toast.makeText(this, R.string.prompt_nfc_not_enabled, Toast.LENGTH_LONG).show();
            } else {
                mNfcEnabled = true;
                mNfcAdapter.setNdefPushMessageCallback(this, this);
                mNfcAdapter.setOnNdefPushCompleteCallback(this, this);
            }
        }

        mLocationFixExecutor = new Utils.FixedDelayExecutor(
                new Runnable() {@Override public void run() {Events.getInstance().post(new Events.RefreshSelfLocationFix());}},
                // TODO: preference?
                TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mLocationFixExecutor.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mLocationFixExecutor.stop();
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        try {
            String payload = Json.toJson(Data.getInstance().getSelfOrThrow().mPublicIdentity);
            return new NdefMessage(
                    new NdefRecord[] {
                            NdefRecord.createMime(NFC_MIME_TYPE, payload.getBytes()),
                            NdefRecord.createApplicationRecord(NFC_AAR_PACKAGE_NAME) });
        } catch (PloggyError e) {
            Log.addEntry(LOG_TAG, "failed to create outbound NFC message");
        }
        return null;
    }

    @Override
    public void onNdefPushComplete(NfcEvent nfcEvent) {
        final Context finalContext = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Vibrator vibe = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
                vibe.vibrate(100);
                Toast.makeText(finalContext, R.string.prompt_nfc_push_complete, Toast.LENGTH_LONG).show();
            }});
    }

    public String getNfcMimeType() {
        return NFC_MIME_TYPE;
    }

    public NfcAdapter getNfcAdapter() {
        return mNfcEnabled ? mNfcAdapter : null;
    }
}
