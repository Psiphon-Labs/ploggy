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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;

import android.util.Pair;
import ch.boye.httpclientandroidlib.HttpEntity;
import ch.boye.httpclientandroidlib.HttpHost;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.HttpStatus;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.client.methods.HttpPut;
import ch.boye.httpclientandroidlib.client.methods.HttpRequestBase;
import ch.boye.httpclientandroidlib.client.utils.URIBuilder;
import ch.boye.httpclientandroidlib.conn.ClientConnectionManager;
import ch.boye.httpclientandroidlib.conn.ClientConnectionOperator;
import ch.boye.httpclientandroidlib.conn.OperatedClientConnection;
import ch.boye.httpclientandroidlib.conn.scheme.Scheme;
import ch.boye.httpclientandroidlib.conn.scheme.SchemeRegistry;
import ch.boye.httpclientandroidlib.conn.ssl.SSLSocketFactory;
import ch.boye.httpclientandroidlib.entity.InputStreamEntity;
import ch.boye.httpclientandroidlib.impl.client.DefaultHttpClient;
import ch.boye.httpclientandroidlib.impl.conn.DefaultClientConnectionOperator;
import ch.boye.httpclientandroidlib.impl.conn.PoolingClientConnectionManager;
import ch.boye.httpclientandroidlib.params.BasicHttpParams;
import ch.boye.httpclientandroidlib.params.HttpConnectionParams;
import ch.boye.httpclientandroidlib.params.HttpParams;
import ch.boye.httpclientandroidlib.protocol.HttpContext;

/**
 * Client-side for Ploggy friend-to-friend requests.
 *
 * Implements HTTP requests through Tor with TLS configured with TransportSecurity specs and mutual
 * authentication.
 */
public class WebClient {

    private static final String LOG_TAG = "Web Client";

    enum RequestType {GET, PUT};

    private X509.KeyMaterial mX509KeyMaterial = null;
    private String mPeerCertificate = null;
    private int mLocalSocksProxyPort = -1;
    private String mHostname = null;
    private int mPort = -1;
    private RequestType mRequestType;
    private String mRequestPath = null;
    private List<Pair<String,String>> mRequestParameters = null;
    private String mRequestBodyMimeType = null;
    private long mRequestBodyLength = -1;
    private InputStream mRequestBodyInputStream = null;
    private Pair<Long, Long> mRangeHeader = null;
    private OutputStream mResponseBodyOutputStream = null;
    private ResponseBodyHandler mResponseBodyHandler = null;

    private static final String LOCAL_SOCKS_PROXY_PORT_PARAM_NAME = "localSocksProxyPort";
    private static final int CONNECT_TIMEOUT_MILLISECONDS = 60000;
    private static final int READ_TIMEOUT_MILLISECONDS = 60000;

    public WebClient(
            X509.KeyMaterial x509KeyMaterial,
            String peerCertificate,
            String hostname,
            int port,
            RequestType requestType,
            String requestPath) {
        mX509KeyMaterial = x509KeyMaterial;
        mPeerCertificate = peerCertificate;
        mHostname = hostname;
        mPort = port;
        mRequestPath = requestPath;
    }

    public WebClient localSocksProxyPort(int localSocksProxyPort) {
        mLocalSocksProxyPort = localSocksProxyPort;
        return this;
    }

    public WebClient requestParameters(List<Pair<String,String>> requestParameters) {
        mRequestParameters = requestParameters;
        return this;
    }

    public WebClient requestBody(String mimeType, long length, InputStream inputStream) {
        mRequestBodyMimeType = mimeType;
        mRequestBodyLength = length;
        mRequestBodyInputStream = inputStream;
        return this;
    }

    public WebClient requestBody(String requestBody) throws Utils.ApplicationError {
        byte[] body;
        try {
            body = requestBody.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
        return requestBody("application/json", body.length, new ByteArrayInputStream(body));
    }

    public WebClient rangeHeader(Pair<Long, Long> rangeHeader) {
        mRangeHeader = rangeHeader;
        return this;
    }

    public WebClient responseBodyOutputStream(OutputStream responseBodyOutputStream) {
        mResponseBodyOutputStream = responseBodyOutputStream;
        mResponseBodyHandler = null;
        return this;
    }

    public interface ResponseBodyHandler {
        void consume(InputStream responseBodyInputStream) throws Utils.ApplicationError;
    }

    public WebClient responseBodyHandler(ResponseBodyHandler responseBodyHandler) {
        mResponseBodyHandler = responseBodyHandler;
        mResponseBodyOutputStream = null;
        return this;
    }

    public String makeRequestAndLoadResponse() throws Utils.ApplicationError {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        responseBodyOutputStream(outputStream);
        try {
            return new String(outputStream.toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    public void makeRequest() throws Utils.ApplicationError {
        HttpRequestBase request = null;
        ClientConnectionManager connectionManager = null;
        try {
            URIBuilder uriBuilder =
                    new URIBuilder()
                        .setScheme(Protocol.WEB_SERVER_PROTOCOL)
                        .setHost(mHostname)
                        .setPort(mPort)
                        .setPath(mRequestPath);
            if (mRequestParameters != null) {
                for (Pair<String,String> requestParameter : mRequestParameters) {
                    uriBuilder.addParameter(requestParameter.first, requestParameter.second);
                }
            }
            URI uri = uriBuilder.build();

            SSLContext sslContext = TransportSecurity.getSSLContext(mX509KeyMaterial, Arrays.asList(mPeerCertificate));
            SSLSocketFactory sslSocketFactory = TransportSecurity.getClientSSLSocketFactory(sslContext);
            // TODO: keep a persistent PoolingClientConnectionManager across makeRequest calls for connection reuse?
            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme(Protocol.WEB_SERVER_PROTOCOL, Protocol.WEB_SERVER_VIRTUAL_PORT, sslSocketFactory));
            if (mLocalSocksProxyPort == -1) {
                connectionManager = new PoolingClientConnectionManager(registry);
            } else {
                connectionManager = new SocksProxyPoolingClientConnectionManager(registry);
            }
            HttpParams params = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(params, CONNECT_TIMEOUT_MILLISECONDS);
            HttpConnectionParams.setSoTimeout(params, READ_TIMEOUT_MILLISECONDS);
            params.setIntParameter(LOCAL_SOCKS_PROXY_PORT_PARAM_NAME, mLocalSocksProxyPort);
            DefaultHttpClient client = new DefaultHttpClient(connectionManager, params);
            switch (mRequestType) {
            case GET:
                request = new HttpGet(uri);
                // TODO: assert mRequestBodyInputStream is null? it's ignored
                break;
            case PUT:
                HttpPut putRequest = new HttpPut(uri);
                if (mRequestBodyInputStream != null) {
                    InputStreamEntity entity = new InputStreamEntity(mRequestBodyInputStream, mRequestBodyLength);
                    entity.setContentType(mRequestBodyMimeType);
                    putRequest.setEntity(entity);
                }
                request = putRequest;
                break;
            }
            if (mRangeHeader != null) {
                String value = "bytes=" + Long.toString(mRangeHeader.first) + "-";
                if (mRangeHeader.second != -1) {
                    value = value + Long.toString(mRangeHeader.second);
                }
                request.addHeader("Range", value);
            }
            HttpResponse response = client.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                throw new Utils.ApplicationError(LOG_TAG, String.format("HTTP request failed with %d", statusCode));
            }
            HttpEntity responseEntity = response.getEntity();
            if (mResponseBodyOutputStream != null) {
                Utils.copyStream(responseEntity.getContent(), mResponseBodyOutputStream);
            } else if (mResponseBodyHandler != null) {
                mResponseBodyHandler.consume(responseEntity.getContent());
            }else {
                // Even if the caller doesn't want the content, we need to consume the bytes
                // (particularly if leaving the socket up in a keep-alive state).
                Utils.discardStream(responseEntity.getContent());
            }
        } catch (URISyntaxException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (UnsupportedOperationException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (IllegalStateException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (IllegalArgumentException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (NullPointerException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (IOException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            if (request != null && !request.isAborted()) {
                request.abort();
            }
            if (connectionManager != null) {
                connectionManager.shutdown();
            }
        }
    }

    private static class SocksProxyPoolingClientConnectionManager extends PoolingClientConnectionManager {

        public SocksProxyPoolingClientConnectionManager(SchemeRegistry registry) {
            super(registry);
        }

        @Override
        protected ClientConnectionOperator createConnectionOperator(SchemeRegistry registry) {
            return new SocksProxyClientConnectionOperator(registry);
        }
    }

    private static class SocksProxyClientConnectionOperator extends DefaultClientConnectionOperator {

        public SocksProxyClientConnectionOperator(SchemeRegistry registry) {
            super(registry);
        }

        // Derived from the original DefaultClientConnectionOperator.java in Apache HttpClient 4.2
        @Override
        public void openConnection(
                final OperatedClientConnection conn,
                final HttpHost target,
                final InetAddress local,
                final HttpContext context,
                final HttpParams params) throws IOException {
            Socket socket = null;
            Socket sslSocket = null;
            try {
                if (conn == null || target == null || params == null) {
                    throw new IllegalArgumentException("Required argument may not be null");
                }
                if (conn.isOpen()) {
                    throw new IllegalStateException("Connection must not be open");
                }

                Scheme scheme = schemeRegistry.getScheme(target.getSchemeName());
                SSLSocketFactory sslSocketFactory = (SSLSocketFactory)scheme.getSchemeSocketFactory();

                int port = scheme.resolvePort(target.getPort());
                String host = target.getHostName();

                // Perform explicit SOCKS4a connection request. SOCKS4a supports remote host name resolution
                // (i.e., Tor resolves the hostname, which may be an onion address).
                // The Android (Apache Harmony) Socket class appears to support only SOCKS4 and throws an
                // exception on an address created using INetAddress.createUnresolved() -- so the typical
                // technique for using Java SOCKS4a/5 doesn't appear to work on Android:
                // https://android.googlesource.com/platform/libcore/+/master/luni/src/main/java/java/net/PlainSocketImpl.java
                // See also: http://www.mit.edu/~foley/TinFoil/src/tinfoil/TorLib.java, for a similar implementation

                // From http://en.wikipedia.org/wiki/SOCKS#SOCKS4a:
                //
                // field 1: SOCKS version number, 1 byte, must be 0x04 for this version
                // field 2: command code, 1 byte:
                //     0x01 = establish a TCP/IP stream connection
                //     0x02 = establish a TCP/IP port binding
                // field 3: network byte order port number, 2 bytes
                // field 4: deliberate invalid IP address, 4 bytes, first three must be 0x00 and the last one must not be 0x00
                // field 5: the user ID string, variable length, terminated with a null (0x00)
                // field 6: the domain name of the host we want to contact, variable length, terminated with a null (0x00)

                int localSocksProxyPort = params.getIntParameter(LOCAL_SOCKS_PROXY_PORT_PARAM_NAME, -1);

                socket = new Socket();
                conn.opening(socket, target);
                socket.setSoTimeout(READ_TIMEOUT_MILLISECONDS);
                socket.connect(new InetSocketAddress("127.0.0.1", localSocksProxyPort), CONNECT_TIMEOUT_MILLISECONDS);

                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                outputStream.write((byte)0x04);
                outputStream.write((byte)0x01);
                outputStream.writeShort((short)port);
                outputStream.writeInt(0x01);
                outputStream.write((byte)0x00);
                outputStream.write(host.getBytes());
                outputStream.write((byte)0x00);

                DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                if (inputStream.readByte() != (byte)0x00 || inputStream.readByte() != (byte)0x5a) {
                    throw new IOException("SOCKS4a connect failed");
                }
                inputStream.readShort();
                inputStream.readInt();

                sslSocket = sslSocketFactory.createLayeredSocket(socket, host, port, params);
                conn.opening(sslSocket, target);
                sslSocket.setSoTimeout(READ_TIMEOUT_MILLISECONDS);
                prepareSocket(sslSocket, context, params);
                conn.openCompleted(sslSocketFactory.isSecure(sslSocket), params);
                // TODO: clarify which connection throws java.net.SocketTimeoutException?
            } catch (IOException e) {
                try {
                    if (sslSocket != null) {
                        sslSocket.close();
                    }
                    if (socket != null) {
                        socket.close();
                    }
                } catch (IOException ioe) {}
                throw e;
            }
        }

        @Override
        public void updateSecureConnection(
                final OperatedClientConnection conn,
                final HttpHost target,
                final HttpContext context,
                final HttpParams params) throws IOException {
            throw new RuntimeException("operation not supported");
        }

        @Override
        protected InetAddress[] resolveHostname(final String host) throws UnknownHostException {
            throw new RuntimeException("operation not supported");
        }
    }
}
