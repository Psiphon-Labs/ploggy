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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;

/**
 * Logging facility.
 *
 * TODO: consider using Log4J or Logback (http://tony19.github.io/logback-android/)
 * 
 * Maintains a fixed-size queue of recent log entries. Posts events to observers of
 * recent entries using the main UI thread for compatibility with ListView Adapters.
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

    private static final int MAX_RECENT_ENTRIES = 500;

    private static ArrayList<Entry> mRecentEntries;
    private static ArrayList<Observer> mObservers;
    private static Handler mHandler;
    
    // TODO: explicit singleton?
    
    public synchronized static void initialize() {
        mRecentEntries = new ArrayList<Entry>();
        mObservers = new ArrayList<Observer>();
        mHandler = new Handler();
    }

    public synchronized static void addEntry(String tag, String message) {
        if (message == null) {
            message = "(null)";
        }
        
        Entry entry = new Entry(tag, message);

        // Update the in-memory entry list on the UI thread (also
        // notifies any ListView adapters subscribed to that list)
        postAddEntry(entry);
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

    public synchronized static void composeEmail(Context context) {
        // TODO: temporary feature for debugging prototype -- will compromise unlinkability
        try {
            StringBuilder body = new StringBuilder();
            for (Entry entry : mRecentEntries) {
                body.append(entry.mTimestamp);
                body.append(" ");
                body.append(entry.mTag);
                body.append(": ");
                body.append(entry.mMessage);
                body.append("\n");
            }
            
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("message/rfc822");
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"feedback+ploggy@psiphon.ca"});
            intent.putExtra(Intent.EXTRA_SUBJECT, "Ploggy Logs");
            intent.putExtra(Intent.EXTRA_TEXT, body.toString());
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.addEntry(LOG_TAG, e.getMessage());
            Log.addEntry(LOG_TAG, "compose log email failed");
        }
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
}
