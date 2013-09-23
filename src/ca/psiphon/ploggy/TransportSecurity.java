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
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Scanner;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;

import org.spongycastle.openssl.PEMWriter;
import org.spongycastle.x509.X509V3CertificateGenerator;

import android.util.Base64;

public class TransportSecurity {

    public static class KeyMaterial {
        public final String mType;
        public final String mCertificate;
        public final String mPrivateKey;
        
        public KeyMaterial(String type, String certificate, String privateKey) {        
            mType = type;
            mCertificate = certificate;
            mPrivateKey = privateKey;
        }
        
        public static KeyMaterial generate() {
            // TODO: ...
            return null;
        }

        public Certificate getCertificate() {
            return new Certificate(mType, mCertificate);
        }
    }

    public static class Certificate {
        public final String mType;
        public final String mCertificate;
        
        public Certificate(String type, String certificate) {        
            mType = type;
            mCertificate = certificate;
        }
        
        public String getFingerprint() {
            // TODO: temp
            return "CE1D32CE0CFFD121E9FE74B94F366A8368A3A6890F2228A9E2B103196313BB22";
        }
        
        public Date getTimestamp() {
            // TODO: temp
            return new Date();
        }
    }

    static KeyMaterial generateKeyMaterial() throws Utils.ApplicationError {
        
        // from: http://code.google.com/p/xebia-france/wiki/HowToGenerateaSelfSignedX509CertificateInJava
        
        try {
            // TODO: validity dates?
            Date validityBeginDate = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
            Date validityEndDate = new Date(System.currentTimeMillis() + 10 * 365 * 24 * 60 * 60 * 1000);
        
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
            keyPairGenerator.initialize(RSA_KEY_SIZE, new SecureRandom());
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
    
            // TODO: us http://www.bouncycastle.org/wiki/display/JA1/BC+Version+2+APIs
            X509V3CertificateGenerator certificateGenerator = new X509V3CertificateGenerator();
            X500Principal dnName = new X500Principal(Utils.getRandomHexString(128));
        
            certificateGenerator.setSerialNumber(BigInteger.valueOf(1));
            certificateGenerator.setSubjectDN(dnName);
            certificateGenerator.setIssuerDN(dnName);
            certificateGenerator.setNotBefore(validityBeginDate);
            certificateGenerator.setNotAfter(validityEndDate);
            certificateGenerator.setPublicKey(keyPair.getPublic());
            certificateGenerator.setSignatureAlgorithm("SHA256WithRSAEncryption");
        
            X509Certificate certificate = certificateGenerator.generate(keyPair.getPrivate(), "BC");
    
            StringWriter pemCertificate = new StringWriter();
            PEMWriter pemWriter = new PEMWriter(pemCertificate);
            pemWriter.writeObject(certificate);
            pemWriter.flush();
            pemWriter.close(); // TODO: finally?
    
            StringWriter pemPrivateKey = new StringWriter();
            pemWriter = new PEMWriter(pemPrivateKey);
            pemWriter.writeObject(keyPair.getPrivate());
            pemWriter.flush();
            pemWriter.close();
            
            return new KeyMaterial(KEY_MATERIAL_TYPE, pemCertificate.toString(), pemPrivateKey.toString());
        } catch (NoSuchProviderException e) {
            throw new Utils.ApplicationError(e);            
        } catch (NoSuchAlgorithmException e) {
            throw new Utils.ApplicationError(e);            
        } catch (CertificateEncodingException e) {
            throw new Utils.ApplicationError(e);            
        } catch (InvalidKeyException e) {
            throw new Utils.ApplicationError(e);            
        } catch (IllegalStateException e) {
            throw new Utils.ApplicationError(e);            
        } catch (SignatureException e) {
            throw new Utils.ApplicationError(e);            
        } catch (IOException e) {
            throw new Utils.ApplicationError(e);            
        }
    }
    
    public static ServerSocket makeServerSocket(TransportSecurity.KeyMaterial transportKeyMaterial) throws Utils.ApplicationError {
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
            TransportSecurity.KeyMaterial transportKeyMaterial,
            TransportSecurity.Certificate serverPublicKey) throws Utils.ApplicationError {
        try {
            KeyStore privateKeyStore = KeyStore.getInstance( "PKCS12");
            privateKeyStore.load(new ByteArrayInputStream(transportKeyMaterial.mPrivateKey.getBytes()), null);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("X509"); 
            keyManagerFactory.init(privateKeyStore, null);
            KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();
            
            KeyStore certificateStore = KeyStore.getInstance(KeyStore.getDefaultType());
            certificateStore.load(null);
            if (serverPublicKey != null) {
                loadCertificateStore(certificateStore, serverPublicKey);
            } else {
                for (Data.Friend friend : Data.getInstance().getFriends()) {
                    loadCertificateStore(certificateStore, friend.mTransportCertificate);                    
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
            TransportSecurity.Certificate certificate) throws IOException, CertificateException, KeyStoreException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        X509Certificate x509certificate = (X509Certificate)certificateFactory.generateCertificate(
                new ByteArrayInputStream(pemToDer(certificate.mCertificate)));
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

    static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    private static final String KEY_MATERIAL_TYPE = "1";
    private static final int RSA_KEY_SIZE = 4096;
    private static final String[] TLS_REQUIRED_CIPHER_SUITES = new String [] { "DH-RSA-AES128-GCM-SHA256" };
    private static final String[] TLS_REQUIRED_PROTOCOLS = new String [] { "TLSv1.2" };
    // TODO: "ECDHE-ECDSA-AES128-GCM-SHA256"; Android support for ECC in TLS... (no JCCE for BC/SC)?
}
