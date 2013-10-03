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
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class TransportSecurity {

    private static final String LOG_TAG = "Transport Security";

    public static ServerSocket makeServerSocket(
            X509.KeyMaterial transportKeyMaterial) throws Utils.ApplicationError {
        try {
            SSLServerSocket sslServerSocket =
                    (SSLServerSocket)(TransportSecurity.getSSLContext(transportKeyMaterial, null).getServerSocketFactory().createServerSocket());
            sslServerSocket.setNeedClientAuth(true);
            sslServerSocket.setEnabledCipherSuites(TLS_REQUIRED_CIPHER_SUITES);
            sslServerSocket.setEnabledProtocols(TLS_REQUIRED_PROTOCOLS);
            return sslServerSocket;
        } catch (IllegalArgumentException e) {
            // TODO: log... unsupported CipherSuite or Protocol
            throw new Utils.ApplicationError(e);
        } catch (IOException e) {
            throw new Utils.ApplicationError(e);
        }
    }

    public static SSLContext getSSLContext(
            X509.KeyMaterial x509KeyMaterial,
            String friendCertificate) throws Utils.ApplicationError {
        try {
            KeyStore selfKeyStore = X509.makeKeyStore();
            X509.loadKeyMaterial(selfKeyStore, x509KeyMaterial);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("X509"); 
            keyManagerFactory.init(selfKeyStore, null);
            KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();
            
            KeyStore peerKeyStore = X509.makeKeyStore();
            if (friendCertificate != null) {
                X509.loadKeyMaterial(peerKeyStore, friendCertificate, null);
            } else {
                for (Data.Friend friend : Data.getInstance().getFriends()) {
                    try {
                        X509.loadKeyMaterial(peerKeyStore, friend.mPublicIdentity.mX509Certificate, null);
                    } catch (Utils.ApplicationError e) {
                        Log.addEntry(LOG_TAG, String.format("no certificate loaded for %s", friend.mPublicIdentity.mNickname));
                    }
                }
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
            trustManagerFactory.init(peerKeyStore);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            return sslContext;

        } catch (KeyStoreException e) {
            throw new Utils.ApplicationError(e);
        } catch (NoSuchAlgorithmException e) {
            throw new Utils.ApplicationError(e);
        } catch (UnrecoverableKeyException e) {
            throw new Utils.ApplicationError(e);
        } catch (KeyManagementException e) {
            throw new Utils.ApplicationError(e);
        }        
    }

    private static final String[] TLS_REQUIRED_CIPHER_SUITES = new String [] { "TLS_DHE_RSA_WITH_AES_128_CBC_SHA" };
    // TODO: DH-RSA-AES128-GCM-SHA256 not supported... no GCM or SHA256?
    // TODO: "ECDHE-ECDSA-AES128-GCM-SHA256"; Android support for ECC in TLS... (no JCCE for BC/SC)?
    // TODO: use TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA
    private static final String[] TLS_REQUIRED_PROTOCOLS = new String [] { "TLSv1.2" };
}
