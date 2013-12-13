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
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import ch.boye.httpclientandroidlib.conn.ssl.SSLSocketFactory;

/**
 * Helpers for building custom TLS connections.
 *
 * Each TLS connection (both client- and server-side):
 * - Requires TLS 1.2
 * - Requires a strong CipherSuite (limited by what's commonly available on Android 4.1+)
 *   which includes perfect forward secrecy
 * - Requires mutual authentication using self key material and friend certificates
 */
public class TransportSecurity {

    private static final String LOG_TAG = "Transport Security";

    public static ServerSocket makeServerSocket(
            X509.KeyMaterial transportKeyMaterial,
            List<String> friendCertificates) throws Utils.ApplicationError {
        try {
            SSLContext sslContext = TransportSecurity.getSSLContext(transportKeyMaterial, friendCertificates);
            SSLServerSocket sslServerSocket = (SSLServerSocket)(sslContext.getServerSocketFactory().createServerSocket());
            sslServerSocket.setNeedClientAuth(true);
            sslServerSocket.setEnabledCipherSuites(TLS_REQUIRED_CIPHER_SUITES);
            sslServerSocket.setEnabledProtocols(TLS_REQUIRED_PROTOCOLS);
            return sslServerSocket;
        } catch (IllegalArgumentException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (IOException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    private static class ClientSSLSocketFactory extends SSLSocketFactory {

        public ClientSSLSocketFactory(SSLContext sslContext) {
            // Using ALLOW_ALL effectively disables hostname verification. Ploggy
            // simply checks that the peer is authenticating with the sole friend
            // certificate expected for this connection.
            super(sslContext, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        }

        @Override
        protected void prepareSocket(SSLSocket socket) throws IOException {
            //socket.setEnabledCipherSuites(TLS_REQUIRED_CIPHER_SUITES);
            socket.setEnabledProtocols(TLS_REQUIRED_PROTOCOLS);
        }
    }

    public static ClientSSLSocketFactory getClientSSLSocketFactory(SSLContext sslContext) {
        return new ClientSSLSocketFactory(sslContext);
    }

    public static SSLContext getSSLContext(
            X509.KeyMaterial x509KeyMaterial,
            List<String> friendCertificates) throws Utils.ApplicationError {
        try {
            KeyManager[] keyManagers = null;
            if (x509KeyMaterial != null) {
                KeyStore selfKeyStore = X509.makeKeyStore();
                X509.loadKeyMaterial(selfKeyStore, x509KeyMaterial);
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("X509");
                keyManagerFactory.init(selfKeyStore, null);
                keyManagers = keyManagerFactory.getKeyManagers();
            }

            KeyStore peerKeyStore = X509.makeKeyStore();
            for (String friendCertificate : friendCertificates) {
                X509.loadKeyMaterial(peerKeyStore, friendCertificate, null);
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
            trustManagerFactory.init(peerKeyStore);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

            SSLContext sslContext = SSLContext.getInstance(TLS_REQUIRED_PROTOCOL);
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            return sslContext;
        } catch (IllegalArgumentException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (GeneralSecurityException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    // Protocol specification

    // TODO: ECC disabled -- key generation works, but TLS fails in ClientHello
    //private static final String[] TLS_REQUIRED_CIPHER_SUITES = new String [] { "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA" };
    //private static final String TLS_REQUIRED_PROTOCOL = "TLSv1.2";

    // TODO: TLS_..._CBC_SHA256?
    // TODO: no GCM-SHA256 built-in, even on Android 4.1?; no JCCE for SpongyCastle to use its GCM-SHA256 with Android TLS?
    // TODO: TLS 1.2 only available on Android 4.1+?
    private static final String[] TLS_REQUIRED_CIPHER_SUITES = new String [] { "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA" };
    private static final String TLS_REQUIRED_PROTOCOL = "TLSv1.2";

    private static final String[] TLS_REQUIRED_PROTOCOLS = new String [] { TLS_REQUIRED_PROTOCOL };
}
