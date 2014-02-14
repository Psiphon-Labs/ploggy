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
import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Date;
import java.util.List;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

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

    private static final int READ_TIMEOUT_MILLISECONDS = 60000;

    public interface RequestHandler {

        public static class PullResponse {
            public final InputStream mData;

            public PullResponse(InputStream data) {
                mData = data;
            }
        }

        public static class DownloadResponse {
            public final boolean mAvailable;
            public final String mMimeType;
            public final InputStream mData;

            public DownloadResponse(boolean available, String mimeType, InputStream data) {
                mAvailable = available;
                mMimeType = mimeType;
                mData = data;
            }
        }

        public void submitWebRequestTask(Runnable task);
        public void updateFriendSent(String friendId, Date lastSentToTimestamp, long additionalBytesSentTo) throws Utils.ApplicationError;
        public void updateFriendReceived(String friendId, Date lastReceivedFromTimestamp, long additionalBytesReceivedFrom) throws Utils.ApplicationError;
        public void handleAskPullRequest(String friendId) throws Utils.ApplicationError;
        public void handleAskLocationRequest(String friendId) throws Utils.ApplicationError;
        public void handlePushRequest(String friendId, String requestBody) throws Utils.ApplicationError;
        public PullResponse handlePullRequest(String friendId, String requestBody) throws Utils.ApplicationError;
        public DownloadResponse handleDownloadRequest(String friendCertificate, String resourceId, Pair<Long, Long> range) throws Utils.ApplicationError;
    }

    private final RequestHandler mRequestHandler;
    private final X509.KeyMaterial mX509KeyMaterial;
    private final List<String> mFriendCertificates;

    public WebServer(
            RequestHandler requestHandler,
            X509.KeyMaterial x509KeyMaterial,
            List<String> friendCertificates) throws Utils.ApplicationError {
        // Bind to loopback only -- not a public web server. Also, specify port 0 to let
        // the system pick any available port for listening.
        super("127.0.0.1", 0);
        mRequestHandler = requestHandler;
        mX509KeyMaterial = x509KeyMaterial;
        mFriendCertificates = friendCertificates;
        setServerSocketFactory(this);
        setAsyncRunner(this);
    }

    @Override
    public ServerSocket createServerSocket() throws IOException {
        try {
            SSLServerSocket sslServerSocket = (SSLServerSocket)TransportSecurity.makeServerSocket(mX509KeyMaterial, mFriendCertificates);
            return sslServerSocket;
        } catch (Utils.ApplicationError e) {
            throw new IOException(e);
        }
    }

    @Override
    protected int getReadTimeout() {
        return READ_TIMEOUT_MILLISECONDS;
    }

    @Override
    public void exec(Runnable webRequestTask) {
        // TODO: verify that either InterruptedException is thrown, or check Thread.isInterrupted(), in NanoHTTPD request handling Runnables
        Log.addEntry(LOG_TAG, "got web request");
        mRequestHandler.submitWebRequestTask(webRequestTask);
    }

    private String getPeerCertificate(Socket socket) throws Utils.ApplicationError {
        // Determine friend id by peer TLS certificate
        try {
            SSLSocket sslSocket = (SSLSocket)socket;
            SSLSession sslSession = sslSocket.getSession();
            Certificate[] certificates = sslSession.getPeerCertificates();
            if (certificates.length != 1) {
                throw new Utils.ApplicationError(LOG_TAG, "unexpected peer certificate count");
            }
            return Utils.encodeBase64(certificates[0].getEncoded());
        } catch (SSLPeerUnverifiedException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (CertificateEncodingException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String certificate = null;
        try {
            certificate = getPeerCertificate(session.getSocket());
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to get peer certificate");
            return new Response(NanoHTTPD.Response.Status.FORBIDDEN, null, "");
        }
        try {
            String uri = session.getUri();
            Method method = session.getMethod();

            if (Method.GET.equals(method) && uri.equals(Protocol.PULL_STATUS_REQUEST_PATH)) {
                Data.Status status = mRequestHandler.handlePullStatusRequest(certificate);
                if (status == null) {
                    // TODO: not currently sharing; serve old status?
                    return new Response(NanoHTTPD.Response.Status.FORBIDDEN, null, "");
                }
                return new Response(NanoHTTPD.Response.Status.OK, Protocol.PULL_STATUS_RESPONSE_MIME_TYPE, Json.toJson(status));

            } else if (Method.GET.equals(method) && uri.equals(Protocol.DOWNLOAD_REQUEST_PATH)) {
                String resourceId = session.getParms().get(Protocol.DOWNLOAD_REQUEST_RESOURCE_ID_PARAMETER);
                if (resourceId == null) {
                    throw new Utils.ApplicationError(LOG_TAG, "download request missing resource id parameter");
                }
                Pair<Long, Long> range = readRangeHeaderHelper(session);
                RequestHandler.DownloadResponse downloadResponse = mRequestHandler.handleDownloadRequest(certificate, resourceId, range);
                Response response;
                if (downloadResponse.mAvailable) {
                    response = new Response(NanoHTTPD.Response.Status.OK, downloadResponse.mMimeType, downloadResponse.mData);
                    response.setChunkedTransfer(true);
                } else {
                    response = new Response(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, null, "");
                }
                return response;

            } else if (Method.POST.equals(method) && uri.equals(Protocol.PUSH_STATUS_REQUEST_PATH)) {
                // TODO: PUT more RESTful?
                Data.Status status = Json.fromJson(new String(readRequestBodyHelper(session)), Data.Status.class);
                mRequestHandler.handlePushStatusRequest(certificate, status);
                return new Response(NanoHTTPD.Response.Status.OK, null, "");
            }
        } catch (IOException e) {
            Log.addEntry(LOG_TAG, e.getMessage());
        } catch (Utils.ApplicationError e) {
        }
        try {
            Data.Friend friend = Data.getInstance().getFriendByCertificate(certificate);
            Log.addEntry(LOG_TAG, "failed to serve request: " + friend.mPublicIdentity.mNickname);
        } catch (Utils.ApplicationError e) {
            Log.addEntry(LOG_TAG, "failed to serve request: unrecognized certificate " + certificate.substring(0, 20) + "...");
        }
        return new Response(NanoHTTPD.Response.Status.FORBIDDEN, null, "");
    }

    private Pair<Long, Long> readRangeHeaderHelper(IHTTPSession session) throws Utils.ApplicationError {
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
                throw new Utils.ApplicationError(LOG_TAG, "invalid range header");
            }
        }
        return new Pair<Long, Long>(startFrom, endAt);
    }

    private byte[] readRequestBodyHelper(IHTTPSession session) throws IOException, Utils.ApplicationError {
        String contentLengthValue = session.getHeaders().get("content-length");
        if (contentLengthValue == null) {
            throw new Utils.ApplicationError(LOG_TAG, "failed to get request content length");
        }
        int contentLength = 0;
        try {
            contentLength = Integer.parseInt(contentLengthValue);
        } catch (NumberFormatException e) {
            throw new Utils.ApplicationError(LOG_TAG, "invalid request content length");
        }
        if (contentLength > Protocol.MAX_POST_REQUEST_BODY_SIZE) {
            throw new Utils.ApplicationError(LOG_TAG, "content length too large: " + Integer.toString(contentLength));
        }
        byte[] buffer = new byte[contentLength];
        int offset = 0;
        int remainingLength = contentLength;
        while (remainingLength > 0) {
            int readLength = session.getInputStream().read(buffer, offset, remainingLength);
            if (readLength == -1 || readLength > remainingLength) {
                throw new Utils.ApplicationError(LOG_TAG,
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
