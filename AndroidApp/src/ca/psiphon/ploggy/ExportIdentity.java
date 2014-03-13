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
import java.io.IOException;
import java.net.URLEncoder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.text.Html;
import android.widget.Toast;

/**
 * Helper for adding friends by exchanging email attachments.
 *
 * On the sending side, this launches an email draft containing self's
 * public identity as a ".ploggy" attachment. On the receiving side,
 * ActivityAddFriend handles ".ploggy" attachment invocations.
 *
 * This work flow can compromise unlinkability:
 * - The sent email will expose the Ploggy link between the sender
 *   and recipient to all email servers along the delivery path.
 * - The email attachment must be located on public storage for the email
 *   client app to read. This exposes the identity to all other apps.
 * - Because email is not face-to-face, it may be less likely that users
 *   will perform a sound, out-of-bound fingerprint verification process.
 *
 * There's a warning prompt before the action is taken.
 * TODO: consider disabling the option entirely if the user selects an "unlinkable" posture.
 */
public class ExportIdentity {

    private static final String LOG_TAG = "Export Identity";

    private static final String PUBLIC_STORAGE_DIRECTORY = "Ploggy";
    // TODO: per-persona filenames?
    private static final String IDENTITY_FILENAME = "identity.ploggy";

    public static void composeEmail(Context context) {
        final Context finalContext = context;
        new AlertDialog.Builder(finalContext)
            .setTitle(finalContext.getString(R.string.label_email_self_title))
            .setMessage(finalContext.getString(R.string.label_email_self_message))
            .setPositiveButton(finalContext.getString(R.string.label_email_self_positive),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                String json =
                                        Json.toJson(Data.getInstance().getSelfOrThrow().mPublicIdentity);
                                String payload = URLEncoder.encode(json, "UTF-8");

                                String link = ActivityAddFriend.ADD_FRIEND_IDENTITY_LINK_PREFIX + payload;

                                String body = finalContext.getString(R.string.identity_email_body)
                                                          .replace("href=\"\"", "href=\""+link+"\"");

                                Intent intent = new Intent(Intent.ACTION_SEND, Uri.parse("mailto:"));
                                intent.setType("text/html");
                                intent.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(body));
                                finalContext.startActivity(intent);
                            } catch (IOException e) {
                                Log.addEntry(LOG_TAG, e.getMessage());
                                Log.addEntry(LOG_TAG, "failed to compose email with identity link");
                            } catch (ActivityNotFoundException e) {
                                Log.addEntry(LOG_TAG, e.getMessage());
                                Log.addEntry(LOG_TAG, "failed to compose email with identity link");
                            } catch (PloggyError e) {
                                Log.addEntry(LOG_TAG, "failed to compose email with identity link");
                            }
                        }
                    })
            .setNegativeButton(finalContext.getString(R.string.label_email_self_negative), null)
            .show();
    }

    public static void saveIdentityToFile(Activity context) {
        final Activity finalContext = context;
        new AlertDialog.Builder(finalContext)
            .setTitle(finalContext.getString(R.string.label_save_identity_to_file_title))
            .setMessage(finalContext.getString(R.string.label_save_identity_to_file_message))
            .setPositiveButton(finalContext.getString(R.string.label_save_identity_to_file_positive),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                File directory = new File(Environment.getExternalStorageDirectory(), PUBLIC_STORAGE_DIRECTORY);
                                directory.mkdirs();
                                File attachmentFile = new File(directory, IDENTITY_FILENAME);

                                Utils.writeStringToFile(
                                        Json.toJson(
                                                Data.getInstance().getSelfOrThrow().mPublicIdentity),
                                        attachmentFile);

                                String toast = finalContext.getString(
                                                    R.string.toast_identity_saved_to_file,
                                                    IDENTITY_FILENAME,
                                                    PUBLIC_STORAGE_DIRECTORY);
                                Toast.makeText(finalContext, toast, Toast.LENGTH_LONG).show();
                            } catch (IOException e) {
                                Log.addEntry(LOG_TAG, e.getMessage());
                                Log.addEntry(LOG_TAG, "failed to save identity to file");
                            } catch (ActivityNotFoundException e) {
                                Log.addEntry(LOG_TAG, e.getMessage());
                                Log.addEntry(LOG_TAG, "failed to save identity to file");
                            } catch (PloggyError e) {
                                Log.addEntry(LOG_TAG, "failed to save identity to file");
                            }
                        }
                    })
            .setNegativeButton(finalContext.getString(R.string.label_save_identity_to_file_negative), null)
            .show();
    }
}
