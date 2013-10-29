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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

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

    public interface RequestHandler {
        public void submitTask(Runnable task);
        public Data.Status handlePullStatusRequest(String friendId) throws Utils.ApplicationError;
        public void handlePushStatusRequest(String friendId, Data.Status status) throws Utils.ApplicationError;        
    }
    
    private RequestHandler mRequestHandler;
    private X509.KeyMaterial mX509KeyMaterial;
    private ArrayList<String> mFriendCertificates;
	
    public WebServer(
            RequestHandler requestHandler,
            X509.KeyMaterial x509KeyMaterial,
            ArrayList<String> friendCertificates) throws Utils.ApplicationError {
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
    public void exec(Runnable webRequestTask) {
        // TODO: verify that either InterruptedException is thrown, or check Thread.isInterrupted(), in NanoHTTPD request handling Runnables        
        mRequestHandler.submitTask(webRequestTask);
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
        try {
            String certificate = getPeerCertificate(session.getSocket());
            String uri = session.getUri();
            Method method = session.getMethod();
            if (Method.GET.equals(method) && uri.equals(Protocol.PULL_STATUS_REQUEST_PATH)) {
                Data.Status status = mRequestHandler.handlePullStatusRequest(certificate);
                return new Response(NanoHTTPD.Response.Status.OK, Protocol.RESPONSE_MIME_TYPE, Json.toJson(status));
            } else if (Method.POST.equals(method) && uri.equals(Protocol.PUSH_STATUS_REQUEST_PATH)) {
                String body = Utils.readInputStreamToString(session.getInputStream());
                Data.Status status = Json.fromJson(body, Data.Status.class);
                mRequestHandler.handlePushStatusRequest(certificate, status);
                return new Response(NanoHTTPD.Response.Status.OK, null, "");
            }
        } catch (IOException e) {
            Log.addEntry(LOG_TAG, e.getMessage());
        } catch (Utils.ApplicationError e) {
            // TODO: log
        }
        return new Response(NanoHTTPD.Response.Status.FORBIDDEN, null, "");
    }
}
