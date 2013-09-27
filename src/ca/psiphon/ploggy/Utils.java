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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.SecureRandom;

import android.content.Context;

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

    public static String inputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder value = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            value.append(line);
        }
        return value.toString();
    }

    public static byte[] inputStreamToBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int readCount;
        byte[] buffer = new byte[16384];
        while ((readCount = inputStream.read(buffer, 0, buffer.length)) != -1) {
            outputStream.write(buffer, 0, readCount);
        }
        outputStream.flush();
        return outputStream.toByteArray();
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
    
    private static Context mApplicationContext;

    public static void setApplicationContext(Context context) {
        mApplicationContext = context;
    }
    
    public static Context getApplicationContext() {
        return mApplicationContext;
    }
}
