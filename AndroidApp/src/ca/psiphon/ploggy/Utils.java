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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.location.Location;
import android.os.FileObserver;
import android.util.Base64;

import de.schildbach.wallet.util.LinuxSecureRandom;

/**
 * Utility functions
 */
public class Utils {

    private static final String LOG_TAG = "Utils";
    
    public static class ApplicationError extends Exception {
        private static final long serialVersionUID = -3656367025650685613L;

        public ApplicationError(String tag, String message) {
            Log.addEntry(tag, message);
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
    
    public static class FileInitializedObserver extends FileObserver {
        private final CountDownLatch mLatch;
        private ArrayList<String> mTargetFilenames;

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
    
    public static String getRandomHexString(int bits) {
        byte[] buffer = new byte[bits/4];
        new SecureRandom().nextBytes(buffer);
        return encodeHex(buffer);
    }
    
    // From: http://stackoverflow.com/questions/332079/in-java-how-do-i-convert-a-byte-array-to-a-string-of-hex-digits-while-keeping-l
    public static String encodeHex(byte[] bytes) {
        char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++)  {
            hexChars[i*2] = hexArray[(bytes[i] & 0xFF)/16];
            hexChars[i*2 + 1] = hexArray[(bytes[i] & 0xFF)%16];
        }
        return new String(hexChars);
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

    public static int calculateLocationDistanceInMeters(
            double longitudeA,
            double latitudeA,
            double longitudeB,
            double latitudeB) {
        Location locationA = new Location("");
        locationA.setLongitude(longitudeA);
        locationA.setLatitude(latitudeA);
        Location locationB = new Location("");
        locationB.setLongitude(longitudeA);
        locationB.setLatitude(latitudeA);
        return Math.round(locationA.distanceTo(locationB));
    }
    
    public static String getISO8601String(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String dateStr = sdf.format(date);
        dateStr += "Z";
        return dateStr;
    }

    public static String getCurrentTimestamp() {
        return getISO8601String(new Date());
    }
    
    private static Context mApplicationContext;

    public static void setApplicationContext(Context context) {
        mApplicationContext = context;
    }
    
    public static Context getApplicationContext() {
        return mApplicationContext;
    }
}
