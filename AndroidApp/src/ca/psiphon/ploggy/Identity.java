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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Representation of Ploggy public Identity.
 * 
 * Users exchange public identities, each of which contains an X509 certificate for
 * authentication and a Hidden Service public key for routing. The identity also contains
 * the user's selected nickname. All of the identity parameters are bound together with a
 * digital signature which can be verified using the X509 certificate.
 */
public class Identity {
    
    private static final String LOG_TAG = "Identity";

    // TODO: distinct root cert and server/client (transport) certs?
    
    public static class PublicIdentity {
        public final String mNickname;
        public final String mX509Certificate;
        public final String mHiddenServiceHostname;
        public final String mSignature;
        
        public PublicIdentity(String nickname, String x509Certificate, String hiddenServiceHostname, String signature) {        
            mNickname = nickname;
            mX509Certificate = x509Certificate;
            mHiddenServiceHostname = hiddenServiceHostname;
            mSignature = signature;
        }
        
        public byte[] getFingerprint() throws Utils.ApplicationError {
            return X509.getFingerprint(mNickname, mX509Certificate, mHiddenServiceHostname);
        }
        
        public String getHiddenServiceHostnameUri() throws Utils.ApplicationError {
            return new String(Utils.decodeBase64(mHiddenServiceHostname)).trim();
        }
    }

    public static class PrivateIdentity {
        public final String mX509PrivateKey;
        public final String mHiddenServicePrivateKey;
        
        public PrivateIdentity(String x509PrivateKey, String hiddenServicePrivateKey) {        
            mX509PrivateKey = x509PrivateKey;
            mHiddenServicePrivateKey = hiddenServicePrivateKey;
        }
    }

    private static byte[] getPublicSigningData(
            String nickname,
            String rootCertificate,
            String hiddenServiceHostname) throws Utils.ApplicationError {
        try {
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            byteArray.write(nickname.getBytes("UTF-8"));
            byteArray.write(rootCertificate.getBytes("UTF-8"));
            byteArray.write(hiddenServiceHostname.getBytes("UTF-8"));
            return byteArray.toByteArray();
        } catch (IOException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }
    
    public static PublicIdentity makeSignedPublicIdentity(
            String nickname,
            X509.KeyMaterial x509KeyMaterial,
            HiddenService.KeyMaterial hiddenServiceKeyMaterial) throws Utils.ApplicationError {        
        String signature = X509.sign(
                x509KeyMaterial,
                getPublicSigningData(
                        nickname,
                        x509KeyMaterial.mCertificate,
                        hiddenServiceKeyMaterial.mHostname));
        return new PublicIdentity(
                nickname,
                x509KeyMaterial.mCertificate,
                hiddenServiceKeyMaterial.mHostname,
                signature);
    }

    public static boolean verifyPublicIdentity(PublicIdentity publicIdentity) {
        // TODO: implement        
        return false;
    }

    public static PrivateIdentity makePrivateIdentity(X509.KeyMaterial x509KeyMaterial, HiddenService.KeyMaterial hiddenServiceKeyMaterial) {
        return new PrivateIdentity(
                x509KeyMaterial.mPrivateKey,
                hiddenServiceKeyMaterial.mPrivateKey);
    }
}
