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
 * Based on : briar-android/src/net/sf/briar/plugins/tor/TorPluginFactory.java
 * 
 * Copyright (C) 2013 Sublime Software Ltd
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version. 
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */ 

package ca.psiphon.ploggy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;

import net.freehaven.tor.control.TorControlConnection;

import android.content.Context;
import android.os.Build;
import android.os.FileObserver;

public class TorWrapper implements net.freehaven.tor.control.EventHandler {

    public static final String SOCKS_PROXY_HOSTNAME = "127.0.0.1";

    private HiddenService.KeyMaterial mHiddenServiceKeyMaterial;
	private int mWebServerPort;
    private int mControlServerPort;
    private int mSocksProxyPort;
	private File mRootDirectory;
	private File mExecutableFile;
	private File mConfigFile;
	private File mControlAuthCookieFile;
	private File mPidFile;
	private File mHostnameFile;

    private Process mProcess = null;
    private int mPid = -1;
    private Socket mControlSocket = null;
    private TorControlConnection mControlConnection = null;
	
    private static final String LOG_TAG = "Tor";
    private static final int COMMAND_AUTH_COOKIE_TIMEOUT_MILLISECONDS = 5000;
    
	public TorWrapper(HiddenService.KeyMaterial hiddenServiceKeyMaterial, int webServerPort) {
	    mHiddenServiceKeyMaterial = hiddenServiceKeyMaterial;
		mWebServerPort = webServerPort;
		Context context = Utils.getApplicationContext();
		mRootDirectory = context.getDir("tor", Context.MODE_PRIVATE);
        mExecutableFile = new File(mRootDirectory, "tor");
        mConfigFile = new File(mRootDirectory, "torrc");
        mControlAuthCookieFile = new File(mRootDirectory, ".tor/control_auth_cookie");
        mPidFile = new File(mRootDirectory, ".tor/pid");
        mHostnameFile = new File(mRootDirectory, "hostname");
    }
	
	public void start() throws Utils.ApplicationError {
        mControlServerPort = Utils.selectAvailableLocalPort(null);
        mSocksProxyPort = Utils.selectAvailableLocalPort(Arrays.asList(mControlServerPort));
	    try {
            writeExecutableFile();
            writeConfigFile();
            writeHiddenServiceFiles();

            mControlAuthCookieFile.mkdirs();
            mControlAuthCookieFile.createNewFile();
            CountDownLatch latch = new CountDownLatch(1);
            FileObserver fileObserver = new WriteObserver(mControlAuthCookieFile, latch);
            fileObserver.startWatching();

            ProcessBuilder processBuilder =
                    new ProcessBuilder(mExecutableFile.getAbsolutePath(), "-f", mConfigFile.getAbsolutePath());
            processBuilder.environment().put("HOME", mRootDirectory.getAbsolutePath());
            processBuilder.directory(mRootDirectory);
            mProcess = processBuilder.start();

            Scanner stdout = new Scanner(mProcess.getInputStream());
            while(stdout.hasNextLine()) {
                Log.addEntry(LOG_TAG, stdout.nextLine());
            }
            stdout.close();
            
            // TODO: i18n errors (string resources); combine logging with throwing Utils.ApplicationError

            int exit = mProcess.waitFor();
            if (exit != 0) {
                Log.addEntry(LOG_TAG, String.format("Tor exited with error %d", exit));
                throw new Utils.ApplicationError();
            }
            
            if (!latch.await(COMMAND_AUTH_COOKIE_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)) {
                Log.addEntry(LOG_TAG, "Timeout waiting for Tor command auth cookie");
                throw new Utils.ApplicationError();
            }
                
            mControlSocket = new Socket("127.0.0.1", mControlServerPort);

            // TODO: catch NumberFormatException
            mPid = Integer.parseInt(Utils.fileToString(mPidFile).trim());

            mControlConnection = new TorControlConnection(mControlSocket);
            mControlConnection.authenticate(Utils.fileToBytes(mControlAuthCookieFile));
            mControlConnection.setEventHandler(this);
            mControlConnection.setEvents(Arrays.asList("NOTICE", "WARN", "ERR"));
	    } catch (IOException e) {
            Log.addEntry(LOG_TAG, "Error starting Tor: " + e.getLocalizedMessage());
	        //throw new Utils.ApplicationError(e);
            throw new RuntimeException(e);
	    } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (mProcess != null) {
                try {
                    mProcess.getOutputStream().close();
                    mProcess.getInputStream().close();
                    mProcess.getErrorStream().close();
                } catch (IOException e) {                    
                }
            }
        }
	}
	
	public void stop() {
        try {
            if (mControlConnection != null) {
                mControlConnection.shutdownTor("TERM");
            }
            if (mControlSocket != null) {
                mControlSocket.close();
            }
        } catch (IOException e) {
            // TODO: log
        }
        if (mProcess != null) {
            mProcess.destroy();
        }
        if (mPid != -1) {
            android.os.Process.killProcess(mPid);
        }
        mControlConnection = null;
        mControlSocket = null;
        mProcess = null;
        mPid = -1;
	}
	
	public int getSocksProxyPort() {
	    return mSocksProxyPort;
	}

	private void writeExecutableFile() throws IOException {
	    if (0 != Build.CPU_ABI.compareTo("armeabi-v7a")) {
	        throw new IOException("no Tor binary for this CPU");
	    }

        mExecutableFile.delete();

        InputStream zippedAsset = Utils.getApplicationContext().getResources().openRawResource(R.raw.tor_arm7);
        ZipInputStream zipStream = new ZipInputStream(zippedAsset);            
        zipStream.getNextEntry();
        Utils.copyStream(zipStream, new FileOutputStream(mExecutableFile));

        if (!mExecutableFile.setExecutable(true)) {
            throw new IOException("failed to set Tor as executable");            
        }
	}

	private void writeConfigFile() throws IOException {
	    final String configuration =
	            String.format(
                    (Locale)null,
    	            "ControlPort %d\n" +
    	            "SocksPort %d\n" +
    	            "CookieAuthentication 1\n" +
    	            "PidFile pid\n" +
    	            "RunAsDaemon 1\n" +
    	            "SafeSocks 1\n",
	                mControlServerPort,
	                mSocksProxyPort);
	            
        Utils.copyStream(
                new ByteArrayInputStream(configuration.getBytes("UTF-8")),
                new FileOutputStream(mConfigFile));
	}
	
	private void writeHiddenServiceFiles() throws IOException {
	    // TODO: ...
	}

    private static class WriteObserver extends FileObserver {
        private final CountDownLatch latch;
        private WriteObserver(File file, CountDownLatch latch) {
            super(file.getAbsolutePath(), FileObserver.CLOSE_WRITE);
            this.latch = latch;
        }
        public void onEvent(int event, String path) {
            stopWatching();
            latch.countDown();
        }
    }

    @Override
    public void circuitStatus(String status, String circID, String path) {
        Log.addEntry(LOG_TAG, status);
    }

    @Override
    public void streamStatus(String status, String streamID, String target) {
        Log.addEntry(LOG_TAG, status);
    }

    @Override
    public void orConnStatus(String status, String orName) {
        Log.addEntry(LOG_TAG, status);
    }

    @Override
    public void bandwidthUsed(long read, long written) {
    }

    @Override
    public void newDescriptors(List<String> orList) {
    }

    @Override
    public void message(String severity, String msg) {
        Log.addEntry(LOG_TAG, msg);
    }

    @Override
    public void unrecognized(String type, String msg) {
        Log.addEntry(LOG_TAG, msg);
    }
}
