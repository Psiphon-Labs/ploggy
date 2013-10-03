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
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Calendar;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

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
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_TYPE, "SC");
            keyPairGenerator.initialize(KEY_SPEC, new SecureRandom());
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
    
            // TODO: use http://www.bouncycastle.org/wiki/display/JA1/BC+Version+2+APIs
            X509V3CertificateGenerator certificateGenerator = new X509V3CertificateGenerator();
            // TODO: validity dates?
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.add(Calendar.DATE, 1);
            Date validityBeginDate = calendar.getTime();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.add(Calendar.YEAR, 30);
            Date validityEndDate = calendar.getTime();        
            X500Principal subjectDN = new X500Principal("CN=" + Utils.getRandomHexString(128));
            certificateGenerator.setSerialNumber(BigInteger.valueOf(1));
            certificateGenerator.setSubjectDN(subjectDN);
            certificateGenerator.setIssuerDN(subjectDN);
            certificateGenerator.setNotBefore(validityBeginDate);
            certificateGenerator.setNotAfter(validityEndDate);
            certificateGenerator.setPublicKey(keyPair.getPublic());
            certificateGenerator.setSignatureAlgorithm(SIGNATURE_TYPE);
            X509Certificate x509certificate = certificateGenerator.generate(keyPair.getPrivate(), "SC");

            return new KeyMaterial(
                    Base64.encodeToString(x509certificate.getEncoded(), Base64.NO_WRAP),
                    Base64.encodeToString(keyPair.getPrivate().getEncoded(), Base64.NO_WRAP));
        } catch (GeneralSecurityException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        } catch (IllegalArgumentException e) {
            // TODO: log... unsupported algo, etc.
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
        } catch (GeneralSecurityException e) {
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
        } catch (GeneralSecurityException e) {
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
                    new ByteArrayInputStream(Base64.decode(certificate, Base64.DEFAULT)));
            String alias = x509certificate.getSubjectDN().getName();
            keyStore.setCertificateEntry(alias, x509certificate);    
            if (privateKey != null) {
                KeyFactory privateKeyFactory = KeyFactory.getInstance(KEY_TYPE);
                PrivateKey decodedPrivateKey = 
                        privateKeyFactory.generatePrivate(
                                new PKCS8EncodedKeySpec(Base64.decode(privateKey, Base64.DEFAULT)));
                keyStore.setKeyEntry(alias, decodedPrivateKey, null, new X509Certificate[] {x509certificate});
            }
        } catch (GeneralSecurityException e) {
            // TODO: log
            throw new Utils.ApplicationError(e);
        } catch (IllegalArgumentException e) {
            // TODO: log... malformed public key (generatePrivate) or invalid Base64
            throw new Utils.ApplicationError(e);
        } catch (NullPointerException e) {
            // TODO: ...getSubjectDN returns null and/or throws NPE on invalid input
            Log.addEntry(LOG_TAG, "invalid certificate");
            throw new Utils.ApplicationError(e);
        }
    }
    
    public static void loadKeyMaterial(
            KeyStore keyStore, KeyMaterial keyMaterial) throws Utils.ApplicationError {
        loadKeyMaterial(keyStore, keyMaterial.mCertificate, keyMaterial.mPrivateKey);
    }
    
    private static final String KEY_TYPE = "EC";
    private static final AlgorithmParameterSpec KEY_SPEC = new ECGenParameterSpec("secp256r1");
    private static final String SIGNATURE_TYPE = "SHA256withECDSA";
    private static final String FINGERPRINT_ALGORITHM = "SHA-256";
}
