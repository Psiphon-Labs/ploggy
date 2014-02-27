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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;

import ch.boye.httpclientandroidlib.HttpHost;
import ch.boye.httpclientandroidlib.conn.ClientConnectionManager;
import ch.boye.httpclientandroidlib.conn.ClientConnectionOperator;
import ch.boye.httpclientandroidlib.conn.OperatedClientConnection;
import ch.boye.httpclientandroidlib.conn.scheme.Scheme;
import ch.boye.httpclientandroidlib.conn.scheme.SchemeRegistry;
import ch.boye.httpclientandroidlib.conn.ssl.SSLSocketFactory;
import ch.boye.httpclientandroidlib.impl.conn.DefaultClientConnectionOperator;
import ch.boye.httpclientandroidlib.impl.conn.PoolingClientConnectionManager;
import ch.boye.httpclientandroidlib.params.HttpParams;
import ch.boye.httpclientandroidlib.protocol.HttpContext;

/**
 * Client-side for Ploggy friend-to-friend requests.
 *
 * Implements HTTP requests through Tor with TLS configured with TransportSecurity specs and mutual
 * authentication.
 *
 * TODO: Upgrade httpclientandroidlib to 4.3?
 *
 */
public class WebClientConnectionPool {

    private static final String LOG_TAG = "Web Client Connection Pool";

    public static final int CONNECT_TIMEOUT_MILLISECONDS = 60000;
    public static final int READ_TIMEOUT_MILLISECONDS = 60000;
    public static final int MAX_TOTAL_POOL_CONNECTIONS = 100;
    public static final int MAX_PER_ROUTE_POOL_CONNECTIONS = 4;

    private final SocksProxyPoolingClientConnectionManager mConnectionManager;

    // Creates a connection pool with mutual authentication for all friend hidden services
    public WebClientConnectionPool(Data data, int localSocksProxyPort) throws PloggyError {
        Data.Self self = data.getSelfOrThrow();
        X509.KeyMaterial x509KeyMaterial = new X509.KeyMaterial(self.mPublicIdentity.mX509Certificate, self.mPrivateIdentity.mX509PrivateKey);
        List<String> friendCertificates = new ArrayList<String>();
        for (Data.Friend friend : data.getFriends()) {
            friendCertificates.add(friend.mPublicIdentity.mX509Certificate);
        }
        mConnectionManager =
                makePoolingClientConnectionManager(data, localSocksProxyPort, x509KeyMaterial, friendCertificates);
    }

    // Creates a connection pool with server authentication only for the specified server
    // certificate only -- no hostname verification
    public WebClientConnectionPool(String serverCertificate, int localSocksProxyPort) throws PloggyError {
        mConnectionManager =
                makePoolingClientConnectionManager(null, localSocksProxyPort, null, Arrays.asList(serverCertificate));
    }

    private SocksProxyPoolingClientConnectionManager makePoolingClientConnectionManager(
            Data data,
            int localSocksProxyPort,
            X509.KeyMaterial x509KeyMaterial,
            List<String> friendCertificates) throws PloggyError {
        SSLContext sslContext = TransportSecurity.getSSLContext(x509KeyMaterial, friendCertificates);
        SSLSocketFactory sslSocketFactory = TransportSecurity.getClientSSLSocketFactory(sslContext, data);
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme(Protocol.WEB_SERVER_PROTOCOL, Protocol.WEB_SERVER_VIRTUAL_PORT, sslSocketFactory));
        SocksProxyPoolingClientConnectionManager poolingClientConnectionManager =
                new SocksProxyPoolingClientConnectionManager(localSocksProxyPort, registry);
        poolingClientConnectionManager.setMaxTotal(MAX_TOTAL_POOL_CONNECTIONS);
        poolingClientConnectionManager.setDefaultMaxPerRoute(MAX_TOTAL_POOL_CONNECTIONS);
        return poolingClientConnectionManager;
    }

    public void shutdown() {
        mConnectionManager.shutdown();
    }

    public ClientConnectionManager getConnectionManager() {
        return mConnectionManager;
    }

    public static class SocksProxyPoolingClientConnectionManager extends PoolingClientConnectionManager {

        private final int mLocalSocksProxyPort;

        public SocksProxyPoolingClientConnectionManager(int localSocksProxyPort, SchemeRegistry registry) {
            super(registry);
            mLocalSocksProxyPort = localSocksProxyPort;
        }

        @Override
        protected ClientConnectionOperator createConnectionOperator(SchemeRegistry registry) {
            return new SocksProxyClientConnectionOperator(mLocalSocksProxyPort, registry);
        }
    }

    private static class SocksProxyClientConnectionOperator extends DefaultClientConnectionOperator {

        private final int mLocalSocksProxyPort;

        public SocksProxyClientConnectionOperator(int localSocksProxyPort, SchemeRegistry registry) {
            super(registry);
            mLocalSocksProxyPort = localSocksProxyPort;
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

                socket = new Socket();
                conn.opening(socket, target);
                socket.setSoTimeout(READ_TIMEOUT_MILLISECONDS);
                socket.connect(new InetSocketAddress("127.0.0.1", mLocalSocksProxyPort), CONNECT_TIMEOUT_MILLISECONDS);

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
