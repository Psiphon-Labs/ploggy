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
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Calendar;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import org.spongycastle.x509.X509V3CertificateGenerator;

/**
 * Helpers generating X509 key pairs and digital signing/verification.
 *
 * See "Crypto algorithm specifications" below for current algorithms and key strengths.
 */
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

    public static KeyMaterial generateKeyMaterial() throws Utils.ApplicationError {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_TYPE, "SC");
            keyPairGenerator.initialize(KEY_SPEC, new SecureRandom());
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
    
            // TODO: use http://www.bouncycastle.org/wiki/display/JA1/BC+Version+2+APIs
            X509V3CertificateGenerator certificateGenerator = new X509V3CertificateGenerator();

            // Using non-distinguishing validity dates and identifiers
            // TODO: use common -- e.g., generic Apache web server -- values? See what Tor does.
            Calendar calendar = Calendar.getInstance();
            calendar.set(2013, Calendar.JANUARY, 1);
            Date validityBeginDate = calendar.getTime(); 
            calendar.add(Calendar.YEAR, 30);
            Date validityEndDate = calendar.getTime();        
            X500Principal subjectDN = new X500Principal("CN=server.example.com");
            certificateGenerator.setSerialNumber(BigInteger.valueOf(1));
            certificateGenerator.setSubjectDN(subjectDN);
            certificateGenerator.setIssuerDN(subjectDN);
            certificateGenerator.setNotBefore(validityBeginDate);
            certificateGenerator.setNotAfter(validityEndDate);
            certificateGenerator.setPublicKey(keyPair.getPublic());
            certificateGenerator.setSignatureAlgorithm(SIGNATURE_TYPE);
            X509Certificate x509certificate = certificateGenerator.generate(keyPair.getPrivate(), "SC");

            return new KeyMaterial(
                    Utils.encodeBase64(x509certificate.getEncoded()),
                    Utils.encodeBase64(keyPair.getPrivate().getEncoded()));
        } catch (IllegalArgumentException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (GeneralSecurityException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    public static String sign(KeyMaterial keyMaterial, byte[] data) throws Utils.ApplicationError {
        return sign(keyMaterial.mPrivateKey, data);
    }
    
    public static String sign(String privateKey, byte[] data) throws Utils.ApplicationError {
        try {
            Signature signer = java.security.Signature.getInstance(SIGNATURE_TYPE);
            signer.initSign(decodePrivateKey(privateKey));
            signer.update(data);
            return Utils.encodeBase64(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }
    
    public static boolean verify(String certificate, byte[] data, String signature) throws Utils.ApplicationError {
        try {
            Signature verifier = java.security.Signature.getInstance(SIGNATURE_TYPE);
            verifier.initVerify(decodeCertificate(certificate));
            verifier.update(data);
            return verifier.verify(Utils.decodeBase64(signature));
        } catch (GeneralSecurityException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }
    
    public static byte[] getFingerprint(String ...params) throws Utils.ApplicationError {
        try {
            MessageDigest hash = MessageDigest.getInstance(FINGERPRINT_ALGORITHM);
            for (int i = 0; i < params.length; i++) {
                hash.update(params[i].getBytes("UTF-8"));
            }
            return hash.digest();
        } catch (UnsupportedEncodingException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (GeneralSecurityException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    public static KeyStore makeKeyStore() throws Utils.ApplicationError {
        try {
            KeyStore keyStore;
            keyStore = KeyStore.getInstance("BKS");
            keyStore.load(null);
            return keyStore;
        } catch (GeneralSecurityException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (IOException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }
    
    public static void loadKeyMaterial(
            KeyStore keyStore, KeyMaterial keyMaterial) throws Utils.ApplicationError {
        loadKeyMaterial(keyStore, keyMaterial.mCertificate, keyMaterial.mPrivateKey);
    }

    public static void loadKeyMaterial(
            KeyStore keyStore, String certificate, String privateKey) throws Utils.ApplicationError {
        try {
            X509Certificate x509certificate = decodeCertificate(certificate);
            String alias = x509certificate.getSubjectDN().getName();
            keyStore.setCertificateEntry(alias, x509certificate);    
            if (privateKey != null) {
                PrivateKey decodedPrivateKey = decodePrivateKey(privateKey); 
                keyStore.setKeyEntry(alias, decodedPrivateKey, null, new X509Certificate[] {x509certificate});
            }
        } catch (IllegalArgumentException e) {
            // TODO: ... malformed public key (generatePrivate) or invalid Base64
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (NullPointerException e) {
            // TODO: ...getSubjectDN returns null and/or throws NPE on invalid input
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (GeneralSecurityException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }
    
    private static X509Certificate decodeCertificate(String certificate)
            throws Utils.ApplicationError, CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance(CERTIFICATE_TYPE);
        return (X509Certificate)certificateFactory.generateCertificate(new ByteArrayInputStream(Utils.decodeBase64(certificate)));
    }

    private static PrivateKey decodePrivateKey(String privateKey)
            throws Utils.ApplicationError, NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory privateKeyFactory = KeyFactory.getInstance(KEY_TYPE);
        return privateKeyFactory.generatePrivate(new PKCS8EncodedKeySpec(Utils.decodeBase64(privateKey)));        
    }
    
    // Crypto algorithm specifications
    
    private static final String FINGERPRINT_ALGORITHM = "SHA-256";
    private static final String CERTIFICATE_TYPE = "X.509";

    // TODO: currently using RSA instead of ECC due to compatibility issues with Android TLS.
    //private static final String KEY_TYPE = "EC";
    //private static final AlgorithmParameterSpec KEY_SPEC = new ECGenParameterSpec("secp256r1");
    //private static final String SIGNATURE_TYPE = "SHA256withECDSA";

    private static final String KEY_TYPE = "RSA";
    private static final AlgorithmParameterSpec KEY_SPEC = new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4);
    private static final String SIGNATURE_TYPE = "SHA256withRSA";
}
