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
    
    private static final String LOG_TAG = "Hidden Service";
    
    public static class KeyMaterial {
        public final String mHostname;
        public final String mPrivateKey;

        public KeyMaterial(String hostname, String privateKey) {        
            mHostname = hostname;
            mPrivateKey = privateKey;
        }
    }

    public static KeyMaterial generateKeyMaterial() throws Utils.ApplicationError {
        TorWrapper tor = new TorWrapper(TorWrapper.Mode.MODE_GENERATE_KEY_MATERIAL);
        tor.start();
        return tor.getKeyMaterial();
    }
}
