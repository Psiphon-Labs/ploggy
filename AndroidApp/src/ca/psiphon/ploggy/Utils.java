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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.FileObserver;
import android.os.Handler;
import android.util.Base64;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import de.schildbach.wallet.util.LinuxSecureRandom;

/**
 * Utility functions
 */
public class Utils {

    private static final String LOG_TAG = "Utils";

    public static class ApplicationError extends Exception {
        private static final long serialVersionUID = -3656367025650685613L;

        public ApplicationError(String tag, String message) {
            if (tag != null) {
                Log.addEntry(tag, message);
            }
        }

        public ApplicationError(String tag, Exception e) {
            // TODO: require message param as well?
            super(e);
            String message = e.getLocalizedMessage();
            if (message == null) {
                message = "(null)";
            }
            Log.addEntry(tag, String.format("%s: %s", e.getClass().toString(), message));
            // TODO: log stack trace?
        }
    }

    public static void writeStringToFile(String data, File file) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        } finally {
            fileOutputStream.close();
        }
    }

    public static String readFileToString(File file) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        try {
            return readInputStreamToString(fileInputStream);
        } finally {
            fileInputStream.close();
        }
    }

    public static int readFileToInt(File file) throws IOException {
        try {
            return Integer.parseInt(Utils.readFileToString(file).trim());
        } catch (NumberFormatException e) {
            throw new IOException(e);
        }
    }

    public static String readInputStreamToString(InputStream inputStream) throws IOException {
        return new String(readInputStreamToBytes(inputStream), "UTF-8");
    }

    public static byte[] readFileToBytes(File file) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        try {
            return readInputStreamToBytes(fileInputStream);
        } finally {
            fileInputStream.close();
        }
    }

    public static byte[] readInputStreamToBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int readCount;
        byte[] buffer = new byte[16384];
        while ((readCount = inputStream.read(buffer, 0, buffer.length)) != -1) {
            outputStream.write(buffer, 0, readCount);
        }
        outputStream.flush();
        return outputStream.toByteArray();
    }

    public static void copyStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        try {
            byte[] buffer = new byte[16384];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0 , length);
            }
        } finally {
            inputStream.close();
            outputStream.close();
        }
    }

    public static class NullOutputStream extends OutputStream {
        @Override
        public void write(int arg0) throws IOException {
        }

        @Override
        public void write(byte[] b) throws IOException {
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
        }
    }

    public static void discardStream(InputStream inputStream) throws IOException {
        copyStream(inputStream, new NullOutputStream());
    }

    public static InputStream makeInputStream(String input) throws Utils.ApplicationError {
        try {
            return new ByteArrayInputStream(input.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    public static class FileInitializedObserver extends FileObserver {
        private final CountDownLatch mLatch;
        private final ArrayList<String> mTargetFilenames;

        public FileInitializedObserver(File directory, String ... filenames) {
            // MOVED_TO is required for the Tor case where the Tor process creates <target>.tmp,
            // writes to that file, then renames to <target>. There's no CLOSE_WRITE event for <target>.
            super(
                directory.getAbsolutePath(),
                FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE);
            mTargetFilenames = new ArrayList<String>(Arrays.asList(filenames));
            mLatch = new CountDownLatch(mTargetFilenames.size());
        }

        @Override
        public void onEvent(int event, String path) {
            if (path != null) {
                for (int i = 0; i < mTargetFilenames.size(); i++) {
                    if (path.equals(mTargetFilenames.get(i))) {
                        mTargetFilenames.remove(i);
                        mLatch.countDown();
                        if (mTargetFilenames.size() == 0) {
                            stopWatching();
                        }
                        break;
                    }
                }
            }
        }

        public boolean await(long timeoutMilliseconds) throws InterruptedException {
            return mLatch.await(timeoutMilliseconds, TimeUnit.MILLISECONDS);
        }
    }

    public static void initSecureRandom() {
        new LinuxSecureRandom();
    }

    public static byte[] getRandomBytes(int byteCount) {
        byte[] buffer = new byte[byteCount];
        new SecureRandom().nextBytes(buffer);
        return buffer;
    }

    public static String encodeBase64(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public static byte[] decodeBase64(String data) throws Utils.ApplicationError {
        try {
            return Base64.decode(data, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    public static String formatFingerprint(byte[] fingerprintBytes) {
        // Adapted from: http://stackoverflow.com/questions/332079/in-java-how-do-i-convert-a-byte-array-to-a-string-of-hex-digits-while-keeping-l
        char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
        char[] chars = new char[fingerprintBytes.length * 3 - 1];
        for (int i = 0; i < fingerprintBytes.length; i++)  {
            chars[i*3] = hexArray[(fingerprintBytes[i] & 0xFF)/16];
            chars[i*3 + 1] = hexArray[(fingerprintBytes[i] & 0xFF)%16];
            if (i < fingerprintBytes.length - 1) {
                chars[i*3 + 2] = ':';
            }
        }
        return new String(chars);
    }

    public static int calculateLocationDistanceInMeters(
            double latitudeA,
            double longitudeA,
            double latitudeB,
            double longitudeB) {
        float[] results = new float[1];
        Location.distanceBetween(latitudeA, longitudeA, latitudeB, longitudeB, results);
        return Math.round(results[0]);
    }

    public static String formatDistance(Context context, int distanceInMeters) {
        if (distanceInMeters < 1000) {
            return context.getString(
                    R.string.format_distance_meters,
                    NumberFormat.getInstance().format(distanceInMeters));
        } else {
            double distanceInKilometers = distanceInMeters/1000.0;
            return context.getString(
                    R.string.format_distance_kilometers,
                    NumberFormat.getInstance().format(distanceInKilometers));
        }
    }

    public static class DateFormatter {

        private static DateFormat mShortTimeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);
        private static DateFormat mWeekdayFormat = new SimpleDateFormat("EEE", Locale.getDefault());
        private static DateFormat mMonthFormat = new SimpleDateFormat("MMM", Locale.getDefault());
        private static DateFormat mMonthDayFormat = new SimpleDateFormat("d", Locale.getDefault());
        private static DateFormat mYearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());

        private static long MILLIS_IN_SEC = 1000;
        private static long MILLIS_IN_MINUTE = 60 * MILLIS_IN_SEC;
        private static long MILLIS_IN_HOUR = 60 * MILLIS_IN_MINUTE;


        /**
         * Returns a human-readable form of the given date-time. It will be relative
         * to now or absolute, depending on how long ago it is.
         * @param context    The `Context` to use for locale and resource access.
         * @param startDate  The date-time to render.
         * @param ago        Add the word "ago" to relative forms.
         * @return  The formatted string representing the date-time.
         */
        public static String formatRelativeDatetime(Context context, Date startDate, boolean ago) {
            return formatRelativeDatetime(context, startDate, new Date(), ago);
        }

        /**
         * Returns a human-readable form of the given date-time. It will be relative
         * to `endDate` or absolute, depending on how long ago it is.
         * @param context    The `Context` to use for resource access.
         * @param startDate  The date-time to render.
         * @param endDate    The date-time that `startDate` will be considered relative to.
         * @param ago        Add the word "ago" to relative forms.
         * @return  The formatted string representing the date-time.
         */
        public static String formatRelativeDatetime(Context context, Date startDate, Date endDate, boolean ago) {
            Resources res = context.getResources();
            long diffMS = endDate.getTime() - startDate.getTime();

            // Within a minute
            if (diffMS < MILLIS_IN_MINUTE) {
                int secs = (int)(diffMS / MILLIS_IN_SEC);
                return res.getQuantityString(
                            ago ? R.plurals.period_seconds_ago_abbrev : R.plurals.period_seconds_abbrev,
                            secs, secs);
            }

            // Within an hour
            if (diffMS < MILLIS_IN_HOUR) {
                int mins = (int)(diffMS / MILLIS_IN_MINUTE);
                return res.getQuantityString(
                            ago ? R.plurals.period_minutes_ago_abbrev : R.plurals.period_minutes_abbrev,
                            mins, mins);
            }

            // If we haven't returned yet, we're going to need Calendar objects
            Calendar startCal = new GregorianCalendar();
            startCal.setTimeInMillis(startDate.getTime());

            Calendar endCal = new GregorianCalendar();
            endCal.setTimeInMillis(endDate.getTime());

            // Same day
            if (startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR)
                    && startCal.get(Calendar.DAY_OF_YEAR) == endCal.get(Calendar.DAY_OF_YEAR)) {
                return mShortTimeFormat.format(startDate);
            }

            // Same week
            if (startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR)
                    && startCal.get(Calendar.WEEK_OF_YEAR) == endCal.get(Calendar.WEEK_OF_YEAR)) {
                return res.getString(
                        R.string.diff_day_same_week_datetime,
                        mWeekdayFormat.format(startDate),
                        mShortTimeFormat.format(startDate));
            }

            // Same year
            if (startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR)) {
                return res.getString(
                        R.string.same_year_datetime,
                        mMonthFormat.format(startDate),
                        mMonthDayFormat.format(startDate),
                        mShortTimeFormat.format(startDate));
            }

            // Older than the same year
            return res.getString(
                    R.string.older_datetime,
                    mMonthFormat.format(startDate),
                    mMonthDayFormat.format(startDate),
                    mYearFormat.format(startDate),
                    mShortTimeFormat.format(startDate));
        }
    }

    public static void shutdownExecutorService(ExecutorService threadPool) {
        try
        {
            threadPool.shutdown();
            if (!threadPool.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                threadPool.shutdownNow();
                threadPool.awaitTermination(100, TimeUnit.MILLISECONDS);
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    public static class FixedDelayExecutor {
        private final Handler mHandler;
        private Runnable mExecutorTask;
        private final Runnable mTask;
        private final int mDelayInMilliseconds;

        public FixedDelayExecutor(Runnable task, int delayInMilliseconds) {
            mHandler = new Handler();
            mTask = task;
            mDelayInMilliseconds = delayInMilliseconds;
        }

        public void start() {
            stop();
            mExecutorTask = new Runnable() {
                @Override
                public void run() {
                    mTask.run();
                    mHandler.postDelayed(mExecutorTask, mDelayInMilliseconds);
                }
            };
            mHandler.postDelayed(mExecutorTask, mDelayInMilliseconds);
        }

        public void stop() {
            if (mExecutorTask != null) {
                mHandler.removeCallbacks(mExecutorTask);
                mExecutorTask = null;
            }
        }
    }

    // Converts a Iterator<String> to an InputStream
    public static class StringIteratorInputStream extends InputStream {

        Iterator<String> mStringIterator;
        String mCurrentString;
        int mIndex;

        StringIteratorInputStream(Iterator<String> stringIterator) {
            mStringIterator = stringIterator;
            mCurrentString = null;
        }

        @Override
        public int read() throws IOException {
            if (mCurrentString == null) {
                if (!mStringIterator.hasNext()) {
                    return -1;
                }
                mCurrentString = mStringIterator.next();
                mIndex = 0;
            }
            int result = mCurrentString.charAt(mIndex);
            mIndex++;
            if (mIndex >= mCurrentString.length()) {
                mCurrentString = null;
            }
            return result;
        }
    }

    private static Context mApplicationContext;

    public static void setApplicationContext(Context context) {
        mApplicationContext = context;
    }

    public static Context getApplicationContext() {
        return mApplicationContext;
    }

    public static void hideKeyboard(Activity activity) {
        if (activity == null) {
            return;
        }
        View currentFocusView = activity.getCurrentFocus();
        if (currentFocusView == null) {
            return;
        }
        InputMethodManager inputManager =
                (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputManager == null) {
            return;
        }
        inputManager.hideSoftInputFromWindow(
                currentFocusView.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }

    public static boolean isConnectedNetworkWifi(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected() && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }
}
