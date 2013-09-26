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

public class Protocol {
    
    public static final String WEB_SERVER_PROTOCOL = "https";
    
    public static final int WEB_SERVER_VIRTUAL_PORT = 443;
    
    public static final String GET_STATUS_REQUEST_PATH = "/status";

    public static boolean isValidNickname(String nickname) {
        // TODO: characters?
        return nickname.length() > 0;
    }

    public static boolean isValidFriend(Data.Friend friend) {
        // TODO: check nickname, cert, etc.
        return true;
    }
}
