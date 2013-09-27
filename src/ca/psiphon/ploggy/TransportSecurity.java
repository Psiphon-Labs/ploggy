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
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Scanner;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import android.util.Base64;

public class TransportSecurity {

    public static ServerSocket makeServerSocket(
            X509.KeyMaterial transportKeyMaterial) throws Utils.ApplicationError {
        try {
            SSLServerSocket sslServerSocket =
                    (SSLServerSocket)(TransportSecurity.getSSLContext(transportKeyMaterial, null).getServerSocketFactory().createServerSocket());
            sslServerSocket.setNeedClientAuth(true);
            sslServerSocket.setEnabledCipherSuites(TLS_REQUIRED_CIPHER_SUITES);
            sslServerSocket.setEnabledProtocols(TLS_REQUIRED_PROTOCOLS);
            return sslServerSocket;
        } catch (IOException e) {
            throw new Utils.ApplicationError(e);
        }
    }

    public static SSLContext getSSLContext(
            X509.KeyMaterial x509KeyMaterial,
            String friendCertificate) throws Utils.ApplicationError {
        try {
            KeyStore privateKeyStore = KeyStore.getInstance( "PKCS12");
            privateKeyStore.load(new ByteArrayInputStream(x509KeyMaterial.mPrivateKey.getBytes()), null);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("X509"); 
            keyManagerFactory.init(privateKeyStore, null);
            KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();
            
            KeyStore certificateStore = KeyStore.getInstance(KeyStore.getDefaultType());
            certificateStore.load(null);
            if (friendCertificate != null) {
                loadCertificateStore(certificateStore, friendCertificate);
            } else {
                for (Data.Friend friend : Data.getInstance().getFriends()) {
                    loadCertificateStore(certificateStore, friend.mPublicIdentity.mX509Certificate);                    
                }
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
            trustManagerFactory.init(certificateStore);
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
        } catch (CertificateException e) {
            throw new Utils.ApplicationError(e);
        } catch (IOException e) {
            throw new Utils.ApplicationError(e);
        }        
    }

    private static void loadCertificateStore(
            KeyStore certificateStore,
            String certificate) throws IOException, CertificateException, KeyStoreException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        X509Certificate x509certificate = (X509Certificate)certificateFactory.generateCertificate(
                new ByteArrayInputStream(pemToDer(certificate)));
        String alias = x509certificate.getSubjectX500Principal().getName();
        certificateStore.setCertificateEntry(alias, x509certificate);        
    }
    
    private static byte[] pemToDer(String pemEncodedValue) throws IOException {
        Scanner scanner = new Scanner(pemEncodedValue);
        StringBuilder buffer = new StringBuilder();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if(!line.startsWith("--")){
                buffer.append(line);
            }
        }
        return Base64.decode(buffer.toString(), Base64.DEFAULT);
    }

    private static final String[] TLS_REQUIRED_CIPHER_SUITES = new String [] { "DH-RSA-AES128-GCM-SHA256" };
    private static final String[] TLS_REQUIRED_PROTOCOLS = new String [] { "TLSv1.2" };
    // TODO: "ECDHE-ECDSA-AES128-GCM-SHA256"; Android support for ECC in TLS... (no JCCE for BC/SC)?
}
