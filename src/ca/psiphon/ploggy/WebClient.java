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
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class WebClient {

    private static final String LOG_TAG = "Web Client";

    private static final int CONNECT_TIMEOUT_MILLISECONDS = 10000;
    private static final int READ_TIMEOUT_MILLISECONDS = 10000;

    public static String makeGetRequest(
            X509.KeyMaterial x509KeyMaterial,
            String peerCertificate,
            Proxy proxy,
            String hostname,
            int port,
            String requestPath,
            String body) throws Utils.ApplicationError {        
        HttpsURLConnection httpsUrlConnection = null;
        try {            
            URL url = new URL(Protocol.WEB_SERVER_PROTOCOL, hostname, port, requestPath);

            // TODO: connection pooling; OkHttp?
            if (proxy != null) {
                httpsUrlConnection = (HttpsURLConnection)url.openConnection(proxy);
            } else {
                httpsUrlConnection = (HttpsURLConnection)url.openConnection();                
            }
            httpsUrlConnection.setConnectTimeout(CONNECT_TIMEOUT_MILLISECONDS);
            httpsUrlConnection.setReadTimeout(READ_TIMEOUT_MILLISECONDS);
            httpsUrlConnection.setHostnameVerifier(TransportSecurity.getHostnameVerifier());
            SSLContext sslContext = TransportSecurity.getSSLContext(x509KeyMaterial, Arrays.asList(peerCertificate));
            httpsUrlConnection.setSSLSocketFactory(TransportSecurity.getSSLSocketFactory(sslContext));
            httpsUrlConnection.setRequestMethod("GET");

            // TODO: stream larger responses to files, etc.
            return Utils.readInputStreamToString(httpsUrlConnection.getInputStream());
        } catch (MalformedURLException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (ProtocolException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (UnsupportedOperationException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (IOException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            if (httpsUrlConnection != null) {
                httpsUrlConnection.disconnect();
            }
        }
    }
    
    public static String makeGetRequest(
            X509.KeyMaterial x509KeyMaterial,
            String friendCertificate,
            String friendHiddenServiceHostname,
            String requestPath,
            String body) throws Utils.ApplicationError {
        Proxy proxy = Engine.getInstance().getLocalProxy();
        return makeGetRequest(
                x509KeyMaterial,
                friendCertificate,
                proxy,
                friendHiddenServiceHostname,
                Protocol.WEB_SERVER_VIRTUAL_PORT,
                requestPath,
                body);
    }
}
