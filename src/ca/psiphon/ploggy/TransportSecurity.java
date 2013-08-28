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
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Set;

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

    public static class TransportKeyPair {
        public String mType; // TODO: "PLOGGYv1"?
        public String mPublicKey;
        public String mPrivateKey;
        
        public static TransportKeyPair generate() {
            return null;
        }

        public static TransportKeyPair fromJson(String json) {
            return null;
        }

        public String toJson(boolean stripPrivateKey) {
            // TODO: use http://nelenkov.blogspot.ca/2011/11/using-ics-keychain-api.html? use SqlCipher?
            return null;
        }
        
        public X509Certificate toX509() throws CertificateException {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            byte[] decodedServerCertificate = Base64.decode(mPublicKey, Base64.NO_WRAP);
            return (X509Certificate)factory.generateCertificate(new ByteArrayInputStream(decodedServerCertificate));            
        }
        
        public void deploy(KeyStore keystore) {
            // keystore.load(stream, null);
        }
    }

    public static class KnownPeerCertificatesTrustManager implements X509TrustManager {
        private Set<byte[]> mKnownPeerCertificates;
        
        KnownPeerCertificatesTrustManager(Set<X509Certificate> knownPeerCertificates) {
            for (X509Certificate knownPeerCertificate : knownPeerCertificates) {
                try {
                    mKnownPeerCertificates.add(knownPeerCertificate.getEncoded());
                } catch (CertificateEncodingException e) {
                    // Skip adding this certificate
                }
            }
        }

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
            if (chain.length != 1) {
                return false;
            }

            byte[] encodedCertificate = null;
            try {
               encodedCertificate = chain[0].getEncoded();
            } catch (CertificateEncodingException e) {
                return false;
            }
            
            // TODO: is contains equivalent to, e.g., Arrays.equals?
            return mKnownPeerCertificates.contains(encodedCertificate);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }    
    
    public static class HiddenServiceIdentity {
        public String mType; // TODO: "TORv1"?
        public String mHostname;
        public String mPrivateKey;

        public static HiddenServiceIdentity generate() {
            return null;
        }

        public static HiddenServiceIdentity fromJson(String json) {
            return null;
        }

        public String toJson() {
            return null;
        }
        
        public void deploy(KeyStore keystore) {
            // keystore.load(stream, passphrase);
        }
    }    
}
