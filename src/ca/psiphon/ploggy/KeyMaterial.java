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

public class KeyMaterial {
    
    public static class TransportKeyPair {
        public String mPublicKey;
        public String mPrivateKey;
    }
    
    public static class HiddenServiceIdentity {
        public String mHostname;
        public String mPrivateKey;
    }
    
    public static TransportKeyPair generateTransportKeyPair() {
        return null;
    }

    public static HiddenServiceIdentity generateHiddenServiceIdentity() {
        return null;
    }
}
