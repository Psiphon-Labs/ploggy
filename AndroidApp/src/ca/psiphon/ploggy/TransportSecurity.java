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
import java.net.ServerSocket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import ch.boye.httpclientandroidlib.conn.ssl.SSLSocketFactory;
import ch.boye.httpclientandroidlib.conn.ssl.X509HostnameVerifier;

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

    // Checks that server hostname (.onion domain) and presented certificate matches friend record
    private static class PloggyHiddenServiceHostnameVerifier implements X509HostnameVerifier {

        private final Data mData;

        PloggyHiddenServiceHostnameVerifier(Data data) {
            mData = data;
        }

        @Override
        public boolean verify(String hostname, SSLSession sslSession) {
            try {
                Certificate[] certificates = sslSession.getPeerCertificates();
                if (certificates.length != 1) {
                    return false;
                }
                return verify(hostname, Utils.encodeBase64(certificates[0].getEncoded()));
            } catch (SSLPeerUnverifiedException e) {
            } catch (CertificateEncodingException e) {
            }
            return false;
        }

        @Override
        public void verify(String hostname, SSLSocket sslSocket) throws IOException {
            if (!verify(hostname, sslSocket.getSession())) {
                throw new IOException("verify certificate failed");
            }
        }

        @Override
        public void verify(String hostname, X509Certificate certificate) throws SSLException {
            try {
                if (verify(hostname, Utils.encodeBase64(certificate.getEncoded()))) {
                    return;
                }
            } catch (CertificateEncodingException e) {
            }
            throw new SSLException("verify certificate failed");
        }

        @Override
        public void verify(String hostname, String[] commonNames, String[] subjectAlts) throws SSLException {
            if (commonNames.length < 1 || !hostname.equals(commonNames[0])) {
                throw new SSLException("unexpected hostname in certificate");
            }
        }

        private boolean verify(String hostname, String certificate) {
            try {
                Data.Friend friend = mData.getFriendByCertificateOrThrow(certificate);
                return hostname.equals(friend.mPublicIdentity.mHiddenServiceHostname);
            } catch (Utils.ApplicationError e) {
            }
            return false;
        }
    }

    private static class ClientSSLSocketFactory extends SSLSocketFactory {

        // This mode expects all friend certificates to be loaded and uses a custom
        // verifier that checks the presented server certificate matches the friend's hidden
        // service hostname
        public ClientSSLSocketFactory(SSLContext sslContext, Data data) {
            super(sslContext, new PloggyHiddenServiceHostnameVerifier(data));
        }

        // This mode should be used only when a single expected server certificate is loaded
        public ClientSSLSocketFactory(SSLContext sslContext) {
            super(sslContext, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        }

        @Override
        protected void prepareSocket(SSLSocket socket) throws IOException {
            socket.setEnabledCipherSuites(TLS_REQUIRED_CIPHER_SUITES);
            socket.setEnabledProtocols(TLS_REQUIRED_PROTOCOLS);
        }
    }

    public static ClientSSLSocketFactory getClientSSLSocketFactory(SSLContext sslContext, Data data) {
        return new ClientSSLSocketFactory(sslContext, data);
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
    private static final String[] TLS_REQUIRED_CIPHER_SUITES = new String [] {
        "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
        "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
        "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
        "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
        "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
        "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
        "TLS_DHE_DSS_WITH_AES_256_CBC_SHA"
        };
    private static final String TLS_REQUIRED_PROTOCOL = "TLSv1.2";

    private static final String[] TLS_REQUIRED_PROTOCOLS = new String [] { TLS_REQUIRED_PROTOCOL };
}
