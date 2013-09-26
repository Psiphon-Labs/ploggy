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

import java.util.Timer;
import java.util.TimerTask;

public class Dummy {
	
	private static Timer mTimer;

	public static void doDummyData() {
		try {
			Data data = Data.getInstance();

	        //data.insertOrUpdateFriend(new Data.Friend("Nickname1", new TransportSecurity.Certificate("", "Certificate1"), new HiddenService.Identity("", "Hostname1")));
	        //data.insertOrUpdateFriend(new Data.Friend("Nickname2", new TransportSecurity.Certificate("", "Certificate2"), new HiddenService.Identity("", "Hostname2")));
	        //data.insertOrUpdateFriend(new Data.Friend("Nickname3", new TransportSecurity.Certificate("", "Certificate3"), new HiddenService.Identity("", "Hostname3")));
			/*
            data.getFriends().add(new Data.Friend("Nickname0", new TransportSecurity.Certificate("", "Certificate0"), new HiddenService.Identity("", "Hostname0")));
	        data.getFriends().add(new Data.Friend("Nickname1", new TransportSecurity.Certificate("", "Certificate1"), new HiddenService.Identity("", "Hostname1")));
	        data.getFriends().add(new Data.Friend("Nickname2", new TransportSecurity.Certificate("", "Certificate2"), new HiddenService.Identity("", "Hostname2")));
            data.getFriends().add(new Data.Friend("Nickname3", new TransportSecurity.Certificate("", "Certificate3"), new HiddenService.Identity("", "Hostname3")));
            data.getFriends().add(new Data.Friend("Nickname4", new TransportSecurity.Certificate("", "Certificate4"), new HiddenService.Identity("", "Hostname4")));
            data.getFriends().add(new Data.Friend("Nickname5", new TransportSecurity.Certificate("", "Certificate5"), new HiddenService.Identity("", "Hostname5")));
            data.getFriends().add(new Data.Friend("Nickname6", new TransportSecurity.Certificate("", "Certificate6"), new HiddenService.Identity("", "Hostname6")));
            data.getFriends().add(new Data.Friend("Nickname7", new TransportSecurity.Certificate("", "Certificate7"), new HiddenService.Identity("", "Hostname7")));
            data.getFriends().add(new Data.Friend("Nickname8", new TransportSecurity.Certificate("", "Certificate8"), new HiddenService.Identity("", "Hostname8")));
            data.getFriends().add(new Data.Friend("Nickname9", new TransportSecurity.Certificate("", "Certificate9"), new HiddenService.Identity("", "Hostname9")));
            */
			for (int i = 0; i < 10; i++) {
			    String nickname = String.format("Nickname%02d", i);
			    data.insertOrUpdateFriend(
			            new Data.Friend(
			                    nickname,
			                    new TransportSecurity.Certificate("", Utils.getRandomHexString(1024)),
			                    new HiddenService.Identity("", Utils.getRandomHexString(64))));
			}
		} catch (Utils.ApplicationError e) {
			// TODO: ...
		}

		mTimer = new Timer();
		mTimer.schedule(
                new TimerTask() {          
                    @Override
                    public void run() {
                    	Log.addEntry("tag", "message");
                    }
                },
                1000,
                1000);        
	}
}
