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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import net.freehaven.tor.control.TorControlConnection;
import android.content.Context;
import android.os.Build;

/**
 * Wrapper for Tor child process.
 *
 * Derived from Briar Project's TorPlugin.java. Also uses Briar project's custom TorControlConnection.
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

    // Hidden Service authentication cookies for the Tor client
    public static class HiddenServiceAuth {
        public final String mHostname;
        public final String mAuthCookie;

        public HiddenServiceAuth(String hostname, String authCookie) {
            mHostname = hostname;
            mAuthCookie = authCookie;
        }
    }

    private final Mode mMode;
    private String mInstanceName;
    private final List<HiddenServiceAuth> mHiddenServiceAuth;
    private HiddenService.KeyMaterial mKeyMaterial;
    private int mWebServerPort = -1;
    private final File mRootDirectory;
    private final File mDataDirectory;
    private final File mHiddenServiceDirectory;
    private final File mExecutableFile;
    private final File mConfigFile;
    private final File mControlPortFile;
    private final File mControlAuthCookieFile;
    private final File mPidFile;
    private final File mHiddenServiceHostnameFile;
    private final File mHiddenServicePrivateKeyFile;
    private final File mHiddenServiceClientKeysFile;

    private Thread mStartupThread = null;
    private PloggyError mStartupError = null;
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

    public TorWrapper(String instanceName, Mode mode) {
        this(instanceName, mode, null, null, -1);
    }

    public TorWrapper(
            String instanceName,
            Mode mode,
            List<HiddenServiceAuth> hiddenServiceAuth,
            HiddenService.KeyMaterial keyMaterial,
            int webServerPort) {
        mMode = mode;
        mInstanceName = instanceName;
        if (mInstanceName == null) {
            mInstanceName = mMode.toString();
        }
        mHiddenServiceAuth = hiddenServiceAuth;
        mKeyMaterial = keyMaterial;
        mWebServerPort = webServerPort;
        Context context = Utils.getApplicationContext();
        String rootDirectory = String.format((Locale)null, "tor-%s-%s", mInstanceName, mMode.toString());
        mRootDirectory = context.getDir(rootDirectory, Context.MODE_PRIVATE);
        mDataDirectory = new File(mRootDirectory, "data");
        mHiddenServiceDirectory = new File(mRootDirectory, "hidden_service");
        // Note: calling the executable/process "ploggy-tor" instead of "tor" to avoid conflict
        // with Orbot, which in some cases appears to want to adopt any process named "tor"
        // as its own -- which causes Orbot to fail because our control interface is not
        // running on the expected port and Orbot can't get our control auth cookie.
        // (this appears to only occur in Orbot's root mode; tested with Orbot 12.x and 13.1)
        mExecutableFile = new File(mRootDirectory, "ploggy-tor");
        mConfigFile = new File(mRootDirectory, "config");
        mControlPortFile = new File(mDataDirectory, "control_port_file");
        mControlAuthCookieFile = new File(mDataDirectory, "control_auth_cookie");
        mPidFile = new File(mDataDirectory, "pid");
        mHiddenServiceHostnameFile = new File(mHiddenServiceDirectory, "hostname");
        mHiddenServicePrivateKeyFile = new File(mHiddenServiceDirectory, "private_key");
        mHiddenServiceClientKeysFile = new File(mHiddenServiceDirectory, "client_keys");
    }

    private String logTag() {
        return String.format("%s [%s][%s]", LOG_TAG, mInstanceName, mMode.toString());
    }

    public void start() {
        stop();
        // Performs start sequence asynchronously, in a background thread
        Runnable startTask = new Runnable() {
            @Override
            public void run() {
                try {
                    if (mMode == Mode.MODE_GENERATE_KEY_MATERIAL) {
                        startGenerateKeyMaterial();
                    } else if (mMode == Mode.MODE_RUN_SERVICES) {
                        startRunServices();
                    }
                } catch (PloggyError e) {
                    Log.addEntry(logTag(), "failed to start Tor");
                    // Save this to throw from awaitStarted
                    mStartupError = e;
                }
            }
        };
        mStartupThread = new Thread(startTask);
        mStartupThread.start();
    }

    public void awaitStarted() throws PloggyError {
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

    private void startGenerateKeyMaterial() throws PloggyError {
        try {
            // TODO: don't need two copies of the executable
            writeExecutableFile();
            writeGenerateKeyMaterialConfigFile();
            mHiddenServiceDirectory.mkdirs();
            if (mHiddenServiceHostnameFile.exists() && !mHiddenServiceHostnameFile.delete()) {
                throw new PloggyError(logTag(), "failed to delete existing hidden service hostname file");
            }
            if (mHiddenServicePrivateKeyFile.exists() && !mHiddenServicePrivateKeyFile.delete()) {
                throw new PloggyError(logTag(), "failed to delete existing hidden service private key file");
            }
            if (mHiddenServiceClientKeysFile.exists() && !mHiddenServiceClientKeysFile.delete()) {
                throw new PloggyError(logTag(), "failed to delete existing hidden service client keys file");
            }
            Utils.FileInitializedObserver hiddenServiceInitializedObserver =
                    new Utils.FileInitializedObserver(
                            mHiddenServiceDirectory,
                            mHiddenServiceHostnameFile.getName(),
                            mHiddenServicePrivateKeyFile.getName(),
                            mHiddenServiceClientKeysFile.getName());
            hiddenServiceInitializedObserver.startWatching();
            startDaemon(false);
            if (!hiddenServiceInitializedObserver.await(HIDDEN_SERVICE_INITIALIZED_TIMEOUT_MILLISECONDS)) {
                throw new PloggyError(logTag(), "timeout waiting for Tor hidden service initialization");
            }
            mKeyMaterial = parseHiddenServiceFiles();
        } catch (IOException e) {
            Log.addEntry(logTag(), "failed to start Tor");
            throw new PloggyError(logTag(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // This mode stops its Tor process
            stop();
            mHiddenServiceHostnameFile.delete();
            mHiddenServicePrivateKeyFile.delete();
        }
    }

    private void startRunServices() throws PloggyError {
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
            throw new PloggyError(logTag(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (!startCompleted) {
                stop();
            }
        }
    }

    private void startDaemon(boolean awaitFirstCircuit) throws PloggyError, IOException, InterruptedException {
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
                            "--hush",
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
                throw new PloggyError(logTag(), String.format("Tor exited with error %d", exit));
            }

            if (!controlInitializedObserver.await(CONTROL_INITIALIZED_TIMEOUT_MILLISECONDS)) {
                throw new PloggyError(logTag(), "timeout waiting for Tor control initialization");
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
            } catch (PloggyError e) {
                Log.addEntry(logTag(), "failed to stop gracefully");
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
            Log.addEntry(logTag(), e.getMessage());
            Log.addEntry(logTag(), "failed to stop gracefully");
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
        mCircuitEstablishedLatch = null;
    }

    public HiddenService.KeyMaterial getKeyMaterial() {
        return mKeyMaterial;
    }

    public int getSocksProxyPort() {
        return mSocksProxyPort;
    }

    public boolean isCircuitEstablished() {
        try {
            return mCircuitEstablishedLatch != null && mCircuitEstablishedLatch.await(0, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
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
        String configuration =
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
                        "HiddenServicePort 443 localhost:7\n" +
                        "HiddenServiceAuthorizeClient basic friend\n",
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
        StringBuilder hiddenServiceAuthLines = new StringBuilder();
        for (HiddenServiceAuth hiddenServiceAuth : mHiddenServiceAuth) {
            hiddenServiceAuthLines.append(
                String.format(
                    (Locale)null,
                    "HidServAuth %s %s\n",
                    hiddenServiceAuth.mHostname,
                    hiddenServiceAuth.mAuthCookie));
        }

        String configuration =
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
                        "HiddenServicePort 443 localhost:%d\n" +
                        "HiddenServiceAuthorizeClient basic friend\n" +
                        "%s",
                    mDataDirectory.getAbsolutePath(),
                    mPidFile.getAbsolutePath(),
                    mControlPortFile.getAbsolutePath(),
                    mControlAuthCookieFile.getAbsolutePath(),
                    mHiddenServiceDirectory.getAbsolutePath(),
                    mWebServerPort,
                    hiddenServiceAuthLines.toString());

        Utils.copyStream(
                new ByteArrayInputStream(configuration.getBytes("UTF-8")),
                new FileOutputStream(mConfigFile));
    }

    private HiddenService.KeyMaterial parseHiddenServiceFiles() throws PloggyError, IOException {
        String hostnameFileContent = Utils.readFileToString(mHiddenServiceHostnameFile);
        // Expected format: "gv69mnyyrwinum7l.onion WSdmfwVn8ewrCLKAwVyhCT # client: friend\n"
        String[] hostnameFileFields = hostnameFileContent.split(" ");
        if (hostnameFileFields.length < 2) {
            throw new PloggyError(logTag(), "unexpected fields in hidden service hostname file");
        }
        String hostname = hostnameFileFields[0];
        String authCookie = hostnameFileFields[1];
        String privateKey = Utils.readFileToString(mHiddenServicePrivateKeyFile);
        return new HiddenService.KeyMaterial(
                hostname,
                authCookie,
                Utils.encodeBase64(privateKey.getBytes()));
    }

    private void writeHiddenServiceFiles() throws PloggyError, IOException {
        mHiddenServiceDirectory.mkdirs();
        String hostnameFileContent = mKeyMaterial.mHostname + " " + mKeyMaterial.mAuthCookie + "\n";
        Utils.writeStringToFile(
                hostnameFileContent,
                mHiddenServiceHostnameFile);
        Utils.writeStringToFile(
                new String(Utils.decodeBase64(mKeyMaterial.mPrivateKey)),
                mHiddenServicePrivateKeyFile);
        // Format (as per rend_service_load_auth_keys in Tor's rendservice.c):
        // client-name friend
        // descriptor-cookie WSdmfwVn8ewrCLKAwVyhCT==
        String clientKeysFileContent = "client-name friend\ndescriptor-cookie " + mKeyMaterial.mAuthCookie + "==\n";
        Utils.writeStringToFile(
                clientKeysFileContent,
                mHiddenServiceClientKeysFile);
    }

    private int getPortValue(String data) throws PloggyError {
        try {
            // Expected format is "PORT=127.0.0.1:<port>\n"
            String[] tokens = data.trim().split(":");
            if (tokens.length != 2) {
                throw new PloggyError(logTag(), "unexpected port value format");
            }
            return Integer.parseInt(tokens[1]);
        } catch (NumberFormatException e) {
            throw new PloggyError(logTag(), e);
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
            if (mCircuitEstablishedLatch != null) {
                mCircuitEstablishedLatch.countDown();
            }
            Log.addEntry(logTag(), "circuit established");
            Events.getInstance(mInstanceName).post(new Events.TorCircuitEstablished());
        }
        if (type.equals("STATUS_CLIENT") && message.startsWith("NOTICE BOOTSTRAP")) {
            Pattern pattern = Pattern.compile(".*PROGRESS=(\\d+).*SUMMARY=\"(.+)\"");
            Matcher matcher = pattern.matcher(message);
            if (matcher.find() && matcher.groupCount() == 2) {
                Log.addEntry(logTag(), "bootstrap " + matcher.group(1) + "%: " + matcher.group(2));
            }
        }
    }
}
