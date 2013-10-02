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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;

import com.squareup.okhttp.OkHttpClient;

public class WebClient {

    public static String makeGetRequest(
            X509.KeyMaterial x509KeyMaterial,
            String friendCertificate,
            String friendHiddenServiceHostname,
            String requestPath,
            String body) throws Utils.ApplicationError {        
        try {            
            URL url = new URL(Protocol.WEB_SERVER_PROTOCOL, friendHiddenServiceHostname, Protocol.WEB_SERVER_VIRTUAL_PORT, requestPath);
            Proxy proxy = Engine.getInstance().getLocalProxy();
            
            // TODO: cache? or setConnectionPool(ConnectionPool connectionPool)?
            // ... see default connection pool params: http://square.github.io/okhttp/javadoc/com/squareup/okhttp/ConnectionPool.html
            OkHttpClient client = new OkHttpClient();        
            client.setProxy(proxy);
            client.setSslSocketFactory(TransportSecurity.getSSLContext(x509KeyMaterial, friendCertificate).getSocketFactory());
    
            HttpURLConnection connection = client.open(url);
            connection.setRequestMethod("GET");
            
            // TODO: stream larger responses to files, etc.
            // TODO: finally { connection.close(); }
            return Utils.readInputStreamToString(connection.getInputStream());
        } catch (MalformedURLException e) {
            throw new Utils.ApplicationError(e);
        } catch (ProtocolException e) {
            throw new Utils.ApplicationError(e);
        } catch (IOException e) {
            throw new Utils.ApplicationError(e);
        }
    }    
}
