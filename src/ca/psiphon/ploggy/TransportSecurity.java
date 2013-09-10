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
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
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

    public static class KnownPeerCertificatesTrustManager implements X509TrustManager {
        
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (!isKnownPeerCertificate(chain)) {
                throw new CertificateException();
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (!isKnownPeerCertificate(chain)) {
                throw new CertificateException();
            }
        }
        
        private boolean isKnownPeerCertificate(X509Certificate[] chain) {
        	
            // TODO: http://www.thoughtcrime.org/blog/authenticity-is-broken-in-ssl-but-your-app-ha/
        	
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
    
    public static SSLContext getSSLContext(TransportSecurity.KeyMaterial transportKeyPair) throws Utils.ApplicationError {
        try {
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            transportKeyPair.deploy(keystore);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, null);
            // TODO: populate KnownPeerCertificatesTrustManager? Subscribe to re-populate? Or query Data on each checkTrusted
            TrustManager[] trustManagers = new TrustManager[] { new TransportSecurity.KnownPeerCertificatesTrustManager() }; 
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagers, new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new Utils.ApplicationError(e);
        }        
    }
}
