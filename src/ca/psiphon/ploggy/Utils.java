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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.os.FileObserver;

import de.schildbach.wallet.util.LinuxSecureRandom;

public class Utils {

    public static class ApplicationError extends Exception {
        private static final long serialVersionUID = -3656367025650685613L;

        public ApplicationError() {
        }

        public ApplicationError(Exception e) {
            super(e);
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

    public static String readInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder value = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            value.append(line);
        }
        return value.toString();
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
            // TODO: ...Tor creates <target>.tmp, writes, then renames
            // TODO: ...https://groups.google.com/forum/#!msg/android-developers/hk6c7lj0Ga0/aLNbOfxjF-oJ
            super(
                directory.getAbsolutePath(),
                FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE);
            mTargetFilenames = new ArrayList<String>(Arrays.asList(filenames));
            mLatch = new CountDownLatch(mTargetFilenames.size());
        }

        @Override
        public void onEvent(int event, String path) {
            if (path != null) {
                // TEMP: android.util.Log.e("TEMP", String.format("event: %d for %s", event, path));
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
    
    // from:
    // http://stackoverflow.com/questions/332079/in-java-how-do-i-convert-a-byte-array-to-a-string-of-hex-digits-while-keeping-l
    public static String byteArrayToHexString(byte[] bytes) {
        char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++)  {
            hexChars[i*2] = hexArray[(bytes[i] & 0xFF)/16];
            hexChars[i*2 + 1] = hexArray[(bytes[i] & 0xFF)%16];
        }
        return new String(hexChars);
    }

    public static String getRandomHexString(int bits) {
        byte[] buffer = new byte[bits/4];
        new SecureRandom().nextBytes(buffer);
        return byteArrayToHexString(buffer);
    }

    public static boolean isLocalPortAvailable(int port)
    {
        int timeoutMilliseconds = 50;
        Socket socket = new Socket();
        SocketAddress sockaddr = new InetSocketAddress("127.0.0.1", port);
        
        try  {
            socket.connect(sockaddr, timeoutMilliseconds);
            return false;
        }
        catch (SocketTimeoutException e) {
            return false;
        }
        catch (IOException e) {
            return true;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public static int selectAvailableLocalPort(List<Integer> exclude)
    {
        // TODO: ...race condition
        int startPort = 1024;
        int maxPort = 65534;
        
        for(int port = startPort; port <= maxPort; port++) {
            if (exclude != null && exclude.contains(port)) {
                continue;
            }
            if (isLocalPortAvailable(port)) {
                return port;
            }
        }

        return 0;
    }
    
    private static Context mApplicationContext;

    public static void setApplicationContext(Context context) {
        mApplicationContext = context;
    }
    
    public static Context getApplicationContext() {
        return mApplicationContext;
    }
}
