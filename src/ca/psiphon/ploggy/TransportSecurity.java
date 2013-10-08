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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

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
            // TODO: log... unsupported CipherSuite or Protocol
            throw new Utils.ApplicationError(e);
        } catch (IOException e) {
            throw new Utils.ApplicationError(e);
        }
    }
    
    // TODO: SSLCertificateSocketFactory? SSLSessionCache?
    private static class CustomSSLSocketFactory extends SSLSocketFactory {
        
        SSLSocketFactory mSocketFactory;
        
        CustomSSLSocketFactory(SSLContext sslContext) {
            mSocketFactory = sslContext.getSocketFactory();
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return mSocketFactory.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return mSocketFactory.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            SSLSocket socket = (SSLSocket)mSocketFactory.createSocket(s, host, port, autoClose);
            socket.setEnabledCipherSuites(TLS_REQUIRED_CIPHER_SUITES);
            socket.setEnabledProtocols(TLS_REQUIRED_PROTOCOLS);
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
            SSLSocket socket = (SSLSocket)mSocketFactory.createSocket(host, port);
            socket.setEnabledCipherSuites(TLS_REQUIRED_CIPHER_SUITES);
            socket.setEnabledProtocols(TLS_REQUIRED_PROTOCOLS);
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            SSLSocket socket = (SSLSocket)mSocketFactory.createSocket(host, port);
            socket.setEnabledCipherSuites(TLS_REQUIRED_CIPHER_SUITES);
            socket.setEnabledProtocols(TLS_REQUIRED_PROTOCOLS);
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
            SSLSocket socket = (SSLSocket)mSocketFactory.createSocket(host, port, localHost, localPort);
            socket.setEnabledCipherSuites(TLS_REQUIRED_CIPHER_SUITES);
            socket.setEnabledProtocols(TLS_REQUIRED_PROTOCOLS);
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            SSLSocket socket = (SSLSocket)mSocketFactory.createSocket(address, port, localAddress, localPort);
            socket.setEnabledCipherSuites(TLS_REQUIRED_CIPHER_SUITES);
            socket.setEnabledProtocols(TLS_REQUIRED_PROTOCOLS);
            return socket;
        }        
    }
    
    public static SSLSocketFactory getSSLSocketFactory(SSLContext sslContext) {
        return new CustomSSLSocketFactory(sslContext);
    }

    private static class CustomHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            // TODO: ...ok as long as trust manager only has *one* peer certificate?
            return true;
        }        
    }
    
    public static HostnameVerifier getHostnameVerifier() {
        return new CustomHostnameVerifier();
    }
    
    public static SSLContext getSSLContext(
            X509.KeyMaterial x509KeyMaterial,
            List<String> friendCertificates) throws Utils.ApplicationError {
        try {
            KeyStore selfKeyStore = X509.makeKeyStore();
            X509.loadKeyMaterial(selfKeyStore, x509KeyMaterial);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("X509");
            keyManagerFactory.init(selfKeyStore, null);
            KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();
            
            KeyStore peerKeyStore = X509.makeKeyStore();
            for (String friendCertificate : friendCertificates) {
                X509.loadKeyMaterial(peerKeyStore, friendCertificate, null);
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
            trustManagerFactory.init(peerKeyStore);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

            SSLContext sslContext = SSLContext.getInstance(TLS_REQUIRED_PROTOCOL);
            sslContext.init(keyManagers, trustManagers, new SecureRandom());

            /*
            // TODO: temp! =========
            android.util.Log.e(LOG_TAG, "getSSLContext");
            android.util.Log.e(LOG_TAG, sslContext.getProtocol());
            android.util.Log.e(LOG_TAG, sslContext.getProvider().getName());
            for (String protocol : sslContext.getSupportedSSLParameters().getProtocols()) {
                android.util.Log.e(LOG_TAG, "supported protocol: " + protocol);
            }
            for (String cipherSuite : sslContext.getSupportedSSLParameters().getCipherSuites()) {
                android.util.Log.e(LOG_TAG, "supported cipher suite: " + cipherSuite);
            }
            for (String protocol : sslContext.getDefaultSSLParameters().getProtocols()) {
                android.util.Log.e(LOG_TAG, "default protocol: " + protocol);
            }
            for (String cipherSuite : sslContext.getDefaultSSLParameters().getCipherSuites()) {
                android.util.Log.e(LOG_TAG, "default cipher suite: " + cipherSuite);
            }
            android.util.Log.e(LOG_TAG, "need client auth: " + (sslContext.getDefaultSSLParameters().getNeedClientAuth() ? "yes" : "no"));
            //=======================
            */
            
            return sslContext;
        } catch (GeneralSecurityException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        } catch (IllegalArgumentException e) {
            // TODO: log ...
            throw new Utils.ApplicationError(e);
        }
    }

    // TODO: ...no GCM-SHA256 built-in; no JCCE for SpongyCastle
    // TODO: TLS_..._CBC_SHA256 (requires TLS 1.2?)
    // TODO: TLS 1.2 not available on Android 4.0, only 4.1+?
    //private static final String[] TLS_REQUIRED_CIPHER_SUITES = new String [] { "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA" };
    //private static final String TLS_REQUIRED_PROTOCOL = "TLSv1.2";
    // TODO: temp!
    private static final String[] TLS_REQUIRED_CIPHER_SUITES = new String [] { "TLS_DHE_RSA_WITH_AES_128_CBC_SHA" };
    private static final String TLS_REQUIRED_PROTOCOL = "SSLv3";

    private static final String[] TLS_REQUIRED_PROTOCOLS = new String [] { TLS_REQUIRED_PROTOCOL };
}
