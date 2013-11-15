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
import android.widget.Toast;

/**
 * User interface for displaying friend status details.
 */
public class ActivityFriendStatusDetails extends ActivitySendIdentityByNfc {
    
    private static final String LOG_TAG = "Friend Status Details";
    
    public static final String FRIEND_ID_BUNDLE_KEY = "friendId";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status_details);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            String friendId = bundle.getString(FRIEND_ID_BUNDLE_KEY);
    
            FragmentStatusDetails statusDetails = (FragmentStatusDetails)
                    getFragmentManager().findFragmentById(R.id.status_details_fragment);
    
            if (friendId != null && statusDetails != null && !statusDetails.isInLayout()) {
                try {
                    statusDetails.show(friendId);
                    return;
                } catch (Data.DataNotFoundError e) {
                    Toast toast = Toast.makeText(this, getString(R.string.prompt_status_details_data_not_found), Toast.LENGTH_SHORT);
                    toast.show();

                } catch (Utils.ApplicationError e) {
                    // fall through to the end...
                }
            }            
        }
        
        // ...reaching this point means we failed
        Log.addEntry(LOG_TAG, "failed to display friend status details");
        finish();
    }
}
