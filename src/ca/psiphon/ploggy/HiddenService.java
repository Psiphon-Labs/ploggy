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

public class HiddenService {
    
    public static class KeyMaterial {
        public final String mHostname;
        public final String mPrivateKey;

        public KeyMaterial(String hostname, String privateKey) {        
            mHostname = hostname;
            mPrivateKey = privateKey;
        }
    }
    
    public static KeyMaterial generateKeyMaterial() {
        // TODO: really generate
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new KeyMaterial(Utils.getRandomHexString(64), Utils.getRandomHexString(1024));
    }
}
