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

import java.util.Timer;
import java.util.TimerTask;

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;

public class ActivityGenerateSelf extends Activity implements View.OnClickListener {

    private ImageView mAvatarImage;
    private EditText mNicknameEdit;
    private TextView mFingerprintText;
    private Button mEditButton;
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
        mNicknameEdit.setEnabled(false);
        mFingerprintText = (TextView)findViewById(R.id.generate_self_fingerprint_text);
        mEditButton = (Button)findViewById(R.id.generate_self_edit_button);
        mEditButton.setEnabled(false);
        mEditButton.setVisibility(View.GONE);
        mEditButton.setOnClickListener(this);
        mSaveButton = (Button)findViewById(R.id.generate_self_save_button);
        mSaveButton.setEnabled(false);
        mSaveButton.setVisibility(View.GONE);
        mSaveButton.setOnClickListener(this);
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage(getText(R.string.text_generate_self_progress));
        mProgressDialog.setCancelable(true);
        mAvatarTimer = new Timer();
    }

    private void showAvatarAndFingerprint(Identity.PublicIdentity publicIdentity) {
        if (publicIdentity.mNickname.length() > 0) {
            try {
                Robohash.setRobohashImage(this, mAvatarImage, publicIdentity);
                mFingerprintText.setText(Utils.encodeHex(publicIdentity.getFingerprint()));        
                return;
            } catch (Utils.ApplicationError e) {
                // TODO: log
            }
        }
        Robohash.setRobohashImage(this, mAvatarImage, null);
        mFingerprintText.setText("");        
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // TODO: ...two modes (1) self already exists; (2) no self exists
        Data.Self self = getSelf();
        if (self == null) {
            startGenerating();
        } else {
            showAvatarAndFingerprint(self.mPublicIdentity);
            mNicknameEdit.setText(self.mPublicIdentity.mNickname);
            mEditButton.setEnabled(true);
            mEditButton.setVisibility(View.VISIBLE);
            mSaveButton.setEnabled(false);
            mSaveButton.setVisibility(View.GONE);

        }
        // TODO: ...don't show keyboard until edit selected
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }
    
    private void startGenerating() {
        Robohash.setRobohashImage(this, mAvatarImage, null);
        mNicknameEdit.setText("");
        mEditButton.setEnabled(false);
        mEditButton.setVisibility(View.GONE);
        mSaveButton.setEnabled(false);
        mSaveButton.setVisibility(View.VISIBLE);        
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

    @Override
    public void onBackPressed() {
        if (getSelf() != null) {
            super.onBackPressed();
        }
    }
    
    @Override
    public void onClick(View view) {
        if (view.equals(mEditButton)) {
            startGenerating();            
        } else if (view.equals(mSaveButton)) {
            String nickname = mNicknameEdit.getText().toString();
            if (mGenerateResult == null || !Protocol.isValidNickname(nickname)) {
                return;
            }            
            try {
                Data.getInstance().updateSelf(
                        new Data.Self(
                                Identity.makeSignedPublicIdentity(
                                        nickname,
                                        mGenerateResult.mX509KeyMaterial,
                                        mGenerateResult.mHiddenServiceKeyMaterial),
                                Identity.makePrivateIdentity(
                                        mGenerateResult.mX509KeyMaterial,
                                        mGenerateResult.mHiddenServiceKeyMaterial)));
                finish();
            } catch (Utils.ApplicationError e) {
                // TODO: log?
            }            
        }
    }

    private static class GenerateResult {
        public final X509.KeyMaterial mX509KeyMaterial;
        public final HiddenService.KeyMaterial mHiddenServiceKeyMaterial;

        public GenerateResult(
                X509.KeyMaterial x509KeyMaterial,
                HiddenService.KeyMaterial hiddenServiceKeyMaterial) {
            mX509KeyMaterial = x509KeyMaterial;
            mHiddenServiceKeyMaterial = hiddenServiceKeyMaterial;
        }
    }
    
    private class GenerateTask extends AsyncTask<Void, Void, GenerateResult> {
        @Override
        protected GenerateResult doInBackground(Void... params) {
            // TODO: check isCancelled()
            try {
                return new GenerateResult(
                        X509.generateKeyMaterial(),
                        // TODO: HiddenService.generateKeyMaterial());
                        new HiddenService.KeyMaterial(Utils.getRandomHexString(1024), Utils.getRandomHexString(1024)));
            } catch (Utils.ApplicationError e) {
                // TODO: log
                // TODO: can't dismiss Activity if can't generate initial self key material...?
            }
            return null;
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
            if (result != null) {
                mGenerateResult = result;
                // TODO: ...temporary publicIdentity
                showAvatarAndFingerprint(
                        new Identity.PublicIdentity(
                                mNicknameEdit.getText().toString(),
                                mGenerateResult.mX509KeyMaterial.mCertificate,
                                mGenerateResult.mHiddenServiceKeyMaterial.mHostname,
                                null));
                mNicknameEdit.setEnabled(true);
            }
        }
    }

    private TextWatcher getNicknameTextChangedListener() {
        // TODO: ...refresh  robohash 1 second after stop typing nickname
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
                
                mSaveButton.setEnabled(mGenerateResult != null && Protocol.isValidNickname(nickname));

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
                                    showAvatarAndFingerprint(
                                            new Identity.PublicIdentity(
                                                    nickname,
                                                    mGenerateResult.mX509KeyMaterial.mCertificate,
                                                    mGenerateResult.mHiddenServiceKeyMaterial.mHostname,
                                                    null));
                                }
                            }
                        });
                    }
                };
                mAvatarTimer.schedule(mAvatarTimerTask, 1000);
            }
        };
    }

    static private Data.Self getSelf() {
        Data.Self self = null;
        try {
            self = Data.getInstance().getSelf();
        } catch (Utils.ApplicationError e) {
            // TODO: log?
        }
        return self;
    }
    
    static public void checkLaunchGenerateSelf(Context context) {
        // TODO: ...helper to ensure Self is generated
        if (getSelf() == null) {
            context.startActivity(new Intent(context, ActivityGenerateSelf.class));
        }
    }        
}
