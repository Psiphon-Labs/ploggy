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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import android.content.Context;

/**
 * Logging facility.
 *
 * A persistent log is maintained, for important events such as added-friend. Also
 * maintains an insensitive log, omitting PII, that could be sent, with user constent
 * as diagnostic feedback.
 * Supports the log view in the main activity: supports reading back older logs
 * from disk, as well as posting a bus event when a log is added.
 */
public class Log {
    
    private static final String LOG_TAG = "Log";

    private static final String LOG_FILE_NAME = "log";    
    
    public static class Entry {
        public final Date mTimestamp;
        public final String mTag;
        public final String mMessage;

        public Entry(String tag, String message) {
            mTimestamp = new Date();
            mTag = tag;
            mMessage = message;
        }
    }

    public static void addEntry(String tag, String message) {
        if (message == null) {
            message = "(null)";
        }
        
        Entry entry = new Entry(tag, message);

        // TODO: consider using http://tony19.github.io/logback-android/
        // TODO: only persist important entries -- added friend, etc.
        //       maintain two files:
        //       1. permanent logs (with sensitive info)
        //       2. session logs (with no sensitive info -- can be sent as diagnostics)
        //       session log rotates (or use ring buffer structure)
        //       readEntries merges both files
        try {
            appendEntryToFile(entry);
        } catch (IOException e) {
            // TODO: ...
        }
        
        Events.post(new Events.LoggedEntry(entry));

        // TODO: temp
        android.util.Log.e(tag, message);
    }

    public synchronized static ArrayList<Entry> readEntries() {
        FileInputStream inputStream = null;
        try {
            Context context = Utils.getApplicationContext();
            inputStream = context.openFileInput(LOG_FILE_NAME);
            return Json.fromJsonStream(inputStream, Entry.class);
        } catch (FileNotFoundException e) {
            return new ArrayList<Entry>();
        } catch (Utils.ApplicationError e) {
            ArrayList<Entry> array = new ArrayList<Entry>();
            array.add(new Entry(LOG_TAG, "failed to load log file"));
            return array;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // TODO: log (to log view at least)?
                }
            }
        }        
    }
    
    private synchronized static void appendEntryToFile(Entry entry) throws IOException {
        FileOutputStream outputStream = null;
        try {
            Context context = Utils.getApplicationContext();
            outputStream = context.openFileOutput(
                    LOG_FILE_NAME,
                    Context.MODE_PRIVATE|Context.MODE_APPEND);
            outputStream.write(Json.toJson(entry).getBytes());
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }        
    }
}