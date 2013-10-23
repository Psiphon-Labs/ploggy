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
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLServerSocket;

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

    private ExecutorService mExecutorService;
    private X509.KeyMaterial mX509KeyMaterial;
    private ArrayList<String> mFriendCerficates;
	
    public WebServer(
            ExecutorService executorService,
            X509.KeyMaterial x509KeyMaterial,
            ArrayList<String> friendCertificates) throws Utils.ApplicationError {
        // Bind to loopback only -- not a public web server. Also, specify port 0 to let
        // the system pick any available port for listening.
        super("127.0.0.1", 0);
        mExecutorService = executorService;
        mX509KeyMaterial = x509KeyMaterial;
        mFriendCerficates = friendCertificates;
        setServerSocketFactory(this);
        setAsyncRunner(this);
    }

    @Override
    public ServerSocket createServerSocket() throws IOException {
        try {
            SSLServerSocket sslServerSocket = (SSLServerSocket)TransportSecurity.makeServerSocket(mX509KeyMaterial, mFriendCerficates);
            return sslServerSocket;
        } catch (Utils.ApplicationError e) {
            throw new IOException(e);
        }
    }

    @Override
    public void exec(Runnable webRequestTask) {
        // TODO: verify that either InterruptedException is thrown, or check Thread.isInterrupted(), in NanoHTTPD request handling Runnables        
        mExecutorService.execute(webRequestTask);
    }

    @Override
    public Response serve(String uri, Method method, Map<String, String> headers, Map<String, String> parms, Map<String, String> files) {
        try {
            if (uri.equals(Protocol.GET_STATUS_REQUEST_PATH)) {
                Data.Status status = Data.getInstance().getSelfStatus();
                return new Response(NanoHTTPD.Response.Status.OK, Protocol.RESPONSE_MIME_TYPE, Json.toJson(status));
            }
        } catch (Utils.ApplicationError e) {
            // TODO: log
        }
        return new Response(NanoHTTPD.Response.Status.FORBIDDEN, null, "");
    }
}
