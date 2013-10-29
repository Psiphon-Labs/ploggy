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

/**
 * Helpers and defined values for verifying/enforcing Ploggy protocol.
 */
public class Protocol {
    
    public static final String WEB_SERVER_PROTOCOL = "https";
    
    public static final int WEB_SERVER_VIRTUAL_PORT = 443;
    
    public static final String RESPONSE_MIME_TYPE = "application/json";
    
    public static final String PUSH_STATUS_REQUEST_PATH = "/pushStatus";

    public static final String PULL_STATUS_REQUEST_PATH = "/pullStatus";

    // TODO: adjust for foreground, battery, sleep, network type 
    public static final int PULL_PERIOD_IN_MILLISECONDS = 5 * 60 * 1000;
    
    public static boolean isValidNickname(String nickname) {
        // TODO: valid characters?
        return nickname.length() > 0;
    }

    public static void validatePublicIdentity(Identity.PublicIdentity publicIdentity) throws Utils.ApplicationError {
        // TODO: Nickname valid, cert valid, hostname valid
        // Identity.verifyPublicIdentity(friend.mPublicIdentity);
    }

    public static void validateStatus(Data.Status status) throws Utils.ApplicationError {
        // TODO: timestamp, long, lat, street address
    }
}
