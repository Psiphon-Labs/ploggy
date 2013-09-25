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

import java.text.DateFormat;
import java.util.Timer;
import java.util.TimerTask;

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;

public class ActivityGenerateSelf extends Activity implements View.OnClickListener {

    private ImageView mAvatarImage;
    private EditText mNicknameEdit;
    private TextView mTransportPublicKeyFingerprintText;
    private TextView mTransportPublicKeyTimestampText;
    private TextView mHiddenServiceHostnameText;
    private Button mSaveButton;
    private ProgressDialog mProgressDialog;
    private GenerateTask mGenerateTask;
    private GenerateResult mGenerateResult;
    private Timer mAvatarTimer;
    private TimerTask mAvatarTimerTask;
        
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generate_self);

        mAvatarImage = (ImageView)findViewById(R.id.generate_self_avatar_image);
        mAvatarImage.setImageResource(R.drawable.ic_unknown_avatar);
        mNicknameEdit = (EditText)findViewById(R.id.generate_self_nickname_edit);
        mNicknameEdit.addTextChangedListener(getNicknameTextChangedListener());
        mTransportPublicKeyFingerprintText = (TextView)findViewById(R.id.generate_self_transport_public_key_fingerprint_text);
        mTransportPublicKeyTimestampText = (TextView)findViewById(R.id.generate_self_transport_public_key_timestamp_text);
        mHiddenServiceHostnameText = (TextView)findViewById(R.id.generate_self_hidden_service_hostname_text);
        mSaveButton = (Button)findViewById(R.id.generate_self_save_button);
        mSaveButton.setEnabled(false);
        mSaveButton.setOnClickListener(this);
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage(getText(R.string.text_generate_progress));
        mProgressDialog.setCancelable(true);
        mAvatarTimer = new Timer();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // TODO: check if already generated?
        mGenerateTask = new GenerateTask();
        mGenerateTask.execute();
    }

    @Override
    public void onPause() {
        super.onPause();
        // TODO: http://stackoverflow.com/questions/1875670/what-to-do-with-asynctask-in-onpause
        if (mGenerateTask != null) {
            mGenerateTask.cancel(true);
            mGenerateTask = null;
        }
    }

    private static class GenerateResult {
        public final TransportSecurity.KeyMaterial mTransportKeyMaterial;
        public final HiddenService.KeyMaterial mHiddenServiceKeyMaterial;

        public GenerateResult(
                TransportSecurity.KeyMaterial transportKeyMaterial,
                HiddenService.KeyMaterial hiddenServiceKeyMaterial) {
            mTransportKeyMaterial = transportKeyMaterial;
            mHiddenServiceKeyMaterial = hiddenServiceKeyMaterial;
        }
    }
    
    private class GenerateTask extends AsyncTask<Void, Void, GenerateResult> {
        @Override
        protected GenerateResult doInBackground(Void... params) {
            // TODO: check isCancelled()
            return new GenerateResult(
                    TransportSecurity.KeyMaterial.generate(),
                    HiddenService.KeyMaterial.generate());
        }        

        @Override
        protected void onPreExecute() {
            mProgressDialog.show();
        }

        @Override
        protected void onPostExecute(GenerateResult result) {
            if (mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            mGenerateResult = result;
            TransportSecurity.Certificate transportCertificate = mGenerateResult.mTransportKeyMaterial.getCertificate();
            HiddenService.Identity hiddenServiceIdentity = mGenerateResult.mHiddenServiceKeyMaterial.getIdentity();
            mTransportPublicKeyFingerprintText.setText(transportCertificate.getFingerprint());        
            mTransportPublicKeyTimestampText.setText(DateFormat.getDateInstance().format(transportCertificate.getTimestamp()));        
            mHiddenServiceHostnameText.setText(hiddenServiceIdentity.mHostname);
        }
    }

    @Override
    public void onClick(View view) {
        if (view.equals(mSaveButton)) {
            // TODO: ...
        }
    }

    private TextWatcher getNicknameTextChangedListener() {
        // TODO: ...refresh  robohash 1 second after stop typing nickname
        final Context context = this;
        return new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                final String nickname = s.toString();
                if (mAvatarTimerTask != null) {
                    mAvatarTimerTask.cancel();
                }
                mAvatarTimerTask = new TimerTask() {          
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (mGenerateResult != null) {                                    
                                    // TODO: common helper function
                                    try {
                                        // TODO: cache bitmap
                                        Data.Friend friend = new Data.Friend(nickname, mGenerateResult.mTransportKeyMaterial.getCertificate(), mGenerateResult.mHiddenServiceKeyMaterial.getIdentity());
                                        mAvatarImage.setImageBitmap(Robohash.getRobohash(context,friend.mId.getBytes()));
                                    } catch (Utils.ApplicationError e) {
                                        // TODO: security issue?
                                        mAvatarImage.setImageResource(R.drawable.ic_unknown_avatar); 
                                    }
                                }
                            }
                        });
                    }
                };
                mAvatarTimer.schedule(mAvatarTimerTask, 1000);
            }
        };
    }
}
