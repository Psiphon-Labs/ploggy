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

import java.util.Map;

import ca.psiphon.ploggy.widgets.TimePickerPreference;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

/**
 * Activity to display SharedPreferences (user settings).
 * 
 * Much of the preferences logic is driven by preferences.xml with UI code is required.
 * TimePicker- and SeekBar-based preferences are implemented with custom widgets, and this
 * Activity provides support for updating the summaries for TimePicker preferences.
 */
public class ActivitySettings extends ActivitySendIdentityByNfc {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).commit();
    }
    
    public static class SettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // TODO: distinct instance of preferences for each persona
            // e.g., getPreferenceManager().setSharedPreferencesName("persona1");
            
            addPreferencesFromResource(R.xml.preferences);

            initTimePickerPreferences();
        }
        
        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            setTimePickerPreferenceSummary(sharedPreferences, key);
        }        

        private void initTimePickerPreferences() {
            SharedPreferences sharedPreferences = this.getPreferenceManager().getSharedPreferences();
            for (Map.Entry<String, ?> entry : sharedPreferences.getAll().entrySet()) {
                setTimePickerPreferenceSummary(sharedPreferences, entry.getKey());
            }            
        }
        
        private void setTimePickerPreferenceSummary(SharedPreferences sharedPreferences, String key) {
            Preference preference = findPreference(key);
            if (preference instanceof TimePickerPreference) {
                TimePickerPreference timePickerPreference = (TimePickerPreference)preference;
                timePickerPreference.setSummary(sharedPreferences.getString(key, ""));
            }            
        }
    }
}
