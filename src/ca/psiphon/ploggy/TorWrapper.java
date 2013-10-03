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
import java.util.zip.ZipInputStream;

import net.freehaven.tor.control.ConfigEntry;
import net.freehaven.tor.control.TorControlConnection;

import android.content.Context;
import android.os.Build;

public class TorWrapper implements net.freehaven.tor.control.EventHandler {
    
    private static final String LOG_TAG = "Tor";

    public enum Mode {
        MODE_GENERATE_KEY_MATERIAL,
        MODE_RUN_SERVICES
    }

    private Mode mMode;
    private HiddenService.KeyMaterial mKeyMaterial;
    private int mWebServerPort = -1;
    private File mRootDirectory;
    private File mDataDirectory;
    private File mHiddenServiceDirectory;
    private File mExecutableFile;
    private File mConfigFile;
    private File mControlPortFile;
    private File mControlAuthCookieFile;
    private File mPidFile;
    private File mHiddenServiceHostnameFile;
    private File mHiddenServicePrivateKeyFile;

    private Process mProcess = null;
    private int mPid = -1;
    private int mControlPort = -1;
    private int mSocksProxyPort = -1;
    private Socket mControlSocket = null;
    private TorControlConnection mControlConnection = null;
    
    private static final int CONTROL_INITIALIZED_TIMEOUT_MILLISECONDS = 5000;
    private static final int HIDDEN_SERVICE_INITIALIZED_TIMEOUT_MILLISECONDS = 30000;
    
    public TorWrapper(Mode mode) {
        this(mode, null, -1);
    }

    public TorWrapper(Mode mode, HiddenService.KeyMaterial keyMaterial, int webServerPort) {
        // TODO: ...supports only one instance per mode at once
        mMode = mode;
        mKeyMaterial = keyMaterial;
        mWebServerPort = webServerPort;
        Context context = Utils.getApplicationContext();
        String rootDirectory = String.format((Locale)null, "tor-%s", mMode.toString());
        mRootDirectory = context.getDir(rootDirectory, Context.MODE_PRIVATE);
        mDataDirectory = new File(mRootDirectory, "data");
        mHiddenServiceDirectory = new File(mRootDirectory, "hidden_service");
        mExecutableFile = new File(mRootDirectory, "tor");
        mConfigFile = new File(mRootDirectory, "config");
        mControlPortFile = new File(mDataDirectory, "control_port_file");
        mControlAuthCookieFile = new File(mDataDirectory, "control_auth_cookie");
        mPidFile = new File(mDataDirectory, "pid");
        mHiddenServiceHostnameFile = new File(mHiddenServiceDirectory, "hostname");
        mHiddenServicePrivateKeyFile = new File(mHiddenServiceDirectory, "private_key");
    }

    public void start() throws Utils.ApplicationError {
        if (mMode == Mode.MODE_GENERATE_KEY_MATERIAL) {
            startGenerateKeyMaterial();
        } else if (mMode == Mode.MODE_RUN_SERVICES) {
            startRunServices();
        }
    }
    
    private void startGenerateKeyMaterial() throws Utils.ApplicationError {
        stop();
        try {
            // TODO: don't need two copies of the executable
            writeExecutableFile();
            writeGenerateKeyMaterialConfigFile();
            mHiddenServiceDirectory.mkdirs();
            if (mHiddenServiceHostnameFile.exists() && !mHiddenServiceHostnameFile.delete()) {
                Log.addEntry(LOG_TAG, "Failed to delete existing hidden service hostname file");
                throw new Utils.ApplicationError();
            }
            if (mHiddenServicePrivateKeyFile.exists() && !mHiddenServicePrivateKeyFile.delete()) {
                Log.addEntry(LOG_TAG, "Failed to delete existing hidden service private key file");
                throw new Utils.ApplicationError();
            }
            Utils.FileInitializedObserver hiddenServiceInitializedObserver =
                    new Utils.FileInitializedObserver(
                            mHiddenServiceDirectory,
                            mHiddenServiceHostnameFile.getName(),
                            mHiddenServicePrivateKeyFile.getName());
            hiddenServiceInitializedObserver.startWatching();
            startDaemon();
            if (!hiddenServiceInitializedObserver.await(HIDDEN_SERVICE_INITIALIZED_TIMEOUT_MILLISECONDS)) {
                Log.addEntry(LOG_TAG, "Timeout waiting for Tor hidden service initialization");
                throw new Utils.ApplicationError();
            }
            String hostname = Utils.readFileToString(mHiddenServiceHostnameFile);
            String privateKey = Utils.readFileToString(mHiddenServicePrivateKeyFile);
            mKeyMaterial = new HiddenService.KeyMaterial(hostname, privateKey);
        } catch (IOException e) {
            Log.addEntry(LOG_TAG, "Error starting Tor: " + e.getLocalizedMessage());
            throw new Utils.ApplicationError(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stop();
            mHiddenServiceHostnameFile.delete();
            mHiddenServicePrivateKeyFile.delete();
        }
    }
    
    private void startRunServices() throws Utils.ApplicationError {
        stop();
        boolean startCompleted = false;
        try {
            writeExecutableFile();
            writeRunServicesConfigFile();
            writeHiddenServiceFiles();
            startDaemon();
            mSocksProxyPort = getPortValue(mControlConnection.getInfo("net/listeners/socks").replaceAll("\"", ""));
            startCompleted = true;
        } catch (IOException e) {
            Log.addEntry(LOG_TAG, "Error starting Tor: " + e.getLocalizedMessage());
            throw new Utils.ApplicationError(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (!startCompleted) {
                stop();
            }
        }
    }
    
    private void startDaemon() throws Utils.ApplicationError, IOException, InterruptedException {
        try {
            mControlAuthCookieFile.delete();
            Utils.FileInitializedObserver controlInitializedObserver =
                    new Utils.FileInitializedObserver(
                            mDataDirectory,
                            mPidFile.getName(),
                            mControlPortFile.getName(),
                            mControlAuthCookieFile.getName());
            controlInitializedObserver.startWatching();

            // TODO: --hush
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
            
            if (!controlInitializedObserver.await(CONTROL_INITIALIZED_TIMEOUT_MILLISECONDS)) {
                Log.addEntry(LOG_TAG, "Timeout waiting for Tor control initialization");
                throw new Utils.ApplicationError();
            }
                
            mPid = Utils.readFileToInt(mPidFile);
            mControlPort = getPortValue(Utils.readFileToString(mControlPortFile));
            mControlSocket = new Socket("127.0.0.1", mControlPort);
            mControlConnection = new TorControlConnection(mControlSocket);
            mControlConnection.authenticate(Utils.readFileToBytes(mControlAuthCookieFile));
            mControlConnection.setEventHandler(this);
            mControlConnection.setEvents(Arrays.asList("NOTICE", "WARN", "ERR"));
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
        
        if (mPid == -1 && mPidFile.exists()) {
            // TODO: use output of ps command when missing pid file...?
            try {
                mPid = Integer.parseInt(Utils.readFileToString(mPidFile).trim());
            } catch (IOException e) {
            }
        }
        if (mPid != -1) {
            android.os.Process.killProcess(mPid);
        }

        mSocksProxyPort = -1;
        mControlPort = -1;
        mControlConnection = null;
        mControlSocket = null;
        mProcess = null;
        mPid = -1;
    }
    
    public HiddenService.KeyMaterial getKeyMaterial() {
        return mKeyMaterial;
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
    
    private void writeGenerateKeyMaterialConfigFile() throws IOException {
        final String configuration =
                String.format(
                    (Locale)null,
                        "DataDirectory %s\n" +
                        "RunAsDaemon 1\n" +
                        "PidFile %s\n" +
                        "ControlPort auto\n" +
                        "ControlPortWriteToFile %s\n" +
                        "CookieAuthentication 1\n" +
                        "CookieAuthFile %s\n" +
                        "SocksPort 0\n" +
                        // TODO: won't generate without HiddenServicePort set... prevent publish?
                        //"HiddenServiceDir %s\n",
                        "HiddenServiceDir %s\nHiddenServicePort 443 127.0.0.1:8443\n",                        
                    mDataDirectory.getAbsolutePath(),
                    mPidFile.getAbsolutePath(),
                    mControlPortFile.getAbsolutePath(),
                    mControlAuthCookieFile.getAbsolutePath(),
                    mHiddenServiceDirectory.getAbsolutePath());
                
        Utils.copyStream(
                new ByteArrayInputStream(configuration.getBytes("UTF-8")),
                new FileOutputStream(mConfigFile));
    }
    
    private void writeRunServicesConfigFile() throws IOException {
        final String configuration =
                String.format(
                    (Locale)null,
                        "DataDirectory %s\n" +
                        "RunAsDaemon 1\n" +
                        "PidFile %s\n" +
                        "ControlPort auto\n" +
                        "ControlPortWriteToFile %s\n" +
                        "CookieAuthentication 1\n" +
                        "CookieAuthFile %s\n" +
                        "SocksPort auto\n" +
                        "HiddenServiceDir %s\n" +
                        "HiddenServicePort 443 127.0.0.1:%d\n",
                    mDataDirectory.getAbsolutePath(),
                    mPidFile.getAbsolutePath(),
                    mControlPortFile.getAbsolutePath(),
                    mControlAuthCookieFile.getAbsolutePath(),
                    mHiddenServiceDirectory.getAbsolutePath(),
                    mWebServerPort);
                
        Utils.copyStream(
                new ByteArrayInputStream(configuration.getBytes("UTF-8")),
                new FileOutputStream(mConfigFile));
    }
    
    private void writeHiddenServiceFiles() throws IOException {
        mHiddenServiceDirectory.mkdirs();
        Utils.writeStringToFile(mKeyMaterial.mHostname, mHiddenServiceHostnameFile);
        Utils.writeStringToFile(mKeyMaterial.mPrivateKey, mHiddenServicePrivateKeyFile);
    }

    private int getPortValue(String data) throws Utils.ApplicationError {
        try {
            // TODO: ...PORT=127.0.0.1:<port>
            String[] tokens = data.split(":");
            if (tokens.length != 2) {
                Log.addEntry(LOG_TAG, "Unexpected port value format");
                throw new Utils.ApplicationError();                
            }
            return Integer.parseInt(tokens[1]);
        } catch (NumberFormatException e) {
            Log.addEntry(LOG_TAG, "Unexpected port value format");
            throw new Utils.ApplicationError(e);
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
