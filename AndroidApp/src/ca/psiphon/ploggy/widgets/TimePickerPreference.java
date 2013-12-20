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

/*
 * TimePickerPreference:
 *
 *   http://code.google.com/p/android-my-time/source/browse/trunk/src/android/preference/TimePickerPreference.java
 *   License: Apache 2.0
 */

package ca.psiphon.ploggy.widgets;

import android.content.Context;
import android.content.DialogInterface;
import android.preference.DialogPreference;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TimePicker;

/**
 * Preference with a TimePicker saving a HH:mm string.
 *
 * @author Dag Rende
 */
public class TimePickerPreference extends DialogPreference implements
                TimePicker.OnTimeChangedListener {

        /**
         * The validation expression for this preference
         */
        private static final String VALIDATION_EXPRESSION = "[0-2]*[0-9]:[0-5]*[0-9]";

        /**
         * The default value for this preference
         */
        private final String defaultValue;
        private String result;
        private TimePicker tp;

        /**
         * @param context
         * @param attrs
         */
        public TimePickerPreference(Context context, AttributeSet attrs) {
                super(context, attrs);
                defaultValue = attrs.getAttributeValue("http://schemas.android.com/apk/res/android", "defaultValue");
                initialize();
        }

        /**
         * @param context
         * @param attrs
         * @param defStyle
         */
        public TimePickerPreference(Context context, AttributeSet attrs,
                        int defStyle) {
                super(context, attrs, defStyle);
                defaultValue = attrs.getAttributeValue("http://schemas.android.com/apk/res/android", "defaultValue");
                initialize();
        }

        /**
         * Initialize this preference
         */
        private void initialize() {
                setPersistent(true);
        }

        /*
         * (non-Javadoc)
         *
         * @see android.preference.DialogPreference#onCreateDialogView()
         */
        @Override
        protected View onCreateDialogView() {

                tp = new TimePicker(getContext());
                tp.setOnTimeChangedListener(this);

                String value = getPersistedString(this.defaultValue);
                int h = getHour(value);
                int m = getMinute(value);
                if (h >= 0 && m >= 0) {
                        tp.setCurrentHour(h);
                        tp.setCurrentMinute(m);
                }
                tp.setIs24HourView(DateFormat.is24HourFormat(getContext()));

                return tp;
        }

        /*
         * (non-Javadoc)
         *
         * @see
         * android.widget.TimePicker.OnTimeChangedListener#onTimeChanged(android
         * .widget.TimePicker, int, int)
         */

        @Override
        public void onTimeChanged(TimePicker view, int hour, int minute) {
                result = hour + ":" + minute;
        }

        /*
         * (non-Javadoc)
         *
         * @see
         * android.preference.DialogPreference#onDismiss(android.content.DialogInterface
         * )
         */
        @Override
        public void onDismiss(DialogInterface dialog) {
                super.onDismiss(dialog);
        }

        /*
         * (non-Javadoc)
         *
         * @see android.preference.DialogPreference#onDialogClosed(boolean)
         */
        @Override
        protected void onDialogClosed(boolean positiveResult) {
                super.onDialogClosed(positiveResult);
                if (positiveResult) {
                        tp.clearFocus();        // to get value of number if edited in text field, and clicking OK without clicking outside the field first (bug in NumberPicker)
                        result = tp.getCurrentHour() + ":" + tp.getCurrentMinute();
                        persistString(result);
                        callChangeListener(result);
                }
        }

        /**
         * Get the hour value (in 24 hour time)
         *
         * @return The hour value, will be 0 to 23 (inclusive)
         */
        public static int getHour(String value) {
                if (value == null || !value.matches(VALIDATION_EXPRESSION)) {
                        return -1;
                }

                return Integer.valueOf(value.split(":|/")[0]);
        }

        /**
         * Get the minute value
         *
         * @return the minute value, will be 0 to 59 (inclusive)
         */
        public static int getMinute(String value) {
                if (value == null || !value.matches(VALIDATION_EXPRESSION)) {
                        return -1;
                }

                return Integer.valueOf(value.split(":|/")[1]);
        }
}
