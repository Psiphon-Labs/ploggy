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
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Scanner;

import javax.security.auth.x500.X500Principal;

import org.spongycastle.openssl.PEMWriter;
import org.spongycastle.x509.X509V3CertificateGenerator;

import android.util.Base64;

public class X509 {

    private static final String LOG_TAG = "X509";

    public static class KeyMaterial {
        public final String mCertificate;
        public final String mPrivateKey;
        
        public KeyMaterial(String certificate, String privateKey) {        
            mCertificate = certificate;
            mPrivateKey = privateKey;
        }
    }

    static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
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
            X500Principal subjectDN = new X500Principal("CN="+Utils.getRandomHexString(128));
        
            certificateGenerator.setSerialNumber(BigInteger.valueOf(1));
            certificateGenerator.setSubjectDN(subjectDN);
            certificateGenerator.setIssuerDN(subjectDN);
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
            
            return new KeyMaterial(pemCertificate.toString(), pemPrivateKey.toString());

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

    static byte[] sign(KeyMaterial keyMaterial, byte[] data) throws Utils.ApplicationError {
        // TODO: ...
        return Utils.getRandomHexString(1024).getBytes();
    }
    
    static byte[] verify(String certificate, byte[] data) throws Utils.ApplicationError {
        // TODO: ...
        return null;
    }
    
    public static byte[] getFingerprint(String ...params) throws Utils.ApplicationError {
        try {
            MessageDigest hash = MessageDigest.getInstance(FINGERPRINT_ALGORITHM);
            for (int i = 0; i < params.length; i++) {
                hash.update(params[i].getBytes("UTF-8"));
            }
            return hash.digest();
        } catch (NoSuchAlgorithmException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        } catch (UnsupportedEncodingException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        }
    }

    public static KeyStore makeKeyStore() throws Utils.ApplicationError {
        try {
            KeyStore keyStore;
            keyStore = KeyStore.getInstance("BKS");
            keyStore.load(null);
            return keyStore;
        } catch (KeyStoreException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        } catch (NoSuchAlgorithmException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        } catch (CertificateException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        } catch (IOException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        }
    }
    
    public static void loadKeyMaterial(
            KeyStore keyStore, String certificate, String privateKey) throws Utils.ApplicationError {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate x509certificate = (X509Certificate)certificateFactory.generateCertificate(
                    new ByteArrayInputStream(pemToDer(certificate)));
            String alias = x509certificate.getSubjectDN().getName();
            keyStore.setCertificateEntry(alias, x509certificate);    
            if (privateKey != null) {
                KeyFactory privateKeyFactory = KeyFactory.getInstance("RSA");
                RSAPrivateKey rsaPrivateKey = 
                        (RSAPrivateKey)privateKeyFactory.generatePrivate(
                                new PKCS8EncodedKeySpec(pemToDer(privateKey)));
                keyStore.setKeyEntry(alias, rsaPrivateKey, null, new X509Certificate[] {x509certificate});
            }
        } catch (NullPointerException e) {
            // TODO: ...getSubjectDN returns null and/or throws NPE on invalid input
            Log.addEntry(LOG_TAG, "invalid certificate");
            throw new Utils.ApplicationError(e);
        } catch (CertificateException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        } catch (KeyStoreException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        } catch (NoSuchAlgorithmException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        } catch (InvalidKeySpecException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        } catch (IOException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        }
    }
    
    public static void loadKeyMaterial(
            KeyStore keyStore, KeyMaterial keyMaterial) throws Utils.ApplicationError {
        loadKeyMaterial(keyStore, keyMaterial.mCertificate, keyMaterial.mPrivateKey);
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

    // TODO: slow
    //private static final int RSA_KEY_SIZE = 4096;
    private static final int RSA_KEY_SIZE = 1024;
    private static final String FINGERPRINT_ALGORITHM = "SHA-256";
}
