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

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
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

/**
 * User interface for (re-)generating self identity.
 *
 * An AsyncTask is used to generate TLS and Hidden Service key material. A progress
 * spinner is displayed while generation occurs, then the user may type their
 * nickname. The resulting identity fingerprint and Robohash avatar is updated
 * after brief pauses in typing.
 */
public class ActivityGenerateSelf extends ActivityPloggyBase implements View.OnClickListener {

    private static final String LOG_TAG = "Generate Self";

    private ImageView mAvatarImage;
    private EditText mNicknameEdit;
    private TextView mFingerprintText;
    private Button mRegenerateButton;
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
        mRegenerateButton = (Button)findViewById(R.id.generate_self_regenerate_button);
        mRegenerateButton.setEnabled(false);
        mRegenerateButton.setVisibility(View.GONE);
        mRegenerateButton.setOnClickListener(this);
        mSaveButton = (Button)findViewById(R.id.generate_self_save_button);
        mSaveButton.setEnabled(false);
        mSaveButton.setVisibility(View.GONE);
        mSaveButton.setOnClickListener(this);
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage(getText(R.string.prompt_generate_self_progress));
        mProgressDialog.setCancelable(false);
        mAvatarTimer = new Timer();
    }

    private void showAvatarAndFingerprint(Identity.PublicIdentity publicIdentity) {
        try {
            if (publicIdentity.mNickname.length() > 0) {
                Robohash.setRobohashImage(this, mAvatarImage, false, publicIdentity);
            } else {
                Robohash.setRobohashImage(this, mAvatarImage, false, null);
            }
            mFingerprintText.setText(Utils.formatFingerprint(publicIdentity.getFingerprint()));
        } catch (PloggyError e) {
            Log.addEntry(LOG_TAG, "failed to show self");
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        Data.Self self = getSelf(this);
        if (self == null) {
            startGenerating();
        } else {
            showAvatarAndFingerprint(self.mPublicIdentity);
            mNicknameEdit.setText(self.mPublicIdentity.mNickname);
            mRegenerateButton.setEnabled(true);
            mRegenerateButton.setVisibility(View.VISIBLE);
            mSaveButton.setEnabled(false);
            mSaveButton.setVisibility(View.GONE);

        }

        // Don't show the keyboard until edit selected
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    private void startGenerating() {
        Robohash.setRobohashImage(this, mAvatarImage, false, null);
        mNicknameEdit.setText("");
        mFingerprintText.setText("");
        mRegenerateButton.setEnabled(false);
        mRegenerateButton.setVisibility(View.GONE);
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
        if (getSelf(this) != null) {
            super.onBackPressed();
        }
    }

    @Override
    public void onClick(View view) {
        if (view.equals(mRegenerateButton)) {
            startGenerating();
        } else if (view.equals(mSaveButton)) {
            String nickname = mNicknameEdit.getText().toString();
            if (mGenerateResult == null || !Protocol.isValidNickname(nickname)) {
                return;
            }
            try {
                Data.getInstance().putSelf(
                        new Data.Self(
                                Identity.makeSignedPublicIdentity(
                                        nickname,
                                        mGenerateResult.mX509KeyMaterial,
                                        mGenerateResult.mHiddenServiceKeyMaterial),
                                Identity.makePrivateIdentity(
                                        mGenerateResult.mX509KeyMaterial,
                                        mGenerateResult.mHiddenServiceKeyMaterial),
                                new Date()));

                Utils.hideKeyboard(this);

                finish();
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to update self");
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
            try {
                HiddenService.KeyMaterial hiddenServiceKeyMaterial = HiddenService.generateKeyMaterial();
                Log.addEntry(LOG_TAG, "generated Tor hidden service key material");
                // TODO: possible to check isCancelled within the key generation?
                if (isCancelled()) {
                    return null;
                }
                X509.KeyMaterial x509KeyMaterial = X509.generateKeyMaterial(hiddenServiceKeyMaterial.mHostname);
                Log.addEntry(LOG_TAG, "generated X.509 key material");
                return new GenerateResult(x509KeyMaterial, hiddenServiceKeyMaterial);
            } catch (PloggyError e) {
                Log.addEntry(LOG_TAG, "failed to generate key material");
                return null;
            }
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
            if (result == null) {
                // Failed, so dismiss the activity.
                // For now, this will simply restart this activity.
                finish();
            } else {
                mGenerateResult = result;
                // Display fingerprint/avatar for blank nickname
                try {
                    showAvatarAndFingerprint(
                            new Identity.PublicIdentity(
                                    mNicknameEdit.getText().toString(),
                                    mGenerateResult.mX509KeyMaterial.mCertificate,
                                    mGenerateResult.mHiddenServiceKeyMaterial.mHostname,
                                    mGenerateResult.mHiddenServiceKeyMaterial.mAuthCookie,
                                    null));
                } catch (PloggyError e) {
                }
                mNicknameEdit.setEnabled(true);
            }
        }
    }

    private TextWatcher getNicknameTextChangedListener() {
        // Refresh identity fingerprint and Robohash avatar 1 second after user stops typing nickname
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

                // TODO: use Handler instead of Timer
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
                                    // Display fingerprint/avatar for updated nickname
                                    try {
                                        showAvatarAndFingerprint(
                                                new Identity.PublicIdentity(
                                                        nickname,
                                                        mGenerateResult.mX509KeyMaterial.mCertificate,
                                                        mGenerateResult.mHiddenServiceKeyMaterial.mHostname,
                                                        mGenerateResult.mHiddenServiceKeyMaterial.mAuthCookie,
                                                        null));
                                    } catch (PloggyError e) {
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

    static private Data.Self getSelf(Context context) {
        Data.Self self = null;
        try {
            self = Data.getInstance().getSelfOrThrow();
        } catch (PloggyError e) {
        }
        return self;
    }

    static public void checkLaunchGenerateSelf(Context context) {
        // Helper to ensure Self is generated. Called from other Activities to jump to this one first.
        // When Self is generated, ensure the background Service is started.
        if (getSelf(context) == null) {
            context.startActivity(new Intent(context, ActivityGenerateSelf.class));
        } else {
            context.startService(new Intent(context, PloggyService.class));
        }
    }
}
