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
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import org.spongycastle.openssl.PEMWriter;
import org.spongycastle.x509.X509V3CertificateGenerator;

public class X509 {

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
        return null;
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
    
    private static final int RSA_KEY_SIZE = 4096;
    private static final String FINGERPRINT_ALGORITHM = "SHA-256";
}
