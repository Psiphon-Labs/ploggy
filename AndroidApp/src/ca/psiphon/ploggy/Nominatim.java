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

        String certString = "MIIFoTCCA4mgAwIBAgIJAK969hZPMX8pMA0GCSqGSIb3DQEBBQUAMGcxCzAJBgNVBAYTAkNBMRAwDgYDVQQIDAdPbnRhcmlvMRAwDgYDVQQHDAdUb3JvbnRvMRUwEwYDVQQKDAxQc2lwaG9uIEluYy4xHTAbBgNVBAMMFG5vbWluYXRpbS5wc2lwaG9uLmNhMB4XDTEzMTIwOTIwMjQyOVoXDTE0MDEwODIwMjQyOVowZzELMAkGA1UEBhMCQ0ExEDAOBgNVBAgMB09udGFyaW8xEDAOBgNVBAcMB1Rvcm9udG8xFTATBgNVBAoMDFBzaXBob24gSW5jLjEdMBsGA1UEAwwUbm9taW5hdGltLnBzaXBob24uY2EwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQDFdaK14G6fsxAG6K3XVliyUf6RX9hEXykI+0NvvVpXJLWZSA1ZJ6ezPlCia03IvkEOx1TNbKlz8kmy1ut2XP12+egVBlFYE+h2n0Hs4nu8mBTQgrZWIq9njyhOUvIEXmXydEKFiKvgGaU+VkNDY7w2Ps4W8Yp20ZAK164Exy9FSbiyvAjsIoGOyf34ImJp7lgLHOl9poRY4rIjzJ0TK5gSd1dh/e5qYxnnPLTeV54pXjjmmIr69/7WfqEJjQ06Z7snHMD//YdnAZiXbLdJg8Kp6CA299AsPdhwoh28GnPpArA4OFSkPu+7WoP8ze28K/4PxeBrXC+mYI23uub81tw/5dPHunROWZ5wDbPO7u6zxltzDAIaIWdxr6lzNRHGMRjgQYkt6VDl/2LQubEgD9S9sVZ/PohprivjZjXXXsoovWco7gPakhfr4D7jby6mYANegpNkKBjWFw9fPWtsiZXBJAzkorZ5d9LOWY+rDwblFtTopHStSSaPg+gunc91jj6g8CZ/PbIOtfhNJFClaP0+nmYrsiclxdaVynAfwiG73x9UcQOqA9TzkJJCtQq+kpR8iJkdVaXOttDAWBeWIuIU2wr+R1X7j1DWnBKkNXrzUFTEtD1MC2XNryOwZmG/q2a1ztxq6qLjPubHNqDG9YGWArIIWUxum32cJA6Nxmib5wIDAQABo1AwTjAdBgNVHQ4EFgQUt+Wbr5CGJ8cCqhqZYbVFxNmTwkcwHwYDVR0jBBgwFoAUt+Wbr5CGJ8cCqhqZYbVFxNmTwkcwDAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQUFAAOCAgEAjAwhEer8TsfvJTs7iD8fJuRCqVaKJ4iKZ9qvCeJPz6/UZcGvT5LsQOVuHctdcfWWJ8Lucr8l5t93bFJadEhdKNaqKDa8XWtBE3Jo87t+1WTkMYFoWP/kpne7XLvwe55O+rPNGRdAtSpcrZa0jgtoJomuGaXPp7WzptIjoPlZr1jjBTXrYbaJy/569gAnryNp9grT/7n4uBMjRWKLduAMbt5pPdXFl5rgxB7BiBqXzTnBXeg9Mf26BlMGPqRwuDxgTCimoBv4KQKYg+JVJetKoLSOyRjfEixPFfb/avmHKIk+oSL3E+LzdvT/SEMZHD8tL3cJALf3N2bRl154ZFXkqCdUGWAV4+45772rvCDU180293dj9+Fv9bHr3MDaLJoyKNk/TTfWAQL2pAdDHdu23m1fVyWNS6zOjEOzxDejmW0vJCMdibdClm1a62vINBbapB0aP43rDEOrZ9532mZv1ZXnvMrFSq4jUqLE2DZtmLiGYmlh4p9ecknsywIkDnw5Mjia+OU3z61v2Tjr/252fdMhP/nZpyJMUedxkYysnQtCaFUJZUJWJSIau7PAfNuXpqMF0Egof9HcDUn8Zn2hfOXdnueoGWXO6JwXV2XCDF4YPouZTU55liW/xS+k3HU9cujYvjUW9/AUp/QYp0NYpY8hiMDusFCQui9eenogHDs=";

        List<Pair<String,String>> requestParameters = new ArrayList<Pair<String, String>>();
        requestParameters.add(new Pair<String, String>("format", "json"));
        requestParameters.add(new Pair<String, String>("lat", String.valueOf(latitude)));
        requestParameters.add(new Pair<String, String>("lon", String.valueOf(longitude)));
        requestParameters.add(new Pair<String, String>("zoom", "18"));
        requestParameters.add(new Pair<String, String>("addressdetails", "1"));
        requestParameters.add(new Pair<String, String>("accept-language", locale.getLanguage()));

        String response;
        try {
            response = WebClient.makeGetRequest(
                null,
                certString,
                torSocksProxyPort,
                "dyuiguvkniysvqgb.onion",
                443,
                "/nominatim/reverse",
                requestParameters);
        }
        catch (Utils.ApplicationError e) {
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
