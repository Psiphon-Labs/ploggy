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

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.util.Pair;
import fi.iki.elonen.NanoHTTPD;

/**
 * Wrapper for NanoHTTPD embedded web server, which is used as the server-side for Ploggy friend-to-friend
 * requests.
 *
 * Uses TLS configured with TransportSecurity specs and mutual authentication. Web clients must present a
 * valid friend certificate. Uses the Engine thread pool to service web requests.
 */
public class WebServer extends NanoHTTPD implements NanoHTTPD.ServerSocketFactory, NanoHTTPD.AsyncRunner {

    private static final String LOG_TAG = "Web Server";

    private static final int READ_TIMEOUT_IN_MILLISECONDS = (int)TimeUnit.MILLISECONDS.convert(60, TimeUnit.SECONDS);

    public interface RequestHandler {

        public static class SyncResponse {
            public final InputStream mInputStream;

            public SyncResponse(InputStream inputStream) {
                mInputStream = inputStream;
            }
        }

        public static class DownloadResponse {
            public final boolean mAvailable;
            public final String mMimeType;
            public final InputStream mInputStream;

            public DownloadResponse(boolean available, String mimeType, InputStream inputStream) {
                mAvailable = available;
                mMimeType = mimeType;
                mInputStream = inputStream;
            }
        }

        public void submitWebRequestTask(Runnable task);
        public String getFriendNicknameByCertificate(String friendCertificate) throws PloggyError;
        public void updateFriendSent(String friendCertificate, Date lastSentToTimestamp, long additionalBytesSentTo) throws PloggyError;
        public void updateFriendReceived(String friendCertificate, Date lastReceivedFromTimestamp, long additionalBytesReceivedFrom) throws PloggyError;
        public void handleAskLocationRequest(String friendCertificate) throws PloggyError;
        public void handleReportLocationRequest(String friendCertificate, String requestBody) throws PloggyError;
        public SyncResponse handleSyncRequest(String friendCertificate, String requestBody) throws PloggyError;
        public DownloadResponse handleDownloadRequest(String friendCertificate, String resourceId, Pair<Long, Long> range) throws PloggyError;
    }

    private final Data mData;
    private final RequestHandler mRequestHandler;
    private final X509.KeyMaterial mX509KeyMaterial;
    private final List<String> mFriendCertificates;

    public WebServer(
            Data data,
            RequestHandler requestHandler,
            X509.KeyMaterial x509KeyMaterial,
            List<String> friendCertificates) throws PloggyError {
        // Bind to loopback only -- not a public web server. Also, specify port 0 to let
        // the system pick any available port for listening.
        super("127.0.0.1", 0);
        mData = data;
        mRequestHandler = requestHandler;
        mX509KeyMaterial = x509KeyMaterial;
        mFriendCertificates = friendCertificates;
        setServerSocketFactory(this);
        setAsyncRunner(this);
    }

    @Override
    public ServerSocket createServerSocket() throws IOException {
        try {
            return TransportSecurity.makeServerSocket(mData, mX509KeyMaterial, mFriendCertificates);
        } catch (PloggyError e) {
            throw new IOException(e);
        }
    }

    @Override
    protected int getReadTimeoutInMilliseconds() {
        return READ_TIMEOUT_IN_MILLISECONDS;
    }

    @Override
    public void exec(Runnable webRequestTask) {
        // TODO: verify that either InterruptedException is thrown, or check Thread.isInterrupted(), in NanoHTTPD request handling Runnables
        Log.addEntry(LOG_TAG, "got web request");
        mRequestHandler.submitWebRequestTask(webRequestTask);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String certificate = null;
        try {
            certificate = TransportSecurity.getPeerCertificate(session.getSocket());
        } catch (PloggyError e) {
            Log.addEntry(LOG_TAG, "failed to get peer certificate");
            return new Response(NanoHTTPD.Response.Status.FORBIDDEN, null, "");
        }
        try {
            String uri = session.getUri();
            Method method = session.getMethod();

            if (method.equals(Method.valueOf(Protocol.ASK_LOCATION_REQUEST_TYPE)) &&
                    uri.equals(Protocol.ASK_LOCATION_REQUEST_PATH)) {
                return serveAskLocationRequest(certificate, session);
            } else if (method.equals(Method.valueOf(Protocol.REPORT_LOCATION_REQUEST_TYPE)) &&
                    uri.equals(Protocol.REPORT_LOCATION_REQUEST_PATH)) {
                return serveReportLocationRequest(certificate, session);
            } else if (method.equals(Method.valueOf(Protocol.SYNC_REQUEST_TYPE)) &&
                    uri.equals(Protocol.SYNC_REQUEST_PATH)) {
                return serveSyncRequest(certificate, session);
            } else if (method.equals(Method.valueOf(Protocol.DOWNLOAD_REQUEST_TYPE)) &&
                    uri.equals(Protocol.DOWNLOAD_REQUEST_PATH)) {
                return serveDownloadRequest(certificate, session);
            }
        } catch (PloggyError e) {
        }
        try {
            Log.addEntry(LOG_TAG, "failed to serve request: " + mRequestHandler.getFriendNicknameByCertificate(certificate));
        } catch (PloggyError e) {
            Log.addEntry(LOG_TAG, "failed to serve request: unrecognized certificate " + certificate.substring(0, 20) + "...");
        }
        return new Response(NanoHTTPD.Response.Status.FORBIDDEN, null, "");
    }

    private Response serveAskLocationRequest(String certificate, IHTTPSession session) throws PloggyError {
        mRequestHandler.handleAskLocationRequest(certificate);
        return new Response(NanoHTTPD.Response.Status.OK, null, "");
    }

    private Response serveReportLocationRequest(String certificate, IHTTPSession session) throws PloggyError {
        try {
            mRequestHandler.handleReportLocationRequest(certificate, new String(readRequestBodyHelper(session)));
            return new Response(NanoHTTPD.Response.Status.OK, null, "");
        } catch (IOException e) {
            throw new PloggyError(LOG_TAG, e);
        }
    }

    private Response serveSyncRequest(String certificate, IHTTPSession session) throws PloggyError {
        try {
            RequestHandler.SyncResponse syncResponse =
                    mRequestHandler.handleSyncRequest(certificate, new String(readRequestBodyHelper(session)));
            Response response =
                    new Response(NanoHTTPD.Response.Status.OK, Protocol.SYNC_RESPONSE_MIME_TYPE, syncResponse.mInputStream);
            response.setChunkedTransfer(true);
            return response;
        } catch (IOException e) {
            throw new PloggyError(LOG_TAG, e);
        }
    }

    private Response serveDownloadRequest(String certificate, IHTTPSession session) throws PloggyError {
        String resourceId = session.getParms().get(Protocol.DOWNLOAD_REQUEST_RESOURCE_ID_PARAMETER);
        if (resourceId == null) {
            throw new PloggyError(LOG_TAG, "download request missing resource id parameter");
        }
        Pair<Long, Long> range = readRangeHeaderHelper(session);
        RequestHandler.DownloadResponse downloadResponse = mRequestHandler.handleDownloadRequest(certificate, resourceId, range);
        Response response;
        if (downloadResponse.mAvailable) {
            response = new Response(NanoHTTPD.Response.Status.OK, downloadResponse.mMimeType, downloadResponse.mInputStream);
            response.setChunkedTransfer(true);
        } else {
            response = new Response(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, null, "");
        }
        return response;
    }

    private Pair<Long, Long> readRangeHeaderHelper(IHTTPSession session) throws PloggyError {
        // From NanoHTTP: https://github.com/NanoHttpd/nanohttpd/blob/master/webserver/src/main/java/fi/iki/elonen/SimpleWebServer.java
        long startFrom = 0;
        long endAt = -1;
        String range = session.getHeaders().get("range");
        if (range != null && range.startsWith("bytes=")) {
            range = range.substring("bytes=".length());
            int minus = range.indexOf('-');
            try {
                if (minus > 0) {
                    startFrom = Long.parseLong(range.substring(0, minus));
                    if (minus < range.length() - 1) {
                        endAt = Long.parseLong(range.substring(minus + 1));
                    }
                }
            } catch (NumberFormatException ignored) {
                throw new PloggyError(LOG_TAG, "invalid range header");
            }
        }
        return new Pair<Long, Long>(startFrom, endAt);
    }

    private byte[] readRequestBodyHelper(IHTTPSession session) throws IOException, PloggyError {
        String contentLengthValue = session.getHeaders().get("content-length");
        if (contentLengthValue == null) {
            throw new PloggyError(LOG_TAG, "failed to get request content length");
        }
        int contentLength = 0;
        try {
            contentLength = Integer.parseInt(contentLengthValue);
        } catch (NumberFormatException e) {
            throw new PloggyError(LOG_TAG, "invalid request content length");
        }
        if (contentLength > Protocol.MAX_MESSAGE_SIZE) {
            throw new PloggyError(LOG_TAG, "content length too large: " + Integer.toString(contentLength));
        }
        byte[] buffer = new byte[contentLength];
        int offset = 0;
        int remainingLength = contentLength;
        while (remainingLength > 0) {
            int readLength = session.getInputStream().read(buffer, offset, remainingLength);
            if (readLength == -1 || readLength > remainingLength) {
                throw new PloggyError(LOG_TAG,
                            String.format(
                                "failed to read POST content: read %d of %d expected bytes",
                                contentLength - remainingLength,
                                contentLength));
            }
            offset += readLength;
            remainingLength -= readLength;
        }
        return buffer;
    }
}
