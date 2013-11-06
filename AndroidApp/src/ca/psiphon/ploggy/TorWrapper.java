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

/**
 * Wrapper for Tor child process. 
 *
 * Derived from Briar Project's TorPlugin.java. Also use's Briar project custom TorControlConnection.
 * Supports two modes: run Tor services (local SOCKS proxy for clients, Hidden Server in front of web
 * server); and generate key material only.
 * Allows Tor to select listen port for control interface and local SOCKS proxy. Uses file monitoring
 * to monitor initial startup of Tor process, and control interface for monitoring Tor bootstrap
 * progress. 
 * Supports multiple simultaneous Tor instances (for testing). Use distinct instance names for
 * simultaneous distinct, Tor instances, each with its own persistent data.
 */
public class TorWrapper implements net.freehaven.tor.control.EventHandler {
    
    private static final String LOG_TAG = "Tor";

    public enum Mode {
        MODE_GENERATE_KEY_MATERIAL,
        MODE_RUN_SERVICES
    }

    private Mode mMode;
    private String mInstanceName;
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

    private Thread mStartupThread = null;
    private Utils.ApplicationError mStartupError = null;
    private Process mProcess = null;
    private int mPid = -1;
    private int mControlPort = -1;
    private int mSocksProxyPort = -1;
    private Socket mControlSocket = null;
    private TorControlConnection mControlConnection = null;
    private CountDownLatch mCircuitEstablishedLatch = null;
    private static final int CONTROL_INITIALIZED_TIMEOUT_MILLISECONDS = 90000;
    private static final int HIDDEN_SERVICE_INITIALIZED_TIMEOUT_MILLISECONDS = 90000;
    private static final int CIRCUIT_ESTABLISHED_TIMEOUT_MILLISECONDS = 90000;
    
    public TorWrapper(Mode mode) {
        this(mode, null, -1);
    }

    public TorWrapper(Mode mode, HiddenService.KeyMaterial keyMaterial, int webServerPort) {
        this(mode, null, keyMaterial, webServerPort);
    }

    public TorWrapper(Mode mode, String instanceName, HiddenService.KeyMaterial keyMaterial, int webServerPort) {
        mMode = mode;
        mInstanceName = instanceName;
        if (mInstanceName == null) {
            mInstanceName = mMode.toString();
        }
        mKeyMaterial = keyMaterial;
        mWebServerPort = webServerPort;
        Context context = Utils.getApplicationContext();
        String rootDirectory = String.format((Locale)null, "tor-%s", mInstanceName);
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

    private String logTag() {
        return String.format("%s [%s]", LOG_TAG, mInstanceName);
    }
    
    public void start() {
        stop();
        // Performs start sequence asynchronously, in a background thread
        Runnable startTask = new Runnable() {
            public void run() {
                try {
                    if (mMode == Mode.MODE_GENERATE_KEY_MATERIAL) {
                        startGenerateKeyMaterial();
                    } else if (mMode == Mode.MODE_RUN_SERVICES) {
                        startRunServices();
                    }
                } catch (Utils.ApplicationError e) {
                    Log.addEntry(logTag(), "failed to start Tor");
                    // Save this to throw from awaitStarted
                    mStartupError = e;
                }
            }
        };
        mStartupThread = new Thread(startTask);
        mStartupThread.start();
    }
    
    public void awaitStarted() throws Utils.ApplicationError {
        if (mStartupThread != null) {
            try {
                mStartupThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (mStartupError != null) {
                throw mStartupError;
            }
        }
    }
    
    private void startGenerateKeyMaterial() throws Utils.ApplicationError {
        try {
            // TODO: don't need two copies of the executable
            writeExecutableFile();
            writeGenerateKeyMaterialConfigFile();
            mHiddenServiceDirectory.mkdirs();
            if (mHiddenServiceHostnameFile.exists() && !mHiddenServiceHostnameFile.delete()) {
                throw new Utils.ApplicationError(logTag(), "failed to delete existing hidden service hostname file");
            }
            if (mHiddenServicePrivateKeyFile.exists() && !mHiddenServicePrivateKeyFile.delete()) {
                throw new Utils.ApplicationError(logTag(), "failed to delete existing hidden service private key file");
            }
            Utils.FileInitializedObserver hiddenServiceInitializedObserver =
                    new Utils.FileInitializedObserver(
                            mHiddenServiceDirectory,
                            mHiddenServiceHostnameFile.getName(),
                            mHiddenServicePrivateKeyFile.getName());
            hiddenServiceInitializedObserver.startWatching();
            startDaemon(false);
            if (!hiddenServiceInitializedObserver.await(HIDDEN_SERVICE_INITIALIZED_TIMEOUT_MILLISECONDS)) {
                throw new Utils.ApplicationError(logTag(), "timeout waiting for Tor hidden service initialization");
            }
            String hostname = Utils.readFileToString(mHiddenServiceHostnameFile);
            String privateKey = Utils.readFileToString(mHiddenServicePrivateKeyFile);
            // Encoding these strings to retain formatting (newlines) across serialization/protocol
            mKeyMaterial = new HiddenService.KeyMaterial(
                    Utils.encodeBase64(hostname.getBytes()),
                    Utils.encodeBase64(privateKey.getBytes()));
        } catch (IOException e) {
            Log.addEntry(logTag(), "failed to start Tor");
            throw new Utils.ApplicationError(logTag(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stop();
            mHiddenServiceHostnameFile.delete();
            mHiddenServicePrivateKeyFile.delete();
        }
    }
    
    private void startRunServices() throws Utils.ApplicationError {
        boolean startCompleted = false;
        try {
            writeExecutableFile();
            writeRunServicesConfigFile();
            writeHiddenServiceFiles();
            startDaemon(true);
            mSocksProxyPort = getPortValue(mControlConnection.getInfo("net/listeners/socks").replaceAll("\"", ""));
            startCompleted = true;
        } catch (IOException e) {
            Log.addEntry(logTag(), "failed to start Tor");
            throw new Utils.ApplicationError(logTag(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (!startCompleted) {
                stop();
            }
        }
    }
    
    private void startDaemon(boolean awaitFirstCircuit) throws Utils.ApplicationError, IOException, InterruptedException {
        try {
            mDataDirectory.mkdirs();
            mCircuitEstablishedLatch = new CountDownLatch(1);
            mControlAuthCookieFile.delete();
            Utils.FileInitializedObserver controlInitializedObserver =
                    new Utils.FileInitializedObserver(
                            mDataDirectory,
                            mPidFile.getName(),
                            mControlPortFile.getName(),
                            mControlAuthCookieFile.getName());
            controlInitializedObserver.startWatching();

            ProcessBuilder processBuilder =
                    new ProcessBuilder(
                            mExecutableFile.getAbsolutePath(),
                            // TEMP: show all Tor output for prototype testing
                            //"--hush",
                            "-f", mConfigFile.getAbsolutePath());
            processBuilder.environment().put("HOME", mRootDirectory.getAbsolutePath());
            processBuilder.directory(mRootDirectory);
            mProcess = processBuilder.start();

            Scanner stdout = new Scanner(mProcess.getInputStream());
            while(stdout.hasNextLine()) {
                Log.addEntry(logTag(), stdout.nextLine());
            }
            stdout.close();
            
            // TODO: i18n errors (string resources); combine logging with throwing Utils.ApplicationError
            int exit = mProcess.waitFor();
            if (exit != 0) {
                throw new Utils.ApplicationError(logTag(), String.format("Tor exited with error %d", exit));
            }
            
            if (!controlInitializedObserver.await(CONTROL_INITIALIZED_TIMEOUT_MILLISECONDS)) {
                throw new Utils.ApplicationError(logTag(), "timeout waiting for Tor control initialization");
            }
                
            mPid = Utils.readFileToInt(mPidFile);
            mControlPort = getPortValue(Utils.readFileToString(mControlPortFile).trim());
            mControlSocket = new Socket("127.0.0.1", mControlPort);
            mControlConnection = new TorControlConnection(mControlSocket);
            mControlConnection.authenticate(Utils.readFileToBytes(mControlAuthCookieFile));
            mControlConnection.setEventHandler(this);
            mControlConnection.setEvents(Arrays.asList("STATUS_CLIENT", "WARN", "ERR"));

            if (awaitFirstCircuit) {
                mCircuitEstablishedLatch.await(CIRCUIT_ESTABLISHED_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
            }
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
        if (mStartupThread != null) {
            mStartupThread.interrupt();
            try {
                awaitStarted();
            } catch (Utils.ApplicationError e) {
                // TODO: log?
            }
            mStartupThread = null;
            mStartupError = null;
        }
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
            // TODO: use output of ps command when missing pid file...(but don't interfere with other instances)?
            try {
                mPid = Utils.readFileToInt(mPidFile);
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
                        "HiddenServiceDir %s\n" +
                        // TODO: FIX! won't generate without HiddenServicePort set... ensure not published? run non-responding server?
                        "HiddenServicePort 443 localhost:7\n",
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
                        "HiddenServicePort 443 localhost:%d\n",
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
    
    private void writeHiddenServiceFiles() throws Utils.ApplicationError, IOException {
        mHiddenServiceDirectory.mkdirs();
        Utils.writeStringToFile(
                new String(Utils.decodeBase64(mKeyMaterial.mHostname)),
                mHiddenServiceHostnameFile);
        Utils.writeStringToFile(
                new String(Utils.decodeBase64(mKeyMaterial.mPrivateKey)),
                mHiddenServicePrivateKeyFile);
    }

    private int getPortValue(String data) throws Utils.ApplicationError {
        try {
            // Expected format is "PORT=127.0.0.1:<port>\n"
            String[] tokens = data.trim().split(":");
            if (tokens.length != 2) {
                throw new Utils.ApplicationError(logTag(), "unexpected port value format");                
            }
            return Integer.parseInt(tokens[1]);
        } catch (NumberFormatException e) {
            throw new Utils.ApplicationError(logTag(), e);
        }        
    }
    
    @Override
    public void circuitStatus(String status, String circID, String path) {
    }

    @Override
    public void streamStatus(String status, String streamID, String target) {
    }

    @Override
    public void orConnStatus(String status, String orName) {
    }

    @Override
    public void bandwidthUsed(long read, long written) {
    }

    @Override
    public void newDescriptors(List<String> orList) {
    }

    @Override
    public void message(String severity, String message) {
        Log.addEntry(logTag(), message);
    }

    @Override
    public void unrecognized(String type, String message) {
        if (type.equals("STATUS_CLIENT") && message.equals("NOTICE CIRCUIT_ESTABLISHED")) {
            mCircuitEstablishedLatch.countDown();
        }
        if (type.equals("STATUS_CLIENT") && message.startsWith("NOTICE BOOTSTRAP")) {
            // TODO: parse and display components (https://gitweb.torproject.org/torspec.git/blob/HEAD:/control-spec.txt)
            Log.addEntry(logTag(), message);
        }
    }
}
