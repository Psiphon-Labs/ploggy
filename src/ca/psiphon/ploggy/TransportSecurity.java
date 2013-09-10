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
import java.io.IOException;
import java.net.ServerSocket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import android.util.Base64;

public class TransportSecurity {
    
    public static String[] getRequiredTransportCipherSuites() {
        // TODO: http://www.openssl.org/docs/apps/ciphers.html#TLS_v1_2_cipher_suites
        return new String [] { "ECDHE-ECDSA-AES128-GCM-SHA256" };
    }
    
    public static String[] getRequiredTransportProtocols() {
        return new String [] { "TLSv1.2" };
    }   

    public static class KeyMaterial {
        public final String mType; // TODO: "PLOGGYv1"?
        public final String mPublicKey;
        public final String mPrivateKey;
        
        public KeyMaterial(String type, String publicKey, String privateKey) {        
            mType = type;
            mPublicKey = publicKey;
            mPrivateKey = privateKey;
        }
        
        public static KeyMaterial generate() {
            // TODO: ...
            return null;
        }

        public PublicKey getPublicKey() {
            return new PublicKey(mType, mPublicKey);
        }
        
        public X509Certificate toX509() throws CertificateException {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            byte[] decodedServerCertificate = Base64.decode(mPublicKey, Base64.NO_WRAP);
            return (X509Certificate)factory.generateCertificate(new ByteArrayInputStream(decodedServerCertificate));            
        }
        
        public void deploy(KeyStore keystore) {
            // TODO: ...
            // keystore.load(stream, null);
        }
    }

    public static class PublicKey {
        public final String mType; // TODO: "PLOGGYv1"?
        public final String mPublicKey;
        
        public PublicKey(String type, String publicKey) {        
            mType = type;
            mPublicKey = publicKey;
        }
        
        public X509Certificate toX509() throws CertificateException {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            byte[] decodedServerCertificate = Base64.decode(mPublicKey, Base64.NO_WRAP);
            return (X509Certificate)factory.generateCertificate(new ByteArrayInputStream(decodedServerCertificate));            
        }
        
        public void deploy(KeyStore keystore) {
            // TODO: ...
            // keystore.load(stream, null);
        }
    }

    public static class AllPeerCertificatesTrustManager implements X509TrustManager {
        
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (!isPeerCertificate(chain)) {
                throw new CertificateException();
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (!isPeerCertificate(chain)) {
                throw new CertificateException();
            }
        }
        
        private boolean isPeerCertificate(X509Certificate[] chain) {
            
            if (chain.length != 1) {
                return false;
            }

            try {
                Data.getInstance().getFriendByTransportCertificate(chain[0]);
                return true;
            } catch (Data.DataNotFoundException e) {
            }
            return false;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
    
    public static class SinglePeerCertificateTrustManager implements X509TrustManager {
        
        private X509Certificate mCertificate;
        
        public SinglePeerCertificateTrustManager(TransportSecurity.PublicKey publicKey) throws Utils.ApplicationError {
            try {
                mCertificate = publicKey.toX509();
            } catch (CertificateException e) {
                throw new Utils.ApplicationError(e);
            }
        }
        
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (!isFixedPeerCertificate(chain)) {
                throw new CertificateException();
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (!isFixedPeerCertificate(chain)) {
                throw new CertificateException();
            }
        }
        
        private boolean isFixedPeerCertificate(X509Certificate[] chain) {
            if (chain.length != 1) {
                return false;
            }

            try {
                return Arrays.equals(chain[0].getEncoded(), mCertificate.getEncoded());
            } catch (CertificateEncodingException e) {
                return false;
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
    
    public static SSLContext getSSLContext(
            TransportSecurity.KeyMaterial transportKeyMaterial,
            TransportSecurity.PublicKey peerPublicKey) throws Utils.ApplicationError {
        try {

            // TODO: http://www.thoughtcrime.org/blog/authenticity-is-broken-in-ssl-but-your-app-ha/
            // TODO: http://blog.chariotsolutions.com/2013/01/https-with-client-certificates-on.html
            // ... -- use keystore for server/peer certs?
            
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            transportKeyMaterial.deploy(keystore);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, null);
            TrustManager[] trustManagers;
            if (peerPublicKey == null) {
                trustManagers = new TrustManager[] { new TransportSecurity.AllPeerCertificatesTrustManager() };
            } else {
                trustManagers = new TrustManager[] { new TransportSecurity.SinglePeerCertificateTrustManager(peerPublicKey) };
            }
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagers, new SecureRandom());
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

    public static ServerSocket makeServerSocket(TransportSecurity.KeyMaterial transportKeyMaterial) throws Utils.ApplicationError {
        try {
            SSLServerSocket sslServerSocket =
                    (SSLServerSocket)(TransportSecurity.getSSLContext(transportKeyMaterial, null).getServerSocketFactory().createServerSocket());
            sslServerSocket.setNeedClientAuth(true);
            sslServerSocket.setEnabledCipherSuites(TransportSecurity.getRequiredTransportCipherSuites());
            sslServerSocket.setEnabledProtocols(TransportSecurity.getRequiredTransportProtocols());
            return sslServerSocket;
        } catch (IOException e) {
            throw new Utils.ApplicationError(e);
        }
    }
}
