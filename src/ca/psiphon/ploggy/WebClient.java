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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;

import javax.net.ssl.SSLContext;

import ch.boye.httpclientandroidlib.HttpEntity;
import ch.boye.httpclientandroidlib.HttpHost;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.HttpStatus;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.client.methods.HttpRequestBase;
import ch.boye.httpclientandroidlib.conn.ClientConnectionManager;
import ch.boye.httpclientandroidlib.conn.ClientConnectionOperator;
import ch.boye.httpclientandroidlib.conn.OperatedClientConnection;
import ch.boye.httpclientandroidlib.conn.scheme.Scheme;
import ch.boye.httpclientandroidlib.conn.scheme.SchemeRegistry;
import ch.boye.httpclientandroidlib.conn.ssl.SSLSocketFactory;
import ch.boye.httpclientandroidlib.impl.client.DefaultHttpClient;
import ch.boye.httpclientandroidlib.impl.conn.DefaultClientConnectionOperator;
import ch.boye.httpclientandroidlib.impl.conn.PoolingClientConnectionManager;
import ch.boye.httpclientandroidlib.params.BasicHttpParams;
import ch.boye.httpclientandroidlib.params.HttpConnectionParams;
import ch.boye.httpclientandroidlib.params.HttpParams;
import ch.boye.httpclientandroidlib.protocol.HttpContext;

public class WebClient {

    private static final String LOG_TAG = "Web Client";

    public static final int UNTUNNELED_REQUEST = -1;
    
    private static final String LOCAL_SOCKS_PROXY_PORT_PARAM_NAME = "localSocksProxyPort";
    private static final int CONNECT_TIMEOUT_MILLISECONDS = 60000;
    private static final int READ_TIMEOUT_MILLISECONDS = 60000;

    public static String makeGetRequest(
            X509.KeyMaterial x509KeyMaterial,
            String peerCertificate,
            int localSocksProxyPort,
            String hostname,
            int port,
            String requestPath) throws Utils.ApplicationError {
        HttpRequestBase request = null;
        try {
            URI uri = new URI(Protocol.WEB_SERVER_PROTOCOL, null, hostname, port, requestPath, null, null);
            SSLContext sslContext = TransportSecurity.getSSLContext(x509KeyMaterial, Arrays.asList(peerCertificate));
            SSLSocketFactory sslSocketFactory = TransportSecurity.getClientSSLSocketFactory(sslContext);
            // TODO: persistent connection manager, httpclient, etc.
            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme(Protocol.WEB_SERVER_PROTOCOL, Protocol.WEB_SERVER_VIRTUAL_PORT, sslSocketFactory));
            ClientConnectionManager connectionManager;
            if (localSocksProxyPort == UNTUNNELED_REQUEST) {
                connectionManager = new PoolingClientConnectionManager(registry);
            } else {
                connectionManager = new SocksProxyPoolingClientConnectionManager(registry);                
            }
            HttpParams params = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(params, CONNECT_TIMEOUT_MILLISECONDS);
            HttpConnectionParams.setSoTimeout(params, READ_TIMEOUT_MILLISECONDS);
            params.setIntParameter(LOCAL_SOCKS_PROXY_PORT_PARAM_NAME, localSocksProxyPort);
            DefaultHttpClient client = new DefaultHttpClient(connectionManager, params);            
            request = new HttpGet(uri);
            HttpResponse response = client.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                throw new Utils.ApplicationError(LOG_TAG, String.format("HTTP request failed with %d", statusCode));
            }
            HttpEntity responseEntity = response.getEntity();
            // TODO: stream larger responses to files, etc.
            return Utils.readInputStreamToString(responseEntity.getContent());
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
        }
    }
    
    public static String makeGetRequest(
            X509.KeyMaterial x509KeyMaterial,
            String friendCertificate,
            int localSocksProxyPort,
            String friendHiddenServiceHostname,
            String requestPath) throws Utils.ApplicationError {
        return makeGetRequest(
                x509KeyMaterial,
                friendCertificate,
                localSocksProxyPort,
                friendHiddenServiceHostname,
                Protocol.WEB_SERVER_VIRTUAL_PORT,
                requestPath);
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

        // TODO: ...derived from the original DefaultClientConnectionOperator.java
        @Override
        public void openConnection(
                final OperatedClientConnection conn,
                final HttpHost target,
                final InetAddress local,
                final HttpContext context,
                final HttpParams params) throws IOException {
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

            // TODO: ...explicit SOCKS4a
            // ... Android (Apache Harmony) Socket appears to support only SOCKS4 and throws on the unresolved address

            /*
            field 1: SOCKS version number, 1 byte, must be 0x04 for this version
            field 2: command code, 1 byte:
                0x01 = establish a TCP/IP stream connection
                0x02 = establish a TCP/IP port binding
            field 3: network byte order port number, 2 bytes
            field 4: deliberate invalid IP address, 4 bytes, first three must be 0x00 and the last one must not be 0x00
            field 5: the user ID string, variable length, terminated with a null (0x00)
            field 6: the domain name of the host we want to contact, variable length, terminated with a null (0x00)
            */
            
            int localSocksProxyPort = params.getIntParameter(LOCAL_SOCKS_PROXY_PORT_PARAM_NAME, -1);

            Socket socket = new Socket();
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

            Socket sslsocket = sslSocketFactory.createLayeredSocket(socket, host, port, params);
            conn.opening(sslsocket, target);
            sslsocket.setSoTimeout(READ_TIMEOUT_MILLISECONDS);
            prepareSocket(sslsocket, context, params);
            conn.openCompleted(sslSocketFactory.isSecure(sslsocket), params);
            
            // TODO: clarify which connection throws java.net.SocketTimeoutException
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
