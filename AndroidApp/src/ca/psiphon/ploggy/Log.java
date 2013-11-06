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
import android.os.Handler;

/**
 * Logging facility.
 *
 * TODO: consider using Log4J or Logback (http://tony19.github.io/logback-android/)
 * 
 * Maintains a fixed-size queue of recent log entries. Posts events to observers of
 * recent entries using the main UI thread for compatibility with ListView Adapters.
 * 
 * Also maintains a persistent, JSON-format log is for important entries such as
 * added-friend. This persistent log is read back and posted to the recent queue on
 * start up.
 */
public class Log {
    
    private static final String LOG_TAG = "Log";

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
    
    public interface Observer {
        void onUpdatedRecentEntries();
    }

    private static final String LOG_FILE_NAME = "log";    
    private static final int MAX_RECENT_ENTRIES = 500;

    private static ArrayList<Entry> mRecentEntries;
    private static ArrayList<Observer> mObservers;
    private static Handler mHandler;
    
    // TODO: explicit singleton?
    
    public synchronized static void initialize() {
        mRecentEntries = readEntriesFromFile();
        mHandler = new Handler();
    }

    public synchronized static void addPersistentEntry(String tag, String message) {
        addEntry(true, tag, message);
    }

    public synchronized static void addEntry(String tag, String message) {
        addEntry(false, tag, message);
    }
   
    public synchronized static int getRecentEntryCount() {
        return mRecentEntries.size();
    }

    public synchronized static Entry getRecentEntry(int index) {
        return mRecentEntries.get(index);
    }
    
    public synchronized static void registerObserver(Observer observer) {
        if (!mObservers.contains(observer)) {
            mObservers.add(observer);
        }
    }
    
    public synchronized static void unregisterObserver(Observer observer) {
        mObservers.remove(observer);
    }
    
    private static void addEntry(boolean persist, String tag, String message) {
        if (message == null) {
            message = "(null)";
        }
        
        Entry entry = new Entry(tag, message);

        if (persist) {
            try {
                appendEntryToFile(entry);
            } catch (IOException e) {
                // TODO: ...
            }
        }
        
        // Update the in-memory entry list on the UI thread (also
        // notifies any ListView adapters subscribed to that list)
        postAddEntry(entry);

        // TODO: temp
        android.util.Log.e(tag, message);
    }

    private static void postAddEntry(Entry entry) {
        final Entry finalEntry = entry;
        mHandler.post(
            new Runnable() {
                @Override
                public void run() {
                    mRecentEntries.add(finalEntry);
                    while (mRecentEntries.size() > MAX_RECENT_ENTRIES) {
                        mRecentEntries.remove(0);
                    }
                    for (Observer observer : mObservers) {
                        observer.onUpdatedRecentEntries();
                    }
                }
            });
    }
    
    private static ArrayList<Entry> readEntriesFromFile() {
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
    
    private static void appendEntryToFile(Entry entry) throws IOException {
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
