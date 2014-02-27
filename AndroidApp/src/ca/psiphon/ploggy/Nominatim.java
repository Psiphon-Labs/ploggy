/*
 * Copyright (c) 2014, Psiphon Inc.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.location.Address;
import android.util.Pair;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Nominatim {
    private static final String LOG_TAG = "Nominatim";

    private static final String SERVER_CERT = "MIIDdjCCAl4CCQDRQO1f49f5yDANBgkqhkiG9w0BAQUFADB9MQswCQYDVQQGEwJDQTEQMA4GA1UECAwHT250YXJpbzEQMA4GA1UEBwwHVG9yb250bzEVMBMGA1UECgwMUHNpcGhvbiBJbmMuMRIwEAYDVQQLDAlOb21pbmF0aW0xHzAdBgNVBAMMFmR5dWlndXZrbml5c3ZxZ2Iub25pb24wHhcNMTMxMjE5MjAzODU2WhcNMTQwMTE4MjAzODU2WjB9MQswCQYDVQQGEwJDQTEQMA4GA1UECAwHT250YXJpbzEQMA4GA1UEBwwHVG9yb250bzEVMBMGA1UECgwMUHNpcGhvbiBJbmMuMRIwEAYDVQQLDAlOb21pbmF0aW0xHzAdBgNVBAMMFmR5dWlndXZrbml5c3ZxZ2Iub25pb24wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCezDWDljtTizOIY5mXiW2rWRGblRyIaYoUrgFoW+UTjVMM6pqFKu8CgKCgVdyyunZA8hAn7bRnmMS2nJ2BVO27nWLe6CtG9/YCf8ZkNafr8re2AqQT6OOK3v5DPKM/Kkr1VO2xwgDaZnB2r/XiDwhEnKuOD2XpdEKWtpdCAscv7+K/iyKd4EuvXWsL3PGG17Ukd1BcGpAb6yt0JpGsVoKv5A/e6BmjJtNPyhcQYWDonKCJC2YNggMIYF6IGJbK4VUWoVY/eRX064T+GIgJQnIET1tJWWLFli5eWEkOhAMKRpzem27eIHUSMZNRKK1VVK4rNdCzNVlqnAvTmOt9Dh0fAgMBAAEwDQYJKoZIhvcNAQEFBQADggEBAH1IIT0bXeQokwZ0vaEeOO+NKPC/WOj3FKnj7q1DctBQw4sgkyA9ABwgkdPh/0hUKjypuc7LMBlGdvJOYvr+CbVe8C0xp9X7nyx8QQQSpOCLKU2DXcswWM4pD5JK9PL9Gm0fpvDyp27fmnuuSjZRVEgwwWvcj3Zdu5tAvjdUI2YYi66rnphIt033sdpIgWrvOFbv64cE6Z4Q/K909pNrettz1W4DITk5tExARngt7IqcwUKhhLwtXmk8Nu9T1zGElAsug08Bjy17ZgdH4t3fVmtxrEHVSXI01ACJlZMMQL3E3aY+M97BACMKEAZ48GsN90v9+jBNY3YdI7f1BcHGvh0=";
    private static final String SERVER_ADDRESS = "dyuiguvkniysvqgb.onion";
    private static final int SERVER_PORT = 443;
    private static final String REQUEST_PATH = "/nominatim/reverse";

    public static class NominatimAddress extends Address {
        public NominatimAddress(Locale locale) {
            super(locale);
        }

        private String mDisplayName;

        public String getDisplayName() {
            return mDisplayName;
        }

        public void setDisplayName(String displayName) {
            mDisplayName = displayName;
        }

        @Override
        public String toString() {
            if (mDisplayName != null) {
                return mDisplayName;
            }

            return "";
        }
    }

    public static NominatimAddress getFromLocation(int torSocksProxyPort, double latitude, double longitude) {
        Locale locale = Locale.getDefault();

        NominatimAddress address = new NominatimAddress(locale);

        address.setLatitude(latitude);
        address.setLongitude(longitude);

        List<Pair<String,String>> requestParameters = new ArrayList<Pair<String, String>>();
        requestParameters.add(new Pair<String, String>("format", "json"));
        requestParameters.add(new Pair<String, String>("lat", String.valueOf(latitude)));
        requestParameters.add(new Pair<String, String>("lon", String.valueOf(longitude)));
        requestParameters.add(new Pair<String, String>("zoom", "18"));
        requestParameters.add(new Pair<String, String>("addressdetails", "1"));
        requestParameters.add(new Pair<String, String>("accept-language", locale.getLanguage()));

        String response;
        try {
            // TODO: reuse this pool (for as long as the current Tor connection is up)
            WebClientConnectionPool connectionPool =
                    new WebClientConnectionPool(SERVER_CERT, torSocksProxyPort);

            WebClientRequest webClientRequest =
                    new WebClientRequest(
                            connectionPool,
                        SERVER_ADDRESS,
                        SERVER_PORT,
                        WebClientRequest.RequestType.GET,
                        REQUEST_PATH).
                            requestParameters(requestParameters);

            response = webClientRequest.makeRequestAndLoadResponse();
        }
        catch (PloggyError e) {
            Log.addEntry(LOG_TAG, "reverse geocode failed: " + e.getMessage());
            return address;
        }

        JsonParser parser = new JsonParser();
        JsonObject responseObj = parser.parse(response).getAsJsonObject();

        if (responseObj.has("display_name")) {
            address.setDisplayName(responseObj.get("display_name").getAsString());
        }

        if (!responseObj.has("address")) {
            return address;
        }

        JsonObject addressObj = responseObj.get("address").getAsJsonObject();

        // Adapted from: http://code.google.com/p/osmbonuspack/source/browse/trunk/OSMBonusPack/src/org/osmdroid/bonuspack/location/GeocoderNominatim.java
        int addressIndex = 0;
        if (addressObj.has("road")) {
            address.setAddressLine(addressIndex++, addressObj.get("road").getAsString());
            address.setThoroughfare(addressObj.get("road").getAsString());
        }

        if (addressObj.has("suburb")) {
            //address.setAddressLine(addressIndex++, addressObj.getString("suburb"));
                    //not kept => often introduce "noise" in the address.
            address.setSubLocality(addressObj.get("suburb").getAsString());
        }

        if (addressObj.has("postcode")) {
            address.setAddressLine(addressIndex++, addressObj.get("postcode").getAsString());
            address.setPostalCode(addressObj.get("postcode").getAsString());
        }

        if (addressObj.has("city")) {
            address.setAddressLine(addressIndex++, addressObj.get("city").getAsString());
            address.setLocality(addressObj.get("city").getAsString());
        }
        else if (addressObj.has("town")) {
            address.setAddressLine(addressIndex++, addressObj.get("town").getAsString());
            address.setLocality(addressObj.get("town").getAsString());
        }
        else if (addressObj.has("village")) {
            address.setAddressLine(addressIndex++, addressObj.get("village").getAsString());
            address.setLocality(addressObj.get("village").getAsString());
        }

        if (addressObj.has("county")) { //France: departement
            address.setSubAdminArea(addressObj.get("county").getAsString());
        }

        if (addressObj.has("state")) { //France: region
            address.setAdminArea(addressObj.get("state").getAsString());
        }

        if (addressObj.has("country")) {
            address.setAddressLine(addressIndex++, addressObj.get("country").getAsString());
            address.setCountryName(addressObj.get("country").getAsString());
        }

        if (addressObj.has("country_code")) {
            address.setCountryCode(addressObj.get("country_code").getAsString());
        }

        /* Other possible OSM tags in Nominatim results not handled yet:
         * subway, golf_course, bus_stop, parking,...
         * house, house_number, building
         * city_district (13e Arrondissement)
         * road => or highway, ...
         * sub-city (like suburb) => locality, isolated_dwelling, hamlet ...
         * state_district
        */

        return address;
    }
}
